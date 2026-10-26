package com.marineus.canime.feature.calendar.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class CalendarDay(
    val date: LocalDate,
    val isInDisplayedMonth: Boolean,
)

data class CalendarMonth(
    val yearMonth: YearMonth,
    val weeks: List<List<CalendarDay>>,
)

object CalendarMonthFactory {
    private const val DAYS_IN_WEEK = 7
    private const val WEEKS_IN_GRID = 6

    fun create(yearMonth: YearMonth): CalendarMonth {
        val firstDay = yearMonth.atDay(1)
        val daysBeforeMonth = firstDay.dayOfWeek.mondayBasedIndex()
        val gridStart = firstDay.minusDays(daysBeforeMonth.toLong())

        val weeks = (0 until WEEKS_IN_GRID).map { weekIndex ->
            (0 until DAYS_IN_WEEK).map { dayIndex ->
                val date = gridStart.plusDays((weekIndex * DAYS_IN_WEEK + dayIndex).toLong())
                CalendarDay(
                    date = date,
                    isInDisplayedMonth = YearMonth.from(date) == yearMonth,
                )
            }
        }

        return CalendarMonth(yearMonth = yearMonth, weeks = weeks)
    }

    private fun DayOfWeek.mondayBasedIndex(): Int = value - DayOfWeek.MONDAY.value
}
