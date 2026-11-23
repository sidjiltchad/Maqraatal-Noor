package org.maqraatalnoor.domain

import org.maqraatalnoor.data.RecitationTurn
import org.maqraatalnoor.data.Student
import org.maqraatalnoor.data.TurnState

/**
 * Pure recitation-queue rules. No Android types, fully unit-tested.
 * Only one student reads at a time; the teacher gives the turn.
 */
object QueueLogic {

    fun initialQueue(students: List<Student>): List<RecitationTurn> =
        students.mapIndexed { index, student ->
            RecitationTurn(
                student = student,
                state = if (index == 0) TurnState.READING else TurnState.WAITING,
            )
        }

    fun raiseHand(turns: List<RecitationTurn>, studentId: String): List<RecitationTurn> =
        turns.map { turn ->
            if (turn.student.id == studentId && turn.state == TurnState.WAITING) {
                turn.copy(handRaised = true)
            } else {
                turn
            }
        }

    fun lowerHand(turns: List<RecitationTurn>, studentId: String): List<RecitationTurn> =
        turns.map { turn ->
            if (turn.student.id == studentId) turn.copy(handRaised = false) else turn
        }

    /** Give the turn to [studentId]: the current reader finishes, the target reads. */
    fun giveTurn(turns: List<RecitationTurn>, studentId: String): List<RecitationTurn> =
        turns.map { turn ->
            when {
                turn.student.id == studentId -> turn.copy(state = TurnState.READING, handRaised = false)
                turn.state == TurnState.READING -> turn.copy(state = TurnState.DONE)
                else -> turn
            }
        }

    /** Next reader: a waiting student with a raised hand first, else the first waiting student. */
    fun nextReader(turns: List<RecitationTurn>): RecitationTurn? =
        turns.firstOrNull { it.state == TurnState.WAITING && it.handRaised }
            ?: turns.firstOrNull { it.state == TurnState.WAITING }

    fun currentReader(turns: List<RecitationTurn>): RecitationTurn? =
        turns.firstOrNull { it.state == TurnState.READING }

    fun isFinished(turns: List<RecitationTurn>): Boolean =
        turns.isNotEmpty() && turns.all { it.state == TurnState.DONE }
}
