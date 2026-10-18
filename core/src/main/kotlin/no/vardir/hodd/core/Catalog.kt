package no.vardir.hodd.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class ItemStatus(val label: String) { OWNED("Owned"), WANTED("Wanted"), LENT("Lent out") }

@Serializable
data class Collection(val id: String = UUID.randomUUID().toString(), val name: String)

@Serializable
data class CatalogItem(
    val id: String = UUID.randomUUID().toString(),
    val collectionId: String,
    val title: String,
    val creator: String = "",
    val kind: String = "Other",
    val format: String = "",
    val barcode: String = "",
    val status: ItemStatus = ItemStatus.OWNED,
    val notes: String = "",
    val lentTo: String = "",
    val photoName: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Catalog(val version: Int = 1, val collections: List<Collection> = emptyList(), val items: List<CatalogItem> = emptyList()) {
    fun addCollection(name: String): Catalog {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Give your collection a name." }
        require(collections.none { it.name.equals(clean, ignoreCase = true) }) { "A collection with that name already exists." }
        return copy(collections = collections + Collection(name = clean))
    }

    fun save(item: CatalogItem): Catalog {
        val clean = item.copy(title = item.title.trim(), creator = item.creator.trim(), barcode = item.barcode.trim(), lentTo = item.lentTo.trim())
        require(clean.title.isNotEmpty()) { "Add a title before saving." }
        require(collections.any { it.id == clean.collectionId }) { "Choose a collection." }
        require(clean.status != ItemStatus.LENT || clean.lentTo.isNotEmpty()) { "Who did you lend this to?" }
        require(clean.barcode.isEmpty() || items.none { it.id != clean.id && it.collectionId == clean.collectionId && it.barcode == clean.barcode }) {
            "This barcode is already in this collection. Open the existing item to edit it."
        }
        return copy(items = if (items.any { it.id == clean.id }) items.map { if (it.id == clean.id) clean else it } else items + clean)
    }

    fun search(query: String = "", collectionId: String? = null, status: ItemStatus? = null): List<CatalogItem> {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        return items.filter { item ->
            (collectionId == null || item.collectionId == collectionId) && (status == null || item.status == status) &&
                terms.all { term -> listOf(item.title, item.creator, item.barcode, item.kind, item.format, item.notes).any { it.contains(term, ignoreCase = true) } }
        }.sortedByDescending { it.addedAt }
    }

    fun removeItem(id: String) = copy(items = items.filterNot { it.id == id })
    fun counts(collectionId: String? = null): Counts {
        val selected = items.filter { collectionId == null || it.collectionId == collectionId }
        return Counts(selected.count { it.status != ItemStatus.WANTED }, selected.count { it.status == ItemStatus.WANTED }, selected.count { it.status == ItemStatus.LENT })
    }
}

data class Counts(val owned: Int, val wanted: Int, val lent: Int)

object CatalogCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(catalog: Catalog): String = json.encodeToString(Catalog.serializer(), catalog)
    fun decode(text: String): Catalog {
        val catalog = json.decodeFromString(Catalog.serializer(), text)
        require(catalog.version == 1) { "This collection was saved by a newer version of Hodd." }
        require(catalog.collections.map { it.id }.distinct().size == catalog.collections.size) { "Duplicate collection IDs." }
        require(catalog.items.map { it.id }.distinct().size == catalog.items.size) { "Duplicate item IDs." }
        catalog.items.forEach {
            require(it.photoName == null || Regex("[a-zA-Z0-9_-]+\\.(jpg|png|webp)").matches(it.photoName)) { "Invalid photo filename." }
            require(catalog.collections.any { collection -> collection.id == it.collectionId }) { "Item without collection." }
        }
        return catalog
    }
}

fun exampleCatalog(): Catalog {
    val books = Collection(id = "example-books", name = "The reading shelf")
    val games = Collection(id = "example-games", name = "Games to remember")
    return Catalog(collections = listOf(books, games), items = listOf(
        CatalogItem(id = "example-1", collectionId = books.id, title = "Northern trails", creator = "Alex Berg", kind = "Book", format = "Hardback", addedAt = 3),
        CatalogItem(id = "example-2", collectionId = books.id, title = "A quieter world", creator = "Robin Dale", kind = "Book", status = ItemStatus.WANTED, addedAt = 2),
        CatalogItem(id = "example-3", collectionId = games.id, title = "Orbit explorer", kind = "Game", format = "Cartridge", addedAt = 1),
    ))
}
