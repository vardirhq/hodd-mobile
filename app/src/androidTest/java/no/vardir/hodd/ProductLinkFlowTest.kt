package no.vardir.hodd

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import no.vardir.hodd.core.CatalogItem
import no.vardir.hodd.core.Collection
import no.vardir.hodd.data.ProductLinkSource
import no.vardir.hodd.data.ItemMetadataSource
import no.vardir.hodd.data.ProductPreview
import no.vardir.hodd.ui.HoddTheme
import no.vardir.hodd.ui.ItemEditor
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ProductLinkFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val directory by lazy { File(compose.activity.filesDir, "photos/store-test-${UUID.randomUUID()}").apply { mkdirs() } }
    private var saved: CatalogItem? = null
    private val messages = mutableListOf<String>()
    private fun image(target: File) {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80, 110, 140)) }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
    }
    private fun item(title: String = "My title", cover: Boolean = false): CatalogItem {
        if (cover) image(File(directory, "old.jpg"))
        return CatalogItem(id = "store-item", collectionId = "s", title = title, kind = "Game", photoName = if (cover) "old.jpg" else null)
    }
    private fun preview(title: String = "Shop title") = ProductPreview(title, "Store description", "https://shop.example/image.jpg", "Brand", "499", "NOK", "https://shop.example/item", publisher = "Publisher")
    private fun editor(item: CatalogItem, source: ProductLinkSource) {
        compose.setContent { HoddTheme { ItemEditor(item, listOf(Collection("s", "Shelf")), "s", false,
            photo = { name -> name?.let { File(directory, it) } }, importPhoto = { _, _ -> error("Store images should not use asynchronous picker callbacks") },
            save = { saved = it }, cancel = {}, message = { messages += it }, productLinks = source,
            metadataRepository = object : ItemMetadataSource {
                override suspend fun lookup(kind: String, barcode: String, title: String, creator: String, platform: String) = emptyList<no.vardir.hodd.core.ItemSuggestion>()
                override suspend fun importCover(suggestion: no.vardir.hodd.core.ItemSuggestion) = error("Not requested")
            }) } }
        compose.onNodeWithText("Import from a store").performScrollTo().performClick()
        compose.onNodeWithText("Product page URL").performScrollTo().performTextInput("https://shop.example/item")
        compose.onNodeWithText("Fetch product details").performScrollTo().performClick()
    }
    private fun waitForReview() = compose.waitUntil(10000) { compose.onAllNodesWithText("Import product details?").fetchSemanticsNodes().isNotEmpty() }
    private fun source(results: List<ProductPreview>) = object : ProductLinkSource {
        override suspend fun preview(url: String) = results
        override suspend fun prepareImage(url: String, target: File) = error("Image was not selected")
    }
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun pendingFetchBlocksSavingAndCompetingPhotosAndCancelPreventsLateApply() {
        val pending = CompletableDeferred<List<ProductPreview>>()
        val source = object : ProductLinkSource {
            override suspend fun preview(url: String) = pending.await()
            override suspend fun prepareImage(url: String, target: File) = error("Not requested")
        }
        editor(item(), source)
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onNodeWithText("Choose photo").assertIsNotEnabled()
        compose.onNodeWithText("Take photo").assertIsNotEnabled()
        compose.onNodeWithText("Cancel request").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").assertIsEnabled()
        pending.complete(listOf(preview()))
        compose.waitForIdle()
        compose.onNodeWithText("Import product details?").assertDoesNotExist()
        compose.onNodeWithText("Save changes").performClick()
        assertEquals("My title", saved!!.title); assertTrue(saved!!.attributes.isEmpty())
    }
    @Test fun refusedStoreLeavesDraftEditableAndOffersBrowserFallback() {
        val source = object : ProductLinkSource {
            override suspend fun preview(url: String): List<ProductPreview> = error("The store is limiting requests (HTTP 429). Wait 60 seconds.")
            override suspend fun prepareImage(url: String, target: File) = error("Not requested")
        }
        editor(item(), source)
        compose.onNodeWithText("Open store in browser").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertIsEnabled().performClick()
        assertEquals("My title", saved!!.title)
        assertTrue(saved!!.attributes.isEmpty())
    }
    @Test fun applyingSelectedMetadataPreservesUserEditsAndCatalogSourceAndSkipsImageByDefault() {
        val original = item().copy(publisher = "My publisher", notes = "My notes", metadataSource = "Wikidata", metadataUrl = "https://www.wikidata.org/wiki/Q1")
        editor(original, source(listOf(preview())))
        waitForReview()
        compose.onNodeWithTag("store-image-checkbox").assertIsOff()
        compose.onNodeWithTag("store-field-Title").assertIsNotEnabled()
        compose.onNodeWithTag("store-field-Publisher").assertIsNotEnabled()
        val folder = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, "22-store-review.png").outputStream().use { compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("Use details").performClick()
        assertNull(saved)
        compose.onNodeWithText("Save changes").performClick()
        assertEquals(original.title, saved!!.title); assertEquals(original.publisher, saved!!.publisher); assertEquals(original.notes, saved!!.notes)
        assertEquals(original.metadataSource, saved!!.metadataSource); assertEquals(original.metadataUrl, saved!!.metadataUrl)
        assertEquals("Brand", saved!!.attributes["brand"]); assertEquals("499 NOK", saved!!.attributes["storePrice"])
        assertEquals("https://shop.example/item", saved!!.attributes["storeUrl"]); assertNull(saved!!.photoName)
    }
    @Test fun ambiguousProductsAreChosenBeforeReviewAndUncheckedFieldsStayBlank() {
        editor(item(""), source(listOf(preview("Wrong product"), preview("Right product"))))
        compose.waitUntil(10000) { compose.onAllNodesWithText("Choose the product").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Right product").performClick(); waitForReview()
        compose.onNodeWithTag("store-field-Publisher").performClick()
        compose.onNodeWithTag("store-field-Store price").performScrollTo().performClick()
        compose.onNodeWithText("Use details").performClick()
        compose.onNodeWithText("Save changes").performClick()
        assertEquals("Right product", saved!!.title); assertEquals("", saved!!.publisher)
        assertNull(saved!!.attributes["storePrice"])
    }
    @Test fun imageDownloadMustFinishBeforeApplyThenPhotoEditorAddsToGalleryWithoutReplacingCover() {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val original = item(cover = true); val before = File(directory, "old.jpg").readBytes()
        val source = object : ProductLinkSource {
            override suspend fun preview(url: String) = listOf(preview())
            override suspend fun prepareImage(url: String, target: File) { started.complete(Unit); release.await(); image(target) }
        }
        editor(original, source); waitForReview()
        compose.onNodeWithTag("store-image-checkbox").performScrollTo().performClick()
        compose.waitUntil(5000) { started.isCompleted }
        compose.onNodeWithText("Use details").assertIsNotEnabled()
        release.complete(Unit)
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Product image preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Use details").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("photo-crop-preview").fetchSemanticsNodes().isNotEmpty() }
        assertNull(saved); assertArrayEquals(before, File(directory, "old.jpg").readBytes())
        compose.onNodeWithText("Use photo").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Save changes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Find details from this photo?").assertDoesNotExist()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Save changes").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save changes").performClick()
        assertEquals("old.jpg", saved!!.photoName)
        assertEquals(1, saved!!.photoNames.size); assertTrue(File(directory, saved!!.photoNames.single()).isFile)
        assertArrayEquals(before, File(directory, "old.jpg").readBytes())
    }
    @Test fun cancelingPendingImageRemovesStagingAndLeavesDraftUnchanged() {
        val started = CompletableDeferred<Unit>(); var target: File? = null
        val original = item(cover = true)
        val source = object : ProductLinkSource {
            override suspend fun preview(url: String) = listOf(preview())
            override suspend fun prepareImage(url: String, output: File) { target = output; image(output); started.complete(Unit); awaitCancellation() }
        }
        editor(original, source); waitForReview()
        compose.onNodeWithTag("store-image-checkbox").performScrollTo().performClick()
        compose.waitUntil(5000) { started.isCompleted }
        compose.onNodeWithText("Cancel", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { target?.exists() == false }
        compose.onNodeWithText("Save changes").assertIsEnabled().performClick()
        assertEquals(original.title, saved!!.title); assertEquals(original.photoName, saved!!.photoName)
        assertEquals(original.notes, saved!!.notes); assertTrue(saved!!.attributes.isEmpty())
    }
}
