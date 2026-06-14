package net.toload.main.hd.keepass

import android.annotation.TargetApi
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.preference.PreferenceManager
import net.toload.main.hd.R
import java.net.URI
import java.util.Locale

@TargetApi(Build.VERSION_CODES.O)
class LimeAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        Thread {
            try {
                if (cancellationSignal.isCanceled) return@Thread
                val targets = structure.findAutofillTargets()
                if (!targets.canFillCredentials()) {
                    callback.onSuccess(null)
                    return@Thread
                }
                if (!isKeepassAutofillConfigured()) {
                    callback.onSuccess(null)
                    return@Thread
                }
                val entries = loadKeepassEntries()
                val matchedEntries = entries.matchFor(targets).take(maxDatasets)
                if (cancellationSignal.isCanceled) {
                    return@Thread
                }

                val responseBuilder = FillResponse.Builder()
                var hasDataset = false
                if (matchedEntries.isNotEmpty()) {
                    val repository = createKeepassRepository()
                    matchedEntries.forEach { entry ->
                        val dataset =
                            if (KeepassAutofillLock.isUnlocked(this)) {
                                buildUnlockedDataset(repository.unlockEntry(entry), targets)
                            } else {
                                buildLockedDataset(entry, targets)
                            }
                        dataset?.let { responseBuilder.addDataset(it) }
                    }
                    hasDataset = true
                }
                if (!hasDataset) {
                    buildSelectionDataset(targets)?.let { dataset ->
                        responseBuilder.addDataset(dataset)
                        hasDataset = true
                    }
                }
                callback.onSuccess(if (hasDataset) responseBuilder.build() else null)
            } catch (e: Exception) {
                val structure = request.fillContexts.lastOrNull()?.structure
                val fallbackTargets = structure?.findAutofillTargets()
                val fallbackDataset = fallbackTargets
                    ?.takeIf { it.canFillCredentials() && isKeepassAutofillConfigured() }
                    ?.let { buildSelectionDataset(it) }
                callback.onSuccess(
                    fallbackDataset?.let { dataset ->
                        FillResponse.Builder()
                            .addDataset(dataset)
                            .build()
                    },
                )
            }
        }.start()
    }

    override fun onSaveRequest(
        request: SaveRequest,
        callback: SaveCallback,
    ) {
        callback.onSuccess()
    }

    private fun loadKeepassEntries(): List<KeepassEntry> {
        return createKeepassRepository().openEntries()
    }

    private fun createKeepassRepository(): KeepassRepository {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val databasePath = prefs.getString(keyDatabasePath, "").orEmpty()
        return KeepassRepository(
            storageClient = KeepassStorageClient(
                context = applicationContext,
                webDavUsername = prefs.getString(keyWebDavUsername, "").orEmpty(),
                webDavPassword = prefs.getString(keyWebDavPassword, "").orEmpty(),
                ftpUsername = prefs.getString(keyFtpUsername, "").orEmpty(),
                ftpPassword = prefs.getString(keyFtpPassword, "").orEmpty(),
            ),
            databasePath = databasePath,
            keyFilePath = prefs.getString(keyDatabaseKey, "").orEmpty(),
            password = prefs.getString(keyDatabasePassword, "").orEmpty(),
        )
    }

    private fun isKeepassAutofillConfigured(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(keyEnabled, false) &&
            prefs.getString(keyDatabasePath, "").orEmpty().isNotBlank()
    }

    private fun buildUnlockedDataset(entry: KeepassEntry, targets: AutofillTargets): Dataset? {
        val presentation = createPresentation(entry)
        val builder = Dataset.Builder()
        var hasValue = false

        targets.usernameId?.let { id ->
            if (entry.username.isNotBlank()) {
                builder.setAutofillValue(id, entry.username, presentation)
                hasValue = true
            }
        }
        targets.passwordId?.let { id ->
            if (entry.password.isNotBlank()) {
                builder.setAutofillValue(id, entry.password, presentation)
                hasValue = true
            }
        }
        return if (hasValue) builder.build() else null
    }

    private fun buildLockedDataset(entry: KeepassEntry, targets: AutofillTargets): Dataset? {
        val presentation = createLockedPresentation(entry.title.ifBlank { getString(R.string.keepass_entry_default_title) })
        return buildAuthenticatedDataset(presentation, targets, entry.id.toString())
    }

    private fun buildSelectionDataset(targets: AutofillTargets): Dataset? {
        val presentation = createLockedPresentation(getString(R.string.keepass_autofill_select_entry))
        return buildAuthenticatedDataset(presentation, targets, null)
    }

    private fun buildAuthenticatedDataset(
        presentation: RemoteViews,
        targets: AutofillTargets,
        entryId: String?,
    ): Dataset? {
        val builder = createPresentationDatasetBuilder(presentation)
        builder.setAuthentication(createAuthIntent(targets, entryId).intentSender)
        targets.autofillIds.forEach { id ->
            builder.setAutofillPlaceholder(id, presentation)
        }
        return if (targets.autofillIds.isNotEmpty()) builder.build() else null
    }

    @Suppress("DEPRECATION")
    private fun createPresentationDatasetBuilder(presentation: RemoteViews): Dataset.Builder {
        return Dataset.Builder(presentation)
    }

    @Suppress("DEPRECATION")
    private fun Dataset.Builder.setAutofillValue(
        id: AutofillId,
        value: String,
        presentation: RemoteViews,
    ) {
        setValue(id, AutofillValue.forText(value), presentation)
    }

    @Suppress("DEPRECATION")
    private fun Dataset.Builder.setAutofillPlaceholder(
        id: AutofillId,
        presentation: RemoteViews,
    ) {
        setValue(id, AutofillValue.forText(autofillPlaceholder), presentation)
    }

    private fun createPresentation(entry: KeepassEntry): RemoteViews {
        return RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, entry.title.ifBlank { getString(R.string.keepass_entry_default_title) })
            setTextViewText(android.R.id.text2, entry.username.ifBlank { entry.url })
        }
    }

    private fun createLockedPresentation(title: String): RemoteViews {
        return RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, title)
            setTextViewText(android.R.id.text2, getString(R.string.keepass_autofill_locked))
        }
    }

    private fun createAuthIntent(targets: AutofillTargets, entryId: String?): PendingIntent {
        val intent = Intent(this, LimeAutofillAuthActivity::class.java).apply {
            targets.usernameId?.let { putExtra(LimeAutofillAuthActivity.extraUsernameId, it) }
            targets.passwordId?.let { putExtra(LimeAutofillAuthActivity.extraPasswordId, it) }
            entryId?.let { putExtra(LimeAutofillAuthActivity.extraEntryId, it) }
        }
        val requestCode = (entryId ?: "selection").hashCode()
        val mutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or mutableFlag,
        )
    }

    private fun AssistStructure.findAutofillTargets(): AutofillTargets {
        val collector = AutofillTargetCollector()
        activityComponent?.packageName?.takeIf { it.isNotBlank() }?.let { collector.addQueryTerm(it) }
        for (index in 0 until windowNodeCount) {
            traverseNode(getWindowNodeAt(index).rootViewNode, collector)
        }
        return collector.toTargets()
    }

    private fun traverseNode(
        node: AssistStructure.ViewNode,
        collector: AutofillTargetCollector,
    ) {
        collector.visit(node)
        for (index in 0 until node.childCount) {
            traverseNode(node.getChildAt(index), collector)
        }
    }

    private fun List<KeepassEntry>.matchFor(targets: AutofillTargets): List<KeepassEntry> {
        val fillableEntries = filter { entry -> entry.username.isNotBlank() || entry.hasPassword }
        val strongTerms = targets.queryTerms
            .map { term -> term.normalizedTerm() }
            .filter { term -> term.length >= 3 }
            .toSet()
        if (strongTerms.isEmpty()) {
            return fillableEntries.take(maxDatasets)
        }

        val matchedEntries = fillableEntries.filter { entry ->
            val entryTerms = (listOf(entry.title, entry.username, entry.url, entry.notes) +
                entry.additionalUrls +
                entry.extraSearchValues)
                .flatMap { value -> value.entrySearchTerms() }
                .toSet()
            strongTerms.any { term -> entryTerms.any { entryTerm -> entryTerm.contains(term) || term.contains(entryTerm) } }
        }
        return if (matchedEntries.isNotEmpty()) matchedEntries else emptyList()
    }

    private fun String.entrySearchTerms(): List<String> {
        val raw = trim()
        if (raw.isBlank()) {
            return emptyList()
        }
        val terms = mutableListOf(raw.normalizedTerm())
        runCatching {
            val uri = if (raw.contains("://")) URI(raw) else URI("https://$raw")
            uri.host?.let { host ->
                terms.add(host.normalizedHost())
                host.split('.')
                    .filter { part -> part.length >= 3 }
                    .forEach { part -> terms.add(part.normalizedTerm()) }
            }
        }
        return terms.distinct()
    }

    private fun String.normalizedHost(): String {
        return lowercase(Locale.US)
            .removePrefix("www.")
            .trim()
    }

    private fun String.normalizedTerm(): String {
        return lowercase(Locale.US)
            .removePrefix("androidapp://")
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trim()
    }

    private class AutofillTargetCollector {
        private var usernameId: AutofillId? = null
        private var passwordId: AutofillId? = null
        private var previousTextId: AutofillId? = null
        private val queryTerms = linkedSetOf<String>()

        fun addQueryTerm(term: String) {
            queryTerms.add(term)
        }

        fun visit(node: AssistStructure.ViewNode) {
            node.webDomain?.takeIf { it.isNotBlank() }?.let { queryTerms.add(it) }
            node.idPackage?.takeIf { it.isNotBlank() }?.let { queryTerms.add(it) }
            node.idEntry?.takeIf { it.isNotBlank() }?.let { queryTerms.add(it) }
            node.hint?.takeIf { it.isNotBlank() }?.let { queryTerms.add(it) }
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { queryTerms.add(it) }
            node.htmlInfo?.tag?.takeIf { it.isNotBlank() }?.let { queryTerms.add(it) }
            node.htmlInfo?.attributes?.forEach { attribute ->
                attribute.first?.toString()?.takeIf { it.isNotBlank() }?.let { queryTerms.add(it) }
                attribute.second?.toString()?.takeIf { it.isNotBlank() }?.let { queryTerms.add(it) }
            }

            val autofillId = node.autofillId ?: return
            when {
                passwordId == null && node.looksLikePasswordField() -> {
                    passwordId = autofillId
                    if (usernameId == null) {
                        usernameId = previousTextId
                    }
                }
                usernameId == null && node.looksLikeUsernameField() -> {
                    usernameId = autofillId
                }
                node.looksLikeTextField() -> {
                    previousTextId = autofillId
                }
            }
        }

        fun toTargets(): AutofillTargets {
            return AutofillTargets(
                usernameId = usernameId,
                passwordId = passwordId,
                queryTerms = queryTerms,
            )
        }

        private fun AssistStructure.ViewNode.looksLikeUsernameField(): Boolean {
            return autofillHints.hasHint("username", "email", "account", "login") ||
                htmlAttribute("type").equals("email", ignoreCase = true) ||
                containsAnyFieldSignal("user", "username", "email", "account", "login", "userid", "帳號", "使用者", "電子郵件")
        }

        private fun AssistStructure.ViewNode.looksLikePasswordField(): Boolean {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return autofillHints.hasHint("password") ||
                htmlAttribute("type").equals("password", ignoreCase = true) ||
                containsAnyFieldSignal("current-password", "new-password") ||
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                containsAnyFieldSignal("password", "passwd", "pwd", "pass", "密碼")
        }

        private fun AssistStructure.ViewNode.looksLikeTextField(): Boolean {
            val htmlType = htmlAttribute("type").lowercase(Locale.US)
            return autofillType == View.AUTOFILL_TYPE_TEXT ||
                htmlType in setOf("text", "email", "tel", "number") ||
                (inputType and InputType.TYPE_CLASS_TEXT) == InputType.TYPE_CLASS_TEXT
        }

        private fun AssistStructure.ViewNode.containsAnyFieldSignal(vararg needles: String): Boolean {
            val haystack = listOfNotNull(
                idEntry,
                hint,
                text?.toString(),
                autofillHints?.joinToString(" "),
                className?.toString(),
                htmlSignalText(),
            ).joinToString(" ").lowercase(Locale.US)
            return needles.any { needle -> haystack.contains(needle.lowercase(Locale.US)) }
        }

        private fun AssistStructure.ViewNode.htmlAttribute(name: String): String {
            return htmlInfo?.attributes
                ?.firstOrNull { attribute -> attribute.first?.toString().equals(name, ignoreCase = true) }
                ?.second
                ?.toString()
                .orEmpty()
        }

        private fun AssistStructure.ViewNode.htmlSignalText(): String {
            val info = htmlInfo ?: return ""
            val attributes = info.attributes
                ?.joinToString(" ") { attribute ->
                    listOfNotNull(attribute.first?.toString(), attribute.second?.toString()).joinToString(" ")
                }
                .orEmpty()
            return listOfNotNull(info.tag, attributes).joinToString(" ")
        }

        private fun Array<String>?.hasHint(vararg needles: String): Boolean {
            if (this == null) return false
            return any { hint ->
                needles.any { needle -> hint.equals(needle, ignoreCase = true) || hint.contains(needle, ignoreCase = true) }
            }
        }
    }

    private data class AutofillTargets(
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
        val queryTerms: Set<String>,
    ) {
        val autofillIds: List<AutofillId> = listOfNotNull(usernameId, passwordId).distinct()

        fun canFillCredentials(): Boolean {
            return autofillIds.isNotEmpty()
        }
    }

    private companion object {
        private const val maxDatasets = 5
        private const val autofillPlaceholder = "PLACEHOLDER"
        private const val keyEnabled = "enabled"
        private const val keyDatabasePath = "database_path"
        private const val keyDatabaseKey = "database_key"
        private const val keyDatabasePassword = "database_password"
        private const val keyWebDavUsername = "webdav_username"
        private const val keyWebDavPassword = "webdav_password"
        private const val keyFtpUsername = "ftp_username"
        private const val keyFtpPassword = "ftp_password"
    }
}
