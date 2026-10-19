package no.vardir.hodd.core

import kotlin.test.*

class FieldChoicesTest {
    @Test fun `platform search accepts common aliases and respects brand`() {
        assertEquals(listOf("PlayStation 5"), FieldChoices.search(FieldChoices.platformsFor("Sony"), "ps5").map { it.value })
        assertEquals(listOf("Super Nintendo Entertainment System"), FieldChoices.search(FieldChoices.platformsFor("Nintendo"), "snes").map { it.value })
        assertTrue(FieldChoices.platformsFor("Sony").none { it.value.contains("Switch") })
        assertTrue(FieldChoices.platformsFor("My console maker").isEmpty())
        assertTrue(FieldChoices.platformsFor("Nintendo").any { it.value == "Nintendo Switch 2" })
    }
    @Test fun `legacy and custom text survive without silent normalization`() {
        val old = "My special cartridge edition"
        val options = FieldChoices.withExisting(FieldChoices.formats("Game"), old, listOf(old, "Local release"))
        assertEquals(old, options.first().value)
        assertEquals(1, options.count { it.value == old })
        val item = CatalogItem(collectionId = "games", title = "Kept", format = old, attributes = mapOf("platform" to "PS5", "privateNote" to "Keep me"))
        val encoded = CatalogCodec.encode(Catalog(collections = listOf(Collection("games", "Games")), items = listOf(item)))
        assertEquals(item, CatalogCodec.decode(encoded).items.single())
        assertEquals("Sony", FieldChoices.brandFor("PS5"))
        assertNull(FieldChoices.brandFor("Unknown platform"))
    }
    @Test fun `saved options deduplicate while preserving labels and search aliases`() {
        val options = FieldChoices.withExisting(listOf(FieldChoice("shelf-id", "My books", "reading")), "shelf-id", listOf("shelf-id"))
        assertEquals("My books", options.single().label)
        assertEquals(options, FieldChoices.search(options, "reading"))
        assertEquals(1, FieldChoices.withExisting(FieldChoices.conditions, "Good", listOf("good", "GOOD")).count { it.value.equals("good", true) })
    }
    @Test fun `format and region choices depend on the category and medium`() {
        assertTrue(FieldChoices.formats("Book").any { it.value == "Paperback" })
        assertTrue(FieldChoices.formats("Game").none { it.value == "Paperback" })
        assertTrue(FieldChoices.regions("Movie", "DVD").any { it.value == "Region 2" })
        assertTrue(FieldChoices.regions("Movie", "Blu-ray").any { it.value == "Region B" })
        assertTrue(FieldChoices.regions("Movie", "Blu-ray").none { it.value == "Region 2" })
        assertTrue(FieldChoices.denominations("Norway").any { it.value == "20 kroner" })
        assertTrue(FieldChoices.denominations("United States").none { it.value == "20 kroner" })
    }
    @Test fun `search handles multiple words accents and language codes offline`() {
        assertTrue(FieldChoices.search(FieldChoices.languages, "bokmal").any { it.value == "Norwegian Bokmål" })
        assertTrue(FieldChoices.search(FieldChoices.languages, "nb").any { it.value == "Norwegian Bokmål" })
        assertEquals(listOf("United States"), FieldChoices.search(FieldChoices.countries, "us").filter { it.keywords == "US" }.map { it.value })
        assertTrue(FieldChoices.search(FieldChoices.brands, "nintendo snes").any { it.value == "Nintendo" })
    }
}
