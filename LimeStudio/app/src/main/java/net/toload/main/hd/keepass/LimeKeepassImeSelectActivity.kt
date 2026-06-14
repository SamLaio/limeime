package net.toload.main.hd.keepass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import net.toload.main.hd.R
import java.net.URLDecoder
import java.util.Locale

class LimeKeepassImeSelectActivity : FragmentActivity() {
    private lateinit var searchField: EditText
    private lateinit var listContainer: LinearLayout
    private var entries: List<KeepassEntry> = emptyList()
    private var finishedWithResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        buildContentView()
        loadEntries()
    }

    override fun finish() {
        if (!finishedWithResult) {
            finishWithEntry(null)
        }
        super.finish()
    }

    private fun buildContentView() {
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(20f), dpToPx(20f), dpToPx(20f), dpToPx(12f))
            }

        TextView(this).apply {
            text = getString(R.string.keepass_browse_database_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, 0, 0, dpToPx(10f))
            root.addView(this)
        }

        TextView(this).apply {
            text = PreferenceManager.getDefaultSharedPreferences(this@LimeKeepassImeSelectActivity)
                .getString(keyDatabasePath, "")
                .orEmpty()
            maxLines = 2
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFF777777.toInt())
            setPadding(0, 0, 0, dpToPx(10f))
            root.addView(this)
        }

        searchField =
            EditText(this).apply {
                hint = getString(R.string.keepass_search_hint)
                setSingleLine(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(0, dpToPx(8f), 0, dpToPx(8f))
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            renderEntries()
                        }

                        override fun afterTextChanged(s: Editable?) = Unit
                    },
                )
            }
        root.addView(
            searchField,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        listContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
        root.addView(
            ScrollView(this).apply { addView(listContainer) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        TextView(this).apply {
            text = getString(R.string.dialog_cancel)
            gravity = Gravity.CENTER
            setTextColor(0xFF008577.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, dpToPx(12f), 0, dpToPx(8f))
            setOnClickListener { finish() }
            root.addView(
                this,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        setContentView(root)
        searchField.requestFocus()
        searchField.post {
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun loadEntries() {
        Toast.makeText(this, R.string.keepass_keyboard_loading, Toast.LENGTH_SHORT).show()
        Thread {
            val result =
                runCatching {
                    createRepository()
                        .openEntries()
                        .filter { entry -> entry.hasImeFields() }
                        .sortedBy { entry -> entry.imeLabel().lowercase(Locale.getDefault()) }
                }
            runOnUiThread {
                result
                    .onSuccess { loadedEntries ->
                        entries = loadedEntries
                        renderEntries()
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this,
                            getString(R.string.keepass_open_database_failed, error.message.orEmpty()),
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
            }
        }.start()
    }

    private fun renderEntries() {
        listContainer.removeAllViews()
        val filteredEntries = filterEntries(searchField.text?.toString().orEmpty())
        if (filteredEntries.isEmpty()) {
            TextView(this).apply {
                text = getString(R.string.keepass_browse_database_empty)
                setTextColor(0xFF777777.toInt())
                setPadding(0, dpToPx(12f), 0, dpToPx(12f))
                listContainer.addView(this)
            }
            return
        }

        filteredEntries.forEach { entry ->
            listContainer.addView(createEntryRow(entry))
        }
    }

    private fun createEntryRow(entry: KeepassEntry): LinearLayout {
        val title = entry.imeLabel()
        val summary =
            listOf(
                entry.username.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.keepass_entry_username_display, it) },
                entry.url.takeIf { it.isNotBlank() },
                entry.notes.takeIf { it.isNotBlank() },
            ).filterNotNull().joinToString("\n")

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8f), dpToPx(10f), dpToPx(8f), dpToPx(10f))
            isClickable = true
            isFocusable = true
            setOnClickListener { finishWithEntry(entry) }

            TextView(this@LimeKeepassImeSelectActivity).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(0xFF2196F3.toInt())
                addView(this)
            }
            if (summary.isNotBlank()) {
                TextView(this@LimeKeepassImeSelectActivity).apply {
                    text = summary
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(0xFF777777.toInt())
                    setPadding(0, dpToPx(4f), 0, 0)
                    addView(this)
                }
            }
        }
    }

    private fun filterEntries(query: String): List<KeepassEntry> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return entries
        }
        return entries.filter { entry ->
            entry.searchValues().any { value ->
                value.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    private fun createRepository(): KeepassRepository {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return KeepassRepository(
            storageClient =
                KeepassStorageClient(
                    context = applicationContext,
                    webDavUsername = prefs.getString(keyWebDavUsername, "").orEmpty(),
                    webDavPassword = prefs.getString(keyWebDavPassword, "").orEmpty(),
                    ftpUsername = prefs.getString(keyFtpUsername, "").orEmpty(),
                    ftpPassword = prefs.getString(keyFtpPassword, "").orEmpty(),
                ),
            databasePath = prefs.getString(keyDatabasePath, "").orEmpty(),
            keyFilePath = prefs.getString(keyDatabaseKey, "").orEmpty(),
            password = prefs.getString(keyDatabasePassword, "").orEmpty(),
        )
    }

    private fun finishWithEntry(entry: KeepassEntry?) {
        finishedWithResult = true
        val unlockedEntry = entry?.let { createRepository().unlockEntry(it) }
        val intent =
            Intent(actionSelectResult)
                .setPackage(packageName)
                .putExtra(extraSelected, unlockedEntry != null)
        if (unlockedEntry != null) {
            intent
                .putExtra(extraEntryId, unlockedEntry.id.toString())
                .putExtra(extraTitle, unlockedEntry.title)
                .putExtra(extraUsername, unlockedEntry.username)
                .putExtra(extraPassword, unlockedEntry.password)
                .putExtra(extraUrl, unlockedEntry.url)
                .putExtra(extraNotes, unlockedEntry.notes)
        }
        sendBroadcast(intent)
        super.finish()
    }

    private fun KeepassEntry.hasImeFields(): Boolean {
        return title.isNotBlank() ||
            username.isNotBlank() ||
            hasPassword ||
            url.isNotBlank() ||
            notes.isNotBlank()
    }

    private fun KeepassEntry.imeLabel(): String {
        return title.ifBlank {
            url.ifBlank {
                username.ifBlank {
                    getString(R.string.keepass_entry_default_title)
                }
            }
        }
    }

    private fun KeepassEntry.searchValues(): List<String> {
        val urls = listOf(url) + additionalUrls
        return listOf(title, username, url, notes) +
            additionalUrls +
            extraSearchValues +
            urls.flatMap { value -> value.urlSearchValues() }
    }

    private fun String.urlSearchValues(): List<String> {
        if (isBlank()) return emptyList()
        val decoded = runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)
        val withoutScheme = decoded.removePrefix("https://").removePrefix("http://")
        val withoutWww = withoutScheme.removePrefix("www.")
        val host = runCatching { Uri.parse(decoded).host.orEmpty() }.getOrDefault("")
        return listOf(decoded, withoutScheme, withoutWww, host, host.removePrefix("www."))
            .filter { value -> value.isNotBlank() }
            .distinct()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics,
        ).toInt()
    }

    companion object {
        const val actionSelectResult = "net.toload.main.hd.keepass.action.IME_SELECT_RESULT"
        const val extraSelected = "net.toload.main.hd.keepass.extra.IME_ENTRY_SELECTED"
        const val extraEntryId = "net.toload.main.hd.keepass.extra.ENTRY_ID"
        const val extraTitle = "net.toload.main.hd.keepass.extra.TITLE"
        const val extraUsername = "net.toload.main.hd.keepass.extra.USERNAME"
        const val extraPassword = "net.toload.main.hd.keepass.extra.PASSWORD"
        const val extraUrl = "net.toload.main.hd.keepass.extra.URL"
        const val extraNotes = "net.toload.main.hd.keepass.extra.NOTES"

        private const val keyDatabasePath = "database_path"
        private const val keyDatabaseKey = "database_key"
        private const val keyDatabasePassword = "database_password"
        private const val keyWebDavUsername = "webdav_username"
        private const val keyWebDavPassword = "webdav_password"
        private const val keyFtpUsername = "ftp_username"
        private const val keyFtpPassword = "ftp_password"
    }
}
