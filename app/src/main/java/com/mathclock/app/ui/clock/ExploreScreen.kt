package com.mathclock.app.ui.clock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mathclock.app.AppContainer
import com.mathclock.app.data.local.AppSettings
import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.domain.time.TimeMath
import com.mathclock.app.ui.appViewModel
import com.mathclock.app.ui.common.AppTopBar
import com.mathclock.app.ui.common.ChoiceRow
import com.mathclock.app.ui.theme.ClassroomColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(val settings: AppSettings = AppSettings(), val minutes: Int = AppSettings.DEFAULT_EXPLORED)

class ExploreViewModel(container: AppContainer) : ViewModel() {
    private val prefs = container.preferences
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()
    private var persistJob: Job? = null
    private var pending = false

    init {
        viewModelScope.launch {
            prefs.settings.collect { s ->
                _state.update { it.copy(settings = s, minutes = if (pending) it.minutes else s.exploredMinutes) }
            }
        }
    }

    fun setMinutes(m: Int) {
        pending = true
        _state.update { it.copy(minutes = TimeMath.normalize(m)) }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(250)
            prefs.setExploredMinutes(m)
            pending = false
        }
    }

    fun setFormat(f: TimeFormat) = viewModelScope.launch { prefs.setTimeFormat(f) }
    fun setMinuteLabels(v: Boolean) = viewModelScope.launch { prefs.setShowMinuteLabels(v) }
}

@Composable
fun ExploreScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> ExploreViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val format = state.settings.timeFormat
    val m = state.minutes

    Scaffold(
        topBar = { AppTopBar("Explore the Clock", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClockWithLegend(
                minutes = m,
                description = "Learning clock showing ${TimeMath.format12(m)}. Drag a hand, or use the hour and minute controls below.",
                onMinutesChange = vm::setMinutes,
                step = 1,
                showMinuteLabels = state.settings.showMinuteLabels,
                modifier = Modifier.widthIn(max = 440.dp),
            )
            Text(
                TimeMath.format(m, format),
                style = MaterialTheme.typography.displaySmall,
                color = ClassroomColors.Navy,
                modifier = Modifier.semantics { contentDescription = "Digital time ${TimeMath.format(m, format)}" },
            )
            Text(TimeMath.dayPartContext(m), style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.widthIn(max = 520.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeAdjustControls(minutes = m, format = format, step = 1, onChange = vm::setMinutes)
                    ChoiceRow(
                        options = listOf(TimeFormat.H12, TimeFormat.H24),
                        selected = format,
                        label = { it.label },
                        onSelect = { vm.setFormat(it) },
                        groupLabel = "Clock format",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Show minute labels", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.settings.showMinuteLabels,
                            onCheckedChange = { vm.setMinuteLabels(it) },
                            modifier = Modifier.semantics { contentDescription = "Show minute labels" },
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ClassroomColors.NotebookPaper),
                        border = BorderStroke(1.dp, ClassroomColors.NotebookLine),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Did you know?", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "The hands go around the dial twice every day, so an analog clock looks the same " +
                                    "in the morning and in the evening. ${TimeMath.format12(m)} and " +
                                    "${TimeMath.format12(TimeMath.normalize(m + 720))} look exactly alike on the dial.",
                            )
                            Text(
                                "24-hour time counts the hours from 00 to 23, so ${TimeMath.format24(m)} means " +
                                    "${TimeMath.format12(m)}. AM is from midnight to noon; PM is from noon to midnight.",
                            )
                            Text(
                                "The short hand moves a little every minute: at ${TimeMath.formatDial(m)} it is " +
                                    if (TimeMath.minute(m) == 0) "exactly on ${TimeMath.hour12(m)}." else
                                        "between ${TimeMath.hour12(m)} and ${TimeMath.hour12(m) % 12 + 1}.",
                            )
                        }
                    }
                }
            }
        }
    }
}
