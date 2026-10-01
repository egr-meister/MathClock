package com.mathclock.app.data.repository

import com.mathclock.app.data.local.PracticeSessionEntity
import com.mathclock.app.data.local.ProgressTotalsEntity
import com.mathclock.app.data.local.SessionSummaryRow
import com.mathclock.app.data.local.TimeQuestionEntity

/**
 * Persistence boundary for practice data. Implemented by [RoomPracticeStore] in the app and by an
 * in-memory fake in unit tests.
 */
interface PracticeStore {
    suspend fun <T> transaction(block: suspend () -> T): T

    suspend fun session(id: Long): PracticeSessionEntity?
    suspend fun dailySession(date: String): PracticeSessionEntity?
    suspend fun insertSession(session: PracticeSessionEntity): Long
    suspend fun insertQuestions(questions: List<TimeQuestionEntity>)
    suspend fun questions(sessionId: Long): List<TimeQuestionEntity>
    suspend fun question(id: Long): TimeQuestionEntity?
    suspend fun recordAnswer(id: Long, answer: Int, answeredAt: Long, answeredLocalDate: String, isCorrect: Boolean): Int
    suspend fun markHintUsed(id: Long)
    suspend fun updateStatus(id: Long, status: String, finishedAt: Long?)
    suspend fun updatePosition(id: Long, position: Int)
    suspend fun deleteSessions(ids: List<Long>)
    suspend fun activeFreeSession(): PracticeSessionEntity?
    suspend fun answeredCount(sessionId: Long): Int
    suspend fun answeredOn(date: String): Int
    suspend fun prunableFreeSessionIds(keep: Int): List<Long>
    suspend fun prunableDailySessionIds(keep: Int): List<Long>
    suspend fun sessionSummaries(): List<SessionSummaryRow>
    suspend fun deleteAllSessions()
    suspend fun totals(): ProgressTotalsEntity?
    suspend fun upsertTotals(totals: ProgressTotalsEntity)
    suspend fun deleteTotals()
}
