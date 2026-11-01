package org.fossify.phone.recording

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DriveUploaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun rec(id: String, startedAt: Long, state: UploadState) = Recording(
        id = id,
        filePath = "/x/$id.m4a",
        number = "1",
        name = "A",
        startedAt = startedAt,
        durationMs = 1,
        uploadState = state,
    )

    @Test
    fun enqueue_uses_unmetered_constraint() {
        assertEquals(NetworkType.UNMETERED, DriveUploader.uploadConstraints().requiredNetworkType)
    }

    @Test
    fun next_batch_returns_pending_and_failed_oldest_first() {
        val store = RecordingStore(tmp.root)
        store.add(rec("a", 300, UploadState.PENDING))
        store.add(rec("b", 100, UploadState.FAILED))
        store.add(rec("c", 200, UploadState.UPLOADED))
        assertEquals(listOf("b", "a"), UploadQueue.nextBatch(store).map { it.id })
    }

    @Test
    fun worker_without_account_keeps_pending() {
        val store = RecordingStore(tmp.root)
        store.add(rec("a", 100, UploadState.PENDING))
        val runner = UploadRunner(store, hasAccount = { false }, upload = { error("must not upload") })
        assertEquals(UploadOutcome.SUCCESS, runner.run())
        assertEquals(UploadState.PENDING, store.all().single().uploadState)
    }
}
