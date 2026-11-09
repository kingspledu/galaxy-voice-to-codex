package com.kuiro.youtubemarker.transcription

import android.content.Context

class OfflineSettings(context: Context) {
    private val prefs = context.getSharedPreferences("offline-whisper", Context.MODE_PRIVATE)
    val automatic get() = prefs.getBoolean("automatic", false)
    val model get() = ModelCatalog.get(prefs.getString("model", "base").orEmpty())
    fun save(id: String, automatic: Boolean) { ModelCatalog.get(id); check(prefs.edit().putString("model", id).putBoolean("automatic", automatic).commit()) }
}
