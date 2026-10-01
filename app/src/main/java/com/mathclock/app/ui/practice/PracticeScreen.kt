package com.mathclock.app.ui.practice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathclock.app.data.local.TimeQuestionEntity
import com.mathclock.app.data.repository.LoadedSession
import com.mathclock.app.data.repository.toExercise
import com.mathclock.app.domain.generation.Exercise
import com.mathclock.app.domain.generation.ExerciseText
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.progress.SessionStatus
import com.mathclock.app.domain.progress.SessionType
import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.domain.time.TimeMath
import com.mathclock.app.ui.appViewModel
import com.mathclock.app.ui.clock.AnalogClock
import com.mathclock.app.ui.clock.ClockLegend
import com.mathclock.app.ui.clock.TimeAdjustControls
import com.mathclock.app.ui.common.AppTopBar
import com.mathclock.app.ui.common.ConfirmDialog
import com.mathclock.app.ui.results.SessionReview
import com.mathclock.app.ui.theme.ClassroomColors
import com.mathclock.app.ui.theme.LocalReducedMotion

@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val vm = appViewModel { c, h -> PracticeViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    val session = state.session
    val isFree = session?.type == SessionType.FREE
    val title = when {
        session == null -> "Practice"
        session.type == SessionType.DAILY -> "Today's Practice"
        else -> Topic.entries.firstOrNull { it.name == session.session.topicSelection }?.title ?: "Mixed Practice"
    }

    Scaffold(
        topBar = {
            AppTopBar(title, onBack) {
                if (isFree && session?.status == SessionStatus.IN_PROGRESS) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Session options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("End session") }, onClick = { menuOpen = false; confirmEnd = true })
                        DropdownMenuItem(text = { Text("Restart") }, onClick = { menuOpen = false; confirmRestart = true })
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing || session == null -> Text(
                    "This practice is no longer available.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                state.showResults -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    SessionReview(session = session, today = state.today)
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!session.isComplete && session.status == SessionStatus.IN_PROGRESS) {
                            Button(
                                onClick = vm::goToFirstUnanswered,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                            ) { Text("Continue unanswered questions") }
                        }
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) { Text("Back to the board") }
                    }
                }
                else -> QuestionContent(state, vm)
            }
        }
    }

    if (state.dayChanged) {
        ConfirmDialog(
            title = "A new day has started",
            text = "Your answers so far stay in the earlier set. Would you like to open today's practice?",
            confirmLabel = "Open today's practice",
            onConfirm = { vm.dismissDayChanged(); vm.openTodaysSet(onOpenSession) },
            onDismiss = vm::dismissDayChanged,
        )
    }
    if (confirmEnd) {
        ConfirmDialog(
            title = "End this practice?",
            text = "Answered questions are kept in history. Unanswered questions do not count.",
            confirmLabel = "End session",
            onConfirm = { confirmEnd = false; vm.endSession(onBack) },
            onDismiss = { confirmEnd = false },
        )
    }
    if (confirmRestart) {
        ConfirmDialog(
            title = "Restart with new questions?",
            text = "This session ends now (answered questions stay in history) and a new set of 10 questions starts.",
            confirmLabel = "Restart",
            onConfirm = { confirmRestart = false; vm.restart(onOpenSession) },
            onDismiss = { confirmRestart = false },
        )
    }
}

@Composable
private fun QuestionContent(state: PracticeUiState, vm: PracticeViewModel) {
    val session = state.session ?: return
    val q = state.question ?: return
    val reduced = LocalReducedMotion.current
    if (reduced) {
        QuestionBody(session, q, state, vm)
    } else {
        AnimatedContent(
            targetState = q.id,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "question",
        ) { id ->
            val shown = session.questions.firstOrNull { it.id == id } ?: q
            QuestionBody(session, shown, state, vm)
        }
    }
}

