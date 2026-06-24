package net.toload.main.hd.keepass

import android.content.Context
import app.keemobile.kotpass.constants.BasicField
import app.keemobile.kotpass.constants.PredefinedIcon
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.database.modifiers.removeEntry
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.TimeData
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID

class KeepassRepository(
    private val storageClient: KeepassStorageClient,
    private val databasePath: String,
    private val keyFilePath: String,
    private val password: String,
) {
    fun openEntries(): List<KeepassEntry> {
        storageClient.prepareLocalDatabase(databasePath)
        val cacheKey = entriesCacheKey()
        synchronized(entriesCacheLock) {
            entriesCache?.takeIf { cached -> cached.key == cacheKey }?.let { cached ->
                return cached.entries
            }
        }
        entryCacheDb.read(cacheKey)?.let { cachedEntries ->
            synchronized(entriesCacheLock) {
                entriesCache = EntriesCache(cacheKey, cachedEntries)
            }
            return cachedEntries
        }
        val database = openDatabase()
        val entries = database.content.group.collectEntries()
        entryCacheDb.write(cacheKey, entries, createCacheCrypto())
        val cachedEntries = entryCacheDb.read(cacheKey) ?: entries.withLockedSecrets()
        synchronized(entriesCacheLock) {
            entriesCache = EntriesCache(cacheKey, cachedEntries)
        }
        return cachedEntries
    }

    fun verifyOpen() {
        openEntries()
    }

    fun unlockEntry(entry: KeepassEntry): KeepassEntry {
        return entry.copy(
            password = entry.encryptedPassword
                .takeIf { it.isNotBlank() }
                ?.let { encrypted -> createCacheCrypto().decrypt(encrypted) }
                ?: entry.password,
            imeFields = entry.imeFields.map { field ->
                field.encryptedValue
                    .takeIf { it.isNotBlank() }
                    ?.let { encrypted ->
                        field.copy(value = createCacheCrypto().decrypt(encrypted), encryptedValue = "")
                    }
                    ?: field
            },
            encryptedPassword = "",
        )
    }

    fun pendingLocalChangeKey(): String? {
        return storageClient.pendingLocalChangeKey(databasePath)
    }

    fun synchronizeDatabase(): KeepassSyncResult {
        invalidateEntriesCache()
        storageClient.prepareLocalDatabase(databasePath)
        val result =
            storageClient.synchronize(databasePath) { baseBytes, localBytes, remoteBytes ->
                val mergedDatabase =
                    KeepassMergeEngine().merge(
                        base = decodeDatabase(baseBytes),
                        local = decodeDatabase(localBytes),
                        remote = decodeDatabase(remoteBytes),
                    )
                encodeDatabase(mergedDatabase)
            }
        rebuildEncryptedEntryCacheFromLocalDatabase()
        clearInMemoryEntriesCache()
        return result
    }

    fun addEntry(input: KeepassEntryInput) {
        invalidateEntriesCache()
        val database = openDatabase()
        val newEntry = createEntry(input)
        val updatedDatabase =
            database.modifyParentGroup {
                copy(entries = entries + newEntry)
            }
        val output = ByteArrayOutputStream()
        updatedDatabase.encode(output)
        storageClient.write(databasePath, output.toByteArray())
        rebuildEncryptedEntryCacheFromLocalDatabase()
        clearInMemoryEntriesCache()
    }

    fun deleteEntry(id: UUID) {
        invalidateEntriesCache()
        val database = openDatabase()
        val updatedDatabase = database.removeEntry(id)
        storageClient.write(databasePath, encodeDatabase(updatedDatabase))
        rebuildEncryptedEntryCacheFromLocalDatabase()
        clearInMemoryEntriesCache()
    }

    private fun entriesCacheKey(): String {
        return listOf(
            databasePath,
            keyFilePath,
            password.hashCode().toString(),
            storageClient.cacheStamp(databasePath),
            keyFilePath.takeIf { it.isNotBlank() }?.let { storageClient.cacheStamp(it) }.orEmpty(),
        ).joinToString("|")
    }

    private val entryCacheDb: KeepassEntryCacheDb
        get() = KeepassEntryCacheDb(storageClient.context)

    private fun openDatabase(): KeePassDatabase {
        val databaseBytes = storageClient.read(databasePath)
        return decodeDatabase(databaseBytes)
    }

    private fun rebuildEncryptedEntryCacheFromLocalDatabase() {
        val entries = openDatabase().content.group.collectEntries()
        entryCacheDb.write(entriesCacheKey(), entries, createCacheCrypto())
    }

    private fun decodeDatabase(databaseBytes: ByteArray): KeePassDatabase {
        val credentials = createCredentials()
        return databaseBytes.inputStream().use { input ->
            KeePassDatabase.decode(input, credentials)
        }
    }

    private fun encodeDatabase(database: KeePassDatabase): ByteArray {
        val output = ByteArrayOutputStream()
        database.encode(output)
        return output.toByteArray()
    }

    private fun createCredentials(): Credentials {
        val keyBytes = keyFilePath.takeIf { it.isNotBlank() }?.let { storageClient.read(it) }
        val passwordValue = password.takeIf { it.isNotBlank() }?.let { EncryptedValue.fromString(it) }
        return when {
            passwordValue != null && keyBytes != null -> Credentials.from(passwordValue, keyBytes)
            passwordValue != null -> Credentials.from(passwordValue)
            keyBytes != null -> Credentials.from(keyBytes)
            else -> throw IllegalArgumentException("請先輸入密碼或選擇金鑰")
        }
    }

    private fun createCacheCrypto(): KeepassCacheCrypto {
        val keyBytes = keyFilePath.takeIf { it.isNotBlank() }?.let { storageClient.read(it) }
        return KeepassCacheCrypto(password = password, keyFileBytes = keyBytes)
    }

    private fun List<KeepassEntry>.withLockedSecrets(): List<KeepassEntry> {
        val crypto = createCacheCrypto()
        return map { entry ->
            entry.copy(
                password = "",
                encryptedPassword = entry.password.takeIf { it.isNotBlank() }?.let { crypto.encrypt(it) }.orEmpty(),
                imeFields = entry.imeFields.map { field ->
                    field.copy(
                        value = "",
                        encryptedValue = field.value.takeIf { it.isNotBlank() }?.let { crypto.encrypt(it) }.orEmpty(),
                    )
                },
            )
        }
    }

    private fun Group.collectEntries(): List<KeepassEntry> {
        return entries.map { entry -> entry.toKeepassEntry() } +
            groups.flatMap { group -> group.collectEntries() }
    }

    private fun Entry.toKeepassEntry(): KeepassEntry {
        val customFields = fields.keys
            .filter { key -> key !in basicFieldKeys }
            .associateWith { key -> fields[key].contentOrEmpty() }
        val kprpcValues = customFields
            .filterKeys { key -> key.contains(kprpcJsonField, ignoreCase = true) }
            .values
            .flatMap { value -> parseKprpcJson(value) }
            .distinct()
        val additionalUrls = kprpcValues.filter { value -> value.looksLikeUrl() }
        val imeFields = keepassImeFields(customFields)
        val imeFieldNames = keepassImeFieldNames()
        return KeepassEntry(
            id = uuid,
            title = fields.title.contentOrEmpty(),
            username = fields.userName.contentOrEmpty(),
            password = fields.password.contentOrEmpty(),
            url = fields.url.contentOrEmpty(),
            notes = fields.notes.contentOrEmpty(),
            additionalUrls = additionalUrls,
            extraSearchValues = (
                customFields
                    .filterKeys { key -> key !in imeFieldNames }
                    .values +
                    kprpcValues
                ).filter { value -> value.isNotBlank() }.distinct(),
            imeFields = imeFields,
        )
    }

    private fun keepassImeFields(customFields: Map<String, String>): List<KeepassImeField> {
        return keepassImeFieldLabels.mapNotNull { (sourceName, keyboardLabel) ->
            customFields[sourceName]
                ?.takeIf { value -> value.isNotBlank() }
                ?.let { value -> KeepassImeField(label = keyboardLabel, value = value) }
        }
    }

    private fun keepassImeFieldNames(): Set<String> {
        return keepassImeFieldLabels.keys
    }

    private fun EntryValue?.contentOrEmpty(): String {
        return this?.content.orEmpty()
    }

    private fun parseKprpcJson(value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return emptyList()
        return runCatching {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).collectJsonStrings()
                trimmed.startsWith("[") -> JSONArray(trimmed).collectJsonStrings()
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.collectJsonStrings(): List<String> {
        val values = mutableListOf<String>()
        keys().forEach { key ->
            values.add(key)
            collectJsonValue(opt(key), values)
        }
        return values
    }

    private fun JSONArray.collectJsonStrings(): List<String> {
        val values = mutableListOf<String>()
        for (index in 0 until length()) {
            collectJsonValue(opt(index), values)
        }
        return values
    }

    private fun collectJsonValue(value: Any?, values: MutableList<String>) {
        when (value) {
            is JSONObject -> values.addAll(value.collectJsonStrings())
            is JSONArray -> values.addAll(value.collectJsonStrings())
            is String -> value
                .takeIf { item -> item.isNotBlank() && !item.isKprpcPlaceholder() }
                ?.let { item -> values.add(item) }
        }
    }

    private fun String.isKprpcPlaceholder(): Boolean {
        return startsWith("{") && endsWith("}") && length <= 32
    }

    private fun String.looksLikeUrl(): Boolean {
        return startsWith("http://", ignoreCase = true) ||
            startsWith("https://", ignoreCase = true) ||
            startsWith("androidapp://", ignoreCase = true) ||
            contains(".")
    }

    private fun createEntry(input: KeepassEntryInput): Entry {
        val now = Instant.now()
        return Entry(
            uuid = UUID.randomUUID(),
            icon = PredefinedIcon.Key,
            times = TimeData(
                creationTime = now,
                lastAccessTime = now,
                lastModificationTime = now,
                locationChanged = now,
                expiryTime = now,
                expires = false,
                usageCount = 0,
            ),
            fields = EntryFields(
                mapOf(
                    BasicField.Title.key to EntryValue.Plain(input.title),
                    BasicField.UserName.key to EntryValue.Plain(input.username),
                    BasicField.Password.key to EntryValue.Encrypted(EncryptedValue.fromString(input.password)),
                    BasicField.Url.key to EntryValue.Plain(input.url),
                    BasicField.Notes.key to EntryValue.Plain(input.notes),
                ),
            ),
        )
    }

    private fun invalidateEntriesCache() {
        runCatching { entryCacheDb.invalidate(entriesCacheKey()) }
        clearInMemoryEntriesCache()
    }

    private fun clearInMemoryEntriesCache() {
        synchronized(entriesCacheLock) {
            entriesCache = null
        }
    }

    private data class EntriesCache(
        val key: String,
        val entries: List<KeepassEntry>,
    )

    companion object {
        private const val kprpcJsonField = "KPRPC JSON"
        private val keepassImeFieldLabels =
            linkedMapOf(
                "持卡人" to "持卡人",
                "號碼" to "號碼",
                "exp_date" to "過期",
                "CVV" to "CVV",
            )
        private val entriesCacheLock = Any()
        private var entriesCache: EntriesCache? = null
        private val basicFieldKeys = setOf(
            BasicField.Title.key,
            BasicField.UserName.key,
            BasicField.Password.key,
            BasicField.Url.key,
            BasicField.Notes.key,
        )

        fun clearEntryCache(context: Context) {
            synchronized(entriesCacheLock) {
                entriesCache = null
            }
            KeepassEntryCacheDb(context.applicationContext).clearAll()
        }
    }
}
