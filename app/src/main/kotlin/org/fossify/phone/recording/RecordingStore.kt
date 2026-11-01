package org.fossify.phone.recording

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists recording metadata as a JSON array in [dir]/recordings.json.
 * All reads and writes go through disk so the list survives process death.
 */
class RecordingStore(private val dir: File) {
    private val file = File(dir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    fun add(recording: Recording) {
        val list = load()
        list.add(recording)
        save(list)
    }

    /** Newest first, by start time. */
    fun all(): List<Recording> = load().sortedByDescending { it.startedAt }

    fun update(id: String, transform: (Recording) -> Recording) {
        val list = load()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = transform(list[index])
            save(list)
        }
    }

    fun delete(id: String) {
        val list = load()
        if (list.removeAll { it.id == id }) {
            save(list)
        }
    }

    private fun load(): MutableList<Recording> {
        if (!file.exists()) return mutableListOf()
        return try {
            json.decodeFromString<List<Recording>>(file.readText()).toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun save(list: List<Recording>) {
        if (!dir.exists()) dir.mkdirs()
        file.writeText(json.encodeToString(list))
    }

    private companion object {
        const val FILE_NAME = "recordings.json"
    }
}
