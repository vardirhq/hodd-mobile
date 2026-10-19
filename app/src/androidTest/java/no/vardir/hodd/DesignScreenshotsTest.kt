package no.vardir.hodd

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import no.vardir.hodd.ui.HoddApp
import no.vardir.hodd.ui.HoddTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DesignScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        compose.waitForIdle()
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(application.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString())
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun capturesHomeCollectionAndAddItem() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        resetUiCatalog(application)
        val vm = HoddViewModel(application)
        compose.setContent { HoddTheme { HoddApp(vm) } }
        compose.waitUntil(10000) { vm.ready.value }
        compose.onNodeWithText("Explore with an example collection").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.catalog.value.items.size == 3 }
        capture("01-home")
        compose.onNodeWithText("The reading shelf").performClick()
        capture("02-collection")
        compose.onNodeWithText("Add item", useUnmergedTree = true).performClick()
        capture("03-add-item")
        compose.onNodeWithText("Title *").performTextInput("A new discovery")
        compose.onNodeWithText("Save to collection").performClick()
        compose.waitUntil(5000) { vm.catalog.value.items.size == 4 }
        compose.onNodeWithTag("item-title").assertTextEquals("A new discovery")
        capture("04-item-details")
    }
}
