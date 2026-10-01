package com.mathclock.app.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mathclock.app.domain.time.ClockGeometry
import com.mathclock.app.domain.time.ClockHand
import com.mathclock.app.ui.theme.ClassroomColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Precisely drawn analog learning clock.
 *
 * When [onMinutesChange] is non-null, both hands can be dragged. The whole dial is the (invisible)
 * touch target; the nearest hand is selected when the drag starts and stays selected for the gesture.
 */
@Composable
fun AnalogClock(
    minutes: Int,
    description: String,
    modifier: Modifier = Modifier,
    onMinutesChange: ((Int) -> Unit)? = null,
    step: Int = 1,
    showMinuteLabels: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    val currentMinutes by rememberUpdatedState(minutes)
    val currentOnChange by rememberUpdatedState(onMinutesChange)
    val currentStep by rememberUpdatedState(step)

    val gestureModifier = if (onMinutesChange != null) {
        Modifier.pointerInput(Unit) {
            var hand = ClockHand.MINUTE
            var working = 0
            detectDragGestures(
                onDragStart = { offset ->
                    working = currentMinutes
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = min(cx, cy)
                    hand = ClockGeometry.selectHand(working, offset.x, offset.y, cx, cy, r)
                },
                onDrag = { change, _ ->
                    change.consume()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val angle = ClockGeometry.pointerAngle(change.position.x, change.position.y, cx, cy)
                    val next = when (hand) {
                        ClockHand.MINUTE -> ClockGeometry.dragMinute(working, angle, currentStep)
                        ClockHand.HOUR -> ClockGeometry.dragHour(working, angle)
                    }
                    if (next != working) {
                        working = next
                        currentOnChange?.invoke(next)
                    }
                },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .then(gestureModifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawClock(minutes, showMinuteLabels, textMeasurer)
        }
    }
}

private fun DrawScope.pointOnDial(angleDeg: Float, radius: Float): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset(center.x + (radius * sin(rad)).toFloat(), center.y - (radius * cos(rad)).toFloat())
}

private fun DrawScope.drawClock(minutes: Int, showMinuteLabels: Boolean, measurer: TextMeasurer) {
    val r = size.minDimension / 2f
    // Frame and face
    drawCircle(ClassroomColors.ClockFrame, radius = r)
    drawCircle(ClassroomColors.ClockFrameDark, radius = r - r * 0.015f, style = Stroke(width = r * 0.03f))
    drawCircle(ClassroomColors.ClockFace, radius = r * 0.86f)

    // Minute and hour ticks
    for (i in 0 until 60) {
        val major = i % 5 == 0
        val outer = pointOnDial(i * 6f, r * 0.84f)
        val inner = pointOnDial(i * 6f, if (major) r * 0.75f else r * 0.80f)
        drawLine(
            color = if (major) ClassroomColors.Navy else ClassroomColors.TextMuted,
            start = inner,
            end = outer,
            strokeWidth = if (major) r * 0.022f else r * 0.009f,
            cap = StrokeCap.Round,
        )
    }

    // Hour numbers
    val numberStyle = TextStyle(
        color = ClassroomColors.Navy,
        fontSize = (r * 0.15f).toSp(),
        fontWeight = FontWeight.Bold,
    )
    for (n in 1..12) {
        val layout = measurer.measure(n.toString(), numberStyle)
        val p = pointOnDial(n * 30f, r * 0.62f)
        drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
    }

    // Optional minute labels in the frame ring
    if (showMinuteLabels) {
        val minuteStyle = TextStyle(
            color = ClassroomColors.Navy,
            fontSize = (r * 0.068f).toSp(),
            fontWeight = FontWeight.SemiBold,
        )
        for (n in 0 until 12) {
            val label = if (n == 0) "00" else (n * 5).toString().padStart(2, '0')
            val layout = measurer.measure(label, minuteStyle)
            val p = pointOnDial(n * 30f, r * 0.93f)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
    }

    // Hour hand: short, wide, blunt (navy)
    val hourAngle = ClockGeometry.hourAngle(minutes)
    rotate(degrees = hourAngle, pivot = center) {
        val w = r * 0.085f
        val len = r * 0.44f
        val path = Path().apply {
            moveTo(center.x - w / 2f, center.y + r * 0.08f)
            lineTo(center.x - w / 2f, center.y - len + w / 2f)
            quadraticTo(center.x, center.y - len - w / 3f, center.x + w / 2f, center.y - len + w / 2f)
            lineTo(center.x + w / 2f, center.y + r * 0.08f)
            close()
        }
        drawPath(path, ClassroomColors.Navy)
    }

    // Minute hand: long, thin, arrow tip (teal)
    val minuteAngle = ClockGeometry.minuteAngle(minutes)
    rotate(degrees = minuteAngle, pivot = center) {
        val len = r * 0.74f
        drawLine(
            color = ClassroomColors.Teal,
            start = Offset(center.x, center.y + r * 0.12f),
            end = Offset(center.x, center.y - len + r * 0.08f),
            strokeWidth = r * 0.035f,
            cap = StrokeCap.Round,
        )
        val tip = Path().apply {
            moveTo(center.x, center.y - len)
            lineTo(center.x - r * 0.05f, center.y - len + r * 0.12f)
            lineTo(center.x + r * 0.05f, center.y - len + r * 0.12f)
            close()
        }
        drawPath(tip, ClassroomColors.Teal)
    }

    drawCircle(ClassroomColors.Navy, radius = r * 0.06f)
    drawCircle(ClassroomColors.Teal, radius = r * 0.025f)
}

/** Text legend so hands are distinguished by shape and words, not by color alone. */
@Composable
fun ClockLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Short wide hand shows hours. Long thin hand with an arrow shows minutes." },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
                drawLine(
                    ClassroomColors.Navy,
                    Offset(0f, size.height / 2),
                    Offset(size.width * 0.8f, size.height / 2),
                    strokeWidth = size.height * 0.7f,
                    cap = StrokeCap.Round,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("Short wide hand = hours", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 28.dp, height = 12.dp)) {
                val y = size.height / 2
                drawLine(ClassroomColors.Teal, Offset(0f, y), Offset(size.width * 0.75f, y), strokeWidth = size.height * 0.25f)
                val p = Path().apply {
                    moveTo(size.width, y)
                    lineTo(size.width * 0.7f, 0f)
                    lineTo(size.width * 0.7f, size.height)
                    close()
                }
                drawPath(p, ClassroomColors.Teal)
            }
            Spacer(Modifier.width(8.dp))
            Text("Long arrow hand = minutes", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ClockWithLegend(
    minutes: Int,
    description: String,
    modifier: Modifier = Modifier,
    onMinutesChange: ((Int) -> Unit)? = null,
    step: Int = 1,
    showMinuteLabels: Boolean = false,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnalogClock(
            minutes = minutes,
            description = description,
            onMinutesChange = onMinutesChange,
            step = step,
            showMinuteLabels = showMinuteLabels,
            modifier = Modifier.fillMaxWidth(),
        )
        ClockLegend(Modifier.fillMaxWidth())
    }
}
