package com.mathclock.app.domain.progress

import com.mathclock.app.domain.generation.Exercise
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.time.ClockGeometry
import com.mathclock.app.domain.time.TimeMath
import java.time.LocalDate
import kotlin.math.roundToInt

enum class SessionType { DAILY, FREE }

enum class SessionStatus { IN_PROGRESS, COMPLETED, ENDED_EARLY }

data class TopicScore(val answered: Int, val correct: Int)

object Scoring {
    /** Correct answers / answered questions × 100, or null when nothing was answered ("No answers yet"). */
    fun accuracyPercent(answered: Int, correct: Int): Int? =
        if (answered <= 0) null else ((correct * 100.0) / answered).roundToInt()

    fun accuracyLabel(answered: Int, correct: Int): String =
        accuracyPercent(answered, correct)?.let { "$it%" } ?: "No answers yet"

    /**
     * Checks a submitted answer. For SET_CLOCK the submitted value is the selected time, snapped to
     * the level precision, compared as normalized minutes (never as hand coordinates).
     */
    fun isCorrect(ex: Exercise, submitted: Int, precision: Int): Boolean = when (ex.topic) {
        Topic.SET_CLOCK ->
            TimeMath.normalize(ClockGeometry.snapTime(submitted, precision)) == TimeMath.normalize(ex.correctAnswer)
        else -> submitted == ex.correctAnswer
    }

    // Per-topic counts encoded as "READ_CLOCK=3/2;SET_CLOCK=1/1" (answered/correct).
    fun encodeTopicCounts(counts: Map<Topic, TopicScore>): String =
        Topic.entries.joinToString(";") { t -> val s = counts[t] ?: TopicScore(0, 0); "${t.name}=${s.answered}/${s.correct}" }

    fun decodeTopicCounts(text: String?): Map<Topic, TopicScore> {
        val result = Topic.entries.associateWith { TopicScore(0, 0) }.toMutableMap()
        text?.split(';')?.forEach { part ->
            val name = part.substringBefore('=', "")
            val nums = part.substringAfter('=', "").split('/')
            val topic = Topic.entries.firstOrNull { it.name == name } ?: return@forEach
            if (nums.size == 2) {
                val a = nums[0].toIntOrNull() ?: 0
                val c = nums[1].toIntOrNull() ?: 0
                result[topic] = TopicScore(a, c)
            }
        }
        return result
    }
}

/** Injected for tests. */
fun interface DateProvider {
    fun today(): LocalDate
}

fun interface Clock {
    fun nowMillis(): Long
}
