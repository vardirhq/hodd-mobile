package no.vardir.hodd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import no.vardir.hodd.HoddViewModel
import no.vardir.hodd.core.*
import no.vardir.hodd.core.Collection
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoddApp(vm: HoddViewModel = viewModel()) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("home") }
    var collectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var itemId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailReturn by rememberSaveable { mutableStateOf("home") }
    var collectionReturn by rememberSaveable { mutableStateOf("home") }
    var query by rememberSaveable { mutableStateOf("") }
    var newCollection by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var backupsOpen by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val collection = catalog.collections.find { it.id == collectionId }
    val item = catalog.items.find { it.id == itemId }
    fun openItem(value: CatalogItem) { detailReturn = page; itemId = value.id; page = "detail" }
    fun openCollection(value: Collection) { collectionReturn = page; collectionId = value.id; query = ""; page = "collection" }
    fun addItem() {
        if (catalog.collections.isEmpty()) newCollection = true
        else { detailReturn = page; itemId = null; page = "edit" }
    }
    fun back() {
        page = when (page) {
            "edit" -> if (item != null) "detail" else detailReturn
            "detail" -> detailReturn
            "collection" -> collectionReturn
            else -> "home"
        }
    }
    BackHandler(page != "home" && page != "edit") { back() }
    LaunchedEffect(error, ready) {
        if (error != null && ready) { snack.showSnackbar(error!!); vm.clearError() }
    }
    fun restoredBackup() { page = "home"; itemId = null; collectionId = null; query = "" }
    if (!ready) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                if (error == null) CircularProgressIndicator()
                else {
                    Text("Couldn’t open your collection", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp)); Text(error!!)
                    Button(onClick = { vm.load() }, modifier = Modifier.padding(top = 20.dp)) { Text("Retry") }
                    TextButton(onClick = { backupsOpen = true }) { Text("Backups & recovery") }
                }
            }
        }
        if (backupsOpen) BackupsDialog(vm, ::restoredBackup) { backupsOpen = false }
        return
    }
    val editor = page == "edit"
    Scaffold(
        contentWindowInsets = if (editor) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (!editor) TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ShelfIcon, null, tint = Violet, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp)); Text("HODD", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                } },
                navigationIcon = { if (page == "detail" || page == "collection") IconButton(onClick = { back() }) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { aboutOpen = true }) { Icon(Icons.Outlined.Info, "About Hodd") }
                    if (page != "detail") {
                        TextButton(onClick = { addItem() }) { Text("Add item") }
                        IconButton(onClick = { newCollection = true }) { Icon(Icons.Outlined.CreateNewFolder, "New collection") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
            )
        },
        bottomBar = {
            if (!editor && page != "detail") NavigationBar(containerColor = Paper) {
                listOf(Triple("home", "Home", Icons.Outlined.Home), Triple("collections", "Collections", Icons.Outlined.GridView), Triple("wishlist", "Wishlist", Icons.Outlined.FavoriteBorder)).forEach { (target, label, icon) ->
                    NavigationBarItem(selected = page == target || (target == "collections" && page == "collection"), onClick = { page = target; query = ""; collectionId = null }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                when (page) {
                    "home" -> HomeScreen(catalog, query, { query = it }, ::openCollection, ::openItem, { collectionId = null; page = "collection" }, { newCollection = true }, vm::loadExamples, vm.repository::photo)
                    "collections" -> CollectionsScreen(catalog, ::openCollection, { newCollection = true })
                    "collection", "wishlist" -> ItemsScreen(catalog, if (page == "wishlist") null else collection, page == "wishlist", query, { query = it }, ::openItem, ::addItem, vm.repository::photo)
                    "detail" -> if (item != null) ItemDetail(item, catalog.collections.find { it.id == item.collectionId }?.name.orEmpty(), vm.repository.photo(item.photoName), { page = "edit" }, { vm.deleteItem(item.id) { page = detailReturn } }, busy)
                    "edit" -> ItemEditor(item, catalog.collections, collectionId, busy, vm.repository::photo, { uri, done -> vm.photo(uri, done) }, { saved -> vm.save(saved) { itemId = saved.id; collectionId = saved.collectionId; page = "detail" } }, { back() }, { message -> scope.launch { snack.showSnackbar(message) } }, savedItems = catalog.items)
                }
            }
        }
    }
    if (newCollection) NewCollectionDialog(busy, catalog.collections.map { it.name }, { newCollection = false }) { name -> vm.addCollection(name) { newCollection = false; collectionReturn = page; collectionId = vm.catalog.value.collections.last().id; page = "collection"; query = "" } }
    if (aboutOpen) AboutHodd({ aboutOpen = false; backupsOpen = true }) { aboutOpen = false }
    if (backupsOpen) BackupsDialog(vm, ::restoredBackup) { backupsOpen = false }
}

