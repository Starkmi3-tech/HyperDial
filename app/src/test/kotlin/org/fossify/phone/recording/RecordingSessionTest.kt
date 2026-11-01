package org.fossify.phone.recording

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingSessionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val fixedClock = { 1762000000000L }

    @Test
    fun begin_returns_named_file_in_dir() {
        val store = RecordingStore(tmp.root)
        val session = RecordingSession(store, fixedClock)
        val dir = tmp.newFolder("rec")
        val file = session.begin("+91 90123", "Alice", dir)
        assertEquals(dir, file.parentFile)
        assertEquals(RecordingNaming.fileName("+91 90123", 1762000000000L), file.name)
    }

    @Test
    fun end_writes_pending_recording_with_duration() {
        val store = RecordingStore(tmp.root)
        val session = RecordingSession(store, fixedClock)
        val dir = tmp.newFolder("rec")
        val file = session.begin("123", "Bob", dir)
        session.end(file, 5000L)
        val recording = store.all().single()
        assertEquals(UploadState.PENDING, recording.uploadState)
        assertEquals(5000L, recording.durationMs)
        assertEquals("123", recording.number)
    }
}
