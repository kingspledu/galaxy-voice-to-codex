package com.kuiro.youtubemarker.export

import com.kuiro.youtubemarker.data.NoteEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class NoteBackupTest {
    @get:Rule val temp = TemporaryFolder()
    private fun entry(file: File) = NoteEntity("test-id", videoTitle = "영상 제목", timestampSeconds = 754,
        audioPath = file.absolutePath, createdAtEpochMs = 100, updatedAtEpochMs = 200)
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) { val item = zip.nextEntry ?: break; put(item.name, zip.readBytes()) }
        }
    }
    @Test fun archivePreservesAudioAndMetadataWithoutLeakingInternalPaths() {
        val root = temp.newFolder("recordings")
        val file = File(root, "test-id.m4a").apply { writeBytes(byteArrayOf(0, 1, 2, -1)) }
        val original = file.readBytes()
        val output = ByteArrayOutputStream()
        NoteBackup.write(entry(file), root, output)
        val entries = unzip(output.toByteArray())
        assertArrayEquals(original, entries.getValue("audio/test-id.m4a"))
        assertArrayEquals(original, file.readBytes())
        val json = JSONObject(entries.getValue("note.json").toString(Charsets.UTF_8))
        assertEquals("영상 제목", json.getString("videoTitle"))
        assertEquals(754, json.getLong("timestampSeconds"))
        assertTrue(json.getBoolean("audioAvailable"))
        assertFalse(json.has("audioPath"))
    }
    @Test fun missingAudioStillExportsHonestMetadata() {
        val root = temp.newFolder("recordings")
        val output = ByteArrayOutputStream()
        NoteBackup.write(entry(File(root, "missing.m4a")), root, output)
        val entries = unzip(output.toByteArray())
        assertEquals(setOf("note.json"), entries.keys)
        val json = JSONObject(entries.getValue("note.json").toString(Charsets.UTF_8))
        assertFalse(json.getBoolean("audioAvailable"))
        assertTrue(json.isNull("audioEntry"))
    }
    @Test fun filesOutsideTheRecordingDirectoryAreRejected() {
        val root = temp.newFolder("recordings")
        val outside = temp.newFile("private.txt").apply { writeText("private") }
        val output = ByteArrayOutputStream()
        try { NoteBackup.write(entry(outside), root, output); fail("Outside path accepted") }
        catch (_: IllegalArgumentException) { assertEquals(0, output.size()); assertEquals("private", outside.readText()) }
    }
}
