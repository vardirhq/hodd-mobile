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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import no.vardir.hodd.core.*
import no.vardir.hodd.data.*
import java.io.File
import kotlin.coroutines.coroutineContext

internal val searchableKinds = setOf("Book", "Comic", "Vinyl", "CD", "Game", "Movie")

/** Every entry point leads to a reviewed catalog search. Recognition never applies item facts. */
@Composable
internal fun ItemAssistPanel(
    draft: CatalogItem, cover: File?, enabled: Boolean,
    apply: (ItemSuggestion, Boolean, String?) -> Unit,
    working: (Boolean) -> Unit, message: (String) -> Unit,
    assistant: LocalItemAssistant? = null,
    metadataRepository: ItemMetadataSource? = null,
    offerIdentification: Boolean = false,
    onIdentificationOffered: () -> Unit = {},
    storeSearch: String? = null,
    onStoreSearchStarted: () -> Unit = {},
    onCatalogTitle: (String) -> Unit = {},
    onCopyReviewed: (String, String) -> Unit = { _, _ -> },
    onManualEntry: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val local = remember(assistant) { assistant ?: LocalItemAssistant(context.applicationContext) }
    val metadata = remember(metadataRepository) { metadataRepository ?: MetadataRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(draft.title) }
    var queryCreator by rememberSaveable { mutableStateOf("") }
    var queryKind by rememberSaveable { mutableStateOf(draft.kind) }
    var queryPlatform by rememberSaveable { mutableStateOf(draft.attributes["platform"].orEmpty()) }
    var queryEdited by rememberSaveable { mutableStateOf(false) }
    var reviewSearch by rememberSaveable { mutableStateOf(false) }
    var recognizedText by rememberSaveable { mutableStateOf("") }
    var recognitionNote by remember { mutableStateOf("") }
    var showRawText by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf(LocalAiStatus.CHECKING) }
    var confirmDownload by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<ItemSuggestion>()) }
    var selected by remember { mutableStateOf<ItemSuggestion?>(null) }
    val currentWorking by rememberUpdatedState(working)
    val currentMessage by rememberUpdatedState(message)
    LaunchedEffect(draft.kind) { queryKind = draft.kind; queryPlatform = draft.attributes["platform"].orEmpty(); suggestions = emptyList(); selected = null }
    LaunchedEffect(draft.title) { if (!queryEdited) query = draft.title }
    LaunchedEffect(draft.attributes["platform"]) { queryPlatform = draft.attributes["platform"].orEmpty() }
    LaunchedEffect(local, advanced) { if (advanced) status = local.status() }
    DisposableEffect(local) { onDispose { job?.cancel(); local.close(); currentWorking(false) } }

    fun task(label: String, action: suspend () -> Unit) {
        if (running) return
        feedback = ""; running = true; currentWorking(true); progress = label
        job = scope.launch {
            try { action() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                feedback = when (error) {
                    is java.net.UnknownHostException, is java.net.SocketTimeoutException -> "Could not reach the catalog. Keep your title and photo, try another search, or enter details manually."
                    else -> error.message ?: "Could not complete this request. Your draft is unchanged."
                }
                currentMessage(feedback)
            } finally { running = false; currentWorking(false); progress = "" }
        }
    }
    fun findMatches() {
        val title = query.trim(); val kind = queryKind; val creator = queryCreator.trim(); val platform = queryPlatform
        task("Finding catalog matches…") {
            val matches = metadata.lookup(kind, draft.barcode, title, creator, platform)
            coroutineContext.ensureActive()
            reviewSearch = false
            suggestions = matches
            if (matches.isEmpty()) feedback = "No matches found. Edit the search title, check the type or platform, or keep entering details manually."
        }
    }
    fun readCover() {
        if (cover == null) return
        task("Reading a possible search title on-device…") {
            val result = local.readCover(cover, queryKind)
            coroutineContext.ensureActive()
            recognizedText = result.recognizedText
            recognitionNote = result.notice
            val candidate = result.items.firstOrNull()
            query = candidate?.title.orEmpty(); queryEdited = true
            // Creator OCR is deliberately not a mandatory filter: it often reads a logo or tagline.
            queryCreator = ""
            queryKind = candidate?.kind?.takeIf { value -> FieldChoices.kinds.any { it.value == value } } ?: queryKind
            queryPlatform = candidate?.attributes?.get("platform").orEmpty().ifBlank { draft.attributes["platform"].orEmpty() }
            reviewSearch = true
            status = local.status()
        }
    }
    LaunchedEffect(storeSearch) {
        if (storeSearch != null && !running) {
            query = catalogSearchTitle(storeSearch); queryEdited = true; queryCreator = ""
            queryKind = draft.kind; queryPlatform = draft.attributes["platform"].orEmpty()
            onStoreSearchStarted()
            findMatches()
        }
    }

    if (offerIdentification && cover != null) AlertDialog(
        onDismissRequest = onIdentificationOffered,
        title = { Text("Find details from this photo?") },
        text = { Text("Read a possible title on this device, correct it, then choose a catalog match. Your photo stays on-device. You can also type the title directly.") },
        confirmButton = { TextButton(onClick = { onIdentificationOffered(); readCover() }, enabled = enabled && !running) { Text("Identify photo") } },
        dismissButton = { TextButton(onClick = onIdentificationOffered) { Text("Not now") } },
    )
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Find item", style = MaterialTheme.typography.titleMedium)
            Text("Start with the title. Choose a match, then check the details of your copy.", style = MaterialTheme.typography.bodySmall, color = Muted)
            OutlinedTextField(query, { query = it.take(300); queryEdited = true; feedback = "" }, label = { Text("Search title") },
                placeholder = { Text("Super Mario Maker 2") }, singleLine = true, enabled = enabled && !running,
                modifier = Modifier.fillMaxWidth().testTag("find-item-query"))
            Button(onClick = ::findMatches, enabled = enabled && !running && queryKind in searchableKinds &&
                (query.isNotBlank() || (queryKind !in setOf("Game", "Movie") && draft.barcode.isNotBlank())), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Find matches")
            }
            Text(when (queryKind) {
                "Game", "Movie" -> "Details from Wikidata. Store links and your photos can supply the cover."
                "Book", "Comic" -> "Details from Open Library. Check the edition before applying."
                "CD", "Vinyl" -> "Details from MusicBrainz; artwork from Cover Art Archive."
                else -> "Catalog search supports books, music, games and movies. You can enter this item manually."
            }, style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("Search sends the title, optional creator and platform to the catalog provider. Your photos and collection stay on-device.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Row {
                TextButton(onClick = { recognizedText = ""; recognitionNote = ""; reviewSearch = true }, enabled = enabled && !running) { Text("Search options") }
                if (cover != null) TextButton(onClick = ::readCover, enabled = enabled && !running) { Text("Read cover") }
            }
            if (feedback.isNotBlank()) Text(feedback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (running) {
                LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { job?.cancel() }) { Text("Cancel request") }
            }
            TextButton(onClick = { onManualEntry(query.trim(), queryKind) }, enabled = enabled && !running) { Text("Enter details manually") }
            TextButton(onClick = { advanced = !advanced }, enabled = enabled && !running) { Text("Local AI options") }
            if (advanced) {
                Text(when (status) {
                    LocalAiStatus.CHECKING -> "Checking local AI…"
                    LocalAiStatus.AVAILABLE -> "Gemini Nano available · suggestions still need review"
                    LocalAiStatus.DOWNLOADABLE -> "Local model available to download"
                    LocalAiStatus.DOWNLOADING -> "Local model downloading"
                    LocalAiStatus.UNAVAILABLE -> "Local AI unavailable · on-device text recognition is available"
                }, style = MaterialTheme.typography.bodySmall)
                if (status == LocalAiStatus.DOWNLOADABLE) TextButton(onClick = { confirmDownload = true }) { Text("Set up local AI") }
                OutlinedTextField(description, { description = it.take(3000) }, label = { Text("Describe your item") }, enabled = enabled && !running, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    task("Preparing a search title on-device…") {
                        val result = local.describe(description, queryKind)
                        coroutineContext.ensureActive()
                        query = result.title; queryEdited = true; queryCreator = ""; recognizedText = ""
                        queryKind = result.kind.ifBlank { queryKind }; recognitionNote = "Check this suggested search title. No item details have been applied."
                        reviewSearch = true
                    }
                }, enabled = enabled && !running && status == LocalAiStatus.AVAILABLE && description.isNotBlank()) { Text("Suggest search title") }
            }
        }
    }

    if (reviewSearch) AlertDialog(onDismissRequest = { if (!running) reviewSearch = false }, title = { Text("Review search") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Correct the title before searching. Recognition is a starting point; it has not changed your item.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it.take(300); queryEdited = true }, label = { Text("Search title") }, enabled = !running, modifier = Modifier.fillMaxWidth())
            SearchableChoice("search-kind", "Search type", queryKind, FieldChoices.kinds, enabled = !running, optional = false, custom = false) {
                if (queryKind != it) queryPlatform = ""
                queryKind = it
            }
            if (queryKind == "Game") SearchableChoice("search-platform", "Platform (optional)", queryPlatform,
                FieldChoices.platforms.map { FieldChoice(it.name) }, enabled = !running) { queryPlatform = it }
            OutlinedTextField(queryCreator, { queryCreator = it.take(300) }, label = { Text("Creator (optional)") }, enabled = !running, modifier = Modifier.fillMaxWidth())
            if (recognizedText.isNotBlank()) {
                TextButton(onClick = { showRawText = !showRawText }) { Text("View recognized text") }
                if (showRawText) Text(recognizedText, style = MaterialTheme.typography.bodySmall)
            }
            if (recognitionNote.isNotBlank()) Text(recognitionNote, style = MaterialTheme.typography.bodySmall, color = Muted)
            if (running) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress) }
        }
    }, confirmButton = { TextButton(onClick = ::findMatches, enabled = !running && queryKind in searchableKinds && query.isNotBlank()) { Text("Find matches") } },
        dismissButton = { TextButton(onClick = { job?.cancel(); reviewSearch = false }) { Text("Back to item") } })

    if (suggestions.isNotEmpty() && selected == null) AlertDialog(onDismissRequest = { suggestions = emptyList() }, title = { Text("Choose a match") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Results for “$query”. Check the year and platforms; originals, sequels and remakes can share names.", style = MaterialTheme.typography.bodySmall)
            suggestions.forEach { item -> OutlinedCard(onClick = { selected = item }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.creator.isNotBlank()) Text(item.creator)
                    if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(item.source, style = MaterialTheme.typography.labelSmall, color = Violet)
                }
            } }
        }
    }, confirmButton = { TextButton(onClick = { suggestions = emptyList(); reviewSearch = true }) { Text("Edit search") } },
        dismissButton = { TextButton(onClick = { suggestions = emptyList() }) { Text("Back to item") } })

    selected?.let { item ->
        var title by remember(item) { mutableStateOf(item.title) }
        var creator by remember(item) { mutableStateOf(item.creator) }
        var platform by remember(item) { mutableStateOf(draft.attributes["platform"].orEmpty().ifBlank { item.attributes["platform"].orEmpty() }) }
        var edition by remember(item) { mutableStateOf(draft.attributes["edition"].orEmpty()) }
        var useFirstYear by remember(item) { mutableStateOf(false) }
        var useTitle by remember(item) { mutableStateOf(true) }
        var replace by remember(item) { mutableStateOf(false) }
        var downloadCover by remember(item) { mutableStateOf(item.coverUrl.isNotBlank() && cover == null) }
        AlertDialog(onDismissRequest = { if (!running) selected = null }, title = { Text("Review details") }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(300) }, label = { Text("Catalog title") }, enabled = !running, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useTitle, { useTitle = it }, enabled = !running, modifier = Modifier.testTag("use-catalog-title")); Text("Use catalog title", style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(creator, { creator = it.take(300) }, label = { Text("Creator") }, enabled = !running, modifier = Modifier.fillMaxWidth())
                if (item.kind == "Game") {
                    SearchableChoice("match-platform", "Platform of your copy", platform, FieldChoices.platforms.map { FieldChoice(it.name) }, enabled = !running) { platform = it }
                    SearchableChoice("match-edition", "Edition of your copy", edition, FieldChoices.editions, enabled = !running) { edition = it }
                }
                if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodySmall)
                listOf("Publisher" to item.publisher, "Series" to item.series, "Format" to item.format, "Language" to item.language,
                    (if (item.kind == "Game" && item.source == "Wikidata") "First release of the work" else "Release year") to item.releaseYear)
                    .filter { it.second.isNotBlank() }.forEach { (label, value) -> Text("$label: $value") }
                item.attributes.filterKeys { it !in setOf("platform", "platformBrand", "edition") }.forEach { (key, value) -> Text("${key.replaceFirstChar { it.uppercase() }}: $value") }
                if (item.source == "Wikidata" && item.kind == "Game" && item.releaseYear.isNotBlank()) {
                    Text("The first release may belong to another platform or region. Your copy's release year stays unchanged unless you confirm it below.", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useFirstYear, { useFirstYear = it }, enabled = !running); Text("Use first release year for my copy", style = MaterialTheme.typography.bodySmall) }
                }
                Text("Source: ${item.source}", style = MaterialTheme.typography.bodySmall, color = Violet)
                Text("Use catalog title adopts the spelling above. Other details fill empty fields; your store link, photos and ownership are kept. Check your platform and edition before applying.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(replace, { replace = it }, enabled = !running); Text("Replace existing details", style = MaterialTheme.typography.bodySmall) }
                if (item.coverUrl.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(downloadCover, { downloadCover = it }, enabled = !running)
                    Text(if (cover == null) "Download catalog cover" else "Replace my cover with catalog artwork", style = MaterialTheme.typography.bodySmall)
                }
                if (running) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress) }
            }
        }, confirmButton = { TextButton(onClick = {
            task("Applying reviewed details…") {
                val reviewed = item.copy(title = title.trim(), creator = creator.trim()).forReviewedCopy(platform, edition, useFirstYear)
                var coverName: String? = null
                var coverFailed = false
                if (downloadCover) {
                    try { coverName = metadata.importCover(item) }
                    catch (error: Exception) { if (error is CancellationException) throw error; coverFailed = true }
                }
                coroutineContext.ensureActive()
                apply(reviewed.copy(title = if (useTitle) reviewed.title else ""), replace, coverName)
                if (reviewed.kind == "Game") onCopyReviewed(platform, edition)
                if (useTitle) onCatalogTitle(reviewed.title)
                selected = null; suggestions = emptyList(); query = reviewed.title; queryEdited = false
                currentMessage(if (coverFailed) "Details applied; artwork was unavailable. Your photo is kept." else "Details applied. Check your copy, then save to the collection.")
            }
        }, enabled = !running && title.isNotBlank()) { Text("Use details") } }, dismissButton = { TextButton(onClick = { job?.cancel(); selected = null }) { Text("Back") } })
    }
    if (confirmDownload) AlertDialog(onDismissRequest = { confirmDownload = false }, title = { Text("Download local AI?") },
        text = { Text("Android downloads Gemini Nano for on-device suggestions. This can be a large download. Title search and on-device text recognition work without it.") },
        confirmButton = { TextButton(onClick = { confirmDownload = false; task("Downloading local AI…") { local.download { }; status = local.status() } }) { Text("Download") } },
        dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Not now") } })
}
