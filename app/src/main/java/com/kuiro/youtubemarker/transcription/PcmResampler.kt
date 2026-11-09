package com.kuiro.youtubemarker.transcription

object PcmResampler {
    fun to16k(mono: FloatArray, rate: Int): FloatArray {
        require(rate in 8000..48000 && mono.isNotEmpty())
        if (rate == 16000) return mono
        val count = (mono.size.toLong() * 16000 / rate).toInt()
        return FloatArray(count) { index ->
            val source = index.toDouble() * rate / 16000
            val left = source.toInt().coerceAtMost(mono.lastIndex)
            val right = (left + 1).coerceAtMost(mono.lastIndex)
            (mono[left] + (mono[right] - mono[left]) * (source - left)).toFloat()
        }
    }
}