@Composable
private fun HomeScreen(catalog: Catalog, query: String, onQuery: (String) -> Unit, openCollection: (Collection) -> Unit, openItem: (CatalogItem) -> Unit, all: () -> Unit, create: () -> Unit, examples: () -> Unit, photo: (String?) -> File?) {
    LazyColumn(contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Your collection,\nwithin reach.", style = MaterialTheme.typography.headlineLarge) }
        item { SearchField(query, onQuery) }
        if (catalog.collections.isEmpty()) {
            item { EmptyState(ShelfIcon, "A home for what you love", "Books, games, records, and the things that are uniquely yours. Create a collection to get started.", "Create a collection", create) }
            item { TextButton(onClick = examples) { Text("Explore with an example collection") } }
        } else if (query.isNotBlank()) {
            val results = catalog.search(query)
            item { Text("${results.size} results", style = MaterialTheme.typography.titleLarge) }
            if (results.isEmpty()) item { Text("No matching items. Try a title, creator, or barcode.", color = Muted) }
            items(results, key = { it.id }) { item -> ItemRow(item, photo(item.photoName)) { openItem(item) } }
        } else {
            val featured = catalog.collections.first()
            val collectionItems = catalog.search(collectionId = featured.id)
            item {
                Column(Modifier.fillMaxWidth()) {
                    Shelf(collectionItems, photo, openItem)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().clickable { openCollection(featured) }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(featured.name, style = MaterialTheme.typography.headlineMedium)
                            val counts = catalog.counts(featured.id)
                            Text("${counts.owned} owned · ${counts.wanted} wanted", color = Muted)
                        }
                        Icon(Icons.Outlined.ArrowForward, "Open collection", tint = Violet)
                    }
                }
            }
            item { HorizontalDivider(color = Line) }
            item { SectionTitle("Recently added", "View all", all) }
            if (catalog.items.isEmpty()) item { Text("Your first find belongs here. Tap Add item to save it.", color = Muted) }
            items(catalog.search().take(5), key = { it.id }) { item -> ItemRow(item, photo(item.photoName)) { openItem(item) } }
            item {
                val counts = catalog.counts()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Owned", counts.owned, Modifier.weight(1f)); Stat("Wanted", counts.wanted, Modifier.weight(1f)); Stat("Lent out", counts.lent, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CollectionsScreen(catalog: Catalog, open: (Collection) -> Unit, create: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(22.dp, 12.dp, 22.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Your collections", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Everything you keep, in its place.", color = Muted) }
        if (catalog.collections.isEmpty()) item { EmptyState(Icons.Outlined.GridView, "Start with a shelf", "Give your collection a name. You can keep different kinds of items together.", "New collection", create) }
        items(catalog.collections, key = { it.id }) { collection ->
            OutlinedCard(onClick = { open(collection) }, colors = CardDefaults.outlinedCardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(ShelfIcon, null, tint = Violet)
                        Spacer(Modifier.width(12.dp)); Text(collection.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ChevronRight, "Open")
                    }
                    Spacer(Modifier.height(12.dp))
                    val counts = catalog.counts(collection.id)
                    Text("${counts.owned} owned · ${counts.wanted} wanted · ${counts.lent} lent out", color = Muted)
                    if (counts.owned + counts.wanted > 0) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { counts.owned.toFloat() / (counts.owned + counts.wanted) }, modifier = Modifier.fillMaxWidth(), color = Violet, trackColor = Line)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemsScreen(catalog: Catalog, collection: Collection?, wishlist: Boolean, query: String, onQuery: (String) -> Unit, open: (CatalogItem) -> Unit, add: () -> Unit, photo: (String?) -> File?) {
    var filterName by rememberSaveable(collection?.id, wishlist) { mutableStateOf("All") }
    val status = if (wishlist) ItemStatus.WANTED else ItemStatus.entries.find { it.label == filterName }
    val results = catalog.search(query, collection?.id, status)
    LazyColumn(contentPadding = PaddingValues(22.dp, 12.dp, 22.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(if (wishlist) "The next find" else collection?.name ?: "All items", style = MaterialTheme.typography.headlineLarge) }
        item { Text(if (wishlist) "A little room for what’s still out there." else "${catalog.counts(collection?.id).owned} owned · ${catalog.counts(collection?.id).wanted} wanted", color = Muted) }
        item { SearchField(query, onQuery) }
        if (!wishlist) item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All") + ItemStatus.entries.map { it.label }) { label -> FilterChip(selected = filterName == label, onClick = { filterName = label }, label = { Text(label) }) }
            }
        }
        item { Text("${results.size} ${if (results.size == 1) "item" else "items"}", color = Muted) }
        if (results.isEmpty()) item { EmptyState(Icons.Outlined.Search, "Nothing here yet", if (query.isNotBlank()) "Try another search or filter." else "Save your next find and make this shelf your own.", "Add an item", add) }
        items(results.chunked(2), key = { it.first().id }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { item -> Column(Modifier.weight(1f).clickable { open(item) }) {
                    Cover(item, photo(item.photoName), Modifier.fillMaxWidth().aspectRatio(.74f))
                    Spacer(Modifier.height(10.dp))
                    Text(item.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.creator.isNotBlank()) Text(item.creator, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(7.dp)); StatusPill(item.status)
                } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun Cover(item: CatalogItem, photo: File?, modifier: Modifier = Modifier) {
    val palette = listOf(Color(0xFF305F4E), Color(0xFFBA613C), Color(0xFF3C5F83), Color(0xFF8E6894), Color(0xFF9B7933))
    val base = palette[item.title.hashCode().mod(palette.size)]
    Surface(modifier, shape = RoundedCornerShape(8.dp), shadowElevation = 4.dp, color = base) {
        if (photo?.exists() == true) AsyncImage(photo, contentDescription = "Cover of ${item.title}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else BoxWithConstraints(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(base, base.copy(red = base.red * .65f, green = base.green * .65f, blue = base.blue * .65f))))) {
            val compact = maxHeight < 160.dp
            Box(Modifier.fillMaxHeight().width(6.dp).background(Color.Black.copy(alpha = .14f)))
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(item.kind.uppercase(), color = Color.White.copy(alpha = .7f), fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 2.sp, maxLines = 1)
                Text(item.title, fontFamily = FontFamily.Serif, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 16.sp else 20.sp, lineHeight = if (compact) 18.sp else 24.sp, maxLines = if (compact) 3 else 4, overflow = TextOverflow.Ellipsis)
                Text(item.creator.ifBlank { "HODD" }, color = Color.White.copy(alpha = .8f), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Shelf(items: List<CatalogItem>, photo: (String?) -> File?, open: (CatalogItem) -> Unit) {
    // Fixed height, flexible width: cover artwork is never cropped to fill a slot.
    Box(
        Modifier.fillMaxWidth().height(222.dp)
            .background(Color(0xFFF3EFE8), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Icon(ShelfIcon, null, tint = Color(0xFF9A896F), modifier = Modifier.size(48.dp))
                Text("Room for your first find", color = Muted)
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 17.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                items(items, key = { it.id }) { item ->
                    Surface(
                        modifier = Modifier.width(112.dp).height(176.dp).clickable { open(item) },
                        shape = RoundedCornerShape(7.dp),
                        color = Color.White,
                        shadowElevation = 3.dp,
                    ) {
                        if (photo(item.photoName)?.exists() == true) {
                            AsyncImage(
                                model = photo(item.photoName),
                                contentDescription = "Open ${item.title}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Cover(item, null, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        // Slim, understated lip instead of the thick faux-wood block.
        Box(
            Modifier.fillMaxWidth().height(9.dp).padding(horizontal = 8.dp)
                .background(Color(0xFFCBB79B), RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun ItemRow(item: CatalogItem, photo: File?, open: () -> Unit) {
    OutlinedCard(onClick = open, colors = CardDefaults.outlinedCardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item, photo, Modifier.width(80.dp).height(112.dp))
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.creator.isNotBlank()) Text(item.creator, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(item.kind, item.format).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall)
                StatusPill(item.status)
            }
        }
    }
}

@Composable
internal fun StatusPill(status: ItemStatus) {
    val color = when (status) { ItemStatus.OWNED -> Color(0xFF226043); ItemStatus.WANTED -> Violet; ItemStatus.LENT -> Color(0xFF96641E) }
    Surface(color = color.copy(alpha = .09f), contentColor = color, shape = RoundedCornerShape(30.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(when (status) { ItemStatus.OWNED -> Icons.Outlined.CheckCircle; ItemStatus.WANTED -> Icons.Outlined.FavoriteBorder; ItemStatus.LENT -> Icons.Outlined.Handshake }, null, Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp)); Text(status.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Stat(label: String, count: Int, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium); Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClick = onClick) { Text(action); Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp)) } }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), placeholder = { Text("Search your collection") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Clear search") } }, singleLine = true, shape = RoundedCornerShape(20.dp))
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Violet, modifier = Modifier.size(42.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = Muted)
        Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
private fun NewCollectionDialog(busy: Boolean, names: List<String>, dismiss: () -> Unit, save: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val duplicate = name.isNotBlank() && names.any { it.equals(name.trim(), ignoreCase = true) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Make room for a collection", style = MaterialTheme.typography.titleLarge) }, text = { OutlinedTextField(name, { name = it }, label = { Text("Collection name") }, placeholder = { Text("Books, games, vinyl…") }, singleLine = true, isError = duplicate, supportingText = { if (duplicate) Text("A collection with this name already exists.") }) }, confirmButton = { TextButton(onClick = { save(name) }, enabled = name.isNotBlank() && !duplicate && !busy) { Text("Create") } }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } })
}
