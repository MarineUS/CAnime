package com.marineus.canime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.marineus.canime.feature.calendar.presentation.CalendarRoute
import com.marineus.canime.ui.theme.CAnimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE) }
            val systemDarkTheme = isSystemInDarkTheme()
            var isDarkTheme by rememberSaveable {
                mutableStateOf(
                    if (preferences.contains(DARK_THEME_KEY)) {
                        preferences.getBoolean(DARK_THEME_KEY, systemDarkTheme)
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
                        preferences.edit { putBoolean(DARK_THEME_KEY, isDarkTheme) }
                    },
                )
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "canime_preferences"
        const val DARK_THEME_KEY = "dark_theme"
    }
}
