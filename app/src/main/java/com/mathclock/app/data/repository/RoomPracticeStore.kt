package com.mathclock.app.data.repository

import androidx.room.withTransaction
import com.mathclock.app.data.local.MathClockDatabase
import com.mathclock.app.data.local.PracticeDao
import com.mathclock.app.data.local.PracticeSessionEntity
import com.mathclock.app.data.local.ProgressTotalsEntity
import com.mathclock.app.data.local.TimeQuestionEntity

class RoomPracticeStore(
    private val db: MathClockDatabase,
    private val dao: PracticeDao = db.practiceDao(),
) : PracticeStore {
    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun session(id: Long) = dao.session(id)
    override suspend fun dailySession(date: String) = dao.dailySession(date)
    override suspend fun insertSession(session: PracticeSessionEntity) = dao.insertSession(session)
    override suspend fun insertQuestions(questions: List<TimeQuestionEntity>) = dao.insertQuestions(questions)
    override suspend fun questions(sessionId: Long) = dao.questions(sessionId)
    override suspend fun question(id: Long) = dao.question(id)
    override suspend fun recordAnswer(id: Long, answer: Int, answeredAt: Long, answeredLocalDate: String, isCorrect: Boolean) =
        dao.recordAnswer(id, answer, answeredAt, answeredLocalDate, isCorrect)
    override suspend fun markHintUsed(id: Long) = dao.markHintUsed(id)
    override suspend fun updateStatus(id: Long, status: String, finishedAt: Long?) = dao.updateStatus(id, status, finishedAt)
    override suspend fun updatePosition(id: Long, position: Int) = dao.updatePosition(id, position)
    override suspend fun deleteSessions(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteSessions(ids)
    }
    override suspend fun activeFreeSession() = dao.activeFreeSession()
    override suspend fun answeredCount(sessionId: Long) = dao.answeredCount(sessionId)
    override suspend fun answeredOn(date: String) = dao.answeredOn(date)
    override suspend fun prunableFreeSessionIds(keep: Int) = dao.prunableFreeSessionIds(keep)
    override suspend fun prunableDailySessionIds(keep: Int) = dao.prunableDailySessionIds(keep)
    override suspend fun sessionSummaries() = dao.sessionSummaries()
    override suspend fun deleteAllSessions() = dao.deleteAllSessions()
    override suspend fun totals() = dao.totals()
    override suspend fun upsertTotals(totals: ProgressTotalsEntity) = dao.upsertTotals(totals)
    override suspend fun deleteTotals() = dao.deleteTotals()
}
