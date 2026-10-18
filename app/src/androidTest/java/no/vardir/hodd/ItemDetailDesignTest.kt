package no.vardir.hodd

import android.graphics.Bitmap
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import no.vardir.hodd.core.Catalog
import no.vardir.hodd.core.CatalogCodec
import no.vardir.hodd.core.Collection
import no.vardir.hodd.ui.HoddApp
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import no.vardir.hodd.core.CatalogItem
import no.vardir.hodd.core.ItemStatus
import no.vardir.hodd.ui.HoddTheme
import no.vardir.hodd.ui.ItemDetail
import no.vardir.hodd.ui.configureHoddSystemBars
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ItemDetailDesignTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val item = CatalogItem(collectionId = "games", title = "Zelda: Breath of the Wild", creator = "Nintendo",
        kind = "Game", format = "Cartridge", status = ItemStatus.LENT, lentTo = "Unsure",
        series = "The Legend of Zelda", publisher = "Nintendo", language = "English", releaseYear = "2017",
        attributes = mapOf("developer" to "Nintendo", "platform" to "Nintendo Switch"),
        notes = "A favorite on the shelf. Keep the cartridge and its case together.", barcode = "123456789",
        metadataSource = "Example catalog", metadataId = "example-id", metadataUrl = "https://example.org/item")

    @Test fun detailGroupsInformationAndKeepsEditWithinReach() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        File(application.filesDir, "catalog.json").writeText(CatalogCodec.encode(Catalog(
            collections = listOf(Collection(id = "games", name = "Games")), items = listOf(item))))
        val vm = HoddViewModel(application)
        compose.runOnUiThread { compose.activity.configureHoddSystemBars() }
        compose.setContent { HoddTheme { HoddApp(vm) } }
        compose.waitUntil(10000) { vm.ready.value }
        compose.onNodeWithText("Games").performScrollTo().performClick()
        compose.onNodeWithText(item.title).performScrollTo().performClick()
        compose.onNodeWithTag("item-title").assertTextEquals(item.title)
        capture("08-detail-hero")
        compose.onNodeWithText("ON LOAN").performScrollTo().assertExists()
        compose.onNodeWithText("Unsure").assertExists()
        compose.onNodeWithText("Nintendo Switch").performScrollTo().assertExists()
        capture("09-detail-metadata")
        compose.onNodeWithText("About this edition").performScrollTo().assertExists()
        compose.onNodeWithText("The Legend of Zelda").performScrollTo().assertExists()
        compose.onNodeWithText("Identifiers & source").performScrollTo().performClick()
        compose.onNodeWithText("123456789").performScrollTo().assertExists()
        compose.onNodeWithText("https://example.org/item").performScrollTo().assertExists()
        compose.onNodeWithTag("detail-edit").assertIsDisplayed().performClick()
        compose.onNodeWithText("Title *").assertTextContains(item.title)
        compose.runOnIdle {
            assertTrue(WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars)
        }
    }

    @Test fun largeTextRetainsEveryValueAndAnAccessibleEditAction() {
        var edited = false
        compose.setContent {
            HoddTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.5f)) {
                    Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        ItemDetail(item.copy(title = "A very long collection item title that needs several lines"), "Games", null, { edited = true }, {}, false)
                    }
                }
            }
        }
        compose.onNodeWithTag("item-title").performScrollTo().assertExists()
        capture("10-detail-large-text")
        compose.onNodeWithText("English").performScrollTo().assertExists()
        compose.onNodeWithText(item.notes).performScrollTo().assertExists()
        compose.onNodeWithTag("detail-edit").assertIsDisplayed().performClick()
        assertTrue(edited)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
