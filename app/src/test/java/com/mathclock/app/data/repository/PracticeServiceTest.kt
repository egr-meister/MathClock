package com.mathclock.app.data.repository

import com.mathclock.app.domain.generation.Difficulty
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.progress.SessionStatus
import com.mathclock.app.domain.time.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class PracticeServiceTest {

    private lateinit var store: FakePracticeStore
    private lateinit var service: PracticeService
    private var today: LocalDate = LocalDate.of(2026, 10, 1)
    private var now = 1_000_000L

    @Before
    fun setUp() {
        store = FakePracticeStore()
        today = LocalDate.of(2026, 10, 1)
        now = 1_000_000L
        service = PracticeService(store, Random(5), clock = { now++ }, dates = { today })
    }

    private fun correctAnswerFor(q: com.mathclock.app.data.local.TimeQuestionEntity) = q.toExercise().correctAnswer

    @Test
    fun dailySetIsGeneratedOncePerDateAndPersisted() = runSuspend {
        val id1 = service.getOrCreateDailySet(today, Difficulty.EASY, TimeFormat.H24)
        val first = service.load(id1)!!
        assertEquals(10, first.questions.size)
        // Reopening returns the same questions and the same option order.
        val id2 = service.getOrCreateDailySet(today, Difficulty.HARD, TimeFormat.H12)
        assertEquals(id1, id2)
        val second = service.load(id2)!!
        assertEquals(first.questions, second.questions)
        assertEquals(Difficulty.EASY, second.difficulty) // settings change applies to the next set only
        val counts = first.questions.groupingBy { it.topic }.eachCount()
        Topic.entries.forEach { assertTrue(counts.getValue(it.name) >= 2) }
    }

    @Test
    fun answersAreCountedOnlyOnce() = runSuspend {
        val id = service.getOrCreateDailySet(today, Difficulty.EASY, TimeFormat.H24)
        val q = service.load(id)!!.questions.first()
        val answer = correctAnswerFor(q)
        val first = service.recordAnswer(q.id, answer)
        assertTrue(first.isCorrect)
        assertFalse(first.alreadyAnswered)
        val wrong = if (q.answerOptions.isNotBlank()) q.toExercise().options.first { it != answer } else answer + 60
        val second = service.recordAnswer(q.id, wrong)
        assertTrue(second.alreadyAnswered)
        assertTrue(second.isCorrect) // original scored answer kept
        assertEquals(1, service.totals().answered)
        assertEquals(1, service.totals().correct)
        assertEquals(answer, service.load(id)!!.questions.first().submittedAnswer)
        assertEquals(1, service.answeredOn(today))
    }

    @Test
    fun completingDailySetMarksCompletedAndCountsOnce() = runSuspend {
        val id = service.getOrCreateDailySet(today, Difficulty.MEDIUM, TimeFormat.H12)
        val questions = service.load(id)!!.questions
        var completedEvents = 0
        questions.forEachIndexed { i, q ->
            val outcome = service.recordAnswer(q.id, if (i % 2 == 0) correctAnswerFor(q) else correctAnswerFor(q) + 7)
            if (outcome.sessionCompleted) completedEvents++
        }
        assertEquals(1, completedEvents)
        val loaded = service.load(id)!!
        assertEquals(SessionStatus.COMPLETED, loaded.status)
        assertEquals(10, loaded.answeredCount)
        assertEquals(5, loaded.correctCount)
        assertEquals(1, service.totals().completedSessions)
    }

    @Test
    fun dateChangeKeepsOldSetAndCreatesNewOne() = runSuspend {
        val oldId = service.getOrCreateDailySet(today, Difficulty.EASY, TimeFormat.H24)
        val q = service.load(oldId)!!.questions.first()
        // Midnight passes while the child is answering: the answer stays in the original set,
        // attributed to the new local date.
        today = today.plusDays(1)
        service.recordAnswer(q.id, correctAnswerFor(q))
        val old = service.load(oldId)!!
        assertEquals(1, old.answeredCount)
        assertEquals("2026-10-02", old.questions.first().answeredLocalDate)
        val newId = service.getOrCreateDailySet(today, Difficulty.EASY, TimeFormat.H24)
        assertTrue(newId != oldId)
        // The old incomplete set remains in history; its unanswered questions are not errors.
        val oldSummary = service.history().first { it.session.id == oldId }
        assertEquals(1, oldSummary.answered)
        assertEquals(1, oldSummary.correct)
        assertEquals(SessionStatus.IN_PROGRESS.name, oldSummary.session.status)
        // Returning to the old date reuses the existing set.
        assertEquals(oldId, service.getOrCreateDailySet(LocalDate.of(2026, 10, 1), Difficulty.HARD, TimeFormat.H24))
    }

    @Test
    fun onlyOneUnfinishedFreeSession() = runSuspend {
        service.startFreeSession(Topic.TIME_AFTER, Difficulty.EASY, TimeFormat.H24)
        var threw = false
        try {
            service.startFreeSession(Topic.READ_CLOCK, Difficulty.EASY, TimeFormat.H24)
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
        // Daily set is separate from the free session.
        assertNotNull(service.getOrCreateDailySet(today, Difficulty.EASY, TimeFormat.H24))
        assertNotNull(service.activeFreeSession())
    }

    @Test
    fun endingWithoutAnswersDiscardsSession() = runSuspend {
        val id = service.startFreeSession(null, Difficulty.EASY, TimeFormat.H24)
        service.endSession(id)
        assertNull(service.load(id))
        assertTrue(service.history().isEmpty())
    }

    @Test
    fun endingEarlyKeepsAnsweredQuestionsOnly() = runSuspend {
        val id = service.startFreeSession(Topic.MINUTES_BETWEEN, Difficulty.EASY, TimeFormat.H24)
        val qs = service.load(id)!!.questions
        service.recordAnswer(qs[0].id, correctAnswerFor(qs[0]))
        service.recordAnswer(qs[1].id, correctAnswerFor(qs[1]) + 1000)
        service.endSession(id)
        val s = service.load(id)!!
        assertEquals(SessionStatus.ENDED_EARLY, s.status)
        val summary = service.history().single()
        assertEquals(2, summary.answered)
        assertEquals(1, summary.correct)
        assertNull(service.activeFreeSession())
    }

    @Test
    fun restartReplacesUnfinishedSession() = runSuspend {
        val id = service.startFreeSession(Topic.SET_CLOCK, Difficulty.HARD, TimeFormat.H12)
        val newId = service.restartFreeSession()!!
        assertTrue(newId != id)
        val loaded = service.load(newId)!!
        assertEquals(Difficulty.HARD, loaded.difficulty)
        assertEquals(TimeFormat.H12, loaded.timeFormat)
        assertTrue(loaded.questions.all { it.topic == Topic.SET_CLOCK.name })
    }

    @Test
    fun positionIsPersistedForRecovery() = runSuspend {
        val id = service.startFreeSession(null, Difficulty.EASY, TimeFormat.H24)
        service.setPosition(id, 4)
        assertEquals(4, service.load(id)!!.session.currentPosition)
    }

    @Test
    fun pruningKeepsLatestSessionsButPreservesTotals() = runSuspend {
        repeat(105) {
            val id = service.startFreeSession(Topic.READ_CLOCK, Difficulty.EASY, TimeFormat.H24)
            val q = service.load(id)!!.questions.first()
            service.recordAnswer(q.id, correctAnswerFor(q))
            service.endSession(id)
        }
        val free = store.sessions.values.count { it.type == "FREE" }
        assertEquals(PracticeService.KEEP_FREE_SESSIONS, free)
        assertEquals(105, service.totals().answered)
        assertEquals(105, service.totals().perTopic.getValue(Topic.READ_CLOCK).answered)
    }

    @Test
    fun dailySetsArePrunedTo90() = runSuspend {
        var date = LocalDate.of(2026, 1, 1)
        repeat(95) {
            today = date
            val id = service.getOrCreateDailySet(date, Difficulty.EASY, TimeFormat.H24)
            val q = service.load(id)!!.questions.first()
            service.recordAnswer(q.id, correctAnswerFor(q))
            service.endSession(id)
            date = date.plusDays(1)
        }
        assertEquals(PracticeService.KEEP_DAILY_SETS, store.sessions.values.count { it.type == "DAILY" })
        assertNull(store.sessions.values.firstOrNull { it.dailyDate == "2026-01-01" })
        assertEquals(95, service.totals().answered)
    }

    @Test
    fun setTheClockIsCheckedByNormalizedTime() = runSuspend {
        val id = service.startFreeSession(Topic.SET_CLOCK, Difficulty.MEDIUM, TimeFormat.H12)
        val q = service.load(id)!!.questions.first()
        val target = correctAnswerFor(q)
        assertFalse(service.recordAnswer(q.id, (target + 720) % 1440).isCorrect) // AM/PM matters
        val q2 = service.load(id)!!.questions[1]
        assertTrue(service.recordAnswer(q2.id, correctAnswerFor(q2)).isCorrect)
    }

    @Test
    fun clearProgressRemovesEverything() = runSuspend {
        val id = service.getOrCreateDailySet(today, Difficulty.EASY, TimeFormat.H24)
        val q = service.load(id)!!.questions.first()
        service.recordAnswer(q.id, correctAnswerFor(q))
        service.clearProgress()
        assertTrue(service.history().isEmpty())
        assertEquals(0, service.totals().answered)
    }
}
