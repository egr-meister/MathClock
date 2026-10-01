package com.mathclock.app.data.repository

import com.mathclock.app.data.local.PracticeSessionEntity
import com.mathclock.app.data.local.ProgressTotalsEntity
import com.mathclock.app.data.local.SessionSummaryRow
import com.mathclock.app.data.local.TimeQuestionEntity
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** In-memory [PracticeStore] mirroring the Room DAO semantics (unique daily date, cascade delete). */
class FakePracticeStore : PracticeStore {
    val sessions = LinkedHashMap<Long, PracticeSessionEntity>()
    val questions = LinkedHashMap<Long, TimeQuestionEntity>()
    var totals: ProgressTotalsEntity? = null
    private var nextSessionId = 1L
    private var nextQuestionId = 1L

    override suspend fun <T> transaction(block: suspend () -> T): T = block()

    override suspend fun session(id: Long) = sessions[id]
    override suspend fun dailySession(date: String) = sessions.values.firstOrNull { it.dailyDate == date }
    override suspend fun insertSession(session: PracticeSessionEntity): Long {
        if (session.dailyDate != null) {
            check(sessions.values.none { it.dailyDate == session.dailyDate }) { "UNIQUE constraint failed: dailyDate" }
        }
        val id = nextSessionId++
        sessions[id] = session.copy(id = id)
        return id
    }
    override suspend fun insertQuestions(questions: List<TimeQuestionEntity>) {
        questions.forEach { val id = nextQuestionId++; this.questions[id] = it.copy(id = id) }
    }
    override suspend fun questions(sessionId: Long) =
        questions.values.filter { it.sessionId == sessionId }.sortedBy { it.position }
    override suspend fun question(id: Long) = questions[id]
    override suspend fun recordAnswer(id: Long, answer: Int, answeredAt: Long, answeredLocalDate: String, isCorrect: Boolean): Int {
        val q = questions[id] ?: return 0
        if (q.submittedAnswer != null) return 0
        questions[id] = q.copy(submittedAnswer = answer, answeredAt = answeredAt, answeredLocalDate = answeredLocalDate, isCorrect = isCorrect)
        return 1
    }
    override suspend fun markHintUsed(id: Long) { questions[id]?.let { questions[id] = it.copy(hintUsed = true) } }
    override suspend fun updateStatus(id: Long, status: String, finishedAt: Long?) {
        sessions[id]?.let { sessions[id] = it.copy(status = status, finishedAt = finishedAt) }
    }
    override suspend fun updatePosition(id: Long, position: Int) {
        sessions[id]?.let { sessions[id] = it.copy(currentPosition = position) }
    }
    override suspend fun deleteSessions(ids: List<Long>) {
        ids.forEach { sid -> sessions.remove(sid); questions.values.removeAll { it.sessionId == sid } }
    }
    override suspend fun activeFreeSession() =
        sessions.values.filter { it.type == "FREE" && it.status == "IN_PROGRESS" }.maxByOrNull { it.startedAt }
    override suspend fun answeredCount(sessionId: Long) = questions(sessionId).count { it.submittedAnswer != null }
    override suspend fun answeredOn(date: String) = questions.values.count { it.answeredLocalDate == date }
    override suspend fun prunableFreeSessionIds(keep: Int) = sessions.values
        .filter { it.type == "FREE" && it.status != "IN_PROGRESS" }
        .sortedWith(compareByDescending<PracticeSessionEntity> { it.startedAt }.thenByDescending { it.id })
        .drop(keep).map { it.id }
    override suspend fun prunableDailySessionIds(keep: Int) = sessions.values
        .filter { it.type == "DAILY" }
        .sortedByDescending { it.dailyDate }
        .drop(keep).map { it.id }
    override suspend fun sessionSummaries() = sessions.values
        .sortedWith(compareByDescending<PracticeSessionEntity> { it.startedAt }.thenByDescending { it.id })
        .map { s ->
            val qs = questions.values.filter { it.sessionId == s.id }
            SessionSummaryRow(s, qs.count { it.submittedAnswer != null }, qs.count { it.isCorrect == true }, qs.size)
        }
    override suspend fun deleteAllSessions() { sessions.clear(); questions.clear() }
    override suspend fun totals() = totals
    override suspend fun upsertTotals(totals: ProgressTotalsEntity) { this.totals = totals }
    override suspend fun deleteTotals() { totals = null }
}

/** Runs a suspend block that never actually suspends (all fake-store calls complete immediately). */
fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return checkNotNull(result) { "Block suspended unexpectedly" }.getOrThrow()
}
