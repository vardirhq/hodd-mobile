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
import no.vardir.hodd.core.FieldChoices
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
    metadataRepository: MetadataRepository? = null,
    offerIdentification: Boolean = false,
    onIdentificationOffered: () -> Unit = {},
) {
    val context = LocalContext.current
    val local = remember(assistant) { assistant ?: LocalItemAssistant(context.applicationContext) }
    val metadata = remember(metadataRepository) { metadataRepository ?: MetadataRepository(context.applicationContext) }
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
    var recognizedText by rememberSaveable { mutableStateOf("") }
    var editCoverText by rememberSaveable { mutableStateOf(false) }
    var reviewTitle by remember { mutableStateOf("") }
    var reviewCreator by remember { mutableStateOf("") }
    var reviewKind by remember { mutableStateOf("") }
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

    fun identifyForLookup() {
        if (cover == null) return
        task("Reading your photo on-device…") {
            val recognized = local.readCover(cover, draft.kind)
            recognizedText = recognized.recognizedText
            notice = "${recognized.notice} Review the title, creator and type before choosing Find catalog matches. No search has been sent."
            suggestions = recognized.items
            if (suggestions.isEmpty()) { editCoverText = recognizedText.isNotBlank(); message(recognized.notice) }
            status = local.status()
        }
    }
    if (offerIdentification && cover != null) AlertDialog(
        onDismissRequest = onIdentificationOffered,
        title = { Text("Find details from this photo?") },
        text = { Text("Read the edited photo on this device, then review the title and type before looking up catalog details online. Your photo stays on-device.") },
        confirmButton = { TextButton(onClick = { onIdentificationOffered(); expanded = true; identifyForLookup() }, enabled = enabled && !running) { Text("Identify photo") } },
        dismissButton = { TextButton(onClick = onIdentificationOffered) { Text("Not now") } },
    )

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
                        notice = result.notice; suggestions = result.items; recognizedText = result.recognizedText
                        if (result.items.isEmpty()) { editCoverText = recognizedText.isNotBlank(); message(result.notice) }
                        status = local.status()
                    }
                }, enabled = enabled && !running && cover != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Read cover")
                }
                if (cover != null && draft.kind in listOf("Book", "Comic", "Vinyl", "CD", "Game", "Movie")) {
                    OutlinedButton(onClick = {
                        identifyForLookup()
                    }, enabled = enabled && !running, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Identify and find online")
                    }
                    Text("Review and edit recognition before sending a title/creator search. Your photo stays on-device.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (recognizedText.isNotBlank()) TextButton(onClick = { editCoverText = true }, enabled = !running) { Text("View cover text") }
                if (cover == null) Text("Take or choose a cover photo above to read its text.", style = MaterialTheme.typography.bodySmall, color = Muted)
                OutlinedTextField(description, { description = it.take(3000) }, label = { Text("Describe your item") },
                    placeholder = { Text("The Hobbit paperback, lent to Martin") }, enabled = enabled && !running,
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                TextButton(onClick = {
                    task("Preparing suggestions with local AI…") {
                        recognizedText = ""
                        suggestions = listOf(local.describe(description, draft.kind))
                        notice = "Suggested by on-device AI. Check the title, creator, edition and ownership details. Nothing has been saved."
                    }
                }, enabled = enabled && !running && status == LocalAiStatus.AVAILABLE && description.isNotBlank()) { Text("Suggest from description") }
                HorizontalDivider(color = Line)
                Text("Free online lookup", style = MaterialTheme.typography.titleSmall)
                Text("Games/movies: Wikidata (work-level facts). Books: Open Library. CDs/vinyl: MusicBrainz; artwork: Cover Art Archive. Online search prioritizes title and creator; barcode is a fallback. Only search terms are sent to the provider. Photos and notes stay on-device.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Button(onClick = {
                    task("Finding matching items…") {
                        val matches = metadata.lookup(draft.kind, draft.barcode, draft.title, draft.creator, draft.attributes["platform"].orEmpty())
                        if (matches.isEmpty()) message("No matching items found. Try a title search or enter details manually.")
                        else { recognizedText = ""; suggestions = matches; notice = "Choose a match, then review its details. Edition and cover availability can vary." }
                    }
                }, enabled = enabled && !running && draft.kind in listOf("Book", "Comic", "Vinyl", "CD", "Game", "Movie") && (draft.title.isNotBlank() || (draft.kind !in listOf("Game", "Movie") && draft.barcode.isNotBlank())), modifier = Modifier.fillMaxWidth()) { Text("Find online") }
                if (draft.kind !in listOf("Book", "Comic", "Vinyl", "CD", "Game", "Movie")) Text("Online lookup is available for books, music, games and movies. Local cover recognition works for every type.", style = MaterialTheme.typography.bodySmall, color = Muted)
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
                if (recognizedText.isNotBlank()) TextButton(onClick = { suggestions = emptyList(); editCoverText = true }) { Text("Review recognized text") }
                suggestions.forEach { item ->
                    OutlinedCard(onClick = { selected = item; reviewTitle = item.title; reviewCreator = item.creator; reviewKind = item.kind.ifBlank { draft.kind }; replace = false; downloadCover = item.coverUrl.isNotBlank() && cover == null }, modifier = Modifier.fillMaxWidth()) {
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
                OutlinedTextField(reviewTitle, { reviewTitle = it.take(300) }, label = { Text("Suggested title") }, enabled = !running,
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(reviewCreator, { reviewCreator = it.take(300) }, label = { Text("Suggested creator") }, enabled = !running,
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                SearchableChoice("review-kind", "Suggested type", reviewKind, FieldChoices.kinds, enabled = !running, optional = false, custom = false) { reviewKind = it }
                listOf("Format" to item.format, "Barcode" to item.barcode,
                    "Status" to item.status, "Lent to" to item.lentTo, "Notes" to item.notes,
                    "Subtitle" to item.subtitle, "Series" to item.series, "Volume" to item.volume,
                    "Publisher" to item.publisher, "Language" to item.language, "Release year" to item.releaseYear,
                    "Condition" to item.condition).filter { it.second.isNotBlank() }.forEach { (label, value) -> Text("$label: $value") }
                item.attributes.filterValues { it.isNotBlank() }.forEach { (key, value) ->
                    Text("${key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }}: $value")
                }
                if (reviewKind in listOf("Book", "Comic", "Vinyl", "CD", "Game", "Movie") && item.source !in listOf("Open Library", "MusicBrainz", "Wikidata")) {
                    OutlinedButton(onClick = {
                        task("Finding catalog matches for the reviewed title…") {
                            val matches = metadata.lookup(reviewKind, "", reviewTitle.trim(), reviewCreator.trim(), item.attributes["platform"].orEmpty().ifBlank { draft.attributes["platform"].orEmpty() })
                            if (matches.isEmpty()) message("No catalog matches. You can correct the title or apply the reviewed details.")
                            else { selected = null; suggestions = matches; notice = "Matches for your reviewed search. Check the edition before applying." }
                        }
                    }, enabled = !running && reviewTitle.isNotBlank()) { Text("Find catalog matches") }
                }
                if (item.sourceUrl.isNotBlank()) Text(item.sourceUrl, style = MaterialTheme.typography.bodySmall, color = Violet)
                Text("Source: ${item.source}", style = MaterialTheme.typography.bodySmall, color = Violet)
                if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodySmall)
                Text(if (item.source == "Wikidata") "Empty fields are filled by default. Matching titles adopt catalog capitalization; other existing details and your photo are kept." else "Empty fields are filled by default. Existing details and your photo are kept.", style = MaterialTheme.typography.bodySmall)
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
                apply(item.copy(title = reviewTitle.trim(), creator = reviewCreator.trim(), kind = reviewKind), replace, coverName)
                selected = null; suggestions = emptyList()
                message(if (coverFailed) "Details applied. Artwork was unavailable; your existing cover is kept." else "Suggestion applied. Review the form, then save.")
            }
        }, enabled = !running && reviewTitle.isNotBlank()) { Text("Use details") } },
        dismissButton = { TextButton(onClick = { selected = null }, enabled = !running) { Text("Back") } },
    ) }
    if (editCoverText) AlertDialog(onDismissRequest = { if (!running) editCoverText = false },
        title = { Text("Review recognized text") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("OCR can misread stylized lettering. Correct it here or use a closer cover photo. This stays on-device.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(recognizedText, { recognizedText = it.take(3000) }, label = { Text("Cover text") }, enabled = !running,
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = {
            task("Reading the corrected cover text…") {
                val result = local.readCoverText(recognizedText, draft.kind)
                suggestions = result.items; notice = result.notice
                if (suggestions.isNotEmpty()) editCoverText = false else message("No title found. Keep the work title on its own line, or try another cover photo.")
            }
        }, enabled = !running && recognizedText.isNotBlank()) { Text("Use corrected text") } },
        dismissButton = { TextButton(onClick = { editCoverText = false }, enabled = !running) { Text("Cancel") } })
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
