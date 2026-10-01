package com.mathclock.app.data.repository

import com.mathclock.app.data.local.PracticeSessionEntity
import com.mathclock.app.data.local.ProgressTotalsEntity
import com.mathclock.app.data.local.SessionSummaryRow
import com.mathclock.app.data.local.TimeQuestionEntity
import com.mathclock.app.domain.generation.DailyPlanner
import com.mathclock.app.domain.generation.Difficulty
import com.mathclock.app.domain.generation.Exercise
import com.mathclock.app.domain.generation.QuestionGenerator
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.progress.Clock
import com.mathclock.app.domain.progress.DateProvider
import com.mathclock.app.domain.progress.Scoring
import com.mathclock.app.domain.progress.SessionStatus
import com.mathclock.app.domain.progress.SessionType
import com.mathclock.app.domain.progress.TopicScore
import com.mathclock.app.domain.time.TimeFormat
import java.time.LocalDate
import kotlin.random.Random

data class LoadedSession(
    val session: PracticeSessionEntity,
    val questions: List<TimeQuestionEntity>,
) {
    val type: SessionType get() = SessionType.valueOf(session.type)
    val status: SessionStatus get() = SessionStatus.valueOf(session.status)
    val difficulty: Difficulty get() = Difficulty.valueOf(session.difficulty)
    val timeFormat: TimeFormat get() = TimeFormat.valueOf(session.timeFormat)
    val answeredCount: Int get() = questions.count { it.submittedAnswer != null }
    val correctCount: Int get() = questions.count { it.isCorrect == true }
    val isComplete: Boolean get() = questions.isNotEmpty() && answeredCount == questions.size
    val dailyDate: LocalDate? get() = session.dailyDate?.let(LocalDate::parse)

    fun topicScores(): Map<Topic, TopicScore> = Topic.entries.associateWith { t ->
        val qs = questions.filter { it.topic == t.name && it.submittedAnswer != null }
        TopicScore(qs.size, qs.count { it.isCorrect == true })
    }
}

data class AnswerOutcome(
    val isCorrect: Boolean,
    /** True when the question had already been answered; nothing was changed. */
    val alreadyAnswered: Boolean,
    val sessionCompleted: Boolean,
)

data class Totals(
    val answered: Int,
    val correct: Int,
    val completedSessions: Int,
    val perTopic: Map<Topic, TopicScore>,
)

fun TimeQuestionEntity.toExercise(): Exercise = Exercise(
    topic = Topic.valueOf(topic),
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    durationMinutes = durationMinutes,
    targetMinutes = targetMinutes,
    crossesMidnight = crossesMidnight,
    options = if (answerOptions.isBlank()) emptyList() else answerOptions.split(',').map { it.trim().toInt() },
)

/**
 * Practice business rules on top of [PracticeStore]. Every multi-step write runs in a transaction.
 */
