package no.vardir.hodd.core

import org.junit.Assert.*
import org.junit.Test

class ItemFindingTest {
    @Test fun storeQueryRemovesSeparatedKnownPlatformButKeepsNumbersAndEditionWords() {
        assertEquals("Super Mario Maker 2", catalogSearchTitle("Super Mario Maker 2 - Nintendo Switch"))
        assertEquals("Super Mario Maker 2", catalogSearchTitle("Super Mario Maker 2"))
        assertEquals("Nintendo Switch Sports", catalogSearchTitle("Nintendo Switch Sports"))
        assertEquals("Control - Ultimate Edition", catalogSearchTitle("Control - Ultimate Edition"))
        assertEquals("Zelda: Tears of the Kingdom", catalogSearchTitle("Zelda: Tears of the Kingdom | Nintendo Switch"))
    }
    @Test fun firstReleaseIsRetainedAsWorkEvidenceWithoutDatingTheUsersPort() {
        val match = ItemSuggestion("A game", kind = "Game", source = "Wikidata", releaseYear = "2011",
            attributes = mapOf("platform" to "Windows", "platformBrand" to "PC", "developer" to "Studio"))
        val reviewed = match.forReviewedCopy("Nintendo Switch", "Standard", false)
        assertEquals("", reviewed.releaseYear)
        assertEquals("2011", reviewed.attributes["firstReleaseYear"])
        assertEquals("Nintendo Switch", reviewed.attributes["platform"])
        assertEquals("Nintendo", reviewed.attributes["platformBrand"])
        assertEquals("Standard", reviewed.attributes["edition"])
        val draft = CatalogItem(collectionId = "s", title = "", kind = "Game", photoName = "store.jpg", attributes = mapOf("storeUrl" to "https://shop.example/item"))
        val combined = draft.withSuggestion(reviewed)
        assertEquals("store.jpg", combined.photoName)
        assertEquals("https://shop.example/item", combined.attributes["storeUrl"])
        assertEquals("Studio", combined.attributes["developer"])
        assertEquals("Wikidata", combined.metadataSource)
        assertEquals("", combined.releaseYear)
        assertEquals("2011", match.forReviewedCopy("Nintendo Switch", "", true).releaseYear)
    }
    @Test fun unknownPlatformStaysBlankAndOtherMediaKeepTheirMetadata() {
        val game = ItemSuggestion("A game", kind = "Game", source = "Wikidata", attributes = mapOf("platform" to "Windows", "platformBrand" to "PC"))
        val reviewed = game.forReviewedCopy("", "", false)
        assertFalse(reviewed.attributes.containsKey("platform")); assertFalse(reviewed.attributes.containsKey("platformBrand"))
        val movie = game.copy(kind = "Movie", releaseYear = "2001")
        assertEquals(movie, movie.forReviewedCopy("", "", false))
    }
}
