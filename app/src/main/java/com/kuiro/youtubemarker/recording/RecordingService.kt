package com.kuiro.youtubemarker.recording

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.Build
import android.widget.Toast
import com.kuiro.youtubemarker.data.*
import com.kuiro.youtubemarker.overlay.ServiceNotification
import com.kuiro.youtubemarker.youtube.YouTubeLinkParser
import java.io.File
import java.util.UUID

class RecordingService : Service() {
    companion object {
        var phase = "IDLE"; private set
        var message = "녹음 준비"; private set
        var generation = 0; private set
        val active get() = phase in setOf("PREPARING", "RECORDING", "SAVING")
    }
    private val handler = Handler(Looper.getMainLooper())
    private val recorder by lazy { LocalRecorder(this) }
    private var note: NoteEntity? = null
    private var started = false
    private var destroyed = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_RECORDING") { finishCapture(); stopSelf(); return START_NOT_STICKY }
        if (note != null) return START_NOT_STICKY
        generation++
        try {
            val notification = ServiceNotification.create(this, "음성 녹음 중 · 종료하면 저장합니다.", Intent(this, javaClass).setAction("STOP_RECORDING"))
            if (Build.VERSION.SDK_INT >= 30) startForeground(102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(102, notification)
            val id = UUID.randomUUID().toString()
            if (!CaptureGate.claim(id)) { fail("다른 창의 녹음을 먼저 저장하세요."); return START_NOT_STICKY }
            val dir = File(noBackupFilesDir, "recordings")
            if (!dir.isDirectory && !dir.mkdirs()) { CaptureGate.release(id); fail("저장 폴더를 만들지 못했습니다."); return START_NOT_STICKY }
            val now = System.currentTimeMillis()
            val link = intent?.getStringExtra("url").orEmpty()
            val seconds = intent?.getLongExtra("seconds", -1L)?.takeIf { it >= 0 }
            val entry = NoteEntity(id, intent?.getStringExtra("title").orEmpty(), link,
                YouTubeLinkParser.videoId(link), seconds, File(dir, "$id.m4a").absolutePath,
                createdAtEpochMs = now, updatedAtEpochMs = now)
            note = entry; phase = "PREPARING"; message = "저장소 준비 중…"
            NoteDatabase.executor.execute {
                try {
                    val dao = NoteDatabase.get(this).notes()
                    NoteDatabase.recoverOnce(dao); dao.insert(entry)
                    handler.post {
                        if (destroyed || note?.id != entry.id) return@post
                        try {
                            recorder.start(File(entry.audioPath)); started = true
                            phase = "RECORDING"; message = "녹음 중 · 종료하고 저장을 누르세요."
                        } catch (_: Exception) { finishCapture(); fail("녹음 시작 실패 · 파일은 삭제하지 않았습니다.") }
                    }
                } catch (_: Exception) {
                    handler.post { if (!destroyed) { note = null; CaptureGate.release(id); fail("저장소 오류로 녹음을 시작하지 않았습니다.") } }
                }
            }
        } catch (_: Exception) { fail("마이크 서비스 시작 실패 · 앱에서 권한을 확인하세요.") }
        return START_NOT_STICKY
    }
    private fun finishCapture() {
        val entry = note ?: return
        note = null
        val result = if (started) recorder.stop() else false
        started = false; phase = "SAVING"; message = "파일 상태 저장 중…"
        // Keep ownership until DB status has been committed.
        NoteDatabase.executor.execute {
            val state = if (result) "RECORDED" else "INTERRUPTED"
            val saved = runCatching { NoteDatabase.get(this).notes().status(entry.id, state, System.currentTimeMillis()) }.isSuccess
            if (saved && result) runCatching { com.kuiro.youtubemarker.transcription.TranscriptionQueue.afterRecording(this, entry.id) }
            handler.post {
                CaptureGate.release(entry.id)
                phase = if (saved && result) "SAVED" else "ERROR"
                message = when {
                    !saved -> "DB 갱신 실패 · 원본은 보존됩니다. 앱에서 중단된 메모를 확인하세요."
                    !result -> "녹음 중단 · 남은 파일은 보존했습니다."
                    else -> "녹음 저장 완료 · 음성 변환 대기"
                }
            }
        }
    }
    private fun fail(value: String) {
        phase = "ERROR"; message = value
        Toast.makeText(this, value, Toast.LENGTH_LONG).show(); stopSelf()
    }
    override fun onDestroy() { destroyed = true; finishCapture(); super.onDestroy() }
}
