package com.kuiro.youtubemarker.export

import com.kuiro.youtubemarker.data.NoteEntity
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

object NoteBackup {
    /** Caller opens a user-selected document. Never modifies the source. */
    fun write(note: NoteEntity, recordingsDirectory: File, output: OutputStream) {
        val file = File(note.audioPath).canonicalFile
        require(file.parentFile == recordingsDirectory.canonicalFile) { "Unexpected audio location" }
        require(Regex("[A-Za-z0-9_-]+").matches(note.id))
        val available = file.isFile && file.length() > 0
        val audioEntry = "audio/${note.id}.m4a"
        val json = JSONObject().apply {
            put("formatVersion", 1); put("id", note.id); put("videoTitle", note.videoTitle)
            put("videoUrl", note.videoUrl); put("videoId", note.videoId ?: JSONObject.NULL)
            put("timestampSeconds", note.timestampSeconds ?: JSONObject.NULL)
            put("audioEntry", if (available) audioEntry else JSONObject.NULL)
            put("audioAvailable", available); put("transcript", note.transcript)
            put("captions", note.captions); put("summary", note.summary)
            put("tags", note.tags); put("category", note.category); put("status", note.status)
            put("createdAtEpochMs", note.createdAtEpochMs); put("updatedAtEpochMs", note.updatedAtEpochMs)
            put("warning", "Interrupted audio may not be playable. This archive contains saved data, not unsaved edits.")
        }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("note.json")); zip.write(json.toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
            if (available) {
                zip.putNextEntry(ZipEntry(audioEntry)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
}
