package org.fossify.phone.recording

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class UploadOutcome { SUCCESS, RETRY }

/** Selects the recordings still waiting to upload, oldest first. */
object UploadQueue {
    fun nextBatch(store: RecordingStore): List<Recording> = store.all()
        .filter { it.uploadState == UploadState.PENDING || it.uploadState == UploadState.FAILED }
        .sortedBy { it.startedAt }
}

/**
 * The testable upload loop, independent of WorkManager and DriveClient.
 * With no signed-in account there is nothing to do yet, so rows stay PENDING
 * and the run succeeds; a failed upload marks the row FAILED and asks for retry.
 */
class UploadRunner(
    private val store: RecordingStore,
    private val hasAccount: () -> Boolean,
    private val upload: (Recording) -> String,
) {
    fun run(): UploadOutcome {
        if (!hasAccount()) return UploadOutcome.SUCCESS
        var outcome = UploadOutcome.SUCCESS
        for (recording in UploadQueue.nextBatch(store)) {
            store.update(recording.id) { it.copy(uploadState = UploadState.UPLOADING) }
            try {
                val driveId = upload(recording)
                store.update(recording.id) { it.copy(uploadState = UploadState.UPLOADED, driveId = driveId) }
            } catch (e: Exception) {
                store.update(recording.id) { it.copy(uploadState = UploadState.FAILED) }
                outcome = UploadOutcome.RETRY
            }
        }
        return outcome
    }
}

/** Uploads pending recordings to Drive, only on an unmetered (Wi-Fi) network. */
class DriveUploader(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = RecordingStore(File(context.filesDir, CallRecordingService.RECORDINGS_DIR))
        val client = DriveClient(context)
        val runner = UploadRunner(
            store = store,
            hasAccount = { client.hasAccount() },
            upload = { recording ->
                val file = File(recording.filePath)
                client.upload(file, file.name)
            },
        )
        when (runner.run()) {
            UploadOutcome.SUCCESS -> Result.success()
            UploadOutcome.RETRY -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "drive-upload"

        fun uploadConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveUploader>()
                .setConstraints(uploadConstraints())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
