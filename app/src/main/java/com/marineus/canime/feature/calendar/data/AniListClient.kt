package com.marineus.canime.feature.calendar.data

import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DateTimeException
import java.time.LocalDate

internal class AniListClient {
    fun fetchGlobalBirthdayCharacters(
        onProgress: (scannedCharacters: Int) -> Unit,
    ): List<BirthdayCharacter> {
        val characters = linkedMapOf<Int, BirthdayCharacter>()
        var scannedCharacters = 0
        GLOBAL_CHARACTER_SORTS.forEach { sort ->
            val firstWindowIds = mutableListOf<Int>()
            repeat(WINDOWS_PER_SORT) { windowIndex ->
                val excludedIds = if (windowIndex == 0) emptyList() else firstWindowIds
            var page = 1
            do {
                val variables = JSONObject()
                    .put("page", page)
                    .put("sort", JSONArray().put(sort))
                    .put("excludedIds", JSONArray(excludedIds))
                val response = execute(GLOBAL_CHARACTERS_QUERY, variables)
                val items = response.getJSONObject("data").getJSONObject("Page").getJSONArray("characters")
                repeat(items.length()) { index ->
                    val node = items.getJSONObject(index)
                    if (windowIndex == 0) firstWindowIds += node.getInt("id")
                    val date = node.optJSONObject("dateOfBirth")
                    val month = date?.optInt("month") ?: 0
                    val day = date?.optInt("day") ?: 0
                    if (isValidMonthDay(month, day)) {
                        characters[node.getInt("id")] = BirthdayCharacter(
                            id = node.getInt("id"),
                            name = node.getJSONObject("name").optString("full", "Bilinmeyen karakter"),
                            imageUrl = node.optJSONObject("image")?.optString("large")?.takeIf(String::isNotBlank),
                            birthMonth = month,
                            birthDay = day,
                            siteUrl = node.optString("siteUrl").takeIf(String::isNotBlank),
                            mediaTitles = emptyList(),
                        )
                    }
                }
                scannedCharacters += items.length()
                onProgress(scannedCharacters)
                page++
            } while (items.length() == GLOBAL_CHARACTER_BATCH_SIZE && page <= MAX_GLOBAL_CHARACTER_PAGE)
            }
        }
        return characters.values.toList()
    }

    fun fetchBirthdayCharacters(username: String): CharacterFetchResult =
        fetchCharacters(fetchMediaIds(username))

    fun fetchNewBirthdayCharacters(username: String, knownMediaIds: Set<Int>): CharacterFetchResult {
        val mediaIds = fetchNewMediaIds(username, knownMediaIds)
        return fetchCharacters(mediaIds)
    }

    private fun fetchCharacters(mediaIds: List<Int>): CharacterFetchResult {
        if (mediaIds.isEmpty()) return CharacterFetchResult(emptyList(), emptyList(), emptySet())

        val characters = linkedMapOf<Int, MutableCharacter>()
        mediaIds.distinct().chunked(MEDIA_BATCH_SIZE).forEach { batch ->
            var characterPage = 1
            do {
                var hasNextPage = false
                val variables = JSONObject()
                    .put("mediaIds", JSONArray(batch))
                    .put("characterPage", characterPage)
                val response = execute(MEDIA_CHARACTERS_QUERY, variables)
                val mediaItems = response.getJSONObject("data").getJSONObject("Page").getJSONArray("media")
                repeat(mediaItems.length()) { mediaIndex ->
                    hasNextPage = addMediaCharacters(mediaItems.getJSONObject(mediaIndex), characters) || hasNextPage
                }
                characterPage++
            } while (hasNextPage)
        }

        val birthdays = characters.values.mapNotNull { character ->
            if (!isValidMonthDay(character.birthMonth, character.birthDay)) return@mapNotNull null
            val month = character.birthMonth
            val day = character.birthDay
            BirthdayCharacter(
                id = character.id,
                name = character.name,
                imageUrl = character.imageUrl,
                birthMonth = month,
                birthDay = day,
                siteUrl = character.siteUrl,
                mediaTitles = character.mediaTitles.sorted(),
            )
        }
        val missingBirthdays = characters.values
            .filterNot { isValidMonthDay(it.birthMonth, it.birthDay) }
            .map { character ->
                MissingBirthdayCharacter(
                    id = character.id,
                    name = character.name,
                    imageUrl = character.imageUrl,
                    siteUrl = character.siteUrl,
                    mediaTitles = character.mediaTitles.sorted(),
                    mediaIds = character.mediaIds.sorted(),
                    role = character.role,
                )
            }
        return CharacterFetchResult(birthdays, missingBirthdays, mediaIds.toSet())
    }

