package com.tim.futureai.domain.link

import kotlinx.coroutines.flow.StateFlow

data class LinkSharedFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val contentUri: String,
)

data class LinkReceivedFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val receivedAtMs: Long,
)

data class GatherLinkState(
    val isActive: Boolean = false,
    val addresses: List<String> = emptyList(),
    val pairingCode: String? = null,
    val sharedFiles: List<LinkSharedFile> = emptyList(),
    val receivedFiles: List<LinkReceivedFile> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)

/** Device-linking boundary used by the feature UI. Android networking stays in platform. */
interface GatherLinkSession {
    val state: StateFlow<GatherLinkState>

    suspend fun start()
    fun stop()
    suspend fun setSharedFiles(contentUris: List<String>)
    suspend fun saveReceivedFile(fileId: String, destinationUri: String)
    suspend fun deleteReceivedFile(fileId: String)
}
