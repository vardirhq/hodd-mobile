package no.vardir.hodd.core

import kotlin.test.*
import org.junit.Test

class ItemSuggestionsTest {
    @Test fun validatesIdentifiersWithoutLosingLeadingZeroes() {
        assertEquals("0012345678905", cleanBarcode("00 1234567890-5"))
        assertTrue(isIsbn("978-0-14-032872-1"))
        assertTrue(isIsbn("0-306-40615-2"))
        assertTrue(isGtin("0035627405723"))
        assertFalse(isIsbn("0035627405723"))
        assertFalse(isIsbn("9780140328722"))
        assertFalse(isGtin("9780140328722"))
    }

    @Test fun parsesBookWorkWithoutInventingEditionOrBarcode() {
        val result = MetadataParser.books("""{"docs":[{"key":"/works/OL123W","title":"Example","author_name":["Chris"],"cover_i":123,"first_publish_year":1990}]}""").single()
        assertEquals("Chris", result.creator)
        assertEquals("", result.format)
        assertEquals("", result.barcode)
        assertTrue(result.coverUrl.startsWith("https://covers.openlibrary.org/"))
        assertEquals(result, SuggestionCodec.decode(SuggestionCodec.encode(listOf(result))).single())
    }

    @Test fun parsesMusicCreditsAndDistinctPhysicalRelease() {
        val result = MetadataParser.music("""{"releases":[{"id":"12345678-1234-1234-1234-123456789abc","title":"Album","barcode":"0035627405723","artist-credit":[{"name":"Artist A","joinphrase":" & "},{"artist":{"name":"Artist B"}}],"media":[{"format":"CD"}],"country":"NO"}]}""", "CD").single()
        assertEquals("Artist A & Artist B", result.creator)
        assertEquals("CD", result.format)
        assertEquals("0035627405723", result.barcode)
        assertTrue(result.sourceUrl.endsWith(result.sourceId))
    }

    @Test fun ignoresMalformedProviderEntries() {
        assertTrue(MetadataParser.books("""{"docs":[null,{"title":"No ID"},{"title":"Bad ID","key":"https://elsewhere"}]}""").isEmpty())
        assertTrue(MetadataParser.music("""{"releases":[{"id":"no","title":"Bad"}]}""", "Vinyl").isEmpty())
    }

    @Test fun untrustedAiCannotSupplyRemoteArtworkOrInvalidIdentifiers() {
        val suggestion = SuggestionCodec.fromLocalAi("""```json
            {"title":"The Hobbit","kind":"Book","coverUrl":"https://untrusted.invalid","status":"LENT"}
            ```""", "Other")
        assertEquals("", suggestion.coverUrl)
        assertEquals("", suggestion.status)
        assertFails { SuggestionCodec.fromLocalAi("""{"title":"Book","barcode":"1234567890123"}""", "Book") }
        assertFails { SuggestionCodec.fromLocalAi("""{"title":""}""", "Book") }
    }

    @Test fun keepsUserEditsUnlessReplacementWasExplicit() {
        val item = CatalogItem(collectionId = "shelf", title = "My title", creator = "", kind = "Book", photoName = "mine.jpg")
        val suggestion = ItemSuggestion("Fetched title", "Author", "Game", "Paperback", source = "Open Library", sourceId = "/works/OL123W")
        val merged = item.withSuggestion(suggestion)
        assertEquals("My title", merged.title)
        assertEquals("Author", merged.creator)
        assertEquals("Book", merged.kind)
        assertEquals("mine.jpg", merged.photoName)
        assertEquals("Fetched title", item.withSuggestion(suggestion, replace = true).title)
    }

    @Test fun localDescriptionCanSuggestLoanWithoutChangingIdentity() {
        val item = CatalogItem(id = "draft", collectionId = "shelf", title = "", addedAt = 123)
        val suggestion = SuggestionCodec.fromLocalAi("""{"title":"The Hobbit","creator":"Tolkien","status":"LENT","lentTo":"Martin","format":"Paperback"}""", "Book")
        val result = item.withSuggestion(suggestion)
        assertEquals(ItemStatus.LENT, result.status)
        assertEquals("Martin", result.lentTo)
        assertEquals("draft", result.id)
        assertEquals("shelf", result.collectionId)
        assertEquals(123L, result.addedAt)
    }

    @Test fun expandedMetadataSurvivesRoundTripAndMergesSafely() {
        val old = CatalogCodec.decode("""{"version":1,"collections":[{"id":"s","name":"Shelf"}],"items":[{"id":"i","collectionId":"s","title":"Example"}]}""")
        assertEquals("", old.items.single().subtitle)
        val enriched = old.items.single().copy(series = "Series A", attributes = mapOf("platform" to "PC"))
        val saved = CatalogCodec.decode(CatalogCodec.encode(old.copy(items = listOf(enriched))))
        assertEquals("Series A", saved.items.single().series)
        assertEquals("PC", saved.items.single().attributes["platform"])
        val suggestion = SuggestionCodec.fromLocalAi("""{"title":"Example","subtitle":"A subtitle","series":"Series B","publisher":"Publisher"}""", "Book")
        val merged = saved.items.single().withSuggestion(suggestion)
        assertEquals("Series A", merged.series)
        assertEquals("A subtitle", merged.subtitle)
        assertEquals("Publisher", merged.publisher)
        assertEquals("Series B", merged.withSuggestion(suggestion, replace = true).series)
    }

    @Test fun oldSavedCatalogsRemainReadableWithNewSourceFields() {
        val catalog = CatalogCodec.decode("""{"version":1,"collections":[{"id":"a","name":"Shelf"}],"items":[{"id":"b","collectionId":"a","title":"Book"}]}""")
        assertEquals("", catalog.items.single().metadataId)
        val updated = catalog.copy(items = listOf(catalog.items.single().copy(metadataSource = "Open Library", metadataId = "/works/OL1W")))
        assertEquals(updated, CatalogCodec.decode(CatalogCodec.encode(updated)))
    }
}
