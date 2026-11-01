package org.fossify.phone.recording

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the on-disk filename for a recording from the call number and start time. */
object RecordingNaming {
    private const val TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss"

    fun fileName(number: String, startedAt: Long): String {
        val timestamp = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).format(Date(startedAt))
        return "${sanitize(number)}_$timestamp.m4a"
    }

    private fun sanitize(number: String): String {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return "unknown"
        val leadingPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return "unknown"
        return if (leadingPlus) "+$digits" else digits
    }
}
