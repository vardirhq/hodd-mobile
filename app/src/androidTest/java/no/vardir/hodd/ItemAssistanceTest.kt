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
        compose.onNodeWithText("Find and fill details").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Local AI ready · Gemini Nano").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Describe your item").performScrollTo().performTextInput("THE HOBBIT")
        compose.onNodeWithText("Suggest from description").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Choose a suggestion").fetchSemanticsNodes().isNotEmpty() }
        assertNull(applied)
        compose.onNodeWithText("The Hobbit").performClick()
        compose.onNodeWithText("Review details").assertExists()
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "06-suggestion-review.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertNull(applied)
        compose.onNodeWithText("Use details").performClick()
        compose.waitUntil(5000) { applied != null }
        assertEquals("The Hobbit", applied!!.title)
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
            assertEquals("On-device AI", assistant.readCover(file, "Book").items.single().source)
            assertEquals(1, model.calls)
            model.fail = true
            val fallback = assistant.readCover(file, "Book")
            assertTrue(fallback.items.any { it.title.contains("HOBBIT", ignoreCase = true) })
            assertTrue(fallback.notice.contains("could not finish"))
        } finally { assistant.close(); file.delete() }
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