@Composable
private fun QuestionBody(session: LoadedSession, q: TimeQuestionEntity, state: PracticeUiState, vm: PracticeViewModel) {
    val ex = q.toExercise()
    val format = session.timeFormat
    val step = session.difficulty.precision
    val answered = q.submittedAnswer != null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val visual: @Composable (Modifier) -> Unit = { m -> QuestionVisual(ex, q, state, vm, format, step, m) }
        val panel: @Composable (Modifier) -> Unit = { m -> QuestionPanel(session, ex, q, state, vm, format, answered, m) }
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                visual(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuestionHeader(session, ex, state)
                    panel(Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuestionHeader(session, ex, state)
                visual(Modifier.fillMaxWidth())
                panel(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun QuestionHeader(session: LoadedSession, ex: Exercise, state: PracticeUiState) {
    val total = session.questions.size
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Question ${state.position + 1} of $total · ${ex.topic.title}",
            style = MaterialTheme.typography.titleMedium,
            color = ClassroomColors.TextMuted,
        )
        LinearProgressIndicator(
            progress = { session.answeredCount / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "${session.answeredCount} of $total answered" },
        )
    }
}

@Composable
private fun QuestionVisual(
    ex: Exercise,
    q: TimeQuestionEntity,
    state: PracticeUiState,
    vm: PracticeViewModel,
    format: TimeFormat,
    step: Int,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (ex.topic) {
            Topic.READ_CLOCK -> {
                AnalogClock(
                    minutes = ex.correctAnswer,
                    // The description must not reveal the answer: it describes hand positions only.
                    description = "Clock for the question. Use the hands to read the time.",
                    modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                )
                ClockLegend()
            }
            Topic.SET_CLOCK -> {
                val value = state.clockValue ?: q.startMinutes
                AnalogClock(
                    minutes = value,
                    description = "Clock you can set. Drag the hands or use the hour and minute controls.",
                    onMinutesChange = vm::setClock,
                    step = step,
                    modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                )
                ClockLegend()
                // Readable selected hour/minute controls instead of a live digital readout.
                TimeAdjustControls(
                    minutes = value,
                    format = format,
                    step = step,
                    onChange = vm::setClock,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
            Topic.TIME_AFTER -> {
                AnalogClock(
                    minutes = ex.startMinutes,
                    description = "Clock showing the start time ${TimeMath.format(ex.startMinutes, format)}.",
                    modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth(),
                )
                TimeCards(
                    "Now" to TimeMath.format(ex.startMinutes, format),
                    "Add" to ExerciseText.minutesLabel(ex.durationMinutes ?: 0),
                )
            }
            Topic.MINUTES_BETWEEN -> {
                val end = ex.endMinutes ?: 0
                TimeCards(
                    "From" to TimeMath.format(ex.startMinutes, format),
                    "To" to TimeMath.format(end, format) + if (ex.crossesMidnight) "\nthe next day" else "",
                )
            }
        }
    }
}

@Composable
private fun TimeCards(vararg cards: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.forEach { (label, value) ->
            Surface(
                color = ClassroomColors.ClockFace,
                border = BorderStroke(2.dp, ClassroomColors.ClockFrame),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = ClassroomColors.TextMuted)
                    Text(value, style = MaterialTheme.typography.headlineSmall, color = ClassroomColors.Navy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QuestionPanel(
    session: LoadedSession,
    ex: Exercise,
    q: TimeQuestionEntity,
    state: PracticeUiState,
    vm: PracticeViewModel,
    format: TimeFormat,
    answered: Boolean,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            ExerciseText.prompt(ex, format),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        ExerciseText.context(ex)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = ClassroomColors.TextMuted) }

        if (ex.topic != Topic.SET_CLOCK) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ex.options.forEach { option ->
                    OptionCard(
                        label = ExerciseText.optionLabel(ex, option, format),
                        selected = if (answered) q.submittedAnswer == option else state.selectedOption == option,
                        answered = answered,
                        isCorrectOption = option == ex.correctAnswer,
                        onClick = { vm.select(option) },
                    )
                }
            }
        }

        if (!answered) {
            if (state.showHint) {
                Surface(color = ClassroomColors.NotebookPaper, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ClassroomColors.NotebookLine)) {
                    Text(
                        "Hint: " + ExerciseText.hint(ex, format),
                        modifier = Modifier
                            .padding(12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!state.showHint) {
                    TextButton(onClick = vm::showHint, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hint") }
                }
                Button(
                    onClick = vm::check,
                    enabled = !state.busy && (ex.topic == Topic.SET_CLOCK || state.selectedOption != null),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text("Check") }
            }
        } else {
            Feedback(ex, q, format)
            if (ex.topic == Topic.SET_CLOCK) {
                Text(
                    "You can keep moving the hands to explore. Your first answer has been saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ClassroomColors.TextMuted,
                )
            }
            Button(
                onClick = vm::next,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(if (state.isLast) "See results" else "Next") }
        }
    }
}

@Composable
private fun OptionCard(
    label: String,
    selected: Boolean,
    answered: Boolean,
    isCorrectOption: Boolean,
    onClick: () -> Unit,
) {
    val (border, marker) = when {
        answered && isCorrectOption -> ClassroomColors.Correct to "✓ Correct answer"
        answered && selected -> ClassroomColors.Incorrect to "✗ Your answer"
        selected -> ClassroomColors.Teal to "● Selected"
        else -> ClassroomColors.ClockFrame to null
    }
    Surface(
        color = if (selected && !answered) Color(0xFFDDF0EF) else ClassroomColors.ClockFace,
        border = BorderStroke(if (selected || (answered && isCorrectOption)) 3.dp else 1.5.dp, border),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = !answered, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (marker != null) {
                Text(marker, style = MaterialTheme.typography.labelLarge, color = border)
            }
        }
    }
}

@Composable
private fun Feedback(ex: Exercise, q: TimeQuestionEntity, format: TimeFormat) {
    val correct = q.isCorrect == true
    Surface(
        color = if (correct) Color(0xFFE3F3E8) else Color(0xFFFBE6E1),
        border = BorderStroke(2.dp, if (correct) ClassroomColors.Correct else ClassroomColors.Incorrect),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (correct) "✓ Correct!" else "✗ Not quite",
                style = MaterialTheme.typography.titleLarge,
                color = if (correct) ClassroomColors.Correct else ClassroomColors.Incorrect,
            )
            if (!correct) {
                Text("Your answer: ${ExerciseText.answerLabel(ex, q.submittedAnswer ?: 0, format)}")
                Text("Correct answer: ${ExerciseText.answerLabel(ex, ex.correctAnswer, format)}", fontWeight = FontWeight.SemiBold)
            }
            Text(ExerciseText.explanation(ex, format))
        }
    }
}
