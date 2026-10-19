package no.vardir.hodd.core

import kotlin.test.*

class CatalogTest {
    private val shelf = Collection("books", "Books")
    private val empty = Catalog(collections = listOf(shelf))
    private fun item(id: String = "one", title: String = "Northern trails", status: ItemStatus = ItemStatus.OWNED) = CatalogItem(id = id, collectionId = shelf.id, title = title, creator = "Alex Berg", kind = "Book", status = status)

    @Test fun `collection names are trimmed and unique without regard to case`() {
        assertEquals("Books", Catalog().addCollection(" Books ").collections.single().name)
        assertFailsWith<IllegalArgumentException> { empty.addCollection("books") }
        assertFailsWith<IllegalArgumentException> { empty.addCollection("  ") }
    }
    @Test fun `blank titles and missing collections cannot be saved`() {
        assertFailsWith<IllegalArgumentException> { empty.save(item(title = "  ")) }
        assertFailsWith<IllegalArgumentException> { Catalog().save(item()) }
    }
    @Test fun `save updates an existing item without duplicating it or its date`() {
        val original = item().copy(addedAt = 123)
        val updated = empty.save(original).save(original.copy(title = " New title "))
        assertEquals(1, updated.items.size)
        assertEquals("New title", updated.items.single().title)
        assertEquals(123L, updated.items.single().addedAt)
    }
    @Test fun `barcode duplicate within a collection is rejected but original can be edited`() {
        val original = item().copy(barcode = " 12345 ")
        val catalog = empty.save(original)
        assertEquals("12345", catalog.items.single().barcode)
        assertFailsWith<IllegalArgumentException> { catalog.save(item(id = "two").copy(barcode = "12345")) }
        assertEquals(1, catalog.save(original).items.size)
    }
    @Test fun `search requires all words and searches metadata case insensitively`() {
        val catalog = empty.save(item().copy(barcode = "978123", notes = "Signed copy"))
        assertEquals(1, catalog.search("  NORTHERN  berg ").size)
        assertEquals(1, catalog.search("978123").size)
        assertEquals(1, catalog.search("signed").size)
        assertTrue(catalog.search("northern missing").isEmpty())
    }
    @Test fun `wishlist filtering does not include lent or owned items`() {
        val catalog = empty.save(item()).save(item("two", status = ItemStatus.WANTED)).save(item("three", status = ItemStatus.LENT).copy(lentTo = "Sam"))
        assertEquals(listOf("two"), catalog.search(status = ItemStatus.WANTED).map { it.id })
        assertEquals(Counts(2, 1, 1), catalog.counts())
    }
    @Test fun `loans require a borrower`() {
        assertFailsWith<IllegalArgumentException> { empty.save(item(status = ItemStatus.LENT)) }
    }
    @Test fun `deletion only removes the selected item`() {
        val catalog = empty.save(item()).save(item("two"))
        assertEquals(listOf("two"), catalog.removeItem("one").items.map { it.id })
        assertEquals(listOf(shelf), catalog.removeItem("one").collections)
    }
    @Test fun `serialized collection roundtrips all item details`() {
        val catalog = empty.save(item().copy(photoName = "cover-123.jpg", notes = "Ønskeliste\nA quote: \"yes\""))
        assertEquals(catalog, CatalogCodec.decode(CatalogCodec.encode(catalog)))
    }
    @Test fun `snapshots explicitly store defaults so restore cannot regenerate timestamps`() {
        val original = empty.save(item())
        val encoded = CatalogCodec.encode(original)
        assertContains(encoded, "\"addedAt\"")
        assertContains(encoded, "\"notes\"")
        assertContains(encoded, "\"status\"")
        assertContains(encoded, "\"version\"")
        assertEquals(original, CatalogCodec.decode(encoded))
    }
    @Test fun `unsupported versions and unsafe photo paths fail without returning an empty catalog`() {
        assertFailsWith<IllegalArgumentException> { CatalogCodec.decode("{\"version\":2}") }
        assertFails { CatalogCodec.decode("not json") }
        assertFailsWith<IllegalArgumentException> { CatalogCodec.decode(CatalogCodec.encode(empty.save(item().copy(photoName = "../catalog.json")))) }
    }
    @Test fun `broken references and duplicate ids cannot load`() {
        assertFailsWith<IllegalArgumentException> { CatalogCodec.decode(CatalogCodec.encode(Catalog(items = listOf(item())))) }
        assertFailsWith<IllegalArgumentException> { CatalogCodec.decode(CatalogCodec.encode(empty.copy(items = listOf(item(), item())))) }
    }
    @Test fun `examples are optional and contain real relationships`() {
        val demo = exampleCatalog()
        assertEquals(demo, CatalogCodec.decode(CatalogCodec.encode(demo)))
        assertEquals(Counts(2, 1, 0), demo.counts())
        assertTrue(Catalog().items.isEmpty())
    }

    @Test fun `tags and locations normalize and survive catalog roundtrip`() {
        val catalog = empty.save(item().copy(tags = listOf(" Co-op ", "co-op", "Sealed"), location = " Shelf A "))
        val restored = CatalogCodec.decode(CatalogCodec.encode(catalog))
        assertEquals(listOf("Co-op", "Sealed"), restored.items.single().tags)
        assertEquals("Shelf A", restored.items.single().location)
        assertEquals(1, restored.search("sealed").size)
        assertEquals(1, restored.search("shelf").size)
        assertFailsWith<IllegalArgumentException> { empty.save(item().copy(tags = List(31) { "tag$" + "it" })) }
    }

    @Test fun `saved views dynamically filter and survive serialization`() {
        val catalog = empty
            .save(item().copy(tags = listOf("Co-op"), location = "Living room"))
            .save(item("two").copy(tags = listOf("Solo"), location = "Attic"))
            .addSavedView("Living co-op", collectionId = shelf.id, tag = "co-op", location = "Living room")
        val restored = CatalogCodec.decode(CatalogCodec.encode(catalog))
        assertEquals(listOf("one"), restored.itemsForView(restored.savedViews.single()).map { it.id })
        val changed = restored.save(item().copy(tags = listOf("Solo"), location = "Living room"))
        assertTrue(changed.itemsForView(changed.savedViews.single()).isEmpty())
        assertTrue(changed.removeSavedView(changed.savedViews.single().id).savedViews.isEmpty())
    }
}
