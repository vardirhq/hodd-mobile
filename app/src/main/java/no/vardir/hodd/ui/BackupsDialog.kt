package no.vardir.hodd.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.vardir.hodd.HoddViewModel
import no.vardir.hodd.data.RecoveryCopy
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BackupsDialog(vm: HoddViewModel, restored: () -> Unit, close: () -> Unit) {
    val recovery by vm.recovery.collectAsStateWithLifecycle()
    val preview by vm.backups.preview.collectAsStateWithLifecycle()
    val message by vm.backups.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<RecoveryCopy?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backups.export(uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.backups.inspect(uri)
    }
    fun dismiss() { if (!busy) { vm.backups.discard(); close() } }
    val confirming = selected != null || preview != null
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(if (confirming) "Replace collection?" else "Backups & recovery") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working. Keep Hodd open until this finishes.") }
                if (confirming) {
                    val collections = selected?.collections ?: preview!!.collections
                    val items = selected?.items ?: preview!!.items
                    Text("${collections} collections · ${items} items", fontWeight = FontWeight.SemiBold)
                    if (preview != null) {
                        Text("${preview!!.photos} photos · ${backupDate(preview!!.createdAt)}", color = Muted)
                        preview!!.titles.forEach { Text("• $it") }
                    } else Text(backupDate(selected!!.createdAt), color = Muted)
                    Text(if (ready) "This replaces your current ${catalog.items.size} items. A recovery copy of the current collection will be kept. Existing photos are retained."
                        else "This restores the selected collection. The unreadable saved file will be preserved.")
                    Text("Review the counts before continuing. You can cancel without changing anything.", color = Muted)
                } else {
                    Text("Export a backup", style = MaterialTheme.typography.titleMedium)
                    Text("Includes your collections, item details and cover photos. Choose a location outside Hodd; keep a copy on another device or in cloud storage for protection against losing this phone.")
                    Text(recovery.lastExportAt?.let { "Last verified export: ${backupDate(it)}" } ?: "No verified export yet.", color = Muted)
                    Button(onClick = { export.launch("hodd-backup-${SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())}.zip") }, enabled = ready && !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Export backup") }
                    OutlinedButton(onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Import backup") }
                    Text("Recovery copies", style = MaterialTheme.typography.titleMedium)
                    Text("The latest 20 catalog revisions are kept with your stored photos. These copies stay inside Hodd and are removed if you uninstall or clear app data. They do not replace an exported backup.", color = Muted)
                    recovery.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (recovery.copies.isEmpty()) Text("No readable recovery copies yet.", color = Muted)
                    recovery.copies.forEach { copy ->
                        OutlinedButton(onClick = { selected = copy }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("recovery-${copy.id}")) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(backupDate(copy.createdAt))
                                Text("${copy.collections} collections · ${copy.items} items", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                message?.let { Text(it, color = Ink) }
            }
        },
        confirmButton = {
            if (confirming) TextButton(onClick = {
                val copy = selected
                val done = { selected = null; restored() }
                if (copy != null) vm.backups.recover(copy.id, done) else vm.backups.restore(done)
            }, enabled = !busy) { Text("Replace collection") }
            else TextButton(onClick = ::dismiss, enabled = !busy) { Text("Close") }
        },
        dismissButton = {
            if (confirming) TextButton(onClick = { selected = null; vm.backups.discard() }, enabled = !busy) { Text("Cancel restore") }
        },
    )
}

private fun backupDate(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
