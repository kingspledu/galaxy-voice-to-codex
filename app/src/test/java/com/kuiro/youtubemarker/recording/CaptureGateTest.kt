package com.kuiro.youtubemarker.recording

import org.junit.Assert.*
import org.junit.Test

class CaptureGateTest {
    @Test fun anotherWindowCannotStealOrReleaseTheMicrophone() {
        assertTrue(CaptureGate.claim("main"))
        try {
            assertFalse(CaptureGate.claim("popup"))
            CaptureGate.release("popup")
            assertEquals("main", CaptureGate.owner)
            assertFalse(CaptureGate.claim("popup"))
        } finally { CaptureGate.release("main") }
        assertTrue(CaptureGate.claim("popup"))
        CaptureGate.release("popup")
        assertNull(CaptureGate.owner)
    }
}
