package com.kuiro.youtubemarker.transcription

import org.junit.Assert.*
import org.junit.Test

class PcmResamplerTest {
    @Test fun oneSecondAt44100ProducesExactly16000Samples() {
        val output = PcmResampler.to16k(FloatArray(44100) { 0.25f }, 44100)
        assertEquals(16000, output.size)
        assertTrue(output.all { kotlin.math.abs(it - 0.25f) < 0.00001f })
    }
    @Test fun native16000IsPreservedWithoutCopying() {
        val input = floatArrayOf(-1f, 0f, 1f)
        assertSame(input, PcmResampler.to16k(input, 16000))
    }
    @Test fun upsamplingDoesNotReadPastTheLastSample() {
        val output = PcmResampler.to16k(floatArrayOf(-1f, 1f), 8000)
        assertArrayEquals(floatArrayOf(-1f, 0f, 1f, 1f), output, 0.00001f)
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedRateFailsExplicitly() {
        PcmResampler.to16k(floatArrayOf(0f), 192000)
    }
}
