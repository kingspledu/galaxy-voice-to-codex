package com.kuiro.youtubemarker.transcription

import android.media.*
import java.io.File
import java.nio.ByteOrder

object AudioDecoder {
    /** Decode a short local memo; never changes its M4A source. */
    fun decode(file: File, stopped: () -> Boolean): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: error("No audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            require(rate in 8000..48000)
            if (format.containsKey(MediaFormat.KEY_DURATION)) require(format.getLong(MediaFormat.KEY_DURATION) <= 120_000_000L) { "Memo exceeds two minutes" }
            val mono = FloatArray(rate * 120)
            var size = 0; var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(channels in 1..2)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder; decoder.configure(format, null, null, 0); decoder.start()
            var inputEnded = false; var outputEnded = false
            val info = MediaCodec.BufferInfo()
            val deadline = android.os.SystemClock.elapsedRealtime() + 120_000
            while (!outputEnded) {
                check(!stopped()) { "Cancelled" }
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Decode timed out" }
                if (!inputEnded) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val input = decoder.getInputBuffer(index)!!
                        val read = extractor.readSampleData(input, 0)
                        if (read < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                        else { decoder.queueInputBuffer(index, 0, read, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val output = decoder.outputFormat
                    require(output.getInteger(MediaFormat.KEY_SAMPLE_RATE) == rate)
                    channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT); require(channels in 1..2)
                    encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                } else if (index >= 0) {
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val output = decoder.getOutputBuffer(index)!!
                            output.position(info.offset); output.limit(info.offset + info.size)
                            val data = output.slice().order(ByteOrder.nativeOrder())
                            val width = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            val frames = data.remaining() / width / channels
                            require(size + frames <= mono.size) { "Memo exceeds two minutes" }
                            repeat(frames) {
                                var sum = 0f
                                repeat(channels) { sum += if (width == 4) data.float else data.short / 32768f }
                                mono[size++] = (sum / channels).coerceIn(-1f, 1f)
                            }
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { decoder.releaseOutputBuffer(index, false) }
                }
            }
            require(size >= rate / 100) { "Empty audio" }
            return PcmResampler.to16k(mono.copyOf(size), rate)
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }; extractor.release()
        }
    }
}
