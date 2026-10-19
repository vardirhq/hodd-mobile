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
import androidx.compose.runtime.saveable.mapSaver
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
import no.vardir.hodd.core.FieldChoices
import no.vardir.hodd.core.FieldChoice
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
    savedItems: List<CatalogItem> = emptyList(),
    fieldCamera: @Composable (String, File, (File) -> Unit, () -> Unit, (String) -> Unit) -> Unit = { field, file, captured, cancelled, error ->
        GuidedFieldCamera(field, file, captured, cancelled, error)
    },
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val editorScroll = rememberScrollState()
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
    var tags by rememberSaveable(draftId) { mutableStateOf(existing?.tags.orEmpty()) }
    var tagInput by rememberSaveable(draftId) { mutableStateOf("") }
    var location by rememberSaveable(draftId) { mutableStateOf(existing?.location.orEmpty()) }
    var lentTo by rememberSaveable(draftId) { mutableStateOf(existing?.lentTo.orEmpty()) }
    var photoName by rememberSaveable(draftId) { mutableStateOf(existing?.photoName) }
    var photoNames by rememberSaveable(draftId) { mutableStateOf(existing?.photoNames.orEmpty()) }
    var addingAdditional by rememberSaveable(draftId) { mutableStateOf(false) }
    var editingAdditional by rememberSaveable(draftId) { mutableStateOf<String?>(null) }
    var pendingPhoto by rememberSaveable(draftId) { mutableStateOf<String?>(null) }
    var editingPhoto by rememberSaveable(draftId) { mutableStateOf<String?>(null) }
    var editingTemporary by rememberSaveable(draftId) { mutableStateOf(false) }
    val createdPhotos = remember(draftId) { mutableSetOf<String>() }
    fun discardUnsavedPhotos() {
        val keep = (listOfNotNull(existing?.photoName) + existing?.photoNames.orEmpty()).toSet()
        createdPhotos.filterNot { it in keep }.forEach { photo(it)?.delete() }
        createdPhotos.clear()
    }
    var offerIdentification by rememberSaveable(draftId) { mutableStateOf(false) }
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
    var attributes by rememberSaveable(draftId, stateSaver = AttributeSaver) { mutableStateOf(existing?.attributes.orEmpty()) }
    var pendingBrand by rememberSaveable(draftId) { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val status = ItemStatus.valueOf(statusName)
    val draft = CatalogItem(draftId, selectedCollection, title, creator, kind, format, barcode, status, notes, if (status == ItemStatus.LENT) lentTo else "", photoName, addedAt, metadataSource, metadataId, metadataUrl, subtitle, series, volume, publisher, language, releaseYear, condition, attributes, photoNames, tags, location)
    val dirty = if (existing != null) draft != existing else title.isNotBlank() || creator.isNotBlank() || barcode.isNotBlank() || notes.isNotBlank() || tags.isNotEmpty() || location.isNotBlank() || format.isNotBlank() || lentTo.isNotBlank() || kind != "Book" || status != ItemStatus.OWNED || photoName != null || photoNames.isNotEmpty() || subtitle.isNotBlank() || series.isNotBlank() || volume.isNotBlank() || publisher.isNotBlank() || language.isNotBlank() || releaseYear.isNotBlank() || condition.isNotBlank() || attributes.isNotEmpty()
    fun close() { if (!busy && !importing && !assisting) { if (dirty) discard = true else { discardUnsavedPhotos(); cancel() } } }
    BackHandler { close() }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { barcode = it; message("Identifier inserted.") }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { importing = true; importPhoto(uri) { if (it != null) { createdPhotos.add(it); editingPhoto = it; editingTemporary = true; focusManager.clearFocus(); keyboard?.hide() }; importing = false } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val name = pendingPhoto
        if (success && name != null) { editingPhoto = name; editingTemporary = true; focusManager.clearFocus(); keyboard?.hide() } else photo(name)?.delete()
        pendingPhoto = null
    }
    fun takePhoto() {
        try {
            val name = "${UUID.randomUUID()}.jpg"
            val target = photo(name)!!
            target.parentFile!!.mkdirs()
            pendingPhoto = name
            createdPhotos.add(name)
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
    editingPhoto?.let { sourceName ->
        val source = photo(sourceName)
        if (source != null) {
            PhotoEditor(source, onAccepted = { editedName ->
                if (editingAdditional != null) photoNames = photoNames.map { if (it == editingAdditional) editedName else it }
                else if (addingAdditional && photoName != null) photoNames = photoNames + editedName
                else photoName = editedName
                if (editingTemporary) { source.delete(); createdPhotos.remove(sourceName) }
                createdPhotos.add(editedName)
                editingPhoto = null; editingTemporary = false; editingAdditional = null; addingAdditional = false; offerIdentification = true
            }, onCancel = {
                if (editingTemporary) { source.delete(); createdPhotos.remove(sourceName) }
                editingPhoto = null; editingTemporary = false; editingAdditional = null; addingAdditional = false
            }, message = message)
            return
        }
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
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(editorScroll).padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                    if (photoName != null) TextButton(onClick = {
                        focusManager.clearFocus(); keyboard?.hide(); editingPhoto = photoName; editingTemporary = false
                    }, enabled = !busy && !importing && !assisting) { Icon(Icons.Outlined.Crop, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit photo") }
                    if (photoName != null) TextButton(onClick = { photoName = photoNames.firstOrNull(); photoNames = photoNames.drop(1) }, enabled = !busy && !importing && !assisting) { Text("Remove photo") }
                }
            }
            if (photoName != null || photoNames.isNotEmpty()) {
                Text("Photo gallery", style = MaterialTheme.typography.titleMedium)
                photoNames.forEach { extra ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Cover(draft.copy(photoName = extra), photo(extra), Modifier.width(55.dp).height(72.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Additional photo", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                val oldCover = photoName
                                photoName = extra
                                photoNames = photoNames.filterNot { it == extra } + listOfNotNull(oldCover)
                            }) { Text("Set as cover") }
                        }
                        IconButton(onClick = { editingAdditional = extra; editingPhoto = extra; editingTemporary = false }, enabled = !busy) {
                            Icon(Icons.Outlined.Crop, "Edit additional photo")
                        }
                        IconButton(onClick = { photoNames = photoNames.filterNot { it == extra } }, enabled = !busy) {
                            Icon(Icons.Outlined.Delete, "Remove additional photo")
                        }
                    }
                }
                if (photoNames.size < 20) {
                    Row {
                        TextButton(onClick = { addingAdditional = true; gallery.launch("image/*") }, enabled = !busy && !importing && !assisting) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add photo")
                        }
                        TextButton(onClick = {
                            addingAdditional = true
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
                            else cameraPermission.launch(Manifest.permission.CAMERA)
                        }, enabled = !busy && !importing && !assisting) { Text("Take another") }
                    }
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
            val canChoose = !busy && !assisting && !importing
            SearchableChoice("collection", "Collection", selectedCollection, collections.map { FieldChoice(it.id, it.name) }, enabled = canChoose, optional = false, custom = false) { selectedCollection = it }
            SearchableChoice("kind", "Type", kind, FieldChoices.kinds, enabled = canChoose, optional = false, custom = false) { kind = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SearchableChoice("format", "Format / edition", format, FieldChoices.formats(kind), enabled = canChoose,
                    saved = savedItems.filter { it.kind == kind }.map { it.format }) { format = it } }
                scanAction("Format")
            }
            Text("Organization", style = MaterialTheme.typography.titleLarge)
            SearchableChoice(
                "location", "Storage location", location, emptyList(),
                enabled = canChoose, saved = savedItems.map { it.location }.filter { it.isNotBlank() },
            ) { location = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    tagInput, { tagInput = it.take(60) },
                    label = { Text("Add a tag") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val tag = tagInput.trim()
                        if (tag.isNotEmpty() && tags.size < 30 && tags.none { it.equals(tag, ignoreCase = true) }) {
                            tags = tags + tag
                        }
                        tagInput = ""
                    },
                    enabled = tagInput.isNotBlank() && tags.size < 30,
                ) { Text("Add") }
            }
            if (tags.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { tags = tags.filterNot { it == tag } },
                            label = { Text(tag) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "Remove $tag", Modifier.size(16.dp)) },
                        )
                    }
                }
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
                Box(Modifier.weight(1f)) { SearchableChoice("publisher", "Publisher / label", publisher, FieldChoices.publishers, enabled = canChoose, saved = savedItems.map { it.publisher }) { publisher = it } }
                scanAction("Publisher")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SearchableChoice("language", "Language", language, FieldChoices.languages, enabled = canChoose, saved = savedItems.map { it.language }) { language = it } }
                scanAction("Language")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SearchableChoice("releaseYear", "Release year", releaseYear, (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 2 downTo 1800).map { FieldChoice(it.toString()) }, enabled = canChoose, saved = savedItems.map { it.releaseYear }) { releaseYear = it } }
                scanAction("Release year")
            }
            SearchableChoice("condition", "Condition", condition, FieldChoices.conditions, enabled = canChoose, saved = savedItems.map { it.condition }) { condition = it }
            val categoryFields = when (kind) {
                "Book", "Comic" -> listOf("isbn" to "ISBN", "pages" to "Page count", "binding" to "Binding")
                "Game" -> listOf("platformBrand" to "Platform brand", "platform" to "Platform", "developer" to "Developer", "region" to "Region", "edition" to "Edition")
                "Movie" -> listOf("director" to "Director", "runtime" to "Runtime", "region" to "Region", "resolution" to "Resolution")
                "Vinyl", "CD" -> listOf("label" to "Record label", "catalogNumber" to "Catalog number", "tracks" to "Track count")
                "Coin" -> listOf("country" to "Country", "denomination" to "Denomination", "mintYear" to "Mint year", "mintMark" to "Mint mark", "material" to "Material")
                else -> emptyList()
            }
            if (categoryFields.isNotEmpty()) Text("$kind details", style = MaterialTheme.typography.titleLarge)
            fun attribute(key: String, value: String) {
                attributes = attributes.toMutableMap().apply { if (value.isBlank()) remove(key) else put(key, value.take(300)) }
            }
            val platformBrand = attributes["platformBrand"].orEmpty().ifBlank { FieldChoices.brandFor(attributes["platform"].orEmpty()).orEmpty() }
            categoryFields.forEach { (key, label) ->
                val options = when (key) {
                    "platformBrand" -> FieldChoices.brands
                    "platform" -> FieldChoices.platformsFor(platformBrand)
                    "binding" -> FieldChoices.bindings
                    "developer" -> FieldChoices.developers
                    "region" -> FieldChoices.regions(kind, format)
                    "edition" -> FieldChoices.editions
                    "resolution" -> FieldChoices.resolutions
                    "label" -> FieldChoices.publishers
                    "country" -> FieldChoices.countries
                    "material" -> FieldChoices.materials
                    "denomination" -> FieldChoices.denominations(attributes["country"].orEmpty())
                    "mintYear" -> (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) downTo 1800).map { FieldChoice(it.toString()) }
                    else -> null
                }
                if (options != null) SearchableChoice(key, label, if (key == "platformBrand") platformBrand else attributes[key].orEmpty(), options,
                    enabled = canChoose && (key != "platform" || platformBrand.isNotBlank() || !attributes["platform"].isNullOrBlank()),
                    supporting = when {
                        key == "platform" && platformBrand.isBlank() -> "Choose a brand first, or keep your existing platform."
                        key == "denomination" -> "Suggestions follow the selected country. Use Custom for historical denominations or currencies."
                        key == "region" && kind == "Movie" -> "Choices depend on the selected movie format. Check the region printed on your edition."
                        else -> null
                    },
                    saved = savedItems.filter { it.kind == kind && (key != "platform" || it.attributes["platformBrand"].orEmpty().ifBlank { FieldChoices.brandFor(it.attributes["platform"].orEmpty()).orEmpty() } == platformBrand) }.mapNotNull { it.attributes[key] },
                ) { value ->
                    if (key == "platformBrand" && value != platformBrand && !attributes["platform"].isNullOrBlank()) pendingBrand = value
                    else attribute(key, value)
                }
                else OutlinedTextField(attributes[key].orEmpty(), { value -> attribute(key, value) }, label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(), enabled = canChoose, singleLine = true)
            }
            ItemAssistPanel(draft, photo(photoName), enabled = !busy && !importing, apply = { suggestion, replace, downloadedCover ->
                val next = draft.withSuggestion(suggestion, replace)
                title = next.title; creator = next.creator; kind = next.kind; format = next.format
                barcode = next.barcode; notes = next.notes; statusName = next.status.name; lentTo = next.lentTo
                subtitle = next.subtitle; series = next.series; volume = next.volume; publisher = next.publisher; language = next.language; releaseYear = next.releaseYear; condition = next.condition; attributes = next.attributes
                metadataSource = next.metadataSource; metadataId = next.metadataId; metadataUrl = next.metadataUrl
                if (downloadedCover != null) photoName = downloadedCover
            }, working = { assisting = it }, message = message, offerIdentification = offerIdentification, onIdentificationOffered = { offerIdentification = false })
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
    pendingBrand?.let { brand ->
        AlertDialog(onDismissRequest = { pendingBrand = null }, title = { Text("Change platform brand?") },
            text = { Text("Changing the brand clears ${attributes["platform"]} so you can choose another platform. Other details are kept.") },
            confirmButton = { TextButton(onClick = {
                attributes = attributes.toMutableMap().apply { remove("platform"); if (brand.isBlank()) remove("platformBrand") else put("platformBrand", brand) }
                pendingBrand = null
            }) { Text("Change brand") } },
            dismissButton = { TextButton(onClick = { pendingBrand = null }) { Text("Keep platform") } })
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard your changes?") }, text = { Text("This item has unsaved changes.") }, confirmButton = { TextButton(onClick = { discardUnsavedPhotos(); cancel() }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}

private val AttributeSaver = mapSaver<Map<String, String>>(
    save = { it }, restore = { saved -> saved.mapValues { it.value as String } },
)
