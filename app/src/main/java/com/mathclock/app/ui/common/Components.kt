@file:OptIn(ExperimentalMaterial3Api::class)

package com.mathclock.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mathclock.app.ui.theme.ClassroomColors
import kotlin.random.Random

@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = ClassroomColors.Ivory),
    )
}

/** − value + control with meaningful labels for accessibility. */
@Composable
fun Stepper(
    label: String,
    valueText: String,
    spokenValue: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        FilledTonalButton(
            onClick = onDecrease,
            modifier = Modifier
                .sizeIn(minWidth = 56.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Decrease $label" },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            valueText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 56.dp)
                .semantics {
                    contentDescription = "$label: $spokenValue"
                    liveRegion = LiveRegionMode.Polite
                },
        )
        FilledTonalButton(
            onClick = onIncrease,
            modifier = Modifier
                .sizeIn(minWidth = 56.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Increase $label" },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    groupLabel: String? = null,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (groupLabel != null) contentDescription = groupLabel },
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = if (option == selected) "Selected" else "Not selected" },
                label = { Text(label(option), maxLines = 2) },
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

/**
 * Adult-entry check before destructive actions: a multiplication question typed into a text field
 * (works with screen readers and switch access). This prevents accidental changes by children; it
 * is not secure authentication.
 */
@Composable
fun AdultGateDialog(
    title: String,
    explanation: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val a by rememberSaveable { mutableStateOf(Random.nextInt(6, 10)) }
    val b by rememberSaveable { mutableStateOf(Random.nextInt(11, 20)) }
    var input by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(explanation)
                Text("For grown-ups: what is $a × $b?", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = { v -> input = v.filter { it.isDigit() }.take(4); wrong = false },
                    label = { Text("Answer") },
                    singleLine = true,
                    isError = wrong,
                    supportingText = { if (wrong) Text("That's not right. Please try again.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.toIntOrNull() == a * b) onConfirm() else wrong = true },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}
