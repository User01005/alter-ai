package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Random

/**
 * Unified State Machine for task completion lifecycle:
 * IDLE -> COMPLETE -> LOADING -> DISINTEGRATING -> COLLAPSING -> COMPLETED
 *
 * This explicit progression guarantees:
 * 1. Immediate visual feedback (COMPLETE: yellow checkbox pop)
 * 2. Stable progress ring coordination without race conditions (LOADING: progress ring glides forward)
 * 3. Static height lock during particle dissolution (DISINTEGRATING: 0 layout shift for adjacent rows)
 * 4. Clean spatial closure before database commit (COLLAPSING: 0dp height before row is removed)
 */
enum class QuestAnimationPhase {
    IDLE,
    COMPLETE,       // Checkbox clicked, visual checkmark + yellow pop (marks virtual completion)
    LOADING,        // Brief loading window (micro-spinner/pulse; progress ring animates smoothly)
    DISINTEGRATING, // Particle dissolution sweep (strict static height lock!)
    COLLAPSING,     // Content dissolved, vertical slot collapses to 0dp
    COMPLETED       // Item removed from composition, committed to DB
}

/**
 * Single source of truth for task animation states and progress ring synchronization.
 */
class QuestAnimationStateController {
    // Map of active taskKey -> current lifecycle phase
    private val _animatingTasks = mutableStateMapOf<String, QuestAnimationPhase>()
    val animatingTasks: Map<String, QuestAnimationPhase> get() = _animatingTasks

    // Global flag indicating whether any quest animation is active.
    private val _isAnyQuestAnimating = MutableStateFlow(false)
    val isAnyQuestAnimating: StateFlow<Boolean> = _isAnyQuestAnimating.asStateFlow()

    fun getPhase(taskKey: String): QuestAnimationPhase {
        return _animatingTasks[taskKey] ?: QuestAnimationPhase.IDLE
    }

    fun isTaskAnimating(taskKey: String): Boolean {
        val phase = getPhase(taskKey)
        return phase != QuestAnimationPhase.IDLE && phase != QuestAnimationPhase.COMPLETED
    }

    fun isTaskChecked(taskKey: String): Boolean {
        val phase = getPhase(taskKey)
        return phase != QuestAnimationPhase.IDLE
    }

    /**
     * Checks if task should be counted as completed for progress ring purposes.
     * Prevents race conditions between the progress ring and row removal.
     */
    fun isTaskVirtuallyCompleted(taskKey: String, isDbCompleted: Boolean): Boolean {
        if (isDbCompleted) return true
        val phase = getPhase(taskKey)
        return phase != QuestAnimationPhase.IDLE
    }

    /**
     * Computes the synchronized completion fraction (0f..1f) across all tasks.
     * Evaluates virtual completion so progress ring glides forward the instant COMPLETE is triggered.
     */
    fun computeProgress(todoItems: List<TodoItem>): Float {
        if (todoItems.isEmpty()) return 0f
        val completed = computeCompletedCount(todoItems)
        return (completed.toFloat() / todoItems.size.toFloat()).coerceIn(0f, 1f)
    }

    fun computeCompletedCount(todoItems: List<TodoItem>): Int {
        if (todoItems.isEmpty()) return 0
        return todoItems.count { todo ->
            val key = "${todo.cardId}_${todo.originalLine}_${todo.taskText}"
            isTaskVirtuallyCompleted(key, todo.isCompleted)
        }
    }

    fun registerPhase(taskKey: String, phase: QuestAnimationPhase) {
        if (phase == QuestAnimationPhase.IDLE || phase == QuestAnimationPhase.COMPLETED) {
            _animatingTasks.remove(taskKey)
        } else {
            _animatingTasks[taskKey] = phase
        }
        _isAnyQuestAnimating.value = _animatingTasks.isNotEmpty()
    }

    fun clearTask(taskKey: String) {
        _animatingTasks.remove(taskKey)
        _isAnyQuestAnimating.value = _animatingTasks.isNotEmpty()
    }
}

val LocalQuestAnimationController = compositionLocalOf { QuestAnimationStateController() }

@Composable
fun rememberQuestAnimationController(): QuestAnimationStateController {
    return remember { QuestAnimationStateController() }
}

/**
 * High-performance digital dust particle for smooth arrow-lead disintegration.
 */
private class QuestDustParticle(
    val xRatio: Float,
    val yRatio: Float,
    val sizePx: Float,
    val sweepTrigger: Float,
    val driftX: Float,
    val driftY: Float,
    val lifespan: Float,
    val opacity: Float,
    val isCircle: Boolean,
    val color: Color
)

/**
 * Enhanced Quest Row Animation Wrapper with:
 * 1. Explicit Complete -> Loading -> Disintegrate -> Collapse state machine
 * 2. Strictly static height lock during active dissolution to prevent adjacent row jumps
 * 3. Staggered entrance animation support for 'Reset day' task restoration
 */
