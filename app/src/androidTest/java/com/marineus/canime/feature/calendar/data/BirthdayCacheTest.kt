package com.marineus.canime.feature.calendar.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BirthdayCacheTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var cache: BirthdayCache

    @Before
    fun setUp() {
        context.deleteDatabase("birthday_cache.db")
        cache = BirthdayCache(context)
    }

    @After
    fun tearDown() {
        cache.close()
        context.deleteDatabase("birthday_cache.db")
    }

    @Test
    fun cachedCharactersCanBeReadWithoutNetworkByMonth() {
        val character = BirthdayCharacter(
            id = 1,
            name = "Test Character",
            imageUrl = "https://example.com/character.jpg",
            birthMonth = 10,
            birthDay = 26,
            siteUrl = "https://anilist.co/character/1",
            mediaTitles = listOf("Test Anime"),
        )

        cache.replaceUserCharacters("test-user", listOf(character))

        assertTrue(cache.hasCache("test-user"))
        assertEquals(listOf(character), cache.getCharactersForMonth("test-user", 10))
        assertTrue(cache.getCharactersForMonth("test-user", 11).isEmpty())
    }
}
