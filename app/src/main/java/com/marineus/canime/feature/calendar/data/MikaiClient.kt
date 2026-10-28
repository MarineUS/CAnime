package com.marineus.canime.feature.calendar.data

import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Resolves only public character data; AniList usernames are never sent to Mikai. */
internal class MikaiClient {
    fun resolveBirthday(character: MissingBirthdayCharacter): BirthdayCharacter? = runCatching {
        val query = URLEncoder.encode(character.name, Charsets.UTF_8.name())
        val search = get("$BASE_URL/characters?search=$query&limit=$CANDIDATE_LIMIT")
        val candidates = search.optJSONArray("result") ?: return null
        repeat(candidates.length()) { index ->
            val candidateId = candidates.getJSONObject(index).getInt("id")
            val detail = get("$BASE_URL/characters/$candidateId").getJSONObject("result")
            if (!detail.hasAniListId(character.id)) return@repeat
            val (month, day) = detail.optString("birthday").parseBirthday() ?: return@repeat
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
        return if (month in 1..12 && day in 1..31) month to day else null
    }

    private fun get(url: String): JSONObject {
        throttle()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) throw IOException("Mikai isteği başarısız oldu ($status).")
        return JSONObject(responseText)
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
        const val CANDIDATE_LIMIT = 5
        const val TIMEOUT_MILLIS = 30_000
        const val REQUEST_INTERVAL_MILLIS = 1_100L
        const val USER_AGENT = "CAnime/1.0 (Android)"
        val BIRTHDAY_PATTERN = Regex("(?:\\d{4}|--)-(\\d{2})-(\\d{2})")
        val rateLock = Any()
        var lastRequestAt = 0L
    }
}
