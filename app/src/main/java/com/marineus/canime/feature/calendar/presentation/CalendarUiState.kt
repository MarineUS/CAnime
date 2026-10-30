package com.marineus.canime.feature.calendar.presentation

import com.marineus.canime.feature.calendar.model.CalendarMonth
import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import com.marineus.canime.feature.calendar.data.CalendarDataStats
import java.time.LocalDate

data class CalendarUiState(
    val month: CalendarMonth,
    val selectedDate: LocalDate,
    val today: LocalDate,
    val birthdaysByDay: Map<Int, List<BirthdayCharacter>> = emptyMap(),
    val username: String = "",
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val syncError: String? = null,
    val dataStats: CalendarDataStats = CalendarDataStats(),
    val showInitialSetup: Boolean = false,
    val syncProgress: Float = 0f,
)
