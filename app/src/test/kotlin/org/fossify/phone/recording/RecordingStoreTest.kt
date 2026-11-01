package org.fossify.phone.recording

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun rec(id: String, startedAt: Long) = Recording(
        id = id,
        filePath = "/x/$id.m4a",
        number = "123",
        name = "A",
        startedAt = startedAt,
        durationMs = 1000,
        uploadState = UploadState.PENDING,
    )

    @Test
    fun add_then_all_returns_newest_first() {
        val store = RecordingStore(tmp.root)
        store.add(rec("a", 100))
        store.add(rec("b", 300))
        store.add(rec("c", 200))
        assertEquals(listOf("b", "c", "a"), store.all().map { it.id })
    }

    @Test
    fun update_changes_upload_state() {
        val store = RecordingStore(tmp.root)
        store.add(rec("a", 100))
        store.update("a") { it.copy(uploadState = UploadState.UPLOADED, driveId = "d1") }
        val r = store.all().single()
        assertEquals(UploadState.UPLOADED, r.uploadState)
        assertEquals("d1", r.driveId)
    }

    @Test
    fun delete_removes_row() {
        val store = RecordingStore(tmp.root)
        store.add(rec("a", 100))
        store.add(rec("b", 200))
        store.delete("a")
        assertEquals(listOf("b"), store.all().map { it.id })
    }

    @Test
    fun survives_reload_from_disk() {
        RecordingStore(tmp.root).add(rec("a", 100))
        val reloaded = RecordingStore(tmp.root)
        assertEquals(1, reloaded.all().size)
        assertEquals("a", reloaded.all().single().id)
    }
}
