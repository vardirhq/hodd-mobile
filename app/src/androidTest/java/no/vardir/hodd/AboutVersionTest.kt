package no.vardir.hodd

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import no.vardir.hodd.core.Catalog
import no.vardir.hodd.core.CatalogCodec
import no.vardir.hodd.ui.HoddApp
import no.vardir.hodd.ui.HoddTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class AboutVersionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun aboutShowsInstalledVersionAndOfflineChangelog() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        File(app.filesDir, "catalog.json").writeText(CatalogCodec.encode(Catalog()))
        val vm = HoddViewModel(app)
        compose.setContent { HoddTheme { HoddApp(vm) } }
        compose.waitUntil(10000) { vm.ready.value }
        compose.onNodeWithContentDescription("About Hodd").performClick()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText("Build ${BuildConfig.VERSION_CODE}").assertIsDisplayed()
        compose.onNodeWithText("View changelog").performClick()
        compose.onNodeWithText("Unreleased").assertIsDisplayed()
        compose.onNodeWithText("${BuildConfig.VERSION_NAME} - ", substring = true).assertIsDisplayed()
        val directory = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "11-about-changelog.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("About Hodd").assertIsDisplayed()
    }
}
