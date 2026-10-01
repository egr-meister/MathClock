package com.mathclock.app.domain.generation

import com.mathclock.app.domain.progress.Scoring
import com.mathclock.app.domain.time.TimeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionGeneratorTest {

    private fun generator(seed: Int) = QuestionGenerator(Random(seed))

    @Test
    fun fourUniqueOptionsWithExactlyOneCorrect() {
        for (seed in 0 until 300) {
            val gen = generator(seed)
            for (d in Difficulty.entries) {
                for (topic in listOf(Topic.READ_CLOCK, Topic.TIME_AFTER, Topic.MINUTES_BETWEEN)) {
                    val ex = gen.generate(topic, d)
                    assertEquals(4, ex.options.size)
                    val normalized = ex.options.map {
                        when (topic) {
                            Topic.READ_CLOCK -> TimeMath.normalize(it)
                            Topic.TIME_AFTER -> TimeMath.normalize(it)
                            else -> it
                        }
                    }
                    assertEquals("options must be distinct: ${ex.options}", 4, normalized.toSet().size)
                    assertEquals(1, ex.options.count { it == ex.correctAnswer })
                    // Visible labels are distinct too (no ambiguous equivalents).
                    for (f in com.mathclock.app.domain.time.TimeFormat.entries) {
                        val labels = ex.options.map { ExerciseText.optionLabel(ex, it, f) }
                        assertEquals(4, labels.toSet().size)
                    }
                }
            }
        }
    }

    @Test
    fun readClockOptionsStayInTheSameHalfOfDay() {
        for (seed in 0 until 200) {
            val ex = generator(seed).generate(Topic.READ_CLOCK, Difficulty.MEDIUM)
            val pm = TimeMath.isPm(ex.correctAnswer)
            assertTrue(ex.options.all { TimeMath.isPm(it) == pm })
        }
    }

    @Test
    fun difficultyPrecisionIsRespected() {
        for (seed in 0 until 300) {
            val gen = generator(seed)
            for (d in Difficulty.entries) {
                Topic.entries.forEach { topic ->
                    val ex = gen.generate(topic, d)
                    assertEquals(0, ex.startMinutes % d.precision)
                    if (topic == Topic.READ_CLOCK || topic == Topic.SET_CLOCK) {
                        assertEquals(0, ex.correctAnswer % d.precision)
                        if (topic == Topic.READ_CLOCK) assertTrue(ex.options.all { it % d.precision == 0 })
                    }
                }
            }
        }
    }

    @Test
    fun durationsMatchDifficultyTable() {
        for (seed in 0 until 300) {
            val gen = generator(seed)
            for (d in Difficulty.entries) {
                val after = gen.generate(Topic.TIME_AFTER, d)
                assertTrue(after.durationMinutes!! in d.timeAfterDurations)
                assertEquals(after.startMinutes + after.durationMinutes!!, after.correctAnswer)
                val between = gen.generate(Topic.MINUTES_BETWEEN, d)
                assertTrue(between.durationMinutes!! in d.minutesBetweenDurations)
                assertEquals(
                    between.durationMinutes,
                    TimeMath.elapsed(between.startMinutes, between.endMinutes!!, between.crossesMidnight),
                )
            }
        }
        assertEquals(listOf(30, 60), Difficulty.EASY.timeAfterDurations)
        assertEquals(listOf(30, 60, 90, 120), Difficulty.EASY.minutesBetweenDurations)
        assertEquals(5, Difficulty.MEDIUM.minutesBetweenDurations.first())
        assertEquals(180, Difficulty.MEDIUM.minutesBetweenDurations.last())
        assertEquals(240, Difficulty.HARD.minutesBetweenDurations.last())
    }

    @Test
    fun easyAndMediumStayWithinTheSameDay() {
        for (seed in 0 until 500) {
            val gen = generator(seed)
            for (d in listOf(Difficulty.EASY, Difficulty.MEDIUM)) {
                val after = gen.generate(Topic.TIME_AFTER, d)
                assertFalse(after.crossesMidnight)
                assertTrue(after.correctAnswer < TimeMath.MINUTES_PER_DAY)
                assertTrue(after.options.all { it in 0 until TimeMath.MINUTES_PER_DAY })
                val between = gen.generate(Topic.MINUTES_BETWEEN, d)
                assertFalse(between.crossesMidnight)
                assertTrue(between.endMinutes!! > between.startMinutes)
            }
        }
    }

    @Test
    fun hardMayCrossMidnightAndIsLabeled() {
        var crossings = 0
        for (seed in 0 until 300) {
            val gen = generator(seed)
            val after = gen.generate(Topic.TIME_AFTER, Difficulty.HARD)
            if (after.crossesMidnight) {
                crossings++
                assertTrue(after.correctAnswer >= TimeMath.MINUTES_PER_DAY)
                assertTrue(ExerciseText.optionLabel(after, after.correctAnswer, com.mathclock.app.domain.time.TimeFormat.H24).endsWith("(the next day)"))
            }
            val between = gen.generate(Topic.MINUTES_BETWEEN, Difficulty.HARD)
            if (between.crossesMidnight) {
                crossings++
                assertTrue(ExerciseText.prompt(between, com.mathclock.app.domain.time.TimeFormat.H24).contains("the next day"))
            }
        }
        assertTrue(crossings > 20)
    }

    @Test
    fun setClockStartsAwayFromTarget() {
        for (seed in 0 until 200) {
            val ex = generator(seed).generate(Topic.SET_CLOCK, Difficulty.EASY)
            assertTrue(ex.startMinutes != ex.correctAnswer)
            assertTrue(ex.options.isEmpty())
        }
    }

    @Test
    fun sessionHasNoDuplicates() {
        for (seed in 0 until 100) {
            val gen = generator(seed)
            val topics = gen.freePracticeTopics(Topic.READ_CLOCK)
            val session = gen.generateSession(topics, Difficulty.EASY)
            assertEquals(10, session.size)
            assertEquals(10, session.map { it.key }.toSet().size)
        }
    }

    @Test
    fun seededGenerationIsDeterministic() {
        val a = generator(42).generateSession(DailyPlanner.topicsFor(20000, Random(1)), Difficulty.MEDIUM)
        val b = generator(42).generateSession(DailyPlanner.topicsFor(20000, Random(1)), Difficulty.MEDIUM)
        assertEquals(a, b)
    }

    @Test
    fun dailyPlanHasTwoOfEachPlusTwoRotatingExtras() {
        for (day in 0L until 8L) {
            val topics = DailyPlanner.topicsFor(day, Random(day))
            assertEquals(10, topics.size)
            val counts = topics.groupingBy { it }.eachCount()
            Topic.entries.forEach { assertTrue(counts.getValue(it) >= 2) }
            val extras = counts.filterValues { it == 3 }.keys
            assertEquals(2, extras.size)
        }
        val day0 = DailyPlanner.topicsFor(0, Random(0)).groupingBy { it }.eachCount().filterValues { it == 3 }.keys
        val day1 = DailyPlanner.topicsFor(1, Random(0)).groupingBy { it }.eachCount().filterValues { it == 3 }.keys
        assertTrue(day0 != day1)
    }

    @Test
    fun setClockValidationComparesNormalizedTimes() {
        val ex = Exercise(Topic.SET_CLOCK, startMinutes = 0, targetMinutes = TimeMath.of(15, 25))
        assertTrue(Scoring.isCorrect(ex, TimeMath.of(15, 25), 5))
        assertTrue(Scoring.isCorrect(ex, TimeMath.of(15, 26), 5)) // snaps to 15:25
        assertFalse(Scoring.isCorrect(ex, TimeMath.of(3, 25), 5)) // wrong half of the day
        assertFalse(Scoring.isCorrect(ex, TimeMath.of(15, 30), 5))
        val hard = Exercise(Topic.SET_CLOCK, startMinutes = 0, targetMinutes = TimeMath.of(15, 27))
        assertFalse(Scoring.isCorrect(hard, TimeMath.of(15, 26), 1))
    }
}
