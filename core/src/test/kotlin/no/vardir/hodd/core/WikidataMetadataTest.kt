package no.vardir.hodd.core

import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test

class WikidataMetadataTest {
    private fun statement(id: String, rank: String = "normal") = """{"rank":"$rank","mainsnak":{"datavalue":{"value":{"id":"$id"}}}}"""
    private fun entity(title: String, type: String = "Q7889", extra: String = ""): JsonObject = Json.parseToJsonElement("""
        {"labels":{"en":{"value":"$title"}},"claims":{"P31":[${statement(type)}]$extra}}
    """).jsonObject
    private fun data(title: String = "Super Mario Maker 2") = mapOf(
        "Q1" to entity(title, extra = """, "P178":[${statement("Q2")}],"P123":[${statement("Q3")}],"P400":[${statement("Q4")}],
            "P179":[${statement("Q5")}],"P577":[{"mainsnak":{"datavalue":{"value":{"time":"+2019-06-28T00:00:00Z","precision":11}}}}]"""),
        "Q2" to entity("Nintendo EPD"), "Q3" to entity("Nintendo"), "Q4" to entity("Nintendo Switch"), "Q5" to entity("Super Mario"))

    @Test fun canonicalTitleAndCatalogFactsDoNotNeedToAppearInOcr() {
        val result = WikidataMetadata.suggestions(listOf("Q1"), data(), "Game", "SUPER MARIO MAKER 2", "Nintendo Switch").single()
        assertEquals("Super Mario Maker 2", result.title)
        assertEquals("Nintendo EPD", result.attributes["developer"])
        assertEquals("Nintendo Switch", result.attributes["platform"])
        assertEquals("Nintendo", result.attributes["platformBrand"])
        assertEquals("2019", result.releaseYear)
        assertEquals("Nintendo", result.publisher)
        assertEquals("Super Mario", result.series)
        assertEquals("", result.barcode)
        assertEquals("", result.language)
        assertEquals("", result.format)
        assertEquals("", result.condition)
        assertEquals("Wikidata", CatalogItem(collectionId = "s", title = "").withSuggestion(result).metadataSource)
    }
    @Test fun rejectsOtherSequelAndWrongItemType() {
        assertTrue(WikidataMetadata.suggestions(listOf("Q1"), data("Super Mario Maker 3"), "Game", "Super Mario Maker 2").isEmpty())
        assertTrue(WikidataMetadata.suggestions(listOf("Q1"), data(), "Movie", "Super Mario Maker 2").isEmpty())
    }
    @Test fun platformEvidenceRejectsIncompatibleMatchButAllowsShortSwitchName() {
        assertTrue(WikidataMetadata.suggestions(listOf("Q1"), data(), "Game", "Super Mario Maker 2", "Nintendo Switch 2").isEmpty())
        assertEquals(1, WikidataMetadata.suggestions(listOf("Q1"), data(), "Game", "Super Mario Maker 2", "Switch").size)
    }
    @Test fun multiplePlatformsDoNotInventWhichEditionTheUserOwns() {
        val all = data().toMutableMap()
        all["Q1"] = entity("Mario", extra = """, "P400":[${statement("Q4")},${statement("Q6")}]""")
        all["Q6"] = entity("Wii U")
        val result = WikidataMetadata.suggestions(listOf("Q1"), all, "Game", "Mario").single()
        assertNull(result.attributes["platform"])
        assertTrue(result.detail.contains("Nintendo Switch, Wii U"))
    }
    @Test fun filmSubclassAndDirectorAreResolvedWithoutGuessingPhysicalMovieFormat() {
        val all = mapOf("Q1" to entity("Example Film", "Q2", """, "P57":[${statement("Q3")}]"""),
            "Q2" to entity("Animated film", extra = """, "P279":[${statement("Q11424")}]"""), "Q3" to entity("A Director"))
        val result = WikidataMetadata.suggestions(listOf("Q1"), all, "Movie", "Example Film").single()
        assertEquals("A Director", result.attributes["director"])
        assertEquals("", result.format)
        assertEquals("", result.language)
    }
    @Test fun ignoresDeprecatedClaimsAndUnknownDatesAndRanksExactTitleFirst() {
        val all = data().toMutableMap()
        all["Q6"] = entity("Super Mario Maker 2 Guide")
        all["Q1"] = entity("Super Mario Maker 2", extra = """, "P178":[${statement("Q2", "deprecated")}]""")
        val results = WikidataMetadata.suggestions(listOf("Q6", "Q1"), all, "Game", "Super Mario Maker 2")
        assertEquals("Q1", results.first().sourceId)
        assertEquals("", results.first().creator)
        assertEquals("", results.first().releaseYear)
    }
    @Test fun liveMarioFixtureHydratesRealLinkedFields() {
        val text = requireNotNull(javaClass.getResource("/wikidata/super-mario-maker-2.json")).readText()
        val result = WikidataMetadata.suggestions(listOf("Q61831178"), WikidataMetadata.entities(text), "Game", "SUPER MARIO MAKER 2", "Nintendo Switch").single()
        assertEquals("Super Mario Maker 2", result.title)
        assertEquals("Nintendo", result.publisher)
        assertEquals("Nintendo Switch", result.attributes["platform"])
        assertEquals("2019", result.releaseYear)
        assertTrue(result.attributes["developer"].orEmpty().contains("Nintendo"))
    }

    @Test fun acceptingCatalogMatchCorrectsCapitalizationWithoutReplacingOtherEdits() {
        val item = CatalogItem(collectionId = "s", title = "SUPER MARIO MAKER 2", publisher = "My publisher", notes = "My notes")
        val suggestion = WikidataMetadata.suggestions(listOf("Q1"), data(), "Game", item.title).single()
        val merged = item.withSuggestion(suggestion)
        assertEquals("Super Mario Maker 2", merged.title)
        assertEquals("My publisher", merged.publisher)
        assertEquals("My notes", merged.notes)
        assertEquals("My custom title", item.copy(title = "My custom title").withSuggestion(suggestion).title)
    }

    @Test fun providerIdsAndMissingEntitiesAreValidated() {
        assertEquals(listOf("Q123"), WikidataMetadata.searchIds("""{"search":[{"id":"https://bad"},{"id":"Q123"},{"id":"Q123"},null]}"""))
        assertTrue(WikidataMetadata.entities("""{"entities":{"Q1":{"missing":""},"unsafe":{}}}""").isEmpty())
    }
}
