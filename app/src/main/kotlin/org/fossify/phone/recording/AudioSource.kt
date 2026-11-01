package org.fossify.phone.recording

import android.media.MediaRecorder
import java.io.File

/** Produces call audio into a file. The only unit that knows how audio is captured. */
interface AudioSource {
    fun start(outputFile: File)
    fun stop()
}

/** The order in which microphone capture sources are tried for in-call recording. */
object MicSourceOrder {
    val candidates: List<Int> = listOf(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.MIC,
    )

    /** Returns the first candidate for which [canUse] is true, or null if none is. */
    fun firstUsable(canUse: (Int) -> Boolean): Int? = candidates.firstOrNull(canUse)
}
