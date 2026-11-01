package org.fossify.phone.recording

import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioSourceSelectionTest {
    @Test
    fun picks_first_candidate_that_can_be_used() {
        val chosen = MicSourceOrder.firstUsable { it == MediaRecorder.AudioSource.VOICE_RECOGNITION }
        assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, chosen)
    }

    @Test
    fun returns_null_when_none_usable() {
        assertNull(MicSourceOrder.firstUsable { false })
    }

    @Test
    fun prefers_voice_communication_when_all_usable() {
        val chosen = MicSourceOrder.firstUsable { true }
        assertEquals(MediaRecorder.AudioSource.VOICE_COMMUNICATION, chosen)
    }
}
