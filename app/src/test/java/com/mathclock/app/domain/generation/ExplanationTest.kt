package com.mathclock.app.domain.generation

import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.domain.time.TimeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExplanationTest {

    @Test
    fun readTheClockExplanation() {
        val text = ExerciseText.readSteps(TimeMath.of(3, 25), TimeFormat.H12).joinToString(" ")
        assertTrue(text.startsWith("The short hand is between 3 and 4. The long hand points to 5, which means 25 minutes. The time is 3:25."))
        assertTrue(text.endsWith("So the answer is 3:25 AM."))
    }

    @Test
    fun readTheClockOneMinutePrecision() {
        val text = ExerciseText.readSteps(TimeMath.of(15, 27), TimeFormat.H24).joinToString(" ")
        assertTrue(text.contains("2 small marks past 5, which means 25 + 2 = 27 minutes"))
        assertTrue(text.endsWith("15:27."))
    }

    @Test
    fun timeAfterExplanation() {
        val text = ExerciseText.afterSteps(TimeMath.of(14, 20), 45, TimeFormat.H24).joinToString(" ")
        assertEquals("From 14:20, add 40 minutes to reach 15:00. Add 5 more minutes to reach 15:05.", text)
    }

    @Test
    fun timeAfterAcrossMidnight() {
        val steps = ExerciseText.afterSteps(TimeMath.of(23, 40), 35, TimeFormat.H24)
        assertEquals("From 23:40, add 20 minutes to reach midnight (00:00).", steps[0])
        assertEquals("The answer is 00:15 the next day.", steps.last())
    }

    @Test
    fun minutesBetweenExplanation() {
        val ex = Exercise(Topic.MINUTES_BETWEEN, TimeMath.of(14, 20), TimeMath.of(15, 5), 45)
        assertEquals(
            "From 14:20 to 15:00 is 40 minutes. From 15:00 to 15:05 is 5 minutes. Total: 40 + 5 = 45 minutes.",
            ExerciseText.explanation(ex, TimeFormat.H24),
        )
    }

    @Test
    fun midnightCrossingExplanation() {
        val ex = Exercise(Topic.MINUTES_BETWEEN, TimeMath.of(23, 40), TimeMath.of(0, 15), 35, crossesMidnight = true)
        assertEquals(
            "From 23:40 to midnight is 20 minutes. Add 15 minutes after midnight. Total: 35 minutes.",
            ExerciseText.explanation(ex, TimeFormat.H24),
        )
        assertEquals(
            "How many minutes are there from 23:40 to 00:15 the next day?",
            ExerciseText.prompt(ex, TimeFormat.H24),
        )
    }

    /** The arithmetic stated in every generated explanation must agree with the correct answer. */
    @Test
    fun explanationsAgreeWithAnswers() {
        val gen = QuestionGenerator(Random(7))
        repeat(400) {
            for (d in Difficulty.entries) {
                val after = gen.generate(Topic.TIME_AFTER, d)
                val afterText = ExerciseText.explanation(after, TimeFormat.H24)
                assertTrue(afterText, afterText.contains(TimeMath.format24(after.correctAnswer)))
                val between = gen.generate(Topic.MINUTES_BETWEEN, d)
                val betweenText = ExerciseText.explanation(between, TimeFormat.H12)
                assertTrue(betweenText, betweenText.contains("${between.durationMinutes} minute"))
                val hint = ExerciseText.hint(between, TimeFormat.H24)
                assertFalse(hint, hint.startsWith("Total"))
                val read = gen.generate(Topic.READ_CLOCK, d)
                assertTrue(ExerciseText.explanation(read, TimeFormat.H12).contains(TimeMath.format12(read.correctAnswer)))
            }
        }
    }

    @Test
    fun sumsInBetweenExplanationsAddUp() {
        val gen = QuestionGenerator(Random(11))
        repeat(500) {
            val ex = gen.generate(Topic.MINUTES_BETWEEN, Difficulty.HARD)
            val total = ExerciseText.betweenSteps(ex, TimeFormat.H24).last()
            val m = Regex("Total: (.*) = (\\d+) minutes").find(total)
            if (m != null) {
                val sum = m.groupValues[1].split(" + ").sumOf { it.trim().toInt() }
                assertEquals(ex.durationMinutes, sum)
                assertEquals(ex.durationMinutes, m.groupValues[2].toInt())
            }
        }
    }
}
