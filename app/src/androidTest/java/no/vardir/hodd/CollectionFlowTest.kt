package no.vardir.hodd

import android.app.Application
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
class CollectionFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun createsCollectionAndSavesItem() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        File(application.filesDir, "catalog.json").delete()
        val vm = HoddViewModel(application)
        compose.setContent { HoddTheme { HoddApp(vm) } }
        compose.waitUntil(10000) { vm.ready.value }
        compose.onNodeWithText("Create a collection").performClick()
        compose.onNodeWithText("Collection name").performTextInput("My books")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { vm.catalog.value.collections.size == 1 }
        compose.onNodeWithText("Add item", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Title *").performTextInput("My first book")
        compose.onNodeWithText("Save to collection").performClick()
        compose.waitUntil(5000) { vm.catalog.value.items.size == 1 }
        compose.onNodeWithTag("item-title").assertTextEquals("My first book")
        compose.onNodeWithText("Edit item").assertExists()
    }
}
