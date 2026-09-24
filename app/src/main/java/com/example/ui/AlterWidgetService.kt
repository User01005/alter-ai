package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R
import com.example.data.AlterCard
import com.example.data.AlterDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Random

object WidgetDisintegrationTracker {
    @Volatile var activeCardId: Long = -1L
    @Volatile var activeTaskText: String = ""
    @Volatile var currentStep: Int = 0 // 0 = normal, 1..4 = progressive sweep
    @Volatile var isAnimating: Boolean = false
    @Volatile var cachedBitmaps: List<Bitmap> = emptyList()

    fun generateFrameBitmap(
        density: Float,
        width: Int,
        height: Int,
        step: Int,
        isNight: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // The text area begins after the checkbox: 4dp padding + 28dp checkbox + 10dp margin = 42dp
        val textStartX = 40f * density
        val textWidth = (width.toFloat() - textStartX).coerceAtLeast(100f)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isNight) Color.parseColor("#1b1b1b") else Color.parseColor("#F9FAFB")
            style = Paint.Style.FILL
        }

        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rand = Random(step * 313L + 77L)

        // Sweep across the text in 4 progressive stages
        val sweepFraction = when (step) {
            1 -> 0.18f
            2 -> 0.48f
            3 -> 0.78f
            else -> 1.08f
        }

        val sweepX = textStartX + sweepFraction * textWidth
        val arrowDepth = 22f * density // Chevron arrow-head pointing right (>)

        // 1. Solid background mask covering the text from textStartX up to the arrow tip
        val maskPath = Path().apply {
            moveTo(textStartX, 0f)
            lineTo((sweepX - arrowDepth).coerceAtLeast(textStartX), 0f)
            lineTo(sweepX.coerceAtLeast(textStartX), height * 0.5f)
            lineTo((sweepX - arrowDepth).coerceAtLeast(textStartX), height.toFloat())
            lineTo(textStartX, height.toFloat())
            close()
        }
        canvas.drawPath(maskPath, maskPaint)

        // 2. High-energy arrow-frontier particles and trailing ember dust
        val colors = listOf(
            Color.parseColor("#FFCC00"), // Alter yellow
            Color.WHITE,                 // Bright spark
            Color.parseColor("#FFDD44"), // Golden yellow
            Color.parseColor("#D4D4D8"), // Light silver
            Color.parseColor("#FFCC00")  // Core accent
        )

        val particleCount = when (step) {
            1 -> 45
            2 -> 60
            3 -> 50
            else -> 25
        }

        for (i in 0 until particleCount) {
            val yNorm = rand.nextFloat()
            val py = (yNorm * height.toFloat()).coerceIn(2f, height.toFloat() - 3f)
            val arrowOffset = (1f - Math.abs(yNorm - 0.5f) * 2f) * arrowDepth
            val frontierAtY = sweepX - arrowDepth + arrowOffset

            val maxTrail = 35f * density
            val trailDist = rand.nextFloat() * rand.nextFloat() * maxTrail
            val px = (frontierAtY - trailDist).coerceIn(textStartX, width.toFloat() - 4f)

            val pSize = (1.5f + rand.nextFloat() * 2.0f) * density
            particlePaint.color = colors[rand.nextInt(colors.size)]

            val fade = (1f - (trailDist / maxTrail)).coerceIn(0.3f, 1f)
            val alpha = if (step == 4) (140 * fade).toInt() else (240 * fade).toInt()
            particlePaint.alpha = alpha.coerceIn(30, 255)

            if (rand.nextBoolean()) {
                canvas.drawCircle(px, py, pSize * 0.5f, particlePaint)
            } else {
                canvas.drawRect(px, py, px + pSize, py + pSize, particlePaint)
            }
        }

        return bitmap
    }
}

class AlterWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return AlterWidgetFactory(this.applicationContext)
    }
}

class AlterWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var tasksList = listOf<Pair<Long, String>>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        if (WidgetDisintegrationTracker.isAnimating) {
            // Fast-path: preserve cached tasks list during disintegration frames.
            // Eliminates binder latency and prevents Android from flashing loading views!
            return
        }

        // Query tasks from database synchronously here as permitted inside onDataSetChanged
        runBlocking {
            val database = AlterDatabase.getDatabase(context)
            val cardsFlow = database.cardDao().getAllCards()
            val cards = try { cardsFlow.first() } catch (e: Exception) { emptyList<AlterCard>() }
            
            val pendingTasks = mutableListOf<Pair<Long, String>>()
            
            for (card in cards) {
                if (card.isDeleted) continue
                val sources = listOf(card.processedContent, card.personalNotes, if (card.type == "upcoming") card.rawInput else "")
                for (source in sources) {
                    if (source.isNotBlank()) {
                        val lines = source.split("\n")
                        for (line in lines) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("☐")) {
                                val taskText = trimmed.drop(1).trim()
                                if (taskText.isNotEmpty()) {
                                    pendingTasks.add(Pair(card.id, taskText))
                                }
                            } else {
                                val todoRegex = Regex("^(?:[-*+•]|\\d+[.)])?\\s*\\[\\s*\\]\\s*(.*)\$")
                                matchTodoRegex(trimmed)?.let { taskText ->
                                    if (taskText.isNotEmpty()) {
                                        pendingTasks.add(Pair(card.id, taskText))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            tasksList = pendingTasks
        }
    }

    private fun matchTodoRegex(trimmed: String): String? {
        val todoRegex = Regex("^(?:[-*+•]|\\d+[.)])?\\s*\\[\\s*\\]\\s*(.*)\$")
        return todoRegex.find(trimmed)?.groupValues?.get(1)?.trim()
    }

    override fun onDestroy() {
        tasksList = emptyList()
    }

    override fun getCount(): Int = tasksList.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= tasksList.size) {
            return RemoteViews(context.packageName, R.layout.widget_list_item)
        }
        
        val task = tasksList[position]
        val cardId = task.first
        val rawText = task.second

        val views = RemoteViews(context.packageName, R.layout.widget_list_item)

        // Dynamic coloring of list row text and checkboxes
        val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
        val themeMode = prefs.getString("app_theme_mode", "system") ?: "system"

        val isNight = when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> {
                val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }

        val isThisTaskDisintegrating = (cardId == WidgetDisintegrationTracker.activeCardId &&
                rawText == WidgetDisintegrationTracker.activeTaskText &&
                WidgetDisintegrationTracker.currentStep > 0)

        // Parse simple markdown symbols (like **bold**) for the widget item list
        val spannableText = parseMarkdownToSpannable(rawText)
        views.setTextViewText(R.id.widget_item_text, spannableText)

        if (isThisTaskDisintegrating) {
            val step = WidgetDisintegrationTracker.currentStep
            val overlayBitmap = WidgetDisintegrationTracker.cachedBitmaps.getOrNull(step - 1)
            
            // Keep native row visible so text does not jump or scale up
            views.setViewVisibility(R.id.widget_item_root, View.VISIBLE)
            // Immediately show checked checkbox in Alter yellow as particles ignite
            views.setImageViewResource(R.id.widget_item_checkbox, R.drawable.ic_checkbox_checked)
            views.setInt(R.id.widget_item_checkbox, "setColorFilter", Color.parseColor("#FFCC00"))
            
            if (isNight) {
                views.setTextColor(R.id.widget_item_text, Color.parseColor("#FFFFFF"))
            } else {
                views.setTextColor(R.id.widget_item_text, Color.parseColor("#1F2937"))
            }

            if (overlayBitmap != null && !overlayBitmap.isRecycled) {
                views.setViewVisibility(R.id.widget_item_particle_overlay, View.VISIBLE)
                views.setImageViewBitmap(R.id.widget_item_particle_overlay, overlayBitmap)
            } else {
                views.setViewVisibility(R.id.widget_item_particle_overlay, View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.widget_item_root, View.VISIBLE)
            views.setViewVisibility(R.id.widget_item_particle_overlay, View.GONE)

            views.setImageViewResource(R.id.widget_item_checkbox, R.drawable.ic_checkbox_unchecked)
            if (isNight) {
                views.setTextColor(R.id.widget_item_text, Color.parseColor("#FFFFFF"))
                views.setInt(R.id.widget_item_checkbox, "setColorFilter", Color.parseColor("#FFFFFF"))
            } else {
                views.setTextColor(R.id.widget_item_text, Color.parseColor("#1F2937"))
                views.setInt(R.id.widget_item_checkbox, "setColorFilter", Color.parseColor("#4B5563"))
            }

            // Fill-in Intent for checking off the item
            val checkIntent = Intent().apply {
                putExtra("extra_action", "TOGGLE_CHECK")
                putExtra("card_id", cardId)
                putExtra("task_text", rawText)
            }
            views.setOnClickFillInIntent(R.id.widget_item_checkbox, checkIntent)

            // Fill-in Intent for clicking the memory text or row (to open app)
            val openIntent = Intent().apply {
                putExtra("extra_action", "OPEN_APP")
                putExtra("card_id", cardId)
                putExtra("task_text", rawText)
            }
            views.setOnClickFillInIntent(R.id.widget_item_root, openIntent)
            views.setOnClickFillInIntent(R.id.widget_item_text, openIntent)
        }

        return views
    }

    override fun getLoadingView(): RemoteViews {
        // Never return null! Returning a valid RemoteViews prevents Android's RemoteViewsAdapter
        // from ever inflating the default framework loading spinner.
        val views = RemoteViews(context.packageName, R.layout.widget_list_item)
        views.setViewVisibility(R.id.widget_item_root, View.VISIBLE)

        val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
        val themeMode = prefs.getString("app_theme_mode", "system") ?: "system"
        val isNight = when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> {
                val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }

        if (isNight) {
            views.setTextColor(R.id.widget_item_text, Color.parseColor("#FFFFFF"))
        } else {
            views.setTextColor(R.id.widget_item_text, Color.parseColor("#1F2937"))
        }

        if (WidgetDisintegrationTracker.isAnimating && WidgetDisintegrationTracker.activeTaskText.isNotEmpty()) {
            views.setTextViewText(R.id.widget_item_text, parseMarkdownToSpannable(WidgetDisintegrationTracker.activeTaskText))
            views.setImageViewResource(R.id.widget_item_checkbox, R.drawable.ic_checkbox_checked)
            views.setInt(R.id.widget_item_checkbox, "setColorFilter", Color.parseColor("#FFCC00"))
            val overlayBitmap = WidgetDisintegrationTracker.cachedBitmaps.firstOrNull()
            if (overlayBitmap != null && !overlayBitmap.isRecycled) {
                views.setViewVisibility(R.id.widget_item_particle_overlay, View.VISIBLE)
                views.setImageViewBitmap(R.id.widget_item_particle_overlay, overlayBitmap)
            } else {
                views.setViewVisibility(R.id.widget_item_particle_overlay, View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.widget_item_particle_overlay, View.GONE)
            views.setImageViewResource(R.id.widget_item_checkbox, R.drawable.ic_checkbox_unchecked)
            if (isNight) {
                views.setInt(R.id.widget_item_checkbox, "setColorFilter", Color.parseColor("#FFFFFF"))
            } else {
                views.setInt(R.id.widget_item_checkbox, "setColorFilter", Color.parseColor("#4B5563"))
            }
        }
        return views
    }

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        if (position in tasksList.indices) {
            val item = tasksList[position]
            return (item.first * 31L + item.second.hashCode().toLong())
        }
        return position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    // Bold Markdown parser helper for widget
    private fun parseMarkdownToSpannable(text: String): CharSequence {
        val pattern = Regex("\\*\\*(.*?)\\*\\*")
        val sb = android.text.SpannableStringBuilder(text)
        var match = pattern.find(sb)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            val innerText = match.groupValues[1]
            sb.replace(start, end, innerText)
            sb.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                start,
                start + innerText.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            match = pattern.find(sb)
        }
        return sb
    }
}
