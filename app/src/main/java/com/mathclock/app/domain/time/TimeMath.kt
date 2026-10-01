package com.mathclock.app.domain.time

import java.util.Locale

/**
 * Learning times are integer minutes from midnight (0..1439). Exercise times are
 * fictional: no time zones, no daylight-saving rules, no device clock involved.
 * Values ≥ 1440 are only used transiently to express "the next day".
 */
object TimeMath {
    const val MINUTES_PER_DAY = 1440
    const val MINUTES_PER_HALF_DAY = 720
    const val NOON = 720
    const val MIDNIGHT = 0

    fun normalize(minutes: Int): Int = Math.floorMod(minutes, MINUTES_PER_DAY)

    fun of(hour24: Int, minute: Int): Int {
        require(hour24 in 0..23) { "hour must be 0..23" }
        require(minute in 0..59) { "minute must be 0..59" }
        return hour24 * 60 + minute
    }

    fun hour24(minutes: Int): Int = normalize(minutes) / 60
    fun minute(minutes: Int): Int = normalize(minutes) % 60

    /** 1..12 */
    fun hour12(minutes: Int): Int {
        val h = hour24(minutes) % 12
        return if (h == 0) 12 else h
    }

    fun isPm(minutes: Int): Boolean = normalize(minutes) >= NOON

    /** Start of the AM or PM half that contains [minutes] (0 or 720). */
    fun halfStart(minutes: Int): Int = if (isPm(minutes)) NOON else MIDNIGHT

    fun format24(minutes: Int): String = String.format(Locale.US, "%02d:%02d", hour24(minutes), minute(minutes))

    fun format12(minutes: Int): String =
        String.format(Locale.US, "%d:%02d %s", hour12(minutes), minute(minutes), if (isPm(minutes)) "PM" else "AM")

    fun format(minutes: Int, format: TimeFormat): String = when (format) {
        TimeFormat.H24 -> format24(minutes)
        TimeFormat.H12 -> format12(minutes)
    }

    /** Clock-face reading without AM/PM, e.g. "3:25". */
    fun formatDial(minutes: Int): String = String.format(Locale.US, "%d:%02d", hour12(minutes), minute(minutes))

    data class AddResult(val minutes: Int, val dayOffset: Int) {
        val crossesMidnight: Boolean get() = dayOffset != 0
    }

    fun add(start: Int, duration: Int): AddResult {
        val total = normalize(start) + duration
        return AddResult(Math.floorMod(total, MINUTES_PER_DAY), Math.floorDiv(total, MINUTES_PER_DAY))
    }

    /**
     * Elapsed minutes from [start] to [end]. The day relationship must be explicit:
     * when [endIsNextDay] is false, [end] must not be before [start].
     */
    fun elapsed(start: Int, end: Int, endIsNextDay: Boolean): Int {
        val s = normalize(start)
        val e = normalize(end)
        return if (endIsNextDay) {
            e + MINUTES_PER_DAY - s
        } else {
            require(e >= s) { "End is before start on the same day; use endIsNextDay = true" }
            e - s
        }
    }

    /** Replaces the hour while keeping minutes and the AM/PM half. [hour12] is 1..12. */
    fun withHour12(minutes: Int, hour12: Int): Int {
        require(hour12 in 1..12)
        return halfStart(minutes) + (hour12 % 12) * 60 + minute(minutes)
    }

    fun withHour24(minutes: Int, hour24: Int): Int = of(hour24, minute(minutes))

    fun withMinute(minutes: Int, minute: Int): Int = of(hour24(minutes), minute)

    fun withPm(minutes: Int, pm: Boolean): Int {
        val inHalf = normalize(minutes) % MINUTES_PER_HALF_DAY
        return inHalf + if (pm) NOON else MIDNIGHT
    }

    fun dayPart(minutes: Int): String = when (hour24(minutes)) {
        in 0..4 -> "night"
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }

    /** Human context sentence that tells which half of the day an analog reading belongs to. */
    fun dayPartContext(minutes: Int): String {
        val n = normalize(minutes)
        val period = if (isPm(n)) "PM" else "AM"
        return when {
            n == NOON -> "It is noon (PM)."
            n == MIDNIGHT -> "It is midnight (AM)."
            else -> "It is ${dayPart(n)} ($period)."
        }
    }
}

enum class TimeFormat(val label: String) {
    H24("24-hour"),
    H12("12-hour"),
}
