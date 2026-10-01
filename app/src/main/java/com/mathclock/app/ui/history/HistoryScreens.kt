package com.mathclock.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mathclock.app.AppContainer
import com.mathclock.app.data.local.SessionSummaryRow
import com.mathclock.app.data.repository.LoadedSession
import com.mathclock.app.data.repository.Totals
import com.mathclock.app.domain.generation.Difficulty
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.progress.Scoring
import com.mathclock.app.domain.progress.SessionType
import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.ui.appViewModel
import com.mathclock.app.ui.common.AppTopBar
import com.mathclock.app.ui.results.SessionReview
import com.mathclock.app.ui.results.formatDate
import com.mathclock.app.ui.results.formatDateTime
import com.mathclock.app.ui.results.statusLabel
import com.mathclock.app.ui.theme.ClassroomColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HistoryUiState(
    val loading: Boolean = true,
    val totals: Totals? = null,
    val sessions: List<SessionSummaryRow> = emptyList(),
    val today: LocalDate? = null,
    val todayAnswered: Int = 0,
)

class HistoryViewModel(container: AppContainer) : ViewModel() {
    private val practice = container.practice
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val (totals, sessions, today, count) = practice.read {
                val t = today()
                Quad(totals(), history(), t, answeredOn(t))
            }
            _state.update { HistoryUiState(false, totals, sessions, today, count) }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> HistoryViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    Scaffold(topBar = { AppTopBar("Practice History", onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TotalsCard(state.totals, state.todayAnswered) }
            if (state.sessions.isEmpty()) {
                item {
                    Text(
                        "No practice yet. Try Today's Practice or pick a topic on the board.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                item {
                    Text("Sessions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                items(state.sessions, key = { it.session.id }) { row -> SessionRow(row, state.today) { onOpen(row.session.id) } }
            }
        }
    }
}

@Composable
private fun TotalsCard(totals: Totals?, todayAnswered: Int) {
    Surface(
        color = ClassroomColors.NotebookPaper,
        border = BorderStroke(1.dp, ClassroomColors.NotebookLine),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("All-time", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (totals == null || totals.answered == 0) {
                Text("No answers yet")
            } else {
                Text("Accuracy: ${Scoring.accuracyLabel(totals.answered, totals.correct)}", style = MaterialTheme.typography.titleMedium)
                Text("${totals.correct} correct of ${totals.answered} answered")
                Text("Completed sessions: ${totals.completedSessions}")
                Topic.entries.forEach { t ->
                    val s = totals.perTopic.getValue(t)
                    Row(Modifier.fillMaxWidth()) {
                        Text(t.title, modifier = Modifier.weight(1f))
                        Text(
                            if (s.answered == 0) "No answers yet" else "${s.correct}/${s.answered} · ${Scoring.accuracyLabel(s.answered, s.correct)}",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text("Answered today: $todayAnswered", style = MaterialTheme.typography.bodyMedium, color = ClassroomColors.TextMuted)
            Text(
                "Totals are kept even when older session details are removed (latest 100 sessions and 90 daily sets are kept).",
                style = MaterialTheme.typography.bodyMedium,
                color = ClassroomColors.TextMuted,
            )
        }
    }
}

@Composable
private fun SessionRow(row: SessionSummaryRow, today: LocalDate?, onClick: () -> Unit) {
    val s = row.session
    val title = if (s.type == SessionType.DAILY.name) {
        "Daily · " + (s.dailyDate?.let { formatDate(LocalDate.parse(it)) } ?: "")
    } else {
        Topic.entries.firstOrNull { it.name == s.topicSelection }?.title ?: "Mixed practice"
    }
    val difficulty = Difficulty.entries.firstOrNull { it.name == s.difficulty }?.label ?: s.difficulty
    val format = TimeFormat.entries.firstOrNull { it.name == s.timeFormat }?.label ?: s.timeFormat
    Surface(
        color = ClassroomColors.ClockFace,
        border = BorderStroke(1.dp, ClassroomColors.ClockFrame),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Open details", onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Scoring.accuracyLabel(row.answered, row.correct), fontWeight = FontWeight.Bold, color = ClassroomColors.Navy)
            }
            Text("${row.answered} of ${row.total} answered · ${row.correct} correct · ${statusLabel(s, today)}")
            Text(
                "${formatDateTime(s.startedAt)} · $difficulty · $format",
                style = MaterialTheme.typography.bodyMedium,
                color = ClassroomColors.TextMuted,
            )
        }
    }
}

class HistoryDetailViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val practice = container.practice
    private val id: Long = checkNotNull(handle.get<Long>("sessionId"))
    private val _state = MutableStateFlow<Pair<LoadedSession?, LocalDate?>?>(null)
    val state: StateFlow<Pair<LoadedSession?, LocalDate?>?> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = practice.read { load(id) to today() } }
    }
}

@Composable
fun HistoryDetailScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, h -> HistoryDetailViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Session Details", onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        val value = state
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                value == null -> CircularProgressIndicator(Modifier.padding(32.dp))
                value.first == null -> Text("This session is no longer available.", Modifier.padding(24.dp))
                else -> Column(
                    Modifier
                        .widthIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    SessionReview(session = value.first!!, today = value.second)
                }
            }
        }
    }
}
