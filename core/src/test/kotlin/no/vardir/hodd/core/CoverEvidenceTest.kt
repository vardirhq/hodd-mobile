package no.vardir.hodd.core

import kotlin.test.*

class CoverEvidenceTest {
    @Test fun `damaged platform header is not offered as a title or author`() {
        val evidence = CoverEvidence(listOf(CoverLine("MLMENDO SWITCH", 30), CoverLine("SUPER AR ODYSSTK", 90)), "Book")
        assertEquals("Game", evidence.kind)
        assertEquals("Nintendo Switch", evidence.detectedPlatform!!.name)
        assertTrue(evidence.detectedPlatform!!.imperfect)
        assertFailsWith<IllegalArgumentException> { evidence.validate(ItemSuggestion("MLMENDO SWITCH", "SUPER AR ODYSSTK")) }
        val raw = evidence.alternatives().single()
        assertEquals("SUPER AR ODYSSTK", raw.title)
        assertEquals("", raw.creator)
        assertEquals("Game", raw.kind)
    }
    @Test fun `game title lines are not assigned to the creator`() {
        val evidence = CoverEvidence(listOf(CoverLine("NINTENDO SWITCH"), CoverLine("SUPER MARIO"), CoverLine("ODYSSEY")), "Game")
        val result = evidence.validate(ItemSuggestion("Super Mario", "ODYSSEY"))
        assertEquals("", result.creator)
        val noHeader = CoverEvidence(listOf(CoverLine("SUPER MARIO"), CoverLine("ODYSSEY")), "Book")
        assertEquals("", noHeader.validate(ItemSuggestion("Super Mario", "ODYSSEY", "Game"), imageBased = true).creator)
        assertEquals("Nintendo Switch", result.attributes["platform"])
        assertEquals("Nintendo", result.attributes["platformBrand"])
        assertEquals("Super Mario Odyssey", evidence.validate(ItemSuggestion("Super Mario Odyssey")).title)
    }
    @Test fun `visible author is retained but invisible facts are removed`() {
        val evidence = CoverEvidence(listOf(CoverLine("THE HOBBIT"), CoverLine("J. R. R. Tolkien")), "Book")
        val result = evidence.validate(ItemSuggestion("The Hobbit", "J. R. R. Tolkien", publisher = "Penguin", releaseYear = "1937", language = "English"))
        assertEquals("J. R. R. Tolkien", result.creator)
        assertEquals("", result.publisher)
        assertEquals("", result.releaseYear)
        assertEquals("", result.language)
    }
    @Test fun `text hallucinations are rejected while image input may correct OCR`() {
        val evidence = CoverEvidence(listOf(CoverLine("NINTENDO SWITCH"), CoverLine("SUPER AR ODYSSTK")), "Game")
        val proposal = ItemSuggestion("Super Mario Odyssey", kind = "Game")
        assertFailsWith<IllegalArgumentException> { evidence.validate(proposal) }
        assertEquals("Super Mario Odyssey", evidence.validate(proposal, imageBased = true).title)
        assertFailsWith<IllegalArgumentException> { evidence.validate(ItemSuggestion("Nintendo Switch"), imageBased = true) }
    }
    @Test fun `titles including platform words are not discarded`() {
        val evidence = CoverEvidence(listOf(CoverLine("NINTENDO SWITCH"), CoverLine("Nintendo Switch Sports", 90)), "Game")
        assertTrue(evidence.alternatives().any { it.title == "Nintendo Switch Sports" })
        assertEquals("Nintendo Switch Sports", evidence.validate(ItemSuggestion("Nintendo Switch Sports")).title)
    }
    @Test fun `switch and switch two remain distinct platforms`() {
        val evidence = CoverEvidence(listOf(CoverLine("Nintendo Switch 2"), CoverLine("My game")), "Book")
        assertEquals("Nintendo Switch 2", evidence.detectedPlatform!!.name)
        assertEquals("Game", evidence.kind)
        assertEquals("Nintendo Switch", CoverEvidence(listOf(CoverLine("Nintendo Switch")), "Game").detectedPlatform!!.name)
    }
    @Test fun `cover recognition cannot infer ownership or arbitrary hidden metadata`() {
        val evidence = CoverEvidence(listOf(CoverLine("My game"), CoverLine("2017")), "Game")
        val result = evidence.validate(ItemSuggestion("My game", status = "LENT", lentTo = "Chris", notes = "Mine", condition = "New", releaseYear = "2017", attributes = mapOf("developer" to "Nintendo", "privateKey" to "2017")))
        assertEquals("", result.status); assertEquals("", result.lentTo); assertEquals("", result.notes); assertEquals("", result.condition)
        assertEquals("2017", result.releaseYear)
        assertTrue(result.attributes.isEmpty())
    }
}
