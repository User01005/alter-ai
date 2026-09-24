package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import com.example.data.*
import com.example.viewmodel.AlterViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class CanvasTool {
    SELECT,
    BRUSH,
    TEXT,
    IMAGE,
    ERASER
}

private val PALETTE_COLORS = listOf(
    "#FFFFFF", // Pure White (Default)
    "#FFCC00", // Signature Gold
    "#FF453A", // Crimson Red
    "#0A84FF", // Electric Blue
    "#30D158", // Emerald Green
    "#BF5AF2", // Purple
    "#8E8E93"  // Slate
)

/**
 * Calculates Euclidean distance from point (px, py) to line segment (x1, y1)-(x2, y2).
 */
private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    if (dx == 0f && dy == 0f) {
        val ddx = px - x1
        val ddy = py - y1
        return sqrt(ddx * ddx + ddy * ddy)
    }
    val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
    val clampedT = t.coerceIn(0f, 1f)
    val projX = x1 + clampedT * dx
    val projY = y1 + clampedT * dy
    val ddx = px - projX
    val ddy = py - projY
    return sqrt(ddx * ddx + ddy * ddy)
}

/**
 * Finds if a tap at (canvasX, canvasY) intersects any stroke path.
 */
private fun findHitStroke(canvasX: Float, canvasY: Float, strokeList: List<CanvasStroke>): CanvasStroke? {
    for (stroke in strokeList.reversed()) {
        if (stroke.points.size < 2) continue
        for (i in 0 until stroke.points.size - 1) {
            val p1 = stroke.points[i]
            val p2 = stroke.points[i + 1]
            val dist = distanceToSegment(canvasX, canvasY, p1.x, p1.y, p2.x, p2.y)
            val hitThreshold = (stroke.strokeWidth * 2.5f).coerceAtLeast(24f)
            if (dist <= hitThreshold) {
                return stroke
            }
        }
    }
    return null
}

