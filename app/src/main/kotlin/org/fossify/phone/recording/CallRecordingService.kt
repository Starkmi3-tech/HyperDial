package org.fossify.phone.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import org.fossify.phone.R
import java.io.File

/**
 * Foreground microphone service that records one call. Started on OFFHOOK and
 * stopped on IDLE by CallStateWatcher. Records through a [MicAudioSource] and
 * writes the finished recording to the store via [RecordingSession].
 */
class CallRecordingService : Service() {
    private val session by lazy { RecordingSession(RecordingStore(recordingsDir())) }
    private var audioSource: AudioSource? = null
    private var currentFile: File? = null
    private var startElapsed: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        startForegroundNotification()
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val dir = recordingsDir().apply { if (!exists()) mkdirs() }
        val file = session.begin(number, name, dir)
        currentFile = file
        startElapsed = SystemClock.elapsedRealtime()
        val source = MicAudioSource()
        audioSource = source
        try {
            source.start(file)
        } catch (e: Exception) {
            // Recorder refused every source; abandon cleanly without touching the call.
            audioSource = null
            currentFile = null
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun handleStop() {
        val file = currentFile
        audioSource?.stop()
        audioSource = null
        if (file != null) {
            session.end(file, SystemClock.elapsedRealtime() - startElapsed)
            currentFile = null
            DriveUploader.enqueue(applicationContext)
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setSmallIcon(R.drawable.ic_dialpad_vector)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun recordingsDir(): File = File(filesDir, RECORDINGS_DIR)

    companion object {
        const val ACTION_START = "org.fossify.phone.recording.START"
        const val ACTION_STOP = "org.fossify.phone.recording.STOP"
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_NAME = "extra_name"
        private const val CHANNEL_ID = "call_recording"
        private const val NOTIFICATION_ID = 42
        const val RECORDINGS_DIR = "recordings"

        fun startFor(context: Context, number: String = "", name: String = "") {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NUMBER, number)
                putExtra(EXTRA_NAME, name)
            }
            context.startForegroundService(intent)
        }

        fun stopFor(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
