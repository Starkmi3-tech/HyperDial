package org.fossify.phone.recording

import kotlinx.serialization.Serializable

enum class UploadState {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED,
}

@Serializable
data class Recording(
    val id: String,
    val filePath: String,
    val number: String,
    val name: String,
    val startedAt: Long,
    val durationMs: Long,
    val uploadState: UploadState,
    val driveId: String? = null,
)
