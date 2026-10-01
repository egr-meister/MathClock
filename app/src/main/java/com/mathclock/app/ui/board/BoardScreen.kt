package com.mathclock.app.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.domain.time.TimeMath
import com.mathclock.app.ui.appViewModel
import com.mathclock.app.ui.clock.ClockWithLegend
import com.mathclock.app.ui.common.ChoiceRow
import com.mathclock.app.ui.common.ConfirmDialog
import com.mathclock.app.ui.theme.ClassroomColors

@Composable
fun BoardScreen(
    onExplore: () -> Unit,
    onPracticeTopic: (Topic?) -> Unit,
    onOpenSession: (Long) -> Unit,
    onCalculator: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = appViewModel { c, _ -> BoardViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val wide = maxWidth >= 700.dp
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "MathClock",
                        style = MaterialTheme.typography.titleLarge,
                        color = ClassroomColors.Navy,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings for grown-ups")
                    }
                }
                val clockPanel: @Composable (Modifier) -> Unit = { m ->
                    LearningClockPanel(
                        minutes = state.exploredMinutes,
                        format = state.settings.timeFormat,
                        showMinuteLabels = state.settings.showMinuteLabels,
                        onChange = vm::setExplored,
                        onFormat = vm::setTimeFormat,
                        onExplore = onExplore,
                        modifier = m,
                    )
                }
                val practicePanel: @Composable (Modifier) -> Unit = { m ->
                    PracticeBoard(
                        state = state,
                        onTopic = onPracticeTopic,
                        onDaily = { vm.openDaily(onOpenSession) },
                        onHistory = onHistory,
                        onResume = { state.activeFree?.let { onOpenSession(it.id) } },
                        onEnd = { confirmEnd = true },
                        modifier = m,
                    )
                }
                if (wide) {
                    Row(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        clockPanel(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        )
                        practicePanel(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        clockPanel(Modifier.fillMaxWidth())
                        practicePanel(Modifier.fillMaxWidth())
                        Spacer(Modifier.size(4.dp))
                    }
                }
                CalculatorStrip(onCalculator, Modifier.padding(16.dp))
            }
        }
    }

    if (confirmEnd) {
        ConfirmDialog(
            title = "End this practice?",
            text = "Answered questions are kept in history. Unanswered questions do not count.",
            confirmLabel = "End session",
            onConfirm = { confirmEnd = false; vm.endActive() },
            onDismiss = { confirmEnd = false },
        )
    }
}

@Composable
private fun LearningClockPanel(
    minutes: Int,
    format: TimeFormat,
    showMinuteLabels: Boolean,
    onChange: (Int) -> Unit,
    onFormat: (TimeFormat) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color0),
        border = BorderStroke(3.dp, ClassroomColors.ClockFrame),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ClockWithLegend(
                minutes = minutes,
                description = "Learning clock showing ${TimeMath.format12(minutes)}. Drag a hand to change the time.",
                onMinutesChange = onChange,
                step = 1,
                showMinuteLabels = showMinuteLabels,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Text(
                TimeMath.format(minutes, format),
                style = MaterialTheme.typography.displaySmall,
                color = ClassroomColors.Navy,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(ClassroomColors.IvoryDeep, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Digital time ${TimeMath.format12(minutes)}" },
            )
            ChoiceRow(
                options = listOf(TimeFormat.H12, TimeFormat.H24),
                selected = format,
                label = { it.label },
                onSelect = onFormat,
                groupLabel = "Clock format",
            )
            Button(
                onClick = onExplore,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text("Explore the clock") }
        }
    }
}

private val Color0 = ClassroomColors.ClockFace

@Composable
private fun PracticeBoard(
    state: BoardUiState,
    onTopic: (Topic?) -> Unit,
    onDaily: () -> Unit,
    onHistory: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Surface(
            color = ClassroomColors.NotebookPaper,
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp),
            border = BorderStroke(1.dp, ClassroomColors.NotebookLine),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val margin = 40.dp.toPx()
                    drawLine(ClassroomColors.MarginRed, Offset(margin, 0f), Offset(margin, size.height), 2.dp.toPx())
                },
        ) {
            Column(Modifier.padding(start = 52.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
                Text(
                    "Time Practice",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ClassroomColors.Navy,
                    modifier = Modifier
                        .padding(end = 72.dp)
                        .semantics { heading() },
                )
                Spacer(Modifier.size(8.dp))
                state.activeFree?.let { a ->
                    Surface(color = ClassroomColors.NotebookYellow, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Unfinished practice: ${a.topicLabel}", fontWeight = FontWeight.SemiBold)
                            Text("${a.answered} of ${a.total} answered", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) { Text("Resume") }
                                TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp)) { Text("End session") }
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                }
                NotebookRow("Read the Clock", "Look at the hands and pick the time") { onTopic(Topic.READ_CLOCK) }
                NotebookRow("Set the Clock", "Move the hands to a time") { onTopic(Topic.SET_CLOCK) }
                NotebookRow("Time After", "What time will it be later?") { onTopic(Topic.TIME_AFTER) }
                NotebookRow("Minutes Between", "How long from one time to another?") { onTopic(Topic.MINUTES_BETWEEN) }
                val dailyStatus = when (val a = state.dailyAnswered) {
                    null -> "10 new questions for today"
                    state.dailyTotal -> "Done! $a of ${state.dailyTotal} answered"
                    else -> "$a of ${state.dailyTotal} answered"
                }
                NotebookRow("Today's Practice", dailyStatus, highlighted = true, onClick = onDaily)
                Spacer(Modifier.size(4.dp))
                OutlinedButton(
                    onClick = { onTopic(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("Mixed practice & options") }
            }
        }
        ProgressBookmark(
            todayAnswered = state.todayAnswered,
            onClick = onHistory,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp),
        )
    }
}

@Composable
private fun NotebookRow(title: String, subtitle: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClickLabel = "Open $title", role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) ClassroomColors.Teal else ClassroomColors.Navy,
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ClassroomColors.TextMuted)
    }
    HorizontalDivider(color = ClassroomColors.NotebookLine)
}

private val BookmarkShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width / 2f, size.height - size.width * 0.35f)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun ProgressBookmark(todayAnswered: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = ClassroomColors.Bookmark,
        contentColor = androidx.compose.ui.graphics.Color.White,
        shape = BookmarkShape,
        modifier = modifier
            .width(64.dp)
            .heightIn(min = 96.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Practice history. $todayAnswered answered today."
                role = Role.Button
            },
    ) {
        Column(
            Modifier.padding(top = 10.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$todayAnswered", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("today", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CalculatorStrip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = ClassroomColors.Navy,
        contentColor = androidx.compose.ui.graphics.Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = "Open calculator", role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Calculator", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clearAndSetSemantics { },
            ) {
                listOf("7", "8", "9", "+", "=").forEach { k ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(ClassroomColors.ClockFrameDark, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(k, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}
