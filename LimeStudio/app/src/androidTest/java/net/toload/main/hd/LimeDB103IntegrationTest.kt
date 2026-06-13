@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.global.LIME
import net.toload.main.hd.limedb.LimeDB
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
open class LimeDB103IntegrationTest {
    private lateinit var appContext: Context
    private lateinit var appDb: File
    private lateinit var appDbWal: File
    private lateinit var appDbShm: File
    private lateinit var appDbJournal: File
    private lateinit var originalDbBackup: File
    private var hadOriginalDb: Boolean = false
    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext()
        appDb = appContext.getDatabasePath(LIME.DATABASE_NAME)
        appDbWal = File((appDb.getPath() + "-wal"))
        appDbShm = File((appDb.getPath() + "-shm"))
        appDbJournal = appContext.getDatabasePath(LIME.DATABASE_JOURNAL)
        closeCurrentDatabase()
        hadOriginalDb = appDb.exists()
        if (hadOriginalDb) {
            originalDbBackup = File(appContext.getCacheDir(), "lime_db_103_original_backup.db")
            copyFile(appDb, originalDbBackup)
        }
        deleteAppDatabaseFiles()
    }
    @After
    fun tearDown() {
        closeCurrentDatabase()
        deleteAppDatabaseFiles()
        if (((hadOriginalDb && (originalDbBackup != null)) && originalDbBackup.exists())) {
            copyFile(originalDbBackup, appDb)
            originalDbBackup.delete()
        }
    }
    @Test
    fun freshInstallCopies103SeedAndRefreshesEmojiData() {
        var db: LimeDB = LimeDB(appContext)
        db.close()
        assertEquals(104, queryUserVersion())
        assertTrue("bundled lime.db must keep core IM rows", (queryInt("SELECT COUNT(*) FROM im WHERE title = ?", "name") > 0))
        assertCj4SchemaExists()
        assertEmojiSchemaExists()
        assertEmojiDataLoaded()
    }
    @Test
    fun openingVersion102DatabaseAddsEmojiSchemaAndData() {
        replaceAppDatabaseWith(createSeedVariant("lime_102_no_emoji.db", 102, true, false))
        var db: LimeDB = LimeDB(appContext)
        db.close()
        assertEquals(104, queryUserVersion())
        assertCj4SchemaExists()
        assertEmojiSchemaExists()
        assertEmojiDataLoaded()
    }
    @Test
    fun openingVersion103DatabaseRepairsMissingEmojiSchema() {
        replaceAppDatabaseWith(createSeedVariant("lime_103_no_emoji.db", 103, true, false))
        var db: LimeDB = LimeDB(appContext)
        db.close()
        assertEquals(104, queryUserVersion())
        assertCj4SchemaExists()
        assertEmojiSchemaExists()
        assertEmojiDataLoaded()
    }
    @Test
    fun openingDatabaseRemovesStaleCj4KeyboardRow() {
        var dbFile: File = createSeedVariant("lime_104_stale_cj4_keyboard.db", 104, false, false)
        var writable: SQLiteDatabase = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE)
        try {
            writable.execSQL(((("INSERT OR REPLACE INTO keyboard " + "(code, name, desc, type, image, imkb, imshiftkb, engkb, engshiftkb, ") + "symbolkb, symbolshiftkb, defaultkb, defaultshiftkb, extendedkb, extendedshiftkb, disable) ") + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"), arrayOf(LIME.DB_TABLE_CJ4, "四碼倉頡", "四碼倉頡輸入法鍵盤", "phone", "cj_keyboard_preview", "lime_cj", "lime_cj_shift", "lime", "lime_shift", "symbols", "symbols_shift", "", "", "lime_cj_number", "lime_cj_number_shift", "false"))
        } finally {
            writable.close()
        }
        replaceAppDatabaseWith(dbFile)
        var db: LimeDB = LimeDB(appContext)
        db.ensureCurrentDatabase()
        db.close()
        assertCj4SchemaExists()
    }
    @Test
    fun emojiRefreshPreservesValidUserUsageAndPrunesInvalidUsage() {
        var db: LimeDB = LimeDB(appContext)
        db.close()
        var emoji: String = queryString("SELECT value FROM emoji_data LIMIT 1")
        var writable: SQLiteDatabase = SQLiteDatabase.openDatabase(appDb.getPath(), null, SQLiteDatabase.OPEN_READWRITE)
        try {
            writable.execSQL("UPDATE im SET desc = ? WHERE code = ?", arrayOf("0.0", "emoji"))
            writable.execSQL("INSERT OR REPLACE INTO emoji_user(value, use_count, last_used) VALUES(?, ?, ?)", arrayOf<Any>(emoji, 7, 1000))
            writable.execSQL("INSERT OR REPLACE INTO emoji_user(value, use_count, last_used) VALUES(?, ?, ?)", arrayOf<Any>("not-an-emoji", 3, 1000))
        } finally {
            writable.close()
        }
        var reopened: LimeDB = LimeDB(appContext)
        reopened.close()
        assertEquals(7, queryInt("SELECT use_count FROM emoji_user WHERE value = ?", emoji))
        assertEquals(0, queryInt("SELECT COUNT(*) FROM emoji_user WHERE value = ?", "not-an-emoji"))
        assertEmojiDataLoaded()
    }
    @Test
    fun restoreOldBackupRunsUpgradeRepairAndEmojiRefresh() {
        var oldDb: File = createSeedVariant("lime_restore_102_no_emoji.db", 102, true, false)
        var restoreZip: File = File(appContext.getCacheDir(), "lime_restore_102_no_emoji.zip")
        createDatabaseRestoreZip(oldDb, restoreZip)
        DBServer.getInstance(appContext)!!.restoreDatabase(restoreZip.getPath())
        assertEquals(104, queryUserVersion())
        assertCj4SchemaExists()
        assertEmojiSchemaExists()
        assertEmojiDataLoaded()
    }
    @Test
    fun restoreVersion101BackupRunsFullUpgradeAndEmojiRefresh() {
        var oldDb: File = createSeedVariant("lime_restore_101_no_emoji.db", 101, true, false)
        var restoreZip: File = File(appContext.getCacheDir(), "lime_restore_101_no_emoji.zip")
        createDatabaseRestoreZip(oldDb, restoreZip)
        DBServer.getInstance(appContext)!!.restoreDatabase(restoreZip.getPath())
        assertEquals(104, queryUserVersion())
        assertCj4SchemaExists()
        assertEmojiSchemaExists()
        assertEmojiDataLoaded()
    }
    @Test
    fun restoreOldBackupWithStaleEmojiFtsSchemaRecreatesEmojiFts() {
        var oldDb: File = createLegacyDbWithStaleEmojiFtsSchema("lime_restore_102_stale_emoji_fts.db", 102)
        var restoreZip: File = File(appContext.getCacheDir(), "lime_restore_102_stale_emoji_fts.zip")
        createDatabaseRestoreZip(oldDb, restoreZip)
        DBServer.getInstance(appContext)!!.restoreDatabase(restoreZip.getPath())
        assertEquals(104, queryUserVersion())
        assertCj4SchemaExists()
        assertEmojiSchemaExists()
        assertEmojiDataLoaded()
        var emojiFtsSql: String = queryString("SELECT sql FROM sqlite_master WHERE name = ?", "emoji_fts").lowercase()
        assertTrue("emoji_fts should be recreated as Android platform FTS4", emojiFtsSql.contains("using fts4"))
    }
    @Test
    fun restoreBareLimeDbBackupMovesDatabaseIntoAndroidDatabaseFolder() {
        var oldDb: File = createSeedVariant("lime_restore_bare_102_no_emoji.db", 102, true, false)
        var restoreZip: File = File(appContext.getCacheDir(), "lime_restore_bare_102_no_emoji.zip")
        createDatabaseRestoreZip(oldDb, restoreZip, LIME.DATABASE_NAME)
        DBServer.getInstance(appContext)!!.restoreDatabase(restoreZip.getPath())
        assertTrue("restored DB should exist in Android databases folder", appDb.exists())
        assertEquals(104, queryUserVersion())
        assertCj4SchemaExists()
        assertEmojiSchemaExists()
        assertEmojiDataLoaded()
    }
    @Test
    fun factoryResetRestores103SeedAndEmojiData() {
        replaceAppDatabaseWith(createSeedVariant("lime_factory_103_no_emoji.db", 103, true, false))
        var db: LimeDB = LimeDB(appContext)
        db.restoredToDefault()
        db.close()
        assertEquals(104, queryUserVersion())
        assertTrue("factory reset must restore core IM rows", (queryInt("SELECT COUNT(*) FROM im WHERE title = ?", "name") > 0))
        assertCj4SchemaExists()
        assertEmojiSchemaExists()
        assertEmojiDataLoaded()
    }
    @Test
    fun secondOpenDoesNotDuplicateEmojiImRows() {
        var db: LimeDB = LimeDB(appContext)
        db.close()
        var emojiImRowsAfterFirstOpen: Int = queryInt("SELECT COUNT(*) FROM im WHERE code = ?", "emoji")
        var emojiDataRowsAfterFirstOpen: Int = queryInt("SELECT COUNT(*) FROM emoji_data")
        var reopened: LimeDB = LimeDB(appContext)
        reopened.close()
        assertEquals(emojiImRowsAfterFirstOpen, queryInt("SELECT COUNT(*) FROM im WHERE code = ?", "emoji"))
        assertEquals(emojiDataRowsAfterFirstOpen, queryInt("SELECT COUNT(*) FROM emoji_data"))
    }
    private fun assertEmojiSchemaExists() {
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE name = ?", "emoji_data"))
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE name = ?", "emoji_user"))
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE name = ?", "emoji_fts"))
    }
    private fun assertEmojiDataLoaded() {
        assertTrue("emoji.db payload must be copied into lime.db", (queryInt("SELECT COUNT(*) FROM emoji_data") > 0))
        assertTrue("emoji IM rows must be rebuilt from emoji data", (queryInt("SELECT COUNT(*) FROM im WHERE code = ?", "emoji") > 0))
    }
    private fun assertCj4SchemaExists() {
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?", LIME.DB_TABLE_CJ4))
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?", "cj4_idx_code"))
        assertEquals(0, queryInt("SELECT COUNT(*) FROM keyboard WHERE code = ?", LIME.DB_TABLE_CJ4))
        assertEquals(1, queryInt("SELECT COUNT(*) FROM keyboard WHERE code = ? AND imkb = ?", LIME.DB_TABLE_CJ, "lime_cj"))
    }
    private fun createSeedVariant(name: String, userVersion: Int, dropEmojiSchema: Boolean, forceOldEmojiVersion: Boolean): File {
        var dbFile: File = File(appContext.getFilesDir(), name)
        copyRawResourceToFile(R.raw.lime, dbFile)
        var db: SQLiteDatabase = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE)
        try {
            if (dropEmojiSchema) {
                dropEmojiFtsSchemaRowsForFixture(db)
                db.execSQL("DROP TABLE IF EXISTS emoji_user")
                db.execSQL("DROP TABLE IF EXISTS emoji_data")
                db.execSQL("DELETE FROM im WHERE code = ?", arrayOf("emoji"))
            }
            if (forceOldEmojiVersion) {
                db.execSQL("UPDATE im SET desc = ? WHERE code = ?", arrayOf("0.0", "emoji"))
            }
            db.setVersion(userVersion)
        } finally {
            db.close()
        }
        return dbFile
    }
    private fun createLegacyDbWithStaleEmojiFtsSchema(name: String, userVersion: Int): File {
        var dbFile: File = File(appContext.getFilesDir(), name)
        if (dbFile.exists()) {
            dbFile.delete()
        }
        var db: SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL(((("CREATE TABLE im (" + "_id INTEGER PRIMARY KEY AUTOINCREMENT, ") + "code TEXT, title TEXT, desc TEXT, keyboard TEXT, disable TEXT, ") + "selkey TEXT, endkey TEXT, spacestyle TEXT)"))
            db.execSQL(((((("CREATE TABLE keyboard (" + "_id INTEGER PRIMARY KEY AUTOINCREMENT, ") + "code TEXT, name TEXT, desc TEXT, type TEXT, image TEXT, ") + "imkb TEXT, imshiftkb TEXT, engkb TEXT, engshiftkb TEXT, ") + "symbolkb TEXT, symbolshiftkb TEXT, defaultkb TEXT, defaultshiftkb TEXT, ") + "extendedkb TEXT, extendedshiftkb TEXT, disable TEXT)"))
            db.execSQL(((("INSERT INTO keyboard " + "(code, name, desc, type, image, imkb, imshiftkb, engkb, engshiftkb, ") + "symbolkb, symbolshiftkb, defaultkb, defaultshiftkb, extendedkb, extendedshiftkb, disable) ") + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"), arrayOf(LIME.DB_TABLE_CJ, "倉頡", "倉頡建盤", "phone", "cj_keyboard_preview", "lime_cj", "lime_cj_shift", "lime_abc", "lime_abc_shift", "symbols", "symbols_shift", "lime", "lime_shift", "lime", "lime_shift", ""))
            insertStaleEmojiFtsSchema(db)
            db.setVersion(userVersion)
        } finally {
            db.close()
        }
        return dbFile
    }
    private fun dropEmojiFtsSchemaRowsForFixture(db: SQLiteDatabase) {
        db.execSQL("PRAGMA writable_schema=ON")
        try {
            db.delete("sqlite_master", "name = ? OR tbl_name = ? OR name LIKE ?", arrayOf("emoji_fts", "emoji_fts", "emoji_fts_%"))
        } finally {
            db.execSQL("PRAGMA writable_schema=OFF")
        }
    }
    private fun insertStaleEmojiFtsSchema(db: SQLiteDatabase) {
        db.execSQL("PRAGMA writable_schema=ON")
        try {
            db.execSQL(((((("INSERT INTO sqlite_master(type, name, tbl_name, rootpage, sql) " + "VALUES('table', 'emoji_fts', 'emoji_fts', 0, ") + "'CREATE VIRTUAL TABLE emoji_fts USING fts5(") + "name_en, name_tw, tags_en, tags_tw, ") + "content=''emoji_data'', content_rowid=''rowid'', ") + "tokenize=''unicode61 remove_diacritics 1'')')"))
        } finally {
            db.execSQL("PRAGMA writable_schema=OFF")
        }
    }
    private fun replaceAppDatabaseWith(source: File) {
        closeCurrentDatabase()
        deleteAppDatabaseFiles()
        copyFile(source, appDb)
    }
    private fun closeCurrentDatabase() {
        try {
            var db: LimeDB = LimeDB(appContext)
            db.close()
        } catch (ignored: Exception) {

        }
    }
    private fun deleteAppDatabaseFiles() {
        appDb.delete()
        appDbWal.delete()
        appDbShm.delete()
        appDbJournal.delete()
    }
    private fun copyRawResourceToFile(rawResourceId: Int, target: File) {
        var input: InputStream = appContext.getResources().openRawResource(rawResourceId)
        try {
            var output: OutputStream = FileOutputStream(target)
            try {
                var buffer: ByteArray = ByteArray(8192)
                var read: Int
                while (true) {
                    read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
            } finally {
                output.close()
            }
        } finally {
            input.close()
        }
    }
    private fun copyFile(source: File, target: File) {
        var parent: File = target.getParentFile()!!
        if ((parent != null)) {
            parent.mkdirs()
        }
        var input: InputStream = FileInputStream(source)
        try {
            var output: OutputStream = FileOutputStream(target)
            try {
                var buffer: ByteArray = ByteArray(8192)
                var read: Int
                while (true) {
                    read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
            } finally {
                output.close()
            }
        } finally {
            input.close()
        }
    }
    private fun createDatabaseRestoreZip(dbFile: File, zipFile: File) {
        createDatabaseRestoreZip(dbFile, zipFile, ("databases/" + LIME.DATABASE_NAME))
    }
    private fun createDatabaseRestoreZip(dbFile: File, zipFile: File, entryName: String) {
        var zip: ZipOutputStream = ZipOutputStream(FileOutputStream(zipFile))
        try {
            zip.putNextEntry(ZipEntry(entryName))
            var input: InputStream = FileInputStream(dbFile)
            try {
                var buffer: ByteArray = ByteArray(8192)
                var read: Int
                while (true) {
                    read = input.read(buffer)
                    if (read == -1) break
                    zip.write(buffer, 0, read)
                }
            } finally {
                input.close()
            }
            zip.closeEntry()
        } finally {
            zip.close()
        }
    }
    private fun queryUserVersion(): Int {
        return queryInt("PRAGMA user_version")
    }
    private fun queryInt(sql: String, vararg args: String): Int {
        var cursor: Cursor? = null
        var db: SQLiteDatabase = SQLiteDatabase.openDatabase(appDb.getPath(), null, SQLiteDatabase.OPEN_READONLY)
        try {
            cursor = db.rawQuery(sql, args)
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        } finally {
            if ((cursor != null)) {
                cursor.close()
            }
            db.close()
        }
    }
    private fun queryString(sql: String, vararg args: String): String {
        var cursor: Cursor? = null
        var db: SQLiteDatabase = SQLiteDatabase.openDatabase(appDb.getPath(), null, SQLiteDatabase.OPEN_READONLY)
        try {
            cursor = db.rawQuery(sql, args)
            assertTrue(cursor.moveToFirst())
            return cursor.getString(0)
        } finally {
            if ((cursor != null)) {
                cursor.close()
            }
            db.close()
        }
    }
    @Test
    fun freshInstallImportsScoredDictionaryFromPayload() {
        var db: LimeDB = LimeDB(appContext)
        db.close()
        assertEquals(104, queryUserVersion())
        assertEquals("dictionary must be a plain (non-fts) table", 0, queryInt(("SELECT COUNT(*) FROM sqlite_master WHERE name='dictionary' " + "AND sql LIKE '%USING fts%'")))
        assertTrue("scored dictionary must have basescore + score columns", (dictionaryHasColumn("basescore") && dictionaryHasColumn("score")))
        assertTrue("dictionary rows imported from payload", (queryInt("SELECT COUNT(*) FROM dictionary") > 0))
        assertEquals("1.0", queryString("SELECT desc FROM im WHERE code='dictionary' AND title='version'"))
    }
    @Test
    fun openingLegacyFtsDictionaryRebuildsScoredTable() {
        var fixture: File = createLegacyFtsDictionaryFixture("lime_legacy_fts_dictionary.db")
        replaceAppDatabaseWith(fixture)
        var db: LimeDB = LimeDB(appContext)
        db.close()
        assertEquals(0, queryInt(("SELECT COUNT(*) FROM sqlite_master WHERE name='dictionary' " + "AND sql LIKE '%USING fts%'")))
        assertEquals("no fts shadow tables remain", 0, queryInt(("SELECT COUNT(*) FROM sqlite_master WHERE type='table' " + "AND name LIKE 'dictionary\\_%' ESCAPE '\\'")))
        assertTrue((dictionaryHasColumn("basescore") && dictionaryHasColumn("score")))
        assertTrue("scored dictionary populated after legacy upgrade", (queryInt("SELECT COUNT(*) FROM dictionary") > 0))
        assertEquals("1.0", queryString("SELECT desc FROM im WHERE code='dictionary' AND title='version'"))
    }
    @Test
    fun secondOpenDoesNotReimportDictionary() {
        var db1: LimeDB = LimeDB(appContext)
        db1.close()
        var rowsAfterFirst: Int = queryInt("SELECT COUNT(*) FROM dictionary")
        var imRowsAfterFirst: Int = queryInt("SELECT COUNT(*) FROM im WHERE code='dictionary' AND title='version'")
        var db2: LimeDB = LimeDB(appContext)
        db2.close()
        assertEquals("dictionary rows unchanged on second open", rowsAfterFirst, queryInt("SELECT COUNT(*) FROM dictionary"))
        assertEquals("no duplicate im dictionary version row", imRowsAfterFirst, queryInt("SELECT COUNT(*) FROM im WHERE code='dictionary' AND title='version'"))
        assertEquals(1, imRowsAfterFirst)
    }
    @Test
    fun recordEnglishUsageIncrementsScore() {
        var db: LimeDB = LimeDB(appContext)
        try {
            var word: String = queryString("SELECT word FROM dictionary LIMIT 1")
            var before: Int = queryInt("SELECT score FROM dictionary WHERE word = ?", word)
            db.recordEnglishUsage(word)
            var after: Int = queryInt("SELECT score FROM dictionary WHERE word = ?", word)
            assertEquals((before + 1), after)
            db.recordEnglishUsage("zzqqxx_not_a_word")
        } finally {
            db.close()
        }
    }
    private fun dictionaryHasColumn(column: String): Boolean {
        var db: SQLiteDatabase = SQLiteDatabase.openDatabase(appDb.getPath(), null, SQLiteDatabase.OPEN_READONLY)
        var c: Cursor? = null
        try {
            c = db.rawQuery("PRAGMA table_info(dictionary)", null)
            var nameIdx: Int = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (column.equals(c.getString(nameIdx))) {
                    return true
                }
            }
            return false
        } finally {
            if ((c != null)) {
                c.close()
            }
            db.close()
        }
    }
    private fun createLegacyFtsDictionaryFixture(name: String): File {
        var dbFile: File = File(appContext.getFilesDir(), name)
        copyRawResourceToFile(R.raw.lime, dbFile)
        var db: SQLiteDatabase = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.execSQL("DROP TABLE IF EXISTS dictionary")
            db.execSQL("CREATE VIRTUAL TABLE dictionary USING fts3(word)")
            db.execSQL("INSERT INTO dictionary(word) VALUES ('the'),('and'),('salt'),('year')")
            db.execSQL("DELETE FROM im WHERE code='dictionary'")
        } finally {
            db.close()
        }
        return dbFile
    }
}
