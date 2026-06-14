package net.toload.main.hd.keepass

import java.util.UUID

data class KeepassEntry(
    val id: UUID,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val additionalUrls: List<String> = emptyList(),
    val extraSearchValues: List<String> = emptyList(),
    val encryptedPassword: String = "",
) {
    val hasPassword: Boolean
        get() = password.isNotBlank() || encryptedPassword.isNotBlank()
}

data class KeepassEntryInput(
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
)

data class KeepassSyncResult(
    val message: String,
)
