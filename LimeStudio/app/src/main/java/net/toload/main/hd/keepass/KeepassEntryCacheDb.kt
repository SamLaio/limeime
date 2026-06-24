package net.toload.main.hd.keepass

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class KeepassEntryCacheDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, databaseVersion) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $tableMeta (
                $columnCacheKey TEXT PRIMARY KEY,
                $columnUpdatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $tableEntries (
                $columnCacheKey TEXT NOT NULL,
                $columnPosition INTEGER NOT NULL,
                $columnEntryId TEXT NOT NULL,
                $columnTitle TEXT NOT NULL,
                $columnUsername TEXT NOT NULL,
                $columnPassword TEXT NOT NULL,
                $columnUrl TEXT NOT NULL,
                $columnNotes TEXT NOT NULL,
                $columnAdditionalUrls TEXT NOT NULL,
                $columnExtraSearchValues TEXT NOT NULL,
                $columnImeFields TEXT NOT NULL,
                PRIMARY KEY ($columnCacheKey, $columnEntryId)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_keepass_entry_cache_key ON $tableEntries($columnCacheKey, $columnPosition)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $tableEntries")
        db.execSQL("DROP TABLE IF EXISTS $tableMeta")
        onCreate(db)
    }

    fun read(cacheKey: String): List<KeepassEntry>? {
        val db = readableDatabase
        if (!hasCache(db, cacheKey)) {
            return null
        }
        val entries = mutableListOf<KeepassEntry>()
        db.query(
            tableEntries,
            arrayOf(
                columnEntryId,
                columnTitle,
                columnUsername,
                columnPassword,
                columnUrl,
                columnNotes,
                columnAdditionalUrls,
                columnExtraSearchValues,
                columnImeFields,
            ),
            "$columnCacheKey = ?",
            arrayOf(cacheKey),
            null,
            null,
            "$columnPosition ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = runCatching { UUID.fromString(cursor.getString(0)) }.getOrNull() ?: continue
                entries.add(
                    KeepassEntry(
                        id = id,
                        title = cursor.getString(1),
                        username = cursor.getString(2),
                        password = "",
                        url = cursor.getString(4),
                        notes = cursor.getString(5),
                        additionalUrls = cursor.getString(6).jsonArrayToList(),
                        extraSearchValues = cursor.getString(7).jsonArrayToList(),
                        imeFields = cursor.getString(8).jsonArrayToImeFields(),
                        encryptedPassword = cursor.getString(3),
                    ),
                )
            }
        }
        return entries
    }

    fun write(cacheKey: String, entries: List<KeepassEntry>, crypto: KeepassCacheCrypto) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            deleteCache(db, cacheKey)
            db.insertWithOnConflict(
                tableMeta,
                null,
                ContentValues().apply {
                    put(columnCacheKey, cacheKey)
                    put(columnUpdatedAt, System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            entries.forEachIndexed { index, entry ->
                db.insertWithOnConflict(
                    tableEntries,
                    null,
                    ContentValues().apply {
                        put(columnCacheKey, cacheKey)
                        put(columnPosition, index)
                        put(columnEntryId, entry.id.toString())
                        put(columnTitle, entry.title)
                        put(columnUsername, entry.username)
                        put(columnPassword, crypto.encrypt(entry.password))
                        put(columnUrl, entry.url)
                        put(columnNotes, entry.notes)
                        put(columnAdditionalUrls, entry.additionalUrls.toJsonArrayString())
                        put(columnExtraSearchValues, entry.extraSearchValues.toJsonArrayString())
                        put(columnImeFields, entry.imeFields.toEncryptedJsonArrayString(crypto))
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun invalidate(cacheKey: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            deleteCache(db, cacheKey)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(tableEntries, null, null)
            db.delete(tableMeta, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun hasCache(db: SQLiteDatabase, cacheKey: String): Boolean {
        db.query(
            tableMeta,
            arrayOf(columnCacheKey),
            "$columnCacheKey = ?",
            arrayOf(cacheKey),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun deleteCache(db: SQLiteDatabase, cacheKey: String) {
        db.delete(tableEntries, "$columnCacheKey = ?", arrayOf(cacheKey))
        db.delete(tableMeta, "$columnCacheKey = ?", arrayOf(cacheKey))
    }

    private fun List<String>.toJsonArrayString(): String {
        val array = JSONArray()
        forEach { value -> array.put(value) }
        return array.toString()
    }

    private fun String.jsonArrayToList(): List<String> {
        return runCatching {
            val array = JSONArray(this)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { value -> value.isNotBlank() }?.let { value -> add(value) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun List<KeepassImeField>.toEncryptedJsonArrayString(crypto: KeepassCacheCrypto): String {
        val array = JSONArray()
        forEach { field ->
            if (field.label.isNotBlank() && field.value.isNotBlank()) {
                array.put(
                    JSONObject()
                        .put("label", field.label)
                        .put("value", crypto.encrypt(field.value)),
                )
            }
        }
        return array.toString()
    }

    private fun String.jsonArrayToImeFields(): List<KeepassImeField> {
        return runCatching {
            val array = JSONArray(this)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val label = item.optString("label").takeIf { value -> value.isNotBlank() } ?: continue
                    val encryptedValue = item.optString("value").takeIf { value -> value.isNotBlank() } ?: continue
                    add(KeepassImeField(label = label, encryptedValue = encryptedValue))
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        private const val databaseName = "keepass_entry_cache.db"
        private const val databaseVersion = 3
        private const val tableMeta = "cache_meta"
        private const val tableEntries = "entries"
        private const val columnCacheKey = "cache_key"
        private const val columnUpdatedAt = "updated_at"
        private const val columnPosition = "position"
        private const val columnEntryId = "entry_id"
        private const val columnTitle = "title"
        private const val columnUsername = "username"
        private const val columnPassword = "password"
        private const val columnUrl = "url"
        private const val columnNotes = "notes"
        private const val columnAdditionalUrls = "additional_urls"
        private const val columnExtraSearchValues = "extra_search_values"
        private const val columnImeFields = "ime_fields"
    }
}
