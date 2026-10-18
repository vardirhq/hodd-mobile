package no.vardir.hodd

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import no.vardir.hodd.core.CatalogItem
import no.vardir.hodd.core.Collection
import no.vardir.hodd.data.LocalItemAssistant
import no.vardir.hodd.ui.GuidedFieldCamera
import no.vardir.hodd.ui.HoddTheme
import no.vardir.hodd.ui.ItemEditor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GuidedFieldScanTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun onlyTextInsideFrameIsReadIncludingAnExifRotatedPhoto() = runBlocking {
        val assistant = LocalItemAssistant(context)
        try {
            for (rotated in listOf(false, true)) {
                val file = File(context.cacheDir, "field-crop-$rotated.jpg")
                try {
                    writeFixture(file, rotated)
                    val text = assistant.readFieldStrip(file).joinToString(" ")
                    assertTrue("Missing center text: $text", text.contains("INSIDE FRAME"))
                    assertFalse(text.contains("OUTSIDE"))
                    assertFalse(text.contains("EDGE"))
                    assertFalse(text.contains("RIGHT"))
                } finally { file.delete() }
            }
        } finally { assistant.close() }
    }

    @Test fun scanningCreatorFillsCreatorAndKeepsPublisher() {
        val photos = File(context.cacheDir, "field-test-photos").apply { mkdirs() }
        compose.setContent {
            HoddTheme {
                ItemEditor(CatalogItem(collectionId = "shelf", title = "My book", publisher = "Original publisher"),
                    listOf(Collection(id = "shelf", name = "Shelf")), "shelf", false,
                    photo = { name -> name?.let { File(photos, it) } }, importPhoto = { _, done -> done(null) },
                    save = {}, cancel = {}, message = {},
                    fieldCamera = { field, output, captured, _, _ ->
                        TextButton(onClick = { writeFixture(output); captured(output) }) { Text("Capture $field fixture") }
                    })
            }
        }
        compose.onNodeWithContentDescription("Scan Creator").performScrollTo().performClick()
        compose.onNodeWithText("Capture Creator fixture").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Choose recognized text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Insert into Creator. Other fields will stay as they are.").assertExists()
        compose.onNodeWithText("INSIDE FRAME").performClick()
        compose.onNodeWithText("Creator / author").assertTextContains("INSIDE FRAME")
        compose.onNodeWithText("Publisher / label").performScrollTo().assertTextContains("Original publisher")
    }

    @Test fun editorCameraKeepsCaptureAboveButtonNavigationAndCanCapture() {
        val messages = mutableListOf<String>()
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent {
            HoddTheme {
                ItemEditor(CatalogItem(collectionId = "shelf", title = "My book"),
                    listOf(Collection(id = "shelf", name = "Shelf")), "shelf", false,
                    photo = { null }, importPhoto = { _, done -> done(null) },
                    save = {}, cancel = {}, message = { messages += it })
            }
        }
        // Open from a focused field, as on the phone, rather than rendering the camera alone.
        compose.onNodeWithText("Creator / author").performClick().performTextInput("Draft author")
        compose.onNodeWithContentDescription("Scan Creator").performScrollTo().performClick()
        compose.onNodeWithTag("field-scan-frame").assertExists()
        compose.onNodeWithText("Only text inside the frame will be read").assertExists()
        compose.waitUntil(20000) {
            messages.isNotEmpty() || compose.onAllNodesWithTag("field-capture-button").fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        }
        assertTrue("Camera failed: $messages", messages.isEmpty())
        val button = compose.onNodeWithTag("field-capture-button")
        button.assertIsDisplayed().assertIsEnabled()
        compose.waitForIdle()
        val density = context.resources.displayMetrics.density
        val navigationBottom = compose.runOnIdle {
            requireNotNull(ViewCompat.getRootWindowInsets(compose.activity.window.decorView))
                .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        }
        assertTrue("Run this regression with three-button navigation", navigationBottom / density >= 32f)
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val bounds = button.getUnclippedBoundsInRoot()
        assertTrue("Capture button overlaps system navigation: $bounds / $screen",
            bounds.bottom.value <= screen.bottom.value - navigationBottom / density - 12f)
        assertTrue("Capture target is too small", bounds.bottom.value - bounds.top.value >= 55.5f)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "07-guided-field-camera.png").outputStream().use {
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Exercise a real capture to prove the visible button is actionable.
        button.performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithTag("field-scan-frame").fetchSemanticsNodes().isEmpty() }
        assertFalse("Camera could not capture: $messages", messages.any { it.contains("Could not capture") })
    }

    private fun writeFixture(file: File, rotated: Boolean = false) {
        val bitmap = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 70f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        canvas.drawText("OUTSIDE TOP", 150f, 300f, paint)
        canvas.drawText("INSIDE FRAME", 150f, 920f, paint)
        canvas.drawText("OUTSIDE BOTTOM", 150f, 1500f, paint)
        paint.textSize = 16f
        canvas.drawText("EDGE", 4f, 920f, paint)
        canvas.drawText("RIGHT", 1140f, 920f, paint)
        val stored = if (rotated) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(-90f) }, true) else bitmap
        try {
            file.outputStream().use { stored.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            if (rotated) ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
        } finally { if (stored !== bitmap) stored.recycle(); bitmap.recycle() }
    }
}
