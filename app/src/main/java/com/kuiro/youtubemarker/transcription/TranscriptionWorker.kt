package com.kuiro.youtubemarker.transcription

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.work.*
import com.kuiro.youtubemarker.data.NoteDatabase
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class TranscriptionWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    companion object { private val engineSlot = Semaphore(1) }
    @Volatile private var inEngine = false
    private val dao get() = NoteDatabase.get(applicationContext).notes()
    override fun doWork(): Result {
        val id = inputData.getString("noteId") ?: return Result.failure()
        var claimed = false; var acquired = false
        val start = SystemClock.elapsedRealtime()
        val before = Debug.getPss()
        try {
            if (runAttemptCount >= 2) {
                dao.releaseTranscription(id, "FAILED", System.currentTimeMillis())
                return Result.failure(workDataOf("message" to "반복 중단으로 자동 실행을 멈췄습니다. 가벼운 모델로 변경 후 직접 재시도하세요. 원본 보존."))
            }
            while (!engineSlot.tryAcquire(250, TimeUnit.MILLISECONDS)) check(!isStopped)
            acquired = true
            check(!isStopped)
            val entry = dao.find(id) ?: return Result.success()
            if (entry.transcript.isNotBlank()) return Result.failure(workDataOf("message" to "기존 전사는 덮어쓰지 않습니다."))
            if (dao.claimTranscription(id, entry.updatedAtEpochMs, System.currentTimeMillis()) != 1) return Result.failure(workDataOf("message" to "녹음 또는 메모 상태가 바뀌었습니다."))
            claimed = true
            val audio = File(entry.audioPath).canonicalFile
            require(audio.parentFile == File(applicationContext.noBackupFilesDir, "recordings").canonicalFile && audio.isFile && audio.length() > 0)
            val model = OfflineSettings(applicationContext).model
            val modelFile = ModelCatalog.file(applicationContext, model)
            check(ModelCatalog.verified(modelFile, model)) { "모델이 없습니다. 설정에서 다운로드 후 다시 시도하세요." }
            val memory = ActivityManager.MemoryInfo()
            applicationContext.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
            check(!memory.lowMemory && memory.availMem >= model.minimumFreeMemory) { "메모리가 부족합니다. base 모델로 변경하세요." }
            val pcm = AudioDecoder.decode(audio) { isStopped }
            inEngine = true; WhisperEngine.resetAbort(); check(!isStopped)
            val text = WhisperEngine.transcribe(modelFile.absolutePath, pcm).trim()
            check(!isStopped && text.isNotBlank())
            check(dao.finishTranscription(id, text, System.currentTimeMillis()) == 1) { "메모가 변경되어 결과를 덮어쓰지 않았습니다." }
            claimed = false
            return Result.success(workDataOf("message" to "기기 내 변환 완료", "model" to model.id,
                "elapsedMs" to SystemClock.elapsedRealtime() - start, "pssBeforeKb" to before, "pssAfterKb" to Debug.getPss()))
        } catch (error: Throwable) {
            // Native process termination/OOM kills cannot be recovered here; WorkManager can resume on a later launch.
            if (error is ThreadDeath) throw error
            return Result.failure(workDataOf("message" to "로컬 변환 실패 · 모델·저장공간·메모리를 확인하세요. 현재 최대 2분 녹음을 지원합니다. 원본 보존, 다시 요청 가능."))
        } finally {
            inEngine = false
            if (claimed) runCatching { dao.releaseTranscription(id, "FAILED", System.currentTimeMillis()) }
            if (acquired) engineSlot.release()
        }
    }
    override fun onStopped() { if (inEngine) runCatching { WhisperEngine.cancel() }; super.onStopped() }
}
