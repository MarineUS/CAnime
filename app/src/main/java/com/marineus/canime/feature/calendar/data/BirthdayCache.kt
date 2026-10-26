package com.marineus.canime.feature.calendar.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import org.json.JSONArray

internal class BirthdayCache(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE birthday_character (
                username TEXT NOT NULL,
                character_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                image_url TEXT,
                birth_month INTEGER NOT NULL,
                birth_day INTEGER NOT NULL,
                site_url TEXT,
                media_titles TEXT NOT NULL,
                PRIMARY KEY (username, character_id)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX birthday_lookup ON birthday_character(username, birth_month, birth_day)",
        )
        database.execSQL(
            """
            CREATE TABLE sync_metadata (
                username TEXT PRIMARY KEY NOT NULL,
                synced_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        database.execSQL("DROP TABLE IF EXISTS birthday_character")
        database.execSQL("DROP TABLE IF EXISTS sync_metadata")
        onCreate(database)
    }

    fun replaceUserCharacters(username: String, characters: List<BirthdayCharacter>) {
        writableDatabase.inTransaction {
            delete("birthday_character", "username = ?", arrayOf(username))
            characters.forEach { character ->
                insertOrThrow(
                    "birthday_character",
                    null,
                    ContentValues().apply {
                        put("username", username)
                        put("character_id", character.id)
                        put("name", character.name)
                        put("image_url", character.imageUrl)
                        put("birth_month", character.birthMonth)
                        put("birth_day", character.birthDay)
                        put("site_url", character.siteUrl)
                        put("media_titles", JSONArray(character.mediaTitles).toString())
                    },
                )
            }
            insertWithOnConflict(
                "sync_metadata",
                null,
                ContentValues().apply {
                    put("username", username)
                    put("synced_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    fun getCharactersForMonth(username: String, month: Int): List<BirthdayCharacter> {
        readableDatabase.query(
            "birthday_character",
            COLUMNS,
            "username = ? AND birth_month = ?",
            arrayOf(username, month.toString()),
            null,
            null,
            "birth_day ASC, name COLLATE NOCASE ASC",
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) {
                    val titlesJson = JSONArray(cursor.getString(cursor.getColumnIndexOrThrow("media_titles")))
                    add(
                        BirthdayCharacter(
                            id = cursor.getInt(cursor.getColumnIndexOrThrow("character_id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url")),
                            birthMonth = cursor.getInt(cursor.getColumnIndexOrThrow("birth_month")),
                            birthDay = cursor.getInt(cursor.getColumnIndexOrThrow("birth_day")),
                            siteUrl = cursor.getString(cursor.getColumnIndexOrThrow("site_url")),
                            mediaTitles = buildList {
                                repeat(titlesJson.length()) { index -> add(titlesJson.getString(index)) }
                            },
                        ),
                    )
                }
            }
        }
    }

    fun hasCache(username: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM sync_metadata WHERE username = ? LIMIT 1",
        arrayOf(username),
    ).use { it.moveToFirst() }

    private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private companion object {
        const val DATABASE_NAME = "birthday_cache.db"
        const val DATABASE_VERSION = 1
        val COLUMNS = arrayOf(
            "character_id",
            "name",
            "image_url",
            "birth_month",
            "birth_day",
            "site_url",
            "media_titles",
        )
    }
}
