package no.vardir.hodd.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionInsightsTest {
    private val books = Collection("books", "Books")
    private val games = Collection("games", "Games")

    @Test fun emptyCatalogHasNoInventedStatistics() {
        val stats = Catalog().insights()
        assertEquals(0, stats.total)
        assertTrue(stats.byKind.isEmpty())
        assertTrue(stats.monthlyAdditions.isEmpty())
    }

    @Test fun countsAndDistributionsRespectCollectionScope() {
        val catalog = Catalog(collections = listOf(books, games), items = listOf(
            CatalogItem(id = "a", collectionId = games.id, title = "A", kind = "Game",
                attributes = mapOf("platform" to "Switch"), tags = listOf("Co-op"), location = "Shelf", addedAt = 1),
            CatalogItem(id = "b", collectionId = games.id, title = "B", kind = "Game",
                attributes = mapOf("platform" to "Switch"), status = ItemStatus.WANTED, addedAt = 2),
            CatalogItem(id = "c", collectionId = books.id, title = "C", kind = "Book",
                status = ItemStatus.LENT, lentTo = "Alex", addedAt = 3),
        ))
        val all = catalog.insights()
        assertEquals(3, all.total)
        assertEquals(2, all.owned)
        assertEquals(1, all.wanted)
        assertEquals(1, all.lent)
        assertEquals(listOf("Switch" to 2), all.byPlatform)
        assertEquals(1, all.tagged)
        assertEquals(1, all.located)
        assertEquals(3, all.monthlyAdditions.sumOf { it.second })
        val scoped = catalog.insights(games.id)
        assertEquals(2, scoped.total)
        assertEquals(0, scoped.lent)
        assertEquals(listOf("Game" to 2), scoped.byKind)
        assertEquals(listOf("Games" to 2), scoped.byCollection)
    }
}
