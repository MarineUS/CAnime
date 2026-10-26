package com.marineus.canime.feature.calendar.model

data class BirthdayCharacter(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val birthMonth: Int,
    val birthDay: Int,
    val siteUrl: String?,
    val mediaTitles: List<String>,
)
