package com.mathclock.app.domain.time

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class ClockHand { HOUR, MINUTE }

/**
 * Pure analog-clock geometry. Angles are degrees clockwise from 12 o'clock, in [0, 360).
 */
object ClockGeometry {
    fun minuteAngle(minutes: Int): Float = TimeMath.minute(minutes) * 6f

    /** 30° per hour plus 0.5° per minute: the hour hand moves continuously. */
    fun hourAngle(minutes: Int): Float =
        (TimeMath.hour24(minutes) % 12) * 30f + TimeMath.minute(minutes) * 0.5f

    /** Angle of point (x, y) relative to centre (cx, cy) in screen coordinates (y grows downwards). */
    fun pointerAngle(x: Float, y: Float, cx: Float, cy: Float): Float {
        val deg = Math.toDegrees(atan2((x - cx).toDouble(), (cy - y).toDouble())).toFloat()
        return normalizeAngle(deg)
    }

    fun normalizeAngle(deg: Float): Float {
        val r = deg % 360f
        return if (r < 0f) r + 360f else r
    }

    fun angularDistance(a: Float, b: Float): Float {
        val d = abs(normalizeAngle(a) - normalizeAngle(b))
        return if (d > 180f) 360f - d else d
    }

    /**
     * Chooses which hand a drag grabs. The nearest hand by angle wins; when both hands are
     * close to the touch angle, the distance from the centre decides (minute hand is longer).
     */
    fun selectHand(
        minutes: Int,
        x: Float,
        y: Float,
        cx: Float,
        cy: Float,
        radius: Float,
    ): ClockHand {
        val angle = pointerAngle(x, y, cx, cy)
        val dHour = angularDistance(angle, hourAngle(minutes))
        val dMinute = angularDistance(angle, minuteAngle(minutes))
        val dist = hypot(x - cx, y - cy)
        if (abs(dHour - dMinute) < 20f) {
            return if (dist > radius * 0.58f) ClockHand.MINUTE else ClockHand.HOUR
        }
        return if (dHour < dMinute) ClockHand.HOUR else ClockHand.MINUTE
    }

    fun snapMinute(minute: Int, step: Int): Int {
        require(step in 1..60)
        val snapped = ((minute.toFloat() / step).roundToInt() * step)
        return Math.floorMod(snapped, 60)
    }

    /**
     * Moves the minute hand to [angle]. The change is applied as the shortest signed movement,
     * so dragging past 12 carries into the next (or previous) hour without large jumps.
     */
    fun dragMinute(current: Int, angle: Float, step: Int): Int {
        val target = snapMinute((normalizeAngle(angle) / 6f).roundToInt() % 60, step)
        val cur = TimeMath.minute(current)
        var delta = target - cur
        if (delta > 30) delta -= 60
        if (delta < -30) delta += 60
        return TimeMath.normalize(current + delta)
    }

    /**
     * Moves the hour hand to [angle] while keeping the minute and the AM/PM half:
     * crossing 12 with the hour hand never flips AM/PM by itself.
     */
    fun dragHour(current: Int, angle: Float): Int {
        val minute = TimeMath.minute(current)
        val base = normalizeAngle(angle) - minute * 0.5f
        val h12 = Math.floorMod((base / 30f).roundToInt(), 12)
        return TimeMath.halfStart(current) + h12 * 60 + minute
    }

    /** Snaps an arbitrary time value to the given minute precision (keeps hour; may carry). */
    fun snapTime(minutes: Int, step: Int): Int {
        val m = TimeMath.minute(minutes)
        val snapped = (m.toFloat() / step).roundToInt() * step
        return TimeMath.normalize(TimeMath.hour24(minutes) * 60 + snapped)
    }
}
