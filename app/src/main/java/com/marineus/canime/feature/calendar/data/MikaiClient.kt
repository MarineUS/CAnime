package com.marineus.canime.feature.calendar.data

import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.DateTimeException
import java.time.LocalDate

/** Resolves only public character data; AniList usernames are never sent to Mikai. */
internal class MikaiClient {
    fun resolveBirthday(character: MissingBirthdayCharacter): BirthdayCharacter? = runCatching {
        val query = URLEncoder.encode(character.name, Charsets.UTF_8.name())
        val candidateIds = linkedSetOf<Int>()
        character.mediaIds.take(MEDIA_CONTEXT_LIMIT).forEach { aniListMediaId ->
            val mikaiAnimeId = mikaiAnimeId(aniListMediaId) ?: return@forEach
            candidateIds += searchCandidateIds(query, mikaiAnimeId)
        }
        candidateIds += searchCandidateIds(query, animeId = null)

        candidateIds.take(DETAIL_CANDIDATE_LIMIT).forEach { candidateId ->
            val detail = get("$BASE_URL/characters/$candidateId").getJSONObject("result")
            if (!detail.hasAniListId(character.id)) return@forEach
            val (month, day) = detail.optString("birthday").parseBirthday() ?: return@forEach
            return BirthdayCharacter(
                id = character.id,
                name = character.name,
                imageUrl = character.imageUrl,
                birthMonth = month,
                birthDay = day,
                siteUrl = character.siteUrl,
                mediaTitles = character.mediaTitles,
            )
        }
        null
    }.getOrNull()

    private fun searchCandidateIds(query: String, animeId: Int?): List<Int> = runCatching {
        val animeFilter = animeId?.let { "&anime=$it" }.orEmpty()
        val search = get("$BASE_URL/characters?search=$query$animeFilter&limit=$SEARCH_LIMIT")
        val results = search.optJSONArray("result") ?: return emptyList()
        buildList { repeat(results.length()) { index -> add(results.getJSONObject(index).getInt("id")) } }
    }.getOrDefault(emptyList())

    private fun mikaiAnimeId(aniListMediaId: Int): Int? {
        synchronized(animeIdCache) {
            if (animeIdCache.containsKey(aniListMediaId)) return animeIdCache[aniListMediaId]
        }
        val resolved = runCatching {
            get("$BASE_URL/anime/al:$aniListMediaId")
                .getJSONObject("result")
                .getJSONObject("ids")
                .getInt("mikai")
        }.getOrNull()
        synchronized(animeIdCache) { animeIdCache[aniListMediaId] = resolved }
        return resolved
    }

    private fun JSONObject.hasAniListId(expectedId: Int): Boolean {
        val ids = optJSONObject("externalIds")?.optJSONArray("anilist") ?: return false
        repeat(ids.length()) { index ->
            if (ids.optString(index).toIntOrNull() == expectedId) return true
        }
        return false
    }

    private fun String.parseBirthday(): Pair<Int, Int>? {
        val match = BIRTHDAY_PATTERN.matchEntire(this) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        return try {
            LocalDate.of(LEAP_YEAR, month, day)
            month to day
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun get(url: String): JSONObject {
        var retryCount = 0
        while (true) {
            throttle()
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val status = connection.responseCode
            if (status == 429 && retryCount < MAX_RETRIES) {
                val waitSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull()
                    ?.coerceIn(1L, MAX_WAIT_SECONDS)
                    ?: (5L * (retryCount + 1)).coerceAtMost(MAX_WAIT_SECONDS)
                connection.disconnect()
                Thread.sleep(waitSeconds * 1_000L)
                retryCount++
                continue
            }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (status !in 200..299) throw IOException("Mikai isteği başarısız oldu ($status).")
            return JSONObject(responseText)
        }
    }

    private fun throttle() {
        synchronized(rateLock) {
            val waitMillis = (lastRequestAt + REQUEST_INTERVAL_MILLIS - System.currentTimeMillis())
                .coerceAtLeast(0L)
            if (waitMillis > 0) Thread.sleep(waitMillis)
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private companion object {
        const val BASE_URL = "https://api.mikai.me/public/v1"
        const val SEARCH_LIMIT = 5
        const val DETAIL_CANDIDATE_LIMIT = 5
        const val MEDIA_CONTEXT_LIMIT = 3
        const val TIMEOUT_MILLIS = 30_000
        const val REQUEST_INTERVAL_MILLIS = 1_100L
        const val MAX_RETRIES = 5
        const val MAX_WAIT_SECONDS = 60L
        const val LEAP_YEAR = 2024
        const val USER_AGENT = "CAnime/1.0 (Android)"
        val BIRTHDAY_PATTERN = Regex("(?:\\d{4}|--)-(\\d{2})-(\\d{2})")
        val rateLock = Any()
        var lastRequestAt = 0L
        val animeIdCache = mutableMapOf<Int, Int?>()
    }
}
