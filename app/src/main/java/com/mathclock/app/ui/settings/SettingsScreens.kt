package com.mathclock.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mathclock.app.AppContainer
import com.mathclock.app.data.local.AppSettings
import com.mathclock.app.domain.generation.Difficulty
import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.ui.appViewModel
import com.mathclock.app.ui.common.AdultGateDialog
import com.mathclock.app.ui.common.AppTopBar
import com.mathclock.app.ui.common.ChoiceRow
import com.mathclock.app.ui.common.SectionTitle
import com.mathclock.app.ui.theme.ClassroomColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val prefs = container.preferences
    val settings: StateFlow<AppSettings> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setDifficulty(d: Difficulty) = viewModelScope.launch { prefs.setDifficulty(d) }
    fun setFormat(f: TimeFormat) = viewModelScope.launch { prefs.setTimeFormat(f) }
    fun setMinuteLabels(v: Boolean) = viewModelScope.launch { prefs.setShowMinuteLabels(v) }
    fun setReducedMotion(v: Boolean) = viewModelScope.launch { prefs.setReducedMotion(v) }

    suspend fun clearCalculations() {
        container.calculator.clear()
        prefs.clearCalculatorDraft()
    }

    suspend fun clearProgress() = container.practice.write { clearProgress() }

    suspend fun clearAll() = container.resetter.clearAll()
}

private enum class PendingAction(val title: String, val text: String, val confirm: String, val done: String) {
    CALC("Clear calculation history?", "All saved calculations will be removed.", "Clear history", "Calculation history cleared"),
    PROGRESS(
        "Clear learning progress?",
        "All practice sessions, daily sets and totals will be removed. This cannot be undone.",
        "Clear progress",
        "Learning progress cleared",
    ),
    ALL(
        "Clear all local data?",
        "Calculations, practice history, totals and preferences will be removed and defaults restored. This cannot be undone.",
        "Clear everything",
        "All data cleared",
    ),
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onPrivacy: () -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val s by vm.settings.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { AppTopBar("Settings", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Practice")
                Text("Default difficulty")
                ChoiceRow(Difficulty.entries.toList(), s.defaultDifficulty, { it.label }, { vm.setDifficulty(it) }, groupLabel = "Default difficulty")
                Text("Time display")
                ChoiceRow(listOf(TimeFormat.H24, TimeFormat.H12), s.timeFormat, { it.label }, { vm.setFormat(it) }, groupLabel = "Time display")
                Text(
                    "Changes apply to new sessions and the next daily set. Sets already started keep their settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ClassroomColors.TextMuted,
                )
                SwitchRow("Show minute labels in Explore", s.showMinuteLabels) { vm.setMinuteLabels(it) }
                SwitchRow("Reduce decorative animation", s.reducedMotion) { vm.setReducedMotion(it) }

                HorizontalDivider()
                SectionTitle("Data (grown-ups)")
                OutlinedButton(onClick = { pending = PendingAction.CALC }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Clear calculation history")
                }
                OutlinedButton(onClick = { pending = PendingAction.PROGRESS }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Clear learning progress")
                }
                OutlinedButton(onClick = { pending = PendingAction.ALL }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Clear all local data")
                }

                HorizontalDivider()
                SectionTitle("About")
                OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Privacy information")
                }
                Text(
                    "MathClock works fully offline. No account, name, age or email is needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ClassroomColors.TextMuted,
                )
            }
        }
    }

    pending?.let { action ->
        AdultGateDialog(
            title = action.title,
            explanation = action.text,
            confirmLabel = action.confirm,
            onConfirm = {
                pending = null
                scope.launch {
                    when (action) {
                        PendingAction.CALC -> vm.clearCalculations()
                        PendingAction.PROGRESS -> vm.clearProgress()
                        PendingAction.ALL -> vm.clearAll()
                    }
                    snackbar.showSnackbar(action.done)
                }
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar("Privacy", onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrivacyText.sections.forEach { (title, body) ->
                    SectionTitle(title)
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

object PrivacyText {
    val sections: List<Pair<String, String>> = listOf(
        "Everything stays on this device" to
            "MathClock does not connect to the internet. The app has no internet permission, no accounts, " +
            "no ads, no analytics and no cloud services.",
        "What is stored" to
            "Calculator history (latest 50 calculations), practice sessions and answers (latest 100 sessions " +
            "and 90 daily sets), overall totals, and your preferences such as difficulty and clock format. " +
            "No name, age, email or other personal identifier is collected.",
        "Where it is stored" to
            "In the app's private storage on this device. Android cloud backup and device-to-device transfer " +
            "are turned off for MathClock, so this data is not copied anywhere else.",
        "Permissions" to
            "MathClock asks for no permissions. It does not use the camera, microphone, location or contacts.",
        "Deleting data" to
            "A grown-up can clear calculation history, learning progress, or all data in Settings. " +
            "Uninstalling the app also removes all of its data.",
    )
}
