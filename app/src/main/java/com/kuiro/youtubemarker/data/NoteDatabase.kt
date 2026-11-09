package com.kuiro.youtubemarker.data

import android.content.Context
import androidx.room.*
import java.util.concurrent.Executors

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val videoTitle: String = "",
    val videoUrl: String = "",
    val videoId: String? = null,
    val timestampSeconds: Long? = null,
    val audioPath: String,
    val transcript: String = "",
    val captions: String = "",
    val summary: String = "",
    val tags: String = "",
    val category: String = "",
    val status: String = "RECORDING",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE id=:id") fun find(id: String): NoteEntity?
    @Insert fun insert(note: NoteEntity)
    @Query("SELECT * FROM notes ORDER BY createdAtEpochMs DESC") fun all(): List<NoteEntity>
    @Query("UPDATE notes SET status=:status, updatedAtEpochMs=:now WHERE id=:id")
    fun status(id: String, status: String, now: Long)
    @Query("UPDATE notes SET status='INTERRUPTED' WHERE status='RECORDING'") fun recoverInterrupted()
    @Query("""UPDATE notes SET videoTitle=:title, videoUrl=:url, videoId=:videoId,
        timestampSeconds=:seconds, transcript=:transcript, captions=:captions, summary=:summary,
        tags=:tags, category=:category, updatedAtEpochMs=:now
        WHERE id=:id AND updatedAtEpochMs=:expected AND status NOT IN ('RECORDING','TRANSCRIBING')""")
    fun edit(id: String, expected: Long, title: String, url: String, videoId: String?, seconds: Long?,
        transcript: String, captions: String, summary: String, tags: String, category: String, now: Long): Int
    @Query("DELETE FROM notes WHERE id=:id AND updatedAtEpochMs=:expected AND status NOT IN ('RECORDING','TRANSCRIBING')")
    fun deleteInactive(id: String, expected: Long): Int
    @Query("UPDATE notes SET status='TRANSCRIBING', updatedAtEpochMs=:now WHERE id=:id AND transcript='' AND status IN ('RECORDED','FAILED','INTERRUPTED','TRANSCRIBING') AND updatedAtEpochMs=:expected")
    fun claimTranscription(id: String, expected: Long, now: Long): Int
    @Query("UPDATE notes SET transcript=:text, status='TRANSCRIBED', updatedAtEpochMs=:now WHERE id=:id AND status='TRANSCRIBING' AND transcript=''")
    fun finishTranscription(id: String, text: String, now: Long): Int
    @Query("UPDATE notes SET status=:state, updatedAtEpochMs=:now WHERE id=:id AND status='TRANSCRIBING'")
    fun releaseTranscription(id: String, state: String, now: Long)
}

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    companion object {
        val executor = Executors.newSingleThreadExecutor()
        @Volatile private var instance: NoteDatabase? = null
        private var recovered = false
        // Call on executor. Opening another activity must not interrupt a live recording.
        fun recoverOnce(dao: NoteDao) {
            if (!recovered) { dao.recoverInterrupted(); recovered = true }
        }
        fun get(context: Context): NoteDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NoteDatabase::class.java, "notes.db")
                .build().also { instance = it }
        }
    }
}
