package org.fossify.phone.recording

import android.media.MediaRecorder
import java.io.File

/**
 * Records from the device microphone to an AAC/.m4a file (mono, 64 kbps).
 * Tries capture sources in [MicSourceOrder] and keeps the first that prepares.
 * The MediaRecorder interaction is verified on-device, not in unit tests.
 */
class MicAudioSource(
    private val recorderFactory: () -> MediaRecorder = { MediaRecorder() },
) : AudioSource {
    private var recorder: MediaRecorder? = null

    override fun start(outputFile: File) {
        MicSourceOrder.firstUsable { tryPrepare(it, outputFile) }
            ?: throw IllegalStateException("No usable audio source for call recording")
        recorder?.start()
    }

    override fun stop() {
        recorder?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // stop() throws if no valid audio was captured; release regardless.
            }
            it.release()
        }
        recorder = null
    }

    private fun tryPrepare(source: Int, outputFile: File): Boolean {
        val candidate = recorderFactory()
        return try {
            candidate.setAudioSource(source)
            candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            candidate.setAudioChannels(CHANNELS)
            candidate.setAudioEncodingBitRate(BIT_RATE)
            candidate.setAudioSamplingRate(SAMPLING_RATE)
            candidate.setOutputFile(outputFile.absolutePath)
            candidate.prepare()
            recorder = candidate
            true
        } catch (e: Exception) {
            try {
                candidate.release()
            } catch (ignored: Exception) {
            }
            false
        }
    }

    private companion object {
        const val CHANNELS = 1
        const val BIT_RATE = 64_000
        const val SAMPLING_RATE = 44_100
    }
}
