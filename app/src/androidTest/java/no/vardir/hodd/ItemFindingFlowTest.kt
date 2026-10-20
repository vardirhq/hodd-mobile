package no.vardir.hodd

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.CompletableDeferred
import no.vardir.hodd.core.*
import no.vardir.hodd.core.Collection
import no.vardir.hodd.data.*
import no.vardir.hodd.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ItemFindingFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val directory by lazy { File(compose.activity.filesDir, "photos/find-test-${UUID.randomUUID()}").apply { mkdirs() } }
    private var saved: CatalogItem? = null
    private val queries = mutableListOf<String>()
    private val match = ItemSuggestion("Super Mario Maker 2", creator = "Nintendo EPD", kind = "Game", publisher = "Nintendo",
        source = "Wikidata", sourceId = "Q61831178", sourceUrl = "https://www.wikidata.org/wiki/Q61831178", releaseYear = "2019",
        attributes = mapOf("platform" to "Nintendo Switch", "platformBrand" to "Nintendo", "developer" to "Nintendo EPD"),
        detail = "Platforms: Nintendo Switch · First catalog release: 2019")
    private fun metadata(results: List<ItemSuggestion> = listOf(match)) = object : ItemMetadataSource {
        override suspend fun lookup(kind: String, barcode: String, title: String, creator: String, platform: String): List<ItemSuggestion> {
            queries += title; assertEquals("Game", kind); return results
        }
        override suspend fun importCover(suggestion: ItemSuggestion) = error("Store cover must be kept")
    }
    private fun editor(item: CatalogItem, metadata: ItemMetadataSource = metadata(), products: ProductLinkSource = ProductLinkImporter) {
        compose.setContent { HoddTheme { ItemEditor(item, listOf(Collection("s", "Shelf")), "s", false,
            photo = { name -> name?.let { File(directory, it) } }, importPhoto = { _, _ -> error("Not requested") },
            save = { saved = it }, cancel = {}, message = {}, productLinks = products, metadataRepository = metadata) } }
    }
    private fun waitFor(text: String) = compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun screenshot(name: String, dialog: Boolean = false) {
        val target = File(compose.activity.getExternalFilesDir(null), "screenshots/$name").apply { parentFile!!.mkdirs() }
        target.outputStream().use { (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun manualTitleFindsCanonicalMetadataWithoutRecognitionAndKeepsStoreCoverAndCopyYear() {
        File(directory, "store.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val original = CatalogItem(collectionId = "s", title = "SUPER MARIO MAKER 2 - SWITCH", kind = "Game", photoName = "store.jpg",
            releaseYear = "2020", notes = "My copy", attributes = mapOf("storeUrl" to "https://shop.example/item", "platform" to "Nintendo Switch"))
        editor(original)
        compose.onNodeWithTag("find-item-query").performTextReplacement("Super Mario Maker 2")
        screenshot("24-find-item.png")
        compose.onNodeWithText("Find matches").performScrollTo().performClick(); waitFor("Choose a match")
        assertNull(saved); assertEquals(listOf("Super Mario Maker 2"), queries)
        compose.onNodeWithTag("catalog-match-${match.sourceId}").performClick(); waitFor("Review details")
        compose.onNodeWithTag("choice-match-platform").assertTextContains("Nintendo Switch")
        screenshot("25-find-item-review.png", true)
        compose.onNodeWithText("Use details").performClick()
        compose.onNodeWithText("Save changes").performClick()
        assertEquals(match.title, saved!!.title); assertEquals("Nintendo", saved!!.publisher)
        assertEquals("store.jpg", saved!!.photoName); assertEquals("My copy", saved!!.notes)
        assertEquals("2020", saved!!.releaseYear); assertEquals("2019", saved!!.attributes["firstReleaseYear"])
        assertEquals("https://shop.example/item", saved!!.attributes["storeUrl"])
        assertEquals("Q61831178", saved!!.metadataId)
    }

    @Test fun storeImportEditsCoverThenFindsCatalogMatchesWithoutAnotherOcrOffer() {
        val source = object : ProductLinkSource {
            override suspend fun preview(url: String) = listOf(ProductPreview("SUPER MARIO MAKER 2 - Nintendo Switch", "Retail marketing", "https://shop.example/cover.jpg", "", "", "", url, publisher = "Retail distributor"))
            override suspend fun prepareImage(url: String, target: File) {
                val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
            }
        }
        editor(CatalogItem(collectionId = "s", title = "", kind = "Game"), products = source)
        compose.onNodeWithText("Import from a store").performScrollTo().performClick()
        compose.onNodeWithText("Product page URL").performScrollTo().performTextInput("https://shop.example/item")
        compose.onNodeWithText("Fetch product details").performScrollTo().performClick(); waitFor("Import product details?")
        compose.onNodeWithTag("store-find-matches").assertIsOn()
        compose.onNodeWithTag("store-field-Publisher").assertIsOff()
        compose.onNodeWithTag("store-image-checkbox").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Product image preview").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(queries.isEmpty())
        compose.onNodeWithText("Use details").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("photo-crop-preview").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(queries.isEmpty()); assertNull(saved)
        compose.onNodeWithText("Use photo").performClick(); waitFor("Choose a match")
        assertEquals(listOf("SUPER MARIO MAKER 2"), queries)
        compose.onNodeWithText("Find details from this photo?").assertDoesNotExist()
        compose.onNodeWithTag("catalog-match-${match.sourceId}").performClick()
        compose.onNodeWithText("Use details").performClick()
        compose.onNodeWithText("Save changes").performClick()
        assertEquals(match.title, saved!!.title); assertEquals("Wikidata", saved!!.metadataSource)
        assertEquals("Nintendo", saved!!.publisher); assertEquals("", saved!!.notes)
        assertEquals("https://shop.example/item", saved!!.attributes["storeUrl"])
        assertTrue(File(directory, saved!!.photoName!!).isFile)
        assertEquals("", saved!!.releaseYear)
    }

    @Test fun decliningCatalogTitleKeepsMyTitleAndCustomCopyChoices() {
        editor(CatalogItem(collectionId = "s", title = "My special copy", kind = "Game",
            attributes = mapOf("platform" to "Custom handheld", "platformBrand" to "My brand", "edition" to "Signed")))
        compose.onNodeWithText("Find matches").performScrollTo().performClick(); waitFor("Choose a match")
        compose.onNodeWithTag("catalog-match-${match.sourceId}").performClick()
        compose.onNodeWithText("Use catalog title").performScrollTo()
        compose.onNodeWithTag("use-catalog-title").performClick()
        compose.onNodeWithText("Use details").performClick()
        compose.onNodeWithText("Save changes").performClick()
        assertEquals("My special copy", saved!!.title)
        assertEquals("Custom handheld", saved!!.attributes["platform"])
        assertEquals("My brand", saved!!.attributes["platformBrand"])
        assertEquals("Signed", saved!!.attributes["edition"])
    }

    @Test fun cancelingLookupPreventsLateMatchAndKeepsDraftUsable() {
        val pending = CompletableDeferred<List<ItemSuggestion>>()
        val source = object : ItemMetadataSource {
            override suspend fun lookup(kind: String, barcode: String, title: String, creator: String, platform: String) = pending.await()
            override suspend fun importCover(suggestion: ItemSuggestion) = error("Not requested")
        }
        editor(CatalogItem(collectionId = "s", title = "My title", kind = "Game"), source)
        compose.onNodeWithText("Find matches").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onNodeWithText("Cancel request").performScrollTo().performClick()
        pending.complete(listOf(match)); compose.waitForIdle()
        compose.onNodeWithText("Choose a match").assertDoesNotExist()
        compose.onNodeWithText("Save changes").assertIsEnabled().performClick()
        assertEquals("My title", saved!!.title); assertEquals("", saved!!.metadataSource)
    }

    @Test fun noMatchKeepsEditableQueryAndManualSave() {
        editor(CatalogItem(collectionId = "s", title = "", kind = "Game"), metadata(emptyList()))
        compose.onNodeWithTag("find-item-query").performTextReplacement("My obscure game")
        compose.onNodeWithText("Find matches").performScrollTo().performClick()
        waitFor("No matches found. Edit the search title, check the type or platform, or keep entering details manually.")
        compose.onNodeWithTag("find-item-query").assertTextContains("My obscure game")
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onNodeWithText("Enter details manually").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").assertIsEnabled().performClick()
        assertEquals("My obscure game", saved!!.title)
    }
}
