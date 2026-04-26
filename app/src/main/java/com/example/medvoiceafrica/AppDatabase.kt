package com.example.medvoiceafrica

// ================================================================
// LOCATION: app/src/main/java/com/example/medvoiceafrica/AppDatabase.kt
// Changes vs previous version:
//   - updateSession now also updates timestamp (for "last modified" sorting)
//   - Added renameSession for the rename dialog
//   - DB version bumped to 3
// ================================================================

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triageLevel: String = "UNKNOWN",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val text: String,
    val isUser: Boolean,
    val triageLevel: String = "UNKNOWN",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Insert
    suspend fun insertSession(session: ChatSession): Long

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)

    // FIX: also update timestamp so "last modified" is accurate
    @Query("UPDATE chat_sessions SET title = :title, triageLevel = :triageLevel, timestamp = :timestamp WHERE id = :id")
    suspend fun updateSession(id: Long, title: String, triageLevel: String, timestamp: Long = System.currentTimeMillis())

    // NEW: rename only (user-triggered, does NOT change timestamp)
    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun renameSession(id: Long, title: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long
}

@Database(
    entities = [ChatSession::class, ChatMessageEntity::class, OmsProtocol::class],
    version = 3,   // bumped from 2 → 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChatSessionDao
    abstract fun messageDao(): ChatMessageDao
    abstract fun omsProtocolDao(): OmsProtocolDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "medvoice_db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}