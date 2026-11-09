package com.kuiro.youtubemarker.data

data class VoiceNote(
    val id: String,
    val videoTitle: String? = null,
    val videoUrl: String? = null,
    val videoId: String? = null,
    val timestampSeconds: Long? = null,
    val audioPath: String,
    val transcript: String? = null,
    val captions: String? = null,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
    val status: ProcessingStatus = ProcessingStatus.RECORDED,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

enum class ProcessingStatus { RECORDED, TRANSCRIBING, TRANSCRIBED, FAILED }
