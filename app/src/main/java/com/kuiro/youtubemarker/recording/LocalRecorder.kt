package com.kuiro.youtubemarker.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class LocalRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    fun start(file: File) {
        check(recorder == null)
        val current = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            current.setAudioSource(MediaRecorder.AudioSource.MIC)
            current.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            current.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            current.setAudioEncodingBitRate(96000)
            current.setAudioSamplingRate(44100)
            current.setOutputFile(file.absolutePath)
            current.prepare()
            current.start()
            recorder = current
        } catch (error: Exception) {
            current.release()
            // Failed or partial recordings are preserved for inspection.
            throw error
        }
    }
    fun stop(): Boolean {
        val current = recorder ?: return false
        recorder = null
        return try { current.stop(); true } catch (_: RuntimeException) { false }
        finally { current.release() }
    }
}
