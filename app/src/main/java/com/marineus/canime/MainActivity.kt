package com.marineus.canime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.marineus.canime.feature.calendar.presentation.CalendarRoute
import com.marineus.canime.ui.theme.CAnimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = getSharedPreferences("canime_preferences", MODE_PRIVATE)
            val systemDarkTheme = isSystemInDarkTheme()
            var isDarkTheme by rememberSaveable {
                mutableStateOf(
                    if (preferences.contains("dark_theme")) {
                        preferences.getBoolean("dark_theme", systemDarkTheme)
                    } else {
                        systemDarkTheme
                    },
                )
            }

            CAnimeTheme(darkTheme = isDarkTheme) {
                CalendarRoute(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = {
                        isDarkTheme = !isDarkTheme
                        preferences.edit().putBoolean("dark_theme", isDarkTheme).apply()
                    },
                )
            }
        }
    }
}
