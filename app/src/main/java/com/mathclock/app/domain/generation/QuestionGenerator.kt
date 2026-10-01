package com.mathclock.app.domain.generation

import com.mathclock.app.domain.time.TimeMath
import kotlin.random.Random

/**
 * Deterministic for a given [random] seed. All loops are bounded; candidate pools are always
 * large enough to fill four distinct options (fallback enumeration guarantees termination).
 */
class QuestionGenerator(private val random: Random) {

    fun generate(topic: Topic, difficulty: Difficulty): Exercise = when (topic) {
        Topic.READ_CLOCK -> readClock(difficulty)
        Topic.SET_CLOCK -> setClock(difficulty)
        Topic.TIME_AFTER -> timeAfter(difficulty)
        Topic.MINUTES_BETWEEN -> minutesBetween(difficulty)
    }

    /** Generates one exercise per topic entry, avoiding duplicates within the list. */
    fun generateSession(topics: List<Topic>, difficulty: Difficulty): List<Exercise> {
        val used = HashSet<String>()
        return topics.map { topic ->
            var ex = generate(topic, difficulty)
            var attempts = 0
            while (ex.key in used && attempts < MAX_ATTEMPTS) {
                ex = generate(topic, difficulty)
                attempts++
            }
            used += ex.key
            ex
        }
    }

    /** Topic list for a free-practice session of [count] questions. [topic] null = mixed. */
    fun freePracticeTopics(topic: Topic?, count: Int = SESSION_SIZE): List<Topic> =
        if (topic != null) {
            List(count) { topic }
        } else {
            val all = Topic.entries
            List(count) { all[it % all.size] }.shuffled(random)
        }

    // ------------------------------------------------------------------ topics

    private fun alignedTime(precision: Int, from: Int = 0, to: Int = TimeMath.MINUTES_PER_DAY - 1): Int {
        val first = ceilTo(from, precision)
        val last = (to / precision) * precision
        require(first <= last) { "Empty time range" }
        val slots = (last - first) / precision + 1
        return first + random.nextInt(slots) * precision
    }

    private fun readClock(d: Difficulty): Exercise {
        val t = alignedTime(d.precision)
        val half = TimeMath.halfStart(t)
        val inHalf = { c: Int -> half + Math.floorMod(c - half, TimeMath.MINUTES_PER_HALF_DAY) }
        val h = TimeMath.hour24(t)
        val m = TimeMath.minute(t)
        val hour12 = TimeMath.hour12(t)
        val candidates = mutableListOf<Int>()
        candidates += t + 60
        candidates += t - 60
        if (m % 5 == 0) {
            // Hour and minute confused: the minute hand's number read as the hour and vice versa.
            val swappedHour = if (m == 0) 12 else m / 5
            val swappedMinute = (hour12 % 12) * 5
            candidates += (swappedHour % 12) * 60 + swappedMinute
            // Number under the long hand read directly as minutes (e.g. "5" instead of 25).
            if (m in 10..55) candidates += h * 60 + m / 5 // kept only when it matches the level precision
        }
        when (d) {
            Difficulty.EASY -> {
                candidates += h * 60 + (m + 30) % 60
                candidates += (h + 1) * 60 + (m + 30) % 60
                candidates += (h - 1) * 60 + (m + 30) % 60
            }
            Difficulty.MEDIUM -> {
                candidates += t + 5; candidates += t - 5; candidates += t + 10; candidates += t - 10
                if (m >= 30) candidates += t + 60 - m // hour hand misread as the next hour
            }
            Difficulty.HARD -> {
                candidates += t + 1; candidates += t - 1; candidates += t + 5; candidates += t - 5
                candidates += t + 10
            }
        }
        val options = pickOptions(
            correct = t,
            candidates = candidates.map(inHalf),
            normalize = { Math.floorMod(it, TimeMath.MINUTES_PER_HALF_DAY) },
            isValid = { it % d.precision == 0 },
            filler = { inHalf(alignedTime(d.precision)) },
            fallback = { i -> inHalf(alternating(t, i, d.precision.coerceAtLeast(5))) },
        )
        return Exercise(topic = Topic.READ_CLOCK, startMinutes = t, targetMinutes = t, options = options)
    }

    private fun setClock(d: Difficulty): Exercise {
        val target = alignedTime(d.precision)
        var start = alignedTime(d.precision)
        var attempts = 0
        while (start == target && attempts < MAX_ATTEMPTS) {
            start = alignedTime(d.precision); attempts++
        }
        if (start == target) start = TimeMath.normalize(target + 60)
        // The starting position stays in the target's half of the day only when it helps; the
        // child still must choose AM/PM explicitly in 12-hour mode.
        return Exercise(topic = Topic.SET_CLOCK, startMinutes = start, targetMinutes = target)
    }

    private fun shouldCrossMidnight(d: Difficulty): Boolean =
        d.allowsMidnightCrossing && random.nextInt(100) < MIDNIGHT_PERCENT

