package com.mathclock.app.domain.generation

import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.domain.time.TimeMath

/** Prompts, option labels, hints and explanations — all generated locally. */
object ExerciseText {

    fun minutesLabel(n: Int): String = if (n == 1) "1 minute" else "$n minutes"
    private fun hoursLabel(n: Int): String = if (n == 1) "1 hour" else "$n hours"

    /** Formats an absolute minute value (may be ≥ 1440). */
    private fun t(abs: Int, f: TimeFormat): String = TimeMath.format(abs, f)

    fun prompt(ex: Exercise, f: TimeFormat): String = when (ex.topic) {
        Topic.READ_CLOCK -> "What time is it?"
        Topic.SET_CLOCK -> "Set the clock to ${t(ex.correctAnswer, f)}."
        Topic.TIME_AFTER ->
            "It is ${t(ex.startMinutes, f)}. What time will it be in ${minutesLabel(requireNotNull(ex.durationMinutes))}?"
        Topic.MINUTES_BETWEEN -> {
            val end = requireNotNull(ex.endMinutes)
            if (ex.crossesMidnight) {
                "How many minutes are there from ${t(ex.startMinutes, f)} to ${t(end, f)} the next day?"
            } else {
                "How many minutes are there from ${t(ex.startMinutes, f)} to ${t(end, f)}?"
            }
        }
    }

    /** Extra context shown with the prompt. Never hides information needed to answer. */
    fun context(ex: Exercise): String? = when (ex.topic) {
        Topic.READ_CLOCK -> TimeMath.dayPartContext(ex.correctAnswer) +
            " An analog dial repeats every 12 hours, so this tells you which half of the day it is."
        Topic.SET_CLOCK -> null
        Topic.TIME_AFTER -> null
        Topic.MINUTES_BETWEEN -> if (ex.crossesMidnight) "The end time is on the next day." else "Both times are on the same day."
    }

    fun optionLabel(ex: Exercise, option: Int, f: TimeFormat): String = when (ex.topic) {
        Topic.READ_CLOCK -> t(option, f)
        Topic.TIME_AFTER -> t(option, f) + if (option >= TimeMath.MINUTES_PER_DAY) " (the next day)" else ""
        Topic.MINUTES_BETWEEN -> minutesLabel(option)
        Topic.SET_CLOCK -> t(option, f)
    }

    fun answerLabel(ex: Exercise, answer: Int, f: TimeFormat): String = optionLabel(ex, answer, f)

    // ------------------------------------------------------------------ explanations

    fun explanation(ex: Exercise, f: TimeFormat): String = when (ex.topic) {
        Topic.READ_CLOCK -> readSteps(ex.correctAnswer, f).joinToString(" ")
        Topic.SET_CLOCK -> setSteps(ex.correctAnswer, f).joinToString(" ")
        Topic.TIME_AFTER -> afterSteps(ex.startMinutes, requireNotNull(ex.durationMinutes), f).joinToString(" ")
        Topic.MINUTES_BETWEEN -> betweenSteps(ex, f).joinToString(" ")
    }

    /** An intermediate step — never the final answer. */
    fun hint(ex: Exercise, f: TimeFormat): String = when (ex.topic) {
        Topic.READ_CLOCK -> {
            val h = TimeMath.hour12(ex.correctAnswer)
            "Look at the short hand first: it is at or just past $h. Then count the long hand in 5-minute steps."
        }
        Topic.SET_CLOCK -> {
            val m = TimeMath.minute(ex.correctAnswer)
            "Start with the long hand: ${minuteHandPosition(m)}. Then move the short hand to the hour."
        }
        Topic.TIME_AFTER -> {
            val steps = afterSteps(ex.startMinutes, requireNotNull(ex.durationMinutes), f)
            if (steps.size > 1) steps.first() else "Add the minutes to the minute part first, then check if you reach a new hour."
        }
        Topic.MINUTES_BETWEEN -> {
            val steps = betweenSteps(ex, f)
            if (steps.size > 1) steps.first() else "Count on from the start time to the end time."
        }
    }

    private fun minuteHandPosition(m: Int): String {
        val number = if (m / 5 == 0) 12 else m / 5
        val marks = m % 5
        return when {
            m == 0 -> "point it at 12 for 0 minutes"
            marks == 0 -> "point it at $number for $m minutes"
            else -> "put it $marks small ${if (marks == 1) "mark" else "marks"} past $number for $m minutes"
        }
    }

    fun readSteps(time: Int, f: TimeFormat): List<String> {
        val h = TimeMath.hour12(time)
        val next = h % 12 + 1
        val m = TimeMath.minute(time)
        val dial = TimeMath.formatDial(time)
        val steps = mutableListOf<String>()
        when {
            m == 0 -> {
                steps += "The long hand points to 12, which means 0 minutes."
                steps += "The short hand points to $h. The time is $dial."
            }
            m == 30 -> {
                steps += "The short hand is halfway between $h and $next."
                steps += "The long hand points to 6, which means 30 minutes. The time is $dial."
            }
            m % 5 == 0 -> {
                steps += "The short hand is between $h and $next."
                steps += "The long hand points to ${m / 5}, which means $m minutes. The time is $dial."
            }
            else -> {
                val number = if (m / 5 == 0) 12 else m / 5
                val base = (m / 5) * 5
                val marks = m % 5
                steps += "The short hand is between $h and $next."
                steps += "The long hand is $marks small ${if (marks == 1) "mark" else "marks"} past $number, " +
                    "which means $base + $marks = $m minutes. The time is $dial."
            }
        }
        steps += "${TimeMath.dayPartContext(time)} So the answer is ${t(time, f)}."
        return steps
    }

