package no.vardir.hodd

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import no.vardir.hodd.core.*
import no.vardir.hodd.ui.HoddTheme
import no.vardir.hodd.ui.ItemEditor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChoiceEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var saved: CatalogItem? = null
    private val restoration by lazy { StateRestorationTester(compose) }
    private fun editor(item: CatalogItem, previous: List<CatalogItem> = emptyList()) {
        restoration.setContent { HoddTheme { ItemEditor(item, listOf(Collection("shelf", "Shelf")), "shelf", false,
            photo = { null }, importPhoto = { _, done -> done(null) }, save = { saved = it }, cancel = {}, message = {}, savedItems = previous) } }
    }
    private fun open(key: String) {
        compose.waitForIdle()
        compose.onNodeWithTag("choice-$key").performScrollTo().performClick()
        compose.waitForIdle()
    }
    private fun search(value: String) {
        compose.waitForIdle()
        compose.onNodeWithTag("choice-search").performTextReplacement(value)
        compose.waitForIdle()
    }
    private fun select(value: String) {
        compose.waitForIdle()
        compose.onNodeWithTag("choice-results").performScrollToNode(hasTestTag("option-$value"))
        compose.waitForIdle()
        compose.onNodeWithTag("option-$value").performClick()
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, name).outputStream().use { compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun brandPlatformSearchAndConfirmedBrandChangePreserveOtherDetails() {
        editor(CatalogItem(collectionId = "shelf", title = "My game", kind = "Game", notes = "Keep my notes", attributes = mapOf("secret" to "Keep this too")))
        open("platformBrand"); search("Nintendo"); screenshot("14-platform-brands.png"); select("Nintendo")
        open("platform"); search("switch"); screenshot("15-platform-search.png"); select("Nintendo Switch 2")
        open("platformBrand"); search("Sony"); select("Sony")
        compose.onNodeWithText("Keep platform").performClick()
        compose.onNodeWithTag("choice-platform").assertTextContains("Nintendo Switch 2")
        open("platformBrand"); search("Sony"); select("Sony")
        compose.onNodeWithText("Change brand").performClick()
        open("platform"); search("ps5"); select("PlayStation 5")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Save changes").performClick()
        assertEquals("Sony", saved!!.attributes["platformBrand"])
        assertEquals("PlayStation 5", saved!!.attributes["platform"])
        assertEquals("Keep this too", saved!!.attributes["secret"])
        assertEquals("Keep my notes", saved!!.notes)
    }
    @Test fun customCancelAndExistingValuesRemainIntactAndSavedChoicesAreReusable() {
        val original = CatalogItem(collectionId = "shelf", title = "A book", format = "Hand stitched edition", publisher = "Tiny publisher", language = "Old dialect", attributes = mapOf("isbn" to "Keep this ISBN"))
        editor(original, listOf(original.copy(condition = "Personal grading scale")))
        open("language"); search("another dialect"); compose.onNodeWithText("Custom…").performClick()
        compose.onNodeWithTag("choice-custom-input").performTextReplacement("Discard this")
        compose.onNodeWithText("Back").performClick(); compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("choice-language").assertTextContains("Old dialect")
        open("condition"); search("personal"); select("Personal grading scale")
        open("language"); search("Norwegian"); select("Norwegian Bokmål")
        open("format"); search("Hand bound"); compose.onNodeWithText("Custom…").performClick()
        compose.onNodeWithTag("choice-custom-input").performTextReplacement("Hand bound by me")
        screenshot("16-custom-choice.png")
        compose.onNodeWithText("Use custom value").performClick()
        compose.onNodeWithText("Save changes").performClick()
        assertEquals("Hand bound by me", saved!!.format)
        assertEquals("Tiny publisher", saved!!.publisher)
        assertEquals("Norwegian Bokmål", saved!!.language)
        assertEquals("Personal grading scale", saved!!.condition)
        assertEquals("Keep this ISBN", saved!!.attributes["isbn"])
    }
    @Test fun movieRegionsAndCoinDenominationsFollowTheirParentSelections() {
        editor(CatalogItem(collectionId = "shelf", title = "My movie", kind = "Movie", format = "DVD"))
        open("region"); search("Region 2"); select("Region 2")
        open("format"); search("Blu-ray"); select("Blu-ray")
        open("region"); search("Region B"); select("Region B")
        open("kind"); select("Coin")
        open("country"); search("Norway"); select("Norway")
        open("denomination"); search("20 kroner"); select("20 kroner")
        compose.onNodeWithText("Save changes").performClick()
        assertEquals("Coin", saved!!.kind)
        assertEquals("Norway", saved!!.attributes["country"])
        assertEquals("20 kroner", saved!!.attributes["denomination"])
        // Hidden attributes remain intact when changing type; they are never silently removed.
        assertEquals("Region B", saved!!.attributes["region"])
    }
}
