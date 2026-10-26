package com.marineus.canime.feature.calendar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthFactoryTest {
    @Test
    fun `month grid always contains six Monday-first weeks`() {
        val month = CalendarMonthFactory.create(YearMonth.of(2026, 10))

        assertEquals(6, month.weeks.size)
        assertTrue(month.weeks.all { it.size == 7 })
        assertEquals(DayOfWeek.MONDAY, month.weeks.first().first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, month.weeks.last().last().date.dayOfWeek)
    }

    @Test
    fun `leap year February includes February 29`() {
        val month = CalendarMonthFactory.create(YearMonth.of(2024, 2))
        val leapDay = month.weeks.flatten().single { it.date == LocalDate.of(2024, 2, 29) }

        assertTrue(leapDay.isInDisplayedMonth)
    }

    @Test
    fun `January grid includes adjacent December dates as out of month`() {
        val month = CalendarMonthFactory.create(YearMonth.of(2026, 1))
        val decemberDay = month.weeks.flatten().first { it.date == LocalDate.of(2025, 12, 29) }
        val januaryDay = month.weeks.flatten().first { it.date == LocalDate.of(2026, 1, 1) }

        assertFalse(decemberDay.isInDisplayedMonth)
        assertTrue(januaryDay.isInDisplayedMonth)
    }
}
