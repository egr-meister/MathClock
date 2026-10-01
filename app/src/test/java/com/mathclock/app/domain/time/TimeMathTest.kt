package com.mathclock.app.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeMathTest {

    @Test
    fun twelveAndTwentyFourHourConversion() {
        assertEquals("00:00", TimeMath.format24(0))
        assertEquals("12:00 AM", TimeMath.format12(0))
        assertEquals("12:00", TimeMath.format24(720))
        assertEquals("12:00 PM", TimeMath.format12(720))
        assertEquals("12:59 AM", TimeMath.format12(59))
        assertEquals("11:59 PM", TimeMath.format12(1439))
        assertEquals("14:20", TimeMath.format24(TimeMath.of(14, 20)))
        assertEquals("2:20 PM", TimeMath.format12(TimeMath.of(14, 20)))
        assertEquals("1:05 AM", TimeMath.format12(TimeMath.of(1, 5)))
    }

    @Test
    fun noonAndMidnightHalves() {
        assertFalse(TimeMath.isPm(0))
        assertFalse(TimeMath.isPm(719))
        assertTrue(TimeMath.isPm(720))
        assertTrue(TimeMath.isPm(1439))
        assertEquals(12, TimeMath.hour12(0))
        assertEquals(12, TimeMath.hour12(720))
        assertEquals(1, TimeMath.hour12(780))
        assertEquals("It is noon (PM).", TimeMath.dayPartContext(720))
        assertEquals("It is midnight (AM).", TimeMath.dayPartContext(0))
    }

    @Test
    fun addAcrossMidnight() {
        val r = TimeMath.add(TimeMath.of(23, 40), 35)
        assertEquals(TimeMath.of(0, 15), r.minutes)
        assertEquals(1, r.dayOffset)
        assertTrue(r.crossesMidnight)
        val same = TimeMath.add(TimeMath.of(14, 20), 45)
        assertEquals(TimeMath.of(15, 5), same.minutes)
        assertEquals(0, same.dayOffset)
        val exact = TimeMath.add(TimeMath.of(23, 0), 60)
        assertEquals(0, exact.minutes)
        assertEquals(1, exact.dayOffset)
    }

    @Test
    fun elapsedSameDayAndNextDay() {
        assertEquals(45, TimeMath.elapsed(TimeMath.of(14, 20), TimeMath.of(15, 5), endIsNextDay = false))
        assertEquals(35, TimeMath.elapsed(TimeMath.of(23, 40), TimeMath.of(0, 15), endIsNextDay = true))
        assertEquals(0, TimeMath.elapsed(600, 600, endIsNextDay = false))
        assertEquals(1440, TimeMath.elapsed(600, 600, endIsNextDay = true))
    }

    @Test(/* end before start without next-day flag is rejected */)
    fun elapsedRejectsSilentOvernight() {
        var threw = false
        try {
            TimeMath.elapsed(TimeMath.of(23, 40), TimeMath.of(0, 15), endIsNextDay = false)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun setters() {
        val t = TimeMath.of(15, 25)
        assertEquals(TimeMath.of(19, 25), TimeMath.withHour12(t, 7))
        assertEquals(TimeMath.of(12, 25), TimeMath.withHour12(t, 12))
        assertEquals(TimeMath.of(0, 25), TimeMath.withHour12(TimeMath.of(3, 25), 12))
        assertEquals(TimeMath.of(3, 25), TimeMath.withPm(t, false))
        assertEquals(t, TimeMath.withPm(t, true))
        assertEquals(TimeMath.of(15, 0), TimeMath.withMinute(t, 0))
    }
}
