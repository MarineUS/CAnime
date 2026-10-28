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
                birthday_source TEXT NOT NULL DEFAULT 'ANILIST',
                confidence TEXT NOT NULL DEFAULT 'HIGH',
                verified_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (username, character_id)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX birthday_lookup ON birthday_character(username, birth_month, birth_day)",
        )
        database.execSQL(
            """
            CREATE TABLE unresolved_character (
                username TEXT NOT NULL,
                character_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                image_url TEXT,
                site_url TEXT,
                media_titles TEXT NOT NULL,
                last_checked_at INTEGER,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (username, character_id)
            )
            """.trimIndent(),
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
        if (oldVersion < 2) {
            database.execSQL(
                "ALTER TABLE birthday_character ADD COLUMN birthday_source TEXT NOT NULL DEFAULT 'ANILIST'",
            )
            database.execSQL(
                "ALTER TABLE birthday_character ADD COLUMN confidence TEXT NOT NULL DEFAULT 'HIGH'",
            )
            database.execSQL(
                "ALTER TABLE birthday_character ADD COLUMN verified_at INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS unresolved_character (
                    username TEXT NOT NULL,
                    character_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    image_url TEXT,
                    site_url TEXT,
                    media_titles TEXT NOT NULL,
                    last_checked_at INTEGER,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (username, character_id)
                )
                """.trimIndent(),
            )
        }
    }

    fun replaceUserCharacters(
        username: String,
        characters: List<BirthdayCharacter>,
        mikaiCharacterIds: Set<Int> = emptySet(),
    ) {
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
                        put("birthday_source", if (character.id in mikaiCharacterIds) "MIKAI" else "ANILIST")
                        put("confidence", "HIGH")
                        put("verified_at", System.currentTimeMillis())
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

    fun getMikaiCharacters(username: String): List<BirthdayCharacter> = queryCharacters(
        selection = "username = ? AND birthday_source = ?",
        selectionArgs = arrayOf(username, "MIKAI"),
    )

    fun upsertUnresolved(username: String, characters: List<MissingBirthdayCharacter>) {
        writableDatabase.inTransaction {
            characters.forEach { character ->
                insertWithOnConflict(
                    "unresolved_character",
                    null,
                    ContentValues().apply {
                        put("username", username)
                        put("character_id", character.id)
                        put("name", character.name)
                        put("image_url", character.imageUrl)
                        put("site_url", character.siteUrl)
                        put("media_titles", JSONArray(character.mediaTitles).toString())
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        }
    }

    fun unresolvedDue(username: String, limit: Int, retryBefore: Long): List<MissingBirthdayCharacter> {
        readableDatabase.query(
            "unresolved_character",
            arrayOf("character_id", "name", "image_url", "site_url", "media_titles"),
            "username = ? AND (last_checked_at IS NULL OR last_checked_at < ?)",
            arrayOf(username, retryBefore.toString()),
            null,
            null,
            "attempt_count ASC, character_id ASC",
            limit.toString(),
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) {
                    val titles = JSONArray(cursor.getString(4))
                    add(
                        MissingBirthdayCharacter(
                            id = cursor.getInt(0),
                            name = cursor.getString(1),
                            imageUrl = cursor.getString(2),
                            siteUrl = cursor.getString(3),
                            mediaTitles = buildList {
                                repeat(titles.length()) { index -> add(titles.getString(index)) }
                            },
                        ),
                    )
                }
            }
        }
    }

    fun markFallbackChecked(username: String, characterId: Int, resolved: Boolean) {
        if (resolved) {
            writableDatabase.delete(
                "unresolved_character",
                "username = ? AND character_id = ?",
                arrayOf(username, characterId.toString()),
            )
        } else {
            writableDatabase.execSQL(
                """
                UPDATE unresolved_character
                SET last_checked_at = ?, attempt_count = attempt_count + 1
                WHERE username = ? AND character_id = ?
                """.trimIndent(),
                arrayOf<Any?>(System.currentTimeMillis(), username, characterId),
            )
        }
    }

    fun getCharactersForMonth(username: String, month: Int): List<BirthdayCharacter> {
        return queryCharacters(
            selection = "username = ? AND birth_month = ?",
            selectionArgs = arrayOf(username, month.toString()),
        )
    }

    private fun queryCharacters(selection: String, selectionArgs: Array<String>): List<BirthdayCharacter> {
        readableDatabase.query(
            "birthday_character", COLUMNS, selection, selectionArgs, null, null,
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
        const val DATABASE_VERSION = 2
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
