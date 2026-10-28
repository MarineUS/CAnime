package com.marineus.canime.feature.calendar.data

import com.marineus.canime.feature.calendar.model.BirthdayCharacter

internal data class MissingBirthdayCharacter(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val siteUrl: String?,
    val mediaTitles: List<String>,
)

internal data class CharacterFetchResult(
    val birthdays: List<BirthdayCharacter>,
    val missingBirthdays: List<MissingBirthdayCharacter>,
)
