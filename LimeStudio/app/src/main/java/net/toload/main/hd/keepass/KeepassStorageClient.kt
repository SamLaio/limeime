package net.toload.main.hd.keepass

import android.content.Context
import android.net.Uri
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class KeepassStorageClient(
    internal val context: Context,
    private val webDavUsername: String,
    private val webDavPassword: String,
    private val ftpUsername: String,
    private val ftpPassword: String,
) {
    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    fun read(path: String): ByteArray {
        return if (isRemotePath(path)) {
            readCachedRemote(path)
        } else {
            readDirect(path)
        }
    }

    fun prepareLocalDatabase(path: String) {
        read(path)
    }

    fun cacheStamp(path: String): String {
        return if (isRemotePath(path)) {
            val cache = cacheFiles(path)
            if (cache.database.exists()) {
                "${cache.database.lastModified()}:${cache.database.length()}"
            } else {
                "remote-missing"
            }
        } else {
            when {
                path.startsWith("content://") ->
                    runCatching { "content:${readContentUri(path).sha256Hex()}" }
                        .getOrElse { "content:${path.hashCode()}" }
                else ->
                    directFile(path)?.let { file ->
                        if (file.exists()) "${file.lastModified()}:${file.length()}" else "missing"
                    } ?: "content:${path.hashCode()}"
            }
        }
    }

    fun write(path: String, bytes: ByteArray) {
        if (isRemotePath(path)) {
            writeCachedRemote(path, bytes)
        } else {
            writeDirect(path, bytes)
        }
    }

    fun synchronize(
        path: String,
        mergeConflict: ((baseBytes: ByteArray, localBytes: ByteArray, remoteBytes: ByteArray) -> ByteArray)? = null,
    ): KeepassSyncResult {
        if (!isRemotePath(path)) {
            readDirect(path)
            return KeepassSyncResult("本機資料庫已可開啟")
        }

        val cache = cacheFiles(path)
        if (!cache.database.exists()) {
            val remoteBytes = readDirect(path)
            val remoteHash = remoteBytes.sha256Hex()
            writeCache(cache, remoteBytes, remoteHash, remoteHash, remoteBytes)
            return KeepassSyncResult("已下載遠端資料庫到本地快取")
        }

        val localBytes = cache.database.readBytes()
        val localHash = localBytes.sha256Hex()
        val baseHash = cache.baseHash.readTextOrNull()
        val hasLocalChanges = baseHash != null && localHash != baseHash
        val remoteBytes = readDirect(path)
        val remoteHash = remoteBytes.sha256Hex()

        return when {
            baseHash == null -> {
                writeCache(cache, remoteBytes, remoteHash, remoteHash, remoteBytes)
                KeepassSyncResult("已建立本地快取基準")
            }
            remoteHash != baseHash && hasLocalChanges -> {
                val baseBytes =
                    if (cache.baseDatabase.exists()) {
                        cache.baseDatabase.readBytes()
                    } else {
                        throw IllegalStateException("缺少同步基準，請先重新下載資料庫")
                    }
                val merger =
                    mergeConflict
                        ?: throw IllegalStateException("遠端資料庫與本地快取都有變更，請先手動合併")
                val mergedBytes = merger(baseBytes, localBytes, remoteBytes)
                val mergedHash = mergedBytes.sha256Hex()
                writeDirect(path, mergedBytes)
                writeCache(cache, mergedBytes, mergedHash, mergedHash, mergedBytes)
                KeepassSyncResult("已差分合併並同步資料庫")
            }
            remoteHash != baseHash -> {
                writeCache(cache, remoteBytes, remoteHash, remoteHash, remoteBytes)
                KeepassSyncResult("已從遠端更新本地快取")
            }
            hasLocalChanges -> {
                writeDirect(path, localBytes)
                writeCache(cache, localBytes, localHash, localHash, localBytes)
                KeepassSyncResult("已將本地快取上傳到遠端資料庫")
            }
            else -> KeepassSyncResult("資料庫已同步")
        }
    }

    fun pendingLocalChangeKey(path: String): String? {
        if (!isRemotePath(path)) {
            return null
        }
        val cache = cacheFiles(path)
        if (!cache.database.exists()) {
            return null
        }
        val baseHash = cache.baseHash.readTextOrNull() ?: return null
        val localHash = cache.database.readBytes().sha256Hex()
        return if (localHash != baseHash) {
            "$path|$baseHash|$localHash"
        } else {
            null
        }
    }

    private fun readDirect(path: String): ByteArray {
        return when {
            path.startsWith("content://") -> readContentUri(path)
            path.startsWith("file://") -> File(Uri.parse(path).path.orEmpty()).readBytes()
            path.startsWith("http://") || path.startsWith("https://") -> readWebDav(path)
            path.startsWith("ftp://") -> readFtp(path)
            else -> File(path).readBytes()
        }
    }

    private fun directFile(path: String): File? {
        return when {
            path.startsWith("file://") -> File(Uri.parse(path).path.orEmpty())
            path.startsWith("content://") -> null
            path.startsWith("http://") || path.startsWith("https://") || path.startsWith("ftp://") -> null
            else -> File(path)
        }
    }

    private fun writeDirect(path: String, bytes: ByteArray) {
        when {
            path.startsWith("content://") -> writeContentUri(path, bytes)
            path.startsWith("file://") -> File(Uri.parse(path).path.orEmpty()).writeBytes(bytes)
            path.startsWith("http://") || path.startsWith("https://") -> writeWebDav(path, bytes)
            path.startsWith("ftp://") -> writeFtp(path, bytes)
            else -> File(path).writeBytes(bytes)
        }
    }

    private fun readCachedRemote(path: String): ByteArray {
        val cache = cacheFiles(path)
        if (cache.database.exists()) {
            return cache.database.readBytes()
        }
        val remoteBytes = readDirect(path)
        val remoteHash = remoteBytes.sha256Hex()
        writeCache(cache, remoteBytes, remoteHash, remoteHash, remoteBytes)
        return remoteBytes
    }

    private fun writeCachedRemote(path: String, bytes: ByteArray) {
        val cache = cacheFiles(path)
        val baseHash = cache.baseHash.readTextOrNull() ?: bytes.sha256Hex()
        writeCache(cache, bytes, bytes.sha256Hex(), baseHash)
    }

    private fun writeCache(
        cache: CacheFiles,
        bytes: ByteArray,
        localHash: String,
        baseHash: String,
        baseBytes: ByteArray? = null,
    ) {
        cache.directory.mkdirs()
        cache.database.writeBytes(bytes)
        cache.localHash.writeText(localHash)
        cache.baseHash.writeText(baseHash)
        if (baseBytes != null) {
            cache.baseDatabase.writeBytes(baseBytes)
        }
    }

    private fun cacheFiles(path: String): CacheFiles {
        val directory = File(context.filesDir, "keepass_cache").apply { mkdirs() }
        val name = path.toByteArray(Charsets.UTF_8).sha256Hex()
        return CacheFiles(
            directory = directory,
            database = File(directory, "$name.kdbx"),
            baseDatabase = File(directory, "$name.base.kdbx"),
            localHash = File(directory, "$name.version"),
            baseHash = File(directory, "$name.baseversion"),
        )
    }

    private fun readContentUri(path: String): ByteArray {
        val uri = Uri.parse(path)
        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IllegalArgumentException("無法開啟檔案")
    }

    private fun writeContentUri(path: String, bytes: ByteArray) {
        val uri = Uri.parse(path)
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
        } ?: throw IllegalArgumentException("無法寫入檔案")
    }

    private fun readWebDav(path: String): ByteArray {
        val request =
            Request.Builder()
                .url(path)
                .get()
                .applyWebDavAuth()
                .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("WebDav 讀取失敗：HTTP ${response.code}")
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun writeWebDav(path: String, bytes: ByteArray) {
        val body = bytes.toRequestBody("application/octet-stream".toMediaType())
        val request =
            Request.Builder()
                .url(path)
                .put(body)
                .applyWebDavAuth()
                .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("WebDav 寫入失敗：HTTP ${response.code}")
            }
        }
    }

    private fun Request.Builder.applyWebDavAuth(): Request.Builder {
        if (webDavUsername.isNotBlank()) {
            header("Authorization", Credentials.basic(webDavUsername, webDavPassword))
        }
        return this
    }

    private fun readFtp(path: String): ByteArray {
        val output = ByteArrayOutputStream()
        withFtpClient(path) { client, remotePath ->
            if (!client.retrieveFile(remotePath, output)) {
                throw IllegalStateException("FTP 讀取失敗：${client.replyString?.trim().orEmpty()}")
            }
        }
        return output.toByteArray()
    }

    private fun writeFtp(path: String, bytes: ByteArray) {
        withFtpClient(path) { client, remotePath ->
            if (!client.storeFile(remotePath, bytes.inputStream())) {
                throw IllegalStateException("FTP 寫入失敗：${client.replyString?.trim().orEmpty()}")
            }
        }
    }

    private fun withFtpClient(path: String, block: (FTPClient, String) -> Unit) {
        val uri = URI(path)
        val host = uri.host ?: throw IllegalArgumentException("FTP 主機不可為空")
        val port = if (uri.port > 0) uri.port else 21
        val remotePath = uri.path?.takeIf { it.isNotBlank() } ?: "/"
        val client = FTPClient()

        try {
            client.connect(host, port)
            if (!client.login(ftpUsername, ftpPassword)) {
                throw IllegalStateException("FTP 登入失敗：${client.replyString?.trim().orEmpty()}")
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            block(client, remotePath)
        } finally {
            if (client.isConnected) {
                try {
                    client.logout()
                } finally {
                    client.disconnect()
                }
            }
        }
    }

    private fun isRemotePath(path: String): Boolean {
        return path.startsWith("http://") || path.startsWith("https://") || path.startsWith("ftp://")
    }

    private fun ByteArray.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun File.readTextOrNull(): String? {
        return if (exists()) readText() else null
    }

    private data class CacheFiles(
        val directory: File,
        val database: File,
        val baseDatabase: File,
        val localHash: File,
        val baseHash: File,
    )
}
