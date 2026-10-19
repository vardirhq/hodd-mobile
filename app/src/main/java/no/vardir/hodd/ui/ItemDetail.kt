package no.vardir.hodd.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.vardir.hodd.core.CatalogItem
import no.vardir.hodd.core.ItemStatus
import java.io.File

private data class DetailFact(val label: String, val value: String)
private val DetailGreen = Color(0xFF365B4A)
private val DetailGold = Color(0xFF8B652B)

@Composable
internal fun ItemDetail(item: CatalogItem, collection: String, photo: File?, edit: () -> Unit, delete: () -> Unit, busy: Boolean) {
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }
    var identifiersOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    val attributeLabels = mapOf(
        "isbn" to "ISBN", "pages" to "Pages", "binding" to "Binding", "platform" to "Platform",
        "platformBrand" to "Platform brand", "developer" to "Developer", "region" to "Region", "edition" to "Edition", "director" to "Director",
        "runtime" to "Runtime", "resolution" to "Resolution", "label" to "Record label",
        "catalogNumber" to "Catalog number", "tracks" to "Tracks", "country" to "Country",
        "denomination" to "Denomination", "mintYear" to "Mint year", "mintMark" to "Mint mark", "material" to "Material",
    )
    val highlights = listOf(
        DetailFact("Platform", item.attributes["platform"].orEmpty()),
        DetailFact("Released", item.releaseYear), DetailFact("Language", item.language),
        DetailFact("Condition", item.condition),
    ).filter { it.value.isNotBlank() }
    val edition = listOf(
        DetailFact("Subtitle", item.subtitle), DetailFact("Series", item.series), DetailFact("Volume", item.volume),
        DetailFact(if (item.kind in listOf("Vinyl", "CD")) "Publisher / label" else "Publisher", item.publisher),
    ).filter { it.value.isNotBlank() } + item.attributes.toSortedMap().filterKeys { it != "platform" && it != "isbn" }
        .filterValues { it.isNotBlank() }.map { (key, value) ->
            DetailFact(attributeLabels[key] ?: key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }, value)
        }
    val hasIdentifiers = item.barcode.isNotBlank() || item.attributes["isbn"].orEmpty().isNotBlank() || item.metadataSource.isNotBlank() || item.metadataId.isNotBlank() || item.metadataUrl.isNotBlank()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(color = Paper, shadowElevation = 8.dp) {
                Column {
                    HorizontalDivider(color = Line)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = edit, enabled = !busy, shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp).testTag("detail-edit")) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp)); Text("Edit item", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedIconButton(onClick = { confirmDelete = true }, enabled = !busy,
                            shape = RoundedCornerShape(18.dp), modifier = Modifier.size(54.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, "Delete item", tint = Muted)
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("item-detail-scroll"),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item { DetailCoverStage(item, collection, photo) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(listOf(item.kind, item.format).filter { it.isNotBlank() }.joinToString("  ·  ").uppercase(),
                        color = DetailGreen, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp)
                    Text(item.title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 31.sp, lineHeight = 35.sp),
                        modifier = Modifier.testTag("item-title"))
                    if (item.creator.isNotBlank()) Text(item.creator, style = MaterialTheme.typography.bodyLarge, color = Muted)
                }
            }
            if (item.status == ItemStatus.LENT) item {
                DetailStatusCard(Icons.Outlined.Handshake, "ON LOAN", item.lentTo.ifBlank { "Borrower not recorded" },
                    "Currently lent out", Color(0xFFF2E9D7), DetailGold)
            }
            if (item.status == ItemStatus.WANTED) item {
                DetailStatusCard(Icons.Outlined.FavoriteBorder, "ON YOUR WISHLIST", "A future find",
                    "Saved for your collection", Color(0xFFEEE9FC), Violet)
            }
            if (highlights.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailSectionHeading("At a glance")
                    Surface(shape = RoundedCornerShape(22.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            highlights.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    pair.forEach { fact -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(fact.label, style = MaterialTheme.typography.labelMedium, color = Muted)
                                        Text(fact.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Ink)
                                    } }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            if (edition.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailSectionHeading("About this edition")
                    Surface(shape = RoundedCornerShape(22.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                            edition.forEachIndexed { index, fact ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(fact.label, style = MaterialTheme.typography.labelMedium, color = Muted)
                                    Text(fact.value, style = MaterialTheme.typography.bodyLarge, color = Ink)
                                }
                                if (index < edition.lastIndex) HorizontalDivider(color = Line.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
            if (item.notes.isNotBlank()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailSectionHeading("Your notes")
                    Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFF0ECE5)) {
                        SelectionContainer { Text(item.notes, Modifier.fillMaxWidth().padding(18.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp), color = Ink) }
                    }
                }
            }
            if (hasIdentifiers) item {
                Column {
                    TextButton(onClick = { identifiersOpen = !identifiersOpen }, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Fingerprint, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                        Text("Identifiers & source", Modifier.weight(1f))
                        Icon(if (identifiersOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                    AnimatedVisibility(identifiersOpen) {
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val isbn = item.attributes["isbn"].orEmpty()
                            if (item.barcode.isNotBlank()) IdentifierText("EAN / ISBN", item.barcode)
                            if (isbn.isNotBlank() && isbn != item.barcode) IdentifierText("ISBN", isbn)
                            if (item.metadataSource.isNotBlank()) IdentifierText("Metadata source", item.metadataSource)
                            if (item.metadataId.isNotBlank()) IdentifierText("Source ID", item.metadataId)
                            if (item.metadataUrl.isNotBlank()) IdentifierText("Source link", item.metadataUrl)
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text("Delete ${item.title}?") }, text = { Text("This removes the item from your collection. This cannot be undone.") },
        confirmButton = { TextButton(onClick = delete, enabled = !busy) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text("Keep item") } })
}

@Composable
private fun DetailCoverStage(item: CatalogItem, collection: String, photo: File?) {
    Box(Modifier.fillMaxWidth().height(235.dp).clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFFE6E2D5), Color(0xFFF3EEE5), Color(0xFFE4E9DF))))
        .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(28.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(DetailGreen.copy(alpha = 0.05f), radius = size.width * 0.46f, center = Offset(size.width * 0.91f, size.height * 0.15f))
            drawCircle(Color.White.copy(alpha = 0.35f), radius = size.width * 0.4f, center = Offset(size.width * 0.05f, size.height * 1.1f))
        }
        Icon(when (item.kind) {
            "Game" -> Icons.Outlined.SportsEsports
            "Movie" -> Icons.Outlined.Movie
            "Vinyl", "CD" -> Icons.Outlined.Album
            "Coin" -> Icons.Outlined.Paid
            else -> Icons.Outlined.AutoStories
        }, null, tint = DetailGreen.copy(alpha = 0.06f), modifier = Modifier.align(Alignment.CenterEnd).offset(x = 28.dp).size(180.dp).rotate(12f))
        Cover(item, photo, Modifier.align(Alignment.Center).offset(y = (-6).dp).width(114.dp).height(157.dp).rotate(-4f))
        Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { StatusPill(item.status) }
        Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ShelfIcon, null, tint = DetailGreen, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(collection, color = DetailGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetailSectionHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
}

@Composable
private fun DetailStatusCard(icon: ImageVector, label: String, title: String, description: String, background: Color, accent: Color) {
    Surface(shape = RoundedCornerShape(22.dp), color = background) {
        Row(Modifier.fillMaxWidth().padding(17.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.6f)) {
                Icon(icon, null, tint = accent, modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = accent, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.1.sp)
                Text(title, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, color = accent, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun IdentifierText(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink) }
    }
}
