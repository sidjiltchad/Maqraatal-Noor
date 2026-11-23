package org.maqraatalnoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maqraatalnoor.data.Level
import org.maqraatalnoor.data.RecitationTurn
import org.maqraatalnoor.data.Student
import org.maqraatalnoor.data.TurnState
import org.maqraatalnoor.domain.QueueLogic

class QueueLogicTest {

    private fun student(id: String, name: String) = Student(id, name, Level.TAJWEED, 0f)

    private fun queue(): List<RecitationTurn> =
        QueueLogic.initialQueue(listOf(student("s1", "أمينة"), student("s2", "فاطمة"), student("s3", "مريم")))

    @Test
    fun initialQueue_firstStudentReadsOthersWait() {
        val turns = queue()
        assertEquals(TurnState.READING, turns[0].state)
        assertEquals(TurnState.WAITING, turns[1].state)
        assertEquals(TurnState.WAITING, turns[2].state)
    }

    @Test
    fun raiseHand_onlyWaitingStudent() {
        val turns = QueueLogic.raiseHand(queue(), "s2")
        assertTrue(turns[1].handRaised)
        assertFalse(turns[0].handRaised)
        assertFalse(turns[2].handRaised)
    }

    @Test
    fun raiseHand_readingStudentCannotRaise() {
        val turns = QueueLogic.raiseHand(queue(), "s1")
        assertFalse(turns[0].handRaised)
    }

    @Test
    fun lowerHand_clearsRaisedHand() {
        val raised = QueueLogic.raiseHand(queue(), "s3")
        val lowered = QueueLogic.lowerHand(raised, "s3")
        assertFalse(lowered[2].handRaised)
    }

    @Test
    fun giveTurn_movesReaderAndClearsHand() {
        val raised = QueueLogic.raiseHand(queue(), "s2")
        val moved = QueueLogic.giveTurn(raised, "s2")
        assertEquals(TurnState.DONE, moved[0].state)
        assertEquals(TurnState.READING, moved[1].state)
        assertFalse(moved[1].handRaised)
        assertEquals("s2", QueueLogic.currentReader(moved)?.student?.id)
    }

    @Test
    fun nextReader_prefersRaisedHand() {
        val raised = QueueLogic.raiseHand(queue(), "s3")
        assertEquals("s3", QueueLogic.nextReader(raised)?.student?.id)
        assertEquals("s2", QueueLogic.nextReader(queue())?.student?.id)
    }

    @Test
    fun isFinished_onlyWhenAllDone() {
        assertFalse(QueueLogic.isFinished(queue()))
        val allDone = queue().map { it.copy(state = TurnState.DONE) }
        assertTrue(QueueLogic.isFinished(allDone))
        assertFalse(QueueLogic.isFinished(emptyList()))
    }

    @Test
    fun currentReader_nullWhenNobodyReads() {
        val none = queue().map { it.copy(state = TurnState.WAITING) }
        assertNull(QueueLogic.currentReader(none))
    }
}