    private fun timeAfter(d: Difficulty): Exercise {
        val duration = d.timeAfterDurations[random.nextInt(d.timeAfterDurations.size)]
        val cross = shouldCrossMidnight(d)
        val start = if (cross) {
            alignedTime(d.precision, from = TimeMath.MINUTES_PER_DAY - duration)
        } else {
            alignedTime(d.precision, to = TimeMath.MINUTES_PER_DAY - 1 - duration)
        }
        val target = start + duration
        val crosses = target >= TimeMath.MINUTES_PER_DAY
        val deltas = when (d) {
            Difficulty.EASY -> listOf(60, -60, 30, -30, 90)
            Difficulty.MEDIUM -> listOf(60, -60, 10, -10, 5, -5)
            Difficulty.HARD -> listOf(60, -60, 10, -10, 1, -1, 5)
        }
        val candidates = mutableListOf<Int>()
        deltas.forEach { candidates += target + it }
        candidates += start - duration // subtracted instead of added
        if (duration >= 60) candidates += start + duration % 60 // hours of the duration ignored
        val validRange = if (crosses) (start + 1) until (start + TimeMath.MINUTES_PER_DAY) else 0 until TimeMath.MINUTES_PER_DAY
        val options = pickOptions(
            correct = target,
            candidates = candidates,
            normalize = { TimeMath.normalize(it) },
            isValid = { it in validRange },
            filler = {
                val dur = d.timeAfterDurations[random.nextInt(d.timeAfterDurations.size)] +
                    (random.nextInt(3) - 1) * 60
                start + dur
            },
            fallback = { i -> alternating(target, i, d.precision.coerceAtLeast(5)) },
        )
        return Exercise(
            topic = Topic.TIME_AFTER,
            startMinutes = start,
            endMinutes = TimeMath.normalize(target),
            durationMinutes = duration,
            targetMinutes = target,
            crossesMidnight = crosses,
            options = options,
        )
    }

    private fun minutesBetween(d: Difficulty): Exercise {
        val duration = d.minutesBetweenDurations[random.nextInt(d.minutesBetweenDurations.size)]
        val cross = shouldCrossMidnight(d)
        val start = if (cross) {
            alignedTime(d.precision, from = TimeMath.MINUTES_PER_DAY - duration)
        } else {
            alignedTime(d.precision, to = TimeMath.MINUTES_PER_DAY - 1 - duration)
        }
        val absEnd = start + duration
        val crosses = absEnd >= TimeMath.MINUTES_PER_DAY
        val end = TimeMath.normalize(absEnd)
        val candidates = mutableListOf<Int>()
        candidates += duration + 60
        candidates += duration - 60
        if (!crosses) {
            // Treating times as decimal numbers: 15:05 − 14:20 → 1505 − 1420 = 85.
            val decimal = (TimeMath.hour24(end) * 100 + TimeMath.minute(end)) -
                (TimeMath.hour24(start) * 100 + TimeMath.minute(start))
            candidates += decimal
        }
        candidates += kotlin.math.abs(TimeMath.minute(end) - TimeMath.minute(start)) // only the minutes compared
        when (d) {
            Difficulty.EASY -> { candidates += duration + 30; candidates += duration - 30 }
            Difficulty.MEDIUM -> { candidates += duration + 10; candidates += duration - 10; candidates += duration + 5 }
            Difficulty.HARD -> { candidates += duration + 10; candidates += duration - 10; candidates += duration + 1; candidates += duration - 1 }
        }
        val maxDuration = d.minutesBetweenDurations.last() + 60
        val options = pickOptions(
            correct = duration,
            candidates = candidates,
            normalize = { it },
            isValid = { it in 1..maxDuration },
            filler = { d.minutesBetweenDurations[random.nextInt(d.minutesBetweenDurations.size)] },
            fallback = { i -> alternating(duration, i, d.precision.coerceAtLeast(5)) },
        )
        return Exercise(
            topic = Topic.MINUTES_BETWEEN,
            startMinutes = start,
            endMinutes = end,
            durationMinutes = duration,
            crossesMidnight = crosses,
            options = options,
        )
    }

    /**
     * Picks exactly three distinct wrong options (distinct after [normalize], and distinct from the
     * correct answer), then shuffles the four options.
     */
    internal fun pickOptions(
        correct: Int,
        candidates: List<Int>,
        normalize: (Int) -> Int,
        isValid: (Int) -> Boolean,
        filler: () -> Int,
        fallback: (Int) -> Int,
    ): List<Int> {
        val chosen = LinkedHashMap<Int, Int>() // normalized -> raw
        chosen[normalize(correct)] = correct
        fun tryAdd(c: Int) {
            if (chosen.size < 4 && isValid(c) && normalize(c) !in chosen) chosen[normalize(c)] = c
        }
        candidates.shuffled(random).forEach(::tryAdd)
        var attempts = 0
        while (chosen.size < 4 && attempts < MAX_ATTEMPTS) {
            tryAdd(filler()); attempts++
        }
        var i = 0
        while (chosen.size < 4 && i < MAX_ATTEMPTS) {
            tryAdd(fallback(i)); i++
        }
        check(chosen.size == 4) { "Could not build four distinct options" }
        return chosen.values.toList().shuffled(random)
    }

    companion object {
        const val SESSION_SIZE = 10
        const val MAX_ATTEMPTS = 200
        const val MIDNIGHT_PERCENT = 30

        /** base+2s, base-2s, base+3s, base-3s, ... */
        internal fun alternating(base: Int, i: Int, step: Int): Int {
            val k = i / 2 + 2
            return if (i % 2 == 0) base + k * step else base - k * step
        }

        private fun ceilTo(value: Int, step: Int): Int = ((value + step - 1) / step) * step
    }
}

object DailyPlanner {
    /**
     * Two questions of each topic plus two extra questions from two different topics that rotate
     * with the date (epoch day), shuffled.
     */
    fun topicsFor(epochDay: Long, random: Random): List<Topic> {
        val all = Topic.entries
        val idx = Math.floorMod(epochDay, all.size.toLong()).toInt()
        val extras = listOf(all[idx], all[(idx + 1) % all.size])
        return (all + all + extras).shuffled(random)
    }
}
