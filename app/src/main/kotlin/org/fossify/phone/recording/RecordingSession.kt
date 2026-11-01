package org.fossify.phone.recording

import java.io.File
import java.util.UUID

/**
 * Tracks one recording from start to finish: [begin] fixes the start time and
 * output file; [end] writes the metadata row to the store as PENDING upload.
 */
class RecordingSession(
    private val store: RecordingStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var startedAt: Long = 0L
    private var number: String = ""
    private var name: String = ""

    fun begin(number: String, name: String, outputDir: File): File {
        this.startedAt = clock()
        this.number = number
        this.name = name
        return File(outputDir, RecordingNaming.fileName(number, startedAt))
    }

    fun end(file: File, durationMs: Long) {
        store.add(
            Recording(
                id = UUID.randomUUID().toString(),
                filePath = file.absolutePath,
                number = number,
                name = name,
                startedAt = startedAt,
                durationMs = durationMs,
                uploadState = UploadState.PENDING,
            ),
        )
    }
}