    private fun fetchNewMediaIds(username: String, knownMediaIds: Set<Int>): List<Int> {
        val newIds = mutableListOf<Int>()
        var page = 1
        var reachedKnownEntry = false
        do {
            val variables = JSONObject()
                .put("userName", username)
                .put("page", page)
            val response = execute(RECENT_MEDIA_QUERY, variables)
            val pageData = response.getJSONObject("data").getJSONObject("Page")
            val entries = pageData.getJSONArray("mediaList")
            repeat(entries.length()) { index ->
                val mediaId = entries.getJSONObject(index).getInt("mediaId")
                if (mediaId in knownMediaIds) reachedKnownEntry = true else if (!reachedKnownEntry) newIds += mediaId
            }
            val hasNextPage = pageData.getJSONObject("pageInfo").optBoolean("hasNextPage")
            page++
        } while (!reachedKnownEntry && hasNextPage)
        return newIds.distinct()
    }

    private fun fetchMediaIds(username: String): List<Int> {
        val query = """
            query (${DOLLAR}userName: String) {
              anime: MediaListCollection(
                userName: ${DOLLAR}userName
                type: ANIME
                status: COMPLETED
              ) {
                lists { entries { mediaId } }
              }
              manga: MediaListCollection(
                userName: ${DOLLAR}userName
                type: MANGA
                status: COMPLETED
              ) {
                lists { entries { mediaId } }
              }
            }
        """.trimIndent()
        val response = execute(query, JSONObject().put("userName", username))
        val data = response.getJSONObject("data")
        return buildList {
            addMediaIds(data.optJSONObject("anime"))
            addMediaIds(data.optJSONObject("manga"))
        }
    }

    private fun MutableList<Int>.addMediaIds(collection: JSONObject?) {
        val lists = collection?.optJSONArray("lists") ?: return
        repeat(lists.length()) { listIndex ->
            val entries = lists.getJSONObject(listIndex).optJSONArray("entries") ?: JSONArray()
            repeat(entries.length()) { entryIndex -> add(entries.getJSONObject(entryIndex).getInt("mediaId")) }
        }
    }

    private fun addMediaCharacters(
        media: JSONObject,
        characters: MutableMap<Int, MutableCharacter>,
    ): Boolean {
        val title = media.getJSONObject("title")
        val mediaTitles = TITLE_FIELDS.mapNotNull { field ->
            title.optString(field).takeIf(String::isNotBlank)
        }
        val mediaId = media.getInt("id")
        val characterConnection = media.getJSONObject("characters")
        val edges = characterConnection.getJSONArray("edges")
        repeat(edges.length()) { edgeIndex ->
            val edge = edges.getJSONObject(edgeIndex)
            val role = edge.optString("role")
            val node = edge.getJSONObject("node")
            val date = node.optJSONObject("dateOfBirth")
            val month = date?.optInt("month") ?: 0
            val day = date?.optInt("day") ?: 0

            val id = node.getInt("id")
            val character = characters.getOrPut(id) {
                MutableCharacter(
                    id = id,
                    name = node.getJSONObject("name").optString("full", "Bilinmeyen karakter"),
                    imageUrl = node.optJSONObject("image")?.optString("large")?.takeIf { it.isNotBlank() },
                    birthMonth = month,
                    birthDay = day,
                    siteUrl = node.optString("siteUrl").takeIf { it.isNotBlank() },
                    role = role,
                )
            }
            if (role == "MAIN") character.role = "MAIN"
            character.mediaTitles += mediaTitles
            character.mediaIds += mediaId
        }
        return characterConnection.getJSONObject("pageInfo").optBoolean("hasNextPage")
    }

    private fun isValidMonthDay(month: Int, day: Int): Boolean = try {
        LocalDate.of(LEAP_YEAR, month, day)
        true
    } catch (_: DateTimeException) {
        false
    }

    private fun execute(query: String, variables: JSONObject): JSONObject {
        var retryCount = 0
        while (true) {
            throttleRequests()
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val body = JSONObject().put("query", query).put("variables", variables).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            if (status == 429 && retryCount < MAX_RATE_LIMIT_RETRIES) {
                val waitSeconds = rateLimitWaitSeconds(connection, retryCount)
                connection.disconnect()
                Thread.sleep(waitSeconds * 1_000L)
                retryCount++
                continue
            }

            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (status !in 200..299) {
                throw IOException(errorMessage(responseText, "AniList isteği başarısız oldu ($status)."))
            }

            val response = JSONObject(responseText)
            if (response.has("errors")) {
                val message = errorMessage(responseText, "AniList verisi alınamadı.")
                if (message.contains("too many requests", ignoreCase = true) &&
                    retryCount < MAX_RATE_LIMIT_RETRIES
                ) {
                    Thread.sleep(fallbackWaitSeconds(retryCount) * 1_000L)
                    retryCount++
                    continue
                }
                throw IOException(message)
            }
            return response
        }
    }

