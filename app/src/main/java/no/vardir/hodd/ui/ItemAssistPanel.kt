package no.vardir.hodd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.vardir.hodd.core.CatalogItem
import no.vardir.hodd.core.ItemSuggestion
import no.vardir.hodd.data.LocalAiStatus
import no.vardir.hodd.data.LocalItemAssistant
import no.vardir.hodd.data.MetadataRepository
import java.io.File

@Composable
internal fun ItemAssistPanel(
    draft: CatalogItem, cover: File?, enabled: Boolean,
    apply: (ItemSuggestion, Boolean, String?) -> Unit,
    working: (Boolean) -> Unit, message: (String) -> Unit,
    assistant: LocalItemAssistant? = null,
) {
    val context = LocalContext.current
    val local = remember(assistant) { assistant ?: LocalItemAssistant(context.applicationContext) }
    val metadata = remember { MetadataRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(LocalAiStatus.CHECKING) }
    var running by remember { mutableStateOf(false) }
    var taskJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<ItemSuggestion>()) }
    var notice by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ItemSuggestion?>(null) }
    var replace by remember { mutableStateOf(false) }
    var downloadCover by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }
    LaunchedEffect(local) { status = local.status() }
    DisposableEffect(local) { onDispose { local.close() } }

    fun task(label: String, action: suspend () -> Unit) {
        if (running) return
        running = true; working(true); progress = label
        taskJob = scope.launch {
            try { action() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                message(when (error) {
                    is java.net.UnknownHostException, is java.net.SocketTimeoutException -> "Could not reach the metadata service. Check your connection or keep entering details offline."
                    is IllegalArgumentException, is IllegalStateException, is java.io.IOException -> error.message ?: "Could not complete this request. Your draft is unchanged."
                    else -> "Could not complete this request. Your draft is unchanged; try again or enter details manually."
                })
            } finally { running = false; working(false); progress = "" }
        }
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, enabled = !running, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text("Find and fill details", Modifier.weight(1f))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (expanded) {
                Text("Local AI is preferred when ready. Cover text is always read on this device. Suggestions need your review.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(when (status) {
                    LocalAiStatus.CHECKING -> "Checking local AI…"
                    LocalAiStatus.AVAILABLE -> "Local AI ready · Gemini Nano"
                    LocalAiStatus.DOWNLOADABLE -> "Local AI supported · model download needed"
                    LocalAiStatus.DOWNLOADING -> "Local AI model is downloading"
                    LocalAiStatus.UNAVAILABLE -> "Local AI unavailable · offline cover text recognition still works"
                }, style = MaterialTheme.typography.labelMedium, color = Violet)
                if (status == LocalAiStatus.DOWNLOADABLE) TextButton(onClick = { confirmDownload = true }, enabled = enabled && !running) { Text("Set up local AI") }
                if (status == LocalAiStatus.UNAVAILABLE || status == LocalAiStatus.DOWNLOADING) TextButton(onClick = {
                    task("Checking local AI…") { status = local.status() }
                }, enabled = enabled && !running) { Text("Check availability again") }
                OutlinedButton(onClick = {
                    task("Reading cover on-device…") {
                        val result = local.readCover(requireNotNull(cover), draft.kind)
                        notice = result.notice; suggestions = result.items; status = local.status()
                    }
                }, enabled = enabled && !running && cover != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Read cover")
                }
                if (cover == null) Text("Take or choose a cover photo above to read its text.", style = MaterialTheme.typography.bodySmall, color = Muted)
                OutlinedTextField(description, { description = it.take(3000) }, label = { Text("Describe your item") },
                    placeholder = { Text("The Hobbit paperback, lent to Martin") }, enabled = enabled && !running,
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                TextButton(onClick = {
                    task("Preparing suggestions with local AI…") {
                        suggestions = listOf(local.describe(description, draft.kind))
                        notice = "Suggested by on-device AI. Check the title, creator, edition and ownership details. Nothing has been saved."
                    }
                }, enabled = enabled && !running && status == LocalAiStatus.AVAILABLE && description.isNotBlank()) { Text("Suggest from description") }
                HorizontalDivider(color = Line)
                Text("Free online lookup", style = MaterialTheme.typography.titleSmall)
                Text("Books: Open Library. CDs/vinyl: MusicBrainz; artwork: Cover Art Archive. Find online sends only the barcode, or title and creator, to the selected provider. Photos and notes stay on-device.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Button(onClick = {
                    task("Finding matching items…") {
                        val matches = metadata.lookup(draft.kind, draft.barcode, draft.title, draft.creator)
                        if (matches.isEmpty()) message("No matching items found. Try a title search or enter details manually.")
                        else { suggestions = matches; notice = "Choose a match, then review its details. Edition and cover availability can vary." }
                    }
                }, enabled = enabled && !running && draft.kind in listOf("Book", "Comic", "Vinyl", "CD") && (draft.barcode.isNotBlank() || draft.title.isNotBlank()), modifier = Modifier.fillMaxWidth()) { Text("Find online") }
                if (draft.kind !in listOf("Book", "Comic", "Vinyl", "CD")) Text("Online lookup is available for books and music. Local cover recognition works for every type.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(progress, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { taskJob?.cancel() }) { Text("Cancel request") }
            }
        }
    }

    if (suggestions.isNotEmpty() && selected == null) AlertDialog(
        onDismissRequest = { suggestions = emptyList() }, title = { Text("Choose a suggestion") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(notice, style = MaterialTheme.typography.bodySmall)
                suggestions.forEach { item ->
                    OutlinedCard(onClick = { selected = item; replace = false; downloadCover = item.coverUrl.isNotBlank() && cover == null }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            if (item.creator.isNotBlank()) Text(item.creator)
                            if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
                            Text(item.source, style = MaterialTheme.typography.labelSmall, color = Violet)
                        }
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { suggestions = emptyList() }) { Text("Cancel") } },
    )
    selected?.let { item -> AlertDialog(
        onDismissRequest = { if (!running) selected = null }, title = { Text("Review details") },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleLarge)
                listOf("Creator" to item.creator, "Type" to item.kind, "Format" to item.format, "Barcode" to item.barcode,
                    "Status" to item.status, "Lent to" to item.lentTo, "Notes" to item.notes,
                    "Subtitle" to item.subtitle, "Series" to item.series, "Volume" to item.volume,
                    "Publisher" to item.publisher, "Language" to item.language, "Release year" to item.releaseYear,
                    "Condition" to item.condition).filter { it.second.isNotBlank() }.forEach { (label, value) -> Text("$label: $value") }
                Text("Source: ${item.source}", style = MaterialTheme.typography.bodySmall, color = Violet)
                if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodySmall)
                Text("Empty fields are filled by default. Existing details and your photo are kept.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(replace, { replace = it }, enabled = !running)
                    Text("Replace existing details", style = MaterialTheme.typography.bodySmall)
                }
                if (item.coverUrl.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(downloadCover, { downloadCover = it }, enabled = !running)
                    Text(if (cover == null) "Download cover if available" else "Replace my cover with downloaded artwork", style = MaterialTheme.typography.bodySmall)
                }
                if (running) {
                    LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress)
                    TextButton(onClick = { taskJob?.cancel() }) { Text("Cancel request") }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            task("Applying details…") {
                var coverName: String? = null
                var coverFailed = false
                if (downloadCover) {
                    try { coverName = metadata.importCover(item) }
                    catch (error: Exception) { if (error is CancellationException) throw error; coverFailed = true }
                }
                apply(item, replace, coverName)
                selected = null; suggestions = emptyList()
                message(if (coverFailed) "Details applied. Artwork was unavailable; your existing cover is kept." else "Suggestion applied. Review the form, then save.")
            }
        }, enabled = !running) { Text("Use details") } },
        dismissButton = { TextButton(onClick = { selected = null }, enabled = !running) { Text("Back") } },
    ) }
    if (confirmDownload) AlertDialog(onDismissRequest = { confirmDownload = false }, title = { Text("Download local AI?") },
        text = { Text("Android will download the Gemini Nano model needed for on-device suggestions. This can be a large download; Wi-Fi is recommended. Hodd never sends your items to cloud AI. Cover text recognition works without this download.") },
        confirmButton = { TextButton(onClick = {
            confirmDownload = false
            task("Downloading local AI model…") {
                local.download { bytes -> progress = "Downloading local AI: ${bytes / (1024 * 1024)} MB received" }
                status = local.status()
                message(if (status == LocalAiStatus.AVAILABLE) "Local AI is ready." else "Model setup is still in progress. Check availability again shortly.")
            }
        }) { Text("Download") } }, dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Not now") } })
}
