@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily as ActualFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.AlterCard
import com.example.viewmodel.AlterViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.os.Build

// ==================== MULTI-TAB & COLUMNS HELPER UTILITIES ====================

@Composable
fun getCornerShape(baseRadiusDp: Float): androidx.compose.ui.graphics.Shape {
    return RoundedCornerShape(baseRadiusDp.dp)
}

enum class ActiveLoggingMode {
    NONE, TEXT, AUDIO, SCAN, ATTACH, NOTE, UPCOMING
}

fun isCardLong(card: AlterCard): Boolean {
    val processedLen = card.processedContent.length
    val rawLen = card.rawInput.length
    val lineCount = card.processedContent.count { it == '\n' } + 1
    // Dynamic threshold: if the text exceeds 160 chars, has multiple lines, or is a PDF file, make it single full-width.
    return (processedLen + rawLen) > 160 || lineCount > 4 || card.type == "pdf"
}

class GroupPartitioner {
    sealed class GridRow {
        data class SingleFullWidth(val card: AlterCard) : GridRow()
        data class PairHalfWidth(val left: AlterCard, val right: AlterCard) : GridRow()
        data class SingleHalfWidth(val card: AlterCard) : GridRow()
    }

    fun partition(cards: List<AlterCard>): List<GridRow> {
        val result = mutableListOf<GridRow>()
        var i = 0
        while (i < cards.size) {
            val card = cards[i]
            val isFull = isCardLong(card)
            if (isFull) {
                result.add(GridRow.SingleFullWidth(card))
                i++
            } else {
                if (i + 1 < cards.size) {
                    val next = cards[i + 1]
                    val nextIsFull = isCardLong(next)
                    if (!nextIsFull) {
                        result.add(GridRow.PairHalfWidth(card, next))
                        i += 2
                    } else {
                        result.add(GridRow.SingleHalfWidth(card))
                        i++
                    }
                } else {
                    result.add(GridRow.SingleHalfWidth(card))
                    i++
                }
            }
        }
        return result
    }
}

@Composable
fun AlterCardItemWrapper(
    card: AlterCard,
    highlightedCardId: Long?,
    activePlayingPath: String?,
    isAudioPlaying: Boolean,
    onPlayPauseAudio: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onDelete: (AlterCard) -> Unit,
    onEdit: (AlterCard) -> Unit,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context,
    viewModel: AlterViewModel,
    todoRemindersMap: Map<String, Long>,
    onHapticClick: () -> Unit,
    onCardClick: (AlterCard) -> Unit,
    showDateInsteadOfTime: Boolean = false
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(240)) + 
                androidx.compose.animation.scaleIn(initialScale = 0.96f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)),
        exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) + 
                androidx.compose.animation.shrinkVertically()
    ) {
        val isHighlighted = card.id == highlightedCardId
        AlterCardItem(
            card = card,
            isHighlighted = isHighlighted,
            activePlayingPath = activePlayingPath,
            isAudioPlaying = isAudioPlaying,
            showDateInsteadOfTime = showDateInsteadOfTime,
            onPlayPauseAudio = onPlayPauseAudio,
            onOpenImage = onOpenImage,
            onDelete = { onDelete(card) },
            onEdit = { onEdit(card) },
            onCopy = {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(card.processedContent))
            },
            onShare = {
                val cleanedText = card.processedContent.replace("*", "")
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, cleanedText)
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Slate")
                context.startActivity(shareIntent)
            },
            onAddCalendar = {
                viewModel.analyzeAndCreateCalendarEvent(card) { title, startMs, endMs, desc ->
                    val calIntent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                        data = android.provider.CalendarContract.Events.CONTENT_URI
                        putExtra(android.provider.CalendarContract.Events.TITLE, title)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, endMs)
                        putExtra(android.provider.CalendarContract.Events.DESCRIPTION, desc)
                        putExtra(android.provider.CalendarContract.Events.ACCESS_LEVEL, android.provider.CalendarContract.Events.ACCESS_PRIVATE)
                        putExtra(android.provider.CalendarContract.Events.AVAILABILITY, android.provider.CalendarContract.Events.AVAILABILITY_BUSY)
                    }
                    context.startActivity(calIntent)
                }
            },
            onUpdateCard = { updatedCard -> viewModel.updateCard(updatedCard) },
            onHapticClick = onHapticClick,
            todoRemindersMap = todoRemindersMap,
            onCardClick = { onCardClick(card) }
        )
    }
}

object FontFamily {
    val SansSerif = ActualFontFamily.Default
    val Monospace = ActualFontFamily.Default
    val Default = ActualFontFamily.Default
}

enum class AppTab {
    Home, AiChat, Memories, Upcoming, Notes, Settings
}

data class ParsedTodoItem(
    val isTodo: Boolean,
    val isCompleted: Boolean,
    val taskText: String,
    val rawLine: String
)

fun parseTodoLine(line: String): ParsedTodoItem? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null

    // Match all checkbox patterns: - [ ], * [ ], + [ ], 1. [ ], • [ ], [ ], - [x], * [x], + [x], [x], [X], - [v], - [✓], - []
    val bracketRegex = Regex("^(?:[-*+•]|\\d+[.)])?\\s*\\[([ xXvV✓\\-]?)\\]\\s*(.*)\$")
    val match = bracketRegex.find(trimmed)
    if (match != null) {
        val checkChar = match.groupValues[1].trim().lowercase()
        val isCompleted = checkChar == "x" || checkChar == "v" || checkChar == "✓"
        val rawTaskText = match.groupValues[2].trim()
        val cleaned = cleanLinePrefix(rawTaskText)
        return ParsedTodoItem(
            isTodo = true,
            isCompleted = isCompleted,
            taskText = cleaned,
            rawLine = line
        )
    }

    // Match Unicode checkboxes: ☐ (unchecked), ☑ / ☒ (checked)
    if (trimmed.startsWith("☐") || trimmed.startsWith("☑") || trimmed.startsWith("☒")) {
        val isCompleted = trimmed.startsWith("☑") || trimmed.startsWith("☒")
        val rawTaskText = trimmed.drop(1).trim()
        val cleaned = cleanLinePrefix(rawTaskText)
        return ParsedTodoItem(
            isTodo = true,
            isCompleted = isCompleted,
            taskText = cleaned,
            rawLine = line
        )
    }

    return null
}

fun formatEditorInput(input: String): String {
    if (input.isEmpty()) return input
    var text = input

    // Auto-convert typed markdown checkbox brackets to clean unicode task boxes (☐ / ☑)
    text = text
        .replace(Regex("(?m)^(\\s*)[-*+•]?\\s*\\[[ \\-]?\\]\\s*"), "$1☐ ")
        .replace(Regex("(?m)^(\\s*)[-*+•]?\\s*\\[[xXvV✓]\\]\\s*"), "$1☑ ")

    // Auto-convert typed asterisk (*) or dash (-) or plus (+) followed by a space into clean bullet point '• '
    text = text
        .replace(Regex("(?m)^(\\s*)[-*+]\\s+"), "$1• ")

    return text
}

data class TodoItem(
    val cardId: Long,
    val originalLine: String,
    val taskText: String,
    val isCompleted: Boolean,
    val sourceLabel: String
)

fun androidx.compose.foundation.lazy.LazyListState.getScrollProgress(): Float {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return 0f
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return 0f
    val firstVisibleItem = visibleItemsInfo.first()
    val totalCount = layoutInfo.totalItemsCount
    val progress = (firstVisibleItem.index + (-firstVisibleItem.offset.toFloat() / firstVisibleItem.size.toFloat().coerceAtLeast(1f))) / totalCount.toFloat()
    return progress.coerceIn(0f, 1f)
}

fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState.getScrollProgress(): Float {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return 0f
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return 0f
    val firstVisibleItem = visibleItemsInfo.first()
    val totalCount = layoutInfo.totalItemsCount
    val avgHeight = visibleItemsInfo.map { it.size.height }.average().toFloat().coerceAtLeast(1f)
    val progress = (firstVisibleItem.index + (-firstVisibleItem.offset.y.toFloat() / avgHeight)) / totalCount.toFloat()
    return progress.coerceIn(0f, 1f)
}

