package net.toload.main.hd.ui.controller

import android.content.ContentValues
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.data.Record
import net.toload.main.hd.data.Related
import net.toload.main.hd.DBServer
import net.toload.main.hd.global.LIME
import net.toload.main.hd.SearchServer
import net.toload.main.hd.ui.view.ManageImView
import net.toload.main.hd.ui.view.ManageRelatedView

/**
 * Controller for IM-related operations.
 * 
 * 
 * This controller handles:
 * 
 *  * IM setup operations (button states, IM info)
 *  * IM file operations (import/export IM databases, backup/restore)
 *  * Record CRUD operations (ManageIm)
 *  * Related phrase CRUD operations (ManageRelated)
 *  * Search and filter logic
 * 
 */
class ManageImController(searchServer: SearchServer?) : BaseController() {
    private val searchServer: SearchServer?

    // Executor used for asynchronous controller operations
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var manageImView: ManageImView? = null
    private var manageRelatedView: ManageRelatedView? = null

    /**
     * Creates a new ManageImController.
     * 
     * @param searchServer The SearchServer instance for database operations
     */
    init {
        this.searchServer = searchServer
    }


    /**
     * Sets the ManageIm view for this controller.
     * 
     * @param view The ManageImView implementation
     */
    fun setManageImView(view: ManageImView?) {
        this.manageImView = view
    }

    /**
     * Sets the ManageRelated view for this controller.
     * 
     * @param view The ManageRelatedView implementation
     */
    fun setManageRelatedView(view: ManageRelatedView?) {
        this.manageRelatedView = view
    }

    /**
     * Loads records asynchronously using the controller's executor. The view's
     * callback methods are used for progress and results; the view implementations
     * are expected to marshal UI updates to the main thread as needed.
     * 
     * @param table the database table name to query
     * @param query the search query string (word or code depending on searchByCode)
     * @param searchByCode true to search by code, false to search by word
     * @param offset pagination offset
     * @param limit pagination limit (max number of records to return)
     */
    fun loadRecordsAsync(
        table: String,
        query: String?,
        searchByCode: Boolean,
        offset: Int,
        limit: Int
    ) {
        val searchServer = searchServer
        if (searchServer == null) {
            // Validation errors are synchronous - log and report immediately
            if (DEBUG) Log.e(TAG, "SearchServer not initialized")
            manageImView?.onError("SearchServer not initialized")
            return
        }
        if (!searchServer.isValidTableName(table)) {
            // Validation errors are synchronous - log and report immediately
            if (DEBUG) Log.e(TAG, "Invalid table name: " + table)
            manageImView?.onError("Invalid table name: " + table)
            return
        }
        // Run the operation in a background executor thread
        executor.submit(Runnable {
            try {
                // Diagnostic logging

                if (DEBUG) Log.i(
                    TAG,
                    "loadRecordsAsync(): table=" + table + ", query=" + query + ", searchByCode=" + searchByCode + ", offset=" + offset + ", limit=" + limit
                )

                val records = searchServer.getRecords(table, query, searchByCode, limit, offset)
                val count = searchServer.countRecordsByWordOrCode(table, query, searchByCode)

                if (DEBUG) Log.i(
                    TAG,
                    "loadRecordsAsync(): result size=" + (if (records == null) "null" else records.size) + ", count=" + count
                )

                val view = manageImView
                if (view != null) {
                    // Post UI updates to main thread
                    mainHandler.post(Runnable {
                        view.updateRecordCount(count)
                        view.displayRecords(records)
                    })
                }
            } catch (e: Exception) {
                handleError(manageImView, "Failed to load records async: " + e.message, e)
            }
        })
    }

    /**
     * Adds a new record.
     * 
     * @param table The table name
     * @param code The code
     * @param word The word
     * @param score The score
     */
    fun addRecord(table: String, code: String, word: String, score: Int) {
        val searchServer = searchServer ?: return
        if (!searchServer.isValidTableName(table)) {
            handleError(manageImView, "Invalid table name: " + table, null)
            return
        }

        try {
            searchServer.addOrUpdateMappingRecord(table, code, word, score)
            searchServer.initialCache()

            manageImView?.refreshRecordList()
        } catch (e: Exception) {
            handleError(manageImView, "Failed to add record: " + e.message, e)
        }
    }


