package no.vardir.hodd

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.vardir.hodd.ui.PhotoEditor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.exifinterface.media.ExifInterface
import no.vardir.hodd.core.*
import no.vardir.hodd.data.PhotoEdits
import no.vardir.hodd.ui.HoddTheme
import no.vardir.hodd.ui.ItemEditor
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.min

class PhotoEditingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val directory by lazy { File(compose.activity.filesDir, "photos/photo-test-${UUID.randomUUID()}").apply { mkdirs() } }
    private var saved: CatalogItem? = null
    private var staged: File? = null
    private fun photo(name: String?) = name?.let { File(directory, it) }
    private fun image(name: String = "original.jpg", width: Int = 600, height: Int = 900): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(110, 110, 110))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = width / 11f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        canvas.drawText("THE HOBBIT", width * 0.1f, height * 0.5f, paint)
        val file = File(directory, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
        return file
    }
    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    @After fun cleanup() { directory.deleteRecursively() }

    private fun editor(original: File, registry: ActivityResultRegistry? = null): StateRestorationTester {
        val tester = StateRestorationTester(compose)
        tester.setContent {
            HoddTheme {
                @androidx.compose.runtime.Composable fun content() {
                    ItemEditor(CatalogItem(id = "photo-item", collectionId = "s", title = "The Hobbit", kind = "Book", photoName = original.name),
                        listOf(Collection("s", "Shelf")), "s", false, photo = ::photo,
                        importPhoto = { _, done -> staged = image("staged.jpg"); done(staged!!.name) },
                        save = { saved = it }, cancel = {}, message = {})
                }
                if (registry == null) content() else CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                    override val activityResultRegistry: ActivityResultRegistry = requireNotNull(registry)
                }) { content() }
            }
        }
        return tester
    }
    private fun waitForPreview() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("photo-crop-preview").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String, dialog: Boolean = false) {
        val folder = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, name).outputStream().use {
            (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun pixelCropAndAdjustmentsAreRenderedWithoutChangingSource() {
        val source = image(); val before = digest(source)
        val target = File(directory, "edited.jpg")
        PhotoEdits.export(source, target, PhotoCrop(0.25f, 0.25f, 0.75f, 0.75f), 0.2f, 1.2f, 0)
        val bitmap = BitmapFactory.decodeFile(target.path)
        assertEquals(300, bitmap.width); assertEquals(450, bitmap.height)
        val expected = (110 * 1.2f + 128 * (1 - 1.2f) + 0.2f * 255).toInt()
        assertTrue(kotlin.math.abs(Color.red(bitmap.getPixel(10, 10)) - expected) <= 5)
        assertEquals(before, digest(source)); bitmap.recycle()
    }
    @Test fun exifRotationAndMirroringAreAppliedBeforeEditing() {
        val source = image(width = 120, height = 80)
        val raw = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        Canvas(raw).apply { drawColor(Color.RED); drawRect(60f, 0f, 120f, 80f, Paint().apply { color = Color.BLUE }) }
        source.outputStream().use { raw.compress(Bitmap.CompressFormat.JPEG, 100, it) }; raw.recycle()
        ExifInterface(source).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val upright = PhotoEdits.decode(source)
        assertEquals(80, upright.width); assertEquals(120, upright.height)
        assertTrue(Color.red(upright.getPixel(40, 20)) > 200)
        assertTrue(Color.blue(upright.getPixel(40, 100)) > 200); upright.recycle()
        ExifInterface(source).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString()); saveAttributes() }
        val mirrored = PhotoEdits.decode(source)
        assertTrue(Color.blue(mirrored.getPixel(20, 40)) > 200); mirrored.recycle()
    }
    @Test fun decodeIsBoundedAndInvalidOrInPlaceEditsCannotDamageSource() {
        val large = image(width = 6000, height = 120)
        val bitmap = PhotoEdits.decode(large)
        assertTrue(maxOf(bitmap.width, bitmap.height) <= 2560); bitmap.recycle()
        val before = digest(large)
        try { PhotoEdits.export(large, large, PhotoCrop(), 0f, 1f, 0); fail("Must use new filename") } catch (_: IllegalArgumentException) { }
        assertEquals(before, digest(large))
        val invalid = File(directory, "invalid.jpg").apply { writeText("not an image") }
        try { PhotoEdits.decode(invalid); fail("Must reject invalid image") } catch (_: IllegalArgumentException) { }
    }
    @Test fun cropAdjustRestoreAcceptAndSkipOfferKeepExistingPhotoIntact() {
        val source = image(); val before = digest(source)
        val tester = editor(source)
        compose.onNodeWithText("Edit photo").performScrollTo().performClick(); waitForPreview()
        compose.onNodeWithTag("photo-crop-preview").performTouchInput {
            val w = min(width.toFloat(), height * 600f / 900f)
            val h = min(height.toFloat(), width * 900f / 600f)
            val x = (width - w) / 2; val y = (height - h) / 2
            swipe(Offset(x + 3, y + 3), Offset(x + w * 0.15f, y + h * 0.15f), 400)
        }
        compose.onNodeWithTag("photo-brightness").performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
        compose.onNodeWithTag("photo-contrast").performSemanticsAction(SemanticsActions.SetProgress) { it(1.3f) }
        tester.emulateSavedInstanceStateRestore(); waitForPreview()
        compose.onNodeWithText("Brightness · 20").assertExists()
        screenshot("19-photo-editor.png")
        compose.onNodeWithText("Use photo").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Find details from this photo?").fetchSemanticsNodes().isNotEmpty() }
        screenshot("20-photo-identification-offer.png", true)
        assertNull(saved); assertEquals(before, digest(source))
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Save changes").performClick()
        val edited = requireNotNull(photo(saved!!.photoName))
        assertNotEquals(source.name, edited.name)
        val result = BitmapFactory.decodeFile(edited.path)
        assertTrue(result.width < 600); assertTrue(result.height < 900); result.recycle()
        assertEquals(before, digest(source))
    }
    @Test fun cancelEditingKeepsOriginalAndDoesNotOfferIdentification() {
        val source = image(); val before = digest(source); editor(source)
        compose.onNodeWithText("Edit photo").performScrollTo().performClick(); waitForPreview()
        compose.onNodeWithContentDescription("Rotate photo clockwise").performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithContentDescription("Cancel photo editing").performClick()
        compose.onNodeWithText("Find details from this photo?").assertDoesNotExist()
        compose.onNodeWithText("Save changes").performClick()
        assertEquals(source.name, saved!!.photoName); assertEquals(before, digest(source))
    }
    @Test fun shortEditorKeepsPreviewAndSaveVisibleAndCropControlsReachable() {
        val source = image()
        compose.setContent { HoddTheme { Box(Modifier.height(320.dp)) {
            PhotoEditor(source, onAccepted = {}, onCancel = {}, message = {})
        } } }
        waitForPreview()
        assertTrue(compose.onNodeWithTag("photo-crop-preview").fetchSemanticsNode().boundsInRoot.height > 40f)
        compose.onNodeWithText("Use photo").assertIsDisplayed()
        compose.onNodeWithText("Precise crop controls").performScrollTo().performClick()
        compose.onNodeWithText("Vertical crop").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Use photo").assertIsDisplayed()
    }

    private fun registry(camera: Boolean) = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            val result: Any = when (contract) {
                is ActivityResultContracts.RequestPermission -> true
                is ActivityResultContracts.GetContent -> Uri.parse("content://photo-test/picked")
                is ActivityResultContracts.TakePicture -> {
                    assertTrue(camera)
                    val file = image("camera-input.jpg")
                    compose.activity.contentResolver.openOutputStream(input as Uri)!!.use { it.write(file.readBytes()) }
                    true
                }
                else -> error("Unexpected photo contract")
            }
            Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, result) }
        }
    }
    @Test fun galleryImportOpensEditorAndCancelRemovesOnlyTheStagedCopy() {
        val source = image(); val before = digest(source); editor(source, registry(false))
        compose.onNodeWithText("Choose photo").performScrollTo().performClick(); waitForPreview()
        assertTrue(staged!!.isFile)
        compose.onNodeWithContentDescription("Cancel photo editing").performClick()
        assertFalse(staged!!.exists()); assertEquals(before, digest(source))
        compose.onNodeWithText("Save changes").performClick(); assertEquals(source.name, saved!!.photoName)
    }
    @Test fun cameraCaptureOpensEditorThenOffersIdentificationAfterAcceptance() {
        val source = image(); val before = digest(source); editor(source, registry(true))
        compose.onNodeWithText("Take photo").performScrollTo().performClick(); waitForPreview()
        compose.onNodeWithText("Use photo").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Find details from this photo?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Identify photo").performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithText("Review search").fetchSemanticsNodes().isNotEmpty() }
        assertNull(saved)
        compose.onNodeWithTag("review-search-query").assertTextContains("THE HOBBIT")
        assertEquals(before, digest(source))
    }
}