class PracticeService(
    private val store: PracticeStore,
    private val random: Random,
    private val clock: Clock,
    private val dates: DateProvider,
) {
    private val generator = QuestionGenerator(random)

    fun today(): LocalDate = dates.today()

    /** Returns today's (or [date]'s) daily set, creating and saving all ten questions first if needed. */
    suspend fun getOrCreateDailySet(date: LocalDate, difficulty: Difficulty, format: TimeFormat): Long =
        store.transaction {
            val key = date.toString()
            store.dailySession(key)?.let { return@transaction it.id }
            val topics = DailyPlanner.topicsFor(date.toEpochDay(), random)
            val exercises = generator.generateSession(topics, difficulty)
            val id = store.insertSession(
                PracticeSessionEntity(
                    type = SessionType.DAILY.name,
                    dailyDate = key,
                    topicSelection = "DAILY",
                    difficulty = difficulty.name,
                    timeFormat = format.name,
                    startedAt = clock.nowMillis(),
                    finishedAt = null,
                    status = SessionStatus.IN_PROGRESS.name,
                ),
            )
            store.insertQuestions(exercises.mapIndexed { i, ex -> ex.toEntity(id, i) })
            id
        }

    suspend fun dailySetFor(date: LocalDate): LoadedSession? =
        store.dailySession(date.toString())?.let { load(it.id) }

    suspend fun activeFreeSession(): LoadedSession? = store.activeFreeSession()?.let { load(it.id) }

    /** Starts a free-practice session. Only one unfinished free session may exist at a time. */
    suspend fun startFreeSession(topic: Topic?, difficulty: Difficulty, format: TimeFormat): Long =
        store.transaction {
            check(store.activeFreeSession() == null) { "An unfinished free-practice session already exists" }
            val exercises = generator.generateSession(generator.freePracticeTopics(topic), difficulty)
            val id = store.insertSession(
                PracticeSessionEntity(
                    type = SessionType.FREE.name,
                    dailyDate = null,
                    topicSelection = topic?.name ?: "MIXED",
                    difficulty = difficulty.name,
                    timeFormat = format.name,
                    startedAt = clock.nowMillis(),
                    finishedAt = null,
                    status = SessionStatus.IN_PROGRESS.name,
                ),
            )
            store.insertQuestions(exercises.mapIndexed { i, ex -> ex.toEntity(id, i) })
            id
        }

    /** Ends the current free session (if any) and starts a new one with the same settings. */
    suspend fun restartFreeSession(): Long? {
        val active = store.activeFreeSession() ?: return null
        val topic = Topic.entries.firstOrNull { it.name == active.topicSelection }
        endSession(active.id)
        return startFreeSession(topic, Difficulty.valueOf(active.difficulty), TimeFormat.valueOf(active.timeFormat))
    }

    suspend fun load(sessionId: Long): LoadedSession? {
        val session = store.session(sessionId) ?: return null
        return LoadedSession(session, store.questions(sessionId))
    }

    /**
     * Records the first answer to a question exactly once and updates aggregates in the same
     * transaction. Repeated submissions (double taps, process recreation) change nothing.
     */
    suspend fun recordAnswer(questionId: Long, answer: Int): AnswerOutcome = store.transaction {
        val q = requireNotNull(store.question(questionId)) { "Unknown question $questionId" }
        val session = requireNotNull(store.session(q.sessionId))
        if (q.submittedAnswer != null) {
            return@transaction AnswerOutcome(q.isCorrect == true, alreadyAnswered = true, sessionCompleted = false)
        }
        val precision = Difficulty.valueOf(session.difficulty).precision
        val correct = Scoring.isCorrect(q.toExercise(), answer, precision)
        val updated = store.recordAnswer(q.id, answer, clock.nowMillis(), dates.today().toString(), correct)
        if (updated != 1) {
            val again = store.question(questionId)
            return@transaction AnswerOutcome(again?.isCorrect == true, alreadyAnswered = true, sessionCompleted = false)
        }
        val topic = Topic.valueOf(q.topic)
        val totals = currentTotalsEntity()
        val perTopic = Scoring.decodeTopicCounts(totals.perTopicCounts).toMutableMap()
        val ts = perTopic.getValue(topic)
        perTopic[topic] = TopicScore(ts.answered + 1, ts.correct + if (correct) 1 else 0)
        var newTotals = totals.copy(
            answeredCount = totals.answeredCount + 1,
            correctCount = totals.correctCount + if (correct) 1 else 0,
            perTopicCounts = Scoring.encodeTopicCounts(perTopic),
        )
        var completed = false
        val questions = store.questions(session.id)
        if (session.status == SessionStatus.IN_PROGRESS.name && questions.all { it.submittedAnswer != null }) {
            store.updateStatus(session.id, SessionStatus.COMPLETED.name, clock.nowMillis())
            newTotals = newTotals.copy(completedSessionCount = newTotals.completedSessionCount + 1)
            completed = true
        }
        store.upsertTotals(newTotals)
        if (completed) prune()
        AnswerOutcome(correct, alreadyAnswered = false, sessionCompleted = completed)
    }

    suspend fun markHintUsed(questionId: Long) = store.markHintUsed(questionId)

    suspend fun setPosition(sessionId: Long, position: Int) = store.updatePosition(sessionId, position)

    /**
     * Ends a session early. A free session with no answers is discarded without a history entry;
     * otherwise answered questions are kept and unanswered ones never count as errors.
     */
    suspend fun endSession(sessionId: Long) = store.transaction {
        val session = store.session(sessionId) ?: return@transaction
        if (session.status != SessionStatus.IN_PROGRESS.name) return@transaction
        val answered = store.answeredCount(sessionId)
        if (answered == 0 && session.type == SessionType.FREE.name) {
            store.deleteSessions(listOf(sessionId))
        } else {
            store.updateStatus(sessionId, SessionStatus.ENDED_EARLY.name, clock.nowMillis())
            prune()
        }
    }

    suspend fun answeredOn(date: LocalDate): Int = store.answeredOn(date.toString())

    suspend fun history(): List<SessionSummaryRow> = store.sessionSummaries().filter {
        it.answered > 0 || it.session.type == SessionType.DAILY.name
    }

    suspend fun totals(): Totals {
        val t = currentTotalsEntity()
        return Totals(t.answeredCount, t.correctCount, t.completedSessionCount, Scoring.decodeTopicCounts(t.perTopicCounts))
    }

    suspend fun clearProgress() = store.transaction {
        store.deleteAllSessions()
        store.deleteTotals()
    }

    /** Keeps the latest [KEEP_FREE_SESSIONS] finished free sessions and [KEEP_DAILY_SETS] daily sets. */
    private suspend fun prune() {
        store.deleteSessions(store.prunableFreeSessionIds(KEEP_FREE_SESSIONS))
        store.deleteSessions(store.prunableDailySessionIds(KEEP_DAILY_SETS))
    }

    private suspend fun currentTotalsEntity(): ProgressTotalsEntity = store.totals() ?: ProgressTotalsEntity(
        answeredCount = 0,
        correctCount = 0,
        completedSessionCount = 0,
        perTopicCounts = Scoring.encodeTopicCounts(emptyMap()),
    )

    companion object {
        const val KEEP_FREE_SESSIONS = 100
        const val KEEP_DAILY_SETS = 90
    }
}

fun Exercise.toEntity(sessionId: Long, position: Int) = TimeQuestionEntity(
    sessionId = sessionId,
    position = position,
    topic = topic.name,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    durationMinutes = durationMinutes,
    targetMinutes = targetMinutes,
    crossesMidnight = crossesMidnight,
    answerOptions = options.joinToString(","),
    submittedAnswer = null,
    answeredAt = null,
    answeredLocalDate = null,
    isCorrect = null,
    hintUsed = false,
)
