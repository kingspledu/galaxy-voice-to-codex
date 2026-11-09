package com.kuiro.youtubemarker.transcription

import android.content.Context
import androidx.work.*

object TranscriptionQueue {
    const val TAG = "transcription"
    fun name(id: String) = "transcription-$id"
    fun enqueue(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(workDataOf("noteId" to id))
            .addTag(TAG).build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(id), ExistingWorkPolicy.KEEP, request)
    }
    fun afterRecording(context: Context, id: String) {
        if (OfflineSettings(context).automatic) enqueue(context, id)
    }
}
