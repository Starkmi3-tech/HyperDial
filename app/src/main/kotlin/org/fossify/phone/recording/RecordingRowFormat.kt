package org.fossify.phone.recording

/** Formats a recording's secondary line, e.g. "01:05 · Uploaded". */
object RecordingRowFormat {
    fun subtitle(recording: Recording): String {
        val totalSeconds = recording.durationMs / 1000
        val duration = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        val label = when (recording.uploadState) {
            UploadState.PENDING -> "Pending"
            UploadState.UPLOADING -> "Uploading"
            UploadState.UPLOADED -> "Uploaded"
            UploadState.FAILED -> "Failed"
        }
        return "$duration · $label"
    }
}
