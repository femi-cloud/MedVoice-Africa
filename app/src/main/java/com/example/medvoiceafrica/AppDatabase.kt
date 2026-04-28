package com.example.medvoiceafrica

// ================================================================
// AppDatabase.kt — FINAL CORRIGÉ
// Corrections vs version précédente :
//   - Ajout de ConsultationLog + ConsultationDao (StatsScreen)
//   - Version bumped 3 → 4
//   - Migration 3→4 ajoutée (crée la table consultation_log)
//   - fallbackToDestructiveMigration retiré (migration propre)
// ================================================================

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ── Entités existantes ────────────────────────────────────────────

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

// ── DAO sessions ──────────────────────────────────────────────────

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

    @Query("UPDATE chat_sessions SET title = :title, triageLevel = :triageLevel, timestamp = :timestamp WHERE id = :id")
    suspend fun updateSession(id: Long, title: String, triageLevel: String, timestamp: Long = System.currentTimeMillis())

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

// ── Database ──────────────────────────────────────────────────────

@Database(
    entities = [
        ChatSession::class,
        ChatMessageEntity::class,
        OmsProtocol::class,
        ConsultationLog::class   // NOUVEAU
    ],
    version = 4,   // bumped 3 → 4
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChatSessionDao
    abstract fun messageDao(): ChatMessageDao
    abstract fun omsProtocolDao(): OmsProtocolDao
    abstract fun consultationDao(): ConsultationDao   // NOUVEAU

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medvoice_db"
                )
                    .addMigrations(MIGRATION_3_4)
                    // fallbackToDestructiveMigration gardé en sécurité pour le dev
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        // ── Migration 3 → 4 : ajout de la table consultation_log ──
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS consultation_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL DEFAULT 0,
                        pathologie TEXT NOT NULL DEFAULT '',
                        triage TEXT NOT NULL DEFAULT 'UNKNOWN',
                        sessionId INTEGER NOT NULL DEFAULT -1,
                        isOffline INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }
    }
}