    private fun throttleRequests() {
        synchronized(requestRateLock) {
            val now = System.currentTimeMillis()
            val waitMillis = (lastRequestAt + REQUEST_INTERVAL_MILLIS - now).coerceAtLeast(0L)
            if (waitMillis > 0L) Thread.sleep(waitMillis)
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private fun rateLimitWaitSeconds(connection: HttpURLConnection, retryCount: Int): Long {
        connection.getHeaderField("Retry-After")?.toLongOrNull()?.let {
            return it.coerceIn(1L, MAX_RATE_LIMIT_WAIT_SECONDS)
        }
        val resetAt = connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
        if (resetAt != null) {
            val seconds = resetAt - System.currentTimeMillis() / 1_000L + 1L
            if (seconds > 0L) return seconds.coerceAtMost(MAX_RATE_LIMIT_WAIT_SECONDS)
        }
        return fallbackWaitSeconds(retryCount)
    }

    private fun fallbackWaitSeconds(retryCount: Int): Long =
        (5L * (retryCount + 1)).coerceAtMost(MAX_RATE_LIMIT_WAIT_SECONDS)

    private fun errorMessage(responseText: String, fallback: String): String = runCatching {
        JSONObject(responseText).getJSONArray("errors").getJSONObject(0).getString("message")
    }.getOrDefault(fallback)

    private data class MutableCharacter(
        val id: Int,
        val name: String,
        val imageUrl: String?,
        val birthMonth: Int,
        val birthDay: Int,
        val siteUrl: String?,
        var role: String,
        val mediaTitles: MutableSet<String> = linkedSetOf(),
        val mediaIds: MutableSet<Int> = linkedSetOf(),
    )

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"
        const val MEDIA_BATCH_SIZE = 50
        const val TIMEOUT_MILLIS = 30_000
        const val MAX_RATE_LIMIT_RETRIES = 8
        const val MAX_RATE_LIMIT_WAIT_SECONDS = 60L
        const val REQUEST_INTERVAL_MILLIS = 2_100L
        const val DOLLAR = '$'
        const val LEAP_YEAR = 2024
        const val GLOBAL_CHARACTER_BATCH_SIZE = 50
        const val MAX_GLOBAL_CHARACTER_PAGE = 100
        const val WINDOWS_PER_SORT = 2
        val GLOBAL_CHARACTER_SORTS = listOf("ID", "ID_DESC")
        val TITLE_FIELDS = listOf("userPreferred", "romaji", "english", "native")
        val requestRateLock = Any()
        var lastRequestAt = 0L

        val RECENT_MEDIA_QUERY = """
            query (${DOLLAR}userName: String, ${DOLLAR}page: Int) {
              Page(page: ${DOLLAR}page, perPage: 10) {
                pageInfo { hasNextPage }
                mediaList(
                  userName: ${DOLLAR}userName
                  status: COMPLETED
                  sort: UPDATED_TIME_DESC
                ) { mediaId }
              }
            }
        """.trimIndent()

        val GLOBAL_CHARACTERS_QUERY = """
            query (
              ${DOLLAR}page: Int
              ${DOLLAR}sort: [CharacterSort]
              ${DOLLAR}excludedIds: [Int]
            ) {
              Page(page: ${DOLLAR}page, perPage: $GLOBAL_CHARACTER_BATCH_SIZE) {
                characters(sort: ${DOLLAR}sort, id_not_in: ${DOLLAR}excludedIds) {
                  id
                  name { full }
                  image { large }
                  dateOfBirth { month day }
                  siteUrl
                }
              }
            }
        """.trimIndent()

        val MEDIA_CHARACTERS_QUERY = """
            query (${DOLLAR}mediaIds: [Int], ${DOLLAR}characterPage: Int) {
              Page(page: 1, perPage: 50) {
                media(id_in: ${DOLLAR}mediaIds) {
                  id
                  title { userPreferred romaji english native }
                  characters(page: ${DOLLAR}characterPage, perPage: 25, sort: [ROLE, RELEVANCE, ID]) {
                    pageInfo { hasNextPage }
                    edges {
                      role
                      node {
                        id
                        name { full }
                        image { large }
                        dateOfBirth { month day }
                        siteUrl
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
