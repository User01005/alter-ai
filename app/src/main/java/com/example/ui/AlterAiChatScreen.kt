package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.api.ChatMessage
import com.example.api.ChatSender
import com.example.data.AlterCard
import com.example.viewmodel.AlterViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlterAiChatScreen(
    viewModel: AlterViewModel,
    onCardClick: (Long) -> Unit,
    triggerHaptic: () -> Unit
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isGenerating by viewModel.isAiChatGenerating.collectAsState()
    val cards by viewModel.cards.collectAsState()

    var inputMessage by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Session Greeting
    val sessionGreeting = "How can I help you synthesize memories today?"

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(chatMessages.size, isGenerating) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val quickPrompts = remember(cards.size) {
        if (cards.isNotEmpty()) {
            listOf(
                "Summarize my thoughts & notes",
                "What tasks or to-dos do I have?",
                "Help me organize my active notes",
                "Brainstorm next steps for my ideas"
            )
        } else {
            listOf(
                "How can you help me organize my thoughts?",
                "What kind of things can I ask you?",
                "Help me brainstorm creative project ideas"
            )
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    val inputBottomPadding = if (isImeVisible) 12.dp else 116.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Pitch Black
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- MESSAGES / CENTER HEADING LIST ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // If no messages exist yet in this chat session, show clean centered doodle welcome
                if (chatMessages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp, bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = sessionGreeting,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(chatMessages, key = { it.id }) { message ->
                        ChatMessageBubble(
                            message = message,
                            cards = cards,
                            onCardClick = onCardClick,
                            triggerHaptic = triggerHaptic,
                            onReplyToMessage = { replyingMsg ->
                                replyingToMessage = replyingMsg
                            }
                        )
                    }
                }

                if (isGenerating) {
                    item {
                        AiGeneratingBubble()
                    }
                }
            }

            // --- QUICK SUGGESTION CHIPS ---
            if (!isGenerating && chatMessages.isEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickPrompts) { prompt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF18181B))
                                .border(0.5.dp, Color(0xFF27272A), RoundedCornerShape(16.dp))
                                .clickable {
                                    triggerHaptic()
                                    viewModel.sendChatMessage(prompt)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = prompt,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFFE4E4E7)
                            )
                        }
                    }
                }
            }

            // --- REPLY PREVIEW BAR ---
            if (replyingToMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF18181B))
                        .border(0.5.dp, Color(0xFF27272A), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${if (replyingToMessage?.sender == ChatSender.USER) "yourself" else "Alter AI"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFBD00)
                        )
                        Text(
                            text = replyingToMessage?.text?.replace(Regex("\\*+|_+|#+"), "")?.take(60) ?: "",
                            fontSize = 12.sp,
                            color = Color(0xFFA1A1AA),
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = { replyingToMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = "Cancel reply",
                            tint = Color(0xFF71717A),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // --- INPUT BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = inputBottomPadding, top = 4.dp)
                    .background(Color(0xFF18181B), shape = RoundedCornerShape(24.dp))
                    .border(0.5.dp, Color(0xFF27272A), shape = RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = {
                        Text(
                            text = "Ask Alter AI...",
                            fontSize = 14.sp,
                            color = Color(0xFF71717A)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputMessage.isNotBlank() && !isGenerating) {
                                triggerHaptic()
                                val fullText = if (replyingToMessage != null) {
                                    "Replying to: \"${replyingToMessage?.text?.take(40)}\"\n$inputMessage"
                                } else {
                                    inputMessage
                                }
                                viewModel.sendChatMessage(fullText)
                                inputMessage = ""
                                replyingToMessage = null
                                keyboardController?.hide()
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Pitch Black Arrow Send Button Frame
                IconButton(
                    onClick = {
                        if (inputMessage.isNotBlank() && !isGenerating) {
                            triggerHaptic()
                            val fullText = if (replyingToMessage != null) {
                                "Replying to: \"${replyingToMessage?.text?.take(40)}\"\n$inputMessage"
                            } else {
                                inputMessage
                            }
                            viewModel.sendChatMessage(fullText)
                            inputMessage = ""
                            replyingToMessage = null
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black, CircleShape) // Pitch black matching app consistency
                        .border(0.5.dp, Color(0xFF27272A), CircleShape)
                ) {
                    Icon(
                        imageVector = Lucide.ArrowRight,
                        contentDescription = "Send Message",
                        tint = if (inputMessage.isNotBlank() && !isGenerating) Color.White else Color(0xFF52525B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    cards: List<AlterCard>,
    onCardClick: (Long) -> Unit,
    triggerHaptic: () -> Unit,
    onReplyToMessage: (ChatMessage) -> Unit
) {
    val isUser = message.sender == ChatSender.USER
    var showTimestamp by remember { mutableStateOf(false) }

    // Swipe gesture offset for reply
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateIntAsState(
        targetValue = dragOffsetX.roundToInt(),
        animationSpec = tween(150),
        label = "SwipeOffset"
    )

    // Clean text without asterisks or markdown symbols while preserving bullet pointers
    val cleanText = remember(message.text) {
        message.text
            .replace(Regex("(^|\\n)\\*\\s+"), "$1• ")
            .replace(Regex("\\*\\*|\\*|_|#+"), "")
            .trim()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(animatedOffsetX, 0) }
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffsetX > 80f) {
                            triggerHaptic()
                            onReplyToMessage(message)
                        }
                        dragOffsetX = 0f
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount > 0 || dragOffsetX > 0) {
                            dragOffsetX = (dragOffsetX + dragAmount).coerceIn(0f, 150f)
                        }
                    }
                )
            },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // NO LEFT AVATAR ICON - Clean alignment on left and right!

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        )
                    )
                    .background(
                        if (isUser) Color(0xFF27272A) // Deep Grey for User
                        else Color(0xFF18181B) // Cool Zinc for AI
                    )
                    .border(
                        width = 0.5.dp,
                        color = if (isUser) Color(0xFF3F3F46) else Color(0xFF27272A),
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        )
                    )
                    .clickable {
                        showTimestamp = !showTimestamp
                    }
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = cleanText,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color.White
                    )

                    // Render Card Citations if present
                    val extractedCardIds = remember(message.text) {
                        extractCardCitations(message.text)
                    }

                    if (extractedCardIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Referenced Notes:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFBD00)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        extractedCardIds.forEach { cardId ->
                            val card = cards.find { it.id == cardId }
                            val title = card?.let { c ->
                                if (c.processedContent.isNotBlank()) c.processedContent.lines().firstOrNull()?.take(25) ?: "Card #$cardId"
                                else c.rawInput.take(25)
                            } ?: "Card #$cardId"

                            Box(
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF27272A))
                                    .border(0.5.dp, Color(0xFFFFBD00).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        triggerHaptic()
                                        onCardClick(cardId)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Lucide.FileText,
                                        contentDescription = "Card Citation",
                                        tint = Color(0xFFFFBD00),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$title ↗",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Timestamp visible ONLY when message is clicked!
            if (showTimestamp) {
                Spacer(modifier = Modifier.height(4.dp))
                val timeStr = remember(message.timestamp) {
                    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(message.timestamp))
                }
                Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    color = Color(0xFF71717A)
                )
            }
        }
    }
}

@Composable
fun AiGeneratingBubble() {
    val transition = rememberInfiniteTransition(label = "DotsPulse")
    val alpha1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "A1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse), label = "A2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse), label = "A3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF18181B))
                .border(0.5.dp, Color(0xFF27272A), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Thinking",
                    fontSize = 12.sp,
                    color = Color(0xFFA1A1AA)
                )
                Box(modifier = Modifier.size(5.dp).background(Color(0xFFFFBD00).copy(alpha = alpha1), CircleShape))
                Box(modifier = Modifier.size(5.dp).background(Color(0xFFFFBD00).copy(alpha = alpha2), CircleShape))
                Box(modifier = Modifier.size(5.dp).background(Color(0xFFFFBD00).copy(alpha = alpha3), CircleShape))
            }
        }
    }
}

/**
 * Helper to extract card IDs from citations like [Card #12: Laundry App] or [Card #12]
 */
fun extractCardCitations(text: String): List<Long> {
    val regex = Regex("\\[Card #(\\d+)")
    val matches = regex.findAll(text)
    return matches.mapNotNull { it.groupValues[1].toLongOrNull() }.distinct().toList()
}
