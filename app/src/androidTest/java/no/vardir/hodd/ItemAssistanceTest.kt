package no.vardir.hodd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import no.vardir.hodd.core.CatalogItem
import no.vardir.hodd.ui.HoddTheme
import no.vardir.hodd.ui.ItemAssistPanel
import kotlinx.coroutines.runBlocking
import no.vardir.hodd.core.ItemSuggestion
import no.vardir.hodd.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ItemAssistanceTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun descriptionSuggestionRequiresReviewBeforeApplying() {
        val assistant = LocalItemAssistant(context, StubModel(LocalAiStatus.AVAILABLE))
        var applied: ItemSuggestion? = null
        compose.setContent {
            HoddTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ItemAssistPanel(CatalogItem(collectionId = "shelf", title = ""), null, true,
                        apply = { suggestion, _, _ -> applied = suggestion }, working = {}, message = {}, assistant = assistant)
                }
            }
        }
        compose.onNodeWithText("Local AI options").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Gemini Nano available · suggestions still need review").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Describe your item").performScrollTo().performTextInput("THE HOBBIT")
        compose.onNodeWithText("Suggest search title").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Review search").fetchSemanticsNodes().isNotEmpty() }
        assertNull(applied)
        compose.onNodeWithText("The Hobbit").assertExists()
        compose.onNodeWithText("Use details").assertDoesNotExist()
        compose.onNodeWithText("Back to item").performClick()
        assertNull(applied)

    }

    @Test fun bookLookupUsesIsbnAndCachesResponse() = runBlocking {
        File(context.cacheDir, "metadata").deleteRecursively()
        var requests = 0
        val repository = MetadataRepository(context) { url, _ ->
            requests++
            assertTrue(url.contains("isbn=9780140328721"))
            """{"docs":[{"key":"/works/OL123W","title":"Matilda","author_name":["Roald Dahl"]}]}""".toByteArray()
        }
        val first = repository.lookup("Book", "9780140328721", "", "")
        val second = repository.lookup("Book", "9780140328721", "", "")
        assertEquals("Matilda", first.single().title)
        assertEquals("9780140328721", first.single().barcode)
        assertEquals(first, second)
        assertEquals(1, requests)
    }

    @Test fun musicSearchKeepsLeadingZeroesAndSeparatesMultipleReleases() = runBlocking {
        File(context.cacheDir, "metadata").deleteRecursively()
        val repository = MetadataRepository(context) { url, _ ->
            assertTrue(url.contains("musicbrainz.org"))
            assertTrue(url.contains("0035627405723"))
            """{"releases":[{"id":"12345678-1234-1234-1234-123456789abc","title":"Album","barcode":"0035627405723","country":"NO"},{"id":"12345678-1234-1234-1234-123456789def","title":"Album","barcode":"0035627405723","country":"GB"}]}""".toByteArray()
        }
        val results = repository.lookup("CD", "0035627405723", "", "")
        assertEquals(2, results.size)
        assertEquals("0035627405723", results.first().barcode)
        assertNotEquals(results.first().sourceId, results.last().sourceId)
    }

    private fun wikidataRepository(requested: MutableList<String>) = MetadataRepository(context) { url, _ ->
        requested += url
        assertTrue(url.startsWith("https://www.wikidata.org/w/api.php?"))
        if (url.contains("wbsearchentities")) {
            assertTrue(url.contains("search=SUPER+MARIO+MAKER+2"))
            """{"search":[{"id":"Q61831178"}]}""".toByteArray()
        } else {
            val payload = """{"entities":{
              "Q61831178":{"labels":{"en":{"value":"Super Mario Maker 2"}},"claims":{
                "P31":[{"mainsnak":{"datavalue":{"value":{"id":"Q7889"}}}}],
                "P123":[{"mainsnak":{"datavalue":{"value":{"id":"Q8093"}}}}]}},
              "Q7889":{"labels":{"en":{"value":"video game"}}},
              "Q8093":{"labels":{"en":{"value":"Nintendo"}}}
            }}"""
            val ids = java.net.URLDecoder.decode(url.substringAfter("&ids="), "UTF-8").split('|')
            val data = kotlinx.serialization.json.Json.parseToJsonElement(payload) as kotlinx.serialization.json.JsonObject
            val entities = data["entities"] as kotlinx.serialization.json.JsonObject
            kotlinx.serialization.json.JsonObject(mapOf("entities" to kotlinx.serialization.json.JsonObject(entities.filterKeys { it in ids }))).toString().toByteArray()
        }
    }

    @Test fun gameLookupHydratesCanonicalFactsAndCachesEntireSearch() = runBlocking {
        File(context.cacheDir, "metadata").deleteRecursively()
        val requests = mutableListOf<String>()
        val repository = wikidataRepository(requests)
        val first = repository.lookup("Game", "not-an-ean", "SUPER MARIO MAKER 2", "", "Nintendo Switch")
        assertEquals("Super Mario Maker 2", first.single().title)
        assertEquals("Nintendo", first.single().publisher)
        assertEquals("Wikidata", first.single().source)
        val count = requests.size
        assertEquals(4, count)
        assertTrue(requests.last().contains("props=labels&"))
        assertTrue(requests.none { it.contains("not-an-ean") })
        assertEquals(first, repository.lookup("Game", "", "SUPER MARIO MAKER 2", "", "Nintendo Switch"))
        assertEquals(count, requests.size)
        try { repository.lookup("Game", "12345", "", ""); fail("Title required") }
        catch (_: IllegalArgumentException) { }
        assertEquals(count, requests.size)
    }

    @Test fun gameOnlineSearchRequiresMatchReviewAndExplicitApply() {
        File(context.cacheDir, "metadata").deleteRecursively()
        val requests = mutableListOf<String>()
        val repository = wikidataRepository(requests)
        val assistant = LocalItemAssistant(context, StubModel(LocalAiStatus.UNAVAILABLE))
        var applied: ItemSuggestion? = null
        compose.setContent {
            HoddTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                ItemAssistPanel(CatalogItem(collectionId = "shelf", title = "SUPER MARIO MAKER 2", kind = "Game"), null, true,
                    apply = { suggestion, _, _ -> applied = suggestion }, working = {}, message = {},
                    assistant = assistant, metadataRepository = repository)
            } }
        }
        compose.onNodeWithText("Find matches").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Choose a match").fetchSemanticsNodes().isNotEmpty() }
        assertNull(applied)
        compose.onNodeWithText("Super Mario Maker 2").performClick()
        compose.onNodeWithText("Publisher: Nintendo").assertExists()
        assertNull(applied)
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "18-wikidata-review.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Use details").performClick()
        compose.waitUntil(5000) { applied != null }
        assertEquals("Super Mario Maker 2", applied!!.title)
        assertEquals("Nintendo", applied!!.publisher)
    }

    @Test fun invalidCodeDoesNotMakeNetworkRequest() = runBlocking {
        val repository = MetadataRepository(context) { _, _ -> fail("Invalid ISBN should never be sent"); byteArrayOf() }
        try { repository.lookup("Book", "9780140328722", "", ""); fail("Expected validation error") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun bundledCoverRecognitionWorksWithoutLocalLanguageModel() = runBlocking {
        val file = coverPhoto()
        val model = StubModel(LocalAiStatus.UNAVAILABLE)
        val assistant = LocalItemAssistant(context, model)
        try {
            val result = assistant.readCover(file, "Book")
            assertTrue(result.items.any { it.title.contains("HOBBIT", ignoreCase = true) })
            assertTrue(result.items.all { it.source == "On-device text recognition" && it.creator.isBlank() })
            assertEquals(0, model.calls)
        } finally { assistant.close(); file.delete() }
    }

    @Test fun availableLocalModelIsPreferredAndFailureFallsBackToRecognizedText() = runBlocking {
        val file = coverPhoto()
        val model = StubModel(LocalAiStatus.AVAILABLE)
        val assistant = LocalItemAssistant(context, model)
        try {
            assertEquals("On-device AI", assistant.readCover(file, "Book").items.first().source)
            assertEquals(1, model.calls)
            model.fail = true
            val fallback = assistant.readCover(file, "Book")
            assertTrue(fallback.items.any { it.title.contains("HOBBIT", ignoreCase = true) })
            assertTrue(fallback.notice.contains("could not produce"))
        } finally { assistant.close(); file.delete() }
    }

    @Test fun imageInputIsPreferredWhenTheLocalAdapterSupportsIt() = runBlocking {
        val file = coverPhoto()
        var images = 0
        var textCalls = 0
        val model = object : LocalTextModel {
            override suspend fun status() = LocalAiStatus.AVAILABLE
            override suspend fun download(progress: (Long) -> Unit) = Unit
            override suspend fun suggest(text: String, kind: String, description: Boolean): ItemSuggestion { textCalls++; error("Should prefer the image") }
            override suspend fun suggestCover(image: Bitmap, text: String, kind: String): ItemSuggestion {
                images++; assertFalse(image.isRecycled); assertTrue(image.width > 0); assertTrue(text.contains("HOBBIT", true))
                return ItemSuggestion("The Hobbit", "J. R. R. Tolkien", "Book", source = "On-device AI")
            }
            override fun close() = Unit
        }
        val assistant = LocalItemAssistant(context, model)
        try {
            val result = assistant.readCover(file, "Book")
            assertEquals("The Hobbit", result.items.first().title)
            assertEquals(1, images); assertEquals(0, textCalls)
            assertTrue(result.notice.contains("cover image"))
            assertTrue(result.recognizedText.contains("HOBBIT", true))
        } finally { assistant.close(); file.delete() }
    }

    @Test fun brandingAsTitleIsRejectedInsteadOfBeingPresentedAsAnIdentification() = runBlocking {
        val model = object : LocalTextModel {
            override suspend fun status() = LocalAiStatus.AVAILABLE
            override suspend fun download(progress: (Long) -> Unit) = Unit
            override suspend fun suggest(text: String, kind: String, description: Boolean) = ItemSuggestion("MLMENDO SWITCH", "SUPER AR ODYSSTK", source = "On-device AI")
            override fun close() = Unit
        }
        val assistant = LocalItemAssistant(context, model)
        try {
            val result = assistant.readCoverText("MLMENDO SWITCH\nSUPER AR ODYSSTK", "Book")
            assertEquals("SUPER AR ODYSSTK", result.items.single().title)
            assertEquals("", result.items.single().creator)
            assertEquals("Game", result.items.single().kind)
            assertEquals("On-device text recognition", result.items.single().source)
            assertTrue(result.notice.contains("could not produce a reliable"))
            val brandingOnly = assistant.readCoverText("Nintendo Switch", "Game")
            assertTrue(brandingOnly.items.isEmpty()); assertEquals("Nintendo Switch", brandingOnly.recognizedText)
        } finally { assistant.close() }
    }

    @Test fun coverReadingOpensEditableSearchWithoutApplyingOrCallingTheCatalog() {
        val file = coverPhoto(); val assistant = LocalItemAssistant(context, StubModel(LocalAiStatus.UNAVAILABLE))
        var applied: ItemSuggestion? = null
        var queries = 0
        val metadata = object : ItemMetadataSource {
            override suspend fun lookup(kind: String, barcode: String, title: String, creator: String, platform: String): List<ItemSuggestion> {
                queries++
                assertEquals("Super Mario Odyssey", title); assertEquals("Game", kind); assertEquals("", creator)
                return listOf(ItemSuggestion("Super Mario Odyssey", kind = "Game", source = "Wikidata", attributes = mapOf("platform" to "Nintendo Switch")))
            }
            override suspend fun importCover(suggestion: ItemSuggestion) = error("Not requested")
        }
        compose.setContent { HoddTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ItemAssistPanel(CatalogItem(collectionId = "shelf", title = "", kind = "Book"), file, true,
                apply = { suggestion, _, _ -> applied = suggestion }, working = {}, message = {}, assistant = assistant, metadataRepository = metadata)
        } } }
        compose.onNodeWithText("Read cover").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Review search").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, queries); assertNull(applied)
        compose.onAllNodesWithText("Search title").onLast().performTextReplacement("Super Mario Odyssey")
        compose.onNodeWithTag("choice-search-kind").performScrollTo().performClick()
        compose.onNodeWithText("Game", substring = false).performClick()
        compose.onNodeWithText("Find matches").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Choose a match").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, queries); assertNull(applied)
        compose.onNodeWithText("Super Mario Odyssey").performClick()
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "17-cover-review.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Use details").performClick()
        compose.waitUntil(5000) { applied != null }
        assertEquals("Super Mario Odyssey", applied!!.title); assertEquals("Game", applied!!.kind)
        assertEquals("Nintendo Switch", applied!!.attributes["platform"])
        file.delete()
    }

    private fun coverPhoto(): File {
        val bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 72f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        canvas.drawText("THE HOBBIT", 80f, 350f, paint)
        paint.textSize = 36f
        canvas.drawText("J. R. R. Tolkien", 100f, 500f, paint)
        val file = File(context.cacheDir, "ocr-${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private class StubModel(private val availability: LocalAiStatus) : LocalTextModel {
        var calls = 0
        var fail = false
        override suspend fun status() = availability
        override suspend fun download(progress: (Long) -> Unit) = Unit
        override suspend fun suggest(text: String, kind: String, description: Boolean): ItemSuggestion {
            calls++
            check(!fail) { "Simulated model failure" }
            assertTrue(text.contains("HOBBIT", ignoreCase = true))
            return ItemSuggestion("The Hobbit", "J. R. R. Tolkien", kind, source = "On-device AI")
        }
        override fun close() = Unit
    }
}
