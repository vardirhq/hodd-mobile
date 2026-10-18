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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import no.vardir.hodd.data.LocalItemAssistant
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ItemEditor(
    existing: CatalogItem?, collections: List<Collection>, preferredCollectionId: String?, busy: Boolean,
    photo: (String?) -> File?, importPhoto: (Uri, (String?) -> Unit) -> Unit,
    save: (CatalogItem) -> Unit, cancel: () -> Unit, message: (String) -> Unit,
    fieldCamera: @Composable (String, File, (File) -> Unit, () -> Unit, (String) -> Unit) -> Unit = { field, file, captured, cancelled, error ->
        GuidedFieldCamera(field, file, captured, cancelled, error)
    },
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val fieldAssistant = remember { LocalItemAssistant(context.applicationContext) }
    DisposableEffect(fieldAssistant) { onDispose { fieldAssistant.close() } }
    var fieldScan by remember { mutableStateOf<String?>(null) }
    var scanResults by remember { mutableStateOf(emptyList<String>()) }
    var lastScanField by rememberSaveable { mutableStateOf("") }
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
    var attributes by remember(draftId) { mutableStateOf(existing?.attributes.orEmpty()) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val status = ItemStatus.valueOf(statusName)
    val draft = CatalogItem(draftId, selectedCollection, title, creator, kind, format, barcode, status, notes, if (status == ItemStatus.LENT) lentTo else "", photoName, addedAt, metadataSource, metadataId, metadataUrl, subtitle, series, volume, publisher, language, releaseYear, condition, attributes)
    val dirty = if (existing != null) draft != existing else title.isNotBlank() || creator.isNotBlank() || barcode.isNotBlank() || notes.isNotBlank() || format.isNotBlank() || lentTo.isNotBlank() || kind != "Book" || status != ItemStatus.OWNED || photoName != null || subtitle.isNotBlank() || series.isNotBlank() || volume.isNotBlank() || publisher.isNotBlank() || language.isNotBlank() || releaseYear.isNotBlank() || condition.isNotBlank() || attributes.isNotEmpty()
    fun close() { if (!busy && !importing && !assisting) { if (dirty) discard = true else cancel() } }
    BackHandler { close() }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { barcode = it; message("Identifier inserted.") }
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
    fun setScannedValue(field: String, value: String) {
        when (field) {
            "Title" -> title = value
            "Creator" -> creator = value
            "Format" -> format = value
            "Subtitle" -> subtitle = value
            "Series" -> series = value
            "Volume" -> volume = value
            "Publisher" -> publisher = value
            "Language" -> language = value
            "Release year" -> releaseYear = value
            else -> attributes = attributes + (field to value)
        }
    }
    val fieldPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && lastScanField.isNotBlank()) fieldScan = lastScanField
        else message("Camera access was not granted. You can enter this field manually.")
    }
    @Composable
    fun scanAction(field: String) {
        IconButton(onClick = {
            focusManager.clearFocus()
            keyboard?.hide()
            lastScanField = field
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) fieldScan = field
            else fieldPermission.launch(Manifest.permission.CAMERA)
        }, enabled = !busy && !assisting && !importing) {
            Icon(Icons.Outlined.DocumentScanner, "Scan " + field)
        }
    }
    fieldScan?.let { field ->
        val file = remember(field) { File(context.cacheDir, "field-ocr-" + UUID.randomUUID() + ".jpg") }
        BackHandler { fieldScan = null; file.delete() }
        // Use the host activity's full screen and insets. A wrap-content Dialog can
        // measure beyond the visible window and put the capture button under system navigation.
        fieldCamera(field, file, { captured ->
            lastScanField = field
            fieldScan = null
            assisting = true
            scope.launch {
                try {
                    scanResults = fieldAssistant.readFieldStrip(captured)
                    if (scanResults.isEmpty()) message("No text found inside the highlighted strip.")
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    message("Text recognition failed. Try again or enter the field manually.")
                } finally { captured.delete(); assisting = false }
            }
        }, { fieldScan = null; file.delete() }, { message(it); fieldScan = null; file.delete() })
        return
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
            Text("Read a cover, identify an item, or add details yourself. Your collection is stored on this device.", color = Muted)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OutlinedTextField(title, { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                scanAction("Title")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OutlinedTextField(creator, { creator = it }, label = { Text("Creator / author") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                scanAction("Creator")
            }
            ChoiceMenu("Collection", collections.find { it.id == selectedCollection }?.name.orEmpty(), collections.map { it.id to it.name }) { selectedCollection = it }
            ChoiceMenu("Type", kind, listOf("Book", "Game", "Movie", "Vinyl", "CD", "Comic", "Coin", "Other").map { it to it }) { kind = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OutlinedTextField(format, { format = it }, label = { Text("Format / edition") }, placeholder = { Text("Hardback, cartridge, first edition…") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                scanAction("Format")
            }
            Text("Additional details", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtitle") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                scanAction("Subtitle")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OutlinedTextField(series, { series = it }, label = { Text("Series") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                scanAction("Series")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OutlinedTextField(volume, { volume = it }, label = { Text("Volume / number") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                scanAction("Volume")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OutlinedTextField(publisher, { publisher = it }, label = { Text("Publisher / label") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                scanAction("Publisher")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OutlinedTextField(language, { language = it }, label = { Text("Language") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                scanAction("Language")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OutlinedTextField(releaseYear, { releaseYear = it }, label = { Text("Release year") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                scanAction("Release year")
            }
            OutlinedTextField(condition, { condition = it }, label = { Text("Condition") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            val categoryFields = when (kind) {
                "Book", "Comic" -> listOf("isbn" to "ISBN", "pages" to "Page count", "binding" to "Binding")
                "Game" -> listOf("platform" to "Platform", "developer" to "Developer", "region" to "Region", "edition" to "Edition")
                "Movie" -> listOf("director" to "Director", "runtime" to "Runtime", "region" to "Region", "resolution" to "Resolution")
                "Vinyl", "CD" -> listOf("label" to "Record label", "catalogNumber" to "Catalog number", "tracks" to "Track count")
                "Coin" -> listOf("country" to "Country", "denomination" to "Denomination", "mintYear" to "Mint year", "mintMark" to "Mint mark", "material" to "Material")
                else -> emptyList()
            }
            if (categoryFields.isNotEmpty()) Text("$kind details", style = MaterialTheme.typography.titleLarge)
            categoryFields.forEach { (key, label) ->
                OutlinedTextField(attributes[key].orEmpty(), { value ->
                    attributes = attributes.toMutableMap().apply { if (value.isBlank()) remove(key) else put(key, value.take(300)) }
                }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            ItemAssistPanel(draft, photo(photoName), enabled = !busy && !importing, apply = { suggestion, replace, downloadedCover ->
                val next = draft.withSuggestion(suggestion, replace)
                title = next.title; creator = next.creator; kind = next.kind; format = next.format
                barcode = next.barcode; notes = next.notes; statusName = next.status.name; lentTo = next.lentTo
                subtitle = next.subtitle; series = next.series; volume = next.volume; publisher = next.publisher; language = next.language; releaseYear = next.releaseYear; condition = next.condition; attributes = next.attributes
                metadataSource = next.metadataSource; metadataId = next.metadataId; metadataUrl = next.metadataUrl
                if (downloadedCover != null) photoName = downloadedCover
            }, working = { assisting = it }, message = message)
            Text("On your shelf", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemStatus.entries.forEach { value -> FilterChip(selected = status == value, onClick = { statusName = value.name }, label = { Text(value.label) }) }
            }
            if (status == ItemStatus.LENT) OutlinedTextField(lentTo, { lentTo = it }, label = { Text("Lent to *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(barcode, { barcode = it }, label = { Text("EAN / ISBN") },
                    supportingText = { Text("Optional identifier. Search prioritizes title and creator.") },
                    modifier = Modifier.weight(1f), singleLine = true)
                IconButton(onClick = {
                    try { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES).setPrompt("Scan EAN or ISBN").setBeepEnabled(false).setOrientationLocked(false)) }
                    catch (_: Exception) { message("Camera unavailable. Enter the identifier manually.") }
                }, enabled = !busy && !assisting) { Icon(Icons.Outlined.QrCodeScanner, "Scan EAN / ISBN") }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes / condition") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        }
    }
    if (scanResults.isNotEmpty()) AlertDialog(
        onDismissRequest = { scanResults = emptyList() },
        title = { Text("Choose recognized text") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Insert into $lastScanField. Other fields will stay as they are.", style = MaterialTheme.typography.bodySmall)
                scanResults.forEach { candidate ->
                    TextButton(onClick = {
                        setScannedValue(lastScanField, candidate)
                        scanResults = emptyList()
                    }) { Text(candidate) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { scanResults = emptyList() }) { Text("Cancel") } }
    )
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
        item.attributes.toSortedMap().forEach { (key, value) -> if (value.isNotBlank()) Text("${key.replaceFirstChar { it.uppercase() }}: $value") }
        if (item.metadataSource.isNotBlank()) Text("Metadata: ${item.metadataSource}\n${item.metadataUrl}", style = MaterialTheme.typography.bodySmall, color = Muted)
        if (item.notes.isNotBlank()) { HorizontalDivider(color = Line); Text("Notes", style = MaterialTheme.typography.titleLarge); Text(item.notes) }
        Button(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit item") }
        TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Delete item", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false }, title = { Text("Delete ${item.title}?") }, text = { Text("This removes the item from your collection. This cannot be undone.") }, confirmButton = { TextButton(onClick = delete, enabled = !busy) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text("Keep item") } })
}
