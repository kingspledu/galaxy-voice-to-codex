package com.kuiro.youtubemarker.transcription

import android.content.Context
import androidx.work.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

class ModelDownloadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    @Volatile private var connection: HttpURLConnection? = null
    override fun doWork(): Result {
        var partial: File? = null
        try {
            val model = ModelCatalog.get(inputData.getString("model").orEmpty())
            val target = ModelCatalog.file(applicationContext, model)
            if (ModelCatalog.verified(target, model)) return Result.success(workDataOf("message" to "모델 준비 완료"))
            check(target.parentFile!!.usableSpace > model.bytes * 2) { "Not enough storage" }
            val part = File(target.parentFile, target.name + ".part"); partial = part
            val conn = URL(model.url).openConnection() as HttpURLConnection
            connection = conn; conn.connectTimeout = 20_000; conn.readTimeout = 60_000
            // GET only. No audio, API credentials, or note metadata are sent.
            require(conn.responseCode == 200)
            var total = 0L
            var lastPercent = -1
            conn.inputStream.use { input -> part.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    check(!isStopped)
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= model.bytes)
                    output.write(buffer, 0, count)
                    val percent = (total * 100 / model.bytes).toInt()
                    if (percent != lastPercent) { setProgressAsync(workDataOf("percent" to percent)); lastPercent = percent }
                }
            } }
            check(ModelCatalog.verified(part, model)) { "Model checksum mismatch" }
            java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            partial = null
            return Result.success(workDataOf("message" to "모델 다운로드·SHA-256 확인 완료"))
        } catch (_: Exception) {
            return Result.failure(workDataOf("message" to "모델 다운로드 실패 · 네트워크·저장공간을 확인하고 다시 누르세요. 녹음은 보존됩니다."))
        } finally { connection?.disconnect(); connection = null; partial?.delete() }
    }
    override fun onStopped() { connection?.disconnect(); super.onStopped() }
    companion object {
        fun name(id: String) = "whisper-model-$id"
        fun enqueue(context: Context, id: String) {
            ModelCatalog.get(id)
            WorkManager.getInstance(context).enqueueUniqueWork(name(id), ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(workDataOf("model" to id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
