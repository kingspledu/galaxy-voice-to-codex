package com.kuiro.youtubemarker.transcription

import android.content.Context
import java.io.File
import java.security.MessageDigest

data class LocalModel(val id: String, val label: String, val bytes: Long, val sha256: String, val minimumFreeMemory: Long) {
    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-$id.bin"
}
object ModelCatalog {
    val models = listOf(
        LocalModel("base", "base 다국어 · 약 148MB · 시작 모델", 147951465, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe", 600_000_000),
        LocalModel("small-q5_1", "small Q5_1 다국어 · 약 190MB · 비교 모델", 190085487, "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", 1_200_000_000),
        LocalModel("large-v3-turbo-q5_0", "large-v3-turbo Q5_0 · 약 574MB · 실험용", 574041195, "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2", 3_000_000_000)
    )
    fun get(id: String) = models.firstOrNull { it.id == id } ?: error("Unknown model")
    fun directory(context: Context) = File(context.noBackupFilesDir, "whisper-models").apply { check(isDirectory || mkdirs()) }
    fun file(context: Context, model: LocalModel) = File(directory(context), "ggml-${model.id}.bin")
    fun installed(context: Context, model: LocalModel) = file(context, model).let { it.isFile && it.length() == model.bytes }
    fun verified(file: File, model: LocalModel): Boolean {
        if (!file.isFile || file.length() != model.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) } }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == model.sha256
    }
}