    fun setSteps(time: Int, f: TimeFormat): List<String> {
        val h = TimeMath.hour12(time)
        val next = h % 12 + 1
        val m = TimeMath.minute(time)
        val shortHand = if (m == 0) "points exactly to $h" else "is a little past $h, on the way to $next"
        val steps = mutableListOf(
            "To show ${t(time, f)}, ${minuteHandPosition(m).replaceFirst("point it", "point the long hand").replaceFirst("put it", "put the long hand")}.",
            "The short hand $shortHand.",
        )
        val period = if (TimeMath.isPm(time)) "PM" else "AM"
        steps += when (f) {
            TimeFormat.H12 -> {
                val n = TimeMath.normalize(time)
                val part = when (n) {
                    TimeMath.NOON -> "noon"
                    TimeMath.MIDNIGHT -> "midnight"
                    else -> TimeMath.dayPart(n).let { if (it == "night") "at night" else "in the $it" }
                }
                "Choose $period because it is $part."
            }
            TimeFormat.H24 -> "${TimeMath.format24(time)} is ${TimeMath.format12(time)}, so the hour is ${TimeMath.hour24(time)}."
        }
        return steps
    }

    private fun reach(abs: Int, f: TimeFormat): String = when {
        abs == TimeMath.MINUTES_PER_DAY -> "midnight (${t(abs, f)})"
        abs > TimeMath.MINUTES_PER_DAY -> "${t(abs, f)} the next day"
        else -> t(abs, f)
    }

    fun afterSteps(start: Int, duration: Int, f: TimeFormat): List<String> {
        val steps = mutableListOf<String>()
        var cur = start
        var rem = duration
        val m = TimeMath.minute(start)
        if (m != 0 && rem >= 60 - m) {
            val a = 60 - m
            cur += a; rem -= a
            steps += "From ${t(start, f)}, add ${minutesLabel(a)} to reach ${reach(cur, f)}."
        } else if (rem < 60) {
            val end = start + rem
            steps += "From ${t(start, f)}, add ${minutesLabel(rem)}: $m + $rem = ${m + rem}, so the time is ${reach(end, f)}."
            return steps
        }
        val hours = rem / 60
        if (hours > 0) {
            cur += hours * 60; rem -= hours * 60
            steps += if (steps.isEmpty()) {
                "From ${t(start, f)}, add ${hoursLabel(hours)} to reach ${reach(cur, f)}."
            } else {
                "Add ${hoursLabel(hours)} to reach ${reach(cur, f)}."
            }
        }
        if (rem > 0) {
            cur += rem
            steps += "Add ${rem} more ${if (rem == 1) "minute" else "minutes"} to reach ${reach(cur, f)}."
        }
        if (cur >= TimeMath.MINUTES_PER_DAY) {
            steps += "The answer is ${t(cur, f)} the next day."
        }
        return steps
    }

    fun betweenSteps(ex: Exercise, f: TimeFormat): List<String> {
        val start = ex.startMinutes
        val end = requireNotNull(ex.endMinutes)
        val total = requireNotNull(ex.durationMinutes)
        if (ex.crossesMidnight) {
            val toMidnight = TimeMath.MINUTES_PER_DAY - start
            return if (end == 0) {
                listOf(
                    "From ${t(start, f)} to midnight is ${minutesLabel(toMidnight)}.",
                    "The end time is exactly midnight. Total: ${minutesLabel(total)}.",
                )
            } else {
                listOf(
                    "From ${t(start, f)} to midnight is ${minutesLabel(toMidnight)}.",
                    "Add ${minutesLabel(end)} after midnight.",
                    "Total: ${minutesLabel(total)}.",
                )
            }
        }
        val steps = mutableListOf<String>()
        val parts = mutableListOf<Int>()
        val sm = TimeMath.minute(start)
        var cur = start
        val nextHour = start - sm + 60
        if (end < nextHour || (sm == 0 && end - start < 60)) {
            return listOf(
                "Both times are in the same hour: ${TimeMath.minute(end)} − $sm = $total.",
                "Total: ${minutesLabel(total)}.",
            )
        }
        if (sm != 0) {
            parts += nextHour - start
            steps += "From ${t(start, f)} to ${t(nextHour, f)} is ${minutesLabel(nextHour - start)}."
            cur = nextHour
        }
        val endHourStart = end - TimeMath.minute(end)
        if (endHourStart > cur) {
            val hours = (endHourStart - cur) / 60
            parts += hours * 60
            steps += "From ${t(cur, f)} to ${t(endHourStart, f)} is ${hoursLabel(hours)} = ${minutesLabel(hours * 60)}."
            cur = endHourStart
        }
        if (end > cur) {
            parts += end - cur
            steps += "From ${t(cur, f)} to ${t(end, f)} is ${minutesLabel(end - cur)}."
        }
        steps += if (parts.size > 1) {
            "Total: ${parts.joinToString(" + ")} = ${minutesLabel(total)}."
        } else {
            "Total: ${minutesLabel(total)}."
        }
        return steps
    }
}
