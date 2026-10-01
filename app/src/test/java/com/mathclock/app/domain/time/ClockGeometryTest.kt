package com.mathclock.app.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockGeometryTest {

    @Test
    fun handAngles() {
        assertEquals(0f, ClockGeometry.minuteAngle(TimeMath.of(3, 0)), 0.001f)
        assertEquals(150f, ClockGeometry.minuteAngle(TimeMath.of(3, 25)), 0.001f)
        assertEquals(90f, ClockGeometry.hourAngle(TimeMath.of(3, 0)), 0.001f)
        // Hour hand moves continuously: 0.5° per minute.
        assertEquals(102.5f, ClockGeometry.hourAngle(TimeMath.of(3, 25)), 0.001f)
        assertEquals(105f, ClockGeometry.hourAngle(TimeMath.of(15, 30)), 0.001f)
        assertEquals(359.5f, ClockGeometry.hourAngle(TimeMath.of(11, 59)), 0.001f)
        assertEquals(0f, ClockGeometry.hourAngle(TimeMath.of(12, 0)), 0.001f)
    }

    @Test
    fun pointerAngleUsesScreenCoordinates() {
        assertEquals(0f, ClockGeometry.pointerAngle(100f, 0f, 100f, 100f), 0.01f)
        assertEquals(90f, ClockGeometry.pointerAngle(200f, 100f, 100f, 100f), 0.01f)
        assertEquals(180f, ClockGeometry.pointerAngle(100f, 200f, 100f, 100f), 0.01f)
        assertEquals(270f, ClockGeometry.pointerAngle(0f, 100f, 100f, 100f), 0.01f)
    }

    @Test
    fun minuteSnapping() {
        assertEquals(25, ClockGeometry.snapMinute(27, 5))
        assertEquals(30, ClockGeometry.snapMinute(28, 5))
        assertEquals(0, ClockGeometry.snapMinute(58, 5))
        assertEquals(30, ClockGeometry.snapMinute(40, 30))
        assertEquals(0, ClockGeometry.snapMinute(50, 30))
        assertEquals(17, ClockGeometry.snapMinute(17, 1))
    }

    @Test
    fun minuteDragWrapsForwardIntoNextHour() {
        val start = TimeMath.of(3, 55)
        val result = ClockGeometry.dragMinute(start, angle = 6f, step = 1) // 1 minute past 12
        assertEquals(TimeMath.of(4, 1), result)
    }

    @Test
    fun minuteDragWrapsBackwardIntoPreviousHour() {
        val start = TimeMath.of(4, 2)
        val result = ClockGeometry.dragMinute(start, angle = 354f, step = 1) // 59
        assertEquals(TimeMath.of(3, 59), result)
    }

    @Test
    fun minuteDragAcrossMidnightWrapsDay() {
        assertEquals(TimeMath.of(0, 0), ClockGeometry.dragMinute(TimeMath.of(23, 55), 0f, 5))
        assertEquals(TimeMath.of(23, 55), ClockGeometry.dragMinute(TimeMath.of(0, 0), 330f, 5))
    }

    @Test
    fun minuteDragSnapsToStep() {
        assertEquals(TimeMath.of(3, 30), ClockGeometry.dragMinute(TimeMath.of(3, 0), 170f, 30))
        assertEquals(TimeMath.of(3, 25), ClockGeometry.dragMinute(TimeMath.of(3, 0), 152f, 5))
    }

    @Test
    fun hourDragKeepsMinuteAndHalfOfDay() {
        // 11:20 PM, hour hand dragged to the 12 position → stays PM (12:20 PM), no AM/PM flip.
        val pm = TimeMath.of(23, 20)
        assertEquals(TimeMath.of(12, 20), ClockGeometry.dragHour(pm, 10f))
        // 11:20 AM dragged to 12 → 12:20 AM (still AM half).
        assertEquals(TimeMath.of(0, 20), ClockGeometry.dragHour(TimeMath.of(11, 20), 10f))
        assertEquals(TimeMath.of(15, 20), ClockGeometry.dragHour(TimeMath.of(13, 20), 100f))
    }

    @Test
    fun handSelectionPrefersNearestHand() {
        val t = TimeMath.of(3, 0) // hour at 90°, minute at 0°
        val cx = 100f; val cy = 100f; val r = 100f
        assertEquals(ClockHand.MINUTE, ClockGeometry.selectHand(t, 100f, 20f, cx, cy, r))
        assertEquals(ClockHand.HOUR, ClockGeometry.selectHand(t, 150f, 100f, cx, cy, r))
        // Hands overlapping (12:00): radius decides.
        val noon = TimeMath.of(12, 0)
        assertEquals(ClockHand.MINUTE, ClockGeometry.selectHand(noon, 100f, 10f, cx, cy, r))
        assertEquals(ClockHand.HOUR, ClockGeometry.selectHand(noon, 100f, 70f, cx, cy, r))
    }
}
