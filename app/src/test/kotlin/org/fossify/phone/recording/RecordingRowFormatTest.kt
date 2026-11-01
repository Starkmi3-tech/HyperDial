package org.fossify.phone.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingRowFormatTest {
    private fun rec(durationMs: Long, state: UploadState) = Recording(
        id = "x",
        filePath = "/x.m4a",
        number = "1",
        name = "A",
        startedAt = 0,
        durationMs = durationMs,
        uploadState = state,
    )

    @Test
    fun subtitle_shows_duration_and_state() {
        assertEquals("01:05 · Uploaded", RecordingRowFormat.subtitle(rec(65_000, UploadState.UPLOADED)))
    }

    @Test
    fun subtitle_pending_label() {
        assertEquals("00:03 · Pending", RecordingRowFormat.subtitle(rec(3_000, UploadState.PENDING)))
    }
}