@Composable
fun DottedCanvasBoardScreen(
    card: AlterCard,
    onBack: () -> Unit,
    viewModel: AlterViewModel,
    triggerHaptic: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    // High-precision Spring specification for fluid, natural physics
    val springSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    // Initialize board state from card
    var boardTitle by remember(card.id) { 
        mutableStateOf(card.rawInput.ifBlank { "Untitled Canvas" }) 
    }
    
    val initialBoard = remember(card.id) {
        val seedContent = card.processedContent.ifBlank { "" }
        val sanitizedSeed = if (seedContent.isNotBlank() && seedContent != "Untitled Canvas" && seedContent != "Untitled Board" && seedContent != "Canvas Board") {
            seedContent
        } else null
        CanvasBoardState.fromJson(card.canvasData, sanitizedSeed)
    }

    var strokes by remember(card.id) { mutableStateOf(initialBoard.strokes) }
    var textBlocks by remember(card.id) { mutableStateOf(initialBoard.textBlocks) }
    var images by remember(card.id) { mutableStateOf(initialBoard.images) }

    // High-precision viewport state for subpixel-smooth 120fps pan & zoom
    var zoomScale by remember { mutableFloatStateOf(initialBoard.zoomScale.coerceIn(0.15f, 6.0f)) }
    var panOffset by remember { mutableStateOf(Offset(initialBoard.panX, initialBoard.panY)) }

    // Subtle tactile fade transition for undo/redo actions
    val canvasAlphaAnimatable = remember { Animatable(1f) }

    var selectedTool by remember { mutableStateOf(CanvasTool.SELECT) }
    var selectedColorHex by remember { mutableStateOf("#FFFFFF") }
    var brushWidth by remember { mutableFloatStateOf(5f) }
    var showColorPalette by remember { mutableStateOf(false) }

    // Live active stroke while drawing
    var currentLiveStrokePoints by remember { mutableStateOf<List<CanvasPoint>>(emptyList()) }

    // Undo/Redo history
    val undoStack = remember { mutableStateListOf<CanvasBoardState>() }
    val redoStack = remember { mutableStateListOf<CanvasBoardState>() }

    // Active selection state for interactive elements
    var selectedStrokeId by remember { mutableStateOf<String?>(null) }
    var selectedImageId by remember { mutableStateOf<String?>(null) }
    var selectedTextBlockId by remember { mutableStateOf<String?>(null) }
    var editingTextBlockId by remember { mutableStateOf<String?>(null) }
    var longPressedItemId by remember { mutableStateOf<String?>(null) }

    // Focus requester for active text field
    val textFocusRequester = remember { FocusRequester() }

    val currentSelectedColorHex by rememberUpdatedState(selectedColorHex)
    val currentBrushWidth by rememberUpdatedState(brushWidth)

    fun animateViewport(targetZoom: Float, targetPan: Offset) {
        coroutineScope.launch {
            val startZoom = zoomScale
            val startPan = panOffset
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { progress, _ ->
                zoomScale = (startZoom + (targetZoom - startZoom) * progress).coerceIn(0.15f, 6.0f)
                panOffset = Offset(
                    startPan.x + (targetPan.x - startPan.x) * progress,
                    startPan.y + (targetPan.y - startPan.y) * progress
                )
            }
        }
    }

    LaunchedEffect(editingTextBlockId) {
        if (editingTextBlockId != null) {
            try {
                textFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    fun captureHistory() {
        undoStack.add(
            CanvasBoardState(
                strokes = strokes,
                textBlocks = textBlocks,
                images = images,
                panX = panOffset.x,
                panY = panOffset.y,
                zoomScale = zoomScale
            )
        )
        if (undoStack.size > 25) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun saveCurrentBoard() {
        val currentState = CanvasBoardState(
            strokes = strokes,
            textBlocks = textBlocks,
            images = images,
            panX = panOffset.x,
            panY = panOffset.y,
            zoomScale = zoomScale
        )
        viewModel.saveCanvasBoard(card.id, currentState, card)
        if (boardTitle != card.rawInput) {
            viewModel.updateCard(card.copy(rawInput = boardTitle, canvasData = currentState.toJson()))
        }
    }

    // Auto-save on unmount / change
    DisposableEffect(card.id) {
        onDispose {
            saveCurrentBoard()
        }
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val localFile = File(context.filesDir, "board_img_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }
                // Inspect natural image dimensions to preserve true aspect ratio and crisp fidelity
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(localFile.absolutePath, boundsOptions)
                val origW = if (boundsOptions.outWidth > 0) boundsOptions.outWidth.toFloat() else 400f
                val origH = if (boundsOptions.outHeight > 0) boundsOptions.outHeight.toFloat() else 400f
                val baseWidth = 300f
                val calculatedHeight = (baseWidth * (origH / origW)).coerceIn(80f, 800f)

                captureHistory()
                // Place near center of current viewport
                val canvasCenterX = (-panOffset.x + 300f) / zoomScale
                val canvasCenterY = (-panOffset.y + 400f) / zoomScale
                val newImage = CanvasImageBlock(
                    x = canvasCenterX - (baseWidth / 2f),
                    y = canvasCenterY - (calculatedHeight / 2f),
                    width = baseWidth,
                    height = calculatedHeight,
                    imagePathOrUri = localFile.absolutePath
                )
                images = images + newImage
                selectedImageId = newImage.id
                selectedTool = CanvasTool.SELECT
                selectedStrokeId = null
                selectedTextBlockId = null
                editingTextBlockId = null
                triggerHaptic()
                saveCurrentBoard()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ======================= INFINITE DOTTED CANVAS =======================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    alpha = canvasAlphaAnimatable.value
                }
                // 1. Gesture detector based on active tool (continuous, never interrupted by pan/zoom/stroke changes)
                .pointerInput(selectedTool) {
                    when (selectedTool) {
                        CanvasTool.BRUSH -> {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    captureHistory()
                                    selectedStrokeId = null
                                    selectedImageId = null
                                    selectedTextBlockId = null
                                    editingTextBlockId = null
                                    longPressedItemId = null
                                    val canvasX = (startOffset.x - panOffset.x) / zoomScale
                                    val canvasY = (startOffset.y - panOffset.y) / zoomScale
                                    currentLiveStrokePoints = listOf(CanvasPoint(canvasX, canvasY))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val canvasX = (change.position.x - panOffset.x) / zoomScale
                                    val canvasY = (change.position.y - panOffset.y) / zoomScale
                                    currentLiveStrokePoints = currentLiveStrokePoints + CanvasPoint(canvasX, canvasY)
                                },
                                onDragEnd = {
                                    if (currentLiveStrokePoints.size > 1) {
                                        val newStroke = CanvasStroke(
                                            id = UUID.randomUUID().toString(),
                                            points = currentLiveStrokePoints,
                                            colorHex = currentSelectedColorHex,
                                            strokeWidth = currentBrushWidth,
                                            isEraser = false
                                        )
                                        strokes = strokes + newStroke
                                        saveCurrentBoard()
                                    }
                                    currentLiveStrokePoints = emptyList()
                                },
                                onDragCancel = {
                                    currentLiveStrokePoints = emptyList()
                                }
                            )
                        }
                        CanvasTool.ERASER -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    captureHistory()
                                    val canvasX = (offset.x - panOffset.x) / zoomScale
                                    val canvasY = (offset.y - panOffset.y) / zoomScale
                                    strokes = strokes.filterNot { stroke ->
                                        stroke.points.any { pt ->
                                            val dx = pt.x - canvasX
                                            val dy = pt.y - canvasY
                                            (dx * dx + dy * dy) < (36f * 36f)
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val canvasX = (change.position.x - panOffset.x) / zoomScale
                                    val canvasY = (change.position.y - panOffset.y) / zoomScale
                                    strokes = strokes.filterNot { stroke ->
                                        stroke.points.any { pt ->
                                            val dx = pt.x - canvasX
                                            val dy = pt.y - canvasY
                                            (dx * dx + dy * dy) < (36f * 36f)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    saveCurrentBoard()
                                }
                            )
                        }
                        CanvasTool.TEXT -> {
                            detectTapGestures { tapOffset ->
                                if (editingTextBlockId != null) {
                                    // If already editing, tapping canvas finishes text and switches to Move mode
                                    editingTextBlockId = null
                                    selectedTextBlockId = null
                                    selectedTool = CanvasTool.SELECT
                                    triggerHaptic()
                                } else {
                                    val canvasX = (tapOffset.x - panOffset.x) / zoomScale
                                    val canvasY = (tapOffset.y - panOffset.y) / zoomScale
                                    captureHistory()
                                    val newBlock = CanvasTextBlock(
                                        id = UUID.randomUUID().toString(),
                                        x = canvasX,
                                        y = canvasY,
                                        text = "",
                                        colorHex = currentSelectedColorHex,
                                        fontSize = 16f,
                                        width = 480f,
                                        isPrimaryNote = false
                                    )
                                    textBlocks = textBlocks + newBlock
                                    editingTextBlockId = newBlock.id
                                    selectedTextBlockId = newBlock.id
                                    selectedStrokeId = null
                                    selectedImageId = null
                                    longPressedItemId = null
                                    triggerHaptic()
                                    saveCurrentBoard()
                                }
                            }
                        }
                        CanvasTool.SELECT, CanvasTool.IMAGE -> {
                            detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                                val oldZoom = zoomScale
                                val newZoom = (oldZoom * zoom).coerceIn(0.15f, 6.0f)
                                val zoomFactor = newZoom / oldZoom
                                val newPanX = centroid.x - (centroid.x - panOffset.x) * zoomFactor + pan.x
                                val newPanY = centroid.y - (centroid.y - panOffset.y) * zoomFactor + pan.y
                                zoomScale = newZoom
                                panOffset = Offset(newPanX, newPanY)
                            }
                        }
                    }
                }
                // 2. Tap & Long-Press gesture on canvas background to select / delete doodle
                .pointerInput(selectedTool) {
                    if (selectedTool == CanvasTool.SELECT) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                val canvasX = (tapOffset.x - panOffset.x) / zoomScale
                                val canvasY = (tapOffset.y - panOffset.y) / zoomScale
                                val hit = findHitStroke(canvasX, canvasY, strokes)
                                if (hit != null) {
                                    selectedStrokeId = hit.id
                                    selectedImageId = null
                                    selectedTextBlockId = null
                                    editingTextBlockId = null
                                    longPressedItemId = null
                                    triggerHaptic()
                                } else {
                                    selectedStrokeId = null
                                    selectedImageId = null
                                    selectedTextBlockId = null
                                    editingTextBlockId = null
                                    longPressedItemId = null
                                }
                            },
                            onLongPress = { tapOffset ->
                                val canvasX = (tapOffset.x - panOffset.x) / zoomScale
                                val canvasY = (tapOffset.y - panOffset.y) / zoomScale
                                val hit = findHitStroke(canvasX, canvasY, strokes)
                                if (hit != null) {
                                    selectedStrokeId = hit.id
                                    selectedImageId = null
                                    editingTextBlockId = null
                                    longPressedItemId = hit.id
                                    triggerHaptic()
                                }
                            }
                        )
                    }
                }
        ) {
            // Draw Canvas: Dotted Grid + Vector Strokes
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // 1. Draw Sleek Dotted Grid Matrix with high-precision subpixel spacing
                val baseStep = 28f
                var step = baseStep * zoomScale
                if (step < 12f) {
                    step *= 2f
                    if (step < 12f) step *= 2f
                }

                val dotRadius = (1.2f * zoomScale.coerceIn(0.6f, 1.2f)).coerceIn(1.0f, 1.8f)
                // Opacity is reduced when zoomed in so content is prominent, and stays subtle
                val dotAlpha = (0.22f / zoomScale.coerceIn(0.4f, 4.0f)).coerceIn(0.06f, 0.16f)
                val dotColor = Color(0xFF8E8E93).copy(alpha = dotAlpha)

                val startX = (panOffset.x % step) - step
                val startY = (panOffset.y % step) - step

                var x = startX
                while (x < canvasWidth + step) {
                    var y = startY
                    while (y < canvasHeight + step) {
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(x, y)
                        )
                        y += step
                    }
                    x += step
                }

                // 2. Apply Viewport Transform & Render Strokes
                translate(left = panOffset.x, top = panOffset.y) {
                    scale(scale = zoomScale, pivot = Offset.Zero) {
                        // Render all saved strokes
                        for (stroke in strokes) {
                            if (stroke.points.size > 1) {
                                val strokePath = Path().apply {
                                    moveTo(stroke.points[0].x, stroke.points[0].y)
                                    for (i in 1 until stroke.points.size) {
                                        val p = stroke.points[i]
                                        lineTo(p.x, p.y)
                                    }
                                }
                                val color = try {
                                    Color(android.graphics.Color.parseColor(stroke.colorHex))
                                } catch (e: Exception) {
                                    Color.White
                                }
                                drawPath(
                                    path = strokePath,
                                    color = color,
                                    style = Stroke(
                                        width = stroke.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // Render live in-progress stroke
                        if (currentLiveStrokePoints.size > 1) {
                            val livePath = Path().apply {
                                moveTo(currentLiveStrokePoints[0].x, currentLiveStrokePoints[0].y)
                                for (i in 1 until currentLiveStrokePoints.size) {
                                    val p = currentLiveStrokePoints[i]
                                    lineTo(p.x, p.y)
                                }
                            }
                            val liveColor = try {
                                Color(android.graphics.Color.parseColor(selectedColorHex))
                            } catch (e: Exception) {
                                Color.White
                            }
                            drawPath(
                                path = livePath,
                                color = liveColor,
                                style = Stroke(
                                    width = brushWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }

            // 3. Selected Stroke / Doodle Bounding Box & Delete Button
            val currentSelectedStroke = strokes.find { it.id == selectedStrokeId }
            if (currentSelectedStroke != null && currentSelectedStroke.points.isNotEmpty()) {
                val minX = currentSelectedStroke.points.minOf { it.x }
                val minY = currentSelectedStroke.points.minOf { it.y }
                val maxX = currentSelectedStroke.points.maxOf { it.x }
                val maxY = currentSelectedStroke.points.maxOf { it.y }

                val pad = 16f
                val screenX = panOffset.x + ((minX - pad) * zoomScale)
                val screenY = panOffset.y + ((minY - pad) * zoomScale)
                val boxWidthPx = (((maxX - minX) + (pad * 2)) * zoomScale).coerceAtLeast(48f)
                val boxHeightPx = (((maxY - minY) + (pad * 2)) * zoomScale).coerceAtLeast(48f)

                val boxWidthDp = with(density) { boxWidthPx.toDp() }
                val boxHeightDp = with(density) { boxHeightPx.toDp() }
                val isDeleteVisible = (longPressedItemId == selectedStrokeId) || selectedTool == CanvasTool.ERASER

                Box(
                    modifier = Modifier
                        .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                        .size(boxWidthDp, boxHeightDp)
                        .border(1.dp, Color(0xFF666666), RoundedCornerShape(8.dp))
                        .then(
                            if (selectedTool == CanvasTool.SELECT) {
                                Modifier.pointerInput(currentSelectedStroke.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        strokes = strokes.map { s ->
                                            if (s.id == currentSelectedStroke.id) {
                                                s.copy(
                                                    points = s.points.map { pt ->
                                                        CanvasPoint(
                                                            pt.x + (dragAmount.x / zoomScale),
                                                            pt.y + (dragAmount.y / zoomScale)
                                                        )
                                                    }
                                                )
                                            } else s
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // Delete Button inside top-right corner of stroke bounding box (fully visible inside frame)
                    if (isDeleteVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(Color(0xFF141414), CircleShape)
                                .border(1.dp, Color(0xFF333333), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    captureHistory()
                                    strokes = strokes.filterNot { it.id == selectedStrokeId }
                                    selectedStrokeId = null
                                    longPressedItemId = null
                                    triggerHaptic()
                                    saveCurrentBoard()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Trash2,
                                contentDescription = "Delete drawing",
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 4. Render Images with Extend / Stretch Frame Handles & Press-and-Hold Delete
            images.forEach { imgBlock ->
                val screenX = panOffset.x + (imgBlock.x * zoomScale)
                val screenY = panOffset.y + (imgBlock.y * zoomScale)
                val scaledWidthDp = with(density) { (imgBlock.width * zoomScale).coerceAtLeast(40f).toDp() }
                val scaledHeightDp = with(density) { (imgBlock.height * zoomScale).coerceAtLeast(40f).toDp() }
                val isImageSelected = (selectedImageId == imgBlock.id)
                val isDeleteVisible = (longPressedItemId == imgBlock.id) || selectedTool == CanvasTool.ERASER

                Box(
                    modifier = Modifier
                        .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                        .size(scaledWidthDp, scaledHeightDp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(
                            width = if (isImageSelected) 1.dp else 0.5.dp,
                            color = if (isImageSelected) Color(0xFF555555) else Color(0xFF2E2E2E),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    selectedTool = CanvasTool.SELECT
                                    selectedImageId = imgBlock.id
                                    selectedStrokeId = null
                                    selectedTextBlockId = null
                                    editingTextBlockId = null
                                    longPressedItemId = null
                                    triggerHaptic()
                                },
                                onLongPress = {
                                    selectedTool = CanvasTool.SELECT
                                    selectedImageId = imgBlock.id
                                    selectedStrokeId = null
                                    selectedTextBlockId = null
                                    editingTextBlockId = null
                                    longPressedItemId = imgBlock.id
                                    triggerHaptic()
                                }
                            )
                        }
                        .then(
                            if (selectedTool == CanvasTool.SELECT && isImageSelected) {
                                Modifier.pointerInput(imgBlock.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        images = images.map { current ->
                                            if (current.id == imgBlock.id) {
                                                current.copy(
                                                    x = current.x + (dragAmount.x / zoomScale),
                                                    y = current.y + (dragAmount.y / zoomScale)
                                                )
                                            } else current
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(imgBlock.imagePathOrUri))
                            .crossfade(true)
                            .size(coil.size.Size.ORIGINAL)
                            .build(),
                        contentDescription = "Board Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Stretch / Resize controls for selected image frame (corner handle only)
                    if (isImageSelected) {
                        // Bottom-Right Corner Extend / Resize Handle (inside frame)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .background(Color(0xFF141414), CircleShape)
                                .border(1.dp, Color(0xFF555555), CircleShape)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        images = images.map { current ->
                                            if (current.id == imgBlock.id) {
                                                current.copy(
                                                    width = (current.width + (dragAmount.x / zoomScale)).coerceAtLeast(60f),
                                                    height = (current.height + (dragAmount.y / zoomScale)).coerceAtLeast(60f)
                                                )
                                            } else current
                                        }
                                        saveCurrentBoard()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Maximize2,
                                contentDescription = "Resize Image Frame",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Delete Button in a black circle with red bin, fully inside frame
                    if (isDeleteVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .background(Color(0xFF141414), CircleShape)
                                .border(1.dp, Color(0xFF333333), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    captureHistory()
                                    images = images.filterNot { it.id == imgBlock.id }
                                    if (selectedImageId == imgBlock.id) selectedImageId = null
                                    longPressedItemId = null
                                    triggerHaptic()
                                    saveCurrentBoard()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Trash2,
                                contentDescription = "Delete Image",
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 5. Render Text Blocks: Horizontal Frame Width Extend & Press-and-Hold Delete
            textBlocks.forEach { textBlock ->
                val screenX = panOffset.x + (textBlock.x * zoomScale)
                val screenY = panOffset.y + (textBlock.y * zoomScale)
                val scaledWidthDp = with(density) { (textBlock.width * zoomScale).coerceAtLeast(80f).toDp() }
                val isEditing = (editingTextBlockId == textBlock.id)
                val isSelected = (selectedTextBlockId == textBlock.id || isEditing)
                val isLongPressed = (longPressedItemId == textBlock.id)
                val isDeleteVisible = (isLongPressed && !isEditing) || selectedTool == CanvasTool.ERASER

                Box(
                    modifier = Modifier
                        .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                        .width(scaledWidthDp)
                        .then(
                            if (isSelected || isLongPressed) {
                                Modifier
                                    .border(1.dp, Color(0xFF383838), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            } else {
                                Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            }
                        )
                        .pointerInput(selectedTool) {
                            if (selectedTool == CanvasTool.SELECT) {
                                detectTapGestures(
                                    onTap = {
                                        if (selectedTextBlockId == textBlock.id && editingTextBlockId != textBlock.id) {
                                            // Second tap: enter text edit mode
                                            editingTextBlockId = textBlock.id
                                        } else {
                                            // First tap: select text block for move/resize without entering text edit mode
                                            selectedTextBlockId = textBlock.id
                                            editingTextBlockId = null
                                        }
                                        selectedStrokeId = null
                                        selectedImageId = null
                                        longPressedItemId = null
                                        triggerHaptic()
                                    },
                                    onLongPress = {
                                        longPressedItemId = textBlock.id
                                        selectedTextBlockId = textBlock.id
                                        editingTextBlockId = null
                                        selectedStrokeId = null
                                        selectedImageId = null
                                        triggerHaptic()
                                    }
                                )
                            }
                        }
                        .then(
                            if (selectedTool == CanvasTool.SELECT && (isSelected || isLongPressed)) {
                                Modifier.pointerInput(textBlock.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        textBlocks = textBlocks.map { current ->
                                            if (current.id == textBlock.id) {
                                                current.copy(
                                                    x = current.x + (dragAmount.x / zoomScale),
                                                    y = current.y + (dragAmount.y / zoomScale)
                                                )
                                            } else current
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val textColor = try {
                        Color(AndroidColor.parseColor(textBlock.colorHex))
                    } catch (e: Exception) {
                        Color.White
                    }
                    val effectiveFontSize = (textBlock.fontSize * zoomScale.coerceIn(0.4f, 4.0f)).sp
                    val effectiveLineHeight = ((textBlock.fontSize + 4) * zoomScale.coerceIn(0.4f, 4.0f)).sp

                    if (isEditing) {
                        BasicTextField(
                            value = textBlock.text,
                            onValueChange = { updated ->
                                textBlocks = textBlocks.map { current ->
                                    if (current.id == textBlock.id) current.copy(text = updated) else current
                                }
                                saveCurrentBoard()
                            },
                            textStyle = TextStyle(
                                color = textColor,
                                fontSize = effectiveFontSize,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Normal,
                                lineHeight = effectiveLineHeight
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp)
                                .focusRequester(textFocusRequester),
                            decorationBox = { innerTextField ->
                                if (textBlock.text.isEmpty()) {
                                    Text(
                                        text = "Type text here...",
                                        color = Color(0xFF777777),
                                        fontSize = effectiveFontSize,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                                innerTextField()
                            }
                        )
                    } else {
                        Text(
                            text = textBlock.text.ifBlank { "Text" },
                            color = textColor,
                            fontSize = effectiveFontSize,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            lineHeight = effectiveLineHeight,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Vertical Line Handle only on Right Edge for Width resizing (dark subtle line)
                    if (isSelected || isLongPressed) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 2.dp)
                                .width(4.dp)
                                .height(22.dp)
                                .background(Color(0xFF444444), RoundedCornerShape(2.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        textBlocks = textBlocks.map { current ->
                                            if (current.id == textBlock.id) {
                                                current.copy(
                                                    width = (current.width + (dragAmount.x / zoomScale)).coerceIn(60f, 2000f)
                                                )
                                            } else current
                                        }
                                        saveCurrentBoard()
                                    }
                                }
                        )
                    }

                    // Delete button in a black circle with red bin, fully inside frame
                    if (isDeleteVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(26.dp)
                                .background(Color(0xFF141414), CircleShape)
                                .border(1.dp, Color(0xFF333333), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    captureHistory()
                                    textBlocks = textBlocks.filterNot { it.id == textBlock.id }
                                    if (editingTextBlockId == textBlock.id) editingTextBlockId = null
                                    longPressedItemId = null
                                    triggerHaptic()
                                    saveCurrentBoard()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Trash2,
                                contentDescription = "Delete text",
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // ======================= TOP BAR =======================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Back button (increased size) + Board Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = {
                        triggerHaptic()
                        saveCurrentBoard()
                        onBack()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Lucide.ArrowLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                BasicTextField(
                    value = boardTitle,
                    onValueChange = { boardTitle = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(horizontal = 4.dp),
                    decorationBox = { innerTextField ->
                        if (boardTitle.isEmpty()) {
                            Text(
                                text = "Untitled Canvas",
                                color = Color(0xFF777777),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        innerTextField()
                    }
                )
            }

            // Right: Undo, Redo, Zoom Controls (+ / - / Reset) - aligned height, spaced, minimal styling
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Undo button
                IconButton(
                    onClick = {
                        if (undoStack.isNotEmpty()) {
                            val lastState = undoStack.removeAt(undoStack.lastIndex)
                            redoStack.add(
                                CanvasBoardState(
                                    strokes = strokes,
                                    textBlocks = textBlocks,
                                    images = images,
                                    panX = panOffset.x,
                                    panY = panOffset.y,
                                    zoomScale = zoomScale
                                )
                            )
                            strokes = lastState.strokes
                            textBlocks = lastState.textBlocks
                            images = lastState.images
                            selectedStrokeId = null
                            selectedImageId = null
                            editingTextBlockId = null
                            longPressedItemId = null

                            coroutineScope.launch {
                                canvasAlphaAnimatable.snapTo(0.65f)
                                canvasAlphaAnimatable.animateTo(1f, springSpec)
                            }
                            animateViewport(lastState.zoomScale, Offset(lastState.panX, lastState.panY))
                            triggerHaptic()
                            saveCurrentBoard()
                        }
                    },
                    enabled = undoStack.isNotEmpty(),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Undo,
                        contentDescription = "Undo",
                        tint = if (undoStack.isNotEmpty()) Color.White else Color(0xFF555555),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Redo button
                IconButton(
                    onClick = {
                        if (redoStack.isNotEmpty()) {
                            val nextState = redoStack.removeAt(redoStack.lastIndex)
                            undoStack.add(
                                CanvasBoardState(
                                    strokes = strokes,
                                    textBlocks = textBlocks,
                                    images = images,
                                    panX = panOffset.x,
                                    panY = panOffset.y,
                                    zoomScale = zoomScale
                                )
                            )
                            strokes = nextState.strokes
                            textBlocks = nextState.textBlocks
                            images = nextState.images
                            selectedStrokeId = null
                            selectedImageId = null
                            editingTextBlockId = null
                            longPressedItemId = null

                            coroutineScope.launch {
                                canvasAlphaAnimatable.snapTo(0.65f)
                                canvasAlphaAnimatable.animateTo(1f, springSpec)
                            }
                            animateViewport(nextState.zoomScale, Offset(nextState.panX, nextState.panY))
                            triggerHaptic()
                            saveCurrentBoard()
                        }
                    },
                    enabled = redoStack.isNotEmpty(),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Redo,
                        contentDescription = "Redo",
                        tint = if (redoStack.isNotEmpty()) Color.White else Color(0xFF555555),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Zoom controls container: aligned height 38dp, minimal transparent container
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    // Zoom Out button
                    IconButton(
                        onClick = {
                            val target = (zoomScale / 1.25f).coerceIn(0.15f, 6.0f)
                            animateViewport(target, panOffset)
                            triggerHaptic()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Text(
                            text = "−",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Zoom percentage indicator & reset
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                animateViewport(1.0f, Offset.Zero)
                                triggerHaptic()
                            }
                            .padding(horizontal = 4.dp)
                    )

                    // Zoom In button
                    IconButton(
                        onClick = {
                            val target = (zoomScale * 1.25f).coerceIn(0.15f, 6.0f)
                            animateViewport(target, panOffset)
                            triggerHaptic()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.Plus,
                            contentDescription = "Zoom In",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ======================= COLOR / BRUSH / TEXT FLYOUT =======================
        AnimatedVisibility(
            visible = showColorPalette && (selectedTool == CanvasTool.BRUSH || selectedTool == CanvasTool.TEXT),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1C1C1C),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Color swatches (Pure White default, signature Gold, Crimson Red, Electric Blue, Emerald Green, Purple, Slate)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PALETTE_COLORS.forEach { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFFFCC00) else Color(0xFF444444),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedColorHex = hex
                                        if (editingTextBlockId != null) {
                                            textBlocks = textBlocks.map { current ->
                                                if (current.id == editingTextBlockId) {
                                                    current.copy(colorHex = hex)
                                                } else current
                                            }
                                            saveCurrentBoard()
                                        }
                                        triggerHaptic()
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedTool == CanvasTool.BRUSH) {
                        // Stroke Width controls for Draw/Pencil
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(3f to "Fine", 6f to "Medium", 12f to "Bold").forEach { (width, label) ->
                                val isSelected = (brushWidth == width)
                                Surface(
                                    onClick = {
                                        brushWidth = width
                                        triggerHaptic()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFF2E2E2E) else Color.Transparent,
                                    border = if (isSelected) BorderStroke(1.dp, Color(0xFFFFCC00)) else null
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color(0xFFFFCC00) else Color(0xFF888888),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else if (selectedTool == CanvasTool.TEXT) {
                        // Text Size presets for Text tool
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(14f to "Small", 18f to "Regular", 24f to "Large", 32f to "Title").forEach { (size, label) ->
                                val currentEditingBlock = textBlocks.find { it.id == editingTextBlockId }
                                val isSelected = (currentEditingBlock?.fontSize == size)
                                Surface(
                                    onClick = {
                                        if (editingTextBlockId != null) {
                                            textBlocks = textBlocks.map { current ->
                                                if (current.id == editingTextBlockId) {
                                                    current.copy(fontSize = size)
                                                } else current
                                            }
                                            saveCurrentBoard()
                                        }
                                        triggerHaptic()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFF2E2E2E) else Color.Transparent,
                                    border = if (isSelected) BorderStroke(1.dp, Color(0xFFFFCC00)) else null
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color(0xFFFFCC00) else Color(0xFF888888),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ======================= BOTTOM FLOATING TOOLBAR (5 TOOLS) =======================
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .testTag("canvas_bottom_dock"),
            shape = CircleShape,
            color = Color(0xFF141414),
            border = BorderStroke(1.dp, Color(0xFF282828)),
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tool 1: SELECT / PAN (Pointer)
                DockToolButton(
                    icon = Lucide.MousePointer,
                    label = "Move",
                    isSelected = selectedTool == CanvasTool.SELECT,
                    onClick = {
                        selectedTool = CanvasTool.SELECT
                        showColorPalette = false
                        triggerHaptic()
                    }
                )

                // Tool 2: BRUSH / DRAW (Doodle - no dot indicator)
                DockToolButton(
                    icon = Lucide.Pen,
                    label = "Draw",
                    isSelected = selectedTool == CanvasTool.BRUSH,
                    onClick = {
                        if (selectedTool == CanvasTool.BRUSH) {
                            showColorPalette = !showColorPalette
                        } else {
                            selectedTool = CanvasTool.BRUSH
                            showColorPalette = true
                        }
                        selectedStrokeId = null
                        selectedImageId = null
                        editingTextBlockId = null
                        triggerHaptic()
                    }
                )

                // Tool 3: TEXT (Color & Size options)
                DockToolButton(
                    icon = Lucide.Type,
                    label = "Text",
                    isSelected = selectedTool == CanvasTool.TEXT,
                    onClick = {
                        if (selectedTool == CanvasTool.TEXT) {
                            showColorPalette = !showColorPalette
                        } else {
                            selectedTool = CanvasTool.TEXT
                            showColorPalette = true
                        }
                        selectedStrokeId = null
                        selectedImageId = null
                        triggerHaptic()
                    }
                )

                // Tool 4: ADD IMAGE
                DockToolButton(
                    icon = Lucide.Image,
                    label = "Image",
                    isSelected = selectedTool == CanvasTool.IMAGE,
                    onClick = {
                        selectedTool = CanvasTool.IMAGE
                        showColorPalette = false
                        selectedStrokeId = null
                        selectedImageId = null
                        editingTextBlockId = null
                        triggerHaptic()
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                // Tool 5: ERASER
                DockToolButton(
                    icon = Lucide.Eraser,
                    label = "Eraser",
                    isSelected = selectedTool == CanvasTool.ERASER,
                    onClick = {
                        selectedTool = CanvasTool.ERASER
                        showColorPalette = false
                        selectedStrokeId = null
                        selectedImageId = null
                        editingTextBlockId = null
                        triggerHaptic()
                    }
                )
            }
        }
    }
}

@Composable
private fun DockToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) Color(0xFF000000) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFFFFCC00)) else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) Color(0xFFFFCC00) else Color(0xFF8E8E93),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isSelected) {
                Text(
                    text = label,
                    color = Color(0xFFFFCC00),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}
