package com.marineus.canime.feature.calendar.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marineus.canime.feature.calendar.model.CalendarMonthFactory
import com.marineus.canime.ui.theme.CAnimeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val selectedDate = LocalDate.of(2026, 10, 26)

    @Test
    fun calendarShowsMonthWeekDaysAndSelectedDate() {
        composeRule.setContent {
            CAnimeTheme(dynamicColor = false) {
                CalendarScreen(
                    state = previewState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onTodayClick = {},
                    onDateSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Ekim 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Pzt").assertIsDisplayed()
        composeRule.onNodeWithText("26 Ekim 2026, Pazartesi").assertIsDisplayed()
    }

    @Test
    fun navigationAndDateSelectionInvokeCallbacks() {
        var previousClicks = 0
        var clickedDate: LocalDate? = null
        composeRule.setContent {
            CAnimeTheme(dynamicColor = false) {
                CalendarScreen(
                    state = previewState(),
                    onPreviousMonth = { previousClicks++ },
                    onNextMonth = {},
                    onTodayClick = {},
                    onDateSelected = { clickedDate = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Önceki ay").performClick()
        composeRule.onNodeWithContentDescription("5 Ekim 2026, Pazartesi").performClick()

        composeRule.runOnIdle {
            assertEquals(1, previousClicks)
            assertEquals(LocalDate.of(2026, 10, 5), clickedDate)
        }
    }

    @Test
    fun themeButtonInvokesToggleCallback() {
        var toggleClicks = 0
        composeRule.setContent {
            CAnimeTheme(darkTheme = false, dynamicColor = false) {
                CalendarScreen(
                    state = previewState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onTodayClick = {},
                    onDateSelected = {},
                    isDarkTheme = false,
                    onThemeToggle = { toggleClicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Koyu temaya geç").performClick()

        composeRule.runOnIdle { assertEquals(1, toggleClicks) }
    }

    private fun previewState() = CalendarUiState(
        month = CalendarMonthFactory.create(YearMonth.of(2026, 10)),
        selectedDate = selectedDate,
        today = selectedDate,
    )
}
