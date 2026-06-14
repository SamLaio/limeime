package net.toload.main.hd.keepass

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class KeepassCacheCrypto(
    private val password: String,
    private val keyFileBytes: ByteArray?,
) {
    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val salt = ByteArray(saltSizeBytes).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(ivSizeBytes).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(salt), algorithm), GCMParameterSpec(tagSizeBits, iv))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(version, salt.encodeBase64(), iv.encodeBase64(), encrypted.encodeBase64()).joinToString(separator)
    }

    fun decrypt(payload: String): String {
        if (payload.isEmpty()) return ""
        val parts = payload.split(separator)
        if (parts.size != 4 || parts[0] != version) {
            return payload
        }
        val salt = parts[1].decodeBase64()
        val iv = parts[2].decodeBase64()
        val encrypted = parts[3].decodeBase64()
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(salt), algorithm), GCMParameterSpec(tagSizeBits, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun deriveKey(salt: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").apply {
            update("lime-keepass-cache".toByteArray(Charsets.UTF_8))
            update(0)
            update(salt)
            update(0)
            update(password.toByteArray(Charsets.UTF_8))
            update(0)
            keyFileBytes?.let { update(it) }
        }.digest()
    }

    private fun ByteArray.encodeBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun String.decodeBase64(): ByteArray {
        return Base64.decode(this, Base64.NO_WRAP)
    }

    private companion object {
        private const val version = "v1"
        private const val separator = ":"
        private const val algorithm = "AES"
        private const val transformation = "AES/GCM/NoPadding"
        private const val ivSizeBytes = 12
        private const val saltSizeBytes = 16
        private const val tagSizeBits = 128
        private val secureRandom = SecureRandom()
    }
}
