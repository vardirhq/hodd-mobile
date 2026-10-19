package no.vardir.hodd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import no.vardir.hodd.core.FieldChoice
import no.vardir.hodd.core.FieldChoices

/** One searchable picker, with a separate custom-entry step that never changes a draft on cancel. */
@Composable
internal fun SearchableChoice(
    key: String, label: String, selected: String, choices: List<FieldChoice>,
    enabled: Boolean = true, optional: Boolean = true, custom: Boolean = true,
    saved: List<String> = emptyList(), supporting: String? = null, choose: (String) -> Unit,
) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    var query by rememberSaveable(key) { mutableStateOf("") }
    var customMode by rememberSaveable(key) { mutableStateOf(false) }
    var customValue by rememberSaveable(key) { mutableStateOf("") }
    val available = remember(choices, selected, saved) { FieldChoices.withExisting(choices, selected, saved) }
    val filtered = remember(available, query) { FieldChoices.search(available, query) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Muted)
        Surface(onClick = { query = ""; customMode = false; open = true }, enabled = enabled,
            shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, Line), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("choice-$key").semantics { role = Role.Button }) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceMark(key, selected)
                Text((available.firstOrNull { it.value == selected }?.label ?: selected).ifBlank { "Choose ${label.lowercase()}" }, Modifier.weight(1f), color = if (selected.isBlank()) Muted else Ink)
                Icon(Icons.Outlined.ExpandMore, null, tint = Muted)
            }
        }
        supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(if (customMode) "Custom ${label.lowercase()}" else "Choose ${label.lowercase()}") },
        text = {
            if (customMode) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use your own value. It will also be available from saved items.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(customValue, { customValue = it.take(300) }, label = { Text("Custom value") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("choice-custom-input"))
            } else Column(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it.take(100) }, label = { Text("Search choices") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("choice-search"))
                LazyColumn(Modifier.weight(1f, fill = false).testTag("choice-results")) {
                    if (optional && query.isBlank()) item {
                        TextButton(onClick = { choose(""); open = false }, modifier = Modifier.fillMaxWidth()) { Text("Leave blank") }
                    }
                    if (filtered.isEmpty()) item { Text("No matching choices. Use Custom to add your own.", Modifier.padding(vertical = 16.dp)) }
                    items(filtered, key = { it.value }) { option ->
                        TextButton(onClick = { choose(option.value); open = false }, modifier = Modifier.fillMaxWidth().testTag("option-${option.value}"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                            ChoiceMark(key, option.value)
                            Spacer(Modifier.width(12.dp))
                            Text(option.label, Modifier.weight(1f), color = Ink)
                            if (selected == option.value) Icon(Icons.Outlined.Check, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (customMode) TextButton(onClick = { choose(customValue.trim()); open = false }, enabled = customValue.isNotBlank()) { Text("Use custom value") }
            else if (custom) TextButton(onClick = { customValue = query.ifBlank { selected }; customMode = true }) { Text("Custom…") }
        },
        dismissButton = {
            TextButton(onClick = { if (customMode) customMode = false else open = false }) { Text(if (customMode) "Back" else "Cancel") }
        },
    )
}
