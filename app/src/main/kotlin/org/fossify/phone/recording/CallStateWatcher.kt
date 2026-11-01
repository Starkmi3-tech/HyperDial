package org.fossify.phone.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import org.fossify.phone.helpers.Config

enum class CallAction { START, STOP, NONE }

/**
 * Maps telephony call states to recording actions, remembering whether a
 * recording is currently active so IDLE only stops a recording that started.
 */
class CallStateMachine {
    private var recording = false

    fun onState(state: String, recordingEnabled: Boolean): CallAction = when (state) {
        STATE_OFFHOOK ->
            if (recordingEnabled && !recording) {
                recording = true
                CallAction.START
            } else {
                CallAction.NONE
            }

        STATE_IDLE ->
            if (recording) {
                recording = false
                CallAction.STOP
            } else {
                CallAction.NONE
            }

        else -> CallAction.NONE
    }

    companion object {
        // The string values the system puts in TelephonyManager.EXTRA_STATE.
        const val STATE_OFFHOOK = "OFFHOOK"
        const val STATE_IDLE = "IDLE"
        const val STATE_RINGING = "RINGING"
    }
}

/**
 * Manifest-registered PHONE_STATE receiver. A fresh instance is created per
 * broadcast, so the state machine lives in the companion to persist across the
 * OFFHOOK -> IDLE transitions of a single call.
 */
class CallStateWatcher : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val enabled = Config.newInstance(context).recordCalls
        when (machine.onState(state, enabled)) {
            CallAction.START -> {
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()
                CallRecordingService.startFor(context, number)
            }

            CallAction.STOP -> CallRecordingService.stopFor(context)
            CallAction.NONE -> Unit
        }
    }

    private companion object {
        val machine = CallStateMachine()
    }
}
