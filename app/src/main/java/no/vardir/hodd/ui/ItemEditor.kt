package no.vardir.hodd.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import no.vardir.hodd.core.CatalogItem
import no.vardir.hodd.core.Collection
import no.vardir.hodd.core.ItemStatus
import no.vardir.hodd.core.withSuggestion
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ItemEditor(
    existing: CatalogItem?, collections: List<Collection>, preferredCollectionId: String?, busy: Boolean,
    photo: (String?) -> File?, importPhoto: (Uri, (String?) -> Unit) -> Unit,
    save: (CatalogItem) -> Unit, cancel: () -> Unit, message: (String) -> Unit,
) {
    val context = LocalContext.current
    val draftId = rememberSaveable(existing?.id) { existing?.id ?: UUID.randomUUID().toString() }
    val addedAt = rememberSaveable(draftId) { existing?.addedAt ?: System.currentTimeMillis() }
    var title by rememberSaveable(draftId) { mutableStateOf(existing?.title.orEmpty()) }
    var creator by rememberSaveable(draftId) { mutableStateOf(existing?.creator.orEmpty()) }
    var selectedCollection by rememberSaveable(draftId) { mutableStateOf(existing?.collectionId ?: preferredCollectionId ?: collections.first().id) }
    var kind by rememberSaveable(draftId) { mutableStateOf(existing?.kind ?: "Book") }
    var format by rememberSaveable(draftId) { mutableStateOf(existing?.format.orEmpty()) }
    var barcode by rememberSaveable(draftId) { mutableStateOf(existing?.barcode.orEmpty()) }
    var statusName by rememberSaveable(draftId) { mutableStateOf((existing?.status ?: ItemStatus.OWNED).name) }
    var notes by rememberSaveable(draftId) { mutableStateOf(existing?.notes.orEmpty()) }
    var lentTo by rememberSaveable(draftId) { mutableStateOf(existing?.lentTo.orEmpty()) }
    var photoName by rememberSaveable(draftId) { mutableStateOf(existing?.photoName) }
    var pendingPhoto by rememberSaveable(draftId) { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var assisting by remember { mutableStateOf(false) }
    var metadataSource by rememberSaveable(draftId) { mutableStateOf(existing?.metadataSource.orEmpty()) }
    var metadataId by rememberSaveable(draftId) { mutableStateOf(existing?.metadataId.orEmpty()) }
    var metadataUrl by rememberSaveable(draftId) { mutableStateOf(existing?.metadataUrl.orEmpty()) }
    var subtitle by rememberSaveable(draftId) { mutableStateOf(existing?.subtitle.orEmpty()) }
    var series by rememberSaveable(draftId) { mutableStateOf(existing?.series.orEmpty()) }
    var volume by rememberSaveable(draftId) { mutableStateOf(existing?.volume.orEmpty()) }
    var publisher by rememberSaveable(draftId) { mutableStateOf(existing?.publisher.orEmpty()) }
    var language by rememberSaveable(draftId) { mutableStateOf(existing?.language.orEmpty()) }
    var releaseYear by rememberSaveable(draftId) { mutableStateOf(existing?.releaseYear.orEmpty()) }
    var condition by rememberSaveable(draftId) { mutableStateOf(existing?.condition.orEmpty()) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val status = ItemStatus.valueOf(statusName)
    val draft = CatalogItem(draftId, selectedCollection, title, creator, kind, format, barcode, status, notes, if (status == ItemStatus.LENT) lentTo else "", photoName, addedAt, metadataSource, metadataId, metadataUrl, subtitle, series, volume, publisher, language, releaseYear, condition, existing?.attributes.orEmpty())
    val dirty = if (existing != null) draft != existing else title.isNotBlank() || creator.isNotBlank() || barcode.isNotBlank() || notes.isNotBlank() || format.isNotBlank() || lentTo.isNotBlank() || kind != "Book" || status != ItemStatus.OWNED || photoName != null || subtitle.isNotBlank() || series.isNotBlank() || volume.isNotBlank() || publisher.isNotBlank() || language.isNotBlank() || releaseYear.isNotBlank() || condition.isNotBlank()
    fun close() { if (!busy && !importing && !assisting) { if (dirty) discard = true else cancel() } }
    BackHandler { close() }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { barcode = it; message("Barcode captured. Open Find and fill details to look it up, or enter details yourself.") }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { importing = true; importPhoto(uri) { if (it != null) photoName = it; importing = false } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val name = pendingPhoto
        if (success && name != null) photoName = name else photo(name)?.delete()
        pendingPhoto = null
    }
    fun takePhoto() {
        try {
            val name = "${UUID.randomUUID()}.jpg"
            val target = photo(name)!!
            target.parentFile!!.mkdirs()
            pendingPhoto = name
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", target))
        } catch (_: Exception) {
            pendingPhoto = null
            message("Could not open the camera. Choose an existing photo instead.")
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto()
        else message("Camera access was not granted. You can choose an existing photo instead.")
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (existing == null) "A new find" else "Edit item", style = MaterialTheme.typography.titleLarge) }, navigationIcon = { IconButton(onClick = ::close, enabled = !busy && !importing && !assisting) { Icon(Icons.Outlined.Close, "Cancel") } }) },
        bottomBar = {
            Surface(shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) {
                Button(onClick = { save(draft) }, enabled = title.isNotBlank() && !busy && !importing && !assisting && (status != ItemStatus.LENT || lentTo.isNotBlank()), modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).heightIn(min = 52.dp)) {
                    if (busy || importing || assisting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(8.dp)); Text(if (existing == null) "Save to collection" else "Save changes") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Make it part of your story.", style = MaterialTheme.typography.headlineMedium)
            Text("Scan a barcode, read a cover, or add details yourself. Your collection is stored on this device.", color = Muted)
            OutlinedButton(onClick = {
                try { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES).setPrompt("Scan an item’s barcode").setBeepEnabled(false).setOrientationLocked(false)) }
                catch (_: Exception) { message("Camera unavailable. You can enter the barcode below.") }
            }, enabled = !busy && !assisting, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.QrCodeScanner, null); Spacer(Modifier.width(10.dp)); Text("Scan barcode") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(draft.copy(title = title.ifBlank { "Your next find" }), photo(photoName), Modifier.width(95.dp).height(130.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your cover", style = MaterialTheme.typography.titleMedium)
                    Text("Use a photo or keep the simple cover.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { gallery.launch("image/*") }, enabled = !busy && !importing && !assisting) { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Choose photo") }
                    TextButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    }, enabled = !busy && !importing && !assisting) { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Take photo") }
                    if (photoName != null) TextButton(onClick = { photoName = null }, enabled = !busy) { Text("Remove photo") }
                }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(creator, { creator = it }, label = { Text("Creator / author") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            ChoiceMenu("Collection", collections.find { it.id == selectedCollection }?.name.orEmpty(), collections.map { it.id to it.name }) { selectedCollection = it }
            ChoiceMenu("Type", kind, listOf("Book", "Game", "Movie", "Vinyl", "CD", "Comic", "Coin", "Other").map { it to it }) { kind = it }
            OutlinedTextField(format, { format = it }, label = { Text("Format / edition") }, placeholder = { Text("Hardback, cartridge, first edition…") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Additional details", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtitle") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(series, { series = it }, label = { Text("Series") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(volume, { volume = it }, label = { Text("Volume / number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(publisher, { publisher = it }, label = { Text("Publisher / label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(language, { language = it }, label = { Text("Language") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(releaseYear, { releaseYear = it }, label = { Text("Release year") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(condition, { condition = it }, label = { Text("Condition") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            ItemAssistPanel(draft, photo(photoName), enabled = !busy && !importing, apply = { suggestion, replace, downloadedCover ->
                val next = draft.withSuggestion(suggestion, replace)
                title = next.title; creator = next.creator; kind = next.kind; format = next.format
                barcode = next.barcode; notes = next.notes; statusName = next.status.name; lentTo = next.lentTo
subtitle = next.subtitle; series = next.series; volume = next.volume; publisher = next.publisher; language = next.language; releaseYear = next.releaseYear; condition = next.condition;
                metadataSource = next.metadataSource; metadataId = next.metadataId; metadataUrl = next.metadataUrl
                if (downloadedCover != null) photoName = downloadedCover
            }, working = { assisting = it }, message = message)
            Text("On your shelf", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemStatus.entries.forEach { value -> FilterChip(selected = status == value, onClick = { statusName = value.name }, label = { Text(value.label) }) }
            }
            if (status == ItemStatus.LENT) OutlinedTextField(lentTo, { lentTo = it }, label = { Text("Lent to *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode / ISBN") }, supportingText = { Text("Use Find and fill details to look up a book or music barcode. Clear the barcode to search by title.") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes / condition") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard your changes?") }, text = { Text("This item has unsaved changes.") }, confirmButton = { TextButton(onClick = cancel) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}

@Composable
private fun ChoiceMenu(label: String, selected: String, choices: List<Pair<String, String>>, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Muted)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(selected, Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null) }
        DropdownMenu(expanded, { expanded = false }) { choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { choose(id); expanded = false }) } }
    }
}

@Composable
internal fun ItemDetail(item: CatalogItem, collection: String, photo: File?, edit: () -> Unit, delete: () -> Unit, busy: Boolean) {
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Cover(item, photo, Modifier.width(190.dp).height(260.dp)) }
        Text(item.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("item-title"))
        if (item.creator.isNotBlank()) Text(item.creator, style = MaterialTheme.typography.titleMedium, color = Muted)
        StatusPill(item.status)
        Text(collection, color = Violet)
        Text(listOf(item.kind, item.format).filter { it.isNotBlank() }.joinToString(" · "), color = Muted)
        if (item.status == ItemStatus.LENT) Text("Lent to ${item.lentTo}")
        if (item.barcode.isNotBlank()) { HorizontalDivider(color = Line); Text("Barcode / ISBN", style = MaterialTheme.typography.labelLarge, color = Muted); Text(item.barcode) }
        listOf("Subtitle" to item.subtitle, "Series" to item.series, "Volume" to item.volume, "Publisher" to item.publisher, "Language" to item.language, "Release year" to item.releaseYear, "Condition" to item.condition).filter { it.second.isNotBlank() }.forEach { (label, value) -> Text("$label: $value") }
        if (item.metadataSource.isNotBlank()) Text("Metadata: ${item.metadataSource}\n${item.metadataUrl}", style = MaterialTheme.typography.bodySmall, color = Muted)
        if (item.notes.isNotBlank()) { HorizontalDivider(color = Line); Text("Notes", style = MaterialTheme.typography.titleLarge); Text(item.notes) }
        Button(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit item") }
        TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Delete item", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false }, title = { Text("Delete ${item.title}?") }, text = { Text("This removes the item from your collection. This cannot be undone.") }, confirmButton = { TextButton(onClick = delete, enabled = !busy) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text("Keep item") } })
}
