package com.marineus.canime.feature.calendar.data

import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class AniListClient {
    fun fetchBirthdayCharacters(username: String): CharacterFetchResult {
        val mediaIds = fetchMediaIds(username)
        if (mediaIds.isEmpty()) return CharacterFetchResult(emptyList(), emptyList())

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
            val month = character.birthMonth.takeIf { it in 1..12 } ?: return@mapNotNull null
            val day = character.birthDay.takeIf { it in 1..31 } ?: return@mapNotNull null
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
            .filter { it.birthMonth !in 1..12 || it.birthDay !in 1..31 }
            .map { character ->
                MissingBirthdayCharacter(
                    id = character.id,
                    name = character.name,
                    imageUrl = character.imageUrl,
                    siteUrl = character.siteUrl,
                    mediaTitles = character.mediaTitles.sorted(),
                )
            }
        return CharacterFetchResult(birthdays, missingBirthdays)
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
        val mediaTitle = media.getJSONObject("title").optString("userPreferred")
        val characterConnection = media.getJSONObject("characters")
        val nodes = characterConnection.getJSONArray("nodes")
        repeat(nodes.length()) { nodeIndex ->
            val node = nodes.getJSONObject(nodeIndex)
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
                )
            }
            if (mediaTitle.isNotBlank()) character.mediaTitles += mediaTitle
        }
        return characterConnection.getJSONObject("pageInfo").optBoolean("hasNextPage")
    }

    private fun execute(query: String, variables: JSONObject): JSONObject {
        var retryCount = 0
        while (true) {
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
            if (status == 429 && retryCount < MAX_RETRIES) {
                val waitSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 2L
                connection.disconnect()
                Thread.sleep(waitSeconds.coerceAtMost(30L) * 1_000L)
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
                throw IOException(errorMessage(responseText, "AniList verisi alınamadı."))
            }
            return response
        }
    }

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
        val mediaTitles: MutableSet<String> = linkedSetOf(),
    )

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"
        const val MEDIA_BATCH_SIZE = 50
        const val TIMEOUT_MILLIS = 30_000
        const val MAX_RETRIES = 2
        const val DOLLAR = '$'

        val MEDIA_CHARACTERS_QUERY = """
            query (${DOLLAR}mediaIds: [Int], ${DOLLAR}characterPage: Int) {
              Page(page: 1, perPage: 50) {
                media(id_in: ${DOLLAR}mediaIds) {
                  title { userPreferred }
                  characters(page: ${DOLLAR}characterPage, perPage: 25, sort: [ROLE, RELEVANCE, ID]) {
                    pageInfo { hasNextPage }
                    nodes {
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
        """.trimIndent()
    }
}
