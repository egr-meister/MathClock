package com.mathclock.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mathclock.app.AppContainer
import com.mathclock.app.data.local.AppSettings
import com.mathclock.app.domain.generation.Difficulty
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.ui.appViewModel
import com.mathclock.app.ui.board.ActiveSessionInfo
import com.mathclock.app.ui.common.AppTopBar
import com.mathclock.app.ui.common.ChoiceRow
import com.mathclock.app.ui.common.ConfirmDialog
import com.mathclock.app.ui.common.SectionTitle
import com.mathclock.app.ui.theme.ClassroomColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val active: ActiveSessionInfo? = null,
)

class PracticeSetupViewModel(container: AppContainer) : ViewModel() {
    private val prefs = container.preferences
    private val practice = container.practice
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val settings = prefs.current()
            val active = practice.read { activeFreeSession() }
            _state.update {
                it.copy(
                    loaded = true,
                    settings = settings,
                    active = active?.let { a ->
                        ActiveSessionInfo(
                            a.session.id,
                            Topic.entries.firstOrNull { t -> t.name == a.session.topicSelection }?.title ?: "Mixed topics",
                            a.answeredCount,
                            a.questions.size,
                        )
                    },
                )
            }
        }
    }

    fun start(topic: Topic?, difficulty: Difficulty, format: TimeFormat, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            if (practice.read { activeFreeSession() } != null) {
                refresh(); return@launch
            }
            prefs.setPracticeTopic(topic?.name ?: "MIXED")
            val id = practice.write { startFreeSession(topic, difficulty, format) }
            onStarted(id)
        }
    }

    fun end() {
        val id = _state.value.active?.id ?: return
        viewModelScope.launch { practice.write { endSession(id) }; refresh() }
    }

    fun restart(onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val id = practice.write { restartFreeSession() }
            if (id != null) onStarted(id) else refresh()
        }
    }
}

private const val MIXED = "MIXED"

@Composable
fun PracticeSetupScreen(
    initialTopic: String?,
    onBack: () -> Unit,
    onStarted: (Long) -> Unit,
) {
    val vm = appViewModel { c, _ -> PracticeSetupViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    var topicName by rememberSaveable { mutableStateOf<String?>(null) }
    var difficultyName by rememberSaveable { mutableStateOf<String?>(null) }
    var formatName by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.loaded) {
        if (state.loaded) {
            if (topicName == null) topicName = initialTopic ?: state.settings.practiceTopic
            if (difficultyName == null) difficultyName = state.settings.defaultDifficulty.name
            if (formatName == null) formatName = state.settings.timeFormat.name
        }
    }
    val topic = Topic.entries.firstOrNull { it.name == topicName }
    val difficulty = Difficulty.entries.firstOrNull { it.name == difficultyName } ?: state.settings.defaultDifficulty
    val format = TimeFormat.entries.firstOrNull { it.name == formatName } ?: state.settings.timeFormat

    Scaffold(topBar = { AppTopBar("Practice", onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.active?.let { a ->
                    Surface(color = ClassroomColors.NotebookYellow, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("You have an unfinished practice", style = MaterialTheme.typography.titleMedium)
                            Text("${a.topicLabel}: ${a.answered} of ${a.total} answered")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onStarted(a.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Resume") }
                                TextButton(onClick = { confirmEnd = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("End session") }
                                TextButton(onClick = { confirmRestart = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Restart") }
                            }
                            Text(
                                "Finish or end it to start a new one. Today's Practice is separate and not affected.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                SectionTitle("Topic")
                Column(Modifier.selectableGroup()) {
                    val choices = listOf<Pair<String, String>>(MIXED to "Mixed topics") + Topic.entries.map { it.name to it.title }
                    choices.forEach { (name, label) ->
                        val selected = (topicName ?: MIXED) == name
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { topicName = name }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                SectionTitle("Difficulty")
                ChoiceRow(
                    options = Difficulty.entries.toList(),
                    selected = difficulty,
                    label = { it.label },
                    onSelect = { difficultyName = it.name },
                    groupLabel = "Difficulty",
                )
                Text(difficulty.precisionDescription + when (difficulty) {
                    Difficulty.EASY -> ". Time steps of 30 or 60 minutes."
                    Difficulty.MEDIUM -> ". Time steps up to 2–3 hours."
                    Difficulty.HARD -> ". May cross midnight (always labeled)."
                }, style = MaterialTheme.typography.bodyMedium, color = ClassroomColors.TextMuted)
                SectionTitle("Time format")
                ChoiceRow(
                    options = listOf(TimeFormat.H24, TimeFormat.H12),
                    selected = format,
                    label = { it.label },
                    onSelect = { formatName = it.name },
                    groupLabel = "Time format",
                )
                Text("10 questions · no timer", fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { vm.start(topic, difficulty, format, onStarted) },
                    enabled = state.loaded && state.active == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) { Text("Start practice") }
            }
        }
    }

    if (confirmRestart) {
        ConfirmDialog(
            title = "Restart with new questions?",
            text = "The unfinished session ends (answered questions stay in history) and a new one starts with the same settings.",
            confirmLabel = "Restart",
            onConfirm = { confirmRestart = false; vm.restart(onStarted) },
            onDismiss = { confirmRestart = false },
        )
    }
    if (confirmEnd) {
        ConfirmDialog(
            title = "End this practice?",
            text = "Answered questions are kept in history. Unanswered questions do not count.",
            confirmLabel = "End session",
            onConfirm = { confirmEnd = false; vm.end() },
            onDismiss = { confirmEnd = false },
        )
    }
}