    /**
     * Updates an existing record.
     * 
     * @param table The table name
     * @param id The record ID
     * @param code The new code
     * @param word The new word
     * @param score The new score
     */
    fun updateRecord(table: String, id: Long, code: String?, word: String?, score: Int) {
        val searchServer = searchServer ?: return
        if (!searchServer.isValidTableName(table)) {
            handleError(manageImView, "Invalid table name: " + table, null)
            return
        }

        try {
            val cv: ContentValues = ContentValues()
            cv.put(LIME.DB_COLUMN_CODE, code)
            cv.put(LIME.DB_COLUMN_WORD, word)
            cv.put(LIME.DB_COLUMN_SCORE, score)
            searchServer.updateRecord(
                table,
                cv,
                LIME.DB_COLUMN_ID + " = ?",
                arrayOf<String?>(id.toString())
            )
            searchServer.initialCache()

            manageImView?.refreshRecordList()
        } catch (e: Exception) {
            handleError(manageImView, "Failed to update record: " + e.message, e)
        }
    }


    /**
     * Deletes a record.
     * 
     * @param table The table name
     * @param id The record ID
     */
    fun deleteRecord(table: String, id: Long) {
        val searchServer = searchServer ?: return
        if (!searchServer.isValidTableName(table)) {
            handleError(manageImView, "Invalid table name: " + table, null)
            return
        }

        try {
            searchServer.deleteRecord(
                table,
                LIME.DB_COLUMN_ID + " = ?",
                arrayOf<String?>(id.toString())
            )
            searchServer.initialCache()

            manageImView?.refreshRecordList()
        } catch (e: Exception) {
            handleError(manageImView, "Failed to delete record: " + e.message, e)
        }
    }

    // ========== Related Phrase Operations ==========
    /**
     * Loads related phrases.
     * 
     * @param pWord The parent word (null for all)
     * @param offset The offset for pagination
     * @param limit The limit for pagination
     */
    fun loadRelatedPhrases(pWord: String?, offset: Int, limit: Int) {
        try {
            val searchServer = searchServer ?: return
            val phrases = searchServer.getRelatedByWord(pWord, limit, offset)
            val count = searchServer.countRecordsRelated(pWord)

            manageRelatedView?.updatePhraseCount(count)
            manageRelatedView?.displayRelatedPhrases(phrases)
        } catch (e: Exception) {
            handleError(manageRelatedView, "Failed to load related phrases: " + e.message, e)
        }
    }

