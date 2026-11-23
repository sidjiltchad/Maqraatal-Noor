package org.maqraatalnoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maqraatalnoor.call.LocalCallSession
import org.maqraatalnoor.call.Participant

class CallSessionTest {

    private fun session(locked: Boolean = false): LocalCallSession {
        val participants = listOf(
            Participant("t1", "أم سارة", "س", isTeacher = true, muted = false),
            Participant("s1", "أمينة", "أ", isTeacher = false),
            Participant("s2", "فاطمة", "ف", isTeacher = false),
        )
        val session = LocalCallSession("s1", participants)
        if (locked) session.setLocked(true)
        return session
    }

    @Test
    fun joinAndLeave_resetSelfState() {
        val session = session()
        assertFalse(session.state.value.joined)
        session.join()
        session.toggleMute() // unmute
        session.toggleCamera() // camera on
        session.raiseHand()
        assertTrue(session.state.value.joined)
        session.leave()
        val state = session.state.value
        assertFalse(state.joined)
        assertTrue(state.selfMuted)
        assertFalse(state.selfCameraOn)
        assertFalse(state.handRaised)
        assertNull(state.currentReaderId)
    }

    @Test
    fun joinRefusedWhileLocked() {
        val session = session(locked = true)
        session.join()
        assertFalse(session.state.value.joined)
    }

    @Test
    fun toggleMuteAndCamera_flipState() {
        val session = session()
        session.join()
        session.toggleMute()
        assertFalse(session.state.value.selfMuted)
        session.toggleCamera()
        assertTrue(session.state.value.selfCameraOn)
    }

    @Test
    fun raiseAndLowerHand_marksSelfParticipant() {
        val session = session()
        session.raiseHand()
        assertTrue(session.state.value.handRaised)
        val self = session.state.value.participants.first { it.id == "s1" }
        assertTrue(self.handRaised)
        session.lowerHand()
        assertFalse(session.state.value.handRaised)
        assertFalse(session.state.value.participants.first { it.id == "s1" }.handRaised)
    }

    @Test
    fun giveTurn_unmutesReaderAndClearsHand() {
        val session = session()
        session.raiseHand()
        session.giveTurn("s1")
        assertEquals("s1", session.state.value.currentReaderId)
        val reader = session.state.value.participants.first { it.id == "s1" }
        assertFalse(reader.muted)
        assertFalse(reader.handRaised)
    }

    @Test
    fun giveTurn_unknownStudentIsNoOp() {
        val session = session()
        session.giveTurn("unknown")
        assertNull(session.state.value.currentReaderId)
    }

    @Test
    fun muteAll_mutesStudentsButNeverTeacher() {
        val session = session()
        session.giveTurn("s2")
        session.muteAll()
        val participants = session.state.value.participants
        assertFalse(participants.first { it.id == "t1" }.muted)
        assertTrue(participants.first { it.id == "s1" }.muted)
        assertTrue(participants.first { it.id == "s2" }.muted)
    }
}
