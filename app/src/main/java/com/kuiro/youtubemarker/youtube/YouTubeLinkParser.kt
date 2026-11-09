package com.kuiro.youtubemarker.youtube

import android.net.Uri

object YouTubeLinkParser {
    fun videoId(raw: String): String? {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("http", "https")) return null
        val host = uri.host?.lowercase() ?: return null
        return when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()
            host in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com") && uri.path == "/watch" -> uri.getQueryParameter("v")
            host in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com") && uri.pathSegments.firstOrNull() in listOf("shorts", "live", "embed") -> uri.pathSegments.getOrNull(1)
            else -> null
        }?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }
}
