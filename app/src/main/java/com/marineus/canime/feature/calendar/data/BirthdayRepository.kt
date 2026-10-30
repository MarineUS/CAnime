package com.marineus.canime.feature.calendar.data

import android.content.Context
import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class BirthdayRepository(context: Context) {
    private val cache = BirthdayCache(context.applicationContext)
    private val client = AniListClient()
    private val mikaiClient = MikaiClient()
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val lastUsername: String
        get() = preferences.getString(LAST_USERNAME, "").orEmpty()

    suspend fun hasCatalog(): Boolean = withContext(Dispatchers.IO) {
        cache.hasCache(CATALOG_USERNAME)
    }

    suspend fun prepareCatalog(onProgress: (SyncProgress) -> Unit): Int = withContext(Dispatchers.IO) {
        val characters = if (cache.hasCache(CATALOG_USERNAME)) {
            cache.getAllCharacters(CATALOG_USERNAME)
        } else {
            onProgress(SyncProgress(0.01f, "Genel karakter kataloğu AniList'ten alınıyor…"))
            client.fetchGlobalBirthdayCharacters { scannedCharacters ->
                val fraction = (scannedCharacters / 20_000f).coerceIn(0f, 1f) * 0.65f
                onProgress(
                    SyncProgress(
                        fraction = fraction,
                        message = "$scannedCharacters farklı karakter tarandı…",
                    ),
                )
            }.also { cache.replaceUserCharacters(CATALOG_USERNAME, it) }
        }
        onProgress(SyncProgress(1f, "Karakter kataloğu hazır."))
        characters.size
    }

    suspend fun charactersForMonth(username: String, month: Int): List<BirthdayCharacter> =
        withContext(Dispatchers.IO) {
            if (username.isBlank()) emptyList()
            else cache.getCharactersForMonth(username.normalized(), month)
        }

    suspend fun stats(username: String): CalendarDataStats = withContext(Dispatchers.IO) {
        if (username.isBlank()) CalendarDataStats() else cache.getStats(username.normalized())
    }

    suspend fun sync(
        username: String,
        onProgress: suspend (SyncProgress) -> Unit = {},
    ): SyncSummary = withContext(Dispatchers.IO) {
        val normalizedUsername = username.normalized()
        require(normalizedUsername.isNotBlank()) { "AniList kullanıcı adı boş olamaz." }
        onProgress(SyncProgress(0.08f, "Tamamlanan anime ve mangalar AniList'ten alınıyor…"))
        val previousCharacterIds = cache.getCharacterIds(normalizedUsername)
        val knownMediaIds = cache.getSyncedMediaIds(normalizedUsername)
        val needsInitialBaseline = knownMediaIds.isEmpty()
        val result = if (needsInitialBaseline) client.fetchBirthdayCharacters(normalizedUsername)
        else client.fetchNewBirthdayCharacters(normalizedUsername, knownMediaIds)
        onProgress(SyncProgress(0.42f, "Karakterler yerel veritabanına hazırlanıyor…"))
        val existingCharacters = if (!needsInitialBaseline) {
            cache.getAllCharacters(normalizedUsername)
        } else {
            emptyList()
        }
        val missingIds = result.missingBirthdays.mapTo(hashSetOf()) { it.id }
        val cachedFallbacks = cache.getFallbackCharacters(normalizedUsername)
            .filter {
                !needsInitialBaseline || it.id in missingIds
            }

        result.birthdays.forEach {
            cache.markFallbackChecked(normalizedUsername, it.id, resolved = true)
        }
        val cachedFallbackIds = cachedFallbacks.mapTo(hashSetOf()) { it.id }
        cache.upsertUnresolved(
            normalizedUsername,
            result.missingBirthdays.filterNot { it.id in cachedFallbackIds },
        )

        onProgress(SyncProgress(0.58f, "Eksik doğum günleri doğrulanmış kaynaklarda aranıyor…"))
        val due = cache.unresolvedDue(
            username = normalizedUsername,
            limit = FALLBACK_CANDIDATES,
            retryBefore = Long.MAX_VALUE,
        )
        val fallbackBatch = due.take(MIKAI_LOOKUPS)
        val newlyResolvedByMikai = mutableListOf<BirthdayCharacter>()
        for ((index, missing) in fallbackBatch.withIndex()) {
            onProgress(
                SyncProgress(
                    fraction = 0.58f + (index + 1f) / fallbackBatch.size * 0.28f,
                    message = "Eksik doğum günleri doğrulanıyor: ${index + 1}/${fallbackBatch.size}…",
                ),
            )
            val birthday = mikaiClient.resolveBirthday(missing)
            cache.markFallbackChecked(
                username = normalizedUsername,
                characterId = missing.id,
                resolved = birthday != null,
            )
            if (birthday != null) newlyResolvedByMikai += birthday
        }

        val newlyResolved = newlyResolvedByMikai
        onProgress(SyncProgress(0.86f, "Doğrulanan bilgiler veritabanına kaydediliyor…"))
        val fallbackCharacters = (cachedFallbacks + newlyResolved).associateBy { it.id }
        val cachedMikaiIds = cache.getCharactersFromSource(normalizedUsername, BirthdaySource.MIKAI)
            .mapTo(hashSetOf()) { it.id }
        val sourceById = cachedFallbacks.associate { character ->
            character.id to if (character.id in cachedMikaiIds) BirthdaySource.MIKAI else BirthdaySource.ACDB
        }.toMutableMap().apply {
            newlyResolvedByMikai.forEach { put(it.id, BirthdaySource.MIKAI) }
        }
        val catalogById = cache.getAllCharacters(CATALOG_USERNAME).associateBy { it.id }
        val catalogMatches = (result.birthdays.map { it.id } + result.missingBirthdays.map { it.id })
            .distinct()
            .mapNotNull(catalogById::get)
        val characters = mergeCharacters(
            existingCharacters + catalogMatches + fallbackCharacters.values + result.birthdays,
        )
        val aniListIds = result.birthdays.mapTo(hashSetOf()) { it.id }
        cache.replaceUserCharacters(
            username = normalizedUsername,
            characters = characters,
            fallbackSources = sourceById.filterKeys { it !in aniListIds },
        )
        cache.saveSyncedMediaIds(
            username = normalizedUsername,
            mediaIds = result.mediaIds,
            replace = needsInitialBaseline,
        )
        preferences.edit { putString(LAST_USERNAME, normalizedUsername) }
        onProgress(SyncProgress(1f, "Takvim hazırlanıyor…"))
        SyncSummary(
            addedCharacters = characters.count { it.id !in previousCharacterIds },
            stats = cache.getStats(normalizedUsername),
        )
    }

    private fun mergeCharacters(characters: List<BirthdayCharacter>): List<BirthdayCharacter> =
        characters.groupBy { it.id }.map { (_, versions) ->
            val newest = versions.last()
            newest.copy(mediaTitles = versions.flatMap { it.mediaTitles }.distinct().sorted())
        }

    private fun String.normalized(): String = trim().lowercase()

    private companion object {
        const val PREFERENCES = "birthday_preferences"
        const val LAST_USERNAME = "last_username"
        const val FALLBACK_CANDIDATES = 20_000
        const val MIKAI_LOOKUPS = 50
        const val CATALOG_USERNAME = "__global_catalog__"
    }
}
