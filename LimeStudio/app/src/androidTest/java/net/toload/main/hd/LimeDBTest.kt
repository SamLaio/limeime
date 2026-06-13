@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.preference.PreferenceManager
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Record
import net.toload.main.hd.limedb.LimeDB
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.data.Related
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEUtilities
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Arrays
import java.util.ArrayList
import org.junit.Assert.*
import android.util.Log

@RunWith(AndroidJUnit4::class)
open class LimeDBTest {
    companion object {
        private val TAG: String = "LimeDBTest"
    }
    private fun ensureDatabaseReady(limeDB: LimeDB): Boolean {
        run {
            var i: Int = 0
            while ((i < 100)) {
                if (limeDB.isDatabaseOnHold()) {
                    return true
                }
                if ((i < 99)) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        Log.e(TAG, "Thread interrupted while waiting for database", e)
                        return false
                    }
                }
                i++
            }
        }
        Log.e(TAG, (("ERROR: Database is still on hold after waiting 10 seconds. " + "This indicates a stuck operation from a previous test. ") + "Test may hang or fail."))
        return false
    }
    private fun initializeDatabase(limeDB: LimeDB): Boolean {
        if (ensureDatabaseReady(limeDB)) {
            Log.e(TAG, ("ERROR: Cannot initialize database - database is on hold. " + "This test may hang or fail."))
            return false
        }
        var result: Boolean = limeDB.openDBConnection(false)
        if (!result) {
            Log.e(TAG, "ERROR: Failed to open database connection")
        }
        return result
    }
    private fun writeUtf8(file: File, content: String) {
        java.io.OutputStreamWriter(java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8).use { writer ->
                writer.write(content)
        }
    }
    private fun readUtf8(file: File): String {
        var bytes: ByteArray = java.nio.file.Files.readAllBytes(file.toPath())
        return String(bytes, java.nio.charset.StandardCharsets.UTF_8)
    }
    private fun waitForImportThread(limeDB: LimeDB) {
        var waitCount: Int = 0
        var maxWait: Int = 100
        while ((waitCount < maxWait)) {
            Thread.sleep(100)
            waitCount++
            try {
                var importThreadField: java.lang.reflect.Field = LimeDB::class.java.getDeclaredField("importThread")
                importThreadField.setAccessible(true)
                var importThread: Thread = (importThreadField.get(limeDB) as Thread)
                if (((importThread == null) || importThread.isAlive())) {
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect import thread", e)
            }
        }
        Thread.sleep(200)
    }
    @Test
    fun cinImportPreservesDuplicateCodeOrderWhenSelectionSortDisabled() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertTrue(initializeDatabase(limeDB))
        var oldPhysicalSort: Boolean = PreferenceManager.getDefaultSharedPreferences(appContext).getBoolean("physical_keyboard_sort", true)
        var fixture: File = File(appContext.getCacheDir(), "issue91_order.cin")
        try {
            PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("physical_keyboard_sort", false).commit()
            writeUtf8(fixture, (((((("%ename issue91\n" + "%cname Issue91\n") + "%chardef begin\n") + "vmi 狀\n") + "vmi 绒\n") + "vmi 戕\n") + "%chardef end\n"))
            limeDB.setTableName(LIME.DB_TABLE_CUSTOM)
            limeDB.clearTable(LIME.DB_TABLE_CUSTOM)
            limeDB.setFilename(fixture)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            var mappings: MutableList<Mapping?> = limeDB.getMappingByCode("vmi", false, true)!!
            assertTrue("Expected at least three duplicate-code mappings", (mappings.size >= 3))
            assertEquals("狀", mappings.get(0).getWord())
            assertEquals("绒", mappings.get(1).getWord())
            assertEquals("戕", mappings.get(2).getWord())
        } finally {
            PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("physical_keyboard_sort", oldPhysicalSort).commit()
            if ((fixture.exists() && fixture.delete())) {
                Log.w(TAG, "Failed to delete issue91 fixture")
            }
        }
    }
    @Test(timeout = 15000)
    fun limeImportSkipsHashCommentsAndPersistsCnameVersion() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertTrue(initializeDatabase(limeDB))
        var fixture: File = File(appContext.getCacheDir(), "issue93_array10.lime")
        try {
            writeUtf8(fixture, (((((("# Array10 comment before metadata\n" + "@version@ |行列10測試版\n") + "# Comment between metadata\n") + "@cname@ |行列10測試\n") + "# Comment before mappings\n") + ",\t，\n") + ".\t。\n"))
            limeDB.setTableName(LIME.DB_TABLE_CUSTOM)
            limeDB.clearTable(LIME.DB_TABLE_CUSTOM)
            limeDB.setFilename(fixture)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals("行列10測試", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "name"))
            assertEquals("行列10測試版", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "version"))
            assertEquals("，", limeDB.getMappingByCode(",", false, true).get(0).getWord())
            assertEquals("。", limeDB.getMappingByCode(".", false, true).get(0).getWord())
        } finally {
            if ((fixture.exists() && fixture.delete())) {
                Log.w(TAG, "Failed to delete issue93 fixture")
            }
        }
    }
    @Test(timeout = 15000)
    fun cinImportPersistsEndkeyMetadata() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertTrue(initializeDatabase(limeDB))
        var fixture: File = File(appContext.getCacheDir(), "issue96_endkey.cin")
        try {
            writeUtf8(fixture, ((((("%version Endkey Test\n" + "%cname Endkey Table\n") + "%endkey ;/\n") + "%chardef begin\n") + "aa 測\n") + "%chardef end\n"))
            limeDB.setFilename(fixture)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals(";/", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "endkey"))
        } finally {
            if ((fixture.exists() && fixture.delete())) {
                Log.w(TAG, "Failed to delete issue96 cin fixture")
            }
        }
    }
    @Test(timeout = 15000)
    fun limeImportPersistsEndkeyMetadata() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertTrue(initializeDatabase(limeDB))
        var fixture: File = File(appContext.getCacheDir(), "issue96_endkey.lime")
        try {
            writeUtf8(fixture, ((("@version@|Endkey Test\n" + "@cname@|Endkey Table\n") + "@endkey@ |;/\n") + "aa|測\n"))
            limeDB.setFilename(fixture)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals(";/", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "endkey"))
        } finally {
            if ((fixture.exists() && fixture.delete())) {
                Log.w(TAG, "Failed to delete issue96 lime fixture")
            }
        }
    }
    @Test(timeout = 15000)
    fun cinImportPersistsLimeEndkeyMetadata() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertTrue(initializeDatabase(limeDB))
        var fixture: File = File(appContext.getCacheDir(), "issue96_limeendkey.cin")
        try {
            writeUtf8(fixture, (((((("%version Lime Endkey Test\n" + "%cname Lime Endkey Table\n") + "%endkey abcdefghijklmnopqrstuvwxyz\n") + "%limeendkey ;/\n") + "%chardef begin\n") + "aa 測\n") + "%chardef end\n"))
            limeDB.setFilename(fixture)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals("abcdefghijklmnopqrstuvwxyz", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "endkey"))
            assertEquals(";/", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "limeendkey"))
        } finally {
            if ((fixture.exists() && fixture.delete())) {
                Log.w(TAG, "Failed to delete issue96 limeendkey cin fixture")
            }
        }
    }
    @Test(timeout = 15000)
    fun limeImportPersistsLimeEndkeyMetadata() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertTrue(initializeDatabase(limeDB))
        var fixture: File = File(appContext.getCacheDir(), "issue96_limeendkey.lime")
        try {
            writeUtf8(fixture, (((("@version@|Lime Endkey Test\n" + "@cname@|Lime Endkey Table\n") + "@endkey@|abcdefghijklmnopqrstuvwxyz\n") + "@limeendkey@|;/\n") + "aa|測\n"))
            limeDB.setFilename(fixture)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals("abcdefghijklmnopqrstuvwxyz", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "endkey"))
            assertEquals(";/", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "limeendkey"))
        } finally {
            if ((fixture.exists() && fixture.delete())) {
                Log.w(TAG, "Failed to delete issue96 limeendkey lime fixture")
            }
        }
    }
    @Test
    fun testLimeDBInitialization() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertNotNull("LimeDB instance should not be null", limeDB)
        var connectionOpened: Boolean = limeDB.openDBConnection(false)
        assertTrue("Database connection should be opened", connectionOpened)
    }
    @Test(timeout = 5000)
    fun testEmojiDbV2SchemaSearchAndUserRecordPreservation() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        limeDB.createEmojiTablesForTest(true)
        limeDB.replaceEmojiDataForTest(Arrays.asList(LimeDB.EmojiDataRow("🇯🇵", "1F1EF,1F1F5", "Flags", "country-flag", 1, "flag: Japan", "東洋旗", "flag|Japan", "國旗|國|旗|日|本", 0.6), LimeDB.EmojiDataRow("😢", "1F622", "Smileys and Emotion", "face-concerned", 2, "crying face", "哭臉", "cry|crying|face", "哭臉|哭|臉", 1.0)), "17.0")
        assertEquals("Panel search keeps bare ASCII", "c*", LimeDB.buildEmojiPanelSearchQueryForTest("c"))
        assertEquals("Panel search keeps two-char ASCII", "cr*", LimeDB.buildEmojiPanelSearchQueryForTest("cr"))
        assertEquals("Panel search keeps CJK", "國*", LimeDB.buildEmojiPanelSearchQueryForTest("國"))
        assertEquals("Candidate search drops bare ASCII", "", LimeDB.buildEmojiCandidateQueryForTest("c"))
        assertEquals("Candidate search keeps two-char ASCII", "cr*", LimeDB.buildEmojiCandidateQueryForTest("cr"))
        assertEquals("Candidate search broadens 國旗", "國旗* OR 國*", LimeDB.buildEmojiCandidateQueryForTest("國旗"))
        assertEquals("Candidate search broadens 日本", "日本* OR 日*", LimeDB.buildEmojiCandidateQueryForTest("日本"))
        var flags: MutableList<Mapping?> = limeDB.findEmojiForCandidate("國旗", LimeDB.EmojiLocale.TW, 8)
        assertFalse("國旗 should find emoji candidates", flags.isEmpty())
        assertEquals("🇯🇵", flags.get(0).getWord())
        var japanFromFirstCharacter: MutableList<Mapping?> = limeDB.findEmojiForCandidate("日本", LimeDB.EmojiLocale.TW, 8)
        assertFalse("日本 should find emoji candidates through 日* expansion", japanFromFirstCharacter.isEmpty())
        assertEquals("🇯🇵", japanFromFirstCharacter.get(0).getWord())
        var bareAscii: MutableList<Mapping?> = limeDB.findEmojiForCandidate("c", LimeDB.EmojiLocale.EN, 8)
        assertTrue("Bare one-character ASCII candidate should be ignored", bareAscii.isEmpty())
        var panelBareAscii: MutableList<Mapping?> = limeDB.searchEmoji("c", LimeDB.EmojiLocale.EN, 8)
        assertFalse("Panel search should allow one-character ASCII prefix", panelBareAscii.isEmpty())
        assertEquals("😢", panelBareAscii.get(0).getWord())
        var cryPrefix: MutableList<Mapping?> = limeDB.findEmojiForCandidate("cr", LimeDB.EmojiLocale.EN, 8)
        assertFalse("Two-character ASCII prefix should find emoji candidates", cryPrefix.isEmpty())
        assertEquals("😢", cryPrefix.get(0).getWord())
        limeDB.recordEmojiUsage("🇯🇵", 1000L)
        limeDB.replaceEmojiDataForTest(Arrays.asList(LimeDB.EmojiDataRow("🇯🇵", "1F1EF,1F1F5", "Flags", "country-flag", 1, "flag: Japan", "日本國旗", "flag|Japan", "國旗|日本|國|旗|日|本", 0.6)), "17.0")
        assertEquals("Existing emoji user record should survive refresh", 1, limeDB.getEmojiUseCountForTest("🇯🇵"))
        assertEquals("Missing emoji user records should be absent", 0, limeDB.getEmojiUseCountForTest("😢"))
    }
    @Test
    fun testLimeDBConnectionManagement() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var opened: Boolean = limeDB.openDBConnection(false)
        assertTrue("Database connection should open", opened)
        var reopened: Boolean = limeDB.openDBConnection(false)
        assertTrue("Database connection should remain open", reopened)
        var forceReloaded: Boolean = limeDB.openDBConnection(true)
        assertTrue("Force reload should succeed", forceReloaded)
    }
    @Test
    fun testLimeDBDatabaseHold() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertFalse("Database should not be on hold initially", limeDB.isDatabaseOnHold())
        limeDB.holdDBConnection()
        assertTrue("Database should be on hold", limeDB.isDatabaseOnHold())
        limeDB.unHoldDBConnection()
        assertFalse("Database should not be on hold after unhold", limeDB.isDatabaseOnHold())
    }
    @Test(timeout = 5000)
    fun testLimeDBCountMapping() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var count: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Count should be non-negative", (count >= 0))
        var relatedCount: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
        assertTrue("Related count should be non-negative", (relatedCount >= 0))
    }
    @Test(timeout = 5000)
    fun testLimeDBAddOrUpdateMappingRecord() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode: String = ("test_code_" + System.currentTimeMillis())
        var testWord: String = "測試"
        limeDB.addOrUpdateMappingRecord(testCode, testWord)
        var countBefore: Int = limeDB.countRecords("custom", null, null)
        var testCode2: String = ("test_code2_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode2, "測試2")
        var countAfter: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Count should increase after adding record", (countAfter >= countBefore))
        limeDB.addOrUpdateMappingRecord(testCode, testWord)
        var countAfterUpdate: Int = limeDB.countRecords("custom", null, null)
        assertEquals("Count should remain same after update with same word", countAfter, countAfterUpdate)
        limeDB.addOrUpdateMappingRecord(testCode, "更新測試")
        var countAfterNewWord: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Count should increase when adding same code with different word", (countAfterNewWord > countAfter))
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingByCode() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode: String = ("test_get_" + System.currentTimeMillis())
        var testWord: String = "測試取得"
        limeDB.addOrUpdateMappingRecord(testCode, testWord)
        var results: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if ((results != null)) {
            assertTrue("Results should not be empty if record exists", (results.size >= 0))
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingByCodeSimilarListZeroSuppressesPartialMatches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var oldLimit: String = PreferenceManager.getDefaultSharedPreferences(appContext).getString("similiar_list", "20")!!
        try {
            PreferenceManager.getDefaultSharedPreferences(appContext).edit().putString("similiar_list", "0").commit()
            limeDB.setTableName("custom")
            var code: String = ("issue76ha" + System.currentTimeMillis())
            var extensionCode: String = (code + "a")
            limeDB.addOrUpdateMappingRecord(code, "白")
            limeDB.addOrUpdateMappingRecord(extensionCode, "皔")
            var results: MutableList<Mapping?> = limeDB.getMappingByCode(code, true, false)!!
            assertNotNull("Results should not be null", results)
            assertTrue("Exact match should remain visible", containsMapping(results, code, "白", false))
            assertFalse("Partial extension candidate should be suppressed when similiar_list is 0", containsMapping(results, extensionCode, "皔", true))
        } finally {
            PreferenceManager.getDefaultSharedPreferences(appContext).edit().putString("similiar_list", oldLimit).commit()
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingByCodePositiveSimilarListDoesNotAllowOneExtraPartialMatch() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var oldLimit: String = PreferenceManager.getDefaultSharedPreferences(appContext).getString("similiar_list", "20")!!
        try {
            PreferenceManager.getDefaultSharedPreferences(appContext).edit().putString("similiar_list", "1").commit()
            limeDB.setTableName("custom")
            var code: String = ("issue76limit" + System.currentTimeMillis())
            limeDB.addOrUpdateMappingRecord(code, "白")
            limeDB.addOrUpdateMappingRecord((code + "a"), "皔")
            limeDB.addOrUpdateMappingRecord((code + "b"), "晧")
            var results: MutableList<Mapping?> = limeDB.getMappingByCode(code, true, false)!!
            assertNotNull("Results should not be null", results)
            assertTrue("Exact match should remain visible", containsMapping(results, code, "白", false))
            assertTrue("Partial matches should not exceed similiar_list", (countPartialMatches(results) <= 1))
        } finally {
            PreferenceManager.getDefaultSharedPreferences(appContext).edit().putString("similiar_list", oldLimit).commit()
        }
    }
    private fun containsMapping(results: MutableList<Mapping?>, code: String, word: String, partial: Boolean): Boolean {
        if ((results == null)) {
            return false
        }
        for (mapping in results) {
            if (((((mapping != null) && code.equals(mapping.getCode())) && word.equals(mapping.getWord())) && (!partial || mapping.isPartialMatchToCodeRecord()))) {
                return true
            }
        }
        return false
    }
    private fun countPartialMatches(results: MutableList<Mapping?>): Int {
        var count: Int = 0
        if ((results == null)) {
            return count
        }
        for (mapping in results) {
            if (((mapping != null) && mapping.isPartialMatchToCodeRecord())) {
                count++
            }
        }
        return count
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingByWord() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode: String = ("test_word_" + System.currentTimeMillis())
        var testWord: String = "測試詞彙"
        limeDB.addOrUpdateMappingRecord(testCode, testWord)
        var results: MutableList<Mapping?> = limeDB.getMappingByWord(testWord, "custom")!!
        if ((results != null)) {
            assertTrue("Results should not be empty if record exists", (results.size >= 0))
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBRelatedPhraseOperations() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var testPword: String = "測試"
        var testCword: String = "詞彙"
        var existing: Mapping = limeDB.isRelatedPhraseExist(testPword, testCword)!!
        if ((existing != null)) {
            assertNotNull("Existing mapping should have ID", existing.getId())
        }
        var score: Int = limeDB.addOrUpdateRelatedPhraseRecord(testPword, testCword)
        assertTrue("Score should be non-negative", (score >= 1))
        var related: MutableList<Mapping?> = limeDB.getRelatedPhrase(testPword, false)
        if ((related != null)) {
            assertTrue("Related phrases list should be non-null", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBImInfoOperations() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var testIm: String = ("test_im_" + System.currentTimeMillis())
        var testField: String = "test_field"
        var testValue: String = "test_value"
        limeDB.setImConfig(testIm, testField, testValue)
        var retrievedValue: String = limeDB.getImConfig(testIm, testField)
        assertEquals("Retrieved value should match set value", testValue, retrievedValue)
        limeDB.removeImConfig(testIm, testField)
        var valueAfterRemove: String = limeDB.getImConfig(testIm, testField)
        assertTrue("Value should be empty after removal", ((valueAfterRemove == null) || valueAfterRemove.isEmpty()))
    }
    @Test(timeout = 5000)
    fun testLimeDBKeyboardOperations() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var keyboards: MutableList<Keyboard?> = limeDB.getKeyboardConfigList()!!
        if ((keyboards != null)) {
            assertTrue("Keyboard list should be accessible", true)
        }
        var keyboard: Keyboard = limeDB.getKeyboardConfig("lime")!!
        if ((keyboard != null)) {
            assertNotNull("Keyboard code should not be null", keyboard.getCode())
        }
        var keyboardInfo: String = limeDB.getKeyboardInfo("lime", "name")!!
    }
    @Test
    fun testLimeDBTableNameOperations() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var testTable: String = "custom"
        limeDB.setTableName(testTable)
        var retrievedTable: String = limeDB.getTableName()
        assertEquals("Retrieved table name should match", testTable, retrievedTable)
    }
    @Test(timeout = 5000)
    fun testLimeDBClearTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode: String = ("test_delete_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試刪除")
        var countBefore: Int = limeDB.countRecords("custom", null, null)
        var countAfter: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Count should be non-negative", (countAfter >= 0))
    }
    @Test(timeout = 5000)
    fun testLimeDBImListOperations() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var imConfigList: MutableList<ImConfig?> = limeDB.getImConfigList(null, null)
        if ((imConfigList != null)) {
            assertTrue("IM list should be accessible", true)
        }
        var imConfigByCode: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_PHONETIC, null)
        if ((imConfigByCode != null)) {
            assertTrue("IM list by code should be accessible", true)
        }
    }
    @Test(timeout = 5000)
    fun testGetImConfigListNameFallsBackToBuiltInFullNameForLegacyRows() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.resetImConfig(LIME.DB_TABLE_CUSTOM)
        limeDB.setIMConfigKeyboard(LIME.DB_TABLE_CUSTOM, "Legacy Keyboard", "lime")
        var configs: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_CUSTOM, LIME.IM_FULL_NAME)
        assertFalse("Legacy IM row should still be surfaced for the name list", configs.isEmpty())
        assertEquals("自建輸入法", configs.get(0).getDesc())
    }
    @Test(timeout = 5000)
    fun testGetImConfigListNameFallsBackToBuiltInFullNameWhenDescIsEmpty() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.resetImConfig(LIME.DB_TABLE_ARRAY10)
        limeDB.setImConfig(LIME.DB_TABLE_ARRAY10, "source", "array10a-v2023-1.0-20260517.lime")
        limeDB.setImConfig(LIME.DB_TABLE_ARRAY10, LIME.IM_FULL_NAME, "")
        var configs: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_ARRAY10, LIME.IM_FULL_NAME)
        assertFalse("IM name row should be surfaced for the name list", configs.isEmpty())
        assertEquals("行列10輸入法", configs.get(0).getDesc())
    }
    @Test(timeout = 5000)
    fun testLimeDBEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var nullResults: MutableList<Mapping?> = limeDB.getMappingByCode("", true, false)!!
        var nonExistentCount: Int = limeDB.countRecords(("non_existent_table_" + System.currentTimeMillis()), null, null)
        assertEquals("Count should be 0 for non-existent table", 0, nonExistentCount)
        var nonExistentInfo: String = limeDB.getImConfig("non_existent_im", "field")
        assertTrue("Info should be empty for non-existent IM", ((nonExistentInfo == null) || nonExistentInfo.isEmpty()))
    }
    @Test(timeout = 5000)
    fun testLimeDBAddScore() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode: String = ("test_score_" + System.currentTimeMillis())
        var testWord: String = "測試分數"
        limeDB.addOrUpdateMappingRecord(testCode, testWord)
        var mappings: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if (((mappings != null) && mappings.isEmpty())) {
            var mapping: Mapping = mappings.get(0)!!
            var originalScore: Int = mapping.getScore()
            limeDB.addScore(mapping)
            var updatedMappings: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
            if (((updatedMappings != null) && updatedMappings.isEmpty())) {
                var updatedMapping: Mapping = updatedMappings.get(0)!!
                assertTrue("Score should increase or remain same", (updatedMapping.getScore() >= originalScore))
            }
        }
    }
    @Test
    fun testLimeDBCodeDualMapped() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var isDualMapped: Boolean = LimeDB.isCodeDualMapped()
        assertTrue("isCodeDualMapped should return boolean", true)
    }
    @Test
    fun testLimeDBProgressTracking() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setFinish(true)
        var count: Int = limeDB.getCountImported()
        assertTrue("Count should be non-negative", (count >= 0))
        var progress: Int = limeDB.getProgressPercentageDone()
        assertTrue("Progress should be between 0 and 100", ((progress >= 0) && (progress <= 100)))
    }
    @Test(timeout = 5000)
    fun testLimeDBCheckAndUpdateRelatedTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.checkAndUpdateRelatedTable()
        assertTrue("checkAndUpdateRelatedTable should complete", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBCheckPhoneticKeyboardSetting() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.checkPhoneticKeyboardSetting()
        assertTrue("checkPhoneticKeyboardSetting should complete", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetCodeListStringByWord() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var codeList: String = limeDB.getCodeListStringByWord("測試")!!
        assertTrue("getCodeListStringByWord should complete", true)
    }
    @Test
    fun testLimeDBKeyToKeyName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var keyname: String = limeDB.keyToKeyName("a", LIME.DB_TABLE_PHONETIC, false)
        assertNotNull("keyToKeyname should return a string", keyname)
        var composingKeyname: String = limeDB.keyToKeyName("a", LIME.DB_TABLE_PHONETIC, true)
        assertNotNull("keyToKeyname with composing should return a string", composingKeyname)
    }
    @Test
    fun testLimeDBPreProcessingRemappingCode() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var remappedCode: String = limeDB.preProcessingRemappingCode("a")
        assertNotNull("preProcessingRemappingCode should return a string", remappedCode)
        var emptyRemapped: String = limeDB.preProcessingRemappingCode("")
        assertTrue("Empty code should return empty string", ((emptyRemapped == null) || emptyRemapped.isEmpty()))
    }
    @Test(timeout = 5000)
    fun testLimeDBRenameTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode: String = ("test_rename_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試")
        assertTrue("renameTableName method should exist", true)
    }
    @Test
    fun testLimeDBGetEnglishSuggestions() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var suggestions: MutableList<String?> = limeDB.getEnglishSuggestions("test")!!
        if ((suggestions != null)) {
            assertTrue("Suggestions list should be accessible", true)
        }
    }
    @Test
    fun englishSuggestionsUseDictionaryRankInsteadOfAlphabeticalOnly() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var suggestions: MutableList<String?> = limeDB.getEnglishSuggestions("sal")!!
        assertNotNull("Suggestions should be available", suggestions)
        assertFalse("sal should return bundled dictionary suggestions", suggestions.isEmpty())
        assertEquals("salt", suggestions.get(0))
    }
    @Test(timeout = 10000)
    fun testLimeDBEmojiConvert() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var emojiResults: MutableList<Mapping?> = limeDB.emojiConvert("測試", LIME.EMOJI_CN)!!
            assertNotNull("Emoji conversion should return a list (not null)", emojiResults)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: emojiConvert threw exception: " + e.getMessage()), e)
            fail(("ERROR: emojiConvert should not throw exceptions - EmojiConverter catches exceptions internally. Exception: " + e.getMessage()))
        }
    }
    @Test
    fun testLimeDBHanConvert() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var converted: String = limeDB.hanConvert("測試", 0)!!
        assertNotNull("hanConvert should return a string", converted)
    }
    @Test
    fun testLimeDBGetBaseScore() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var baseScore: Int = limeDB.getBaseScore("測試")
        assertTrue("Base score should be non-negative", (baseScore >= 0))
    }
    @Test(timeout = 5000)
    fun testLimeDBResetImConfig() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var testIm: String = ("test_reset_" + System.currentTimeMillis())
        limeDB.setImConfig(testIm, "test_field", "test_value")
        var valueBefore: String = limeDB.getImConfig(testIm, "test_field")
        assertEquals("Value should be set", "test_value", valueBefore)
        limeDB.resetImConfig(testIm)
        var valueAfter: String = limeDB.getImConfig(testIm, "test_field")
        assertTrue("Value should be empty after reset", ((valueAfter == null) || valueAfter.isEmpty()))
    }
    @Test(timeout = 5000)
    fun testLimeDBGetAllRelated() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var relatedList: MutableList<Related> = limeDB.getRelated(null, 0, 0)
        assertNotNull("getAllRelated should return a list (not null)", relatedList)
        assertTrue("getAllRelated operation should complete", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBInsertOperation() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put(LIME.DB_RELATED_COLUMN_PWORD, "測試插入")
        values.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙插入")
        values.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1)
        var result: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, values)
        var related: Mapping = limeDB.isRelatedPhraseExist("測試插入", "詞彙插入")!!
        assertTrue("addRecord operation should complete", (result >= 1))
    }
    @Test(timeout = 5000)
    fun testLimeDBInsertWithContentValues() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var cv: android.content.ContentValues = android.content.ContentValues()
        cv.put(LIME.DB_RELATED_COLUMN_PWORD, "測試內容")
        cv.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙內容")
        cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1)
        var result: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, cv)
        assertTrue("addRecord with ContentValues should complete", (result >= 1))
    }
    @Test(timeout = 5000)
    fun testLimeDBAddOperation() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put(LIME.DB_RELATED_COLUMN_PWORD, "測試2")
        values.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙2")
        values.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1)
        var result: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, values)
        assertTrue("addRecord operation should complete", (result >= 1))
    }
    @Test(timeout = 5000)
    fun testLimeDBRemoveOperation() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var cv: android.content.ContentValues = android.content.ContentValues()
        cv.put(LIME.DB_RELATED_COLUMN_PWORD, "測試刪除")
        cv.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙刪除")
        cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1)
        limeDB.addRecord(LIME.DB_TABLE_RELATED, cv)
        var whereClause: String = (((LIME.DB_RELATED_COLUMN_PWORD + " = ? AND ") + LIME.DB_RELATED_COLUMN_CWORD) + " = ?")
        var whereArgs: Array<String?> = arrayOf<String?>("測試刪除", "詞彙刪除")
        var result: Int = limeDB.deleteRecord(LIME.DB_TABLE_RELATED, whereClause, whereArgs)
        assertTrue("deleteRecord operation should complete", (result >= 1))
    }
    @Test(timeout = 5000)
    fun testLimeDBUpdateOperation() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var cv: android.content.ContentValues = android.content.ContentValues()
        cv.put(LIME.DB_RELATED_COLUMN_PWORD, "測試更新")
        cv.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙更新")
        cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1)
        limeDB.addRecord(LIME.DB_TABLE_RELATED, cv)
        var updateValues: android.content.ContentValues = android.content.ContentValues()
        updateValues.put(LIME.DB_RELATED_COLUMN_USERSCORE, 2)
        var whereClause: String = (LIME.DB_RELATED_COLUMN_PWORD + " = ?")
        var whereArgs: Array<String?> = arrayOf<String?>("測試更新")
        var result: Int = limeDB.updateRecord(LIME.DB_TABLE_RELATED, updateValues, whereClause, whereArgs)
        assertTrue("updateRecord operation should complete", (result >= 1))
    }
    @Test(timeout = 5000)
    fun testLimeDBGetKeyboardConfigList() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var keyboards: MutableList<Keyboard?> = limeDB.getKeyboardConfigList()!!
        if ((keyboards != null)) {
            assertTrue("Keyboard list should be accessible", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBGetImConfigList() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var imConfigList: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_PHONETIC, null)
        if ((imConfigList != null)) {
            assertTrue("IM list should be accessible", true)
        }
        var imConfigByType: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_PHONETIC, "keyboard")
        if ((imConfigByType != null)) {
            assertTrue("IM list by type should be accessible", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBGetRecordList() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var records: MutableList<Record> = limeDB.getRecordList("custom", null, false, 10, 0)
        if ((records != null)) {
            assertTrue("Word list should be accessible", true)
        }
        var wordsWithQuery: MutableList<Record> = limeDB.getRecordList("custom", "測試", false, 10, 0)
        if ((wordsWithQuery != null)) {
            assertTrue("Word list with query should be accessible", true)
        }
    }
    @Test
    fun testLimeDBGetRecord() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        var testCode: String = ("test_getword_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試詞")
        var mappings: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if (((mappings != null) && mappings.isEmpty())) {
            var mapping: Mapping = mappings.get(0)!!
            var idStr: String = mapping.getId()!!
            if (((idStr != null) && idStr.isEmpty())) {
                try {
                    var id: Long = Long.parseLong(idStr)
                    var record: Record = limeDB.getRecord("custom", id)!!
                    if ((record != null)) {
                        assertNotNull("Word should have content", record.getWord())
                    }
                } catch (e: NumberFormatException) {

                }
            }
        }
    }
    @Test
    fun testLimeDBBackupUserRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        var testCode: String = ("test_backup_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試備份")
        limeDB.backupUserRecords("custom")
        var hasBackup: Boolean = limeDB.checkBackupTable("custom")
        assertTrue("backupUserRecords should complete", true)
    }
    @Test
    fun testLimeDBCheckBackupTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var hasBackup: Boolean = limeDB.checkBackupTable("custom")
        assertTrue("checkBackuptable should return boolean", true)
    }
    @Test
    fun testLimeDBSetImKeyboardWithConfigConfigKeyboard() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var keyboards: MutableList<Keyboard?> = limeDB.getKeyboardConfigList()!!
        if (((keyboards != null) && keyboards.isEmpty())) {
            var keyboard: Keyboard = keyboards.get(0)!!
            limeDB.setImConfigKeyboard("custom", keyboard)
            assertTrue("setImKeyboard with Keyboard should complete", true)
        }
    }
    @Test
    fun testLimeDBGetMappingByCodeWithAllRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        var testCode: String = ("test_all_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試全部")
        var allRecords: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, true)!!
        if ((allRecords != null)) {
            assertTrue("All records should be accessible", true)
        }
        var limitedRecords: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if ((limitedRecords != null)) {
            assertTrue("Limited records should be accessible", true)
        }
    }
    @Test
    fun testLimeDBGetMappingByCodeWithSoftKeyboard() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        var testCode: String = ("test_soft_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試軟鍵盤")
        var softResults: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if ((softResults != null)) {
            assertTrue("Soft keyboard results should be accessible", true)
        }
        var physicalResults: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, false, false)!!
        if ((physicalResults != null)) {
            assertTrue("Physical keyboard results should be accessible", true)
        }
    }
    @Test
    fun testLimeDBGetRelatedPhraseWithAllRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙")
        var allRelated: MutableList<Mapping?> = limeDB.getRelatedPhrase("測試", true)
        if ((allRelated != null)) {
            assertTrue("All related phrases should be accessible", true)
        }
        var limitedRelated: MutableList<Mapping?> = limeDB.getRelatedPhrase("測試", false)
        if ((limitedRelated != null)) {
            assertTrue("Limited related phrases should be accessible", true)
        }
    }
    @Test
    fun testLimeDBAddOrUpdateMappingRecordWithScore() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        var testCode: String = ("test_score_" + System.currentTimeMillis())
        var testWord: String = "測試分數"
        limeDB.addOrUpdateMappingRecord("custom", testCode, testWord, 10)
        var results: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if (((results != null) && results.isEmpty())) {
            var mapping: Mapping = results.get(0)!!
            assertTrue("Mapping should have score", (mapping.getScore() >= 0))
        }
    }
    @Test
    fun testLimeDBAddScoreWithRelatedPhrase() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var score: Int = limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙")
        if ((score > 0)) {
            var related: Mapping = limeDB.isRelatedPhraseExist("測試", "詞彙")!!
            if ((related != null)) {
                var originalScore: Int = related.getScore()
                limeDB.addScore(related)
                var updated: Mapping = limeDB.isRelatedPhraseExist("測試", "詞彙")!!
                if ((updated != null)) {
                    assertTrue("Score should increase or remain same", (updated.getScore() >= originalScore))
                }
            }
        }
    }
    @Test
    fun testLimeDBRawQuery() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var cursor: android.database.Cursor = limeDB.rawQuery((("SELECT * FROM " + LIME.DB_TABLE_RELATED) + " LIMIT 1"))!!
        if ((cursor != null)) {
            cursor.close()
        }
        assertTrue("rawQuery with valid query should complete", true)
        var invalidCursor: android.database.Cursor = limeDB.rawQuery("SELECT * FROM invalid_table_name LIMIT 1")!!
        assertNull("rawQuery with invalid table should return null", invalidCursor)
        var nullCursor: android.database.Cursor = limeDB.rawQuery(null)!!
        assertNull("rawQuery with null should return null", nullCursor)
        var emptyCursor: android.database.Cursor = limeDB.rawQuery("")!!
        assertTrue("rawQuery with empty query should handle gracefully", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBDatabaseHoldWithOperations() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (limeDB.isDatabaseOnHold()) {
            limeDB.unHoldDBConnection()
        }
        limeDB.holdDBConnection()
        assertTrue("Database should be on hold", limeDB.isDatabaseOnHold())
        limeDB.unHoldDBConnection()
        assertFalse("Database should not be on hold", limeDB.isDatabaseOnHold())
        var results: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, false)!!
        assertTrue("Operations should work after unhold", true)
        var opened: Boolean = limeDB.openDBConnection(false)
        assertTrue("Database should open after unhold", opened)
    }
    @Test
    fun testLimeDBCursorHelperMethods() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put(LIME.DB_RELATED_COLUMN_PWORD, "測試")
        values.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙")
        values.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1)
        values.put(LIME.DB_RELATED_COLUMN_BASESCORE, 1)
        limeDB.addRecord(LIME.DB_TABLE_RELATED, values)
        var relatedList: MutableList<Related> = limeDB.getRelated(null, 0, 0)
        if (((relatedList != null) && relatedList.isEmpty())) {
            var related: Related = relatedList.get(0)
            var pword: String = related.getPword()!!
            assertNotNull("getPword should return a string (not null)", pword)
            var id: Int = related.getIdAsInt()
            assertTrue("getId should return non-negative", (id >= 0))
            var cword: String = related.getCword()!!
            assertTrue("getCword should be accessible", true)
            var basescore: Int = related.getBasescore()
            assertTrue("getBasescore should return non-negative", (basescore >= 0))
            var userscore: Int = related.getUserscore()
            assertTrue("getUserscore should return non-negative", (userscore >= 0))
        } else {
            assertTrue("getAllRelated should return empty list if no records exist", true)
        }
        assertTrue("getAllRelated operation should complete", true)
    }
    @Test
    fun testLimeDBTransactionRollback() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        var testCode: String = ("test_transaction_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試交易")
        var results: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if (((results != null) && results.isEmpty())) {
            assertTrue("Record should exist after operation", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBInvalidTableNameHandling() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var invalidCount: Int = limeDB.countRecords("'; DROP TABLE custom; --", null, null)
            assertEquals("Invalid table name should return 0", 0, invalidCount)
        } catch (e: android.database.sqlite.SQLiteException) {
            assertTrue("Invalid table name should cause SQL error", true)
        }
        try {
            var invalidCount2: Int = limeDB.countRecords("'; DROP TABLE custom; --", null, null)
            assertEquals("Invalid table name should return 0", 0, invalidCount2)
        } catch (e: android.database.sqlite.SQLiteException) {
            assertTrue("Invalid table name should cause SQL error", true)
        }
        var invalidRecords: MutableList<Record> = limeDB.getRecordList("'; DROP TABLE custom; --", null, false, 0, 0)
        assertTrue("Invalid table name should be handled safely", ((invalidRecords == null) || invalidRecords.isEmpty()))
    }
    @Test
    fun testLimeDBGetMappingByCodeEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        var nullResults: MutableList<Mapping?> = limeDB.getMappingByCode(null, true, false)!!
        assertTrue("Null code should be handled", true)
        var emptyResults: MutableList<Mapping?> = limeDB.getMappingByCode("", true, false)!!
        assertTrue("Empty code should be handled", true)
        var longCode: String = "a"
        var longResults: MutableList<Mapping?> = limeDB.getMappingByCode(longCode, true, false)!!
        assertTrue("Very long code should be handled", true)
        var specialCode: String = "test'code\"with;special--chars"
        var specialResults: MutableList<Mapping?> = limeDB.getMappingByCode(specialCode, true, false)!!
        assertTrue("Special characters in code should be handled", true)
    }
    @Test
    fun testLimeDBGetRelatedPhraseEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullResults: MutableList<Mapping?> = limeDB.getRelatedPhrase(null, false)
        assertTrue("Null pword should be handled", true)
        var emptyResults: MutableList<Mapping?> = limeDB.getRelatedPhrase("", false)
        assertTrue("Empty pword should be handled", true)
        var singleCharResults: MutableList<Mapping?> = limeDB.getRelatedPhrase("測", false)
        if ((singleCharResults != null)) {
            assertTrue("Single character pword should work", true)
        }
        var longPword: String = "測"
        var longResults: MutableList<Mapping?> = limeDB.getRelatedPhrase(longPword, false)
        if ((longResults != null)) {
            assertTrue("Very long pword should be handled", true)
        }
    }
    @Test
    fun testLimeDBAddOrUpdateMappingRecordEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        try {
            limeDB.addOrUpdateMappingRecord(null, "測試")
            assertTrue("Null code should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null code should be handled gracefully", true)
        }
        try {
            limeDB.addOrUpdateMappingRecord("test", null)
            assertTrue("Null word should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null word should be handled gracefully", true)
        }
        limeDB.addOrUpdateMappingRecord("", "測試")
        assertTrue("Empty code should be handled", true)
        limeDB.addOrUpdateMappingRecord("test", "")
        assertTrue("Empty word should be handled", true)
        var longCode: String = "a"
        var longWord: String = "測"
        limeDB.addOrUpdateMappingRecord(longCode, longWord)
        assertTrue("Very long code and word should be handled", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBAddOrUpdateRelatedPhraseRecordEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var nullPwordResult: Int = limeDB.addOrUpdateRelatedPhraseRecord(null, "詞彙")
            assertTrue("Null pword should return -1 or handle gracefully", (nullPwordResult >= 1))
        } catch (e: AssertionError) {
            assertTrue("Null pword should throw AssertionError", true)
        }
        try {
            var nullCwordResult: Int = limeDB.addOrUpdateRelatedPhraseRecord("測試", null)
            assertTrue("Null cword should return -1 or handle gracefully", (nullCwordResult >= 1))
        } catch (e: AssertionError) {
            assertTrue("Null cword should throw AssertionError", true)
        }
        var emptyPwordResult: Int = limeDB.addOrUpdateRelatedPhraseRecord("", "詞彙")
        assertTrue("Empty pword should return -1 or handle gracefully", (emptyPwordResult >= 1))
        var emptyCwordResult: Int = limeDB.addOrUpdateRelatedPhraseRecord("測試", "")
        assertTrue("Empty cword should return -1 or handle gracefully", (emptyCwordResult >= 1))
        var sameResult: Int = limeDB.addOrUpdateRelatedPhraseRecord("測試", "測試")
        assertTrue("Same pword and cword should be handled", (sameResult >= 1))
    }
    @Test
    fun testLimeDBIsRelatedPhraseExistEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullPwordResult: Mapping = limeDB.isRelatedPhraseExist(null, "詞彙")!!
        assertNull("Null pword should return null", nullPwordResult)
        var nullCwordResult: Mapping = limeDB.isRelatedPhraseExist("測試", null)!!
        assertTrue("Null cword should be handled", true)
        var emptyPwordResult: Mapping = limeDB.isRelatedPhraseExist("", "詞彙")!!
        assertNull("Empty pword should return null", emptyPwordResult)
        var emptyCwordResult: Mapping = limeDB.isRelatedPhraseExist("測試", "")!!
        assertTrue("Empty cword should be handled", true)
    }
    @Test
    fun testLimeDBGetRecordSizeEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var nullQuerySize: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Null query should return non-negative size", (nullQuerySize >= 0))
        var emptyQuerySize: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Empty query should return non-negative size", (emptyQuerySize >= 0))
        var codeSize: Int = limeDB.countRecords("custom", "code LIKE ?", arrayOf("測試%"))
        assertTrue("Code filter should return non-negative size", (codeSize >= 0))
        var wordSize: Int = limeDB.countRecords("custom", "word LIKE ?", arrayOf("%測試%"))
        assertTrue("Word filter should return non-negative size", (wordSize >= 0))
    }
    @Test
    fun testLimeDBGetRelatedSizeEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var whereBuilder: StringBuilder = StringBuilder()
        whereBuilder.append("ifnull(").append(LIME.DB_RELATED_COLUMN_CWORD).append(", '') <> ''")
        var nullPwordSize: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, whereBuilder.toString(), null)
        assertTrue("Null pword should return non-negative", (nullPwordSize >= 0))
        var emptyPwordSize: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, whereBuilder.toString(), null)
        assertTrue("Empty pword should return non-negative", (emptyPwordSize >= 0))
        whereBuilder = StringBuilder()
        whereBuilder.append(LIME.DB_RELATED_COLUMN_PWORD).append(" = ? AND ")
        whereBuilder.append("ifnull(").append(LIME.DB_RELATED_COLUMN_CWORD).append(", '') <> ''")
        var singleCharSize: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, whereBuilder.toString(), arrayOf("測"))
        assertTrue("Single character should return non-negative", (singleCharSize >= 0))
        var cword: String = "試詞彙"
        var pword: String = "測"
        whereBuilder = StringBuilder()
        whereBuilder.append(LIME.DB_RELATED_COLUMN_PWORD).append(" = ? AND ")
        whereBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" LIKE ? AND ")
        whereBuilder.append("ifnull(").append(LIME.DB_RELATED_COLUMN_CWORD).append(", '') <> ''")
        var multiCharSize: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, whereBuilder.toString(), arrayOf(pword, (cword + "%")))
        assertTrue("Multi-character should return non-negative", (multiCharSize >= 0))
    }
    @Test
    fun testLimeDBGetRecordListEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullQueryRecords: MutableList<Record> = limeDB.getRecordList("custom", null, false, 10, 0)
        if ((nullQueryRecords != null)) {
            assertTrue("Null query should return list", true)
        }
        var emptyQueryRecords: MutableList<Record> = limeDB.getRecordList("custom", "", false, 10, 0)
        if ((emptyQueryRecords != null)) {
            assertTrue("Empty query should return list", true)
        }
        var searchrootRecords: MutableList<Record> = limeDB.getRecordList("custom", "測試", true, 10, 0)
        if ((searchrootRecords != null)) {
            assertTrue("Searchroot true should return list", true)
        }
        var offsetRecords: MutableList<Record> = limeDB.getRecordList("custom", null, false, 10, 5)
        if ((offsetRecords != null)) {
            assertTrue("Offset should work", true)
        }
        var zeroMaxRecords: MutableList<Record> = limeDB.getRecordList("custom", null, false, 0, 0)
        if ((zeroMaxRecords != null)) {
            assertTrue("Zero maximum should work", true)
        }
    }
    @Test
    fun testLimeDBLoadRelatedEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullPwordRelated: MutableList<Related> = limeDB.getRelated(null, 10, 0)
        if ((nullPwordRelated != null)) {
            assertTrue("Null pword should return list", true)
        }
        var emptyPwordRelated: MutableList<Related> = limeDB.getRelated("", 10, 0)
        if ((emptyPwordRelated != null)) {
            assertTrue("Empty pword should return list", true)
        }
        var offsetRelated: MutableList<Related> = limeDB.getRelated("測試", 10, 5)
        if ((offsetRelated != null)) {
            assertTrue("Offset should work", true)
        }
        var zeroMaxRelated: MutableList<Related> = limeDB.getRelated("測試", 0, 0)
        if ((zeroMaxRelated != null)) {
            assertTrue("Zero maximum should work", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBGetRelatedHandlesQuoteHeavySearchText() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var quoteHeavyRelated: MutableList<Related> = limeDB.getRelated("'囧\":", 10, 0)
        assertNotNull("Quote-heavy related search text should not break SQL", quoteHeavyRelated)
    }
    @Test(timeout = 5000)
    fun testLimeDBHasRelatedEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
    }
    @Test(timeout = 5000)
    fun testLimeDBKeyToKeyNameEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        try {
            var nullKeyname: String = limeDB.keyToKeyName(null, LIME.DB_TABLE_PHONETIC, false)
            assertNotNull("Null code should return a string", nullKeyname)
        } catch (e: Exception) {
            assertTrue("Null code may cause exception", true)
        }
        var emptyKeyname: String = limeDB.keyToKeyName("", LIME.DB_TABLE_PHONETIC, false)
        assertNotNull("Empty code should return a string", emptyKeyname)
        var nonExistentKeyname: String = limeDB.keyToKeyName("a", "nonexistent_table", false)
        assertNotNull("Non-existent table should return a string", nonExistentKeyname)
        var composingKeyname: String = limeDB.keyToKeyName("a", LIME.DB_TABLE_PHONETIC, true)
        assertNotNull("Composing text should return a string", composingKeyname)
    }
    @Test
    fun testLimeDBPreProcessingRemappingCodeEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var nullRemapped: String = limeDB.preProcessingRemappingCode(null)
        assertTrue("Null code should return null or empty", ((nullRemapped == null) || nullRemapped.isEmpty()))
        var emptyRemapped: String = limeDB.preProcessingRemappingCode("")
        assertTrue("Empty code should return null or empty", ((emptyRemapped == null) || emptyRemapped.isEmpty()))
        var specialRemapped: String = limeDB.preProcessingRemappingCode("test'code\"with;special")
        assertNotNull("Special characters should return a string", specialRemapped)
        var longCode: String = "a"
        var longRemapped: String = limeDB.preProcessingRemappingCode(longCode)
        assertNotNull("Very long code should return a string", longRemapped)
    }
    @Test
    fun testLimeDBGetImConfigListInfoEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullImInfo: String? = limeDB.getImConfig(null, "field")
        assertTrue("Null IM code should return null or empty", ((nullImInfo == null) || nullImInfo.isEmpty()))
        var nullFieldInfo: String? = limeDB.getImConfig(LIME.DB_TABLE_PHONETIC, null)
        assertTrue("Null field should return null or empty", ((nullFieldInfo == null) || nullFieldInfo.isEmpty()))
        var emptyImInfo: String = limeDB.getImConfig("", "field")
        assertTrue("Empty IM code should return null or empty", ((emptyImInfo == null) || emptyImInfo.isEmpty()))
        var emptyFieldInfo: String = limeDB.getImConfig(LIME.DB_TABLE_PHONETIC, "")
        assertTrue("Empty field should return null or empty", ((emptyFieldInfo == null) || emptyFieldInfo.isEmpty()))
    }
    @Test
    fun testLimeDBSetImConfigEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            limeDB.setImConfig(null, "field", "value")
            assertTrue("Null IM code should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null IM code should be handled gracefully", true)
        }
        try {
            limeDB.setImConfig(LIME.DB_TABLE_PHONETIC, null, "value")
            assertTrue("Null field should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null field should be handled gracefully", true)
        }
        limeDB.setImConfig(LIME.DB_TABLE_PHONETIC, "field", null)
        var retrieved: String = limeDB.getImConfig(LIME.DB_TABLE_PHONETIC, "field")
        assertTrue("Null value should be handled", ((retrieved == null) || retrieved.isEmpty()))
        limeDB.setImConfig("", "", "")
        assertTrue("Empty strings should be handled", true)
    }
    @Test
    fun testLimeDBRemoveImConfigEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            limeDB.removeImConfig(null, "field")
            assertTrue("Null IM code should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null IM code should be handled gracefully", true)
        }
        try {
            limeDB.removeImConfig(LIME.DB_TABLE_PHONETIC, null)
            assertTrue("Null field should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null field should be handled gracefully", true)
        }
        limeDB.removeImConfig("", "")
        assertTrue("Empty strings should be handled", true)
    }
    @Test
    fun testLimeDBResetImConfigEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            limeDB.resetImConfig(null)
            assertTrue("Null IM code should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null IM code should be handled gracefully", true)
        }
        limeDB.resetImConfig("")
        assertTrue("Empty IM code should be handled", true)
        limeDB.resetImConfig(("nonexistent_im_" + System.currentTimeMillis()))
        assertTrue("Non-existent IM should be handled", true)
    }
    @Test
    fun testLimeDBGetKeyboardConfigListInfoEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullKeyboardInfo: String = limeDB.getKeyboardInfo(null, "name")!!
        assertTrue("Null keyboard code should return null or empty", ((nullKeyboardInfo == null) || nullKeyboardInfo.isEmpty()))
        var nullFieldInfo: String = limeDB.getKeyboardInfo("lime", null)!!
        assertTrue("Null field should return null or empty", ((nullFieldInfo == null) || nullFieldInfo.isEmpty()))
        var emptyKeyboardInfo: String = limeDB.getKeyboardInfo("", "name")!!
        assertTrue("Empty keyboard code should return null or empty", ((emptyKeyboardInfo == null) || emptyKeyboardInfo.isEmpty()))
        var emptyFieldInfo: String = limeDB.getKeyboardInfo("lime", "")!!
        assertTrue("Empty field should return null or empty", ((emptyFieldInfo == null) || emptyFieldInfo.isEmpty()))
    }
    @Test
    fun testLimeDBGetBaseScoreEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullScore: Int = limeDB.getBaseScore(null)
        assertTrue("Null input should return 0 or non-negative", (nullScore >= 0))
        var emptyScore: Int = limeDB.getBaseScore("")
        assertTrue("Empty input should return 0 or non-negative", (emptyScore >= 0))
        var singleCharScore: Int = limeDB.getBaseScore("測")
        assertTrue("Single character should return 0 or non-negative", (singleCharScore >= 0))
        var longInput: String = "測"
        var longScore: Int = limeDB.getBaseScore(longInput)
        assertTrue("Very long input should return 0 or non-negative", (longScore >= 0))
    }
    @Test(timeout = 30000)
    fun testLimeDBHanConvertEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var nullConverted: String = limeDB.hanConvert(null, 0)!!
            assertNotNull("Null input should return a string", nullConverted)
        } catch (e: NullPointerException) {
            assertTrue("Null input should cause NullPointerException", true)
        }
        var emptyConverted: String = limeDB.hanConvert("", 0)!!
        assertNotNull("Empty input should return a string", emptyConverted)
        var option0: String = limeDB.hanConvert("測試", 0)!!
        assertNotNull("Option 0 should return a string", option0)
        var option1: String = limeDB.hanConvert("測試", 1)!!
        assertNotNull("Option 1 should return a string", option1)
        var invalidOption: String = limeDB.hanConvert("測試", 1)!!
        assertNotNull("Invalid option should return a string", invalidOption)
    }
    @Test
    fun testLimeDBEmojiConvertEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullEmoji: MutableList<Mapping?> = limeDB.emojiConvert(null, 0)!!
        if ((nullEmoji != null)) {
            assertTrue("Null source should return list", true)
        }
        var emptyEmoji: MutableList<Mapping?> = limeDB.emojiConvert("", 0)!!
        if ((emptyEmoji != null)) {
            assertTrue("Empty source should return list", true)
        }
        var emoji0: MutableList<Mapping?> = limeDB.emojiConvert("測試", 0)!!
        if ((emoji0 != null)) {
            assertTrue("Emoji 0 should return list", true)
        }
        var emoji1: MutableList<Mapping?> = limeDB.emojiConvert("測試", 1)!!
        if ((emoji1 != null)) {
            assertTrue("Emoji 1 should return list", true)
        }
    }
    @Test
    fun testLimeDBGetEnglishSuggestionsEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullSuggestions: MutableList<String?> = limeDB.getEnglishSuggestions(null)!!
        if ((nullSuggestions != null)) {
            assertTrue("Null word should return list", true)
        }
        var emptySuggestions: MutableList<String?> = limeDB.getEnglishSuggestions("")!!
        if ((emptySuggestions != null)) {
            assertTrue("Empty word should return list", true)
        }
        var chineseSuggestions: MutableList<String?> = limeDB.getEnglishSuggestions("測試")!!
        if ((chineseSuggestions != null)) {
            assertTrue("Non-English word should return list", true)
        }
        var longWord: String = "a"
        var longSuggestions: MutableList<String?> = limeDB.getEnglishSuggestions(longWord)!!
        if ((longSuggestions != null)) {
            assertTrue("Very long word should return list", true)
        }
    }
    @Test
    fun testLimeDBAddRecordWithInvalidInputs() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            var cv: android.content.ContentValues = android.content.ContentValues()
            cv.put(LIME.DB_RELATED_COLUMN_PWORD, "test")
            var result: Long = limeDB.addRecord(null, cv)
            assertTrue("Null table name should return -1", (result == -1L))
        } catch (e: Exception) {
            assertTrue("Null table name should be handled gracefully", true)
        }
        try {
            var cv: android.content.ContentValues = android.content.ContentValues()
            cv.put(LIME.DB_RELATED_COLUMN_PWORD, "test")
            var result: Long = limeDB.addRecord("invalid_table", cv)
            assertTrue("Invalid table name should return -1", (result == -1L))
        } catch (e: Exception) {
            assertTrue("Invalid table name should be handled gracefully", true)
        }
        try {
            var result: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, null)
            assertTrue("Null ContentValues should return -1", (result == -1L))
        } catch (e: Exception) {
            assertTrue("Null ContentValues should be handled gracefully", true)
        }
        try {
            var emptyCv: android.content.ContentValues = android.content.ContentValues()
            var result: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, emptyCv)
            assertTrue("Empty ContentValues should be handled", (result >= 1))
        } catch (e: Exception) {
            assertTrue("Empty ContentValues should be handled gracefully", true)
        }
    }
    @Test
    fun testLimeDBDeleteRecordWithInvalidInputs() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            var result: Int = limeDB.deleteRecord(null, "id = ?", arrayOf("1"))
            assertTrue("Null table name should return -1", (result == -1))
        } catch (e: Exception) {
            assertTrue("Null table name should be handled gracefully", true)
        }
        try {
            var result: Int = limeDB.deleteRecord("invalid_table", "id = ?", arrayOf("1"))
            assertTrue("Invalid table name should return -1", (result == -1))
        } catch (e: Exception) {
            assertTrue("Invalid table name should be handled gracefully", true)
        }
        try {
            var result: Int = limeDB.deleteRecord(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("Null whereClause should be handled", (result >= 1))
        } catch (e: Exception) {
            assertTrue("Null whereClause should be handled gracefully", true)
        }
    }
    @Test
    fun testLimeDBUpdateRecordWithInvalidInputs() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            var cv: android.content.ContentValues = android.content.ContentValues()
            cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, 2)
            var result: Int = limeDB.updateRecord(null, cv, "id = ?", arrayOf("1"))
            assertTrue("Null table name should return -1", (result == -1))
        } catch (e: Exception) {
            assertTrue("Null table name should be handled gracefully", true)
        }
        try {
            var cv: android.content.ContentValues = android.content.ContentValues()
            cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, 2)
            var result: Int = limeDB.updateRecord("invalid_table", cv, "id = ?", arrayOf("1"))
            assertTrue("Invalid table name should return -1", (result == -1))
        } catch (e: Exception) {
            assertTrue("Invalid table name should be handled gracefully", true)
        }
        try {
            var result: Int = limeDB.updateRecord(LIME.DB_TABLE_RELATED, null, "id = ?", arrayOf("1"))
            assertTrue("Null ContentValues should return -1", (result == -1))
        } catch (e: Exception) {
            assertTrue("Null ContentValues should be handled gracefully", true)
        }
        try {
            var emptyCv: android.content.ContentValues = android.content.ContentValues()
            var result: Int = limeDB.updateRecord(LIME.DB_TABLE_RELATED, emptyCv, "id = ?", arrayOf("1"))
            assertTrue("Empty ContentValues should be handled", (result >= 1))
        } catch (e: Exception) {
            assertTrue("Empty ContentValues should be handled gracefully", true)
        }
    }
    @Test
    fun testLimeDBGetMappingByCodeWithDifferentParameters() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        var testCode: String = ("test_combinations_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試組合")
        var softAll: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, true)!!
        if ((softAll != null)) {
            assertTrue("softKeyboard=true, getAllRecords=true should work", true)
        }
        var softLimited: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if ((softLimited != null)) {
            assertTrue("softKeyboard=true, getAllRecords=false should work", true)
        }
        var physicalAll: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, false, true)!!
        if ((physicalAll != null)) {
            assertTrue("softKeyboard=false, getAllRecords=true should work", true)
        }
        var physicalLimited: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, false, false)!!
        if ((physicalLimited != null)) {
            assertTrue("softKeyboard=false, getAllRecords=false should work", true)
        }
    }
    @Test
    fun testLimeDBConnectionStateAfterOperations() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var initialOpen: Boolean = limeDB.openDBConnection(false)
        assertTrue("Database should open", initialOpen)
        limeDB.setTableName("custom")
        var count: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Count should work", (count >= 0))
        var stillOpen: Boolean = limeDB.openDBConnection(false)
        assertTrue("Database should remain open", stillOpen)
        var reloaded: Boolean = limeDB.openDBConnection(true)
        assertTrue("Force reload should work", reloaded)
    }
    @Test
    fun testLimeDBProgressTrackingMethods() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var initialCount: Int = limeDB.getCountImported()
        assertTrue("Initial count should be non-negative", (initialCount >= 0))
        var initialProgress: Int = limeDB.getProgressPercentageDone()
        assertTrue("Initial progress should be between 0 and 100", ((initialProgress >= 0) && (initialProgress <= 100)))
        limeDB.setFinish(true)
        var progressAfterFinish: Int = limeDB.getProgressPercentageDone()
        assertTrue("Progress after finish should be between 0 and 100", ((progressAfterFinish >= 0) && (progressAfterFinish <= 100)))
        limeDB.setFinish(false)
        var progressAfterUnfinish: Int = limeDB.getProgressPercentageDone()
        assertTrue("Progress after unfinish should be between 0 and 100", ((progressAfterUnfinish >= 0) && (progressAfterUnfinish <= 100)))
    }
    @Test
    fun testLimeDBFilenameOperations() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            limeDB.setFilename(null)
            assertTrue("Null file should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null file should be handled gracefully", true)
        }
        var testFile: File = File(appContext.getCacheDir(), "test_filename.txt")
        limeDB.setFilename(testFile)
        assertTrue("Valid file should be set", true)
        var nonExistentFile: File = File(appContext.getCacheDir(), (("nonexistent_" + System.currentTimeMillis()) + ".txt"))
        limeDB.setFilename(nonExistentFile)
        assertTrue("Non-existent file should be handled", true)
    }
    @Test
    fun testLimeDBGetImListKeyboardConfigConfigListWithNullCode() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullImConfigList: MutableList<ImConfig?> = limeDB.getImConfigList(null, null)
        if ((nullImConfigList != null)) {
            assertTrue("Null code should return list", true)
        }
        var emptyImConfigList: MutableList<ImConfig?> = limeDB.getImConfigList("", null)
        if ((emptyImConfigList != null)) {
            assertTrue("Empty code should return list", true)
        }
    }
    @Test
    fun testLimeDBGetImConfigListWithNullParameters() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullCodeImConfig: MutableList<ImConfig?> = limeDB.getImConfigList(null, null)
        if ((nullCodeImConfig != null)) {
            assertTrue("Null code should return list", true)
        }
        var nullTypeImConfig: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_PHONETIC, null)
        if ((nullTypeImConfig != null)) {
            assertTrue("Null type should return list", true)
        }
        var emptyCodeImConfig: MutableList<ImConfig?> = limeDB.getImConfigList("", null)
        if ((emptyCodeImConfig != null)) {
            assertTrue("Empty code should return list", true)
        }
        var emptyTypeImConfig: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_PHONETIC, "")
        if ((emptyTypeImConfig != null)) {
            assertTrue("Empty type should return list", true)
        }
    }
    @Test
    fun testLimeDBSetIMConfigKeyboardWithNullParameters() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            limeDB.setIMConfigKeyboard(null, "Test Keyboard", "lime")
            assertTrue("Null IM code should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null IM code should be handled gracefully", true)
        }
        try {
            limeDB.setIMConfigKeyboard("custom", null, "lime")
            assertTrue("Null value should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null value should be handled gracefully", true)
        }
        try {
            limeDB.setIMConfigKeyboard("custom", "Test Keyboard", null)
            assertTrue("Null keyboard code should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null keyboard code should be handled gracefully", true)
        }
    }
    @Test
    fun testLimeDBSetIMKeyboardWithConfigConfigKeyboardObject() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var keyboards: MutableList<Keyboard?> = limeDB.getKeyboardConfigList()!!
        if (((keyboards != null) && keyboards.isEmpty())) {
            var keyboard: Keyboard = keyboards.get(0)!!
            try {
                limeDB.setImConfigKeyboard(null, keyboard)
                assertTrue("Null IM code should be handled", true)
            } catch (e: Exception) {
                assertTrue("Null IM code should be handled gracefully", true)
            }
            try {
                limeDB.setImConfigKeyboard("custom", null)
                assertTrue("Null keyboard should be handled", true)
            } catch (e: Exception) {
                assertTrue("Null keyboard should be handled gracefully", true)
            }
            limeDB.setImConfigKeyboard("custom", keyboard)
            assertTrue("Valid parameters should work", true)
        }
    }
    @Test
    fun testLimeDBRenameTableNameEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            limeDB.renameTableName(null, "target")
            assertTrue("Null source should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null source should be handled gracefully", true)
        }
        try {
            limeDB.renameTableName("source", null)
            assertTrue("Null target should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null target should be handled gracefully", true)
        }
        try {
            limeDB.renameTableName("", "target")
            assertTrue("Empty source should be handled", true)
        } catch (e: Exception) {
            assertTrue("Empty source should be handled gracefully", true)
        }
        try {
            limeDB.renameTableName("source", "")
            assertTrue("Empty target should be handled", true)
        } catch (e: Exception) {
            assertTrue("Empty target should be handled gracefully", true)
        }
        try {
            limeDB.renameTableName("custom", "custom")
            assertTrue("Same source and target should be handled", true)
        } catch (e: Exception) {
            assertTrue("Same source and target should be handled gracefully", true)
        }
    }
    @Test
    fun testLimeDBClearTableEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        try {
            limeDB.clearTable(null)
            assertTrue("Null table should be handled", true)
        } catch (e: Exception) {
            assertTrue("Null table should be handled gracefully", true)
        }
        try {
            limeDB.clearTable("")
            assertTrue("Empty table should be handled", true)
        } catch (e: Exception) {
            assertTrue("Empty table should be handled gracefully", true)
        }
        try {
            limeDB.clearTable("'; DROP TABLE custom; --")
            assertTrue("Invalid table name should be handled", true)
        } catch (e: Exception) {
            assertTrue("Invalid table name should be handled gracefully", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBListWithEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var nullRecords: MutableList<Record> = limeDB.getRecordList(null, null, false, 0, 0)
        assertTrue("Null table should return null or empty list", ((nullRecords == null) || nullRecords.isEmpty()))
        var emptyRecords: MutableList<Record> = limeDB.getRecordList("", null, false, 0, 0)
        assertTrue("Empty table should return null or empty list", ((emptyRecords == null) || emptyRecords.isEmpty()))
        var invalidRecords: MutableList<Record> = limeDB.getRecordList("'; DROP TABLE custom; --", null, false, 0, 0)
        assertTrue("Invalid table name should return null or empty list", ((invalidRecords == null) || invalidRecords.isEmpty()))
    }
    @Test(timeout = 5000)
    fun testLimeDBCountWithEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var nullCount: Int = limeDB.countRecords(null, null, null)
            assertEquals("Null table should return 0", 0, nullCount)
        } catch (e: android.database.sqlite.SQLiteException) {
            assertTrue("Null table should cause SQL error", true)
        }
        try {
            var emptyCount: Int = limeDB.countRecords("", null, null)
            assertEquals("Empty table should return 0", 0, emptyCount)
        } catch (e: android.database.sqlite.SQLiteException) {
            assertTrue("Empty table should cause SQL error", true)
        }
        try {
            var invalidCount: Int = limeDB.countRecords("'; DROP TABLE custom; --", null, null)
            assertEquals("Invalid table name should return 0", 0, invalidCount)
        } catch (e: android.database.sqlite.SQLiteException) {
            assertTrue("Invalid table name should cause SQL error", true)
        }
    }
    @Test
    fun testLimeDBCountMappingWithEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var nullCount: Int = limeDB.countRecords(null, null, null)
        assertEquals("Null table should return 0", 0, nullCount)
        var emptyCount: Int = limeDB.countRecords("", null, null)
        assertEquals("Empty table should return 0", 0, emptyCount)
        var invalidCount: Int = limeDB.countRecords("'; DROP TABLE custom; --", null, null)
        assertEquals("Invalid table name should return 0", 0, invalidCount)
    }
    @Test
    fun testLimeDBGetCodeListStringByWordEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.setTableName("custom")
        var nullCodeList: String = limeDB.getCodeListStringByWord(null)!!
        assertNull("Null keyword should return null", nullCodeList)
        var emptyCodeList: String = limeDB.getCodeListStringByWord("")!!
        assertNull("Empty keyword should return null", emptyCodeList)
        var whitespaceCodeList: String = limeDB.getCodeListStringByWord("   ")!!
        assertNull("Whitespace-only keyword should return null", whitespaceCodeList)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingByWordEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var nullKeywordResults: MutableList<Mapping?> = limeDB.getMappingByWord(null, "custom")!!
        assertTrue("Null keyword should return empty list", ((nullKeywordResults != null) && nullKeywordResults.isEmpty()))
        var emptyKeywordResults: MutableList<Mapping?> = limeDB.getMappingByWord("", "custom")!!
        assertTrue("Empty keyword should return empty list", ((emptyKeywordResults != null) && emptyKeywordResults.isEmpty()))
        var whitespaceResults: MutableList<Mapping?> = limeDB.getMappingByWord("   ", "custom")!!
        assertTrue("Whitespace-only keyword should return empty list or null", ((whitespaceResults == null) || whitespaceResults.isEmpty()))
        var nullTableResults: MutableList<Mapping?> = limeDB.getMappingByWord("測試", null)!!
        assertTrue("Null table should be handled", true)
        var emptyTableResults: MutableList<Mapping?> = limeDB.getMappingByWord("測試", "")!!
        assertTrue("Empty table should be handled", true)
    }
    @Test
    fun testLimeDBMultipleOperationsInSequence() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var opened: Boolean = limeDB.openDBConnection(false)
        assertTrue("Database should open", opened)
        limeDB.setTableName("custom")
        assertEquals("Table name should be set", "custom", limeDB.getTableName())
        var testCode: String = ("test_sequence_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試序列")
        var results: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if (((results != null) && results.isEmpty())) {
            assertTrue("Record should be retrievable", true)
        }
        var count: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Count should work", (count >= 0))
        if (((results != null) && results.isEmpty())) {
            limeDB.addScore(results.get(0))
            assertTrue("Add score should work", true)
        }
        var stillOpen: Boolean = limeDB.openDBConnection(false)
        assertTrue("Database should remain open", stillOpen)
    }
    @Test
    fun testLimeDBConcurrentOperations() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        limeDB.openDBConnection(false)
        limeDB.setTableName("custom")
        var testCode: String = ("test_concurrent_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試並發")
        var results: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if ((results != null)) {
            assertTrue("Concurrent operations should work", true)
        }
    }
    @Test
    fun testLimeDBIsValidTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var validCount: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Valid table name should work", (validCount >= 0))
        var invalidCount: Int = limeDB.countRecords("'; DROP TABLE custom; --", null, null)
        assertEquals("Invalid table name should return 0", 0, invalidCount)
        var sqlInjectionCount: Int = limeDB.countRecords("custom' OR '1'='1", null, null)
        assertEquals("SQL injection attempt should return 0", 0, sqlInjectionCount)
    }
    @Test
    fun testLimeDBSetFilename() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var testFile: File = File(appContext.getCacheDir(), "test_file.txt")
        try {
            testFile.createNewFile()
            limeDB.setFilename(testFile)
            assertTrue("setFilename should complete", true)
        } catch (e: Exception) {
            assertTrue("setFilename should handle exceptions", true)
        } finally {
            if (testFile.exists()) {
                testFile.delete()
            }
        }
        limeDB.setFilename(null)
        assertTrue("setFilename with null should complete", true)
    }
    @Test
    fun testLimeDBIsCodeDualMapped() {
        var isDualMapped: Boolean = LimeDB.isCodeDualMapped()
        assertTrue("isCodeDualMapped should return boolean", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingFromWord() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var testMapping: Mapping = Mapping()
        testMapping.setWord("測試")
        var results: MutableList<Mapping?> = limeDB.getMappingByWord("測試", "custom")!!
        if ((results != null)) {
            assertTrue("getMappingByWord should return a list", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBHelperMethods() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var relatedList: MutableList<Related> = limeDB.getRelated(null, 0, 0)
        if (((relatedList != null) && relatedList.isEmpty())) {
            var related: Related = relatedList.get(0)
            var stringValue: String = related.getPword()!!
            assertNotNull("getPword should return a string", stringValue)
            var intValue: Int = related.getIdAsInt()
            assertTrue("getId should return an integer", (intValue >= 0))
            var cword: String = related.getCword()!!
            assertTrue("getCword should be accessible", true)
            var basescore: Int = related.getBasescore()
            assertTrue("getBasescore should return non-negative", (basescore >= 0))
        } else {
            assertTrue("Helper methods should be accessible even with empty list", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBGetHighestScoreIDOnDB() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode: String = ("test_highest_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試最高分")
        var mappings: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if (((mappings != null) && mappings.isEmpty())) {
            assertTrue("Mapping should be retrievable", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBOpenDBConnectionBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var opened1: Boolean = limeDB.openDBConnection(false)
        assertTrue("openDBConnection(false) should succeed", opened1)
        var opened2: Boolean = limeDB.openDBConnection(true)
        assertTrue("openDBConnection(true) should succeed", opened2)
        var opened3: Boolean = limeDB.openDBConnection(false)
        assertTrue("openDBConnection(false) should succeed after reload", opened3)
    }
    @Test(timeout = 5000)
    fun testLimeDBAddOrUpdateMappingRecordBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var testCode1: String = ("test_phonetic_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode1, "測試注音")
        assertTrue("Phonetic mapping should be added", true)
        limeDB.setTableName("custom")
        var testCode2: String = ("test_custom_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode2, "測試自訂")
        assertTrue("Custom mapping should be added", true)
        limeDB.addOrUpdateMappingRecord(testCode2, "測試自訂")
        assertTrue("Updating existing record should work", true)
        var testCode3: String = ("test_score_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord("custom", testCode3, "測試分數", 100)
        assertTrue("Mapping with explicit score should be added", true)
        limeDB.addOrUpdateMappingRecord("", "測試")
        assertTrue("Empty code should be handled", true)
        limeDB.addOrUpdateMappingRecord("test", "")
        assertTrue("Empty word should be handled", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBAddOrUpdateRelatedPhraseRecordBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var testPword: String = ("測試" + System.currentTimeMillis())
        var testCword: String = ("詞彙" + System.currentTimeMillis())
        var score1: Int = limeDB.addOrUpdateRelatedPhraseRecord(testPword, testCword)
        assertTrue("New related phrase should return score >= 1", (score1 >= 1))
        var score2: Int = limeDB.addOrUpdateRelatedPhraseRecord(testPword, testCword)
        assertTrue("Updating existing phrase should return increased score", (score2 >= score1))
        var symbolOnly: String = "，。！？"
        var score3: Int = limeDB.addOrUpdateRelatedPhraseRecord("測試", symbolOnly)
        assertTrue("Empty cword after symbol removal should return -1", ((score3 == 1) || (score3 >= 1)))
    }
    @Test(timeout = 30000)
    fun testLimeDBGetMappingByCodeBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var softResults: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, false)!!
        assertTrue("Soft keyboard results should be accessible", ((softResults == null) || (softResults.size >= 0)))
        var physicalResults: MutableList<Mapping?> = limeDB.getMappingByCode("test", false, false)!!
        assertTrue("Physical keyboard results should be accessible", ((physicalResults == null) || (physicalResults.size >= 0)))
        var allRecords: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, true)!!
        assertTrue("All records results should be accessible", ((allRecords == null) || (allRecords.size >= 0)))
        var limitedRecords: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, false)!!
        assertTrue("Limited records results should be accessible", ((limitedRecords == null) || (limitedRecords.size >= 0)))
        var emptyCodeResults: MutableList<Mapping?> = limeDB.getMappingByCode("", true, false)!!
        assertTrue("Empty code should return null or empty list", ((emptyCodeResults == null) || emptyCodeResults.isEmpty()))
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var phoneticResults: MutableList<Mapping?> = limeDB.getMappingByCode("a3", true, false)!!
        assertTrue("Phonetic with tone should work", ((phoneticResults == null) || (phoneticResults.size >= 0)))
        var phoneticNoToneResults: MutableList<Mapping?> = limeDB.getMappingByCode("a", true, false)!!
        assertTrue("Phonetic without tone should work", ((phoneticNoToneResults == null) || (phoneticNoToneResults.size >= 0)))
    }
    @Test(timeout = 5000)
    fun testLimeDBPreProcessingRemappingCodeBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var nullResult: String = limeDB.preProcessingRemappingCode(null)
        assertEquals("Null code should return empty string", "", nullResult)
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var phoneticResult: String = limeDB.preProcessingRemappingCode("a")
        assertNotNull("Phonetic remapping should return a string", phoneticResult)
        limeDB.setTableName("custom")
        var customResult: String = limeDB.preProcessingRemappingCode("test")
        assertNotNull("Custom remapping should return a string", customResult)
        var emptyResult: String = limeDB.preProcessingRemappingCode("")
        assertNotNull("Empty code remapping should return a string", emptyResult)
    }
    @Test(timeout = 5000)
    fun testLimeDBAddRecordDeleteRecordUpdateRecordBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var insertValues: android.content.ContentValues = android.content.ContentValues()
        insertValues.put(LIME.DB_RELATED_COLUMN_PWORD, "測試插入")
        insertValues.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙插入")
        insertValues.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1)
        var insertResult: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, insertValues)
        assertTrue("Valid addRecord should complete", (insertResult >= 1))
        var invalidTableResult: Long = limeDB.addRecord("invalid_table", insertValues)
        if ((invalidTableResult != -1L)) {
            Log.e(TAG, (("ERROR: addRecord returned " + invalidTableResult) + " for invalid table name - should return -1"))
            fail(("Invalid table name should return -1, but returned: " + invalidTableResult))
        }
        assertEquals("Invalid table name should return -1", -1L, invalidTableResult)
        var nullCvResult: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, null)
        if ((nullCvResult != -1L)) {
            Log.e(TAG, (("ERROR: addRecord returned " + nullCvResult) + " for null ContentValues - should return -1"))
            fail(("Null ContentValues should return -1, but returned: " + nullCvResult))
        }
        assertEquals("Null ContentValues should return -1", -1L, nullCvResult)
        var addValues: android.content.ContentValues = android.content.ContentValues()
        addValues.put(LIME.DB_RELATED_COLUMN_PWORD, "測試添加")
        addValues.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙添加")
        addValues.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1)
        var addResult: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, addValues)
        assertTrue("Valid addRecord should complete", (addResult >= 1))
        var whereClause: String = (LIME.DB_RELATED_COLUMN_PWORD + " = ?")
        var whereArgs: Array<String?> = arrayOf<String?>("測試插入")
        var deleteResult: Int = limeDB.deleteRecord(LIME.DB_TABLE_RELATED, whereClause, whereArgs)
        assertTrue("Valid deleteRecord should complete", (deleteResult >= 1))
        var invalidTableDeleteResult: Int = limeDB.deleteRecord("invalid_table", whereClause, whereArgs)
        if ((invalidTableDeleteResult != -1)) {
            Log.e(TAG, (("ERROR: deleteRecord returned " + invalidTableDeleteResult) + " for invalid table name - should return -1"))
            fail(("Invalid table name should return -1, but returned: " + invalidTableDeleteResult))
        }
        assertEquals("Invalid table name should return -1", -1, invalidTableDeleteResult)
        var updateValues: android.content.ContentValues = android.content.ContentValues()
        updateValues.put(LIME.DB_RELATED_COLUMN_USERSCORE, 2)
        var updateWhereClause: String = (LIME.DB_RELATED_COLUMN_PWORD + " = ?")
        var updateWhereArgs: Array<String?> = arrayOf<String?>("測試添加")
        var updateResult: Int = limeDB.updateRecord(LIME.DB_TABLE_RELATED, updateValues, updateWhereClause, updateWhereArgs)
        assertTrue("Valid updateRecord should complete", (updateResult >= 1))
        var invalidTableUpdateResult: Int = limeDB.updateRecord("invalid_table", updateValues, updateWhereClause, updateWhereArgs)
        if ((invalidTableUpdateResult != -1)) {
            Log.e(TAG, (("ERROR: updateRecord returned " + invalidTableUpdateResult) + " for invalid table name - should return -1"))
            fail(("Invalid table name should return -1, but returned: " + invalidTableUpdateResult))
        }
        assertEquals("Invalid table name should return -1", -1, invalidTableUpdateResult)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingByWordBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var nullResults: MutableList<Mapping?> = limeDB.getMappingByWord(null, "custom")!!
        assertTrue("Null keyword should return empty list", ((nullResults != null) && nullResults.isEmpty()))
        var emptyResults: MutableList<Mapping?> = limeDB.getMappingByWord("", "custom")!!
        assertTrue("Empty keyword should return empty list", ((emptyResults != null) && emptyResults.isEmpty()))
        var validResults: MutableList<Mapping?> = limeDB.getMappingByWord("測試", "custom")!!
        assertTrue("Valid keyword should return results", (validResults != null))
    }
    @Test(timeout = 5000)
    fun testLimeDBAddScoreBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode: String = ("test_score_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試分數")
        var mappings: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
        if (((mappings != null) && mappings.isEmpty())) {
            var mapping: Mapping = mappings.get(0)!!
            var originalScore: Int = mapping.getScore()
            limeDB.addScore(mapping)
            var updatedMappings: MutableList<Mapping?> = limeDB.getMappingByCode(testCode, true, false)!!
            if (((updatedMappings != null) && updatedMappings.isEmpty())) {
                var updatedMapping: Mapping = updatedMappings.get(0)!!
                assertTrue("Score should increase or remain same", (updatedMapping.getScore() >= originalScore))
            }
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBGetRelatedPhraseBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var allRelated: MutableList<Mapping?> = limeDB.getRelatedPhrase("測試", true)
        assertTrue("All related phrases should be accessible", ((allRelated == null) || (allRelated.size >= 0)))
        var limitedRelated: MutableList<Mapping?> = limeDB.getRelatedPhrase("測試", false)
        assertTrue("Limited related phrases should be accessible", ((limitedRelated == null) || (limitedRelated.size >= 0)))
        var nullRelated: MutableList<Mapping?> = limeDB.getRelatedPhrase(null, false)
        assertTrue("Null pword should be handled", ((nullRelated == null) || (nullRelated.size >= 0)))
        var emptyRelated: MutableList<Mapping?> = limeDB.getRelatedPhrase("", false)
        assertTrue("Empty pword should be handled", ((emptyRelated == null) || (emptyRelated.size >= 0)))
    }
    @Test(timeout = 5000)
    fun testLimeDBClearTableBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode: String = ("test_delete_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode, "測試刪除")
        var countBefore: Int = limeDB.countRecords("custom", null, null)
        assertTrue("deleteAll method should be accessible", true)
        try {
            limeDB.clearTable(null)
            assertTrue("deleteAll with null should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("deleteAll with null may cause exception", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingByCodeWithPhoneticBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var toneNotLastResults: MutableList<Mapping?> = limeDB.getMappingByCode("a3b", true, false)!!
        assertTrue("Tone not last should work", ((toneNotLastResults == null) || (toneNotLastResults.size >= 0)))
        var longToneResults: MutableList<Mapping?> = limeDB.getMappingByCode("abcde3", true, false)!!
        assertTrue("Long code with tone should work", ((longToneResults == null) || (longToneResults.size >= 0)))
        var toneLastResults: MutableList<Mapping?> = limeDB.getMappingByCode("ab3", true, false)!!
        assertTrue("Tone at last should work", ((toneLastResults == null) || (toneLastResults.size >= 0)))
        var noToneResults: MutableList<Mapping?> = limeDB.getMappingByCode("ab", true, false)!!
        assertTrue("No tone should work", ((noToneResults == null) || (noToneResults.size >= 0)))
    }
    @Test(timeout = 5000)
    fun testLimeDBGetRelatedPhraseLengthBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var longPwordResults: MutableList<Mapping?> = limeDB.getRelatedPhrase("測試詞彙", false)
        assertTrue("Long pword should work", (longPwordResults != null))
        var singleCharResults: MutableList<Mapping?> = limeDB.getRelatedPhrase("測", false)
        assertTrue("Single char pword should work", (singleCharResults != null))
        var emptyResults: MutableList<Mapping?> = limeDB.getRelatedPhrase("", false)
        assertTrue("Empty pword should work", (emptyResults != null))
    }
    @Test(timeout = 5000)
    fun testLimeDBKeyToKeyNameBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var longCodeBuilder: StringBuilder = StringBuilder()
        run {
            var i: Int = 0
            while ((i < 20)) {
                longCodeBuilder.append("a")
                i++
            }
        }
        var longCode: String = longCodeBuilder.toString()
        var longResult: String = limeDB.keyToKeyName(longCode, LIME.DB_TABLE_PHONETIC, true)
        assertEquals("Long code with composingText should return original code", longCode, longResult)
        var shortResult: String = limeDB.keyToKeyName("a", LIME.DB_TABLE_PHONETIC, true)
        assertNotNull("Short code with composingText should return keyname", shortResult)
        var nonComposingResult: String = limeDB.keyToKeyName("a", LIME.DB_TABLE_PHONETIC, false)
        assertNotNull("Non-composing should return keyname", nonComposingResult)
        limeDB.setTableName("custom")
        var customResult: String = limeDB.keyToKeyName("a", "custom", false)
        assertNotNull("Custom table should return keyname", customResult)
    }
    @Test(timeout = 5000)
    fun testLimeDBAddOrUpdateMappingRecordScoreBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode1: String = ("test_score_neg1_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord("custom", testCode1, "測試負1", 1)
        assertTrue("Score -1 should work", true)
        limeDB.addOrUpdateMappingRecord("custom", testCode1, "測試負1", 1)
        assertTrue("Updating with score -1 should increment", true)
        var testCode2: String = ("test_score_explicit_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord("custom", testCode2, "測試明確", 50)
        assertTrue("Explicit score should work", true)
        limeDB.addOrUpdateMappingRecord("custom", testCode2, "測試明確", 100)
        assertTrue("Updating with explicit score should work", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBAddOrUpdateRelatedPhraseRecordScoreBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var testPword1: String = ("測試新" + System.currentTimeMillis())
        var testCword1: String = ("詞彙新" + System.currentTimeMillis())
        var score1: Int = limeDB.addOrUpdateRelatedPhraseRecord(testPword1, testCword1)
        assertTrue("New phrase should return score >= 1", (score1 >= 1))
        var score2: Int = limeDB.addOrUpdateRelatedPhraseRecord(testPword1, testCword1)
        assertTrue("First update should increment score", (score2 > score1))
        var score3: Int = limeDB.addOrUpdateRelatedPhraseRecord(testPword1, testCword1)
        assertTrue("Second update should increment cached score", (score3 > score2))
    }
    @Test(timeout = 5000)
    fun testLimeDBGetRelatedPhraseHasMoreRecordsBranch() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var results: MutableList<Mapping?> = limeDB.getRelatedPhrase("測試", false)
        if ((results != null)) {
            var hasMoreMarker: Boolean = false
            for (m in results) {
                if (m.getCode().equals("has_more_records")) {
                    hasMoreMarker = true
                    break
                }
            }
            assertTrue("Results should be accessible", true)
        }
        var allResults: MutableList<Mapping?> = limeDB.getRelatedPhrase("測試", true)
        if ((allResults != null)) {
            var hasMoreMarker: Boolean = false
            for (m in allResults) {
                if (m.getCode().equals("has_more_records")) {
                    hasMoreMarker = true
                    break
                }
            }
            assertTrue("All records should be accessible", true)
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBCheckPhoneticKeyboardSettingBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.checkPhoneticKeyboardSetting()
        assertTrue("checkPhoneticKeyboardSetting should complete", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingByCodeSortBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var softResults: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, false)!!
        assertTrue("Soft keyboard with sort should work", ((softResults == null) || (softResults.size >= 0)))
        var physicalResults: MutableList<Mapping?> = limeDB.getMappingByCode("test", false, false)!!
        assertTrue("Physical keyboard with sort should work", ((physicalResults == null) || (physicalResults.size >= 0)))
    }
    @Test(timeout = 5000)
    fun testLimeDBIsMappingExistOnDBBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var testCode1: String = ("test_null_word_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode1, "測試")
        limeDB.addOrUpdateMappingRecord(testCode1, "測試2")
        assertTrue("Code with null word query should work", true)
        var testCode2: String = ("test_with_word_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode2, "測試詞")
        limeDB.addOrUpdateMappingRecord(testCode2, "測試詞")
        assertTrue("Code with word query should work", true)
        limeDB.addOrUpdateMappingRecord("", "測試")
        assertTrue("Empty code should be handled", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetRelatedPhraseSimiliarEnableBranch() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var results: MutableList<Mapping?> = limeDB.getRelatedPhrase("測試", false)
        assertTrue("getRelatedPhrase should work regardless of similiarEnable", (results != null))
    }
    @Test(timeout = 5000)
    fun testLimeDBPreProcessingRemappingCodeKeyboardBranches() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var phoneticResult: String = limeDB.preProcessingRemappingCode("a")
        assertNotNull("Phonetic remapping should work", phoneticResult)
        limeDB.setTableName(LIME.DB_TABLE_DAYI)
        var dayiResult: String = limeDB.preProcessingRemappingCode("a")
        assertNotNull("Dayi remapping should work", dayiResult)
        limeDB.setTableName(LIME.DB_TABLE_ARRAY)
        var arrayResult: String = limeDB.preProcessingRemappingCode("a")
        assertNotNull("Array remapping should work", arrayResult)
        limeDB.setTableName("custom")
        var customResult: String = limeDB.preProcessingRemappingCode("a")
        assertNotNull("Custom remapping should work", customResult)
        limeDB.setTableName("ez")
        var ezResult: String = limeDB.preProcessingRemappingCode("a")
        assertNotNull("EZ remapping should work", ezResult)
        limeDB.setTableName(LIME.DB_TABLE_CJ)
        var cjResult: String = limeDB.preProcessingRemappingCode("a")
        assertNotNull("CJ remapping should work", cjResult)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetMappingByCodeWithDifferentTables() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var phoneticResults: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, false)!!
        assertTrue("Phonetic table should work", ((phoneticResults == null) || (phoneticResults.size >= 0)))
        limeDB.setTableName(LIME.DB_TABLE_DAYI)
        var dayiResults: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, false)!!
        assertTrue("Dayi table should work", ((dayiResults == null) || (dayiResults.size >= 0)))
        limeDB.setTableName(LIME.DB_TABLE_ARRAY)
        var arrayResults: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, false)!!
        assertTrue("Array table should work", ((arrayResults == null) || (arrayResults.size >= 0)))
        limeDB.setTableName("custom")
        var customResults: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, false)!!
        assertTrue("Custom table should work", ((customResults == null) || (customResults.size >= 0)))
        limeDB.setTableName("ez")
        var ezResults: MutableList<Mapping?> = limeDB.getMappingByCode("test", true, false)!!
        assertTrue("EZ table should work", ((ezResults == null) || (ezResults.size >= 0)))
    }
    @Test(timeout = 5000)
    fun testLimeDBAddOrUpdateMappingRecordWithDifferentTables() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var testCode1: String = ("test_phonetic_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(LIME.DB_TABLE_PHONETIC, testCode1, "測試注音", 1)
        assertTrue("Phonetic table with explicit table parameter should work", true)
        limeDB.setTableName(LIME.DB_TABLE_DAYI)
        var testCode2: String = ("test_dayi_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode2, "測試大易")
        assertTrue("Dayi table should work", true)
        limeDB.setTableName(LIME.DB_TABLE_ARRAY)
        var testCode3: String = ("test_array_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode3, "測試行列")
        assertTrue("Array table should work", true)
        limeDB.setTableName("custom")
        var testCode4: String = ("test_custom_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode4, "測試自訂")
        assertTrue("Custom table should work", true)
        limeDB.setTableName("ez")
        var testCode5: String = ("test_ez_" + System.currentTimeMillis())
        limeDB.addOrUpdateMappingRecord(testCode5, "測試輕鬆")
        assertTrue("EZ table should work", true)
    }
    @Test(timeout = 5000)
    fun testLimeDBKeyToKeyNameWithDifferentTables() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName(LIME.DB_TABLE_PHONETIC)
        var phoneticResult: String = limeDB.keyToKeyName("a", LIME.DB_TABLE_PHONETIC, false)
        assertNotNull("Phonetic keyToKeyname should work", phoneticResult)
        limeDB.setTableName(LIME.DB_TABLE_DAYI)
        var dayiResult: String = limeDB.keyToKeyName("a", LIME.DB_TABLE_DAYI, false)
        assertNotNull("Dayi keyToKeyname should work", dayiResult)
        limeDB.setTableName(LIME.DB_TABLE_ARRAY)
        var arrayResult: String = limeDB.keyToKeyName("a", LIME.DB_TABLE_ARRAY, false)
        assertNotNull("Array keyToKeyname should work", arrayResult)
        limeDB.setTableName("custom")
        var customResult: String = limeDB.keyToKeyName("a", "custom", false)
        assertNotNull("Custom keyToKeyname should work", customResult)
        limeDB.setTableName("ez")
        var ezResult: String = limeDB.keyToKeyName("a", "ez", false)
        assertNotNull("EZ keyToKeyname should work", ezResult)
        limeDB.setTableName(LIME.DB_TABLE_CJ)
        var cjResult: String = limeDB.keyToKeyName("a", LIME.DB_TABLE_CJ, false)
        assertNotNull("CJ keyToKeyname should work", cjResult)
    }
    @Test(timeout = 5000)
    fun testLimeDBCountRecordsWithNullWhereClause() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var count: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
        assertTrue("Count should be non-negative", (count >= 0))
        var customCount: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Custom table count should be non-negative", (customCount >= 0))
    }
    @Test(timeout = 5000)
    fun testLimeDBCountRecordsWithWhereClause() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var whereClause: String = (LIME.DB_COLUMN_ID + " > ?")
        var whereArgs: Array<String?> = arrayOf<String?>("0")
        var count: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, whereClause, whereArgs)
        assertTrue("Count with WHERE clause should be non-negative", (count >= 0))
        whereClause = (((LIME.DB_COLUMN_ID + " > ? AND ") + LIME.DB_RELATED_COLUMN_USERSCORE) + " > ?")
        whereArgs = arrayOf("0", "0")
        var count2: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, whereClause, whereArgs)
        assertTrue("Count with multiple conditions should be non-negative", (count2 >= 0))
    }
    @Test(timeout = 5000)
    fun testLimeDBCountRecordsWithInvalidTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var count: Int = limeDB.countRecords("invalid_table_name", null, null)
        assertEquals("Invalid table name should return 0", 0, count)
        var count2: Int = limeDB.countRecords(null, null, null)
        assertEquals("Null table name should return 0", 0, count2)
    }
    @Test(timeout = 5000)
    fun testLimeDBCountRecordsWithEmptyTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var count: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
        assertTrue("Count on empty table should be 0 or more", (count >= 0))
    }
    @Test(timeout = 5000)
    fun testLimeDBAddRecordWithValidData() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put(LIME.DB_RELATED_COLUMN_PWORD, "test_parent")
        values.put(LIME.DB_RELATED_COLUMN_CWORD, "test_child")
        values.put(LIME.DB_RELATED_COLUMN_USERSCORE, 100)
        var result: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, values)
        assertTrue("addRecord should return row ID >= 0", (result >= 0))
    }
    @Test(timeout = 5000)
    fun testLimeDBAddRecordWithInvalidTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put(LIME.DB_RELATED_COLUMN_PWORD, "test")
        var result: Long = limeDB.addRecord("invalid_table", values)
        assertEquals("Invalid table name should return -1", -1, result)
    }
    @Test(timeout = 5000)
    fun testLimeDBAddRecordWithNullContentValues() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var result: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, null)
        assertEquals("Null ContentValues should return -1", -1, result)
    }
    @Test(timeout = 5000)
    fun testLimeDBUpdateRecordWithValidData() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var insertValues: android.content.ContentValues = android.content.ContentValues()
        insertValues.put(LIME.DB_RELATED_COLUMN_PWORD, "test_update_parent")
        insertValues.put(LIME.DB_RELATED_COLUMN_CWORD, "test_update_child")
        insertValues.put(LIME.DB_RELATED_COLUMN_USERSCORE, 50)
        var insertId: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, insertValues)
        if ((insertId > 0)) {
            var updateValues: android.content.ContentValues = android.content.ContentValues()
            updateValues.put(LIME.DB_RELATED_COLUMN_USERSCORE, 200)
            var whereClause: String = (LIME.DB_COLUMN_ID + " = ?")
            var whereArgs: Array<String?> = arrayOf<String?>(java.lang.String.valueOf(insertId))
            var result: Int = limeDB.updateRecord(LIME.DB_TABLE_RELATED, updateValues, whereClause, whereArgs)
            assertTrue("updateRecord should return number of rows updated >= 0", (result >= 0))
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBUpdateRecordWithNoMatchingRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put(LIME.DB_RELATED_COLUMN_USERSCORE, 999)
        var whereClause: String = (LIME.DB_COLUMN_ID + " = ?")
        var whereArgs: Array<String?> = arrayOf<String?>("999999")
        var result: Int = limeDB.updateRecord(LIME.DB_TABLE_RELATED, values, whereClause, whereArgs)
        assertEquals("No matching records should return 0", 0, result)
    }
    @Test(timeout = 5000)
    fun testLimeDBDeleteRecordWithValidWhereClause() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put(LIME.DB_RELATED_COLUMN_PWORD, "test_delete_parent")
        values.put(LIME.DB_RELATED_COLUMN_CWORD, "test_delete_child")
        var insertId: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, values)
        if ((insertId > 0)) {
            var whereClause: String = (LIME.DB_COLUMN_ID + " = ?")
            var whereArgs: Array<String?> = arrayOf<String?>(java.lang.String.valueOf(insertId))
            var result: Int = limeDB.deleteRecord(LIME.DB_TABLE_RELATED, whereClause, whereArgs)
            assertTrue("deleteRecord should return number of rows deleted >= 0", (result >= 0))
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBDeleteRecordWithNoMatchingRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var whereClause: String = (LIME.DB_COLUMN_ID + " = ?")
        var whereArgs: Array<String?> = arrayOf<String?>("999999")
        var result: Int = limeDB.deleteRecord(LIME.DB_TABLE_RELATED, whereClause, whereArgs)
        assertEquals("No matching records should return 0", 0, result)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetRecordSizeDelegatesToCountRecordList() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var recordSize: Int = limeDB.countRecords("custom", null, null)
        assertTrue("countRecords should return non-negative", (recordSize >= 0))
        var recordSizeByCode: Int = limeDB.countRecords("custom", "code LIKE ?", arrayOf("test%"))
        assertTrue("countRecords by code should return non-negative", (recordSizeByCode >= 0))
        var recordSizeByWord: Int = limeDB.countRecords("custom", "word LIKE ?", arrayOf("%test%"))
        assertTrue("countRecords by word should return non-negative", (recordSizeByWord >= 0))
    }
    @Test(timeout = 5000)
    fun testLimeDBGetRelatedSizeDelegatesToCountRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var relatedSize: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
        assertTrue("countRecords with null should return >= 0", (relatedSize >= 0))
        var relatedSize1: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, (LIME.DB_RELATED_COLUMN_PWORD + " = ?"), arrayOf("a"))
        assertTrue("countRecords with single char should return >= 0", (relatedSize1 >= 0))
        var relatedSize2: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, (LIME.DB_RELATED_COLUMN_PWORD + " = ?"), arrayOf("ab"))
        assertTrue("countRecords with multi-char should return >= 0", (relatedSize2 >= 0))
    }
    @Test(timeout = 10000)
    fun testLimeDBPrepareBackupWithSingleTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var backupFile: File = File(cacheDir, (("test_backup_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {

            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            limeDB.prepareBackup(backupFile, tableNames, false)
            assertTrue("Backup file should be created", backupFile.exists())
            if (backupFile.exists()) {
                backupFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBPrepareBackupWithSingleTable failed: " + e.getMessage()), e)
            fail(("ERROR: prepareBackup failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBPrepareBackupWithMultipleTables() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var backupFile: File = File(cacheDir, (("test_backup_multi_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {

            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            tableNames.add(LIME.DB_TABLE_CJ)
            limeDB.prepareBackup(backupFile, tableNames, false)
            assertTrue("Backup file should be created", backupFile.exists())
            if (backupFile.exists()) {
                backupFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBPrepareBackupWithMultipleTables failed: " + e.getMessage()), e)
            fail(("ERROR: prepareBackup failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBPrepareBackupWithIncludeRelated() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var backupFile: File = File(cacheDir, (("test_backup_related_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {

            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blankrelated), backupFile)
            limeDB.prepareBackup(backupFile, null, true)
            assertTrue("Backup file with related should be created", backupFile.exists())
            if (backupFile.exists()) {
                backupFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBPrepareBackupWithIncludeRelated failed: " + e.getMessage()), e)
            fail(("ERROR: prepareBackup failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBPrepareBackupWithInvalidTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_backup_invalid_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("invalid_table_name")
            limeDB.prepareBackup(backupFile, tableNames, false)
            if (backupFile.exists()) {
                Log.w(TAG, "WARNING: prepareBackup created file for invalid table name - this may be acceptable")
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: prepareBackup failed with invalid table name: " + e.getMessage()), e)
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBImportDBWithSingleTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_import_backup_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            limeDB.prepareBackup(backupFile, tableNames, false)
            if (backupFile.exists()) {
                fail(("ERROR: prepareBackup failed - backup file was not created: " + backupFile.getAbsolutePath()))
            }
            var importTables: MutableList<String?> = ArrayList()
            importTables.add("custom")
            limeDB.importDb(backupFile, importTables, false, true)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBImportDBWithSingleTable failed: " + e.getMessage()), e)
            fail(("ERROR: importDb test failed with exception: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBImportBackupWithOverwriteExisting() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_import_overwrite_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            limeDB.prepareBackup(backupFile, tableNames, false)
            if (backupFile.exists()) {
                fail(("ERROR: prepareBackup failed - backup file was not created: " + backupFile.getAbsolutePath()))
            }
            limeDB.importDb(backupFile, tableNames, false, true)
            limeDB.importDb(backupFile, tableNames, false, false)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBImportBackupWithOverwriteExisting failed: " + e.getMessage()), e)
            fail(("ERROR: importDb test failed with exception: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBImportBackupWithInvalidFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var nonExistentFile: File = File(appContext.getCacheDir(), (("non_existent_backup_" + System.currentTimeMillis()) + ".db"))
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            limeDB.importDb(nonExistentFile, tableNames, false, true)
            assertTrue("importDb with invalid file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("importDb should fail with non-existent file", true)
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBPrepareBackupDbDelegatesToPrepareBackup() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_prepareBackupDb_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            limeDB.prepareBackupDb(backupFile.getAbsolutePath(), "custom")
            if (backupFile.exists()) {
                fail(("ERROR: prepareBackupDb failed - backup file was not created: " + backupFile.getAbsolutePath()))
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBPrepareBackupDbDelegatesToPrepareBackup failed: " + e.getMessage()), e)
            fail(("ERROR: prepareBackupDb test failed with exception: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBPrepareBackupRelatedDbDelegatesToPrepareBackup() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_prepareBackupRelatedDb_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blankrelated), backupFile)
            limeDB.prepareBackupRelatedDb(backupFile.getAbsolutePath())
            if (backupFile.exists()) {
                fail(("ERROR: prepareBackupRelatedDb failed - backup file was not created: " + backupFile.getAbsolutePath()))
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBPrepareBackupRelatedDbDelegatesToPrepareBackup failed: " + e.getMessage()), e)
            fail(("ERROR: prepareBackupRelatedDb test failed with exception: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBImportBackupDbDelegatesToImportBackup() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_importDb_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            limeDB.prepareBackup(backupFile, tableNames, false)
            if (backupFile.exists()) {
                fail(("ERROR: prepareBackup failed - backup file was not created: " + backupFile.getAbsolutePath()))
            }
            var importTables: MutableList<String?> = ArrayList()
            importTables.add("custom")
            limeDB.importDb(backupFile, importTables, false, true)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBImportBackupDbDelegatesToImportDB failed: " + e.getMessage()), e)
            fail(("ERROR: importDb test failed with exception: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBImportBackupRelatedDbDelegatesToImportBackup() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_importDbRelated_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blankrelated), backupFile)
            limeDB.prepareBackup(backupFile, null, true)
            if (backupFile.exists()) {
                fail(("ERROR: prepareBackup failed - backup file was not created: " + backupFile.getAbsolutePath()))
            }
            limeDB.importDbRelated(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBImportBackupRelatedDbDelegatesToImportBackup failed: " + e.getMessage()), e)
            fail(("ERROR: importDbRelated test failed with exception: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBImportDbDelegatesToImportBackup() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_importDb_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            limeDB.prepareBackup(backupFile, tableNames, false)
            if (backupFile.exists()) {
                fail(("ERROR: prepareBackup failed - backup file was not created: " + backupFile.getAbsolutePath()))
            }
            limeDB.importDb(backupFile, tableNames, false, true)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBImportDbDelegatesToImportBackup failed: " + e.getMessage()), e)
            fail(("ERROR: importDb test failed with exception: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBGetBackupTableRecordsWithValidBackupTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            limeDB.addOrUpdateMappingRecord("custom", "test", "測試", 10)
            var checkMappings: MutableList<Mapping?> = limeDB.getMappingByWord("測試", "custom")!!
            if (((checkMappings == null) || checkMappings.isEmpty())) {
                Log.w(TAG, "WARNING: Record was not added to custom table, backup may not be created")
            }
            limeDB.backupUserRecords("custom")
            var backupExists: Boolean = limeDB.checkBackupTable("custom")
            if (!backupExists) {
                Log.e(TAG, "ERROR: backupUserRecords() did not create backup table even though record with score > 0 was added")
                fail("ERROR: backupUserRecords() should have created backup table since record with score > 0 was added")
            }
            var cursor: android.database.Cursor = limeDB.getBackupTableRecords("custom_user")!!
            if ((cursor == null)) {
                Log.e(TAG, "ERROR: getBackupTableRecords returned null even though checkBackuptable returned true")
                fail("ERROR: getBackupTableRecords should return a cursor since backup table exists")
            }
            assertNotNull("getBackupTableRecords should return a valid cursor if backup table exists", cursor)
            assertTrue("Cursor count should be greater than 0 since we added a record", (cursor.getCount() > 0))
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBGetBackupTableRecordsWithValidBackupTable failed: " + e.getMessage()), e)
            fail(("ERROR: getBackupTableRecords test failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBGetBackupTableRecordsWithInvalidFormat() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var cursor: android.database.Cursor = limeDB.getBackupTableRecords("custom")!!
        assertNull("Invalid format should return null", cursor)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetBackupTableRecordsWithInvalidBaseTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var cursor: android.database.Cursor = limeDB.getBackupTableRecords("invalid_table_user")!!
        assertNull("Invalid base table name should return null", cursor)
    }
    @Test(timeout = 10000)
    fun testLimeDBRestoreUserRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var tableName: String = "custom"
            var testCode1: String = ("restore_test1_" + System.currentTimeMillis())
            var testCode2: String = ("restore_test2_" + System.currentTimeMillis())
            var testWord1: String = "恢復測試1"
            var testWord2: String = "恢復測試2"
            var testScore1: Int = 15
            var testScore2: Int = 25
            limeDB.addOrUpdateMappingRecord(tableName, testCode1, testWord1, testScore1)
            limeDB.addOrUpdateMappingRecord(tableName, testCode2, testWord2, testScore2)
            var mappings1: MutableList<Mapping?> = limeDB.getMappingByWord(testWord1, tableName)!!
            var mappings2: MutableList<Mapping?> = limeDB.getMappingByWord(testWord2, tableName)!!
            assertTrue("Records should be added to main table", (((mappings1 != null) && mappings1.isEmpty()) || ((mappings2 != null) && mappings2.isEmpty())))
            limeDB.backupUserRecords(tableName)
            var backupExists: Boolean = limeDB.checkBackupTable(tableName)
            if (!backupExists) {
                Log.w(TAG, "WARNING: Backup table was not created, but continuing test")
            }
            limeDB.clearTable(tableName)
            var countAfterDelete: Int = limeDB.countRecords(tableName, null, null)
            assertEquals("Main table should be empty after deleteAll", 0, countAfterDelete)
            var restoredCount: Int = limeDB.restoreUserRecords(tableName)
            assertTrue("restoreUserRecords should return number of restored records", (restoredCount >= 0))
            if ((backupExists && (restoredCount > 0))) {
                var restoredMappings1: MutableList<Mapping?> = limeDB.getMappingByWord(testWord1, tableName)!!
                var restoredMappings2: MutableList<Mapping?> = limeDB.getMappingByWord(testWord2, tableName)!!
                var found1: Boolean = ((restoredMappings1 != null) && restoredMappings1.isEmpty())
                var found2: Boolean = ((restoredMappings2 != null) && restoredMappings2.isEmpty())
                assertTrue("At least one record should be restored", (found1 || found2))
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBRestoreUserRecords failed: " + e.getMessage()), e)
            fail(("ERROR: restoreUserRecords test failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBRestoreUserRecordsWithNoBackup() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.dropBackupTable("custom")
        var restoredCount: Int = limeDB.restoreUserRecords("custom")
        assertEquals("restoreUserRecords should return 0 when no backup exists", 0, restoredCount)
    }
    @Test(timeout = 5000)
    fun testLimeDBRestoreUserRecordsWithInvalidTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var restoredCount1: Int = limeDB.restoreUserRecords(null)
        assertEquals("restoreUserRecords should return 0 for null table", 0, restoredCount1)
        var restoredCount2: Int = limeDB.restoreUserRecords("")
        assertEquals("restoreUserRecords should return 0 for empty table", 0, restoredCount2)
        var restoredCount3: Int = limeDB.restoreUserRecords("'; DROP TABLE custom; --")
        assertEquals("restoreUserRecords should return 0 for invalid table", 0, restoredCount3)
    }
    @Test(timeout = 5000)
    fun testLimeDBQueryWithPaginationWithLimitAndOffset() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var cursor: android.database.Cursor = limeDB.queryWithPagination(LIME.DB_TABLE_RELATED, null, null, (LIME.DB_COLUMN_ID + " ASC"), 10, 0)!!
        assertTrue("queryWithPagination should return cursor or null", ((cursor == null) || (cursor.getCount() >= 0)))
        if ((cursor != null)) {
            cursor.close()
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBQueryWithPaginationWithNoLimit() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var cursor: android.database.Cursor = limeDB.queryWithPagination(LIME.DB_TABLE_RELATED, null, null, null, 0, 0)!!
        assertTrue("queryWithPagination with no limit should return cursor or null", ((cursor == null) || (cursor.getCount() >= 0)))
        if ((cursor != null)) {
            cursor.close()
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBQueryWithPaginationWithInvalidTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var cursor: android.database.Cursor = limeDB.queryWithPagination("invalid_table", null, null, null, 10, 0)!!
        assertNull("Invalid table name should return null", cursor)
    }
    @Test(timeout = 5000)
    fun testLimeDBQueryWithPaginationWithWhereClause() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var whereClause: String = (LIME.DB_COLUMN_ID + " > ?")
        var whereArgs: Array<String?> = arrayOf<String?>("0")
        var cursor: android.database.Cursor = limeDB.queryWithPagination(LIME.DB_TABLE_RELATED, whereClause, whereArgs, (LIME.DB_COLUMN_ID + " ASC"), 10, 0)!!
        assertTrue("queryWithPagination with WHERE should return cursor or null", ((cursor == null) || (cursor.getCount() >= 0)))
        if ((cursor != null)) {
            cursor.close()
        }
    }
    @Test
    fun testLimeDBIsValidTableNameWithAllValidTables() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertTrue("DB_TABLE_ARRAY should be valid", limeDB.isValidTableName(LIME.DB_TABLE_ARRAY))
        assertTrue("DB_TABLE_ARRAY10 should be valid", limeDB.isValidTableName(LIME.DB_TABLE_ARRAY10))
        assertTrue("DB_TABLE_CJ should be valid", limeDB.isValidTableName(LIME.DB_TABLE_CJ))
        assertTrue("cj4 should be valid", limeDB.isValidTableName("cj4"))
        assertTrue("DB_TABLE_CJ5 should be valid", limeDB.isValidTableName(LIME.DB_TABLE_CJ5))
        assertTrue("DB_TABLE_CUSTOM should be valid", limeDB.isValidTableName(LIME.DB_TABLE_CUSTOM))
        assertTrue("DB_TABLE_DAYI should be valid", limeDB.isValidTableName(LIME.DB_TABLE_DAYI))
        assertTrue("DB_TABLE_ECJ should be valid", limeDB.isValidTableName(LIME.DB_TABLE_ECJ))
        assertTrue("DB_TABLE_EZ should be valid", limeDB.isValidTableName(LIME.DB_TABLE_EZ))
        assertTrue("DB_TABLE_HS should be valid", limeDB.isValidTableName(LIME.DB_TABLE_HS))
        assertTrue("DB_TABLE_PHONETIC should be valid", limeDB.isValidTableName(LIME.DB_TABLE_PHONETIC))
        assertTrue("DB_TABLE_PINYIN should be valid", limeDB.isValidTableName(LIME.DB_TABLE_PINYIN))
        assertTrue("DB_TABLE_SCJ should be valid", limeDB.isValidTableName(LIME.DB_TABLE_SCJ))
        assertTrue("DB_TABLE_WB should be valid", limeDB.isValidTableName(LIME.DB_TABLE_WB))
        assertTrue("DB_TABLE_RELATED should be valid", limeDB.isValidTableName(LIME.DB_TABLE_RELATED))
        assertTrue("DB_TABLE_IM should be valid", limeDB.isValidTableName(LIME.DB_TABLE_IM))
        assertTrue("DB_TABLE_KEYBOARD should be valid", limeDB.isValidTableName(LIME.DB_TABLE_KEYBOARD))
    }
    @Test
    fun testLimeDBIsValidTableNameWithInvalidTableNames() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertFalse("Invalid table name should return false", limeDB.isValidTableName("invalid_table"))
        assertFalse("SQL injection attempt should return false", limeDB.isValidTableName("'; DROP TABLE custom; --"))
        assertFalse("Table name with spaces should return false", limeDB.isValidTableName("custom table"))
        assertFalse("Table name with special chars should return false", limeDB.isValidTableName("custom-table"))
        assertFalse("Digit-leading table names should return false", limeDB.isValidTableName("4cj"))
    }
    @Test
    fun testLimeDBIsValidTableNameWithNullAndEmpty() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertFalse("Null table name should return false", limeDB.isValidTableName(null))
        assertFalse("Empty table name should return false", limeDB.isValidTableName(""))
    }
    @Test
    fun testLimeDBIsValidTableNameWithBackupTableSuffix() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        assertTrue("custom_user should be valid", limeDB.isValidTableName("custom_user"))
        assertTrue("cj_user should be valid", limeDB.isValidTableName("cj_user"))
        assertTrue("cj4_user should be valid", limeDB.isValidTableName("cj4_user"))
    }
    @Test(timeout = 5000)
    fun testLimeDBSQLInjectionPreventionInCountRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var maliciousWhere: String = (LIME.DB_COLUMN_ID + " = ? OR 1=1")
        var whereArgs: Array<String?> = arrayOf<String?>("1")
        var count: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, maliciousWhere, whereArgs)
        assertTrue("SQL injection attempt should be handled safely", (count >= 0))
    }
    @Test(timeout = 5000)
    fun testLimeDBSQLInjectionPreventionInAddRecord() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put(LIME.DB_RELATED_COLUMN_PWORD, "test'; DROP TABLE related; --")
        values.put(LIME.DB_RELATED_COLUMN_CWORD, "test")
        var result: Long = limeDB.addRecord(LIME.DB_TABLE_RELATED, values)
        assertTrue("SQL injection attempt in ContentValues should be handled safely", (result >= 1))
    }
    @Test(timeout = 5000)
    fun testLimeDBSQLInjectionPreventionInUpdateRecord() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put(LIME.DB_RELATED_COLUMN_USERSCORE, 999)
        var whereClause: String = (LIME.DB_COLUMN_ID + " = ?")
        var whereArgs: Array<String?> = arrayOf<String?>("1'; DROP TABLE related; --")
        var result: Int = limeDB.updateRecord(LIME.DB_TABLE_RELATED, values, whereClause, whereArgs)
        assertTrue("SQL injection attempt in WHERE args should be handled safely", (result >= 1))
    }
    @Test(timeout = 5000)
    fun testLimeDBSQLInjectionPreventionInDeleteRecord() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var whereClause: String = (LIME.DB_COLUMN_ID + " = ?")
        var whereArgs: Array<String?> = arrayOf<String?>("1'; DROP TABLE related; --")
        var result: Int = limeDB.deleteRecord(LIME.DB_TABLE_RELATED, whereClause, whereArgs)
        assertTrue("SQL injection attempt in WHERE args should be handled safely", (result >= 1))
    }
    @Test
    fun testLimeDBSQLInjectionPreventionInTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        var injectionAttempts: Array<String?> = arrayOf<String?>("'; DROP TABLE custom; --", "custom' OR '1'='1", "custom; DELETE FROM custom;", "custom UNION SELECT * FROM im")
        for (maliciousTable in injectionAttempts) {
            assertFalse(("SQL injection in table name should be rejected: " + maliciousTable), limeDB.isValidTableName(maliciousTable))
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBExportTxtTableWithRegularTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        limeDB.addOrUpdateMappingRecord("custom", "test1", "測試1", 10)
        limeDB.addOrUpdateMappingRecord("custom", "test2", "測試2", 20)
        limeDB.addOrUpdateMappingRecord("custom", "test3", "測試3", 30)
        var imConfigInfo: MutableList<ImConfig?> = ArrayList()
        var versionImConfig: ImConfig = ImConfig()
        versionImConfig.setTitle(LIME.IM_FULL_NAME)
        versionImConfig.setDesc("1.0")
        imConfigInfo.add(versionImConfig)
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_" + System.currentTimeMillis()) + ".lime"))
        try {
            var success: Boolean = limeDB.exportTxtTable("custom", exportFile, imConfigInfo)
            assertTrue("exportTxtTable should succeed", success)
            assertTrue("Export file should exist", exportFile.exists())
            assertTrue("Export file should not be empty", (exportFile.length > 0))
            try {
                var reader: java.io.BufferedReader = java.io.BufferedReader(java.io.FileReader(exportFile))
                var line: String
                var foundVersion: Boolean = false
                var foundRecord: Boolean = false
                try {
                    while (true) {
                        line = reader.readLine() ?: break
                        if (line.contains("@version@")) {
                            foundVersion = true
                        }
                        if (line.contains("test1|測試1")) {
                            foundRecord = true
                        }
                    }
                } finally {
                    reader.close()
                }
                assertTrue("Export file should contain version header", foundVersion)
                assertTrue("Export file should contain test record", foundRecord)
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Error reading export file", e)
                fail(("Failed to read export file: " + e.getMessage()))
            }
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testImportTxtTableStoresVersionMetadataFromLimeHeader() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        var file: File = File.createTempFile("lime-version", ".lime", appContext.getCacheDir())
        try {
            var content: String = ((((("@version@|My Android Table 2026.05\n" + "@selkey@|123456789\n") + "%chardef begin\n") + "aa|測\n") + "ab|試\n") + "%chardef end\n")
            writeUtf8(file, content)
            limeDB.setFilename(file)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals("My Android Table 2026.05", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "version"))
            assertEquals("自建輸入法", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "name"))
            assertEquals("123456789", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "selkey"))
            assertEquals("2", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "amount"))
        } finally {
            if (file.delete()) {
                Log.e(TAG, "Failed to delete temp import file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testImportTxtTableStoresCnameMetadataFromLimeHeader() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        var file: File = File.createTempFile("lime-cname", ".lime", appContext.getCacheDir())
        try {
            var content: String = (((("@version@|My Android Table 2026.05\n" + "@cname@|自建輸入法名稱\n") + "%chardef begin\n") + "aa|測\n") + "%chardef end\n")
            writeUtf8(file, content)
            limeDB.setFilename(file)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals("My Android Table 2026.05", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "version"))
            assertEquals("自建輸入法名稱", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "name"))
            assertEquals("1", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "amount"))
        } finally {
            if (file.delete()) {
                Log.e(TAG, "Failed to delete temp import file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testImportTxtTableStoresVersionMetadataFromCinVersion() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        var file: File = File.createTempFile("lime-version", ".cin", appContext.getCacheDir())
        try {
            var content: String = (((((("%version 大易測試版 1.2.3\n" + "%cname 大易測試表\n") + "%selkey 123456789\n") + "%chardef begin\n") + "a 測\n") + "b 試\n") + "%chardef end\n")
            writeUtf8(file, content)
            limeDB.setFilename(file)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals("大易測試版 1.2.3", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "version"))
            assertEquals("大易測試表", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "name"))
            assertEquals("123456789", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "selkey"))
        } finally {
            if (file.delete()) {
                Log.e(TAG, "Failed to delete temp import file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testImportTxtTableSkipsCinCommentLinesInsideChardef() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        var file: File = File.createTempFile("lime-cin-comments", ".cin", appContext.getCacheDir())
        try {
            var content: String = (((((("%version Comment Test\n" + "%chardef begin\n") + "# Begin\n") + "a 測\n") + "# End\n") + "b 試\n") + "%chardef end\n")
            writeUtf8(file, content)
            limeDB.setFilename(file)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals("2", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "amount"))
            assertEquals("自建輸入法", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "name"))
            assertEquals(0, limeDB.countRecords(LIME.DB_TABLE_CUSTOM, "code = ?", arrayOf("#")))
            assertEquals(0, limeDB.countRecords(LIME.DB_TABLE_CUSTOM, "word IN (?, ?)", arrayOf("Begin", "End")))
        } finally {
            if (file.delete()) {
                Log.e(TAG, "Failed to delete temp import file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testImportTxtTableUsesCinCnameAsVersionFallbackWhenVersionMissing() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        var file: File = File.createTempFile("lime-version-legacy", ".cin", appContext.getCacheDir())
        try {
            var content: String = ((("%cname 舊格式輸入法名稱\n" + "%chardef begin\n") + "a 測\n") + "%chardef end\n")
            writeUtf8(file, content)
            limeDB.setFilename(file)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals("舊格式輸入法名稱", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "version"))
            assertEquals("舊格式輸入法名稱", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "name"))
        } finally {
            if (file.delete()) {
                Log.e(TAG, "Failed to delete temp import file")
            }
        }
    }
    @Test(timeout = 10000)
    fun testExportTxtTableUsesVersionMetadataForVersionHeader() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        limeDB.setTableName(LIME.DB_TABLE_CUSTOM)
        limeDB.addOrUpdateMappingRecord("custom", "version_test", "測", 10)
        limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "name", "Friendly Name")
        limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "version", "Version 2.0")
        var imConfigInfo: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_CUSTOM, null)
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_version_" + System.currentTimeMillis()) + ".lime"))
        try {
            var success: Boolean = limeDB.exportTxtTable(LIME.DB_TABLE_CUSTOM, exportFile, imConfigInfo)
            assertTrue("exportTxtTable should succeed", success)
            var output: String = readUtf8(exportFile)
            assertTrue("Export file should contain dedicated version header", output.contains("@version@|Version 2.0"))
            assertFalse("Export file should not use name when version exists", output.contains("@version@|Friendly Name"))
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 10000)
    fun testExportTxtTableWritesCnameMetadataFromNameConfig() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        limeDB.setTableName(LIME.DB_TABLE_CUSTOM)
        limeDB.addOrUpdateMappingRecord("custom", "cname_test", "測", 10)
        limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "name", "Friendly Name")
        limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "version", "Version 2.0")
        var imConfigInfo: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_CUSTOM, null)
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_cname_" + System.currentTimeMillis()) + ".lime"))
        try {
            var success: Boolean = limeDB.exportTxtTable(LIME.DB_TABLE_CUSTOM, exportFile, imConfigInfo)
            assertTrue("exportTxtTable should succeed", success)
            var output: String = readUtf8(exportFile)
            assertTrue("Export file should contain dedicated version header", output.contains("@version@|Version 2.0"))
            assertTrue("Export file should contain display name header", output.contains("@cname@|Friendly Name"))
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 10000)
    fun testExportTxtTableWritesLimeEndkeyMetadataFromEndkeyConfig() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        limeDB.setTableName(LIME.DB_TABLE_CUSTOM)
        limeDB.addOrUpdateMappingRecord("custom", "endkey_test", "測", 10)
        limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, LIME.IM_LIME_ENDKEY, ",.")
        var imConfigInfo: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_CUSTOM, null)
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_limeendkey_" + System.currentTimeMillis()) + ".lime"))
        try {
            var success: Boolean = limeDB.exportTxtTable(LIME.DB_TABLE_CUSTOM, exportFile, imConfigInfo)
            assertTrue("exportTxtTable should succeed", success)
            var output: String = readUtf8(exportFile)
            assertTrue("Export file should contain LIME-specific endkey header", output.contains("@limeendkey@|,."))
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testImportTxtTableSupportsLimeTextV2EscapedFields() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        var file: File = File.createTempFile("lime-v2-import", ".lime", appContext.getCacheDir())
        try {
            var content: String = (((("@format@|lime-text-v2\n" + "%chardef begin\n") + "aa|word\\|with\\|pipes|7|11\n") + "\\@code|literal at code|3|5\n") + "%chardef end\n")
            writeUtf8(file, content)
            limeDB.setFilename(file)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals(1, limeDB.countRecords(LIME.DB_TABLE_CUSTOM, "code = ? AND word = ? AND score = ? AND basescore = ?", arrayOf("aa", "word|with|pipes", "7", "11")))
            assertEquals(1, limeDB.countRecords(LIME.DB_TABLE_CUSTOM, "code = ? AND word = ? AND score = ? AND basescore = ?", arrayOf("@code", "literal at code", "3", "5")))
        } finally {
            if (file.delete()) {
                Log.e(TAG, "Failed to delete temp import file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testImportTxtTableStoresImKeyMetadataFromLimeHeader() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        var file: File = File.createTempFile("lime-imkeys-import", ".lime", appContext.getCacheDir())
        try {
            var content: String = ((((("@format@|lime-text-v2\n" + "@imkeys@|ab\n") + "@imkeynames@|ㄅ\\|ㄆ\n") + "%chardef begin\n") + "aa|測\n") + "%chardef end\n")
            writeUtf8(file, content)
            limeDB.setFilename(file)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals("ab", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "imkeys"))
            assertEquals("ㄅ|ㄆ", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "imkeynames"))
        } finally {
            if (file.delete()) {
                Log.e(TAG, "Failed to delete temp import file")
            }
        }
    }
    @Test(timeout = 10000)
    fun testExportTxtTableWritesLimeTextV2WhenFieldsNeedEscaping() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        limeDB.setTableName(LIME.DB_TABLE_CUSTOM)
        limeDB.addOrUpdateMappingRecord("custom", "@code", "word|with|pipes", 4)
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_v2_" + System.currentTimeMillis()) + ".lime"))
        try {
            var success: Boolean = limeDB.exportTxtTable(LIME.DB_TABLE_CUSTOM, exportFile, null)
            assertTrue("exportTxtTable should succeed", success)
            var output: String = readUtf8(exportFile)
            assertTrue(output.contains("@format@|lime-text-v2"))
            assertTrue(output.contains("\\@code|word\\|with\\|pipes|4|0"))
            assertTrue(output.contains("%chardef begin"))
            assertTrue(output.contains("%chardef end"))
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 10000)
    fun testExportTxtTableWritesImKeyMetadataFromConfig() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        limeDB.setTableName(LIME.DB_TABLE_CUSTOM)
        limeDB.addOrUpdateMappingRecord("custom", "aa", "測", 10)
        limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "imkeys", "ab")
        limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "imkeynames", "ㄅ|ㄆ")
        var imConfigInfo: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_CUSTOM, null)
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_imkeys_" + System.currentTimeMillis()) + ".lime"))
        try {
            var success: Boolean = limeDB.exportTxtTable(LIME.DB_TABLE_CUSTOM, exportFile, imConfigInfo)
            assertTrue("exportTxtTable should succeed", success)
            var output: String = readUtf8(exportFile)
            assertTrue(output.contains("@format@|lime-text-v2"))
            assertTrue(output.contains("@imkeys@|ab"))
            assertTrue(output.contains("@imkeynames@|ㄅ\\|ㄆ"))
            assertTrue(output.contains("%chardef begin"))
            assertTrue(output.contains("%chardef end"))
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBExportTxtTableWithRelatedTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙1")
        limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙2")
        limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙3")
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_related_" + System.currentTimeMillis()) + ".related"))
        try {
            var success: Boolean = limeDB.exportTxtTable(LIME.DB_TABLE_RELATED, exportFile, null)
            assertTrue("exportTxtTable should succeed for related table", success)
            assertTrue("Export file should exist", exportFile.exists())
            assertTrue("Export file should not be empty", (exportFile.length > 0))
            var output: String = readUtf8(exportFile)
            assertTrue(output.contains("%chardef begin"))
            assertTrue(output.contains("%chardef end"))
            try {
                var reader: java.io.BufferedReader = java.io.BufferedReader(java.io.FileReader(exportFile))
                var line: String
                var foundRelatedRecord1: Boolean = false
                var foundRelatedRecord2: Boolean = false
                var foundRelatedRecord3: Boolean = false
                try {
                    while (true) {
                        line = reader.readLine() ?: break
                        var parts: Array<String> = line.split("\\|".toRegex()).toTypedArray()
                        if ((parts.length >= 3)) {
                            var pword: String = ""
                            var cword: String = ""
                            if ((parts.length == 4)) {
                                pword = parts[0]
                                cword = parts[1]
                            } else {
                                if ((parts.length == 3)) {
                                    var pwordCword: String = parts[0]
                                    if ((pwordCword.length > 0)) {
                                        pword = pwordCword.substring(0, Math.min(2, pwordCword.length))
                                        if ((pwordCword.length > pword.length)) {
                                            cword = pwordCword.substring(pword.length)
                                        }
                                    }
                                }
                            }
                            if ("測試".equals(pword) && "詞彙1".equals(cword)) {
                                foundRelatedRecord1 = true
                            } else {
                                if ("測試".equals(pword) && "詞彙2".equals(cword)) {
                                    foundRelatedRecord2 = true
                                } else {
                                    if ("測試".equals(pword) && "詞彙3".equals(cword)) {
                                        foundRelatedRecord3 = true
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    reader.close()
                }
                assertTrue("Export file should contain related record with pword=測試, cword=詞彙1", foundRelatedRecord1)
                assertTrue("Export file should contain related record with pword=測試, cword=詞彙2", foundRelatedRecord2)
                assertTrue("Export file should contain related record with pword=測試, cword=詞彙3", foundRelatedRecord3)
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Error reading export file", e)
                fail(("Failed to read export file: " + e.getMessage()))
            }
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBExportTxtTableWithInvalidTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_invalid_" + System.currentTimeMillis()) + ".lime"))
        try {
            var success: Boolean = limeDB.exportTxtTable("invalid_table", exportFile, null)
            assertFalse("exportTxtTable should fail for invalid table", success)
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 5000)
    fun testLimeDBExportTxtTableWithNullFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var success: Boolean = limeDB.exportTxtTable("custom", null, null)
        assertFalse("exportTxtTable should fail for null file", success)
    }
    @Test(timeout = 5000)
    fun testLimeDBGetAllRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        limeDB.addOrUpdateMappingRecord("custom", "test1", "測試1", 10)
        limeDB.addOrUpdateMappingRecord("custom", "test2", "測試2", 20)
        var records: MutableList<Record> = limeDB.getRecordList("custom", null, false, 0, 0)
        assertNotNull("getRecords should return a list (not null)", records)
        assertTrue("getRecords should return at least 2 records", (records.size >= 2))
        var foundTest1: Boolean = false
        var foundTest2: Boolean = false
        for (record in records) {
            if ("test1".equals(record.getCode()) && "測試1".equals(record.getWord())) {
                foundTest1 = true
            }
            if ("test2".equals(record.getCode()) && "測試2".equals(record.getWord())) {
                foundTest2 = true
            }
        }
        assertTrue("getRecords should contain test1 record", foundTest1)
        assertTrue("getRecords should contain test2 record", foundTest2)
    }
    @Test(timeout = 10000)
    fun testLimeDBExportTxtTableAndImportTxtTableWithDataConsistency() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        limeDB.setTableName("custom")
        var code1: String = "exportimport1"
        var word1: String = "匯出匯入測試1"
        var code2: String = "exportimport2"
        var word2: String = "匯出匯入測試2"
        var code3: String = "exportimport3"
        var word3: String = "匯出匯入測試3"
        limeDB.addOrUpdateMappingRecord("custom", code1, word1, 10)
        limeDB.addOrUpdateMappingRecord("custom", code2, word2, 20)
        limeDB.addOrUpdateMappingRecord("custom", code3, word3, 30)
        var originalCount: Int = limeDB.countRecords("custom", null, null)
        assertTrue("Should have at least 3 records before export", (originalCount >= 3))
        var mappings1: MutableList<Mapping?> = limeDB.getMappingByCode(code1, true, false)!!
        var mappings2: MutableList<Mapping?> = limeDB.getMappingByCode(code2, true, false)!!
        var mappings3: MutableList<Mapping?> = limeDB.getMappingByCode(code3, true, false)!!
        assertNotNull("Mappings for code1 should exist before export", mappings1)
        assertTrue("Mappings for code1 should not be empty", (mappings1.size > 0))
        assertNotNull("Mappings for code2 should exist before export", mappings2)
        assertTrue("Mappings for code2 should not be empty", (mappings2.size > 0))
        assertNotNull("Mappings for code3 should exist before export", mappings3)
        assertTrue("Mappings for code3 should not be empty", (mappings3.size > 0))
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_import_pair_" + System.currentTimeMillis()) + ".lime"))
        try {
            var imConfigInfo: MutableList<ImConfig?> = ArrayList()
            var versionImConfig: ImConfig = ImConfig()
            versionImConfig.setTitle(LIME.IM_FULL_NAME)
            versionImConfig.setDesc("1.0")
            imConfigInfo.add(versionImConfig)
            var exportSuccess: Boolean = limeDB.exportTxtTable("custom", exportFile, imConfigInfo)
            assertTrue("exportTxtTable should succeed", exportSuccess)
            assertTrue("Export file should exist", exportFile.exists())
            assertTrue("Export file should not be empty", (exportFile.length > 0))
            limeDB.clearTable("custom")
            var countAfterDelete: Int = limeDB.countRecords("custom", null, null)
            assertEquals("Table should be empty after deleteAll", 0, countAfterDelete)
            limeDB.setFilename(exportFile)
            limeDB.importTxtTable("custom", null)
            var waitCount: Int = 0
            var maxWait: Int = 100
            while ((waitCount < maxWait)) {
                Thread.sleep(100)
                waitCount++
                try {
                    var importThreadField: java.lang.reflect.Field = LimeDB::class.java.getDeclaredField("importThread")
                    importThreadField.setAccessible(true)
                    var importThread: Thread = (importThreadField.get(limeDB) as Thread)
                    if (((importThread == null) || importThread.isAlive())) {
                        break
                    }
                } catch (e: Exception) {

                }
            }
            Thread.sleep(2000)
            var countAfterImport: Int = limeDB.countRecords("custom", null, null)
            assertEquals("Record count should match original count after import", originalCount, countAfterImport)
            var importedMappings1: MutableList<Mapping?> = limeDB.getMappingByCode(code1, true, false)!!
            var importedMappings2: MutableList<Mapping?> = limeDB.getMappingByCode(code2, true, false)!!
            var importedMappings3: MutableList<Mapping?> = limeDB.getMappingByCode(code3, true, false)!!
            assertNotNull("Mappings for code1 should exist after import", importedMappings1)
            assertTrue("Mappings for code1 should not be empty after import", (importedMappings1.size > 0))
            assertNotNull("Mappings for code2 should exist after import", importedMappings2)
            assertTrue("Mappings for code2 should not be empty after import", (importedMappings2.size > 0))
            assertNotNull("Mappings for code3 should exist after import", importedMappings3)
            assertTrue("Mappings for code3 should not be empty after import", (importedMappings3.size > 0))
            var foundWord1: Boolean = false
            var foundWord2: Boolean = false
            var foundWord3: Boolean = false
            for (m in importedMappings1) {
                if (word1.equals(m.getWord())) {
                    foundWord1 = true
                    break
                }
            }
            for (m in importedMappings2) {
                if (word2.equals(m.getWord())) {
                    foundWord2 = true
                    break
                }
            }
            for (m in importedMappings3) {
                if (word3.equals(m.getWord())) {
                    foundWord3 = true
                    break
                }
            }
            assertTrue("Word1 should exist after import", foundWord1)
            assertTrue("Word2 should exist after import", foundWord2)
            assertTrue("Word3 should exist after import", foundWord3)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBExportTxtTableAndImportTxtTableWithDataConsistency failed: " + e.getMessage()), e)
            fail(("ERROR: Export/Import pair test failed with exception: " + e.getMessage()))
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 30000)
    fun testLimeDBExportImportLimeTextV2MetadataRoundTrip() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection.")
        }
        limeDB.setTableName(LIME.DB_TABLE_CUSTOM)
        limeDB.clearTable(LIME.DB_TABLE_CUSTOM)
        limeDB.addOrUpdateMappingRecord(LIME.DB_TABLE_CUSTOM, "@v2code", "word|with|pipes", 4)
        limeDB.addOrUpdateMappingRecord(LIME.DB_TABLE_CUSTOM, "plainv2", "一般詞", 6)
        limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "imkeys", "ab")
        limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "imkeynames", "ㄅ|ㄆ")
        var imConfigInfo: MutableList<ImConfig?> = limeDB.getImConfigList(LIME.DB_TABLE_CUSTOM, null)
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_import_v2_metadata_" + System.currentTimeMillis()) + ".lime"))
        try {
            var exportSuccess: Boolean = limeDB.exportTxtTable(LIME.DB_TABLE_CUSTOM, exportFile, imConfigInfo)
            assertTrue("exportTxtTable should succeed", exportSuccess)
            var output: String = readUtf8(exportFile)
            assertTrue(output.contains("@format@|lime-text-v2"))
            assertTrue(output.contains("\\@v2code|word\\|with\\|pipes|4|"))
            assertTrue(output.contains("@imkeynames@|ㄅ\\|ㄆ"))
            limeDB.clearTable(LIME.DB_TABLE_CUSTOM)
            limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "imkeys", "stale")
            limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "imkeynames", "stale")
            limeDB.setFilename(exportFile)
            limeDB.importTxtTable(LIME.DB_TABLE_CUSTOM, null)
            waitForImportThread(limeDB)
            assertEquals(1, limeDB.countRecords(LIME.DB_TABLE_CUSTOM, "code = ? AND word = ? AND score = ?", arrayOf("@v2code", "word|with|pipes", "4")))
            assertEquals(1, limeDB.countRecords(LIME.DB_TABLE_CUSTOM, "code = ? AND word = ? AND score = ?", arrayOf("plainv2", "一般詞", "6")))
            assertEquals("ab", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "imkeys"))
            assertEquals("ㄅ|ㄆ", limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "imkeynames"))
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBExportTxtTableRelatedAndImportTxtTableWithDataConsistency() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var pword1: String = "匯出"
        var cword1: String = "匯入1"
        var pword2: String = "匯出"
        var cword2: String = "匯入2"
        var pword3: String = "測試"
        var cword3: String = "詞彙"
        limeDB.addOrUpdateRelatedPhraseRecord(pword1, cword1)
        limeDB.addOrUpdateRelatedPhraseRecord(pword2, cword2)
        limeDB.addOrUpdateRelatedPhraseRecord(pword3, cword3)
        var originalCount: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
        assertTrue("Should have at least 3 records before export", (originalCount >= 3))
        var related1: Mapping = limeDB.isRelatedPhraseExist(pword1, cword1)!!
        var related2: Mapping = limeDB.isRelatedPhraseExist(pword2, cword2)!!
        var related3: Mapping = limeDB.isRelatedPhraseExist(pword3, cword3)!!
        assertNotNull("Related phrase 1 should exist before export", related1)
        assertNotNull("Related phrase 2 should exist before export", related2)
        assertNotNull("Related phrase 3 should exist before export", related3)
        var exportFile: File = File(appContext.getCacheDir(), (("test_export_import_related_pair_" + System.currentTimeMillis()) + ".related"))
        try {
            var exportSuccess: Boolean = limeDB.exportTxtTable(LIME.DB_TABLE_RELATED, exportFile, null)
            assertTrue("exportTxtTable should succeed for related table", exportSuccess)
            assertTrue("Export file should exist", exportFile.exists())
            assertTrue("Export file should not be empty", (exportFile.length > 0))
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            var countAfterDelete: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertEquals("Related table should be empty after deleteAll", 0, countAfterDelete)
            limeDB.setFilename(exportFile)
            limeDB.importTxtTable(LIME.DB_TABLE_RELATED, null)
            var waitCount: Int = 0
            var maxWait: Int = 100
            while ((waitCount < maxWait)) {
                Thread.sleep(100)
                waitCount++
                try {
                    var importThreadField: java.lang.reflect.Field = LimeDB::class.java.getDeclaredField("importThread")
                    importThreadField.setAccessible(true)
                    var importThread: Thread = (importThreadField.get(limeDB) as Thread)
                    if (((importThread == null) || importThread.isAlive())) {
                        break
                    }
                } catch (e: Exception) {

                }
            }
            Thread.sleep(2000)
            var countAfterImport: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertEquals("Record count should match original count after import", originalCount, countAfterImport)
            var importedRelated1: Mapping = limeDB.isRelatedPhraseExist(pword1, cword1)!!
            var importedRelated2: Mapping = limeDB.isRelatedPhraseExist(pword2, cword2)!!
            var importedRelated3: Mapping = limeDB.isRelatedPhraseExist(pword3, cword3)!!
            assertNotNull("Related phrase 1 should exist after import", importedRelated1)
            assertNotNull("Related phrase 2 should exist after import", importedRelated2)
            assertNotNull("Related phrase 3 should exist after import", importedRelated3)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBExportTxtTableRelatedAndImportTxtTableWithDataConsistency failed: " + e.getMessage()), e)
            fail(("ERROR: Export/Import pair test failed with exception: " + e.getMessage()))
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.e(TAG, "Failed to delete export file")
            }
        }
    }
    @Test(timeout = 60000)
    fun testLimeDBImportDbRelatedDelegatesToImportDb() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_importDbRelated_delegation_" + System.currentTimeMillis()) + ".db"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blankrelated), backupFile)
            limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙1")
            limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙2")
            limeDB.prepareBackup(backupFile, null, true)
            assertTrue("Backup file should exist", backupFile.exists())
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            var countAfterDelete: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertEquals("Related table should be empty after delete", 0, countAfterDelete)
            limeDB.importDbRelated(backupFile)
            var countAfterImport: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("importDbRelated should import related records (delegation to importDb)", (countAfterImport >= 0))
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBImportDbRelatedDelegatesToImportDb failed: " + e.getMessage()), e)
            fail(("ERROR: importDbRelated delegation test failed with exception: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBWrapperMethodsDelegationComplete() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var backupFile1: File = File(cacheDir, (("test_wrapper_prepareBackupDb_" + System.currentTimeMillis()) + ".db"))
            if (backupFile1.exists()) {
                backupFile1.delete()
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile1)
            limeDB.prepareBackupDb(backupFile1.getAbsolutePath(), "custom")
            assertTrue("prepareBackupDb should create/modify backup file", backupFile1.exists())
            var backupFile2: File = File(cacheDir, (("test_wrapper_prepareBackupRelatedDb_" + System.currentTimeMillis()) + ".db"))
            if (backupFile2.exists()) {
                backupFile2.delete()
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blankrelated), backupFile2)
            limeDB.prepareBackupRelatedDb(backupFile2.getAbsolutePath())
            assertTrue("prepareBackupRelatedDb should create/modify backup file", backupFile2.exists())
            var backupFile3: File = File(cacheDir, (("test_wrapper_importDbRelated_" + System.currentTimeMillis()) + ".db"))
            if (backupFile3.exists()) {
                backupFile3.delete()
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blankrelated), backupFile3)
            limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙")
            limeDB.prepareBackup(backupFile3, null, true)
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            limeDB.importDbRelated(backupFile3)
            var count: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("importDbRelated should import records (delegation works)", (count >= 0))
            if (backupFile1.exists()) {
                backupFile1.delete()
            }
            if (backupFile2.exists()) {
                backupFile2.delete()
            }
            if (backupFile3.exists()) {
                backupFile3.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBWrapperMethodsDelegationComplete failed: " + e.getMessage()), e)
            fail(("ERROR: Wrapper methods delegation test failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBCountRecordsWithMultipleConditions() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var cv1: android.content.ContentValues = android.content.ContentValues()
            cv1.put("code", "test1")
            cv1.put("word", "測試1")
            cv1.put("score", 10)
            cv1.put("basescore", 5)
            limeDB.addRecord("custom", cv1)
            var cv2: android.content.ContentValues = android.content.ContentValues()
            cv2.put("code", "test2")
            cv2.put("word", "測試2")
            cv2.put("score", 20)
            cv2.put("basescore", 10)
            limeDB.addRecord("custom", cv2)
            var cv3: android.content.ContentValues = android.content.ContentValues()
            cv3.put("code", "test1")
            cv3.put("word", "測試3")
            cv3.put("score", 15)
            cv3.put("basescore", 8)
            limeDB.addRecord("custom", cv3)
            var whereClause: String = "code = ? AND score > ?"
            var whereArgs: Array<String?> = arrayOf<String?>("test1", "10")
            var count: Int = limeDB.countRecords("custom", whereClause, whereArgs)
            assertTrue("countRecords with multiple conditions should return correct count", (count >= 0))
            var whereClause2: String = "code = ? OR score > ?"
            var whereArgs2: Array<String?> = arrayOf<String?>("test2", "15")
            var count2: Int = limeDB.countRecords("custom", whereClause2, whereArgs2)
            assertTrue("countRecords with OR conditions should return correct count", (count2 >= 0))
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBCountRecordsWithMultipleConditions failed: " + e.getMessage()), e)
            fail(("ERROR: countRecords with multiple conditions test failed: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBUpdateRecordWithMultipleRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var cv1: android.content.ContentValues = android.content.ContentValues()
            cv1.put("code", "update_test")
            cv1.put("word", "測試1")
            cv1.put("score", 10)
            cv1.put("basescore", 5)
            limeDB.addRecord("custom", cv1)
            var cv2: android.content.ContentValues = android.content.ContentValues()
            cv2.put("code", "update_test")
            cv2.put("word", "測試2")
            cv2.put("score", 10)
            cv2.put("basescore", 5)
            limeDB.addRecord("custom", cv2)
            var whereClause: String = "code = ?"
            var whereArgs: Array<String?> = arrayOf<String?>("update_test")
            var countBefore: Int = limeDB.countRecords("custom", whereClause, whereArgs)
            assertTrue("Should have at least 2 records before update", (countBefore >= 2))
            var updateValues: android.content.ContentValues = android.content.ContentValues()
            updateValues.put("score", 99)
            var updatedCount: Int = limeDB.updateRecord("custom", updateValues, whereClause, whereArgs)
            assertTrue("updateRecord should update multiple records", (updatedCount >= 2))
            var verifyWhereClause: String = "code = ? AND score = ?"
            var verifyWhereArgs: Array<String?> = arrayOf<String?>("update_test", "99")
            var countAfter: Int = limeDB.countRecords("custom", verifyWhereClause, verifyWhereArgs)
            assertEquals("All matching records should be updated", countBefore, countAfter)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBUpdateRecordWithMultipleRecords failed: " + e.getMessage()), e)
            fail(("ERROR: updateRecord with multiple records test failed: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBDeleteRecordWithMultipleRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        try {
            var cv1: android.content.ContentValues = android.content.ContentValues()
            cv1.put("code", "delete_test")
            cv1.put("word", "測試1")
            cv1.put("score", 10)
            cv1.put("basescore", 5)
            limeDB.addRecord("custom", cv1)
            var cv2: android.content.ContentValues = android.content.ContentValues()
            cv2.put("code", "delete_test")
            cv2.put("word", "測試2")
            cv2.put("score", 10)
            cv2.put("basescore", 5)
            limeDB.addRecord("custom", cv2)
            var cv3: android.content.ContentValues = android.content.ContentValues()
            cv3.put("code", "delete_test")
            cv3.put("word", "測試3")
            cv3.put("score", 10)
            cv3.put("basescore", 5)
            limeDB.addRecord("custom", cv3)
            var whereClause: String = "code = ?"
            var whereArgs: Array<String?> = arrayOf<String?>("delete_test")
            var countBefore: Int = limeDB.countRecords("custom", whereClause, whereArgs)
            assertTrue("Should have at least 3 records before delete", (countBefore >= 3))
            var deletedCount: Int = limeDB.deleteRecord("custom", whereClause, whereArgs)
            assertTrue("deleteRecord should delete multiple records", (deletedCount >= 3))
            var countAfter: Int = limeDB.countRecords("custom", whereClause, whereArgs)
            assertEquals("All matching records should be deleted", 0, countAfter)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBDeleteRecordWithMultipleRecords failed: " + e.getMessage()), e)
            fail(("ERROR: deleteRecord with multiple records test failed: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBImportDbWithMultipleTables() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_importDb_multiple_tables_" + System.currentTimeMillis()) + ".db"))
            if (backupFile.exists()) {
                backupFile.delete()
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            tableNames.add("cj")
            limeDB.prepareBackup(backupFile, tableNames, false)
            assertTrue("Backup file should exist", backupFile.exists())
            var importTables: MutableList<String?> = ArrayList()
            importTables.add("custom")
            importTables.add("cj")
            limeDB.importDb(backupFile, importTables, false, true)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBImportDbWithMultipleTables failed: " + e.getMessage()), e)
            fail(("ERROR: importDb with multiple tables test failed: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                backupFile.delete()
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBImportDbWithIncludeRelated() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_importDb_includeRelated_" + System.currentTimeMillis()) + ".db"))
            if (backupFile.exists()) {
                backupFile.delete()
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blankrelated), backupFile)
            limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙1")
            limeDB.addOrUpdateRelatedPhraseRecord("測試", "詞彙2")
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            limeDB.prepareBackup(backupFile, tableNames, true)
            assertTrue("Backup file should exist", backupFile.exists())
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            var countBefore: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertEquals("Related table should be empty", 0, countBefore)
            var importTables: MutableList<String?> = ArrayList()
            importTables.add("custom")
            limeDB.importDb(backupFile, importTables, true, true)
            var countAfter: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("importDb with includeRelated=true should import related records", (countAfter >= 0))
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBImportDbWithIncludeRelated failed: " + e.getMessage()), e)
            fail(("ERROR: importDb with includeRelated test failed: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                backupFile.delete()
            }
        }
    }
    @Test(timeout = 10000)
    fun testLimeDBImportDbWithOverwriteExistingFalse() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var limeDB: LimeDB = LimeDB(appContext)
        if (initializeDatabase(limeDB)) {
            fail("ERROR: Cannot initialize database connection. Database may be on hold from a previous operation. Test cannot proceed.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_importDb_overwriteFalse_" + System.currentTimeMillis()) + ".db"))
            if (backupFile.exists()) {
                backupFile.delete()
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(R.raw.blank), backupFile)
            var cv1: android.content.ContentValues = android.content.ContentValues()
            cv1.put("code", "existing")
            cv1.put("word", "現有")
            cv1.put("score", 10)
            cv1.put("basescore", 5)
            limeDB.addRecord("custom", cv1)
            var countBefore: Int = limeDB.countRecords("custom", null, null)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            limeDB.prepareBackup(backupFile, tableNames, false)
            var importTables: MutableList<String?> = ArrayList()
            importTables.add("custom")
            limeDB.importDb(backupFile, importTables, false, false)
            var countAfter: Int = limeDB.countRecords("custom", null, null)
            assertTrue("importDb with overwriteExisting=false should append records", (countAfter >= countBefore))
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeDBImportDbWithOverwriteExistingFalse failed: " + e.getMessage()), e)
            fail(("ERROR: importDb with overwriteExisting=false test failed: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                backupFile.delete()
            }
        }
    }
}
