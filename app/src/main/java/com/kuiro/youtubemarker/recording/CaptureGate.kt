package com.kuiro.youtubemarker.recording

/** Main-thread-only capture ownership shared by activity and foreground recorder. */
object CaptureGate {
    var owner: String? = null
        private set
    fun claim(id: String): Boolean {
        if (owner != null) return false
        owner = id
        return true
    }
    fun release(id: String) { if (owner == id) owner = null }
}
