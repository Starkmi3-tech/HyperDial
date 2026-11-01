package org.fossify.phone.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class CallStateMachineTest {
    private val offhook = CallStateMachine.STATE_OFFHOOK
    private val idle = CallStateMachine.STATE_IDLE
    private val ringing = CallStateMachine.STATE_RINGING

    @Test
    fun maps_offhook_to_start_when_enabled() {
        assertEquals(CallAction.START, CallStateMachine().onState(offhook, recordingEnabled = true))
    }

    @Test
    fun maps_offhook_to_none_when_disabled() {
        assertEquals(CallAction.NONE, CallStateMachine().onState(offhook, recordingEnabled = false))
    }

    @Test
    fun maps_offhook_then_idle_to_start_then_stop() {
        val machine = CallStateMachine()
        assertEquals(CallAction.START, machine.onState(offhook, recordingEnabled = true))
        assertEquals(CallAction.STOP, machine.onState(idle, recordingEnabled = true))
    }

    @Test
    fun maps_ringing_then_idle_to_no_recording() {
        val machine = CallStateMachine()
        assertEquals(CallAction.NONE, machine.onState(ringing, recordingEnabled = true))
        assertEquals(CallAction.NONE, machine.onState(idle, recordingEnabled = true))
    }
}
