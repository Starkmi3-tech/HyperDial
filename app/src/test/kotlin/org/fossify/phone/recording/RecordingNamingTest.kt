package org.fossify.phone.recording

import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingNamingTest {
    @Test
    fun builds_name_from_number_and_time() {
        val name = RecordingNaming.fileName("+91 90123", 1762000000000L)
        assertTrue(name, name.startsWith("+9190123_"))
        assertTrue(name, name.endsWith(".m4a"))
        assertTrue(name, Regex("""^\+9190123_\d{8}-\d{6}\.m4a$""").matches(name))
    }

    @Test
    fun unknown_number_becomes_unknown() {
        val name = RecordingNaming.fileName("", 1762000000000L)
        assertTrue(name, name.startsWith("unknown_"))
        assertTrue(name, name.endsWith(".m4a"))
    }
}
