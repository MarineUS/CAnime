package com.marineus.canime.feature.calendar.data

import com.marineus.canime.feature.calendar.model.BirthdayCharacter

internal data class MissingBirthdayCharacter(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val siteUrl: String?,
    val mediaTitles: List<String>,
    val mediaIds: List<Int>,
    val role: String,
)

internal data class CharacterFetchResult(
    val birthdays: List<BirthdayCharacter>,
    val missingBirthdays: List<MissingBirthdayCharacter>,
    val mediaIds: Set<Int>,
)

internal enum class BirthdaySource { ANILIST, ACDB, MIKAI }

data class CalendarDataStats(
    val totalCharacters: Int = 0,
)

data class SyncSummary(
    val addedCharacters: Int,
    val stats: CalendarDataStats,
)

data class SyncProgress(
    val fraction: Float,
    val message: String,
)
