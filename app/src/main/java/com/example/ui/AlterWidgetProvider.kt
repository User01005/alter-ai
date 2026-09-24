package com.example.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AlterCard
import com.example.data.AlterDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class AlterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AlterDatabase.getDatabase(context)
            val cardsFlow = database.cardDao().getAllCards()
            val cards = try { cardsFlow.first() } catch (e: Exception) { emptyList<AlterCard>() }

            var pendingCount = 0
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
                                    pendingCount++
                                }
                            } else {
                                val todoRegex = Regex("^(?:[-*+•]|\\d+[.)])?\\s*\\[\\s*\\]\\s*(.*)\$")
                                val match = todoRegex.find(trimmed)
                                if (match != null) {
                                    val taskText = match.groupValues[1].trim()
                                    if (taskText.isNotEmpty()) {
                                        pendingCount++
                                    }
                                }
                            }
                        }
                    }
                }
            }

            for (appWidgetId in appWidgetIds) {
                updateWidgetInstance(context, appWidgetManager, appWidgetId, pendingCount)
            }
        }
    }

    private fun updateWidgetInstance(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        pendingCount: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

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

        // Apply theme colors dynamically to RemoteViews elements
        if (isNight) {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg)
            views.setTextColor(R.id.widget_title, android.graphics.Color.parseColor("#B3FFFFFF"))
            views.setTextColor(R.id.widget_count, android.graphics.Color.parseColor("#CCCCCC"))
            views.setTextColor(R.id.empty_text, android.graphics.Color.parseColor("#888888"))
            views.setInt(R.id.widget_divider, "setBackgroundColor", android.graphics.Color.parseColor("#333333"))
            views.setInt(R.id.btn_widget_type, "setBackgroundResource", R.drawable.widget_button_secondary)
            views.setInt(R.id.btn_widget_record, "setBackgroundResource", R.drawable.widget_button_primary)
            views.setInt(R.id.btn_widget_type_icon, "setColorFilter", android.graphics.Color.parseColor("#FFFFFF"))
            views.setInt(R.id.btn_widget_record_icon, "setColorFilter", android.graphics.Color.parseColor("#FFFFFF"))
        } else {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_light)
            views.setTextColor(R.id.widget_title, android.graphics.Color.parseColor("#111111"))
            views.setTextColor(R.id.widget_count, android.graphics.Color.parseColor("#666666"))
            views.setTextColor(R.id.empty_text, android.graphics.Color.parseColor("#666666"))
            views.setInt(R.id.widget_divider, "setBackgroundColor", android.graphics.Color.parseColor("#E5E7EB"))
            views.setInt(R.id.btn_widget_type, "setBackgroundResource", R.drawable.widget_button_secondary_light)
            views.setInt(R.id.btn_widget_record, "setBackgroundResource", R.drawable.widget_button_secondary_light)
            views.setInt(R.id.btn_widget_type_icon, "setColorFilter", android.graphics.Color.parseColor("#374151"))
            views.setInt(R.id.btn_widget_record_icon, "setColorFilter", android.graphics.Color.parseColor("#374151"))
        }

        views.setTextViewText(R.id.widget_count, "$pendingCount pending")

        val svcIntent = Intent(context, AlterWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list, svcIntent)
        views.setEmptyView(R.id.widget_list, R.id.empty_text)

        val selectIntent = Intent(context, AlterWidgetProvider::class.java).apply {
            action = "WIDGET_ITEM_CLICK"
        }
        val selectPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            selectIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setPendingIntentTemplate(R.id.widget_list, selectPendingIntent)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 200,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, openPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_count, openPendingIntent)
        views.setOnClickPendingIntent(R.id.empty_text, openPendingIntent)

        val typeIntent = Intent(context, MainActivity::class.java).apply {
            action = "ALTER_TRIGGER_TEXT"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val typePendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 101,
            typeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_type, typePendingIntent)

        val recordIntent = Intent(context, MainActivity::class.java).apply {
            action = "ALTER_TRIGGER_AUDIO"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val recordPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 102,
            recordIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_record, recordPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, AlterWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(componentName)

        if (intent.action == "WIDGET_ITEM_CLICK") {
            val extraAction = intent.getStringExtra("extra_action")
            val cardId = intent.getLongExtra("card_id", -1L)
            val taskText = intent.getStringExtra("task_text") ?: ""

            if (extraAction == "TOGGLE_CHECK" && cardId != -1L && taskText.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AlterDatabase.getDatabase(context)
                    val card = db.cardDao().getCardById(cardId) ?: return@launch

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

                    val dm = context.resources.displayMetrics
                    val density = dm.density
                    val width = (360f * density).toInt().coerceIn(540, 960)
                    val height = (42f * density).toInt().coerceIn(80, 140)

                    WidgetDisintegrationTracker.activeCardId = cardId
                    WidgetDisintegrationTracker.activeTaskText = taskText
                    WidgetDisintegrationTracker.isAnimating = true
                    WidgetDisintegrationTracker.currentStep = 4

                    // Pre-generate the disintegration frame in memory
                    val frame = WidgetDisintegrationTracker.generateFrameBitmap(density, width, height, 4, isNight)
                    WidgetDisintegrationTracker.cachedBitmaps = listOf(frame)

                    // Notify widget ONCE to display the checked state and particle dissolution
                    // (Zero periodic loading indicators or flickering)
                    manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)

                    // Hold for smooth visual completion (420ms)
                    kotlinx.coroutines.delay(420)

                    fun toggleInString(text: String): Pair<String, Boolean> {
                        if (text.isBlank()) return Pair(text, false)
                        val lines = text.split("\n")
                        var toggled = false
                        val updatedLines = lines.map { line ->
                            val trimmed = line.trim()
                            if (!toggled && trimmed.endsWith(taskText)) {
                                if (trimmed.startsWith("☐")) {
                                    toggled = true
                                    line.replaceFirst("☐", "☑")
                                } else if (trimmed.startsWith("- [ ]") || trimmed.startsWith("- []")) {
                                    toggled = true
                                    line.replace("- [ ]", "- [x]").replace("- []", "- [x]")
                                } else if (trimmed.startsWith("[ ]") || trimmed.startsWith("[]")) {
                                    toggled = true
                                    line.replace("[ ]", "[x]").replace("[]", "[x]")
                                } else {
                                    line
                                }
                            } else {
                                line
                            }
                        }
                        return Pair(updatedLines.joinToString("\n"), toggled)
                    }

                    val (newProcessed, inProcessed) = toggleInString(card.processedContent)
                    val (newNotes, inNotes) = toggleInString(card.personalNotes)
                    val (newRaw, inRaw) = toggleInString(card.rawInput)

                    db.cardDao().insertCard(
                        card.copy(
                            processedContent = if (inProcessed) newProcessed else card.processedContent,
                            personalNotes = if (inNotes) newNotes else card.personalNotes,
                            rawInput = if (inRaw) newRaw else card.rawInput
                        )
                    )

                    // Clear disintegration state and notify widget of item removal
                    WidgetDisintegrationTracker.cachedBitmaps = emptyList()
                    WidgetDisintegrationTracker.activeCardId = -1L
                    WidgetDisintegrationTracker.activeTaskText = ""
                    WidgetDisintegrationTracker.currentStep = 0
                    WidgetDisintegrationTracker.isAnimating = false

                    manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)

                    val updateIntent = Intent(context, AlterWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(updateIntent)
                }
            } else if (extraAction == "OPEN_APP") {
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    action = "ALTER_TRIGGER_HIGHLIGHT"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("card_id", cardId)
                    putExtra("task_text", taskText)
                }
                context.startActivity(openIntent)
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val database = AlterDatabase.getDatabase(context)
                val cardsFlow = database.cardDao().getAllCards()
                val cards = try { cardsFlow.first() } catch (e: Exception) { emptyList() }

                var pendingCount = 0
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
                                        pendingCount++
                                    }
                                } else {
                                    val todoRegex = Regex("^(?:[-*+•]|\\d+[.)])?\\s*\\[\\s*\\]\\s*(.*)\$")
                                    val match = todoRegex.find(trimmed)
                                    if (match != null) {
                                        val taskText = match.groupValues[1].trim()
                                        if (taskText.isNotEmpty()) {
                                            pendingCount++
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ids.forEach { id ->
                    updateWidgetInstance(context, manager, id, pendingCount)
                }
                
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            }
        }
    }
}
