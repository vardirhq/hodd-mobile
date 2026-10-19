package no.vardir.hodd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.vardir.hodd.BuildConfig

@Composable
internal fun AboutHodd(backups: () -> Unit, close: () -> Unit) {
    var showChanges by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val notes = remember { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readLines() } }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (showChanges) "Changelog" else "About Hodd") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Version ${BuildConfig.VERSION_NAME}", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("Build ${BuildConfig.VERSION_CODE}", color = Muted)
                if (!showChanges) {
                    Text("A local home for the things you collect.")
                    Text("Early alpha. Your catalog and photos stay on this device.", color = Muted)
                    TextButton(onClick = backups) { Text("Backups & recovery") }
                    TextButton(onClick = { showChanges = true }) { Text("View changelog") }
                } else {
                    notes.filter { it.isNotBlank() && it != "# Changelog" }.forEach { line ->
                        when {
                            line.startsWith("## ") -> Text(line.removePrefix("## ").replace("[", "").replace("]", ""),
                                style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
                            line.startsWith("### ") -> Text(line.removePrefix("### "), color = Violet, fontWeight = FontWeight.SemiBold)
                            line.startsWith("- ") -> Text("• ${line.removePrefix("- ")}", color = Ink)
                            else -> Text(line, color = Muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
        dismissButton = { if (showChanges) TextButton(onClick = { showChanges = false }) { Text("About") } },
    )
}