@Composable
fun QuestRowWrapper(
    taskKey: String,
    isCompleted: Boolean,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    enableDisintegration: Boolean = true,
    staggerIndex: Int = 0,
    staggerTrigger: Long = 0L,
    controller: QuestAnimationStateController = LocalQuestAnimationController.current,
    content: @Composable (phase: QuestAnimationPhase, isChecked: Boolean, startCompletion: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(QuestAnimationPhase.IDLE) }
    var measuredHeightPx by remember { mutableIntStateOf(0) }
    var measuredWidthPx by remember { mutableIntStateOf(0) }

    val disintegrationProgress = remember { Animatable(0f) }
    val collapseProgress = remember { Animatable(1f) }

    // Staggered entrance animation for 'Reset day' functionality
    val entranceAlpha = remember(staggerTrigger) { Animatable(if (staggerTrigger > 0L) 0f else 1f) }
    val entranceOffsetY = remember(staggerTrigger) { Animatable(if (staggerTrigger > 0L) 18f else 0f) }

    LaunchedEffect(staggerTrigger) {
        if (staggerTrigger > 0L) {
            val staggerDelay = (staggerIndex * 38L).coerceAtMost(600L)
            delay(staggerDelay)
            launch {
                entranceAlpha.animateTo(1f, animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing))
            }
            launch {
                entranceOffsetY.animateTo(0f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f))
            }
        }
    }

    // Pre-calculated particles generated once per taskKey
    val particles = remember(taskKey) {
        val rand = Random(taskKey.hashCode().toLong() xor 0x7E3779B9L)
        val count = 480
        val list = ArrayList<QuestDustParticle>(count)
        val palette = listOf(
            Color(0xFF27272A),
            Color(0xFF18181B),
            Color(0xFF3F3F46),
            Color(0xFF52525B),
            Color(0xFF71717A),
            Color(0xFFA1A1AA),
            Color(0xFFE4E4E7),
            Color(0xFFFFCC00)
        )
        for (i in 0 until count) {
            val xRatio = (i.toFloat() / count.toFloat()) * 0.99f + (rand.nextFloat() - 0.5f) * 0.02f
            val yRatio = rand.nextFloat()
            val yCentered = (yRatio - 0.5f) * 2f
            val arrowLead = (1f - (yCentered * yCentered).coerceIn(0f, 1f)) * 0.14f

            val noiseFront = (
                kotlin.math.sin(xRatio * 32f + yRatio * 22f) * 0.45f +
                kotlin.math.cos(xRatio * 20f - yRatio * 28f) * 0.45f +
                kotlin.math.sin(yRatio * 48f) * 0.25f
            ) * 0.03f

            val sweepTrigger = (xRatio * 0.88f - arrowLead + noiseFront).coerceIn(0.01f, 0.98f)
            val size = if (rand.nextFloat() < 0.72f) 1.2f else 1.8f
            val isCircle = rand.nextFloat() < 0.60f

            val tier = rand.nextFloat()
            val (lifespan, driftX, opacity) = when {
                tier < 0.30f -> Triple(0.20f + rand.nextFloat() * 0.08f, 3f + rand.nextFloat() * 7f, 0.80f + rand.nextFloat() * 0.20f)
                tier < 0.75f -> Triple(0.38f + rand.nextFloat() * 0.16f, 1.5f + rand.nextFloat() * 4f, 0.65f + rand.nextFloat() * 0.30f)
                else -> Triple(0.65f + rand.nextFloat() * 0.28f, 0.5f + rand.nextFloat() * 2f, 0.50f + rand.nextFloat() * 0.35f)
            }

            val driftY = (rand.nextFloat() - 0.5f) * 3f
            val isYellow = rand.nextFloat() < 0.18f
            val col = if (isYellow) Color(0xFFFFCC00) else palette[rand.nextInt(palette.size)]

            list.add(
                QuestDustParticle(
                    xRatio = xRatio.coerceIn(0f, 1f),
                    yRatio = yRatio.coerceIn(0f, 1f),
                    sizePx = size,
                    sweepTrigger = sweepTrigger,
                    driftX = driftX,
                    driftY = driftY,
                    lifespan = lifespan,
                    opacity = opacity,
                    isCircle = isCircle,
                    color = col
                )
            )
        }
        list
    }

    // Reusable Path objects to eliminate GC frame drops
    val cachedErasePath = remember { Path() }
    val cachedBandPath = remember { Path() }

    fun startCompletion() {
        if (phase != QuestAnimationPhase.IDLE) return

        if (!enableDisintegration) {
            // Standard instant completion for other sections
            onCompleted()
            return
        }

        coroutineScope.launch {
            // Step 1: COMPLETE - Instant visual responsiveness, yellow checkbox pop
            // Immediately registers with the controller so progress ring glides forward
            phase = QuestAnimationPhase.COMPLETE
            controller.registerPhase(taskKey, QuestAnimationPhase.COMPLETE)
            delay(110L)

            // Step 2: LOADING - Micro loading state, ensures smooth progress ring interpolation
            phase = QuestAnimationPhase.LOADING
            controller.registerPhase(taskKey, QuestAnimationPhase.LOADING)
            delay(130L)

            // Step 3: DISINTEGRATING - Parabolic arrow sweep + organic dust (Height strictly locked!)
            phase = QuestAnimationPhase.DISINTEGRATING
            controller.registerPhase(taskKey, QuestAnimationPhase.DISINTEGRATING)
            disintegrationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750, easing = LinearEasing)
            )

            // Step 4: COLLAPSING - Height smoothly collapses to 0dp after particles vanish
            phase = QuestAnimationPhase.COLLAPSING
            controller.registerPhase(taskKey, QuestAnimationPhase.COLLAPSING)
            collapseProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )

            // Step 5: COMPLETED - Safe DB commit with height already 0dp (zero layout jump)
            phase = QuestAnimationPhase.COMPLETED
            controller.registerPhase(taskKey, QuestAnimationPhase.COMPLETED)
            onCompleted()
        }
    }

    val isChecked = isCompleted || (phase != QuestAnimationPhase.IDLE)

    // STABLE LAYOUT HEIGHT LOCK:
    // During COMPLETE, LOADING, and DISINTEGRATING phases, height is locked strictly to measuredHeightPx.toDp()
    // This completely prevents content jumps or re-measure jitter for adjacent rows.
    val animatedHeightModifier = when (phase) {
        QuestAnimationPhase.COLLAPSING -> {
            val targetDp = with(density) { (measuredHeightPx * collapseProgress.value).toDp() }
            Modifier.height(targetDp)
        }
        QuestAnimationPhase.COMPLETED -> Modifier.height(0.dp)
        QuestAnimationPhase.COMPLETE,
        QuestAnimationPhase.LOADING,
        QuestAnimationPhase.DISINTEGRATING -> {
            if (measuredHeightPx > 0) {
                Modifier.height(with(density) { measuredHeightPx.toDp() })
            } else {
                Modifier
            }
        }
        else -> Modifier
    }

    // Staggered entrance modifier for 'Reset day'
    val entranceModifier = if (staggerTrigger > 0L) {
        Modifier.graphicsLayer {
            alpha = entranceAlpha.value
            translationY = entranceOffsetY.value * density.density
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(entranceModifier)
            .then(animatedHeightModifier)
            .clipToBounds()
            .onSizeChanged {
                // Strictly lock height on IDLE only; ignore any measurements during active dissolution
                if (phase == QuestAnimationPhase.IDLE && it.height > 0) {
                    measuredHeightPx = it.height
                    measuredWidthPx = it.width
                }
            }
    ) {
        // Content rendering with hardware-accelerated DstOut erasure
        if (phase != QuestAnimationPhase.COMPLETED && collapseProgress.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()

                        val dProgress = disintegrationProgress.value
                        if (phase == QuestAnimationPhase.DISINTEGRATING && dProgress > 0.005f) {
                            val canvasW = size.width
                            val canvasH = size.height
                            val sweepX = dProgress * (canvasW + 70f)
                            val arrowDepth = 28.dp.toPx()

                            // Reusable parabolic arrow path
                            cachedErasePath.reset()
                            cachedErasePath.moveTo(0f, 0f)
                            val steps = 16
                            for (k in 0..steps) {
                                val y = canvasH * (k.toFloat() / steps.toFloat())
                                val yCent = ((k.toFloat() / steps.toFloat()) - 0.5f) * 2f
                                val curveOffset = (1f - (yCent * yCent).coerceIn(0f, 1f)) * arrowDepth
                                val edgeX = (sweepX - arrowDepth + curveOffset).coerceAtLeast(0f)
                                cachedErasePath.lineTo(edgeX, y)
                            }
                            cachedErasePath.lineTo(0f, canvasH)
                            cachedErasePath.close()

                            drawPath(
                                path = cachedErasePath,
                                color = Color.Black,
                                blendMode = BlendMode.DstOut
                            )

                            // Feathered trail band
                            val trailBandW = 20.dp.toPx()
                            cachedBandPath.reset()
                            for (k in 0..steps) {
                                val y = canvasH * (k.toFloat() / steps.toFloat())
                                val yCent = ((k.toFloat() / steps.toFloat()) - 0.5f) * 2f
                                val curveOffset = (1f - (yCent * yCent).coerceIn(0f, 1f)) * arrowDepth
                                val edgeX = (sweepX - arrowDepth + curveOffset).coerceAtLeast(0f)
                                if (k == 0) cachedBandPath.moveTo(edgeX, y) else cachedBandPath.lineTo(edgeX, y)
                            }
                            for (k in steps downTo 0) {
                                val y = canvasH * (k.toFloat() / steps.toFloat())
                                val yCent = ((k.toFloat() / steps.toFloat()) - 0.5f) * 2f
                                val curveOffset = (1f - (yCent * yCent).coerceIn(0f, 1f)) * arrowDepth
                                val trailX = (sweepX - arrowDepth + curveOffset + trailBandW).coerceAtLeast(0f)
                                cachedBandPath.lineTo(trailX, y)
                            }
                            cachedBandPath.close()

                            drawPath(
                                path = cachedBandPath,
                                color = Color.Black.copy(alpha = 0.45f),
                                blendMode = BlendMode.DstOut
                            )
                        } else if (phase == QuestAnimationPhase.COLLAPSING) {
                            // During collapse, content is fully dissolved
                            drawRect(
                                color = Color.Black,
                                size = size,
                                blendMode = BlendMode.DstOut
                            )
                        }
                    }
            ) {
                content(phase, isChecked) { startCompletion() }
            }
        }

        // Particle canvas layer (Only active during DISINTEGRATING phase)
        if (phase == QuestAnimationPhase.DISINTEGRATING && disintegrationProgress.value in 0.01f..0.99f) {
            val dProgress = disintegrationProgress.value
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (measuredHeightPx > 0) with(density) { measuredHeightPx.toDp() } else 60.dp)
                    .clipToBounds()
            ) {
                val cardW = if (measuredWidthPx > 0) measuredWidthPx.toFloat() else size.width
                val cardH = if (measuredHeightPx > 0) measuredHeightPx.toFloat() else size.height
                val currentSweep = dProgress

                // Micro digital scanlines right along the arrow-head frontier
                val sweepX = currentSweep * (cardW + 70f)
                val arrowDepth = 28.dp.toPx()
                if (sweepX > 2f && sweepX < cardW + arrowDepth + 20f) {
                    val randNoise = Random(((dProgress * 2200).toLong()) xor 0x3CA)
                    val noiseCount = 6
                    for (n in 0 until noiseCount) {
                        val ny = (randNoise.nextFloat() * (cardH - 4f)).coerceIn(0f, cardH - 2f)
                        val yNormalized = ny / cardH.coerceAtLeast(1f)
                        val yCent = (yNormalized - 0.5f) * 2f
                        val curveOffset = (1f - (yCent * yCent).coerceIn(0f, 1f)) * arrowDepth
                        val frontAtY = sweepX - arrowDepth + curveOffset
                        val nx = (frontAtY - 14f + randNoise.nextFloat() * 16f).coerceIn(0f, cardW - 8f)
                        val nLen = 3f + randNoise.nextFloat() * 8f
                        val isYellowGlitch = n % 3 == 0
                        val nCol = if (isYellowGlitch) Color(0xFFFFCC00).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.65f)
                        drawRect(
                            color = nCol,
                            topLeft = Offset(nx, ny),
                            size = Size(nLen.coerceAtMost(cardW - nx), 1.5f * density.density)
                        )
                    }
                }

                // Digital dust particles forming the arrow head with an organic trailing tail
                particles.forEach { p ->
                    if (currentSweep >= p.sweepTrigger) {
                        val particleAge = currentSweep - p.sweepTrigger
                        val particleLife = (particleAge / p.lifespan).coerceIn(0f, 1f)

                        if (particleLife < 1f) {
                            val currentX = (p.xRatio * cardW + (p.driftX * particleLife * density.density)).coerceIn(0f, cardW - 2f)
                            val currentY = (p.yRatio * cardH + (p.driftY * particleLife * density.density)).coerceIn(0f, cardH - 2f)
                            val pAlpha = ((1f - particleLife) * p.opacity).coerceIn(0f, 1f)

                            if (pAlpha > 0.02f) {
                                val pSize = p.sizePx * density.density * (1f - particleLife * 0.25f)
                                val finalColor = p.color.copy(alpha = p.color.alpha * pAlpha)

                                if (p.isCircle) {
                                    drawCircle(
                                        color = finalColor,
                                        center = Offset(currentX + pSize * 0.5f, currentY + pSize * 0.5f),
                                        radius = pSize * 0.5f
                                    )
                                } else {
                                    drawRect(
                                        color = finalColor,
                                        topLeft = Offset(currentX, currentY),
                                        size = Size(
                                            pSize.coerceAtMost(cardW - currentX),
                                            pSize.coerceAtMost(cardH - currentY)
                                        )
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
