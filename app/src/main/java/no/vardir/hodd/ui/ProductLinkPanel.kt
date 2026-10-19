package no.vardir.hodd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.vardir.hodd.core.CatalogItem
import no.vardir.hodd.core.ItemSuggestion
import no.vardir.hodd.data.ProductLinkSource
import no.vardir.hodd.data.ProductPreview
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

private class ProductPhotoOwner { var file: File? = null }

/** Owns requests and staged images until explicit acceptance transfers them to the item editor. */
@Composable
internal fun ProductLinkPanel(
    draft: CatalogItem, enabled: Boolean, source: ProductLinkSource, photo: (String?) -> File?,
    apply: (ItemSuggestion, String?) -> Unit, working: (Boolean) -> Unit, message: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf("") }
    var candidates by remember { mutableStateOf(emptyList<ProductPreview>()) }
    var selected by remember { mutableStateOf<ProductPreview?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var requestId by remember { mutableIntStateOf(0) }
    val owner = remember { ProductPhotoOwner() }
    var previewPhoto by remember { mutableStateOf<File?>(null) }
    val currentWorking by rememberUpdatedState(working)
    fun clearPhoto() { owner.file?.delete(); owner.file = null; previewPhoto = null }
    fun cancelRequest() {
        requestId++; job?.cancel(); job = null; running = false; currentWorking(false); clearPhoto()
    }
    fun dismiss() { cancelRequest(); selected = null; candidates = emptyList() }
    DisposableEffect(Unit) { onDispose { job?.cancel(); owner.file?.delete(); currentWorking(false) } }
    fun request(label: String, action: suspend () -> Unit) {
        if (running) return
        val id = ++requestId
        running = true; progress = label; currentWorking(true)
        job = scope.launch {
            try { action() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                message("${error.message ?: "Could not complete this import."} Your saved item is unchanged.")
            } finally { if (requestId == id) { running = false; currentWorking(false); job = null } }
        }
    }
    fun loadImage(item: ProductPreview) {
        clearPhoto()
        val target = photo("store-product-${UUID.randomUUID()}.jpg")
        if (target == null) { message("Could not prepare image storage."); return }
        owner.file = target
        request("Preparing product image…") {
            try {
                withContext(Dispatchers.IO) {
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Could not prepare image storage." }
                    source.prepareImage(item.imageUrl, target)
                }
                coroutineContext.ensureActive()
                previewPhoto = target
            } catch (error: Exception) { target.delete(); throw error }
        }
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, enabled = enabled && !running, contentPadding = PaddingValues(0.dp)) { Text("Import from a store") }
            if (expanded) {
                Text("Read a public product page on this device. The store receives your request; photos and collection data are not uploaded.", style = MaterialTheme.typography.bodySmall, color = Muted)
                OutlinedTextField(url, { url = it.take(2000) }, label = { Text("Product page URL") }, singleLine = true,
                    enabled = enabled && !running, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    request("Reading product details…") {
                        val found = source.preview(url.trim())
                        coroutineContext.ensureActive()
                        require(found.isNotEmpty()) { "No product details found." }
                        candidates = found
                        selected = found.singleOrNull()
                    }
                }, enabled = enabled && !running && url.trim().startsWith("https://", true)) { Text("Fetch product details") }
            }
            if (running) {
                LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress)
                TextButton(onClick = { cancelRequest() }) { Text("Cancel request") }
            }
        }
    }
    if (candidates.isNotEmpty() && selected == null) AlertDialog(onDismissRequest = ::dismiss,
        title = { Text("Choose the product") }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This page describes several products or variants. Select yours.")
                candidates.forEach { candidate -> OutlinedCard(onClick = { clearPhoto(); selected = candidate }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                        if (candidate.sku.isNotBlank()) Text("SKU: ${candidate.sku}")
                        if (candidate.price.isNotBlank()) Text("Store price: ${candidate.priceLabel}")
                    }
                } }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = ::dismiss) { Text("Cancel") } })
    selected?.let { item ->
        var useTitle by remember(item) { mutableStateOf(draft.title.isBlank()) }
        var useDescription by remember(item) { mutableStateOf(draft.notes.isBlank()) }
        var useBrand by remember(item) { mutableStateOf(draft.attributes["brand"].isNullOrBlank()) }
        var usePublisher by remember(item) { mutableStateOf(draft.publisher.isBlank()) }
        val priceEmpty = draft.attributes["storePrice"].isNullOrBlank() && draft.attributes["Store price"].isNullOrBlank()
        var usePrice by remember(item) { mutableStateOf(priceEmpty) }
        var useImage by remember(item) { mutableStateOf(false) }
        @Composable fun field(label: String, value: String, checked: Boolean, editable: Boolean, change: (Boolean) -> Unit) {
            if (value.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, change, enabled = !running && editable, modifier = Modifier.testTag("store-field-$label"))
                Text("$label: $value" + if (!editable) " (existing value kept)" else "", style = MaterialTheme.typography.bodyMedium)
            }
        }
        AlertDialog(onDismissRequest = ::dismiss, title = { Text("Import product details?") }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field("Title", item.title, useTitle, draft.title.isBlank()) { useTitle = it }
                field("Brand", item.brand, useBrand, draft.attributes["brand"].isNullOrBlank()) { useBrand = it }
                field("Publisher", item.publisher, usePublisher, draft.publisher.isBlank()) { usePublisher = it }
                field("Description → notes", item.description, useDescription, draft.notes.isBlank()) { useDescription = it }
                field("Store price", item.priceLabel.takeIf { item.price.isNotBlank() }.orEmpty(), usePrice, priceEmpty) { usePrice = it }
                Text("Store prices are retail listings, not collection valuations. Existing fields are kept. Nothing is saved until Save on the item form.", style = MaterialTheme.typography.bodySmall)
                Text(item.url, style = MaterialTheme.typography.bodySmall, color = Violet)
                if (item.imageUrl.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(useImage, { checked ->
                            useImage = checked
                            if (checked) loadImage(item) else cancelRequest()
                        }, enabled = draft.photoName == null || draft.photoNames.size < 20, modifier = Modifier.testTag("store-image-checkbox"))
                        Text(if (draft.photoName == null) "Import product image" else "Add product image to gallery")
                    }
                    if (useImage && previewPhoto != null) {
                        AsyncImage(previewPhoto, "Product image preview", Modifier.fillMaxWidth().height(180.dp))
                        Text("The image opens in the photo editor before it is added.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (useImage && previewPhoto == null && !running) TextButton(onClick = { loadImage(item) }) { Text("Retry image preview") }
                }
                if (running) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress); TextButton(onClick = { useImage = false; cancelRequest() }) { Text("Cancel image request") } }
            }
        }, confirmButton = { TextButton(onClick = {
            val chosen = item.copy(title = if (useTitle) item.title else "", description = if (useDescription) item.description else "",
                brand = if (useBrand) item.brand else "", publisher = if (usePublisher) item.publisher else "", price = if (usePrice) item.price else "")
            val acceptedPhoto = previewPhoto?.takeIf { useImage }
            if (acceptedPhoto != null) { owner.file = null; previewPhoto = null } else clearPhoto()
            selected = null; candidates = emptyList()
            apply(chosen.suggestion(draft.kind), acceptedPhoto?.name)
        }, enabled = !running && (!useImage || previewPhoto != null)) { Text("Use details") } },
            dismissButton = { TextButton(onClick = ::dismiss) { Text("Cancel") } })
    }
}
