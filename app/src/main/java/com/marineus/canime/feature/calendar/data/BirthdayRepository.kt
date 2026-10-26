package com.marineus.canime.feature.calendar.data

import android.content.Context
import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BirthdayRepository(context: Context) {
    private val cache = BirthdayCache(context.applicationContext)
    private val client = AniListClient()
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val lastUsername: String
        get() = preferences.getString(LAST_USERNAME, "").orEmpty()

    suspend fun hasCache(username: String): Boolean = withContext(Dispatchers.IO) {
        cache.hasCache(username.normalized())
    }

    suspend fun charactersForMonth(username: String, month: Int): List<BirthdayCharacter> =
        withContext(Dispatchers.IO) {
            if (username.isBlank()) emptyList()
            else cache.getCharactersForMonth(username.normalized(), month)
        }

    suspend fun sync(username: String): Int = withContext(Dispatchers.IO) {
        val normalizedUsername = username.normalized()
        require(normalizedUsername.isNotBlank()) { "AniList kullanıcı adı boş olamaz." }
        val characters = client.fetchBirthdayCharacters(normalizedUsername)
        cache.replaceUserCharacters(normalizedUsername, characters)
        preferences.edit().putString(LAST_USERNAME, normalizedUsername).apply()
        characters.size
    }

    private fun String.normalized(): String = trim().lowercase()

    private companion object {
        const val PREFERENCES = "birthday_preferences"
        const val LAST_USERNAME = "last_username"
    }
}
