package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.app.PendingIntent
import android.app.AlarmManager
import android.os.Build
import android.appwidget.AppWidgetManager
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.TodoReminderReceiver
import com.example.api.ChatMessage
import com.example.api.ChatSender
import com.example.api.ChatSession
import com.example.api.GeminiService
import com.example.audio.AudioRecorder
import com.example.data.AlterCard
import com.example.data.AlterRepository
import com.example.data.ChatMessageEntity
import com.example.data.ChatSessionEntity
import com.example.ui.AlterWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AlterViewModel(
    private val repository: AlterRepository,
    private val context: Context
) : ViewModel() {

    companion object {
        private val bgSupervisor = SupervisorJob()
        private val bgScope = CoroutineScope(bgSupervisor + Dispatchers.IO)
        private val _globalIsProcessing = MutableStateFlow(false)
        private val _globalProcessingMessage = MutableStateFlow("")
        private val _globalErrorMessage = MutableStateFlow<String?>(null)
    }

    private val _todoRemindersMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val todoRemindersMap: StateFlow<Map<String, Long>> = _todoRemindersMap.asStateFlow()

    init {
        runAutoPurge()
        loadTodoReminders()
        observeChatHistory()
        try {
            val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
            val savedError = prefs.getString("last_background_error", null)
            if (!savedError.isNullOrBlank()) {
                _globalErrorMessage.value = savedError
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setError(msg: String) {
        _globalErrorMessage.value = msg
        try {
            val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
            prefs.edit().putString("last_background_error", msg).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearErrorMessage() {
        _globalErrorMessage.value = null
        try {
            val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
            prefs.edit().remove("last_background_error").apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadTodoReminders() {
        val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
        val allEntries = prefs.all
        val mapped = mutableMapOf<String, Long>()
        allEntries.forEach { (key, value) ->
            if (value is Long) {
                mapped[key] = value
            } else if (value is Int) {
                mapped[key] = value.toLong()
            }
        }
        _todoRemindersMap.value = mapped
    }

    private val audioRecorder = AudioRecorder(context)

    fun getMicAmplitude(): Int {
        return audioRecorder.getMaxAmplitude()
    }

    private fun notifyWidgetUpdate() {
        try {
            val widgetIntent = Intent(context, AlterWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, AlterWidgetProvider::class.java)
            )
            widgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(widgetIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Flow of historical active (un-deleted) scratchpad cards
    val cards: StateFlow<List<AlterCard>> = repository.allCards
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Flow of soft-deleted cells in the Trash Bin (past 7 days)
    val deletedCards: StateFlow<List<AlterCard>> = repository.deletedCards
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Dynamic UI State variables ---
    private val _cornerSmoothness = MutableStateFlow(
        try {
            context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
                .getFloat("corner_smoothness", 0.0f)
        } catch (e: Exception) { 0.0f }
    )
    val cornerSmoothness: StateFlow<Float> = _cornerSmoothness.asStateFlow()

    fun setCornerSmoothness(value: Float) {
        _cornerSmoothness.value = value
        try {
            context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
                .edit().putFloat("corner_smoothness", value).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    val isProcessing: StateFlow<Boolean> = _globalIsProcessing.asStateFlow()
    val currentProcessingMessage: StateFlow<String> = _globalProcessingMessage.asStateFlow()
    val errorMessage: StateFlow<String?> = _globalErrorMessage.asStateFlow()

    // --- AI Chatbot States & Actions (Room Persistent) ---
    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiChatGenerating = MutableStateFlow(false)
    val isAiChatGenerating: StateFlow<Boolean> = _isAiChatGenerating.asStateFlow()

    private fun observeChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.allChatSessions.collect { sessionsWithMsgs ->
                val mappedSessions = sessionsWithMsgs.map { item ->
                    val msgs = item.messages.sortedBy { it.timestamp }.map { entity ->
                        ChatMessage(
                            id = entity.id,
                            sender = if (entity.sender == "AI") ChatSender.AI else ChatSender.USER,
                            text = entity.text,
                            timestamp = entity.timestamp
                        )
                    }
                    ChatSession(
                        id = item.session.id,
                        title = item.session.title,
                        timestamp = item.session.timestamp,
                        messages = msgs
                    )
                }
                withContext(Dispatchers.Main) {
                    _chatSessions.value = mappedSessions
                    val currentActiveId = _activeSessionId.value
                    if (currentActiveId != null) {
                        val activeSession = mappedSessions.find { it.id == currentActiveId }
                        if (activeSession != null) {
                            if (!_isAiChatGenerating.value || activeSession.messages.size >= _chatMessages.value.size) {
                                _chatMessages.value = activeSession.messages
                            }
                        }
                    }
                }
            }
        }
    }

    fun startNewChatSession() {
        _activeSessionId.value = null
        _chatMessages.value = emptyList()
    }

    fun loadChatSession(sessionId: String) {
        _activeSessionId.value = sessionId
        val session = _chatSessions.value.find { it.id == sessionId }
        if (session != null) {
            _chatMessages.value = session.messages
        }
    }

    fun deleteChatSession(sessionId: String) {
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = null
            _chatMessages.value = emptyList()
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteChatSession(sessionId)
        }
    }

    fun clearChatHistory() {
        _activeSessionId.value = null
        _chatMessages.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllChatSessions()
        }
    }

    fun sendChatMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _isAiChatGenerating.value) return

        val currentActiveId = _activeSessionId.value ?: java.util.UUID.randomUUID().toString()
        _activeSessionId.value = currentActiveId

        val firstUserMsg = _chatMessages.value.firstOrNull { it.sender == ChatSender.USER }?.text ?: trimmed
        val cleanTitle = firstUserMsg.replace(Regex("\\*+|_+|#+"), "").take(32)

        val userMessage = ChatMessage(sender = ChatSender.USER, text = trimmed)
        val currentMsgs = _chatMessages.value + userMessage
        _chatMessages.value = currentMsgs

        val sessionEntity = ChatSessionEntity(
            id = currentActiveId,
            title = cleanTitle,
            timestamp = System.currentTimeMillis()
        )
        val userMsgEntity = ChatMessageEntity(
            id = userMessage.id,
            sessionId = currentActiveId,
            sender = userMessage.sender.name,
            text = userMessage.text,
            timestamp = userMessage.timestamp
        )

        // Immediately persist user message and session atomically
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveSessionAndMessage(sessionEntity, userMsgEntity)
            } catch (e: Exception) {
                // Ignore transient DB errors
            }
        }

        _isAiChatGenerating.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentCards = cards.value
                val appContext = buildAppContextSummary(currentCards)
                val rawResponseText = GeminiService.processChat(
                    userMessage = trimmed,
                    history = currentMsgs,
                    appContext = appContext
                )
                val cleanResponseText = rawResponseText
                    .replace(Regex("(^|\\n)\\*\\s+"), "$1• ")
                    .replace(Regex("\\*\\*|\\*|_|#+"), "")
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()

                val aiMessage = ChatMessage(sender = ChatSender.AI, text = cleanResponseText)

                withContext(Dispatchers.Main) {
                    _chatMessages.value = _chatMessages.value + aiMessage
                }

                val aiMsgEntity = ChatMessageEntity(
                    id = aiMessage.id,
                    sessionId = currentActiveId,
                    sender = aiMessage.sender.name,
                    text = aiMessage.text,
                    timestamp = aiMessage.timestamp
                )
                repository.saveSessionAndMessage(
                    ChatSessionEntity(
                        id = currentActiveId,
                        title = cleanTitle,
                        timestamp = System.currentTimeMillis()
                    ),
                    aiMsgEntity
                )
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    sender = ChatSender.AI,
                    text = "Sorry, issue retrieving information: ${e.localizedMessage ?: "Network error"}. Please try again."
                )
                withContext(Dispatchers.Main) {
                    _chatMessages.value = _chatMessages.value + errorMessage
                }
                try {
                    repository.saveSessionAndMessage(
                        ChatSessionEntity(
                            id = currentActiveId,
                            title = cleanTitle,
                            timestamp = System.currentTimeMillis()
                        ),
                        ChatMessageEntity(
                            id = errorMessage.id,
                            sessionId = currentActiveId,
                            sender = errorMessage.sender.name,
                            text = errorMessage.text,
                            timestamp = errorMessage.timestamp
                        )
                    )
                } catch (dbEx: Exception) {
                    // Ignore DB exception
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isAiChatGenerating.value = false
                }
            }
        }
    }

    private fun buildAppContextSummary(cardsList: List<AlterCard>): String {
        if (cardsList.isEmpty()) return "No saved notes or memories available in the app."
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return cardsList.filter { !it.isDeleted }.take(150).joinToString("\n---\n") { card ->
            val dateStr = sdf.format(java.util.Date(card.timestamp))
            val title = if (card.processedContent.isNotBlank()) {
                card.processedContent.lines().firstOrNull { it.isNotBlank() }?.take(50) ?: "Note"
            } else card.rawInput.take(30)
            val content = if (card.processedContent.isNotBlank()) card.processedContent else card.rawInput
            val notes = if (card.personalNotes.isNotBlank()) "\n[User Note]: ${card.personalNotes}" else ""
            val canvas = if (!card.canvasData.isNullOrBlank()) "\n[Canvas Board attached]" else ""

            "[Card #${card.id}] Type: ${card.type.uppercase()} | Date: $dateStr | Title: $title\nContent:\n$content$notes$canvas"
        }
    }

    // --- Widget Interaction / Highlight state ---
    private val _highlightedCardId = MutableStateFlow<Long?>(null)
    val highlightedCardId: StateFlow<Long?> = _highlightedCardId.asStateFlow()

    private val _highlightedTaskText = MutableStateFlow<String?>(null)
    val highlightedTaskText: StateFlow<String?> = _highlightedTaskText.asStateFlow()

    private val _triggerOpenTextInput = MutableStateFlow(false)
    val triggerOpenTextInput: StateFlow<Boolean> = _triggerOpenTextInput.asStateFlow()

    fun triggerTextInputOpen() {
        _triggerOpenTextInput.value = true
    }

    fun consumeTextInputOpenTrigger() {
        _triggerOpenTextInput.value = false
    }

    private val _triggerOpenAudioInput = MutableStateFlow(false)
    val triggerOpenAudioInput: StateFlow<Boolean> = _triggerOpenAudioInput.asStateFlow()

    fun triggerAudioInputOpen() {
        _triggerOpenAudioInput.value = true
    }

    fun consumeAudioInputOpenTrigger() {
        _triggerOpenAudioInput.value = false
    }

    fun triggerHighlight(cardId: Long, taskText: String) {
        _highlightedCardId.value = cardId
        _highlightedTaskText.value = taskText
        viewModelScope.launch {
            delay(4000)
            if (_highlightedCardId.value == cardId) {
                _highlightedCardId.value = null
            }
            if (_highlightedTaskText.value == taskText) {
                _highlightedTaskText.value = null
            }
        }
    }

    // --- Attachment States ---
    private val _selectedAttachmentUri = MutableStateFlow<Uri?>(null)
    val selectedAttachmentUri: StateFlow<Uri?> = _selectedAttachmentUri.asStateFlow()

    private val _selectedAttachmentType = MutableStateFlow<String?>(null) // "image" or "pdf"
    val selectedAttachmentType: StateFlow<String?> = _selectedAttachmentType.asStateFlow()

    private val _selectedAttachmentName = MutableStateFlow<String?>(null)
    val selectedAttachmentName: StateFlow<String?> = _selectedAttachmentName.asStateFlow()

    fun selectAttachment(uri: Uri, type: String, name: String) {
        _selectedAttachmentUri.value = uri
        _selectedAttachmentType.value = type
        _selectedAttachmentName.value = name
    }

    fun clearAttachment() {
        _selectedAttachmentUri.value = null
        _selectedAttachmentType.value = null
        _selectedAttachmentName.value = null
    }

    private var timerJob: Job? = null

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun dismissError() {
        clearErrorMessage()
    }

    fun exportAlterBackup(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cardsList = repository.getAllCardsDirect()
                val rootObj = org.json.JSONObject()
                rootObj.put("app", "Alter")
                rootObj.put("version", 1)
                rootObj.put("exportTimestamp", System.currentTimeMillis())
                rootObj.put("totalCards", cardsList.size)

                val jsonArray = org.json.JSONArray()
                cardsList.forEach { card ->
                    val obj = org.json.JSONObject().apply {
                        put("id", card.id)
                        put("type", card.type)
                        put("rawInput", card.rawInput)
                        put("processedContent", card.processedContent)
                        put("timestamp", card.timestamp)
                        put("isDeleted", card.isDeleted)
                        put("deletedTimestamp", card.deletedTimestamp ?: -1L)
                        put("personalNotes", card.personalNotes)

                        if (!card.mediaPath.isNullOrBlank()) {
                            val mediaFile = java.io.File(card.mediaPath)
                            if (mediaFile.exists()) {
                                val bytes = mediaFile.readBytes()
                                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                put("mediaBase64", base64)
                                put("mediaFileName", mediaFile.name)
                            }
                        }
                    }
                    jsonArray.put(obj)
                }
                rootObj.put("cards", jsonArray)

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(rootObj.toString(2).toByteArray(kotlin.text.Charsets.UTF_8))
                    output.flush()
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export successful! (${cardsList.size} notes & audio files saved)", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun shareBackupFileToDrive(ctx: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cardsList = repository.getAllCardsDirect()
                val rootObj = org.json.JSONObject()
                rootObj.put("app", "Alter")
                rootObj.put("version", 1)
                rootObj.put("exportTimestamp", System.currentTimeMillis())
                rootObj.put("totalCards", cardsList.size)

                val jsonArray = org.json.JSONArray()
                cardsList.forEach { card ->
                    val obj = org.json.JSONObject().apply {
                        put("id", card.id)
                        put("type", card.type)
                        put("rawInput", card.rawInput)
                        put("processedContent", card.processedContent)
                        put("timestamp", card.timestamp)
                        put("isDeleted", card.isDeleted)
                        put("deletedTimestamp", card.deletedTimestamp ?: -1L)
                        put("personalNotes", card.personalNotes)

                        if (!card.mediaPath.isNullOrBlank()) {
                            val mediaFile = java.io.File(card.mediaPath)
                            if (mediaFile.exists()) {
                                val bytes = mediaFile.readBytes()
                                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                put("mediaBase64", base64)
                                put("mediaFileName", mediaFile.name)
                            }
                        }
                    }
                    jsonArray.put(obj)
                }
                rootObj.put("cards", jsonArray)

                val df = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
                val time = df.format(java.util.Date())
                val tempFile = java.io.File(ctx.cacheDir, "alter_backup_$time.alter.json")
                tempFile.writeText(rootObj.toString(2), kotlin.text.Charsets.UTF_8)

                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    tempFile
                )

                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Alter Backup - $time")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val chooser = android.content.Intent.createChooser(intent, "Export to Drive or Share")
                    chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(chooser)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(ctx, "Export to Drive failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importAlterBackup(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(kotlin.text.Charsets.UTF_8).readText()
                } ?: throw IllegalStateException("Could not read selected file.")

                var importedCount = 0
                val trimmed = content.trim()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    val jsonArray = if (trimmed.startsWith("{")) {
                        val root = org.json.JSONObject(trimmed)
                        if (root.has("cards")) root.getJSONArray("cards") else org.json.JSONArray()
                    } else {
                        org.json.JSONArray(trimmed)
                    }

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val type = obj.optString("type", "text")
                        val rawInput = obj.optString("rawInput", "")
                        val processedContent = obj.optString("processedContent", "")
                        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        val isDeleted = obj.optBoolean("isDeleted", false)
                        val delTimestamp = if (obj.has("deletedTimestamp") && obj.getLong("deletedTimestamp") > 0) obj.getLong("deletedTimestamp") else null
                        val personalNotes = obj.optString("personalNotes", "")

                        var restoredMediaPath: String? = null
                        if (obj.has("mediaBase64")) {
                            val base64Str = obj.getString("mediaBase64")
                            if (base64Str.isNotBlank()) {
                                val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                                val fileName = obj.optString("mediaFileName", "alter_media_${timestamp}.mp4")
                                val mediaFile = java.io.File(context.filesDir, fileName)
                                mediaFile.writeBytes(bytes)
                                restoredMediaPath = mediaFile.absolutePath
                            }
                        }

                        val card = AlterCard(
                            type = type,
                            rawInput = rawInput,
                            processedContent = processedContent,
                            timestamp = timestamp,
                            mediaPath = restoredMediaPath,
                            isDeleted = isDeleted,
                            deletedTimestamp = delTimestamp,
                            personalNotes = personalNotes
                        )
                        repository.insertCard(card)
                        importedCount++
                    }

                    notifyWidgetUpdate()

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Successfully imported $importedCount items (all notes, audio & text)!", android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    restoreDatabase(uri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    restoreDatabase(uri)
                } catch (ex: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Import failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun backupDatabase(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val database = com.example.data.AlterDatabase.getDatabase(context)
                // Force a full WAL checkpoint to commit all data into the main file
                database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                val dbFile = context.getDatabasePath("alter_database")
                if (dbFile.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        dbFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Backup successful!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Backup failed: database file not found.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Backup failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportToJSON(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cardsList = repository.getAllCardsDirect()
                val jsonArray = org.json.JSONArray()
                
                cardsList.forEach { card ->
                    val obj = org.json.JSONObject().apply {
                        put("id", card.id)
                        put("type", card.type)
                        put("rawInput", card.rawInput)
                        put("processedContent", card.processedContent)
                        put("timestamp", card.timestamp)
                        put("mediaPath", card.mediaPath ?: "")
                        put("isDeleted", card.isDeleted)
                    }
                    jsonArray.put(obj)
                }
                
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(jsonArray.toString(4).toByteArray(kotlin.text.Charsets.UTF_8))
                    output.flush()
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "JSON Export successful! (${cardsList.size} items)", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "JSON Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportToText(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cardsList = repository.getAllCardsDirect()
                val sb = java.lang.StringBuilder()
                sb.append("# ALTER NOTES & THOUGHTS BACKUP\n")
                sb.append("Generated on: ${java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
                sb.append("Total Cards: ${cardsList.size}\n")
                sb.append("=========================================\n\n")
                
                cardsList.forEach { card ->
                    val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault())
                    val dateStr = sdf.format(java.util.Date(card.timestamp))
                    
                    sb.append("## [${card.type.uppercase()}] - $dateStr\n")
                    if (card.rawInput.isNotBlank()) {
                        sb.append("**Raw Input:**\n${card.rawInput}\n\n")
                    }
                    sb.append("**Processed Content:**\n${card.processedContent}\n")
                    if (!card.mediaPath.isNullOrBlank()) {
                        sb.append("**Media File:** ${card.mediaPath}\n")
                    }
                    sb.append("\n-----------------------------------------\n\n")
                }
                
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(sb.toString().toByteArray(kotlin.text.Charsets.UTF_8))
                    output.flush()
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Text Export successful! (${cardsList.size} items)", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Text Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun restoreDatabase(uri: Uri) {
        viewModelScope.launch {
            try {
                val database = com.example.data.AlterDatabase.getDatabase(context)
                database.close()
                val dbFile = context.getDatabasePath("alter_database")
                val walFile = context.getDatabasePath("alter_database-wal")
                val shmFile = context.getDatabasePath("alter_database-shm")
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()
                
                android.widget.Toast.makeText(context, "Restore successful. Restarting app...", android.widget.Toast.LENGTH_LONG).show()
                kotlinx.coroutines.delay(1000)
                
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Runtime.getRuntime().exit(0)
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Restore failed.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Audio Recording Logic ---

    fun discardAudioRecording() {
        stopTimer()
        _isRecording.value = false
        _recordingDurationSeconds.value = 0
        try {
            val audioFile = audioRecorder.stopRecording()
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleAudioRecording() {
        if (_isRecording.value) {
            stopAndProcessAudio()
        } else {
            startAudioRecording()
        }
    }

    private fun startAudioRecording() {
        clearErrorMessage()
        val file = audioRecorder.startRecording()
        if (file != null) {
            _isRecording.value = true
            _recordingDurationSeconds.value = 0
            startTimer()
        } else {
            setError("Failed to initiate microphone. Please grant RECORD_AUDIO permissions.")
        }
    }

    private fun stopAndProcessAudio() {
        stopTimer()
        _isRecording.value = false
        val audioFile = audioRecorder.stopRecording()
        if (audioFile != null && audioFile.exists()) {
            if (_recordingDurationSeconds.value < 2) {
                setError("Voice recording too short. Try speaking for longer.")
                try { audioFile.delete() } catch (ignored: Exception) {}
            } else {
                processRecordedAudioFile(audioFile)
            }
        } else {
            setError("Recorded voice session was empty or failed.")
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingDurationSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun processRecordedAudioFile(file: File) {
        bgScope.launch {
            _globalIsProcessing.value = true
            _globalProcessingMessage.value = "Uploading voice memo and filtering slop..."
            clearErrorMessage()

            try {
                val bytes = file.readBytes()
                if (bytes.isEmpty()) {
                    _globalProcessingMessage.value = ""
                    setError("Audio recording was empty. Please check microphone permissions and try again.")
                    _globalIsProcessing.value = false
                    return@launch
                }
                val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                // Copy temporary file to a persistent folder so the user can listen later!
                val audioDir = File(context.filesDir, "alter_audio")
                if (!audioDir.exists()) audioDir.mkdirs()
                val persistentAudioFile = File(audioDir, "audio_${System.currentTimeMillis()}.aac")
                file.copyTo(persistentAudioFile, overwrite = true)

                // Let's pass the raw audio to the Gemini API Slop filter
                val cleanedText = GeminiService.processAudio(base64Audio, "audio/aac")
                
                val trimmedText = cleanedText.trim()
                val isNoSpeech = trimmedText.contains("[NO_SPEECH_DETECTED]", ignoreCase = true) ||
                                 trimmedText.equals("no input", ignoreCase = true) ||
                                 trimmedText.equals("no speech detected", ignoreCase = true) ||
                                 trimmedText.equals("silence", ignoreCase = true) ||
                                 trimmedText.equals("[silence]", ignoreCase = true) ||
                                 trimmedText.isEmpty()

                val isNetworkErr = cleanedText.startsWith("Error:") || 
                                   cleanedText.startsWith("Network Exception:") || 
                                   cleanedText.contains("parts' missing") || 
                                   cleanedText.contains("candidates[0]")

                if (isNoSpeech) {
                    setError("Nothing detected in this audio. Please speak clearly and try again.")
                    try { persistentAudioFile.delete() } catch (ignored: Exception) {}
                } else if (isNetworkErr) {
                    setError(cleanedText)
                    try { persistentAudioFile.delete() } catch (ignored: Exception) {}
                } else {
                    // Create and insert card in database
                    val card = AlterCard(
                        type = "audio",
                        rawInput = "Voice Note (${_recordingDurationSeconds.value}s)",
                        processedContent = cleanedText,
                        mediaPath = persistentAudioFile.absolutePath
                    )
                    repository.insertCard(card)
                    notifyWidgetUpdate()
                }
            } catch (e: Exception) {
                setError("Failed to process audio: ${e.localizedMessage}")
            } finally {
                _globalIsProcessing.value = false
                _globalProcessingMessage.value = ""
                // Clean up voice file
                try { file.delete() } catch (ignored: Exception) {}
            }
        }
    }

    // --- Direct Text Processing ---

    fun processTextInput() {
        val currentText = _inputText.value.trim()
        val attachmentUri = _selectedAttachmentUri.value
        val attachmentType = _selectedAttachmentType.value
        val attachmentName = _selectedAttachmentName.value

        if (currentText.isEmpty() && attachmentUri == null) return

        bgScope.launch {
            _globalIsProcessing.value = true
            _globalProcessingMessage.value = "Synthesizing raw thoughts with attachments..."
            clearErrorMessage()

            try {
                if (attachmentUri != null) {
                    val bytes = context.contentResolver.openInputStream(attachmentUri)?.readBytes()
                    if (bytes != null) {
                        if (attachmentType == "pdf" || attachmentType?.contains("pdf") == true) {
                            // Save original PDF to sandbox permanent file
                            val docDir = File(context.filesDir, "alter_docs")
                            if (!docDir.exists()) docDir.mkdirs()
                            val persistentDocFile = File(docDir, "doc_${System.currentTimeMillis()}.pdf")
                            persistentDocFile.writeBytes(bytes)

                            // Render page 1 to Bitmap
                            var pdfBase64Image: String? = null
                            try {
                                val parcelFileDescriptor = context.contentResolver.openFileDescriptor(attachmentUri, "r")
                                if (parcelFileDescriptor != null) {
                                    val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
                                    if (pdfRenderer.pageCount > 0) {
                                        val page = pdfRenderer.openPage(0)
                                        val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        page.close()

                                        val bos = java.io.ByteArrayOutputStream()
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, bos)
                                        pdfBase64Image = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                                    }
                                    pdfRenderer.close()
                                    parcelFileDescriptor.close()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            val prompt = if (currentText.isNotEmpty()) {
                                "Based on first page of PDF: \"$attachmentName\" and user thought: \"$currentText\", process and structure content in clean Markdown. CRITICAL REQUIREMENT: Do NOT create checkable checkbox tasks (`- [ ]`) unless they explicitly have dates and times associated with them. Otherwise, use plain bullet points (`- `)."
                            } else {
                                "Deconstruct the attached PDF document: \"$attachmentName\" and extract its key sections, goals, and details. CRITICAL REQUIREMENT: Do NOT create checkable checkbox tasks (`- [ ]`) unless they explicitly have dates and times associated with them. Otherwise, use plain bullet points (`- `)."
                            }

                            val cleanedText = if (pdfBase64Image != null) {
                                GeminiService.processImage(pdfBase64Image, "image/png", prompt)
                            } else {
                                GeminiService.processText(prompt + "\n\n(Note: PDF rendering fell back to metadata parsing due to a loading error.)")
                            }

                            if (cleanedText.startsWith("Error:") || cleanedText.startsWith("Network Exception:")) {
                                setError(cleanedText)
                            } else {
                                val card = AlterCard(
                                    type = "pdf",
                                    rawInput = if (currentText.isNotEmpty()) "PDF: $attachmentName ($currentText)" else "PDF: $attachmentName",
                                    processedContent = cleanedText,
                                    mediaPath = persistentDocFile.absolutePath
                                )
                                repository.insertCard(card)
                                notifyWidgetUpdate()
                                _inputText.value = ""
                                clearAttachment()
                            }
                        } else {
                            // Image attachment
                            val imageDir = File(context.filesDir, "alter_images")
                            if (!imageDir.exists()) imageDir.mkdirs()
                            val persistentImageFile = File(imageDir, "img_${System.currentTimeMillis()}.jpg")
                            persistentImageFile.writeBytes(bytes)

                            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            val rawMimeType = context.contentResolver.getType(attachmentUri) ?: "image/jpeg"
                            val mimeType = if (rawMimeType.contains("/")) rawMimeType else "image/jpeg"
                            val prompt = if (currentText.isNotEmpty()) {
                                "Extract text and goals from this attachment according to user comments: \"$currentText\". Format nicely using clean Markdown. CRITICAL REQUIREMENT: Do NOT create checkable checkbox tasks (`- [ ]`) unless they explicitly have dates and times associated with them. Otherwise, use plain bullet points (`- `)."
                            } else {
                                "Analyze this image and list key details, items, or checklists in clean formatted Markdown. CRITICAL REQUIREMENT: Do NOT create checkable checkbox tasks (`- [ ]`) unless they explicitly have dates and times associated with them. Otherwise, use plain bullet points (`- `)."
                            }

                            val cleanedText = GeminiService.processImage(base64Image, mimeType, prompt)

                            if (cleanedText.startsWith("Error:") || cleanedText.startsWith("Network Exception:")) {
                                setError(cleanedText)
                            } else {
                                val card = AlterCard(
                                    type = "image",
                                    rawInput = if (currentText.isNotEmpty()) "Attached Image comment: \"$currentText\"" else "Attached Image: $attachmentName",
                                    processedContent = cleanedText,
                                    mediaPath = persistentImageFile.absolutePath
                                )
                                repository.insertCard(card)
                                notifyWidgetUpdate()
                                _inputText.value = ""
                                clearAttachment()
                            }
                        }
                    } else {
                        setError("Failed to read attachment bytes.")
                    }
                } else {
                    // standard direct text with no attachment
                    val cleanedText = GeminiService.processText(currentText)
                    if (cleanedText.startsWith("Error:") || cleanedText.startsWith("Network Exception:")) {
                        setError(cleanedText)
                    } else {
                        val card = AlterCard(
                            type = "text",
                            rawInput = currentText,
                            processedContent = cleanedText
                        )
                        repository.insertCard(card)
                        notifyWidgetUpdate()
                        _inputText.value = ""
                    }
                }
            } catch (e: Exception) {
                setError("Processing failed: ${e.localizedMessage}")
            } finally {
                _globalIsProcessing.value = false
                _globalProcessingMessage.value = ""
            }
        }
    }

    // --- Handle Shared Intent Streams (Text & Image) ---

    fun handleSharedText(text: String) {
        _inputText.value = text
        // Automatically request formatting
        bgScope.launch {
            _globalIsProcessing.value = true
            _globalProcessingMessage.value = "Processing shared text clipping..."
            clearErrorMessage()

            val cleanedText = GeminiService.processText(text)

            if (cleanedText.startsWith("Error:") || cleanedText.startsWith("Network Exception:")) {
                setError(cleanedText)
            } else {
                val card = AlterCard(
                    type = "text",
                    rawInput = text,
                    processedContent = cleanedText
                )
                repository.insertCard(card)
                notifyWidgetUpdate()
                _inputText.value = "" // clear
            }
            _globalIsProcessing.value = false
            _globalProcessingMessage.value = ""
        }
    }

    // --- Shared Image Staging and Custom Instructions System ---

    private val _pendingSharedImageUri = MutableStateFlow<Uri?>(null)
    val pendingSharedImageUri: StateFlow<Uri?> = _pendingSharedImageUri.asStateFlow()

    private val _pendingSharedImageInstruction = MutableStateFlow("")
    val pendingSharedImageInstruction: StateFlow<String> = _pendingSharedImageInstruction.asStateFlow()

    fun updatePendingImageInstruction(text: String) {
        _pendingSharedImageInstruction.value = text
    }

    fun cancelPendingImage() {
        _pendingSharedImageUri.value = null
        _pendingSharedImageInstruction.value = ""
    }

    fun handleSharedImage(uri: Uri) {
        _pendingSharedImageUri.value = uri
        _pendingSharedImageInstruction.value = ""
    }

    fun processStagedImage() {
        val uri = _pendingSharedImageUri.value ?: return
        val instruction = _pendingSharedImageInstruction.value.trim()

        bgScope.launch {
            _globalIsProcessing.value = true
            _globalProcessingMessage.value = "Ingesting screenshot & applying instruction..."
            clearErrorMessage()

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    
                    // Copy to sandbox permanent files directory
                    val imageDir = File(context.filesDir, "alter_images")
                    if (!imageDir.exists()) imageDir.mkdirs()
                    val persistentImageFile = File(imageDir, "img_${System.currentTimeMillis()}.jpg")
                    persistentImageFile.writeBytes(bytes)

                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val userPrompt = if (instruction.isNotEmpty()) {
                        "Extract text and goals from this screenshot image according to user instructions: \"$instruction\". Format content nicely using clean structured Markdown. CRITICAL REQUIREMENT: Do NOT create checkable checkbox tasks (`- [ ]`) unless they explicitly have dates and times associated with them. Otherwise, use plain bullet points (`- `)."
                    } else {
                        "Extract text and structured goals from this screenshot image. Filter any noise/ads, structure the core information using clean Markdown, and format it nicely. CRITICAL REQUIREMENT: Do NOT create checkable checkbox tasks (`- [ ]`) unless they explicitly have dates and times associated with them. Otherwise, use plain bullet points (`- `)."
                    }

                    val cleanedText = GeminiService.processImage(base64Image, mimeType, userPrompt)

                    if (cleanedText.startsWith("Error:") || cleanedText.startsWith("Network Exception:")) {
                        setError(cleanedText)
                    } else {
                        val card = AlterCard(
                           type = "image",
                           rawInput = if (instruction.isNotEmpty()) "Screenshot with note: \"$instruction\"" else "Shared Screenshot",
                           processedContent = cleanedText,
                           mediaPath = persistentImageFile.absolutePath
                        )
                        repository.insertCard(card)
                        notifyWidgetUpdate()
                        cancelPendingImage() // reset staging on success
                    }
                } else {
                    setError("Could not read bytes from the shared image.")
                }
            } catch (e: Exception) {
                setError("Image processing failed: ${e.localizedMessage}")
            } finally {
                _globalIsProcessing.value = false
                _globalProcessingMessage.value = ""
            }
        }
    }

    // --- Add to Google Calendar Schedule Analysis Flow ---

    fun analyzeAndCreateCalendarEvent(
        card: AlterCard,
        onResult: (title: String, startTime: Long, endTime: Long, desc: String) -> Unit
    ) {
        bgScope.launch {
            _globalIsProcessing.value = true
            _globalProcessingMessage.value = "Analyzing text to extract schedule/event details..."
            clearErrorMessage()

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val currentFormatted = sdf.format(java.util.Date())

            val parsePrompt = """
                Extract a single calendar event from the following document content.
                Current System DateTime is: $currentFormatted (use this to calculate relative dates like 'tomorrow at noon', 'this Friday', etc.).
                
                Document content:
                ---
                ${card.processedContent}
                ---

                Respond ONLY with a valid JSON block enclosing these exact string fields (DO NOT add markdown code ticks like ```json, just output the raw bracketed JSON string):
                {
                  "title": "Title of event",
                  "startTime": "YYYY-MM-DDTHH:mm:ss",
                  "endTime": "YYYY-MM-DDTHH:mm:ss",
                  "description": "Brief description"
                }
            """.trimIndent()

            try {
                val response = GeminiService.processText(parsePrompt)
                val json = response.trim().removeSurrounding("```json", "```").trim()
                
                val moshi = com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                val adapter = moshi.adapter(CalendarEventJson::class.java)
                val event = adapter.fromJson(json)
                
                if (event != null) {
                    val startMs = try { sdf.parse(event.startTime)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                    val endMs = try { sdf.parse(event.endTime)?.time ?: (startMs + 60 * 60 * 1000) } catch (e: Exception) { startMs + 60 * 60 * 1000 }
                    onResult(event.title, startMs, endMs, event.description)
                } else {
                    setError("Could not parse event json details.")
                }
            } catch (e: Exception) {
                setError("Calendar analysis failed: ${e.localizedMessage}")
            } finally {
                _globalIsProcessing.value = false
                _globalProcessingMessage.value = ""
            }
        }
    }

    // --- Card Updates (e.g. checkbox state toggles in Memories screen) ---

    fun reprocessCard(card: AlterCard, newRawInput: String, onComplete: () -> Unit = {}) {
        bgScope.launch {
            _globalIsProcessing.value = true
            _globalProcessingMessage.value = "Re-analyzing updated slate..."
            clearErrorMessage()
            try {
                val cleanedText = when (card.type) {
                    "text" -> GeminiService.processText(newRawInput)
                    "image" -> {
                        val mediaPath = card.mediaPath
                        if (mediaPath != null && File(mediaPath).exists()) {
                            val bytes = File(mediaPath).readBytes()
                            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            val mimeType = "image/jpeg"
                            
                            val instruction = if (newRawInput.startsWith("Screenshot with note: \"") && newRawInput.endsWith("\"")) {
                                newRawInput.substringAfter("Screenshot with note: \"").substringBeforeLast("\"")
                            } else {
                                newRawInput
                            }
                            
                            val userPrompt = if (instruction.isNotEmpty() && instruction != "Shared Screenshot") {
                                "Extract text and goals from this screenshot image according to user instructions: \"$instruction\". Format content nicely using clean structured Markdown. CRITICAL REQUIREMENT: Do NOT create checkable checkbox tasks (`- [ ]`) unless they explicitly have dates and times associated with them. Otherwise, use plain bullet points (`- `)."
                            } else {
                                "Extract text and structured goals from this screenshot image. Filter any noise/ads, structure the core information using clean Markdown, and format it nicely. CRITICAL REQUIREMENT: Do NOT create checkable checkbox tasks (`- [ ]`) unless they explicitly have dates and times associated with them. Otherwise, use plain bullet points (`- `)."
                            }
                            GeminiService.processImage(base64Image, mimeType, userPrompt)
                        } else {
                            GeminiService.processText(newRawInput)
                        }
                    }
                    "audio" -> {
                        // Keep audio file but let them edit the prompt description or text transcript
                        GeminiService.processText(newRawInput)
                    }
                    else -> GeminiService.processText(newRawInput)
                }

                if (cleanedText.startsWith("Error:") || cleanedText.startsWith("Network Exception:")) {
                    setError(cleanedText)
                } else {
                    val updatedCard = card.copy(
                        rawInput = newRawInput,
                        processedContent = cleanedText
                    )
                    repository.insertCard(updatedCard)
                    notifyWidgetUpdate()
                    onComplete()
                }
            } catch (e: Exception) {
                setError("Failed to re-process slate: ${e.localizedMessage}")
            } finally {
                _globalIsProcessing.value = false
                _globalProcessingMessage.value = ""
            }
        }
    }

    fun updateCard(card: AlterCard) {
        viewModelScope.launch {
            repository.insertCard(card)
            notifyWidgetUpdate()
        }
    }

    // --- Database Modification ---

    fun deleteCard(card: AlterCard) {
        // By default, delete card acts as soft-delete to put it in the Bin
        softDeleteCard(card)
    }

    fun softDeleteCard(card: AlterCard) {
        viewModelScope.launch {
            val softDeleted = card.copy(
                isDeleted = true,
                deletedTimestamp = System.currentTimeMillis()
            )
            repository.insertCard(softDeleted)
            cancelAllTasksAlarmsForCard(card)
            notifyWidgetUpdate()
        }
    }

    fun restoreCard(card: AlterCard) {
        viewModelScope.launch {
            val restored = card.copy(
                isDeleted = false,
                deletedTimestamp = null
            )
            repository.insertCard(restored)
            // Note: Does not automatically recreate alarms upon restore as requested times may have passed
            notifyWidgetUpdate()
        }
    }

    fun permanentlyDeleteCard(card: AlterCard) {
        viewModelScope.launch {
            cancelAllTasksAlarmsForCard(card)
            repository.deleteCard(card)
            notifyWidgetUpdate()
        }
    }

    private fun cancelAllTasksAlarmsForCard(card: AlterCard) {
        val todoRegex = Regex("^-\\s*\\[([ xX]?)\\]\\s*(.*)\$", RegexOption.MULTILINE)
        val lines = card.processedContent.split("\n")
        lines.forEach { line ->
            val match = todoRegex.find(line.trim())
            if (match != null) {
                val taskText = match.groupValues[2].trim()
                if (taskText.isNotEmpty()) {
                     cancelTodoAlarm(card.id, taskText)
                }
            }
        }
    }

    fun insertOfflineNote(title: String, content: String) {
        viewModelScope.launch {
            repository.insertCard(
                AlterCard(
                    type = "note",
                    rawInput = title,
                    processedContent = content,
                    timestamp = System.currentTimeMillis()
                )
            )
            notifyWidgetUpdate()
        }
    }

    fun insertUpcomingTask(taskText: String) {
        viewModelScope.launch {
            val lines = taskText.split("\n")
            val tasksToAdd = mutableListOf<String>()
            for (line in lines) {
                val cleaned = line.trim()
                if (cleaned.isEmpty()) continue
                
                var clean = cleaned
                // Clean markdown task prefixes
                if (clean.startsWith("- [ ] ")) clean = clean.substring(6)
                else if (clean.startsWith("* [ ] ")) clean = clean.substring(6)
                else if (clean.startsWith("- [] ")) clean = clean.substring(5)
                else if (clean.startsWith("* [] ")) clean = clean.substring(5)
                else if (clean.startsWith("- [x] ", ignoreCase = true)) clean = clean.substring(6)
                else if (clean.startsWith("* [x] ", ignoreCase = true)) clean = clean.substring(6)
                // Clean standard bullets/pointers
                else if (clean.startsWith("- ")) clean = clean.substring(2)
                else if (clean.startsWith("* ")) clean = clean.substring(2)
                else if (clean.startsWith("• ")) clean = clean.substring(2)
                else if (clean.startsWith("+ ")) clean = clean.substring(2)
                
                val finalClean = clean.trim()
                if (finalClean.isNotEmpty()) {
                    tasksToAdd.add(finalClean)
                }
            }
            if (tasksToAdd.isEmpty() && taskText.trim().isNotEmpty()) {
                tasksToAdd.add(taskText.trim())
            }
            for (task in tasksToAdd) {
                repository.insertCard(
                    AlterCard(
                        type = "upcoming",
                        rawInput = task,
                        processedContent = "☐ $task",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            notifyWidgetUpdate()
        }
    }

    fun runAutoPurge() {
        viewModelScope.launch {
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L)
            repository.permanentlyDeleteOldCards(sevenDaysAgo)
            notifyWidgetUpdate()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAllCards()
            notifyWidgetUpdate()
        }
    }

    // --- Todo Alarm Notifications ---

    fun scheduleTodoAlarm(cardId: Long, taskText: String, timeInMillis: Long) {
        val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
        val key = "reminder_${cardId}_${taskText}"
        prefs.edit().putLong(key, timeInMillis).apply()
        _todoRemindersMap.value = _todoRemindersMap.value + (key to timeInMillis)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            putExtra("taskText", taskText)
            putExtra("cardId", cardId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (cardId.toInt() * 31 + taskText.hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        android.app.AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        android.app.AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                }
            }
        } catch (e: SecurityException) {
            // Fallback in case of unexpected exact alarm security restrictions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            }
        }
    }

    fun getTodoAlarmTime(cardId: Long, taskText: String): Long {
        return _todoRemindersMap.value["reminder_${cardId}_${taskText}"] ?: 0L
    }

    fun cancelTodoAlarm(cardId: Long, taskText: String) {
        val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
        val key = "reminder_${cardId}_${taskText}"
        prefs.edit().putLong(key, -1L).apply()
        _todoRemindersMap.value = _todoRemindersMap.value + (key to -1L)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, com.example.TodoReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (cardId.toInt() * 31 + taskText.hashCode()),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    // --- Infinite Dotted Canvas Board Support ---

    private val syncManager = com.example.data.GoogleSyncManager.getInstance(context)
    val currentUser: StateFlow<com.example.data.GoogleUser?> = syncManager.currentUser
    val isOnboardingCompleted: StateFlow<Boolean> = syncManager.isOnboardingCompleted
    val syncStatus: StateFlow<String> = syncManager.syncStatus

    fun completeOnboarding(user: com.example.data.GoogleUser? = null) {
        syncManager.completeOnboarding(user)
    }

    fun signInGoogle(user: com.example.data.GoogleUser) {
        syncManager.signIn(user)
    }

    fun signOutGoogle() {
        syncManager.signOut()
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            delay(400)
            syncManager.markSyncCompleted()
        }
    }

    fun exportUniversalSyncJson(): String {
        return syncManager.createUniversalSyncBundle(cards.value)
    }

    fun saveCanvasBoard(cardId: Long, canvasState: com.example.data.CanvasBoardState, currentCardFallback: AlterCard? = null) {
        viewModelScope.launch {
            val existing = cards.value.find { it.id == cardId } ?: currentCardFallback ?: return@launch
            val canvasJson = canvasState.toJson()
            // Extract primary text for preview if present
            val primaryText = canvasState.textBlocks.firstOrNull { it.isPrimaryNote }?.text
                ?: canvasState.textBlocks.firstOrNull()?.text
                ?: ""

            val updated = existing.copy(
                canvasData = canvasJson,
                processedContent = primaryText
            )
            repository.insertCard(updated)
            syncManager.markSyncCompleted()
        }
    }

    fun createCanvasNote(
        title: String,
        initialContent: String = "",
        onCreated: (AlterCard) -> Unit = {}
    ) {
        viewModelScope.launch {
            val initialState = com.example.data.CanvasBoardState.fromJson(
                null,
                initialNoteContent = if (initialContent.isNotBlank()) initialContent else null
            )
            val newCard = AlterCard(
                type = "note",
                rawInput = title.ifBlank { "Untitled Canvas" },
                processedContent = initialContent,
                timestamp = System.currentTimeMillis(),
                canvasData = initialState.toJson()
            )
            val newId = repository.insertCard(newCard)
            val savedCard = newCard.copy(id = newId)
            notifyWidgetUpdate()
            syncManager.markSyncCompleted()
            onCreated(savedCard)
        }
    }
}

// Simple Factory for creating ViewModel initialized with Repository & Context dependencies
class AlterViewModelFactory(
    private val repository: AlterRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlterViewModel::class.java)) {
            return AlterViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class CalendarEventJson(
    val title: String,
    val startTime: String,
    val endTime: String,
    val description: String
)