@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun AlterAppScreen(
    viewModel: AlterViewModel,
    onMicClick: () -> Unit
) {
    val cards by viewModel.cards.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingSeconds by viewModel.recordingDurationSeconds.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMsg by viewModel.currentProcessingMessage.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    val cornerSmoothness by viewModel.cornerSmoothness.collectAsState()

    // Shared image staging variables
    val pendingImageUri by viewModel.pendingSharedImageUri.collectAsState()
    val pendingImageInstruction by viewModel.pendingSharedImageInstruction.collectAsState()

    // Attachment States
    val selectedUri by viewModel.selectedAttachmentUri.collectAsState()
    val selectedName by viewModel.selectedAttachmentName.collectAsState()
    val selectedType by viewModel.selectedAttachmentType.collectAsState()

    // Interactive states
    var currentTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var activeLoggingMode by remember { mutableStateOf(ActiveLoggingMode.NONE) }
    var showAddOptionsModal by remember { mutableStateOf(false) }

    var dragOffsetY by remember(activeLoggingMode) { mutableStateOf(0f) }
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "DragDismissY"
    )

    LaunchedEffect(isRecording) {
        if (isRecording) {
            activeLoggingMode = ActiveLoggingMode.AUDIO
            showAddOptionsModal = true
        } else if (activeLoggingMode == ActiveLoggingMode.AUDIO) {
            activeLoggingMode = ActiveLoggingMode.NONE
            showAddOptionsModal = false
        }
    }

    val triggerOpenText by viewModel.triggerOpenTextInput.collectAsState()
    LaunchedEffect(triggerOpenText) {
        if (triggerOpenText) {
            activeLoggingMode = ActiveLoggingMode.TEXT
            showAddOptionsModal = true
            viewModel.consumeTextInputOpenTrigger()
        }
    }

    val triggerOpenAudio by viewModel.triggerOpenAudioInput.collectAsState()
    LaunchedEffect(triggerOpenAudio) {
        if (triggerOpenAudio) {
            activeLoggingMode = ActiveLoggingMode.AUDIO
            showAddOptionsModal = true
            viewModel.consumeAudioInputOpenTrigger()
        }
    }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var showNoteInputDialog by remember { mutableStateOf(false) }
    var noteTitleInput by remember { mutableStateOf("") }
    var noteContentInput by remember { mutableStateOf("") }
    var taskInputText by remember { mutableStateOf("") }

    var isSearching by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showTrashBin by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }
    var activeTodoOptions by remember { mutableStateOf<TodoItem?>(null) }
    val todoRemindersMap by viewModel.todoRemindersMap.collectAsState()
    val highlightedCardId by viewModel.highlightedCardId.collectAsState()
    val listState = rememberLazyListState()
    val homeGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    val memoriesGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    val notesGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()

    val isThemeDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val optionsBarBg = if (isThemeDark) Color(0xFF18181B) else Color(0xFFE2E8F0)
    val optionsBarInactive = if (isThemeDark) Color(0xFFC8C8C8) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    // Dynamically filtered cards reactively based on live search queries
    val filteredCards = remember(cards, searchQuery, isSearching) {
        if (isSearching && searchQuery.isNotEmpty()) {
            cards.filter {
                it.rawInput.contains(searchQuery, ignoreCase = true) ||
                it.processedContent.contains(searchQuery, ignoreCase = true)
            }
        } else {
            cards
        }
    }

    // Smoothly scroll to the highlighted card when triggered from the widget
    LaunchedEffect(highlightedCardId, filteredCards) {
        val targetId = highlightedCardId
        if (targetId != null) {
            val index = filteredCards.indexOfFirst { it.id == targetId }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            focusManager.clearFocus()
        }
    }

    var previousTab by remember { mutableStateOf<AppTab?>(null) }
    LaunchedEffect(currentTab) {
        if (previousTab != null && previousTab != currentTab) {
            showAddOptionsModal = false
            activeLoggingMode = ActiveLoggingMode.NONE
            showTextInputDialog = false
            showNoteInputDialog = false
            focusManager.clearFocus()
        }
        previousTab = currentTab
    }

    // Automatically schedule future alarms for new checklists and slate lines that list dates and times dynamically
    LaunchedEffect(cards, todoRemindersMap) {
        cards.forEach { card ->
            if (card.type == "note" || card.isDeleted) return@forEach // skip notes and deleted cards from automatic reminder analysis
            val lines = card.processedContent.split("\n")
            lines.forEach { line ->
                val parsed = parseTodoLine(line)
                if (parsed != null && !parsed.isCompleted && parsed.taskText.isNotEmpty()) {
                    val taskText = parsed.taskText
                    val alarmTime = todoRemindersMap["reminder_${card.id}_${taskText}"] ?: 0L
                    val isDismissed = alarmTime == -1L
                    val isScheduled = alarmTime > 0L
                    if (!isDismissed && !isScheduled) {
                        val autoTime = parseDateTimeFromText(taskText)
                        if (autoTime != null && autoTime > System.currentTimeMillis()) {
                            viewModel.scheduleTodoAlarm(card.id, taskText, autoTime)
                        }
                    }
                }
            }
        }
    }

    fun triggerHaptic() {
        try {
            val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
            val hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
            if (hapticsEnabled) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    var showHamburgerMenu by remember { mutableStateOf(false) }
    var showChatHistorySheet by remember { mutableStateOf(false) }
    var cardToDeleteSoft by remember { mutableStateOf<com.example.data.AlterCard?>(null) }
    var cardToDeletePermanent by remember { mutableStateOf<com.example.data.AlterCard?>(null) }

    // Real-time dynamic header clock thread updating to the exact second
    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdfTime = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
        val sdfDate = java.text.SimpleDateFormat("EEE, MMM dd, yyyy", java.util.Locale.getDefault())
        while (true) {
            val now = java.util.Date()
            currentTimeString = sdfTime.format(now)
            currentDateString = sdfDate.format(now)
            delay(1000)
        }
    }

    // Attachment file picking callback launcher registry
    val pickAttachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val cr = context.contentResolver
            val type = cr.getType(uri) ?: "application/octet-stream"
            var name = "file"
            try {
                cr.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val categoryType = if (type.contains("pdf", ignoreCase = true)) "pdf" else "image"
            viewModel.selectAttachment(uri, categoryType, name)
            activeLoggingMode = if (categoryType == "image") ActiveLoggingMode.SCAN else ActiveLoggingMode.ATTACH
            showAddOptionsModal = true
            showTextInputDialog = true
        }
    }

    val cameraUriParam = remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUriParam.value != null) {
            viewModel.selectAttachment(cameraUriParam.value!!, "image", "Scan")
            activeLoggingMode = ActiveLoggingMode.SCAN
            showAddOptionsModal = true
            showTextInputDialog = true
        }
    }

    // Media playback engine
    var activePlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }

    fun playAudio(path: String) {
        try {
            if (activePlayingPath == path) {
                if (isAudioPlaying) {
                    mediaPlayer?.pause()
                    isAudioPlaying = false
                } else {
                    mediaPlayer?.start()
                    isAudioPlaying = true
                }
            } else {
                mediaPlayer?.release()
                val mp = android.media.MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    start()
                }
                mediaPlayer = mp
                activePlayingPath = path
                isAudioPlaying = true
                mp.setOnCompletionListener {
                    isAudioPlaying = false
                    activePlayingPath = null
                }
            }
        } catch (e: Exception) {
            // gracefully ignored
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    // Modal view for screenshot full preview
    var fullImageToOpen by remember { mutableStateOf<String?>(null) }
    var cardToEdit by remember { mutableStateOf<AlterCard?>(null) }
    var detailCardToShow by remember { mutableStateOf<AlterCard?>(null) }
    var startInEditMode by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(
        enabled = detailCardToShow != null || showAddOptionsModal || activeLoggingMode != ActiveLoggingMode.NONE || currentTab != AppTab.Home
    ) {
        triggerHaptic()
        if (detailCardToShow != null) {
            detailCardToShow = null
            showAddOptionsModal = false
            activeLoggingMode = ActiveLoggingMode.NONE
            currentTab = AppTab.Home
        } else if (activeLoggingMode != ActiveLoggingMode.NONE) {
            activeLoggingMode = ActiveLoggingMode.NONE
        } else if (showAddOptionsModal) {
            showAddOptionsModal = false
        } else if (currentTab != AppTab.Home) {
            currentTab = AppTab.Home
        }
    }

    val currentDetailCard = detailCardToShow
    if (currentDetailCard != null) {
        val currentCard = cards.find { it.id == currentDetailCard.id } ?: currentDetailCard
        val isCanvas = currentCard.type == "note" && (!currentCard.canvasData.isNullOrBlank() || currentCard.rawInput.startsWith("Untitled Canvas", ignoreCase = true))
        if (isCanvas) {
            DottedCanvasBoardScreen(
                card = currentCard,
                onBack = {
                    detailCardToShow = null
                    showAddOptionsModal = false
                    activeLoggingMode = ActiveLoggingMode.NONE
                    currentTab = AppTab.Home
                },
                viewModel = viewModel,
                triggerHaptic = { triggerHaptic() }
            )
        } else {
            FullScreenCardDetail(
                card = currentCard,
                onBack = {
                    detailCardToShow = null
                    showAddOptionsModal = false
                    activeLoggingMode = ActiveLoggingMode.NONE
                    currentTab = AppTab.Home
                },
                viewModel = viewModel,
                isAudioPlaying = isAudioPlaying,
                activePlayingPath = activePlayingPath,
                onPlayAudio = { path -> playAudio(path) },
                triggerHaptic = { triggerHaptic() }
            )
        }
    } else {
        val isPopupActive = showAddOptionsModal || activeLoggingMode != ActiveLoggingMode.NONE || isRecording
        val fabRotationAngle by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isPopupActive) 135f else 0f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            ),
            label = "FabRotation"
        )
        val isImeVisible = WindowInsets.isImeVisible
        val bottomPadding = if (isImeVisible) {
            12.dp
        } else {
            116.dp
        }

        var appEntered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            appEntered = true
        }
        val appEntranceAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (appEntered) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "AppEntranceAlpha"
        )
        val appEntranceScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (appEntered) 1f else 0.985f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
            ),
            label = "AppEntranceScale"
        )

        Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appEntranceAlpha
                scaleX = appEntranceScale
                scaleY = appEntranceScale
            }
            .testTag("alter_main_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Dynamic Capitalization of App name/Tab name
                    val topHeaderTitle = when (currentTab) {
                        AppTab.Home -> "Home"
                        AppTab.AiChat -> "Alter"
                        AppTab.Memories -> "Memories"
                        AppTab.Upcoming -> "Upcoming"
                        AppTab.Notes -> "Notes"
                        AppTab.Settings -> "Settings"
                    }
                    Text(
                        text = topHeaderTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.testTag("app_dynamic_header_title")
                    )

                    // Right: Settings & Hamburger Menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (currentTab == AppTab.AiChat) {
                            IconButton(
                                onClick = {
                                    triggerHaptic()
                                    showChatHistorySheet = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Lucide.Clock,
                                    contentDescription = "Chat History",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (currentTab != AppTab.Settings) {
                            // Hamburger Box (Anchor for DropdownMenu)
                            Box {
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val translationYPx = with(density) { 3.5.dp.toPx() }

                                val rotationTop by animateFloatAsState(
                                    targetValue = if (showHamburgerMenu) 45f else 0f,
                                    animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                    label = "MenuRotationTop"
                                )
                                val rotationBottom by animateFloatAsState(
                                    targetValue = if (showHamburgerMenu) -45f else 0f,
                                    animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                    label = "MenuRotationBottom"
                                )
                                val transYTop by animateFloatAsState(
                                    targetValue = if (showHamburgerMenu) translationYPx else 0f,
                                    animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                    label = "MenuTransYTop"
                                )
                                val transYBottom by animateFloatAsState(
                                    targetValue = if (showHamburgerMenu) -translationYPx else 0f,
                                    animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                                    label = "MenuTransYBottom"
                                )

                                IconButton(
                                    onClick = {
                                        triggerHaptic()
                                        showHamburgerMenu = !showHamburgerMenu
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.size(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .height(2.dp)
                                                .graphicsLayer(
                                                    rotationZ = rotationTop,
                                                    translationY = transYTop
                                                )
                                                .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(1.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .height(2.dp)
                                                .graphicsLayer(
                                                    rotationZ = rotationBottom,
                                                    translationY = transYBottom
                                                )
                                                .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(1.dp))
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showHamburgerMenu,
                                    onDismissRequest = { showHamburgerMenu = false },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Search Slates", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Lucide.Search,
                                                contentDescription = null,
                                                tint = if (isSearching) Color(0xFFFFBD00) else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            triggerHaptic()
                                            isSearching = !isSearching
                                            showHamburgerMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Chat History", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Lucide.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            triggerHaptic()
                                            showChatHistorySheet = true
                                            showHamburgerMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Trash Bin", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Lucide.Trash,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            triggerHaptic()
                                            showTrashBin = true
                                            showHamburgerMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Lucide.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            triggerHaptic()
                                            currentTab = AppTab.Settings
                                            showHamburgerMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Smooth unfolding Search Textfield
                AnimatedVisibility(
                    visible = isSearching,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search inputs or analyzed items...",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("slate_search_input")
                                .onFocusChanged { isSearchFocused = it.isFocused },
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = Color(0xFFFFBD00),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(22.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Text("✕", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                    }
                                }
                            },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Persistent Background Processing Error Banner ("Red Issue at Top")
                AnimatedVisibility(
                    visible = !errorMsg.isNullOrBlank(),
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp)
                            .shadow(4.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF3B1212))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Lucide.AlertCircle,
                                        contentDescription = "Error Alert",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Processing Issue",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFFEF4444)
                                    )
                                    Text(
                                        text = errorMsg ?: "",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                        color = Color(0xFFFCA5A5),
                                        maxLines = 3,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    triggerHaptic()
                                    viewModel.dismissError()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text(
                                    text = "✕",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // --- TAB CAPSULE NAVIGATION BAR ---
                if (currentTab != AppTab.Settings && !isInputFocused && !isSearchFocused && !WindowInsets.isImeVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val navTabs = listOf(
                            Triple(AppTab.Home, "Home", Lucide.Home),
                            Triple(AppTab.AiChat, "Alter AI", Lucide.Sparkles),
                            Triple(AppTab.Memories, "Memories", Lucide.Memories),
                            Triple(AppTab.Upcoming, "Upcoming", Lucide.Calendar),
                            Triple(AppTab.Notes, "Notes", Lucide.FileText)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(64.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            navTabs.forEach { (tabRoute, label, icon) ->
                                val isActive = currentTab == tabRoute

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isActive) Color.Black else Color.Transparent)
                                        .border(
                                            width = if (isActive) 0.5.dp else 0.dp,
                                            color = if (isActive) Color(0xFFFFBD00) else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            currentTab = tabRoute
                                            triggerHaptic()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .animateContentSize(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (tabRoute == AppTab.AiChat) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_alter_ai_doodle),
                                            contentDescription = label,
                                            modifier = Modifier.size(22.dp),
                                            tint = if (isActive) Color(0xFFFFBD00) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(22.dp),
                                            tint = if (isActive) Color(0xFFFFBD00) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isActive,
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = label,
                                                fontFamily = FontFamily.SansSerif,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 0.dp
                )
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (currentTab != AppTab.Settings && !isPopupActive) {
                val scrollProgress by remember(currentTab) {
                    derivedStateOf {
                        when (currentTab) {
                            AppTab.Home -> homeGridState.getScrollProgress()
                            AppTab.Memories -> memoriesGridState.getScrollProgress()
                            AppTab.Notes -> notesGridState.getScrollProgress()
                            AppTab.Upcoming -> listState.getScrollProgress()
                            else -> 0f
                        }
                    }
                }
                val isScrolling by remember(currentTab) {
                    derivedStateOf {
                        when (currentTab) {
                            AppTab.Home -> homeGridState.isScrollInProgress
                            AppTab.Memories -> memoriesGridState.isScrollInProgress
                            AppTab.Notes -> notesGridState.isScrollInProgress
                            AppTab.Upcoming -> listState.isScrollInProgress
                            else -> false
                        }
                    }
                }
                val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isScrolling) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                    label = "ScrollbarAlpha"
                )

                if (scrollbarAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, bottom = 120.dp, end = 4.dp)
                            .width(4.dp)
                            .fillMaxHeight()
                            .graphicsLayer(alpha = scrollbarAlpha)
                            .background(Color(0xFF888888).copy(alpha = 0.2f), CircleShape)
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val maxOffset = maxHeight - 48.dp
                            val thumbOffset = (scrollProgress * maxOffset.value).dp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .offset(y = thumbOffset)
                                    .background(Color(0xFF888888), CircleShape)
                            )
                        }
                    }
                }
            }

            Crossfade(
                targetState = currentTab,
                animationSpec = tween(durationMillis = 300),
                label = "TabContentShift"
            ) { tabState ->
                when (tabState) {
                    AppTab.AiChat -> {
                        AlterAiChatScreen(
                            viewModel = viewModel,
                            onCardClick = { cardId ->
                                val foundCard = cards.find { it.id == cardId }
                                if (foundCard != null) {
                                    detailCardToShow = foundCard
                                }
                            },
                            triggerHaptic = { triggerHaptic() }
                        )
                    }
                    AppTab.Home -> {
                        // ========================== HOME VIEW (DATE DIVIDED COLUMNS GRID) ==========================
                        val cardsByDate = remember(filteredCards) {
                            filteredCards.filter { !it.isDeleted && it.type.isNotBlank() }.groupBy { card ->
                                val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                                sdf.format(Date(card.timestamp))
                            }
                        }
                        val sortedDates = remember(cardsByDate) {
                            cardsByDate.keys.sortedByDescending { dateStr ->
                                cardsByDate[dateStr]?.maxOfOrNull { it.timestamp } ?: 0L
                            }
                        }

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = homeGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingEdges()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 32.dp, bottom = 204.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp
                        ) {
                            // Removed top list item overlay per user intent

                            // Shared screenshot staging portal
                            pendingImageUri?.let { uri ->
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp))
                                            .border(0.5.dp, MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(18.dp))
                                            .padding(16.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Lucide.Image,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Shared Screenshot Detected",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = "Shared Preview",
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                                                    contentScale = ContentScale.Crop
                                                )

                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "Add custom caption or instructions before synthesizing:",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                    OutlinedTextField(
                                                        value = pendingImageInstruction,
                                                        onValueChange = { viewModel.updatePendingImageInstruction(it) },
                                                        placeholder = {
                                                            Text(
                                                                "Tell Gemini what to do... (e.g. 'Extract receipt todos')",
                                                                fontSize = 12.sp
                                                            )
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textStyle = TextStyle(fontSize = 13.sp),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedContainerColor = MaterialTheme.colorScheme.background,
                                                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                                                        ),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(onClick = { viewModel.cancelPendingImage() }) {
                                                    Text("Cancel", color = Color.Red.copy(alpha = 0.8f))
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(
                                                    onClick = { viewModel.processStagedImage() },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    ),
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    Text("Synthesize Screenshot", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Processing state and errors
                            item(span = StaggeredGridItemSpan.FullLine) {
                                AnimatedVisibility(
                                    visible = isProcessing,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                                            .border(0.5.dp, MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(16.dp))
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = processingMsg,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                }
                            }

                            if (filteredCards.isEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    NothingDumpedEmptyState()
                                }
                            } else {
                                // RENDER DATE GROUPS STAGGERED GRID COLUMN LAYOUT
                                sortedDates.forEachIndexed { index, dateStr ->
                                    item(key = "hdr_$dateStr", span = StaggeredGridItemSpan.FullLine) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    top = if (index == 0 && !isRecording && pendingImageUri == null && !isProcessing && errorMsg == null) 0.dp else 4.dp,
                                                    bottom = 4.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = dateStr,
                                                fontFamily = FontFamily.SansSerif,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface, // only #ffffff and medium weight, 16px/sp as requested
                                                style = TextStyle(letterSpacing = 0.2.sp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(0.75.dp)
                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            )
                                        }
                                    }

                                    val cardsInGroup = cardsByDate[dateStr] ?: emptyList()

                                    items(
                                        items = cardsInGroup,
                                        span = { card -> 
                                            if (isCardLong(card)) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane 
                                        }
                                    ) { card ->
                                        AlterCardItemWrapper(
                                            card = card,
                                            highlightedCardId = highlightedCardId,
                                            activePlayingPath = activePlayingPath,
                                            isAudioPlaying = isAudioPlaying,
                                            onPlayPauseAudio = { playAudio(it) },
                                            onOpenImage = { fullImageToOpen = it },
                                            onDelete = { cardToDeleteSoft = it },
                                            onEdit = { 
                                                detailCardToShow = it
                                                startInEditMode = true 
                                            },
                                            clipboardManager = clipboardManager,
                                            context = context,
                                            viewModel = viewModel,
                                            todoRemindersMap = todoRemindersMap,
                                            onHapticClick = { triggerHaptic() },
                                            onCardClick = { 
                                                detailCardToShow = it
                                                startInEditMode = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AppTab.Memories -> {
                        // ========================== CHRONIC TIMELINE FEEDVIEW ==========================
                        val memoryCards = remember(filteredCards) {
                            filteredCards.filter { it.type != "note" && it.type != "upcoming" && !it.isDeleted }
                        }

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = memoriesGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingEdges()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 32.dp, bottom = 204.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp
                        ) {
                            if (memoryCards.isEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    NothingDumpedEmptyState()
                                }
                            } else {
                                items(
                                    items = memoryCards,
                                    span = { card -> 
                                        if (isCardLong(card)) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane 
                                    }
                                ) { card ->
                                    AlterCardItemWrapper(
                                        card = card,
                                        highlightedCardId = highlightedCardId,
                                        activePlayingPath = activePlayingPath,
                                        isAudioPlaying = isAudioPlaying,
                                        onPlayPauseAudio = { playAudio(it) },
                                        onOpenImage = { fullImageToOpen = it },
                                        onDelete = { cardToDeleteSoft = it },
                                        onEdit = { 
                                            detailCardToShow = it
                                            startInEditMode = true 
                                        },
                                        clipboardManager = clipboardManager,
                                        context = context,
                                        viewModel = viewModel,
                                        todoRemindersMap = todoRemindersMap,
                                        onHapticClick = { triggerHaptic() },
                                        onCardClick = { 
                                            detailCardToShow = it
                                            startInEditMode = false
                                        },
                                        showDateInsteadOfTime = true
                                    )
                                }
                            }
                        }
                    }

                    AppTab.Upcoming -> {
                        // ========================== UPCOMING CHECKLISTS TODO ==========================
                        val todoItems = remember(cards) {
                            val items = mutableListOf<TodoItem>()
                            val seenKeys = mutableSetOf<String>()
                            cards.forEach { card ->
                                if (!card.isDeleted) {
                                    val sources = listOf(
                                        Pair(card.processedContent, if (card.type == "note") card.rawInput.ifBlank { "Note" } else card.rawInput.ifBlank { "Summary" }),
                                        Pair(card.personalNotes, if (card.type == "note") "Note Details" else "Personal Notes"),
                                        Pair(if (card.type == "upcoming") card.rawInput else "", "Task")
                                    )
                                    sources.forEach { (content, defaultLabel) ->
                                        if (content.isNotBlank()) {
                                            val lines = content.split("\n")
                                            lines.forEach { line ->
                                                val parsed = parseTodoLine(line)
                                                if (parsed != null && parsed.taskText.isNotBlank()) {
                                                    val uniqueKey = "${card.id}_${parsed.taskText}"
                                                    if (seenKeys.add(uniqueKey)) {
                                                        items.add(
                                                            TodoItem(
                                                                cardId = card.id,
                                                                originalLine = line,
                                                                taskText = parsed.taskText,
                                                                isCompleted = parsed.isCompleted,
                                                                sourceLabel = defaultLabel.ifBlank { if (card.type == "note") "Note" else "Slate" }
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            items
                        }

                        val pendingTodos = remember(todoItems) { todoItems.filter { !it.isCompleted } }
                        val completedTodos = remember(todoItems) { todoItems.filter { it.isCompleted } }
                        var completedExpanded by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (todoItems.isEmpty()) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .fadingEdges(topFadeHeight = 16.dp, bottomFadeHeight = 32.dp),
                                        contentPadding = PaddingValues(top = 32.dp, bottom = 204.dp)
                                    ) {
                                        item {
                                            NothingDumpedEmptyState()
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .fadingEdges(topFadeHeight = 16.dp, bottomFadeHeight = 32.dp),
                                        contentPadding = PaddingValues(top = 32.dp, bottom = 204.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            items = pendingTodos,
                                            key = { "${it.cardId}_${it.originalLine}_${it.taskText}" }
                                        ) { todo ->
                                            TodoItemRow(
                                                todo = todo,
                                                cards = cards,
                                                viewModel = viewModel,
                                                todoRemindersMap = todoRemindersMap,
                                                context = context,
                                                onShowReminderChooser = { activeTodoOptions = it },
                                                onCardClick = { parentCard ->
                                                    detailCardToShow = parentCard
                                                    startInEditMode = true
                                                },
                                                enableDisintegration = true
                                            )
                                        }

                                        if (completedTodos.isNotEmpty()) {
                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            triggerHaptic()
                                                            completedExpanded = !completedExpanded
                                                        }
                                                        .padding(vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Completed (${completedTodos.size})",
                                                        fontFamily = FontFamily.SansSerif,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = if (completedExpanded) "▲" else "▼",
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )
                                                }
                                            }

                                            if (completedExpanded) {
                                                items(
                                                    items = completedTodos,
                                                    key = { "completed_${it.cardId}_${it.originalLine}_${it.taskText}" }
                                                ) { todo ->
                                                    TodoItemRow(
                                                        todo = todo,
                                                        cards = cards,
                                                        viewModel = viewModel,
                                                        todoRemindersMap = todoRemindersMap,
                                                        context = context,
                                                        onShowReminderChooser = { activeTodoOptions = it },
                                                        onCardClick = { parentCard ->
                                                            detailCardToShow = parentCard
                                                            startInEditMode = true
                                                        },
                                                        enableDisintegration = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AppTab.Notes -> {
                        // ========================== OFFLINE NOTES BOOKSHELF VIEW ==========================
                        val notesCards = remember(filteredCards) {
                            filteredCards.filter { it.type == "note" && !it.isDeleted }
                        }

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = notesGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingEdges()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 32.dp, bottom = 204.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp
                        ) {
                            if (notesCards.isEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    NothingDumpedEmptyState()
                                }
                            } else {
                                items(
                                    items = notesCards,
                                    span = { card -> 
                                        if (isCardLong(card)) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane 
                                    }
                                ) { card ->
                                    AlterCardItemWrapper(
                                        card = card,
                                        highlightedCardId = highlightedCardId,
                                        activePlayingPath = activePlayingPath,
                                        isAudioPlaying = isAudioPlaying,
                                        onPlayPauseAudio = { playAudio(it) },
                                        onOpenImage = { fullImageToOpen = it },
                                        onDelete = { cardToDeleteSoft = it },
                                        onEdit = { 
                                            detailCardToShow = it
                                            startInEditMode = true 
                                        },
                                        clipboardManager = clipboardManager,
                                        context = context,
                                        viewModel = viewModel,
                                        todoRemindersMap = todoRemindersMap,
                                        onHapticClick = { triggerHaptic() },
                                        onCardClick = { 
                                            detailCardToShow = it
                                            startInEditMode = false
                                        },
                                        showDateInsteadOfTime = true
                                    )
                                }
                            }
                        }
                    }

                    AppTab.Settings -> {
                        SettingsPage(
                            viewModel = viewModel,
                            onBackToHome = { currentTab = AppTab.Home }
                        )
                    }
                }
            }

            // Faded background overlay when options modal is open or when entering text/notes as inline popup
            AnimatedVisibility(
                visible = showAddOptionsModal || activeLoggingMode != ActiveLoggingMode.NONE || showTextInputDialog || showNoteInputDialog || isRecording,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showAddOptionsModal = false
                                showTextInputDialog = false
                                showNoteInputDialog = false
                                activeLoggingMode = ActiveLoggingMode.NONE
                                if (isRecording) {
                                    onMicClick()
                                }
                                viewModel.clearAttachment()
                        }
                )
            }

            // ========================== SOPHISTICATED EXPANDING "+" FAB FLOATING OVERLAY ==========================
            if (currentTab != AppTab.Settings && currentTab != AppTab.AiChat) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(
                            bottom = bottomPadding,
                            start = 16.dp,
                            end = 16.dp
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (!isPopupActive) {
                        if (currentTab == AppTab.Notes) {
                            // Dedicated "Add a note" pill button instead of "+" icon
                            Row(
                                modifier = Modifier
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color(0xFFFFCC00))
                                    .clickable {
                                        triggerHaptic()
                                        activeLoggingMode = ActiveLoggingMode.NOTE
                                        showAddOptionsModal = true
                                    }
                                    .padding(horizontal = 24.dp)
                                    .align(Alignment.BottomEnd)
                                    .testTag("expandable_fab_trigger_btn"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Lucide.FileText,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Add a note",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            // Standard "+" button is shown on the right
                            Box(
                                modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFCC00))
                                        .clickable {
                                            triggerHaptic()
                                            showAddOptionsModal = true
                                        }
                                        .align(Alignment.BottomEnd)
                                        .testTag("expandable_fab_trigger_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Lucide.Plus,
                                    contentDescription = "Expand thought logging triggers",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer(rotationZ = fabRotationAngle)
                                )
                            }
                        }
                    } else {
                        // Under here we display the unified design: popup container (if an action is active) sits on top, and Options Bar lies beneath it!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 1. CHOSEN POPUP BOX OR CONTAINER (if activeLoggingMode != ActiveLoggingMode.NONE)
                            AnimatedVisibility(
                                visible = activeLoggingMode != ActiveLoggingMode.NONE,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                when (activeLoggingMode) {
                                    ActiveLoggingMode.NONE -> {
                                        /* Safe fallback */
                                    }
                                    ActiveLoggingMode.TEXT, ActiveLoggingMode.SCAN, ActiveLoggingMode.ATTACH -> {
                                        var localText by remember(activeLoggingMode) { mutableStateOf("") }
                                        LaunchedEffect(inputText) {
                                            if (inputText != localText) {
                                                localText = inputText
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 188.dp)
                                                .offset { IntOffset(0, animatedDragOffsetY.roundToInt()) }
                                                .offset(y = 8.dp)
                                                .background(Color(0xFF18181B), RoundedCornerShape(16.dp))
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtLeast(0f)
                                                        },
                                                        onDragEnd = {
                                                            if (dragOffsetY > 120f) {
                                                                 activeLoggingMode = ActiveLoggingMode.NONE
                                                                 showAddOptionsModal = false
                                                                 viewModel.clearAttachment()
                                                            }
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            dragOffsetY = 0f
                                                        }
                                                    )
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 52.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Draggable visual hint (extremely subtle micro bar)
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterHorizontally)
                                                        .width(36.dp)
                                                        .height(3.dp)
                                                        .background(Color(0xFF222222), CircleShape)
                                                )

                                                // Attachment Preview Badge
                                                if (selectedUri != null) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                                                            .padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (selectedType == "pdf") Lucide.FileText else Lucide.Image,
                                                            contentDescription = null,
                                                            tint = Color(0xFFFFCC00),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = selectedName ?: "Selected file",
                                                                color = Color(0xFFC8C8C8),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                maxLines = 1,
                                                                fontFamily = FontFamily.SansSerif
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                triggerHaptic()
                                                                viewModel.clearAttachment()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Lucide.X,
                                                                contentDescription = "Remove attachment",
                                                                tint = Color(0xFF888888),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                // Clean Input and Pill Button block exactly matching the CSS/image spec
                                                androidx.compose.foundation.text.BasicTextField(
                                                    value = localText,
                                                    onValueChange = {
                                                        localText = it
                                                        viewModel.updateInputText(it)
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 64.dp),
                                                    textStyle = TextStyle(
                                                        color = Color(0xFFC8C8C8),
                                                        fontSize = 14.sp,
                                                        fontFamily = FontFamily.SansSerif,
                                                        lineHeight = 16.sp,
                                                        letterSpacing = 0.01.em
                                                    ),
                                                    cursorBrush = SolidColor(Color(0xFFFFCC00)),
                                                    decorationBox = { innerTextField ->
                                                        if (localText.isEmpty()) {
                                                            Text(
                                                                text = if (selectedUri != null) "Add caption guide..." else "Spill messy thought...",
                                                                color = Color(0xFF555555),
                                                                fontSize = 14.sp,
                                                                fontFamily = FontFamily.SansSerif,
                                                                lineHeight = 16.sp,
                                                                letterSpacing = 0.01.em
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                )
                                            }

                                            // Black action pill button on bottom right matching spec and aligned in parent Box
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(0xFF000000))
                                                    .clickable {
                                                        triggerHaptic()
                                                        if (localText.isNotBlank() || selectedUri != null) {
                                                            viewModel.updateInputText(localText)
                                                            viewModel.processTextInput()
                                                            localText = ""
                                                        }
                                                        activeLoggingMode = ActiveLoggingMode.NONE
                                                        showAddOptionsModal = false
                                                    }
                                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Rewire this",
                                                    color = Color.White,
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 14.sp,
                                                    lineHeight = 18.sp,
                                                    letterSpacing = 0.01.em,
                                                    modifier = Modifier.widthIn(max = 100.dp)
                                                )
                                                Icon(
                                                    imageVector = Lucide.ArrowRight,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    ActiveLoggingMode.AUDIO -> {
                                        var peakAmplitude by remember { mutableStateOf(0) }
                                        LaunchedEffect(isRecording) {
                                            if (isRecording) {
                                                while (true) {
                                                    delay(80)
                                                    peakAmplitude = viewModel.getMicAmplitude()
                                                }
                                            } else {
                                                peakAmplitude = 0
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 188.dp)
                                                .offset { IntOffset(0, animatedDragOffsetY.roundToInt()) }
                                                .offset(y = 8.dp)
                                                .background(Color(0xFF18181B), RoundedCornerShape(16.dp))
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtLeast(0f)
                                                        },
                                                        onDragEnd = {
                                                            if (dragOffsetY > 120f) {
                                                                if (isRecording) {
                                                                    viewModel.discardAudioRecording()
                                                                }
                                                                activeLoggingMode = ActiveLoggingMode.NONE
                                                                showAddOptionsModal = false
                                                                currentTab = AppTab.Home
                                                            }
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            dragOffsetY = 0f
                                                        }
                                                    )
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Draggable indicator handle matching design system
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterHorizontally)
                                                        .width(36.dp)
                                                        .height(3.dp)
                                                        .background(Color(0xFF222222), CircleShape)
                                                )

                                                // Top row: Header Title + Discard Close Button
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (isRecording) "Voice Thought" else "Voice Note Complete",
                                                        color = Color(0xFFC8C8C8),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.SansSerif
                                                    )

                                                    IconButton(
                                                        onClick = {
                                                            triggerHaptic()
                                                            if (isRecording) {
                                                                viewModel.discardAudioRecording()
                                                            }
                                                            activeLoggingMode = ActiveLoggingMode.NONE
                                                            showAddOptionsModal = false
                                                            currentTab = AppTab.Home
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Lucide.X,
                                                            contentDescription = "Discard voice recording",
                                                            tint = Color(0xFF666666),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }

                                                // Edge-to-Edge Continuous Waveform container
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(58.dp)
                                                        .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val infiniteWaveTransition = rememberInfiniteTransition(label = "audio_wave_flow")
                                                    val wavePhaseAnim by infiniteWaveTransition.animateFloat(
                                                        initialValue = 0f,
                                                        targetValue = (2 * Math.PI).toFloat(),
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween(1400, easing = LinearEasing),
                                                            repeatMode = RepeatMode.Restart
                                                        ),
                                                        label = "wave_phase"
                                                    )

                                                    androidx.compose.foundation.Canvas(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .fillMaxHeight()
                                                    ) {
                                                        val width = size.width
                                                        val height = size.height
                                                        val midY = height / 2f
                                                        val volumeFactor = (peakAmplitude / 8000f).coerceIn(0f, 1f)

                                                        if (isRecording) {
                                                            val baseAmp = if (volumeFactor > 0.04f) {
                                                                (6.dp.toPx() + 18.dp.toPx() * volumeFactor).coerceAtMost(height * 0.42f)
                                                            } else {
                                                                3.dp.toPx()
                                                            }

                                                            val steps = 120
                                                            val dx = width / steps
                                                            val wavePath = androidx.compose.ui.graphics.Path()

                                                            for (i in 0..steps) {
                                                                val x = i * dx
                                                                val normX = (x / width).coerceIn(0f, 1f)
                                                                val envelope = kotlin.math.sin(normX * Math.PI.toFloat())
                                                                val angle1 = (normX * 5.0f * Math.PI.toFloat()) + wavePhaseAnim
                                                                val angle2 = (normX * 9.0f * Math.PI.toFloat()) - (wavePhaseAnim * 1.5f)
                                                                val waveOffset = (kotlin.math.sin(angle1) * 0.72f + kotlin.math.sin(angle2) * 0.28f) * baseAmp * envelope
                                                                val y = midY + waveOffset

                                                                if (i == 0) {
                                                                    wavePath.moveTo(x, y)
                                                                } else {
                                                                    wavePath.lineTo(x, y)
                                                                }
                                                            }

                                                            // Ambient glow behind wave
                                                            drawPath(
                                                                path = wavePath,
                                                                color = Color(0xFFFFCC00).copy(alpha = 0.2f),
                                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                                    width = 5.dp.toPx(),
                                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                                )
                                                            )

                                                            // Sharp golden wave line
                                                            drawPath(
                                                                path = wavePath,
                                                                color = Color(0xFFFFCC00),
                                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                                    width = 2.dp.toPx(),
                                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                                )
                                                            )
                                                        } else {
                                                            // Baseline resting line
                                                            val baselinePath = androidx.compose.ui.graphics.Path().apply {
                                                                moveTo(0f, midY)
                                                                lineTo(width, midY)
                                                            }
                                                            drawPath(
                                                                path = baselinePath,
                                                                color = Color(0xFF2A2A2A),
                                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                                    width = 1.5.dp.toPx(),
                                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                                )
                                                            )
                                                        }
                                                    }
                                                }

                                                // Bottom Row: Time duration counter on left + Black Action Pill Button on right
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Duration counter
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = formatDuration(recordingSeconds),
                                                            color = Color(0xFFFFCC00),
                                                            fontFamily = FontFamily.SansSerif,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = if (isRecording) "• Listening..." else "• Recording complete",
                                                            color = Color(0xFF666666),
                                                            fontSize = 12.sp,
                                                            fontFamily = FontFamily.SansSerif
                                                        )
                                                    }

                                                    // Black action pill button matching design system exactly
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(Color(0xFF000000))
                                                            .clickable {
                                                                triggerHaptic()
                                                                if (isRecording) {
                                                                    onMicClick()
                                                                }
                                                                activeLoggingMode = ActiveLoggingMode.NONE
                                                                showAddOptionsModal = false
                                                                currentTab = AppTab.Home
                                                            }
                                                            .padding(vertical = 10.dp, horizontal = 16.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isRecording) "Rewire this" else "Save note",
                                                            color = Color.White,
                                                            fontFamily = FontFamily.SansSerif,
                                                            fontSize = 14.sp,
                                                            lineHeight = 18.sp,
                                                            letterSpacing = 0.01.em,
                                                            modifier = Modifier.widthIn(max = 110.dp)
                                                        )
                                                        Icon(
                                                            imageVector = Lucide.ArrowRight,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    ActiveLoggingMode.NOTE -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 188.dp)
                                                .offset { IntOffset(0, animatedDragOffsetY.roundToInt()) }
                                                .offset(y = 8.dp)
                                                .background(Color(0xFF18181B), RoundedCornerShape(16.dp))
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtLeast(0f)
                                                        },
                                                        onDragEnd = {
                                                            if (dragOffsetY > 120f) {
                                                                activeLoggingMode = ActiveLoggingMode.NONE
                                                                showAddOptionsModal = false
                                                            }
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            dragOffsetY = 0f
                                                        }
                                                    )
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 52.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Draggable indicator
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterHorizontally)
                                                        .width(36.dp)
                                                        .height(3.dp)
                                                        .background(Color(0xFF222222), CircleShape)
                                                )

                                                // Clean spacing for direct-start input cards

                                                // Inputs Container
                                                // Note Title Input
                                                androidx.compose.foundation.text.BasicTextField(
                                                    value = noteTitleInput,
                                                    onValueChange = { noteTitleInput = it },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textStyle = TextStyle(
                                                        color = Color(0xFFC8C8C8),
                                                        fontSize = 14.sp,
                                                        fontFamily = FontFamily.SansSerif,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    cursorBrush = SolidColor(Color(0xFFFFCC00)),
                                                    decorationBox = { innerTextField ->
                                                        if (noteTitleInput.isEmpty()) {
                                                            Text(
                                                                text = "Note Title",
                                                                color = Color(0xFF555555),
                                                                fontSize = 14.sp,
                                                                fontFamily = FontFamily.SansSerif,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Note Content Formatting Controls
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0xFFFFCC00).copy(alpha = 0.15f))
                                                            .clickable {
                                                                triggerHaptic()
                                                                val current = noteContentInput
                                                                noteContentInput = if (current.isBlank()) "☐ " else if (current.endsWith("\n")) "${current}☐ " else "$current\n☐ "
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = "+ Task",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFFFCC00)
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0xFF2A2A2A))
                                                            .clickable {
                                                                triggerHaptic()
                                                                val current = noteContentInput
                                                                noteContentInput = if (current.isBlank()) "• " else if (current.endsWith("\n")) "${current}• " else "$current\n• "
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = "+ Bullet",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Color(0xFFC8C8C8)
                                                        )
                                                    }
                                                }

                                                // Note Content Input
                                                androidx.compose.foundation.text.BasicTextField(
                                                    value = noteContentInput,
                                                    onValueChange = { noteContentInput = formatEditorInput(it) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 80.dp),
                                                    textStyle = TextStyle(
                                                        color = Color(0xFFC8C8C8),
                                                        fontSize = 14.sp,
                                                        fontFamily = FontFamily.SansSerif,
                                                        lineHeight = 18.sp,
                                                        letterSpacing = 0.01.em
                                                    ),
                                                    cursorBrush = SolidColor(Color(0xFFFFCC00)),
                                                    decorationBox = { innerTextField ->
                                                        if (noteContentInput.isEmpty()) {
                                                            Text(
                                                                text = "Spill messy note thoughts...",
                                                                color = Color(0xFF555555),
                                                                fontSize = 14.sp,
                                                                fontFamily = FontFamily.SansSerif,
                                                                lineHeight = 18.sp,
                                                                letterSpacing = 0.01.em
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                )
                                            }

                                            // Black action pill button in NOTE mode aligned to parent Box bottom-right
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(0xFF000000))
                                                    .clickable {
                                                        triggerHaptic()
                                                        if (noteTitleInput.isNotBlank() || noteContentInput.isNotBlank()) {
                                                            viewModel.insertOfflineNote(noteTitleInput, noteContentInput)
                                                            noteTitleInput = ""
                                                            noteContentInput = ""
                                                        }
                                                        activeLoggingMode = ActiveLoggingMode.NONE
                                                        showAddOptionsModal = false
                                                    }
                                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Save Note",
                                                    color = Color.White,
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 14.sp,
                                                    lineHeight = 18.sp,
                                                    letterSpacing = 0.01.em,
                                                    modifier = Modifier.widthIn(max = 110.dp)
                                                )
                                                Icon(
                                                    imageVector = Lucide.ArrowRight,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    ActiveLoggingMode.UPCOMING -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 188.dp)
                                                .offset { IntOffset(0, animatedDragOffsetY.roundToInt()) }
                                                .offset(y = 8.dp)
                                                .background(Color(0xFF18181B), RoundedCornerShape(16.dp))
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtLeast(0f)
                                                        },
                                                        onDragEnd = {
                                                            if (dragOffsetY > 120f) {
                                                                activeLoggingMode = ActiveLoggingMode.NONE
                                                                showAddOptionsModal = false
                                                            }
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            dragOffsetY = 0f
                                                        }
                                                    )
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 52.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Draggable indicator
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterHorizontally)
                                                        .width(36.dp)
                                                        .height(3.dp)
                                                        .background(Color(0xFF222222), CircleShape)
                                                )

                                                // Top row: header and close
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Direct Upcoming Task",
                                                        color = Color(0xFFC8C8C8),
                                                        fontSize = 14.sp,
                                                        fontFamily = FontFamily.SansSerif,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            triggerHaptic()
                                                            activeLoggingMode = ActiveLoggingMode.NONE
                                                            showAddOptionsModal = false
                                                            taskInputText = ""
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Lucide.X,
                                                            contentDescription = "Close",
                                                            tint = Color(0xFF666666),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }

                                                // Clean Input and Pill Button block matching CSS exactly
                                                androidx.compose.foundation.text.BasicTextField(
                                                    value = taskInputText,
                                                    onValueChange = { taskInputText = it },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 64.dp),
                                                    textStyle = TextStyle(
                                                        color = Color(0xFFC8C8C8),
                                                        fontSize = 14.sp,
                                                        fontFamily = FontFamily.SansSerif,
                                                        lineHeight = 18.sp,
                                                        letterSpacing = 0.01.em
                                                    ),
                                                    cursorBrush = SolidColor(Color(0xFFFFCC00)),
                                                    decorationBox = { innerTextField ->
                                                        if (taskInputText.isEmpty()) {
                                                            Text(
                                                                text = "What needs to be done? e.g., Buy groceries tomorrow...",
                                                                color = Color(0xFF555555),
                                                                fontSize = 14.sp,
                                                                fontFamily = FontFamily.SansSerif,
                                                                lineHeight = 18.sp,
                                                                letterSpacing = 0.01.em
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                )
                                            }

                                            // Black action pill button in UPCOMING mode aligned to parent Box bottom-right
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                     .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(0xFF000000))
                                                    .clickable {
                                                        triggerHaptic()
                                                        if (taskInputText.isNotBlank()) {
                                                            viewModel.insertUpcomingTask(taskInputText)
                                                            taskInputText = ""
                                                        }
                                                        activeLoggingMode = ActiveLoggingMode.NONE
                                                        showAddOptionsModal = false
                                                    }
                                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Add Task",
                                                    color = Color.White,
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    lineHeight = 18.sp,
                                                    letterSpacing = 0.01.em,
                                                    modifier = Modifier.widthIn(max = 110.dp)
                                                )
                                                Icon(
                                                    imageVector = Lucide.ArrowRight,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 2. THE CHOSEN OPTIONS ROW + DISMISS BUTTON (Sized and Spaced exactly as requested in Figma specs, aligned perfectly with the closed FAB placement)
                            AnimatedVisibility(
                                visible = true, // Keep option bar visible unconditionally as requested by user
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                val visibleOptions = remember(currentTab) {
                                    when (currentTab) {
                                        AppTab.Home -> listOf("Text", "Audio", "Scan", "Attach", "Note", "Canvas")
                                        AppTab.Upcoming -> listOf("Task", "Text", "Audio", "Scan", "Attach")
                                        AppTab.Memories -> listOf("Text", "Audio", "Scan", "Attach")
                                        AppTab.Notes -> listOf("Note", "Canvas")
                                        else -> listOf("Text", "Audio", "Scan", "Attach", "Note", "Canvas")
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                ) {
                                    // Left element: Frame 1686555757 menu container (dynamic width, themed bg, 44dp radius)
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 56.dp) // Pushes it exactly to the left of the yellow FAB!
                                            .wrapContentWidth()
                                            .height(56.dp)
                                            .background(
                                                optionsBarBg,
                                                shape = RoundedCornerShape(44.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
                                    ) {
                                        // 1: Text Item (Lucide.Type) - Frame 1686555795
                                        if (visibleOptions.contains("Text")) {
                                            Column(
                                                modifier = Modifier
                                                    .width(24.dp)
                                                    .height(36.dp)
                                                    .clickable(
                                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        triggerHaptic()
                                                        activeLoggingMode = ActiveLoggingMode.TEXT
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                            ) {
                                                Icon(
                                                    imageVector = Lucide.Type,
                                                    contentDescription = "Text Item",
                                                    tint = if (activeLoggingMode == ActiveLoggingMode.TEXT) Color(0xFFFFCC00) else optionsBarInactive,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Text",
                                                    fontSize = 8.sp,
                                                    color = if (activeLoggingMode == ActiveLoggingMode.TEXT) Color(0xFFFFCC00) else optionsBarInactive,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }

                                        // 2: Audio Voice Memo - Frame 1686555796
                                        if (visibleOptions.contains("Audio")) {
                                            Column(
                                                modifier = Modifier
                                                    .width(24.dp)
                                                    .height(36.dp)
                                                    .clickable(
                                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        triggerHaptic()
                                                        if (!isRecording) {
                                                            activeLoggingMode = ActiveLoggingMode.AUDIO
                                                            onMicClick()
                                                        } else {
                                                            onMicClick()
                                                            activeLoggingMode = ActiveLoggingMode.NONE
                                                            showAddOptionsModal = false
                                                            currentTab = AppTab.Home
                                                        }
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                            ) {
                                                Icon(
                                                    imageVector = if (isRecording) Lucide.Stop else Lucide.Mic,
                                                    contentDescription = null,
                                                    tint = if (activeLoggingMode == ActiveLoggingMode.AUDIO || isRecording) Color(0xFFFFCC00) else optionsBarInactive,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Audio",
                                                    fontSize = 8.sp,
                                                    color = if (activeLoggingMode == ActiveLoggingMode.AUDIO || isRecording) Color(0xFFFFCC00) else optionsBarInactive,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }

                                        // 3: Scan Document OCR - Frame 1686555797
                                        if (visibleOptions.contains("Scan")) {
                                            Column(
                                                modifier = Modifier
                                                    .width(24.dp)
                                                    .height(36.dp)
                                                    .clickable(
                                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        triggerHaptic()
                                                        val tempFile = java.io.File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            tempFile
                                                        )
                                                        cameraUriParam.value = uri
                                                        cameraLauncher.launch(uri)
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                            ) {
                                                Icon(
                                                    imageVector = Lucide.Scan,
                                                    contentDescription = null,
                                                    tint = if (activeLoggingMode == ActiveLoggingMode.SCAN) Color(0xFFFFCC00) else optionsBarInactive,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Scan",
                                                    fontSize = 8.sp,
                                                    color = if (activeLoggingMode == ActiveLoggingMode.SCAN) Color(0xFFFFCC00) else optionsBarInactive,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }

                                        // 4: Attachment - Frame 1686555798
                                        if (visibleOptions.contains("Attach")) {
                                            Column(
                                                modifier = Modifier
                                                    .width(25.dp)
                                                    .height(36.dp)
                                                    .clickable(
                                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        triggerHaptic()
                                                        pickAttachmentLauncher.launch("*/*")
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                            ) {
                                                Icon(
                                                    imageVector = Lucide.Paperclip,
                                                    contentDescription = null,
                                                    tint = if (activeLoggingMode == ActiveLoggingMode.ATTACH) Color(0xFFFFCC00) else optionsBarInactive,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Attach",
                                                    fontSize = 8.sp,
                                                    color = if (activeLoggingMode == ActiveLoggingMode.ATTACH) Color(0xFFFFCC00) else optionsBarInactive,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }

                                        // 5: Note - Frame 1686555799
                                        if (visibleOptions.contains("Note")) {
                                            Column(
                                                modifier = Modifier
                                                    .width(24.dp)
                                                    .height(36.dp)
                                                    .clickable(
                                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        triggerHaptic()
                                                        activeLoggingMode = ActiveLoggingMode.NOTE
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                            ) {
                                                Icon(
                                                    imageVector = Lucide.FileText,
                                                    contentDescription = null,
                                                    tint = if (activeLoggingMode == ActiveLoggingMode.NOTE) Color(0xFFFFCC00) else optionsBarInactive,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Note",
                                                    fontSize = 8.sp,
                                                    color = if (activeLoggingMode == ActiveLoggingMode.NOTE) Color(0xFFFFCC00) else optionsBarInactive,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }

                                        // 6: Task - Direct Upcoming Todo
                                        if (visibleOptions.contains("Task")) {
                                            Column(
                                                modifier = Modifier
                                                    .width(24.dp)
                                                    .height(36.dp)
                                                    .clickable(
                                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        triggerHaptic()
                                                        activeLoggingMode = ActiveLoggingMode.UPCOMING
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                            ) {
                                                Icon(
                                                    imageVector = Lucide.CheckSquare,
                                                    contentDescription = "Upcoming Task",
                                                    tint = if (activeLoggingMode == ActiveLoggingMode.UPCOMING) Color(0xFFFFCC00) else optionsBarInactive,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Task",
                                                    fontSize = 8.sp,
                                                    color = if (activeLoggingMode == ActiveLoggingMode.UPCOMING) Color(0xFFFFCC00) else optionsBarInactive,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }

                                        // 7: Canvas Board - Open interactive infinite board
                                        if (visibleOptions.contains("Canvas")) {
                                            Column(
                                                modifier = Modifier
                                                    .width(32.dp)
                                                    .height(36.dp)
                                                    .clickable(
                                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        triggerHaptic()
                                                        showAddOptionsModal = false
                                                        activeLoggingMode = ActiveLoggingMode.NONE
                                                        currentTab = AppTab.Home
                                                        viewModel.createCanvasNote("Untitled Canvas") { createdCard ->
                                                            detailCardToShow = createdCard
                                                            startInEditMode = false
                                                        }
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                            ) {
                                                Icon(
                                                    imageVector = Lucide.Grid,
                                                    contentDescription = "Canvas Board",
                                                    tint = optionsBarInactive,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Canvas",
                                                    fontSize = 8.sp,
                                                    color = optionsBarInactive,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }
                                    }

                                    // Right element: open/close FAB circular yellow button (56px size, #121212 "X" icon)
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .align(Alignment.BottomEnd) // Force absolute math-alignment matching standard FAB
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFCC00))
                                            .clickable {
                                                triggerHaptic()
                                                showAddOptionsModal = false
                                                activeLoggingMode = ActiveLoggingMode.NONE
                                                if (isRecording) {
                                                    onMicClick()
                                                }
                                                viewModel.clearAttachment()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Lucide.Plus, // Rotate plus to form X smoothly!
                                            contentDescription = "Close panel",
                                            tint = Color(0xFF121212),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer(rotationZ = fabRotationAngle)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChatHistorySheet) {
        ChatHistoryModalSheet(
            viewModel = viewModel,
            onDismiss = { showChatHistorySheet = false },
            triggerHaptic = { triggerHaptic() }
        )
    }

    // Manage Reminder Dialog Options Modal
    if (activeTodoOptions != null) {
        val todo = activeTodoOptions!!
        val alarmTime = todoRemindersMap["reminder_${todo.cardId}_${todo.taskText}"] ?: 0L
        val autoAlarmTime = remember(todo.taskText) { parseDateTimeFromText(todo.taskText) }
        val isDismissed = alarmTime == -1L
        val hasManual = alarmTime > 0L
        val hasAuto = !isDismissed && autoAlarmTime != null && autoAlarmTime > System.currentTimeMillis()

        Dialog(
            onDismissRequest = { activeTodoOptions = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var animateIn by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                animateIn = true
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        activeTodoOptions = null
                    }
                    .background(Color.Black.copy(alpha = 0.5f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = animateIn,
                    enter = androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        )
                    ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                    exit = androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { }
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .border(0.5.dp, Color(0xFF383838), RoundedCornerShape(24.dp))
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Handle bar / Drag visual at top
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        if (dragAmount > 10) activeTodoOptions = null
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape)
                            )
                        }

                        Text(
                            text = "Manage Reminder",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.SansSerif
                        )

                        Text(
                            text = "Goal: ${todo.taskText.replace("**", "").replace("*", "").trim()}",
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontFamily = FontFamily.SansSerif
                        )

                        if (hasManual) {
                            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd hh:mm a", java.util.Locale.getDefault())
                            Text(
                                text = "SET FOR: ${sdf.format(java.util.Date(alarmTime))}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color(0xFFFFCC00),
                                fontFamily = FontFamily.SansSerif
                            )
                        } else if (hasAuto) {
                            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd hh:mm a", java.util.Locale.getDefault())
                            Text(
                                text = "AUTO DETECTED: ${sdf.format(java.util.Date(autoAlarmTime!!))}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color(0xFFFFCC00).copy(alpha = 0.7f),
                                fontFamily = FontFamily.SansSerif
                            )
                        } else {
                            Text(
                                text = "No active reminder set.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (hasManual || hasAuto) {
                                Button(
                                    onClick = {
                                        triggerHaptic()
                                        viewModel.cancelTodoAlarm(todo.cardId, todo.taskText)
                                        android.widget.Toast.makeText(context, "Reminder removed", android.widget.Toast.LENGTH_SHORT).show()
                                        activeTodoOptions = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C1515)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete", color = Color(0xFFFF453A), fontSize = 14.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    triggerHaptic()
                                    val calendar = java.util.Calendar.getInstance()
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            calendar.set(java.util.Calendar.YEAR, year)
                                            calendar.set(java.util.Calendar.MONTH, month)
                                            calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)

                                            android.app.TimePickerDialog(
                                                context,
                                                { _, hourOfDay, minute ->
                                                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                                    calendar.set(java.util.Calendar.MINUTE, minute)
                                                    calendar.set(java.util.Calendar.SECOND, 0)

                                                    viewModel.scheduleTodoAlarm(todo.cardId, todo.taskText, calendar.timeInMillis)
                                                    android.widget.Toast.makeText(context, "Reminder Scheduled!", android.widget.Toast.LENGTH_SHORT).show()
                                                    activeTodoOptions = null
                                                },
                                                calendar.get(java.util.Calendar.HOUR_OF_DAY),
                                                calendar.get(java.util.Calendar.MINUTE),
                                                false
                                            ).show()
                                        },
                                        calendar.get(java.util.Calendar.YEAR),
                                        calendar.get(java.util.Calendar.MONTH),
                                        calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (hasManual || hasAuto) "Edit" else "Set Reminder", color = Color.Black, fontSize = 14.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Multi-Format Preview and Download Dialog
    fullImageToOpen?.let { imagePath ->
        Dialog(onDismissRequest = { fullImageToOpen = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape = RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header row with download and close options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = File(imagePath).name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Download button
                            IconButton(
                                onClick = {
                                    triggerHaptic()
                                    // Trigger robust download logic
                                    try {
                                        val file = File(imagePath)
                                        if (file.exists()) {
                                            val resolver = context.contentResolver
                                            val contentValues = android.content.ContentValues().apply {
                                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, when (file.extension.lowercase()) {
                                                    "pdf" -> "application/pdf"
                                                    "csv" -> "text/csv"
                                                    "png" -> "image/png"
                                                    "jpg", "jpeg" -> "image/jpeg"
                                                    else -> "application/octet-stream"
                                                })
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                                                }
                                            }
                                            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                            if (uri != null) {
                                                resolver.openOutputStream(uri)?.use { outStream ->
                                                    file.inputStream().use { inStream ->
                                                        inStream.copyTo(outStream)
                                                    }
                                                }
                                                android.widget.Toast.makeText(context, "Downloaded to Downloads: ${file.name}", android.widget.Toast.LENGTH_LONG).show()
                                            } else {
                                                val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                                val destFile = File(publicDownloads, file.name)
                                                file.copyTo(destFile, overwrite = true)
                                                android.widget.Toast.makeText(context, "Downloaded to Downloads: ${destFile.name}", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        try {
                                            val file = File(imagePath)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "*/*"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Save or download file"))
                                        } catch (ex: Exception) {
                                            android.widget.Toast.makeText(context, "Could not complete download", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Lucide.Download,
                                    contentDescription = "Download File",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Close button
                            IconButton(
                                onClick = {
                                    triggerHaptic()
                                    fullImageToOpen = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("✕", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Content Viewer Box
                    val lowercasePath = imagePath.lowercase()
                    val isImage = lowercasePath.endsWith(".png") || lowercasePath.endsWith(".jpg") || lowercasePath.endsWith(".jpeg") || lowercasePath.endsWith(".webp") || lowercasePath.endsWith(".gif")
                    val isPdf = lowercasePath.endsWith(".pdf")
                    val isCsv = lowercasePath.endsWith(".csv")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isImage) {
                            AsyncImage(
                                model = File(imagePath),
                                contentDescription = "Full Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else if (isPdf) {
                            val pdfBitmap = remember(imagePath) {
                                try {
                                    val file = File(imagePath)
                                    val fileDescriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                    val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
                                    if (renderer.pageCount > 0) {
                                        val page = renderer.openPage(0)
                                        val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        page.close()
                                        renderer.close()
                                        fileDescriptor.close()
                                        bitmap
                                    } else {
                                        renderer.close()
                                        fileDescriptor.close()
                                        null
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            if (pdfBitmap != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Image(
                                        bitmap = pdfBitmap.asImageBitmap(),
                                        contentDescription = "PDF Visual Page Preview",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 350.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "PDF Document Preview (Page 1)",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                                ) {
                                    Icon(
                                        imageVector = Lucide.FileText,
                                        contentDescription = "PDF Document",
                                        tint = Color(0xFFFF453A),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "PDF Document Container",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${File(imagePath).length() / 1024} KB",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        } else if (isCsv) {
                            val csvLines = remember(imagePath) {
                                try {
                                    File(imagePath).readLines().take(50).map { line ->
                                        line.split(",").map { it.trim() }
                                    }
                                } catch (e: Exception) {
                                    emptyList<List<String>>()
                                }
                            }

                            if (csvLines.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 380.dp)
                                        .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                                        .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    val stateVertical = rememberScrollState()
                                    val stateHorizontal = rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .fadingEdges(topFadeHeight = 32.dp, bottomFadeHeight = 32.dp)
                                            .verticalScroll(stateVertical)
                                            .horizontalScroll(stateHorizontal)
                                    ) {
                                        csvLines.forEach { cells ->
                                            Row(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                cells.forEach { cellText ->
                                                    Box(
                                                        modifier = Modifier
                                                            .widthIn(min = 80.dp, max = 150.dp)
                                                            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                            .padding(6.dp)
                                                    ) {
                                                        Text(
                                                            text = cellText,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                                ) {
                                    Icon(
                                        imageVector = Lucide.FileText,
                                        contentDescription = "CSV File",
                                        tint = Color(0xFF34C759),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "CSV Data Sheet Document",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        } else {
                            // Other document type
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                            ) {
                                Icon(
                                    imageVector = Lucide.FileText,
                                    contentDescription = "Document",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Document Attachment",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${File(imagePath).length() / 1024} KB",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

    // Modal Recently Deleted Trash Bin Dialog (7 Day Auto Purge)
    if (showTrashBin) {
        val deletedList by viewModel.deletedCards.collectAsState(initial = emptyList())
        Dialog(onDismissRequest = { showTrashBin = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.80f)
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape = RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Lucide.Trash,
                                contentDescription = "Bin",
                                tint = Color(0xFFFFBD00),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recycle Trash Bin",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        IconButton(
                            onClick = { showTrashBin = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
                        }
                    }

                    Text(
                        text = "Keeping deleted slates for up to 7 days before permanent purge.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.SansSerif
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), thickness = 0.75.dp)

                    if (deletedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Lucide.Trash,
                                    contentDescription = null,
                                    tint = Color.Gray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Your Trash Bin is empty.",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fadingEdges(topFadeHeight = 24.dp, bottomFadeHeight = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(deletedList, key = { it.id }) { card ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                                        .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (card.rawInput.length > 50) card.rawInput.take(50) + "..." else card.rawInput,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val ageMs = System.currentTimeMillis() - (card.deletedTimestamp ?: System.currentTimeMillis())
                                        val ageDays = ageMs / (1000L * 60L * 60L * 24L)
                                        Text(
                                            text = "Deleted $ageDays days ago",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Restore Button
                                        IconButton(
                                            onClick = { viewModel.restoreCard(card) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Lucide.Play,
                                                contentDescription = "Restore",
                                                tint = Color.Green,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Permanently Delete Button
                                        IconButton(
                                            onClick = { cardToDeletePermanent = card },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Lucide.Trash,
                                                contentDescription = "Permanent Delete",
                                                tint = Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Soft Delete Confirmation Dialog ---
    cardToDeleteSoft?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDeleteSoft = null },
            title = { Text("Delete Slate?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif) },
            text = { Text("This will move the slate to the Trash Bin.", color = Color.LightGray, fontFamily = FontFamily.SansSerif) },
            confirmButton = {
                Button(
                    onClick = {
                        triggerHaptic()
                        viewModel.deleteCard(card)
                        cardToDeleteSoft = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF2A2A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToDeleteSoft = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
        )
    }

    // --- Permanent Delete Confirmation Dialog ---
    cardToDeletePermanent?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDeletePermanent = null },
            title = { Text("Permanently Delete?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif) },
            text = { Text("This slate will be permanently removed. This action cannot be undone.", color = Color.LightGray, fontFamily = FontFamily.SansSerif) },
            confirmButton = {
                Button(
                    onClick = {
                        triggerHaptic()
                        viewModel.permanentlyDeleteCard(card)
                        cardToDeletePermanent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF2A2A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToDeletePermanent = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
        )
    }
    if (false) { detailCardToShow?.let { card ->
        Dialog(
            onDismissRequest = { detailCardToShow = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var animateIn by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                animateIn = true
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        detailCardToShow = null
                    }
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(
                        bottom = 24.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = animateIn,
                    enter = androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        )
                    ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
                    exit = androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                // Prevent clicking on card from dismissing dialog
                            }
                            .fillMaxWidth()
                            .then(if (card.type == "note") Modifier.fillMaxHeight(0.7f) else Modifier.wrapContentHeight().heightIn(max = 700.dp))
                            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 4.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Handle bar / Drag visual at top
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (card.type == "note") 12.dp else 32.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        if (dragAmount > 10) detailCardToShow = null
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape)
                            )
                        }

                        if (card.type == "note") {
                            var editedTitle by remember(card.id) { mutableStateOf(card.rawInput) }
                            var editedContent by remember(card.id) { mutableStateOf(card.processedContent) }

                            // Title Input
                            androidx.compose.foundation.text.BasicTextField(
                                value = editedTitle,
                                onValueChange = { 
                                    editedTitle = it
                                    viewModel.updateCard(card.copy(rawInput = it))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold
                                ),
                                cursorBrush = SolidColor(Color(0xFFFFCC00)),
                                decorationBox = { innerTextField ->
                                    if (editedTitle.isEmpty()) {
                                        Text(
                                            text = "Title",
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            fontSize = 20.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            // Content Input
                            androidx.compose.foundation.text.BasicTextField(
                                value = editedContent,
                                onValueChange = { 
                                    editedContent = it
                                    viewModel.updateCard(card.copy(processedContent = it))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = 22.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFFFFCC00)),
                                decorationBox = { innerTextField ->
                                    if (editedContent.isEmpty()) {
                                        Text(
                                            text = "Take a note...",
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        } else {
                            // Scrollable content area
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // 1) AI Analysed response section
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MarkdownCardContent(
                                    markdownText = card.processedContent,
                                    onToggleCheck = { oldLine, newLine ->
                                        val newContent = card.processedContent.replaceFirst(oldLine, newLine)
                                        viewModel.updateCard(card.copy(processedContent = newContent))
                                    },
                                    onHapticClick = { triggerHaptic() },
                                    highlightYellow = true
                                )
                            }

                            // Divider
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.75.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            )

                            // 2) Original Input section
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ORIGINAL INPUT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = card.rawInput.ifEmpty { "No typed input recorded." },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = 22.sp
                                )
                            }

                            // 3) Sleek Red Option to Hear Audio (only if audio type)
                            if (card.type == "audio" && !card.mediaPath.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val isMemoPlaying = isAudioPlaying && activePlayingPath == card.mediaPath
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            triggerHaptic()
                                            playAudio(card.mediaPath ?: "")
                                        }
                                        .background(Color(0xFFCF2A2A).copy(alpha = 0.1f))
                                        .border(0.5.dp, Color(0xFFCF2A2A).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMemoPlaying) Lucide.Stop else Lucide.Play,
                                        contentDescription = if (isMemoPlaying) "Stop" else "Listen",
                                        tint = Color(0xFFCF2A2A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isMemoPlaying) "Stop playing recording" else "Hear voice audio",
                                        color = Color(0xFFCF2A2A),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }

                        }

                        if (card.type != "note") {
                            // Close Text / Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        triggerHaptic()
                                        detailCardToShow = null
                                        cardToEdit = card
                                    }
                                ) {
                                    Text(
                                        "Edit",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = {
                                        triggerHaptic()
                                        detailCardToShow = null
                                    }
                                ) {
                                    Text(
                                        "Close",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

// Modal Edit Slate Content Dialog
    cardToEdit?.let { card ->
        var editedRawInput by remember(card.id) { mutableStateOf(card.rawInput) }
        var editedProcessedContent by remember(card.id) { mutableStateOf(card.processedContent) }
        var isReprocessing by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = { if (!isReprocessing) cardToEdit = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var animateIn by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                animateIn = true
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isReprocessing) cardToEdit = null
                    }
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(
                        bottom = 24.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = animateIn,
                    enter = androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        )
                    ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
                    exit = androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { }
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .heightIn(max = 700.dp)
                            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 4.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Handle bar / Drag visual at top
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        if (dragAmount > 10) cardToEdit = null
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape)
                            )
                        }

                        // Scrollable content area
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Edit Slate",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // 1) ANALYZED OUTPUT SECTION
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "PROCESSED SLATE FORMAT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                                OutlinedTextField(
                                    value = editedProcessedContent,
                                    onValueChange = { editedProcessedContent = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp, max = 220.dp),
                                    textStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isReprocessing
                                )
                            }
                            
                            // Divider
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.75.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            )

                            // 2) ORIGINAL INPUT SECTION
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ORIGINAL INPUT (YOUR RAW THOUGHTS)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                                OutlinedTextField(
                                    value = editedRawInput,
                                    onValueChange = { editedRawInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 60.dp, max = 110.dp),
                                    textStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isReprocessing
                                )
                            }

                            // Dialog Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { cardToEdit = null },
                                    enabled = !isReprocessing
                                ) {
                                    Text("Cancel", fontFamily = FontFamily.SansSerif, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.updateCard(card.copy(
                                            rawInput = editedRawInput,
                                            processedContent = editedProcessedContent
                                        ))
                                        cardToEdit = null
                                    },
                                    enabled = !isReprocessing,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCC00))
                                ) {
                                    Text("Save Changes", fontFamily = FontFamily.SansSerif, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}
}

@Composable
fun SwipeableCardContainer(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 90.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(getCornerShape(18f))
    ) {
        val currentOffset = offsetX.value
        
        // Background swipe actions reveal
        if (currentOffset != 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (currentOffset > 0) Color(0xFFD32F2F).copy(alpha = 0.9f) // Bright beautiful crimson for Delete
                        else Color(0xFFFFBD00).copy(alpha = 0.92f) // Beautiful Alter Yellow for Edit!
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = if (currentOffset > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (currentOffset > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.Trash,
                            contentDescription = "Swipe right to delete",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Release to Delete",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Release to Edit Input",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Icon(
                            imageVector = Lucide.Edit,
                            contentDescription = "Swipe left to edit",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Foreground sliding card
        Box(
            modifier = Modifier
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value > swipeThreshold) {
                                scope.launch {
                                    offsetX.animateTo(size.width.toFloat(), spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    onSwipeRight()
                                    offsetX.snapTo(0f)
                                }
                            } else if (offsetX.value < -swipeThreshold) {
                                scope.launch {
                                    offsetX.animateTo(-size.width.toFloat(), spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    onSwipeLeft()
                                    offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                }
                            } else {
                                scope.launch {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newVal = offsetX.value + dragAmount
                                offsetX.snapTo(newVal)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
fun AlterCardItem(
    card: AlterCard,
    isHighlighted: Boolean = false,
    activePlayingPath: String?,
    isAudioPlaying: Boolean,
    onPlayPauseAudio: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onAddCalendar: () -> Unit,
    onUpdateCard: (AlterCard) -> Unit,
    onHapticClick: () -> Unit,
    todoRemindersMap: Map<String, Long>,
    onCardClick: () -> Unit,
    showDateInsteadOfTime: Boolean = false
) {
    var isCopiedConfirmed by remember { mutableStateOf(false) }
    var showCardMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "GlowTransition")
    val glowAlpha by if (isHighlighted) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowAlpha"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    LaunchedEffect(isCopiedConfirmed) {
        if (isCopiedConfirmed) {
            delay(1500)
            isCopiedConfirmed = false
        }
    }

    val isCanvasCard = card.type == "note" && (!card.canvasData.isNullOrBlank() || card.rawInput.startsWith("Untitled Canvas", ignoreCase = true))

    val sourceLabel = when {
        isCanvasCard -> "Canvas"
        card.type == "audio" -> "Audio"
        card.type == "image" -> "Scan"
        card.type == "pdf" -> "Attachment"
        card.type == "note" -> "Note"
        card.type == "upcoming" -> "Task"
        else -> "Text"
    }

    val iconVector = when {
        isCanvasCard -> Lucide.Grid
        card.type == "audio" -> Lucide.Mic
        card.type == "image" -> Lucide.Image
        card.type == "pdf" -> Lucide.Paperclip
        card.type == "note" -> Lucide.FileText
        card.type == "upcoming" -> Lucide.CheckSquare
        else -> Lucide.Edit
    }

    val hasCardReminder = remember(card, todoRemindersMap) {
        val lines = card.processedContent.split("\n")
        lines.any { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {
                val isCheckedText = trimmedLine.startsWith("- [x] ", ignoreCase = true) || 
                                    trimmedLine.startsWith("* [x] ", ignoreCase = true) || 
                                    trimmedLine.startsWith("- [X] ", ignoreCase = true) || 
                                    trimmedLine.startsWith("* [X] ", ignoreCase = true) ||
                                    trimmedLine.startsWith("- [x]") || 
                                    trimmedLine.startsWith("* [x]")
                if (!isCheckedText) {
                    val taskText = cleanLinePrefix(trimmedLine)
                    if (taskText.isNotEmpty()) {
                        val alarmTime = todoRemindersMap["reminder_${card.id}_${taskText}"] ?: 0L
                        val isDismissed = alarmTime == -1L
                        val isManualSet = alarmTime > 0L
                        val autoTime = parseDateTimeFromText(taskText)
                        val isAutoActive = !isDismissed && autoTime != null && autoTime > System.currentTimeMillis()
                        isManualSet || isAutoActive
                    } else false
                } else false
            } else false
        }
    }

    val formattedTime = remember(card.timestamp, showDateInsteadOfTime) {
        val pattern = if (showDateInsteadOfTime) "dd/MM" else "h:mm a"
        val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        sdf.format(java.util.Date(card.timestamp)).lowercase()
    }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    SwipeableCardContainer(
        onSwipeRight = onDelete,
        onSwipeLeft = onEdit
    ) {
        val outerCardShape = getCornerShape(18f)
        val innerCardShape = getCornerShape(14f)

        // Outer CSS Envelope: Background #18181B (SlateGrayCard) with radius 18dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 248.dp)
                .shadow(elevation = 2.dp, shape = outerCardShape)
                .clip(outerCardShape)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = outerCardShape
                )
                .clickable {
                    onHapticClick()
                    onCardClick()
                }
                .then(
                    if (isHighlighted) {
                        Modifier.border(
                            width = 2.dp,
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFBD00).copy(alpha = glowAlpha),
                                    Color(0xFFFF8C00).copy(alpha = glowAlpha)
                                )
                            ),
                            shape = outerCardShape
                        )
                    } else {
                        Modifier
                    }
                )
                .testTag("alter_card_item_${card.id}")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top grey part of EXACTLY 24px (24.dp) only, showing non-priority info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Name in regular font of which category it is
                    Text(
                        text = sourceLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontFamily = FontFamily.SansSerif
                    )

                    // Right: category icon (like audio = waves) | divider | clock icon | creation time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val categoryIcon = when {
                            isCanvasCard -> Lucide.Grid
                            card.type == "audio" -> Lucide.Waves
                            card.type == "image" -> Lucide.Image
                            card.type == "pdf" -> Lucide.Paperclip
                            card.type == "note" -> Lucide.FileText
                            else -> Lucide.Type
                        }

                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = "Category Icon",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(11.dp)
                        )

                        if (card.personalNotes.isNotBlank() && card.type != "note") {
                            Box(
                                modifier = Modifier
                                    .width(0.75.dp)
                                    .height(10.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            )

                            Icon(
                                imageVector = Lucide.FileText,
                                contentDescription = "Contains personal notes indicator",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                modifier = Modifier.size(11.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(0.75.dp)
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        )

                        Icon(
                            imageVector = Lucide.Clock,
                            contentDescription = "Clock Icon",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(11.dp)
                        )

                        Text(
                            text = formattedTime,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Inner block card showing AI analyzed response:
                // Padding of 4px from bottom, left, and right, and 0px from top of outer card.
                // Placed 24px down as the 24px header part is above it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = innerCardShape
                        )
                        .drawBehind {
                            if (isCanvasCard) {
                                // Subtle dotted matrix pattern for canvas boards only
                                val spacing = 16.dp.toPx()
                                val dotRadius = 1.0.dp.toPx()
                                val dotColor = Color(0xFF2E2E2E)
                                var x = 8.dp.toPx()
                                while (x < size.width) {
                                    var y = 8.dp.toPx()
                                    while (y < size.height) {
                                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
                                        y += spacing
                                    }
                                    x += spacing
                                }
                            }
                        }
                ) {
                    val cardContentScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 210.dp)
                            .verticalScroll(cardContentScrollState)
                    ) {
                        // Upper block containing AI analyzed response content with its own internal layout padding
                        val bottomPadding = if (card.type == "note" || card.rawInput.isBlank() || (isCanvasCard && card.processedContent.isBlank())) 12.dp else 4.dp
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Show Heading for note (card.rawInput)
                            if (isCanvasCard) {
                                Text(
                                    text = card.rawInput.ifBlank { "Untitled Canvas" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.SansSerif
                                )
                            } else if (card.type == "note" && card.rawInput.isNotBlank()) {
                                Text(
                                    text = card.rawInput,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            // Image / OCR scan preview
                            if (card.type == "image") {
                                card.mediaPath?.let { path ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onOpenImage(path) }
                                    ) {
                                        AsyncImage(
                                            model = File(path),
                                            contentDescription = "OCR JPEG preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                "Analyze ↗",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Standard Markdown parser content (only when text is present and not canvas)
                            if (!isCanvasCard && card.processedContent.isNotBlank()) {
                                MarkdownCardContent(
                                    markdownText = card.processedContent,
                                    onToggleCheck = { oldLine, newLine ->
                                        val updatedText = card.processedContent.replace(oldLine, newLine)
                                        onUpdateCard(card.copy(processedContent = updatedText))
                                    },
                                    onHapticClick = onHapticClick,
                                    highlightYellow = true
                                )
                            }
                        }

                        // Inside the black card, nested grey card containing user raw input:
                        // Matches the exact style relation of how the black box fits in the main slate-grey card
                        if (card.type != "note" && card.rawInput.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface, // Pair exactly with the outer Slate Card
                                        shape = RoundedCornerShape(10.dp) // Concentric: black box is 14dp, minus 4dp padding = 10dp
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = card.rawInput,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownCardContent(
    markdownText: String,
    onToggleCheck: ((String, String) -> Unit)? = null,
    onHapticClick: (() -> Unit)? = null,
    highlightYellow: Boolean = false
) {
    val lines = markdownText.split("\n")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { line ->
            var trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                return@forEach
            }

            // Remove any trailing asterisk or surrounding formatting artifacts
            if (trimmedLine.startsWith("*") && trimmedLine.endsWith("*") && !trimmedLine.startsWith("**")) {
                trimmedLine = trimmedLine.removePrefix("*").removeSuffix("*").trim()
            }

            // 1) Divider check (if it starts with "--", "---", "===", "***", etc., acting as a divider)
            val isDivider = trimmedLine.startsWith("--") || 
                            trimmedLine.startsWith("---") || 
                            trimmedLine.startsWith("***") || 
                            trimmedLine == "---" || 
                            trimmedLine == "==" || 
                            trimmedLine.startsWith("===")
            
            if (isDivider) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                return@forEach
            }

            // 2) Heading check - supports single or multiple consecutive `#`
            // Removing all the multiple leading hashes '###' so they don't render prefix symbols.
            if (trimmedLine.startsWith("#")) {
                // Count the number of hashes
                val hashCount = trimmedLine.takeWhile { it == '#' }.length
                val textOnly = trimmedLine.drop(hashCount).trim()
                
                if (textOnly.isNotEmpty()) {
                    val fontSize = when (hashCount) {
                        1 -> 18.sp
                        2 -> 16.sp
                        else -> 14.sp
                    }
                    val fontWeight = if (hashCount == 1) FontWeight.Bold else FontWeight.SemiBold
                    
                    LinkifyText(
                        text = parseBoldText(textOnly, highlightYellow),
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = fontWeight,
                            fontSize = fontSize,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                return@forEach
            }

            // 3) Checkboxes (To-do / Checklist tasks)
            val parsedTodo = parseTodoLine(line)

            when {
                parsedTodo != null && !parsedTodo.isCompleted -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onToggleCheck != null) {
                                onHapticClick?.invoke()
                                val oldLine = line
                                val newLine = if (line.contains("☐")) {
                                    line.replace("☐", "☑")
                                } else if (line.contains("[ ]") || line.contains("[]") || line.contains("[-]")) {
                                    line.replace(Regex("\\[[ \\-]?\\]"), "[x]")
                                } else {
                                    "☑ ${parsedTodo.taskText}"
                                }
                                onToggleCheck?.invoke(oldLine, newLine)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlterCheckbox(
                            checked = false,
                            onCheckedChange = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        LinkifyText(
                            text = parseBoldText(parsedTodo.taskText, highlightYellow),
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        )
                    }
                }
                parsedTodo != null && parsedTodo.isCompleted -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onToggleCheck != null) {
                                onHapticClick?.invoke()
                                val oldLine = line
                                val newLine = if (line.contains("☑") || line.contains("☒")) {
                                    line.replace("☑", "☐").replace("☒", "☐")
                                } else if (line.contains("[x]", ignoreCase = true) || line.contains("[v]", ignoreCase = true) || line.contains("[✓]")) {
                                    line.replace(Regex("\\[[xXvV✓]\\]"), "[ ]")
                                } else {
                                    "☐ ${parsedTodo.taskText}"
                                }
                                onToggleCheck?.invoke(oldLine, newLine)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlterCheckbox(
                            checked = true,
                            onCheckedChange = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        LinkifyText(
                            text = parseBoldText(parsedTodo.taskText, highlightYellow),
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
                // Bullet points
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || trimmedLine.startsWith("+ ") || trimmedLine.startsWith("• ") -> {
                    val bulletText = cleanLinePrefix(trimmedLine)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        LinkifyText(
                            text = parseBoldText(bulletText, highlightYellow),
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        )
                    }
                }
                else -> {
                    // Regular text line. Let's make sure if it consists only of asterisks, skip it
                    val cleanText = trimmedLine.replace("*", "").trim()
                    if (cleanText.isEmpty()) {
                        return@forEach
                    }
                    LinkifyText(
                        text = parseBoldText(trimmedLine, highlightYellow),
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveEditorSkin(
    value: String,
    onValueChange: (String) -> Unit,
    headerTitle: String,
    headerIcon: androidx.compose.ui.graphics.vector.ImageVector = Lucide.FileText,
    placeholder: String = "Tap to add details...",
    modifier: Modifier = Modifier,
    showAddActions: Boolean = true,
    onHapticClick: (() -> Unit)? = null
) {
    val isTitle = headerTitle.equals("TITLE", ignoreCase = true)
    
    // Parse tasks in the text for the interactive checklist section
    val lines = remember(value) { if (value.isEmpty()) emptyList() else value.split("\n") }
    val tasksInText = remember(lines) {
        val list = mutableListOf<Pair<Int, ParsedTodoItem>>()
        lines.forEachIndexed { index, line ->
            val parsed = parseTodoLine(line)
            if (parsed != null && parsed.taskText.isNotBlank()) {
                list.add(Pair(index, parsed))
            }
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = headerIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = headerTitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            if (showAddActions && !isTitle) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .clickable {
                                onHapticClick?.invoke()
                                val newContent = if (value.isBlank()) "☐ " else if (value.endsWith("\n")) "${value}☐ " else "$value\n☐ "
                                onValueChange(newContent)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ Task",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .clickable {
                                onHapticClick?.invoke()
                                val newContent = if (value.isBlank()) "• " else if (value.endsWith("\n")) "${value}• " else "$value\n• "
                                onValueChange(newContent)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ Bullet",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .clickable {
                                onHapticClick?.invoke()
                                val newContent = if (value.isBlank()) "# " else if (value.endsWith("\n")) "${value}# " else "$value\n# "
                                onValueChange(newContent)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ Header",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // If there are tasks, render an interactive quick-check checklist panel
        if (tasksInText.isNotEmpty() && !isTitle) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val completedCount = tasksInText.count { it.second.isCompleted }
                Text(
                    text = "TASKS ($completedCount/${tasksInText.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )

                tasksInText.forEach { (lineIndex, task) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onHapticClick?.invoke()
                                val currentLines = lines.toMutableList()
                                if (lineIndex < currentLines.size) {
                                    val targetLine = currentLines[lineIndex]
                                    val isNowCompleted = !task.isCompleted
                                    val updatedLine = if (isNowCompleted) {
                                        if (targetLine.contains("☐")) targetLine.replace("☐", "☑")
                                        else if (targetLine.contains("[ ]")) targetLine.replace("[ ]", "[x]")
                                        else if (targetLine.contains("[]")) targetLine.replace("[]", "[x]")
                                        else "☑ ${task.taskText}"
                                    } else {
                                        if (targetLine.contains("☑") || targetLine.contains("☒")) targetLine.replace("☑", "☐").replace("☒", "☐")
                                        else if (targetLine.contains("[x]", ignoreCase = true)) targetLine.replace(Regex("\\[[xX]\\]"), "[ ]")
                                        else "☐ ${task.taskText}"
                                    }
                                    currentLines[lineIndex] = updatedLine
                                    onValueChange(currentLines.joinToString("\n"))
                                }
                            }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlterCheckbox(
                            checked = task.isCompleted,
                            onCheckedChange = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = task.taskText,
                            style = TextStyle(
                                color = if (task.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Full multiline text editor
        BasicTextField(
            value = value,
            onValueChange = { rawNewText ->
                val formatted = if (isTitle) rawNewText else formatEditorInput(rawNewText)
                onValueChange(formatted)
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isTitle) Modifier.heightIn(min = 36.dp)
                    else Modifier.heightIn(min = 80.dp)
                ),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (isTitle) 16.sp else 14.sp,
                fontWeight = if (isTitle) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                lineHeight = if (isTitle) 22.sp else 21.sp
            ),
            cursorBrush = SolidColor(Color(0xFFFFCC00)),
            singleLine = isTitle,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            fontSize = if (isTitle) 16.sp else 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun LinkifyText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val hasUrl = text.getStringAnnotations(tag = "URL", start = 0, end = text.length).isNotEmpty()
    if (hasUrl) {
        androidx.compose.foundation.text.ClickableText(
            text = text,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            onClick = { offset ->
                text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        var url = annotation.item
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://$url"
                        }
                        try {
                            uriHandler.openUri(url)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
            }
        )
    } else {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@Composable
fun parseBoldText(text: String, highlightYellow: Boolean = false): AnnotatedString {
    return parseBoldAndLinkText(text, highlightYellow)
}

@Composable
fun parseBoldAndLinkText(text: String, highlightYellow: Boolean = false): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val cleanText = text.replace("**", "").replace("*", "")
    
    // Pattern to find HTTP/HTTPS URLs or www. links
    val urlPattern = java.util.regex.Pattern.compile(
        "(https?://[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
        java.util.regex.Pattern.CASE_INSENSITIVE
    )
    
    // 1) First parse date times for yellow highlight if requested (REMOVED "|at" to meet Requirement 8)
    val dateTimeRegex = Regex("\\b(?:tomorrow|today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday|january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)\\b|\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b|\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b|\\b\\d{1,2}\\s*(?:am|pm)\\b", RegexOption.IGNORE_CASE)
    val yellowRanges = mutableListOf<IntRange>()
    if (highlightYellow) {
        dateTimeRegex.findAll(cleanText).forEach { matchResult ->
            yellowRanges.add(matchResult.range)
        }
    }
    
    val matcher = urlPattern.matcher(cleanText)
    var lastIndex = 0
    
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        val url = matcher.group()
        
        // Append text from last index up to start of URL
        if (start > lastIndex) {
            val normalText = cleanText.substring(lastIndex, start)
            builder.appendNormalAndYellow(normalText, lastIndex, yellowRanges)
        }
        
        // Push URL link annotation and link style
        builder.pushStringAnnotation(tag = "URL", annotation = url)
        builder.pushStyle(SpanStyle(color = Color(0xFF007AFF), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Normal))
        builder.append(url)
        builder.pop()
        builder.pop()
        
        lastIndex = end
    }
    
    if (lastIndex < cleanText.length) {
        val remainingText = cleanText.substring(lastIndex)
        builder.appendNormalAndYellow(remainingText, lastIndex, yellowRanges)
    }
    
    return builder.toAnnotatedString()
}

// Extension to append and highlight yellow if matching
private fun AnnotatedString.Builder.appendNormalAndYellow(
    textSegment: String,
    segmentOffset: Int,
    yellowRanges: List<IntRange>
) {
    var localIdx = 0
    val segmentLength = textSegment.length
    
    while (localIdx < segmentLength) {
        val globalIdx = segmentOffset + localIdx
        // Check if global index falls inside any yellow range
        val matchingRange = yellowRanges.find { globalIdx in it }
        
        if (matchingRange != null) {
            // Find length of yellow text we can append in this segment
            val rangeEndInSegment = (matchingRange.last + 1 - segmentOffset).coerceAtMost(segmentLength)
            val yellowSubString = textSegment.substring(localIdx, rangeEndInSegment)
            
            pushStyle(SpanStyle(color = Color(0xFFFFBD00), fontWeight = FontWeight.Normal))
            append(yellowSubString)
            pop()
            
            localIdx = rangeEndInSegment
        } else {
            // Find next yellow range start or end of segment
            val nextYellowStart = yellowRanges
                .map { it.first }
                .filter { it > globalIdx }
                .minOrNull()
            
            val nextStop = if (nextYellowStart != null) {
                (nextYellowStart - segmentOffset).coerceAtMost(segmentLength)
            } else {
                segmentLength
            }
            
            val normalSubString = textSegment.substring(localIdx, nextStop)
            append(normalSubString)
            localIdx = nextStop
        }
    }
}

fun isActionRequiredTask(text: String): Boolean {
    val lowercase = text.lowercase()
    
    // 1. Check for action-oriented phrases/verbs/words and upcoming events
    val hasActionPhrases = lowercase.contains("i want to") ||
                           lowercase.contains("i have to") ||
                           lowercase.contains("i'm supposed to") ||
                           lowercase.contains("i am supposed to") ||
                           lowercase.contains("need to") ||
                           lowercase.contains("should") ||
                           lowercase.contains("must") ||
                           lowercase.contains("todo") ||
                           lowercase.contains("task") ||
                           lowercase.contains("schedule") ||
                           lowercase.contains("reminder") ||
                           lowercase.contains("please") ||
                           lowercase.contains("remember to") ||
                           lowercase.contains("buy") ||
                           lowercase.contains("call") ||
                           lowercase.contains("get") ||
                           lowercase.contains("send") ||
                           lowercase.contains("meet") ||
                           lowercase.contains("pay") ||
                           lowercase.contains("do ") ||
                           lowercase.contains("finish") ||
                           lowercase.contains("complete") ||
                           lowercase.contains("event") ||
                           lowercase.contains("meeting") ||
                           lowercase.contains("appointment") ||
                           lowercase.contains("interview") ||
                           lowercase.contains("flight") ||
                           lowercase.contains("booking") ||
                           lowercase.contains("reservation") ||
                           lowercase.contains("class") ||
                           lowercase.contains("seminar") ||
                           lowercase.contains("webinar") ||
                           lowercase.contains("session") ||
                           lowercase.contains("party") ||
                           lowercase.contains("lunch") ||
                           lowercase.contains("dinner") ||
                           lowercase.contains("wedding") ||
                           lowercase.contains("zoom")
    
    // 2. Check for date or time:
    val hasDateTime = parseDateTimeFromText(text) != null || 
                      lowercase.contains("today") ||
                      lowercase.contains("tomorrow") ||
                      lowercase.contains("yesterday") ||
                      lowercase.contains("monday") ||
                      lowercase.contains("tuesday") ||
                      lowercase.contains("wednesday") ||
                      lowercase.contains("thursday") ||
                      lowercase.contains("friday") ||
                      lowercase.contains("saturday") ||
                      lowercase.contains("sunday") ||
                      lowercase.contains("at ") ||
                      Regex("\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
                      Regex("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    
    return hasActionPhrases || hasDateTime
}

fun cleanLinePrefix(line: String): String {
    var trimmed = line.trim()
    
    // Remove brackets with any content or without: - [ ], * [x], 1. [ ], • [ ], [ ]
    val checkboxPrefixRegex = Regex("^(?:[-*+•]|\\d+[.)])?\\s*\\[[ xXvV✓\\-]?\\]\\s*")
    trimmed = trimmed.replace(checkboxPrefixRegex, "")

    // Remove bullets: -, *, +, •, or numbered list prefixes like 1. or 2)
    val bulletPrefixRegex = Regex("^(?:[-*+•]|\\d+[.)])\\s*")
    trimmed = trimmed.replace(bulletPrefixRegex, "")

    // Remove unicode boxes: ☐, ☑, ☒
    if (trimmed.startsWith("☐") || trimmed.startsWith("☑") || trimmed.startsWith("☒")) {
        trimmed = trimmed.drop(1).trim()
    }

    // Strip bold/italic asterisks from scheduling tasks
    trimmed = trimmed.replace("**", "").replace("*", "")

    return trimmed.trim()
}

fun parseDateTimeFromText(text: String): Long? {
    try {
        val lower = text.lowercase().trim()
        val now = java.util.Calendar.getInstance()
        
        // Helper function to extract Hour & Minute from text
        fun extractTime(str: String): Pair<Int, Int>? {
            // Priority 1: Match numbers with explicit "am" or "pm" or a colon (e.g. 5pm, 14:30, 2:15 pm)
            val specificTimeRegex = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b|\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE)
            specificTimeRegex.find(str)?.let { match ->
                if (match.groupValues[1].isNotEmpty()) {
                    val hour = match.groupValues[1].toInt()
                    val minStr = match.groupValues[2]
                    val minute = if (minStr.isNotEmpty()) minStr.toInt() else 0
                    val ampm = match.groupValues[3].lowercase()
                    var h = hour
                    if (ampm == "pm" && h < 12) h += 12
                    if (ampm == "am" && h == 12) h = 0
                    if (h in 0..23 && minute in 0..59) {
                        return Pair(h, minute)
                    }
                } else if (match.groupValues[4].isNotEmpty()) {
                    val hour = match.groupValues[4].toInt()
                    val minute = match.groupValues[5].toInt()
                    val ampm = match.groupValues[6].lowercase()
                    var h = hour
                    if (ampm == "pm" && h < 12) h += 12
                    if (ampm == "am" && h == 12) h = 0
                    if (h in 0..23 && minute in 0..59) {
                        return Pair(h, minute)
                    }
                }
            }

            // Priority 2: Preceded by "at" (e.g. "at 5", "at 14")
            val atRegex = Regex("\\bat\\s+(\\d{1,2})\\b", RegexOption.IGNORE_CASE)
            atRegex.find(str)?.let { match ->
                val hour = match.groupValues[1].toInt()
                if (hour in 0..23) {
                    return Pair(hour, 0)
                }
            }
            return null
        }

        // Pattern 1: Tomorrow mentions
        if (lower.contains("tomorrow")) {
            val (hour, minute) = extractTime(lower) ?: Pair(9, 0) // Default to 9:00 AM
            val cal = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        // Pattern 2: Today mentions
        if (lower.contains("today")) {
            val (hour, minute) = extractTime(lower) ?: Pair(18, 0) // Default to 6:00 PM
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis < System.currentTimeMillis()) {
                val hasSpecificTime = extractTime(lower) != null
                if (!hasSpecificTime) {
                    cal.timeInMillis = System.currentTimeMillis() + (2 * 60 * 60 * 1000) // 2 hours later
                } else {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, 1) // default to tomorrow same time if past
                }
            }
            return cal.timeInMillis
        }

        // Pattern 3: Slash / hyphen numerical dates (e.g. 10/14 or 10/14/2026 or 10-14)
        val slashRegex = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")
        slashRegex.find(lower)?.let { match ->
            val p1 = match.groupValues[1].toInt()
            val p2 = match.groupValues[2].toInt()
            val p3Str = match.groupValues[3]
            
            val month = p1 - 1
            val day = p2
            val year = if (p3Str.isNotEmpty()) {
                val yearVal = p3Str.toInt()
                if (yearVal < 100) 2000 + yearVal else yearVal
            } else {
                now.get(java.util.Calendar.YEAR)
            }
            
            val (hour, minute) = extractTime(lower) ?: Pair(9, 0)
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, month)
                set(java.util.Calendar.DAY_OF_MONTH, day)
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        // Pattern 4: Calendar Months text (e.g. June 10, may 5th, jul 4)
        val months = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
        )
        val shortMonths = listOf(
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec"
        )
        for (i in 0..11) {
            val mFull = months[i]
            val mShort = shortMonths[i]
            val monthRegex = Regex("\\b(?:$mFull|$mShort)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
            monthRegex.find(lower)?.let { match ->
                val day = match.groupValues[1].toInt()
                val (hour, minute) = extractTime(lower) ?: Pair(9, 0)
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.MONTH, i)
                    set(java.util.Calendar.DAY_OF_MONTH, day)
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis < System.currentTimeMillis()) {
                    cal.add(java.util.Calendar.YEAR, 1)
                }
                return cal.timeInMillis
            }
        }

        // Pattern 5: Days of week (e.g. Monday, Friday)
        val days = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        for (i in 0..6) {
            if (lower.contains(days[i])) {
                val targetDayOfWeek = i + 1
                val cal = java.util.Calendar.getInstance()
                val currentDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                var daysDiff = targetDayOfWeek - currentDayOfWeek
                if (daysDiff <= 0) {
                    daysDiff += 7
                }
                val (hour, minute) = extractTime(lower) ?: Pair(9, 0)
                cal.apply {
                    add(java.util.Calendar.DAY_OF_YEAR, daysDiff)
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }
        }

        // Pattern 6: Time only (e.g. at 5pm, at 14:00)
        extractTime(lower)?.let { (hour, minute) ->
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis < System.currentTimeMillis()) {
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            return cal.timeInMillis
        }

    } catch (e: Exception) {
        // Safe fallback
    }
    return null
}




@Composable
fun TodoItemRow(
    todo: TodoItem,
    cards: List<AlterCard>,
    viewModel: AlterViewModel,
    todoRemindersMap: Map<String, Long>,
    context: Context,
    onShowReminderChooser: (TodoItem) -> Unit,
    onCardClick: (AlterCard) -> Unit = {},
    enableDisintegration: Boolean = true,
    staggerIndex: Int = 0,
    staggerTrigger: Long = 0L
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    fun executeCompletionToggle() {
        val parentCard = cards.find { it.id == todo.cardId }
        if (parentCard != null) {
            fun toggleInText(text: String): Pair<String, Boolean> {
                if (text.isBlank()) return Pair(text, false)
                val lines = text.split("\n")
                var toggled = false
                val updatedLines = lines.map { currentLine ->
                    val parsed = parseTodoLine(currentLine)
                    if (!toggled && parsed != null && (currentLine == todo.originalLine || parsed.taskText == todo.taskText)) {
                        toggled = true
                        val indent = currentLine.takeWhile { it.isWhitespace() }
                        if (todo.isCompleted) {
                            if (currentLine.contains("☑") || currentLine.contains("☒")) {
                                currentLine.replace("☑", "☐").replace("☒", "☐")
                            } else if (currentLine.contains("[x]", ignoreCase = true) || currentLine.contains("[v]", ignoreCase = true) || currentLine.contains("[✓]")) {
                                currentLine.replace(Regex("\\[[xXvV✓]\\]"), "[ ]")
                            } else {
                                "${indent}☐ ${parsed.taskText}"
                            }
                        } else {
                            if (currentLine.contains("☐")) {
                                currentLine.replace("☐", "☑")
                            } else if (currentLine.contains("[ ]") || currentLine.contains("[]") || currentLine.contains("[-]")) {
                                currentLine.replace(Regex("\\[[ \\-]?\\]"), "[x]")
                            } else {
                                "${indent}☑ ${parsed.taskText}"
                            }
                        }
                    } else {
                        currentLine
                    }
                }
                return Pair(updatedLines.joinToString("\n"), toggled)
            }

            val (newProcessed, inProcessed) = toggleInText(parentCard.processedContent)
            val (newNotes, inNotes) = toggleInText(parentCard.personalNotes)
            val (newRaw, inRaw) = toggleInText(parentCard.rawInput)

            viewModel.updateCard(
                parentCard.copy(
                    processedContent = if (inProcessed) newProcessed else parentCard.processedContent,
                    personalNotes = if (inNotes) newNotes else parentCard.personalNotes,
                    rawInput = if (inRaw) newRaw else parentCard.rawInput
                )
            )
        }
    }

    val alarmTime = todoRemindersMap["reminder_${todo.cardId}_${todo.taskText}"] ?: 0L
    val autoAlarmTime = remember(todo.taskText) { parseDateTimeFromText(todo.taskText) }
    
    val isDismissed = alarmTime == -1L
    val isAlarmSet = alarmTime > 0L
    val isAutoAlarmActive = !isAlarmSet && !isDismissed && autoAlarmTime != null && autoAlarmTime > System.currentTimeMillis()
    
    val displayAlarmTime = if (isAlarmSet) alarmTime else if (isAutoAlarmActive) autoAlarmTime ?: 0L else 0L
    val hasAnyAlarm = isAlarmSet || isAutoAlarmActive

    val (formattedDate, formattedTime) = remember(displayAlarmTime) {
        if (displayAlarmTime > 0L) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = displayAlarmTime }
            val dateSdf = java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault())
            val timeSdf = if (cal.get(java.util.Calendar.MINUTE) == 0) {
                java.text.SimpleDateFormat("h a", java.util.Locale.getDefault())
            } else {
                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            }
            Pair(
                dateSdf.format(java.util.Date(displayAlarmTime)),
                timeSdf.format(java.util.Date(displayAlarmTime)).uppercase()
            )
        } else {
            Pair("", "")
        }
    }

    val taskKey = "${todo.cardId}_${todo.originalLine}_${todo.taskText}"

    QuestRowWrapper(
        taskKey = taskKey,
        isCompleted = todo.isCompleted,
        onCompleted = { executeCompletionToggle() },
        enableDisintegration = enableDisintegration,
        staggerIndex = staggerIndex,
        staggerTrigger = staggerTrigger
    ) { phase, isChecked, startCompletion ->
        val isAnimating = phase != QuestAnimationPhase.IDLE && phase != QuestAnimationPhase.COMPLETED

        // Outer Gray Container Card matching the app's signature 3-layer dark theme:
        // 1. Background = Pitch Black
        // 2. Outer Card = Signature Gray (MaterialTheme.colorScheme.surface)
        // 3. Inner Card = Pitch Black container (MaterialTheme.colorScheme.background) holding reminder text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Checkbox on the left, outside the inner black container (in the gray container)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isAnimating) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            if (!todo.isCompleted) {
                                startCompletion()
                            } else {
                                executeCompletionToggle()
                            }
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (phase == QuestAnimationPhase.LOADING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFFFCC00),
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    } else {
                        AlterCheckbox(
                            checked = isChecked,
                            onCheckedChange = null
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Inner Pitch Black container holding the reminder text
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            val parentCard = cards.find { it.id == todo.cardId }
                            if (parentCard != null) {
                                onCardClick(parentCard)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = parseBoldText(todo.taskText, highlightYellow = true),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 18.sp,
                        color = if (todo.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        style = TextStyle(
                            textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Three-line hierarchy reminder indicator on the right:
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .widthIn(min = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onShowReminderChooser(todo)
                        }
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    // Line 1: Clock Icon
                    Icon(
                        imageVector = Lucide.Clock,
                        contentDescription = "Schedule Reminder",
                        tint = if (hasAnyAlarm) Color(0xFFFFCC00) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp)
                    )

                    if (hasAnyAlarm && formattedDate.isNotBlank()) {
                        // Line 2: Date (e.g. 8/22)
                        Text(
                            text = formattedDate,
                            color = Color(0xFFFFCC00),
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 1.dp)
                        )

                        // Line 3: Time (e.g. 12 PM)
                        Text(
                            text = formattedTime,
                            color = Color(0xFFFFCC00).copy(alpha = 0.9f),
                            fontSize = 9.5.sp,
                            lineHeight = 10.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 0.5.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

@Composable
fun AlterCheckbox(
    checked: Boolean,
    onCheckedChange: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) Color(0xFFFFBD00) else Color.Transparent,
        animationSpec = tween(150),
        label = "checkboxBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color(0xFFFFBD00) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
        animationSpec = tween(150),
        label = "checkboxBorder"
    )

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(
                width = 1.2.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickable { onCheckedChange() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height * 0.45f)
                    lineTo(size.width * 0.35f, size.height * 0.85f)
                    lineTo(size.width, size.height * 0.1f)
                }
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
    }
}

fun Modifier.fadingEdges(topFadeHeight: androidx.compose.ui.unit.Dp = 32.dp, bottomFadeHeight: androidx.compose.ui.unit.Dp = 180.dp): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val topFadePx = topFadeHeight.toPx()
        val bottomFadePx = bottomFadeHeight.toPx()
        val height = size.height
        if (height > 0f) {
            val topStop = (topFadePx / height).coerceIn(0f, 0.5f)
            val bottomStop = ((height - bottomFadePx) / height).coerceIn(0.5f, 1f)
            
            val stops = mutableListOf<Pair<Float, Color>>()
            if (topStop > 0f) {
                stops.add(0f to Color.Transparent)
                stops.add(0.5f * topStop to Color.Black.copy(alpha = 0.5f))
            } else {
                stops.add(0f to Color.Black)
            }
            stops.add(topStop to Color.Black)
            stops.add(bottomStop to Color.Black)
            if (bottomStop < 1f) {
                stops.add(bottomStop + 0.5f * (1f - bottomStop) to Color.Black.copy(alpha = 0.5f))
                stops.add(1f to Color.Transparent)
            } else {
                stops.add(1f to Color.Black)
            }

            drawRect(
                brush = Brush.verticalGradient(*stops.toTypedArray()),
                blendMode = BlendMode.DstIn
            )
        }
    }

@Composable
fun SettingsPage(
    viewModel: com.example.viewmodel.AlterViewModel,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    val prefs = remember { context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE) }
    
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("haptics_enabled", true)) }
    var selectedPattern by remember { mutableStateOf(prefs.getString("vib_pattern_name", "Intense Buzz") ?: "Intense Buzz") }
    var autoCalendarReminders by remember { mutableStateOf(prefs.getBoolean("auto_calendar_reminders", true)) }
    var selectedThemeMode by remember { mutableStateOf(prefs.getString("app_theme_mode", "system") ?: "system") }
    
    var patternListExpanded by remember { mutableStateOf(false) }

    val triggerHaptic = {
        try {
            if (hapticsEnabled) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
        } catch (e: Exception) {}
    }

    val exportBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportAlterBackup(it) }
    }

    val importBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importAlterBackup(it) }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(topFadeHeight = 32.dp, bottomFadeHeight = 32.dp)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Header Bar (Back button only - visual title is in topBar)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    triggerHaptic()
                    onBackToHome()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Lucide.ArrowLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // SECTION 1: APPEARANCE & PERSONALIZATION
        Text(
            text = "APPEARANCE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = getCornerShape(16f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Item 1: Theme Mode Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Sparkles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Visual Accent Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Choose canvas shade details",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                
                // Beautiful Custom Segmented Pills Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .height(38.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val segments = listOf(
                        "system" to "System",
                        "light" to "Light",
                        "dark" to "Pitch Black"
                    )
                    segments.forEach { (modeValue, modeLabel) ->
                        val isSel = selectedThemeMode == modeValue
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.background else Color.Transparent
                                )
                                .border(
                                    width = if (isSel) 0.5.dp else 0.dp,
                                    color = if (isSel) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    triggerHaptic()
                                    selectedThemeMode = modeValue
                                    prefs.edit().putString("app_theme_mode", modeValue).apply()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = modeLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), thickness = 0.5.dp)

                // Item 2: Vibration Rhythm Alert Pattern
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            triggerHaptic()
                            patternListExpanded = !patternListExpanded
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Clock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Rhythm Motor Pattern",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = selectedPattern,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                    Text(
                        text = if (patternListExpanded) "▲" else "▼",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                androidx.compose.animation.AnimatedVisibility(visible = patternListExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f))
                            .padding(bottom = 8.dp)
                    ) {
                        val patterns = listOf(
                            "Steady Default",
                            "Pulsing Glow",
                            "Heartbeat Echo",
                            "Intense Buzz",
                            "Calming Pulse",
                            "Off"
                        )
                        patterns.forEach { pattern ->
                            val isSelected = selectedPattern == pattern
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        triggerHaptic()
                                        selectedPattern = pattern
                                        prefs.edit().putString("vib_pattern_name", pattern).apply()
                                        
                                        // Vibrate sequence test logic
                                        try {
                                            val vibePattern = when (pattern) {
                                                "Steady Default" -> longArrayOf(0, 500, 250, 500)
                                                "Pulsing Glow" -> longArrayOf(0, 300, 200, 300, 200, 300, 500)
                                                "Heartbeat Echo" -> longArrayOf(0, 150, 150, 150, 600, 150, 150)
                                                "Intense Buzz" -> longArrayOf(0, 800, 200, 800, 200, 1200)
                                                "Calming Pulse" -> longArrayOf(0, 100, 400, 100, 400)
                                                "Off" -> longArrayOf(0)
                                                else -> longArrayOf(0, 500)
                                            }
                                            if (pattern != "Off") {
                                                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                                                    vibratorManager?.defaultVibrator
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                                }
                                                if (vibrator != null && vibrator.hasVibrator()) {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                        vibrator.vibrate(android.os.VibrationEffect.createWaveform(vibePattern, -1))
                                                    } else {
                                                        @Suppress("DEPRECATION")
                                                        vibrator.vibrate(vibePattern, -1)
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {}
                                    }
                                    .padding(horizontal = 48.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pattern,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Lucide.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 2: HEURISTICS & PHYSICAL INTERACTIONS
        Text(
            text = "HEURISTICS & PHYSICAL TAPS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Item 1: Tactile Clicks
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            triggerHaptic()
                            hapticsEnabled = !hapticsEnabled
                            prefs.edit().putBoolean("haptics_enabled", hapticsEnabled).apply()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Waves,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Tactile Clicks",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "Gesture & action buzz events",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = {
                            triggerHaptic()
                            hapticsEnabled = it
                            prefs.edit().putBoolean("haptics_enabled", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Gray,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), thickness = 0.5.dp)

                // Item 2: Dynamic Calendar Sync
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            triggerHaptic()
                            autoCalendarReminders = !autoCalendarReminders
                            prefs.edit().putBoolean("auto_calendar_reminders", autoCalendarReminders).apply()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Calendar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Dynamic Calendar Sync",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "Auto-link date reminders with calendar OS",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                    Switch(
                        checked = autoCalendarReminders,
                        onCheckedChange = {
                            triggerHaptic()
                            autoCalendarReminders = it
                            prefs.edit().putBoolean("auto_calendar_reminders", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Gray,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 3: BACKUP & DATA RESTORATION
        Text(
            text = "BACKUP & RESTORATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // Item 1: Export
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Save all notes, audio recordings, text & tasks to local file or share",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        triggerHaptic()
                        viewModel.shareBackupFileToDrive(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Lucide.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Item 2: Import
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Load backup file to restore all notes, audio recordings, text & tasks",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        triggerHaptic()
                        importBackupLauncher.launch(arrayOf("*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Lucide.FileText,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 3: DANGER ZONE
        Text(
            text = "DANGER ZONE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF3B30),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(0.5.dp, Color(0xFFFF3B30).copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFF3B30).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Trash,
                            contentDescription = null,
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Erase All App Slates",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Permanently clear all local slates from SQLite",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                var showConfirmClearAll by remember { mutableStateOf(false) }

                Button(
                    onClick = {
                        triggerHaptic()
                        showConfirmClearAll = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Clear All Local Data",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showConfirmClearAll) {
                    AlertDialog(
                        onDismissRequest = { showConfirmClearAll = false },
                        containerColor = Color(0xFF18181B),
                        title = {
                            Text(
                                "Wipe All Data?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        },
                        text = {
                            Text(
                                "This action is completely irreversible and will permanently delete all your slates, recordings, and tasks.",
                                color = Color(0xFFECECEC),
                                fontFamily = FontFamily.SansSerif
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    triggerHaptic()
                                    viewModel.clearAll()
                                    showConfirmClearAll = false
                                }
                            ) {
                                Text("WIPE EVERYTHING", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmClearAll = false }) {
                                Text("Cancel", color = Color(0xFFECECEC))
                            }
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun FullScreenCardDetail(
    card: com.example.data.AlterCard,
    onBack: () -> Unit,
    viewModel: com.example.viewmodel.AlterViewModel,
    isAudioPlaying: Boolean,
    activePlayingPath: String?,
    onPlayAudio: (String) -> Unit,
    triggerHaptic: () -> Unit
) {
    var isEditing by remember { mutableStateOf(true) }
    
    var editedRawInput by remember(card.id) { mutableStateOf(card.rawInput) }
    var editedProcessedContent by remember(card.id) { mutableStateOf(card.processedContent) }
    var editedPersonalNotes by remember(card.id) { mutableStateOf(card.personalNotes) }

    LaunchedEffect(card.id) {
        editedRawInput = card.rawInput
        editedProcessedContent = card.processedContent
        editedPersonalNotes = card.personalNotes
    }

    val isNoteCard = card.type == "note"
    val hasAttachment = !card.mediaPath.isNullOrEmpty() && (card.type == "audio" || card.type == "image" || card.type == "pdf")

    var showAsBoard by remember { mutableStateOf(false) }

    if (showAsBoard) {
        DottedCanvasBoardScreen(
            card = card,
            onBack = { showAsBoard = false },
            viewModel = viewModel,
            triggerHaptic = { triggerHaptic() }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left group: Back button + Card Category Text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = {
                        triggerHaptic()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    val cardCategoryLabel = when (card.type) {
                        "audio" -> "Audio Info"
                        "image" -> "Image Info"
                        "pdf" -> "Document"
                        "note" -> "Note"
                        else -> "Text"
                    }

                    Text(
                        text = cardCategoryLabel,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Right group: Empty (no pencil icons in edit mode)
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isNoteCard) {
                    // --- NOTE CARD EDITING SKIN ---
                    InteractiveEditorSkin(
                        value = editedRawInput,
                        onValueChange = {
                            editedRawInput = it
                            viewModel.updateCard(card.copy(rawInput = it))
                        },
                        headerTitle = "TITLE",
                        headerIcon = Lucide.Edit,
                        placeholder = "Note Title...",
                        showAddActions = false,
                        onHapticClick = { triggerHaptic() }
                    )

                    InteractiveEditorSkin(
                        value = editedProcessedContent,
                        onValueChange = {
                            editedProcessedContent = it
                            viewModel.updateCard(card.copy(processedContent = it))
                        },
                        headerTitle = "NOTE CONTENT",
                        headerIcon = Lucide.FileText,
                        placeholder = "Tap to write note...",
                        showAddActions = true,
                        onHapticClick = { triggerHaptic() }
                    )
                } else {
                    // --- AUDIO / IMAGE / PDF / TEXT SLATE EDITING SKIN ---
                    InteractiveEditorSkin(
                        value = editedProcessedContent,
                        onValueChange = {
                            editedProcessedContent = it
                            viewModel.updateCard(card.copy(processedContent = it))
                        },
                        headerTitle = "SUMMARY",
                        headerIcon = Lucide.Sparkles,
                        placeholder = "Summary will appear here...",
                        showAddActions = true,
                        onHapticClick = { triggerHaptic() }
                    )

                    InteractiveEditorSkin(
                        value = editedRawInput,
                        onValueChange = {
                            editedRawInput = it
                            viewModel.updateCard(card.copy(rawInput = it))
                        },
                        headerTitle = "RECORDED INPUT",
                        headerIcon = if (card.type == "audio") Lucide.Mic else Lucide.Edit,
                        placeholder = "No recorded input...",
                        showAddActions = false,
                        onHapticClick = { triggerHaptic() }
                    )

                    InteractiveEditorSkin(
                        value = editedPersonalNotes,
                        onValueChange = {
                            editedPersonalNotes = it
                            viewModel.updateCard(card.copy(personalNotes = it))
                        },
                        headerTitle = "PERSONAL NOTES",
                        headerIcon = Lucide.FileText,
                        placeholder = "Tap to spill personal notes here...",
                        showAddActions = true,
                        onHapticClick = { triggerHaptic() }
                    )
                }

                // Spacer at bottom of scrollable column to prevent content overlaps with bottom attachments box
                Spacer(modifier = Modifier.height(if (hasAttachment) 160.dp else 40.dp))
            }
        }

        // --- ATTACHMENTS BOX FIXED AT BOTTOM OF CONTAINER ---
        if (hasAttachment) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ATTACHMENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    
                    val path = card.mediaPath ?: ""
                    if (card.type == "audio") {
                        val isMemoPlaying = isAudioPlaying && activePlayingPath == path
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    triggerHaptic()
                                    onPlayAudio(path)
                                }
                                .background(Color(0xFFCF2A2A).copy(alpha = 0.1f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isMemoPlaying) Lucide.Stop else Lucide.Play,
                                contentDescription = if (isMemoPlaying) "Stop" else "Listen",
                                tint = Color(0xFFCF2A2A),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isMemoPlaying) "Stop playing recording" else "Hear voice audio",
                                color = Color(0xFFCF2A2A),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    } else if (card.type == "image") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = java.io.File(path),
                                    contentDescription = "Attached image thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Attached Image Slate",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = java.io.File(path).name,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else { // pdf or other
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Lucide.Paperclip,
                                contentDescription = "Document Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Attached Document",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = java.io.File(path).name,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NothingDumpedEmptyState() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "empty_state_anim")
    
    // Smooth floating animation
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "floating_orb"
    )
    
    // Rotating shadow / ring animation
    val rotateAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(8000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "glowing_ring"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // High quality drawn glass 3D orb with shadow and glow ring
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    translationY = floatAnim
                },
            contentAlignment = Alignment.Center
        ) {
            // Draw stunning 3D glassmorphic elements using Canvas
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 3.8f
                
                // 1. Draw dynamic background glow
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color(0x33FFBD00), Color.Transparent),
                        center = center,
                        radius = radius * 2.2f
                    )
                )

                // 2. Draw rotating outer orbit/ring with 3D slope perspective
                withTransform({
                    rotate(rotateAnim, center)
                }) {
                    drawOval(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0x88FFBD00), Color(0x11FFFFFF), Color(0x00FFBD00)),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height)
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - radius * 1.8f, center.y - radius * 0.4f),
                        size = androidx.compose.ui.geometry.Size(radius * 3.6f, radius * 0.8f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                }

                // 3. Base sphere shadow
                drawOval(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y + radius * 0.9f),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 0.3f)
                )

                // 4. Draw beautiful glossy glass orb sphere
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            Color(0xDDFFFFFF), 
                            Color(0x99ECECEC), 
                            Color(0x44888888),
                            Color(0xAA111111)
                        ),
                        center = center - androidx.compose.ui.geometry.Offset(radius * 0.3f, radius * 0.3f),
                        radius = radius * 1.5f
                    ),
                    radius = radius,
                    center = center
                )

                // 5. Bright highlight on top-left of sphere for 3D realism
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.0f)),
                        center = center - androidx.compose.ui.geometry.Offset(radius * 0.4f, radius * 0.4f),
                        radius = radius * 0.4f
                    ),
                    radius = radius * 0.35f,
                    center = center - androidx.compose.ui.geometry.Offset(radius * 0.4f, radius * 0.4f)
                )
                
                // 6. Gold inner core overlay
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color(0xEEFFBD00), Color(0x00FFBD00)),
                        center = center,
                        radius = radius * 0.6f
                    ),
                    radius = radius * 0.45f,
                    center = center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Nothing dumped yet",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(letterSpacing = 0.1.sp)
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = "Dumping your thoughts clarifies your mind.",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryModalSheet(
    viewModel: AlterViewModel,
    onDismiss: () -> Unit,
    triggerHaptic: () -> Unit
) {
    val chatSessions by viewModel.chatSessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0xFF3F3F46), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Lucide.Clock,
                        contentDescription = "History",
                        tint = Color(0xFFFFBD00),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chat History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // New Chat Button
                OutlinedButton(
                    onClick = {
                        triggerHaptic()
                        viewModel.startNewChatSession()
                        onDismiss()
                    },
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFBD00)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Plus,
                        contentDescription = "New Chat",
                        tint = Color(0xFFFFBD00),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            if (chatSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No previous chat history",
                        fontSize = 14.sp,
                        color = Color(0xFF71717A)
                    )
                }
            } else {
                Text(
                    text = "Swipe cards left or right to delete session",
                    fontSize = 11.sp,
                    color = Color(0xFF71717A),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(chatSessions, key = { it.id }) { session ->
                        val isSelected = session.id == activeSessionId
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                    triggerHaptic()
                                    viewModel.deleteChatSession(session.id)
                                    true
                                } else false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFEF4444))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Lucide.Trash,
                                        contentDescription = "Delete",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) Color(0xFF27272A) else Color(0xFF18181B))
                                    .border(
                                        width = 0.5.dp,
                                        color = if (isSelected) Color(0xFFFFBD00) else Color(0xFF27272A),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        triggerHaptic()
                                        viewModel.loadChatSession(session.id)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title.ifBlank { "Chat Session" },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val dateStr = remember(session.timestamp) {
                                            java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.timestamp))
                                        }
                                        Text(
                                            text = "${session.messages.size} messages • $dateStr",
                                            fontSize = 11.sp,
                                            color = Color(0xFFA1A1AA)
                                        )
                                    }
                                    if (isSelected) {
                                        Text(
                                            text = "Active",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFBD00)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

