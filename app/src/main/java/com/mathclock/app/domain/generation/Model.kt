package com.mathclock.app.domain.generation

enum class Topic(val title: String, val shortTitle: String) {
    READ_CLOCK("Read the Clock", "Read"),
    SET_CLOCK("Set the Clock", "Set"),
    TIME_AFTER("Time After", "After"),
    MINUTES_BETWEEN("Minutes Between", "Between"),
}

enum class Difficulty(
    val label: String,
    /** Minute precision of clock times. */
    val precision: Int,
    val timeAfterDurations: List<Int>,
    val minutesBetweenDurations: List<Int>,
    val allowsMidnightCrossing: Boolean,
) {
    EASY(
        label = "Easy",
        precision = 30,
        timeAfterDurations = listOf(30, 60),
        minutesBetweenDurations = (30..120 step 30).toList(),
        allowsMidnightCrossing = false,
    ),
    MEDIUM(
        label = "Medium",
        precision = 5,
        timeAfterDurations = (5..120 step 5).toList(),
        minutesBetweenDurations = (5..180 step 5).toList(),
        allowsMidnightCrossing = false,
    ),
    HARD(
        label = "Hard",
        precision = 1,
        timeAfterDurations = (1..180).toList(),
        minutesBetweenDurations = (1..240).toList(),
        allowsMidnightCrossing = true,
    ),
    ;

    val precisionDescription: String
        get() = when (this) {
            EASY -> "Whole and half hours"
            MEDIUM -> "Five-minute steps"
            HARD -> "One-minute steps"
        }
}

/**
 * A generated exercise. Value conventions:
 * - READ_CLOCK: [targetMinutes] is the time shown; options are times (0..1439).
 * - SET_CLOCK: [targetMinutes] is the time to set; [startMinutes] is the initial hand position; no options.
 * - TIME_AFTER: [targetMinutes] is start + duration as an absolute value (≥ 1440 means the next day);
 *   options are absolute values too.
 * - MINUTES_BETWEEN: [endMinutes] is the normalized end time; [durationMinutes] is the answer;
 *   options are durations. [crossesMidnight] says the end time is on the next day.
 */
data class Exercise(
    val topic: Topic,
    val startMinutes: Int,
    val endMinutes: Int? = null,
    val durationMinutes: Int? = null,
    val targetMinutes: Int? = null,
    val crossesMidnight: Boolean = false,
    val options: List<Int> = emptyList(),
) {
    val correctAnswer: Int
        get() = when (topic) {
            Topic.READ_CLOCK, Topic.SET_CLOCK, Topic.TIME_AFTER -> requireNotNull(targetMinutes)
            Topic.MINUTES_BETWEEN -> requireNotNull(durationMinutes)
        }

    /** Identity used to avoid duplicate exercises within a session. */
    val key: String
        get() = if (topic == Topic.SET_CLOCK) {
            "${topic.name}:$targetMinutes"
        } else {
            "${topic.name}:$startMinutes:$endMinutes:$durationMinutes:$targetMinutes"
        }
}
