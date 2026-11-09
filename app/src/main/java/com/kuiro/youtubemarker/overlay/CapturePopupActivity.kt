package com.kuiro.youtubemarker.overlay

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import com.kuiro.youtubemarker.recording.*
import com.kuiro.youtubemarker.youtube.YouTubeLinkParser

class CapturePopupActivity : Activity() {
    private lateinit var titleInput: EditText
    private lateinit var url: EditText
    private lateinit var time: EditText
    private lateinit var status: TextView
    private lateinit var record: Button
    private val handler = Handler(Looper.getMainLooper())
    private var requesting = false
    private var expectedGeneration = 0
    private var requestedAt = 0L
    private var lastPhase = ""
    private var lastGeneration = -1
    private var closeAfterSave = false
    private val update = object : Runnable {
        override fun run() {
            if (!requesting || RecordingService.generation >= expectedGeneration) {
                requesting = false
                if (lastPhase != RecordingService.phase || lastGeneration != RecordingService.generation) {
                    status.text = RecordingService.message
                    lastPhase = RecordingService.phase; lastGeneration = RecordingService.generation
                }
            }
            if (requesting && SystemClock.elapsedRealtime() - requestedAt > 8000) {
                requesting = false; status.text = "서비스 응답이 없습니다. 알림과 앱에서 녹음 상태를 확인하세요."
            }
            val active = RecordingService.active
            record.text = if (active) "■ 종료하고 저장" else "● 녹음 시작"
            record.isEnabled = !requesting && RecordingService.phase !in setOf("PREPARING", "SAVING")
            listOf(titleInput, url, time).forEach { it.isEnabled = !active && !requesting }
            if (closeAfterSave && !active && !requesting) { finish(); return }
            if (closeAfterSave && RecordingService.phase == "RECORDING") stopRecording()
            handler.postDelayed(this, 250)
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setFinishOnTouchOutside(false)
        closeAfterSave = state?.getBoolean("closeAfterSave") ?: false
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        fun field(hint: String) = EditText(this).apply { this.hint = hint; root.addView(this) }
        root.addView(TextView(this).apply { text = "음성 메모 · 영상 시점은 직접 입력"; textSize = 18f })
        val prefs = getSharedPreferences("video", MODE_PRIVATE)
        titleInput = field("영상 제목 (선택)").apply { setText(state?.getString("title") ?: prefs.getString("title", "")) }
        url = field("YouTube URL (선택)").apply { setText(state?.getString("url") ?: prefs.getString("url", "")) }
        time = field("현재 영상 시점 예: 12:34").apply { setText(state?.getString("time").orEmpty()) }
        status = TextView(this).apply { text = "녹음 준비"; root.addView(this) }
        record = Button(this).apply {
            text = "● 녹음 시작"; root.addView(this)
            setOnClickListener {
                if (RecordingService.active) stopRecording()
                else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20)
                else beginRecording()
            }
        }
        root.addView(Button(this).apply {
            text = "닫고 영상으로 돌아가기"
            setOnClickListener { closePopup() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        window.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    override fun onResume() { super.onResume(); handler.post(update) }
    override fun onPause() { handler.removeCallbacks(update); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("title", titleInput.text.toString()); out.putString("url", url.text.toString()); out.putString("time", time.text.toString())
        out.putBoolean("closeAfterSave", closeAfterSave)
        super.onSaveInstanceState(out)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 20 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) beginRecording()
        else status.text = "마이크 권한이 필요합니다. 앱 설정에서 변경할 수 있습니다."
    }
    private fun beginRecording() {
        if (requesting || RecordingService.active) return
        if (CaptureGate.owner != null) { status.text = "다른 창의 녹음을 먼저 저장하세요."; return }
        val link = url.text.toString().trim()
        if (link.isNotEmpty() && YouTubeLinkParser.videoId(link) == null) { url.error = "YouTube URL을 확인하세요."; return }
        val raw = time.text.toString().trim()
        if (raw.isNotEmpty() && !Regex("\\d{1,5}:[0-5]\\d(:[0-5]\\d)?").matches(raw)) { time.error = "예: 12:34 또는 1:02:03"; return }
        val seconds = if (raw.isEmpty()) -1L else raw.split(':').fold(0L) { n, part -> n * 60 + part.toLong() }
        getSharedPreferences("video", MODE_PRIVATE).edit().putString("url", link).putString("title", titleInput.text.toString()).apply()
        expectedGeneration = RecordingService.generation + 1; requestedAt = SystemClock.elapsedRealtime()
        requesting = true; record.isEnabled = false; status.text = "마이크 서비스 준비 중…"
        try {
            startForegroundService(Intent(this, RecordingService::class.java).setAction("START_RECORDING")
                .putExtra("url", link).putExtra("title", titleInput.text.toString()).putExtra("seconds", seconds))
        } catch (_: Exception) { requesting = false; status.text = "녹음을 시작하지 못했습니다. 앱에서 다시 시도하세요." }
    }
    private fun stopRecording() {
        runCatching { startService(Intent(this, RecordingService::class.java).setAction("STOP_RECORDING")) }
            .onFailure { status.text = "녹음 알림에서 종료해 주세요." }
    }
    private fun closePopup() {
        if (RecordingService.active || requesting) { closeAfterSave = true; if (!requesting) stopRecording() }
        else finish()
    }
    @Deprecated("Legacy back handling")
    override fun onBackPressed() { closePopup() }
}
