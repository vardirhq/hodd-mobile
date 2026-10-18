package no.vardir.hodd.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class ItemSuggestion(
    val title: String,
    val creator: String = "",
    val kind: String = "",
    val format: String = "",
    val barcode: String = "",
    val notes: String = "",
    val status: String = "",
    val lentTo: String = "",
    val source: String = "",
    val sourceId: String = "",
    val sourceUrl: String = "",
    val coverUrl: String = "",
    val detail: String = "",
    val subtitle: String = "",
    val series: String = "",
    val volume: String = "",
    val publisher: String = "",
    val language: String = "",
    val releaseYear: String = "",
    val condition: String = "",
    val attributes: Map<String, String> = emptyMap(),
)

/** Keeps leading zeroes; never interprets a product identifier as a number. */
fun cleanBarcode(value: String): String = value.filterNot { it.isWhitespace() || it == '-' }.uppercase()

fun isIsbn(value: String): Boolean {
    val code = cleanBarcode(value)
    if (code.length == 10 && code.take(9).all(Char::isDigit) && (code.last().isDigit() || code.last() == 'X')) {
        return code.mapIndexed { i, c -> (10 - i) * (if (c == 'X') 10 else c.digitToInt()) }.sum() % 11 == 0
    }
    return code.length == 13 && (code.startsWith("978") || code.startsWith("979")) && isGtin(code)
}

fun isGtin(value: String): Boolean {
    val code = cleanBarcode(value)
    if (code.length !in listOf(8, 12, 13, 14) || !code.all { it in '0'..'9' }) return false
    return code.reversed().mapIndexed { index, c -> c.digitToInt() * if (index % 2 == 0) 1 else 3 }.sum() % 10 == 0
}

object SuggestionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(items: List<ItemSuggestion>): String = json.encodeToString(items)
    fun decode(text: String): List<ItemSuggestion> = json.decodeFromString(text)

    fun fromLocalAi(text: String, defaultKind: String): ItemSuggestion {
        require(text.length <= 12000) { "The local AI response was too long." }
        val raw = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = json.parseToJsonElement(raw).jsonObject
        fun field(key: String, limit: Int = 300): String {
            val value = (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            require(value.length <= limit) { "The local AI response contained an oversized field." }
            return value
        }
        val title = field("title")
        require(title.isNotBlank()) { "The local AI could not identify a title. Try clearer text or a cover photo." }
        val kind = field("kind").takeIf { it in listOf("Book", "Comic", "Game", "Movie", "Vinyl", "CD", "Coin", "Other") } ?: defaultKind
        val barcode = cleanBarcode(field("barcode"))
        require(barcode.isEmpty() || isIsbn(barcode) || isGtin(barcode)) { "The suggested barcode was invalid. Enter it manually." }
        val borrower = field("lentTo")
        val status = field("status").takeIf { it in listOf("OWNED", "WANTED", "LENT") && (it != "LENT" || borrower.isNotBlank()) }.orEmpty()
        return ItemSuggestion(title, field("creator"), kind, field("format"), barcode, field("notes", 2000), status, borrower, source = "On-device AI",
            subtitle = field("subtitle"), series = field("series"), volume = field("volume"), publisher = field("publisher"),
            language = field("language"), releaseYear = field("releaseYear"), condition = field("condition"))
    }
}

/** Suggestions cannot silently replace edits or move an item between collections. */
fun CatalogItem.withSuggestion(suggestion: ItemSuggestion, replace: Boolean = false): CatalogItem {
    fun merge(current: String, proposed: String) = if (proposed.isNotBlank() && (replace || current.isBlank())) proposed else current
    val nextStatus = if (replace || title.isBlank()) ItemStatus.entries.find { it.name == suggestion.status } ?: status else status
    val borrower = merge(lentTo, suggestion.lentTo)
    return copy(
        title = merge(title, suggestion.title), creator = merge(creator, suggestion.creator),
        kind = if (replace || title.isBlank()) suggestion.kind.ifBlank { kind } else kind,
        format = merge(format, suggestion.format), barcode = merge(barcode, suggestion.barcode),
        notes = merge(notes, suggestion.notes),
        status = if (nextStatus == ItemStatus.LENT && borrower.isBlank()) status else nextStatus,
        lentTo = borrower,
        subtitle = merge(subtitle, suggestion.subtitle), series = merge(series, suggestion.series),
        volume = merge(volume, suggestion.volume), publisher = merge(publisher, suggestion.publisher),
        language = merge(language, suggestion.language), releaseYear = merge(releaseYear, suggestion.releaseYear),
        condition = merge(condition, suggestion.condition),
        attributes = attributes.toMutableMap().apply { suggestion.attributes.forEach { (key, value) -> if (value.isNotBlank() && (replace || this[key].isNullOrBlank())) this[key] = value } },
        metadataSource = suggestion.source.takeIf { it in listOf("Open Library", "MusicBrainz") } ?: metadataSource,
        metadataId = suggestion.sourceId.ifBlank { metadataId }, metadataUrl = suggestion.sourceUrl.ifBlank { metadataUrl },
    )
}

object MetadataParser {
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())

    fun books(text: String, barcode: String = ""): List<ItemSuggestion> = Json.parseToJsonElement(text).jsonObject.array("docs").mapNotNull { element ->
        val book = element as? JsonObject ?: return@mapNotNull null
        val title = book.text("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val key = book.text("key").takeIf { Regex("/works/OL[0-9]+W").matches(it) } ?: return@mapNotNull null
        val cover = book.text("cover_i").toLongOrNull()?.takeIf { it > 0 }
        ItemSuggestion(title = title, creator = book.array("author_name").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", "),
            kind = "Book", barcode = barcode, source = "Open Library", sourceId = key,
            sourceUrl = "https://openlibrary.org$key", coverUrl = cover?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg?default=false" }.orEmpty(),
            detail = listOf(book.text("first_publish_year"), "Work match; check your edition").filter(String::isNotBlank).joinToString(" · "))
    }.take(8)

    fun music(text: String, kind: String): List<ItemSuggestion> = Json.parseToJsonElement(text).jsonObject.array("releases").mapNotNull { element ->
        val release = element as? JsonObject ?: return@mapNotNull null
        val id = release.text("id").takeIf { Regex("[a-fA-F0-9-]{36}").matches(it) } ?: return@mapNotNull null
        val title = release.text("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val artist = release.array("artist-credit").mapNotNull { it as? JsonObject }.joinToString("") { credit ->
            credit.text("name").ifBlank { (credit["artist"] as? JsonObject)?.text("name").orEmpty() } + credit.text("joinphrase")
        }
        val formats = release.array("media").mapNotNull { (it as? JsonObject)?.text("format")?.takeIf(String::isNotBlank) }.distinct()
        val barcode = cleanBarcode(release.text("barcode")).takeIf(::isGtin).orEmpty()
        ItemSuggestion(title = title, creator = artist, kind = kind, format = formats.joinToString(", "), barcode = barcode,
            source = "MusicBrainz", sourceId = id, sourceUrl = "https://musicbrainz.org/release/$id",
            coverUrl = "https://coverartarchive.org/release/$id/front-250",
            detail = listOf(release.text("date"), release.text("country"), release.text("disambiguation")).filter(String::isNotBlank).joinToString(" · "))
    }.take(8)
}
