package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class TodoReminderReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val taskText = intent.getStringExtra("taskText") ?: "Scheduled Task Reminder"
        val cardId = intent.getLongExtra("cardId", -1L)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val prefs = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)
        val selectedPattern = prefs.getString("vib_pattern_name", "Intense Buzz") ?: "Intense Buzz"

        val vibePattern = when (selectedPattern) {
            "Steady Default" -> longArrayOf(0, 500, 250, 500)
            "Pulsing Glow" -> longArrayOf(0, 300, 200, 300, 200, 300, 500)
            "Heartbeat Echo" -> longArrayOf(0, 150, 150, 150, 600, 150, 150)
            "Intense Buzz" -> longArrayOf(0, 800, 200, 800, 200, 1200)
            "Calming Pulse" -> longArrayOf(0, 100, 400, 100, 400)
            "Off" -> longArrayOf(0)
            else -> longArrayOf(0, 800, 200, 800, 200, 1200)
        }

        val patternSuffix = selectedPattern.lowercase(java.util.Locale.getDefault()).replace(" ", "_")
        val channelId = "alter_vibrant_todo_channel_$patternSuffix"

        // Clean up old channels to prevent clutter
        try {
            notificationManager.deleteNotificationChannel("alter_vibrant_todo_channel_v2")
            notificationManager.deleteNotificationChannel("alter_vibrant_todo_channel_v3")
            notificationManager.deleteNotificationChannel("alter_vibrant_todo_channel_v4")
        } catch (e: Exception) {
            // Safe fallback
        }

        // Dual safeguard: Explicit manual vibration command to ensure a strong physical vibration
        if (selectedPattern != "Off") {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                }
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val audioAttributes = android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                        vibrator.vibrate(android.os.VibrationEffect.createWaveform(vibePattern, -1), audioAttributes)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(vibePattern, -1)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete all previous active todo channels except the current selection to force custom vibration refresh
            try {
                notificationManager.notificationChannels.forEach { existingChannel ->
                    if (existingChannel.id.startsWith("alter_vibrant_todo_channel_") && existingChannel.id != channelId) {
                        notificationManager.deleteNotificationChannel(existingChannel.id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val channel = NotificationChannel(
                channelId,
                "Alter Todo Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies scheduled tasks and todos"
                enableLights(true)
                lightColor = android.graphics.Color.YELLOW
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                
                // Explicitly bind sound with snazzy attributes to the NotificationChannel
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)

                // Override and bypass standard OS automatic vibration fallback to ensure only the app's custom design pattern is physically felt
                enableVibration(false)
                vibrationPattern = null
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            cardId.toInt(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.drawable.ic_notification_small)
            .setContentTitle(taskText)
            .setContentText("Scheduled slate task")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(null) // Prevent system from overriding with a default platform pattern

        notificationManager.notify(cardId.toInt() * 31 + taskText.hashCode(), notificationBuilder.build())
    }
}
