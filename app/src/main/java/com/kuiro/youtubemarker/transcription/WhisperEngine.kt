package com.kuiro.youtubemarker.transcription

object WhisperEngine {
    init { System.loadLibrary("yvm_whisper") }
    external fun resetAbort()
    external fun cancel()
    external fun transcribe(path: String, pcm: FloatArray): String
}
