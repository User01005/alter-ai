package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.data.AlterDatabase
import com.example.data.AlterRepository
import com.example.ui.AlterAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AlterViewModel
import com.example.viewmodel.AlterViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AlterViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.triggerAudioInputOpen()
            viewModel.toggleAudioRecording()
        } else {
            Toast.makeText(
                this,
                "Microphone access is required to record in-app voice thoughts.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Notification permission is recommended to receive timely task reminders.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask for runtime notification permissions on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize SQLite Room persistency layer
        val database = AlterDatabase.getDatabase(applicationContext)
        val repository = AlterRepository(database.cardDao(), database.chatDao())

        // Obtain AlterViewModel with proper constructor injections
        val factory = AlterViewModelFactory(repository, applicationContext)
        viewModel = ViewModelProvider(this, factory)[AlterViewModel::class.java]

        // Parse any share intents trigger on cold app start
        handleShareIntent(intent)
        handleWidgetIntents(intent)

        setContent {
            val prefs = remember { getSharedPreferences("todo_reminders", MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(prefs.getString("app_theme_mode", "system") ?: "system") }
            
            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "app_theme_mode") {
                        themeMode = prefs.getString("app_theme_mode", "system") ?: "system"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            
            val isDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            
            MyApplicationTheme(darkTheme = isDarkTheme) {
                AlterAppScreen(
                    viewModel = viewModel,
                    onMicClick = { checkPermissionAndToggleRecord() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        handleWidgetIntents(intent)
    }

    private fun handleWidgetIntents(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            "ALTER_TRIGGER_TEXT" -> {
                viewModel.updateInputText("")
                viewModel.triggerTextInputOpen()
            }
            "ALTER_TRIGGER_AUDIO" -> {
                viewModel.triggerAudioInputOpen()
                checkPermissionAndToggleRecord()
            }
            "ALTER_TRIGGER_HIGHLIGHT" -> {
                val cardId = intent.getLongExtra("card_id", -1L)
                val taskText = intent.getStringExtra("task_text") ?: ""
                if (cardId != -1L) {
                    viewModel.triggerHighlight(cardId, taskText)
                }
            }
        }
    }

    /**
     * Checks permission state and launches Android RECORD_AUDIO systems if not pre-granted.
     */
    private fun checkPermissionAndToggleRecord() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.toggleAudioRecording()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * Dispatches share intents directly to corresponding ViewModel handlers based on types.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type || type.startsWith("text/")) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedText != null) {
                    viewModel.handleSharedText(sharedText)
                }
            } else if (type.startsWith("image/")) {
                val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                if (imageUri != null) {
                    viewModel.handleSharedImage(imageUri)
                }
            }
        }
    }
}
