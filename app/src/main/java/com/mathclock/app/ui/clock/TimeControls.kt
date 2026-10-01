package com.mathclock.app.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathclock.app.domain.time.ClockGeometry
import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.domain.time.TimeMath
import com.mathclock.app.ui.common.ChoiceRow
import com.mathclock.app.ui.common.Stepper

/**
 * Accessible alternative to dragging: explicit hour, minute and (12-hour mode) AM/PM controls.
 * The hour control never changes AM/PM by itself; only the AM/PM control does.
 */
@Composable
fun TimeAdjustControls(
    minutes: Int,
    format: TimeFormat,
    step: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (format) {
            TimeFormat.H12 -> {
                val h = TimeMath.hour12(minutes)
                Stepper(
                    label = "Hour",
                    valueText = h.toString(),
                    spokenValue = h.toString(),
                    onDecrease = { onChange(TimeMath.withHour12(minutes, (h + 10) % 12 + 1)) },
                    onIncrease = { onChange(TimeMath.withHour12(minutes, h % 12 + 1)) },
                )
            }
            TimeFormat.H24 -> {
                val h = TimeMath.hour24(minutes)
                Stepper(
                    label = "Hour",
                    valueText = h.toString().padStart(2, '0'),
                    spokenValue = h.toString(),
                    onDecrease = { onChange(TimeMath.withHour24(minutes, Math.floorMod(h - 1, 24))) },
                    onIncrease = { onChange(TimeMath.withHour24(minutes, (h + 1) % 24)) },
                )
            }
        }
        val m = ClockGeometry.snapMinute(TimeMath.minute(minutes), step)
        Stepper(
            label = "Minute",
            valueText = m.toString().padStart(2, '0'),
            spokenValue = m.toString(),
            onDecrease = { onChange(TimeMath.withMinute(minutes, Math.floorMod(m - step, 60))) },
            onIncrease = { onChange(TimeMath.withMinute(minutes, Math.floorMod(m + step, 60))) },
        )
        if (format == TimeFormat.H12) {
            ChoiceRow(
                options = listOf(false, true),
                selected = TimeMath.isPm(minutes),
                label = { pm -> if (pm) "PM (afternoon / evening)" else "AM (night / morning)" },
                onSelect = { pm -> onChange(TimeMath.withPm(minutes, pm)) },
                groupLabel = "AM or PM",
            )
        }
    }
}
