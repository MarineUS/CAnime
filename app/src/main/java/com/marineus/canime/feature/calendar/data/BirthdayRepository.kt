package com.marineus.canime.feature.calendar.data

import android.content.Context
import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BirthdayRepository(context: Context) {
    private val cache = BirthdayCache(context.applicationContext)
    private val client = AniListClient()
    private val fallbackClient = MikaiClient()
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
        val result = client.fetchBirthdayCharacters(normalizedUsername)
        val missingIds = result.missingBirthdays.mapTo(hashSetOf()) { it.id }
        val cachedFallbacks = cache.getMikaiCharacters(normalizedUsername)
            .filter { it.id in missingIds }

        result.birthdays.forEach {
            cache.markFallbackChecked(normalizedUsername, it.id, resolved = true)
        }
        val cachedFallbackIds = cachedFallbacks.mapTo(hashSetOf()) { it.id }
        cache.upsertUnresolved(
            normalizedUsername,
            result.missingBirthdays.filterNot { it.id in cachedFallbackIds },
        )

        val newlyResolved = cache.unresolvedDue(
            username = normalizedUsername,
            limit = FALLBACK_LOOKUPS_PER_SYNC,
            retryBefore = System.currentTimeMillis() - FALLBACK_RETRY_MILLIS,
        ).mapNotNull { missing ->
            fallbackClient.resolveBirthday(missing).also { birthday ->
                cache.markFallbackChecked(
                    username = normalizedUsername,
                    characterId = missing.id,
                    resolved = birthday != null,
                )
            }
        }

        val fallbackCharacters = (cachedFallbacks + newlyResolved).associateBy { it.id }
        val characters = (fallbackCharacters.values + result.birthdays)
            .associateBy { it.id }
            .values
            .toList()
        val aniListIds = result.birthdays.mapTo(hashSetOf()) { it.id }
        cache.replaceUserCharacters(
            username = normalizedUsername,
            characters = characters,
            mikaiCharacterIds = fallbackCharacters.keys - aniListIds,
        )
        preferences.edit().putString(LAST_USERNAME, normalizedUsername).apply()
        characters.size
    }

    private fun String.normalized(): String = trim().lowercase()

    private companion object {
        const val PREFERENCES = "birthday_preferences"
        const val LAST_USERNAME = "last_username"
        const val FALLBACK_LOOKUPS_PER_SYNC = 8
        const val FALLBACK_RETRY_MILLIS = 7L * 24 * 60 * 60 * 1_000
    }
}
