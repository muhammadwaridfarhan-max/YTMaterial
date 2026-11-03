package com.example.ytclient

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.example.ytclient.settings.AppSettings
import com.example.ytclient.settings.SettingsManager
import com.example.ytclient.ui.App
import com.example.ytclient.ui.theme.YTTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initial = runBlocking { SettingsManager.settings.first() }
        YTApp.applyLanguage(this, initial.language)

        setContent {
            val settings by SettingsManager.settings.collectAsState(initial = initial)
            LaunchedEffect(settings.language) {
                YTApp.applyLanguage(this@MainActivity, settings.language)
            }
            // Always show the composition for blur state updates
            LaunchedEffect(settings.blurEnabled) {
                // Re-read blur state; bar will read from SettingsManager directly
            }
            YTTheme(settings) {
                App()
            }
        }
    }
}
