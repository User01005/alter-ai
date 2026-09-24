package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "alter_cards")
data class AlterCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "audio", "text", "image"
    val rawInput: String, // raw text input, cached audio path, image path, etc.
    val processedContent: String, // Gemini polished response (clean Markdown)
    val timestamp: Long = System.currentTimeMillis(),
    val mediaPath: String? = null, // local persistent file path for audio/image media playback/preview
    val isDeleted: Boolean = false,
    val deletedTimestamp: Long? = null,
    val personalNotes: String = "",
    val canvasData: String? = null // JSON serialized canvas state for board/doodles/notes/images
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val sender: String, // "USER" or "AI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatSessionWithMessages(
    @Embedded val session: ChatSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val messages: List<ChatMessageEntity>
)

@Dao
interface AlterCardDao {
    @Query("SELECT * FROM alter_cards WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllCards(): Flow<List<AlterCard>>

    @Query("SELECT * FROM alter_cards WHERE isDeleted = 1 ORDER BY deletedTimestamp DESC")
    fun getDeletedCards(): Flow<List<AlterCard>>

    @Query("SELECT * FROM alter_cards ORDER BY timestamp DESC")
    suspend fun getAllCardsDirect(): List<AlterCard>

    @Query("SELECT * FROM alter_cards WHERE id = :cardId LIMIT 1")
    suspend fun getCardById(cardId: Long): AlterCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: AlterCard): Long

    @Delete
    suspend fun deleteCard(card: AlterCard)

    @Query("DELETE FROM alter_cards WHERE isDeleted = 1 AND deletedTimestamp < :threshold")
    suspend fun permanentlyDeleteOldCards(threshold: Long)

    @Query("DELETE FROM alter_cards")
    suspend fun clearAllCards()
}

@Dao
interface ChatDao {
    @Transaction
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllChatSessions(): Flow<List<ChatSessionWithMessages>>

    @Transaction
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    suspend fun getAllChatSessionsDirect(): List<ChatSessionWithMessages>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Query("UPDATE chat_sessions SET title = :title, timestamp = :timestamp WHERE id = :sessionId")
    suspend fun updateSession(sessionId: String, title: String, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Transaction
    suspend fun saveSessionAndMessage(session: ChatSessionEntity, message: ChatMessageEntity) {
        val rowId = insertSession(session)
        if (rowId == -1L) {
            updateSession(session.id, session.title, session.timestamp)
        }
        insertMessage(message)
    }

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllChatSessions()

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllChatMessages()
}

@Database(
    entities = [AlterCard::class, ChatSessionEntity::class, ChatMessageEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AlterDatabase : RoomDatabase() {
    abstract fun cardDao(): AlterCardDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AlterDatabase? = null

        fun getDatabase(context: Context): AlterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlterDatabase::class.java,
                    "alter_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class AlterRepository(
    private val cardDao: AlterCardDao,
    private val chatDao: ChatDao
) {
    val allCards: Flow<List<AlterCard>> = cardDao.getAllCards()
    val deletedCards: Flow<List<AlterCard>> = cardDao.getDeletedCards()
    val allChatSessions: Flow<List<ChatSessionWithMessages>> = chatDao.getAllChatSessions()

    suspend fun getAllCardsDirect(): List<AlterCard> {
        return cardDao.getAllCardsDirect()
    }

    suspend fun insertCard(card: AlterCard): Long {
        return cardDao.insertCard(card)
    }

    suspend fun deleteCard(card: AlterCard) {
        cardDao.deleteCard(card)
    }

    suspend fun permanentlyDeleteOldCards(threshold: Long) {
        cardDao.permanentlyDeleteOldCards(threshold)
    }

    suspend fun clearAllCards() {
        cardDao.clearAllCards()
    }

    suspend fun insertChatSession(session: ChatSessionEntity) {
        val rowId = chatDao.insertSession(session)
        if (rowId == -1L) {
            chatDao.updateSession(session.id, session.title, session.timestamp)
        }
    }

    suspend fun insertChatMessage(message: ChatMessageEntity) {
        chatDao.insertMessage(message)
    }

    suspend fun saveSessionAndMessage(session: ChatSessionEntity, message: ChatMessageEntity) {
        chatDao.saveSessionAndMessage(session, message)
    }

    suspend fun insertChatMessages(messages: List<ChatMessageEntity>) {
        chatDao.insertMessages(messages)
    }

    suspend fun deleteChatSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }

    suspend fun clearAllChatSessions() {
        chatDao.clearAllChatSessions()
        chatDao.clearAllChatMessages()
    }
}