    /**
     * Adds a related phrase.
     * 
     * @param pWord The parent word
     * @param cWord The child word
     * @param score The score
     */
    fun addRelatedPhrase(pWord: String?, cWord: String?, score: Int) {
        try {
            val searchServer = searchServer ?: return
            val cv: ContentValues = ContentValues()
            cv.put(LIME.DB_RELATED_COLUMN_PWORD, pWord)
            cv.put(LIME.DB_RELATED_COLUMN_CWORD, cWord)
            cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, score)
            if (searchServer.hasRelated(pWord, cWord)) {
                searchServer.updateRecord(
                    LIME.DB_TABLE_RELATED,
                    cv,
                    LIME.DB_RELATED_COLUMN_PWORD + " = ? AND " + LIME.DB_RELATED_COLUMN_CWORD + " = ?",
                    arrayOf<String?>(pWord, cWord)
                )
            } else {
                searchServer.addRecord(LIME.DB_TABLE_RELATED, cv)
            }

            manageRelatedView?.refreshPhraseList()
        } catch (e: Exception) {
            handleError(manageRelatedView, "Failed to add related phrase: " + e.message, e)
        }
    }

    /**
     * Updates a related phrase.
     * 
     * @param id The phrase ID
     * @param pWord The new parent word
     * @param cWord The new child word
     * @param score The new score
     */
    fun updateRelatedPhrase(id: Long, pWord: String?, cWord: String?, score: Int) {
        try {
            val searchServer = searchServer ?: return
            val cv: ContentValues = ContentValues()
            cv.put(LIME.DB_RELATED_COLUMN_PWORD, pWord)
            cv.put(LIME.DB_RELATED_COLUMN_CWORD, cWord)
            cv.put(LIME.DB_RELATED_COLUMN_BASESCORE, score)
            searchServer.updateRecord(
                LIME.DB_TABLE_RELATED,
                cv,
                LIME.DB_COLUMN_ID + " = ?",
                arrayOf<String?>(id.toString())
            )

            manageRelatedView?.refreshPhraseList()
        } catch (e: Exception) {
            handleError(manageRelatedView, "Failed to update related phrase: " + e.message, e)
        }
    }

    /**
     * Deletes a related phrase.
     * 
     * @param id The phrase ID
     */
    fun deleteRelatedPhrase(id: Long) {
        try {
            val searchServer = searchServer ?: return
            searchServer.deleteRecord(
                LIME.DB_TABLE_RELATED,
                LIME.DB_COLUMN_ID + " = ?",
                arrayOf<String?>(id.toString())
            )

            manageRelatedView?.refreshPhraseList()
        } catch (e: Exception) {
            handleError(manageRelatedView, "Failed to delete related phrase: " + e.message, e)
        }
    }

    val imConfigFullNameList: MutableList<ImConfig>
        /**
         * Returns the list of IMs.
         * 
         * @return a non-null list of `Im` objects; may be empty when the server is uninitialized
         */
        get() {
            // Return empty list if server is not initialized (e.g., in test mode)
            if (searchServer == null) {
                return ArrayList()
            }
            val result: MutableList<ImConfig?> =
                searchServer.getImConfigList(null, LIME.IM_FULL_NAME)
            return result.filterNotNull().toMutableList()
        }

    /**
     * Returns the record count for a table.
     * 
     * @param tableName the table to count records for
     * @return the number of records in the table, or 0 if the server is not initialized
     */
    fun countRecords(tableName: String?): Int {
        // Return 0 if server is not initialized (e.g., in test mode)
        if (searchServer == null) {
            return 0
        }
        return searchServer.countRecords(tableName)
    }


    val keyboardList: MutableList<Keyboard>
        /**
         * Returns a list of available keyboards.
         * 
         * @return a non-null list of `Keyboard` objects; may be empty
         */
        get() {
            val keyboards = searchServer?.keyboard
            return keyboards?.filterNotNull()?.toMutableList() ?: ArrayList()
        }

    /**
     * Returns the currently configured keyboard for an IM table, or null
     * when the IM has no `keyboard` kv row or the referenced keyboard code
     * is not registered. Used by the Settings UI to display the IM's actual
     * current keyboard description (was previously showing the IM's full
     * name by mistake).
     * 
     * @param table the IM table / code (e.g. "pinyin", "dayi")
     * @return the matching `Keyboard`, or null
     */
    fun getCurrentKeyboard(table: String?): Keyboard? {
        if (searchServer == null || table == null || table.isEmpty()) return null
        // The im row that holds the keyboard mapping has title='keyboard'; the
        // keyboard CODE is in the `keyboard` column (the `desc` column carries
        // the human-readable name like "LIME+數字列鍵盤"). `getImConfig` returns
        // the `desc` column, which would resolve to nothing — read the row
        // directly via getImConfigList so we can pick up the `keyboard` column.
        val rows: MutableList<ImConfig?> =
            searchServer.getImConfigList(table, LIME.DB_IM_COLUMN_KEYBOARD)
        if (rows == null || rows.isEmpty()) return null
        val kbCode: String? = rows.get(0)?.keyboard
        if (kbCode == null || kbCode.isEmpty()) return null
        return searchServer.getKeyboardConfig(kbCode)
    }

    /**
     * Sets the keyboard for an IM table.
     * 
     * @param table the IM table name
     * @param keyboard the `Keyboard` to set for the table
     */
    fun setIMKeyboard(table: String?, keyboard: Keyboard) {
        try {
            searchServer?.setIMKeyboard(table, keyboard)
            val view = manageImView
            if (view != null) {
                // Signal completion and refresh the list so UI shows latest data
                view.refreshRecordList()
                if (DEBUG) Log.i(TAG, "setIMKeyboard(): updated keyboard for table=" + table)
            }
        } catch (e: Exception) {
            handleError(manageImView, "Failed to set keyboard", e)
        }
    }

    /**
     * Updates user-editable IM metadata in the shared `im` table.
     * 
     * 
     * Mirrors the iOS ManageImController path: display name is stored as
     * `title='name'` and version as `title='version'` for the IM code.
     * 
     * @param table the IM table / code
     * @param name the display name; must not be blank
     * @param version the version label; blank is allowed
     * @return true when values were persisted, false when validation or storage failed
     */
    fun updateIMMetadata(table: String?, name: String?, version: String?): Boolean {
        val trimmedTable = if (table == null) "" else table.trim { it <= ' ' }
        val trimmedName = if (name == null) "" else name.trim { it <= ' ' }
        val trimmedVersion = if (version == null) "" else version.trim { it <= ' ' }
        if (trimmedTable.isEmpty() || trimmedName.isEmpty()) {
            return false
        }

        try {
            return updateIMMetadataField(trimmedTable, "name", trimmedName)
                    && updateIMMetadataField(trimmedTable, "version", trimmedVersion)
        } catch (e: Exception) {
            handleError(manageImView, "Failed to update IM metadata: " + e.message, e)
            return false
        }
    }

    /**
     * Updates one editable IM metadata field.
     * 
     * @param table the IM table / code
     * @param field either `name`, `version`, or `limeendkey`
     * @param value the value to store; blank is allowed for version and limeendkey
     * @return true when the value was persisted
     */
    fun updateIMMetadataField(table: String?, field: String?, value: String?): Boolean {
        val trimmedTable = if (table == null) "" else table.trim { it <= ' ' }
        val trimmedField = if (field == null) "" else field.trim { it <= ' ' }
        val trimmedValue = if (value == null) "" else value.trim { it <= ' ' }
        if (trimmedTable.isEmpty()) return false
        if (("name" != trimmedField) && ("version" != trimmedField) && !LIME.IM_LIME_ENDKEY.equals(
                trimmedField
            )
        ) return false
        if ("name" == trimmedField && trimmedValue.isEmpty()) return false

        try {
            val dbServer: DBServer? = DBServer.getInstance()
            if (dbServer != null) {
                dbServer.setImConfig(trimmedTable, trimmedField, trimmedValue)
            } else if (searchServer != null) {
                searchServer.setImConfig(trimmedTable, trimmedField, trimmedValue)
            } else {
                return false
            }
            if (searchServer != null) {
                searchServer.initialCache()
            }
            return true
        } catch (e: Exception) {
            handleError(manageImView, "Failed to update IM metadata field: " + e.message, e)
            return false
        }
    }

    /**
     * Sets the enabled/disabled state of an IM entry in the `im` table.
     * 
     * 
     * Updates the `disable` column for the row with the given id.
     * The change is in-memory on the ImConfig object at call-site; this method
     * persists it to the database so the state survives restarts.
     * 
     * @param id      the `_id` of the IM row to update
     * @param enabled `true` to enable (disable=false), `false` to disable
     */
    fun setImEnabled(id: Int, enabled: Boolean) {
        val searchServer = searchServer
        if (searchServer == null) {
            if (DEBUG) Log.e(TAG, "SearchServer not initialized")
            return
        }
        executor.submit(Runnable {
            try {
                val cv: ContentValues = ContentValues()
                cv.put(LIME.DB_IM_COLUMN_DISABLE, (!enabled).toString())
                searchServer.updateRecord(
                    LIME.DB_TABLE_IM, cv,
                    LIME.DB_IM_COLUMN_ID + " = ?",
                    arrayOf<String?>(id.toString())
                )
                if (DEBUG) Log.i(TAG, "setImEnabled(): id=" + id + ", enabled=" + enabled)
            } catch (e: Exception) {
                handleError(manageImView, "Failed to set IM enabled: " + e.message, e)
            }
        })
    }

    /**
     * Exposes the SearchServer instance for callers that need to run background tasks.
     * 
     * @return the `SearchServer` used by this controller; may be null in tests
     */
    fun getSearchServer(): SearchServer {
        return this.searchServer!!
    }


    companion object {
        private const val DEBUG = false
        private const val TAG = "ManageImController"
    }
}
