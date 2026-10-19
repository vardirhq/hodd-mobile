package no.vardir.hodd

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import no.vardir.hodd.core.*
import no.vardir.hodd.ui.HoddApp
import no.vardir.hodd.ui.HoddTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

internal fun resetUiCatalog(context: Context) {
    listOf("catalog.json", "catalog.json.bak", "catalog.json.new", "recovery", "photos", "preserved-catalogs", "last-backup-export").forEach { File(context.filesDir, it).deleteRecursively() }
}

class BackupFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun seed(): Catalog {
        resetUiCatalog(app)
        val catalog = Catalog(collections = listOf(Collection("books", "Books")), items = listOf(CatalogItem(id = "kept", collectionId = "books", title = "Keep this book", notes = "Keep this note")))
        File(app.filesDir, "catalog.json").writeText(CatalogCodec.encode(catalog))
        return catalog
    }
    private fun screenshot(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, name).outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun exportCancelAndConfirmedImportUseTheActualBackupFlow() {
        val original = seed()
        val backup = File(app.cacheDir, "ui-export.zip")
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(backup)))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val vm = HoddViewModel(app)
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { HoddTheme { HoddApp(vm) } } }
        compose.waitUntil(10000) { vm.ready.value }
        compose.onNodeWithContentDescription("About Hodd").performClick()
        compose.onNodeWithText("Backups & recovery").performClick()
        compose.onNodeWithText("Export backup").performScrollTo().performClick()
        compose.waitUntil(15000) { vm.recovery.value.lastExportAt != null && !vm.busy.value }
        assertTrue(backup.length() > 0)
        screenshot("12-backups.png")
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { vm.addCollection("After backup") {} }
        compose.waitUntil(5000) { vm.catalog.value.collections.size == 2 }
        compose.onNodeWithContentDescription("About Hodd").performClick()
        compose.onNodeWithText("Backups & recovery").performClick()
        compose.onNodeWithText("Import backup").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.backups.preview.value != null && !vm.busy.value }
        screenshot("13-import-preview.png")
        assertEquals(2, vm.catalog.value.collections.size)
        compose.onNodeWithText("Cancel restore").performClick()
        compose.runOnIdle { assertNull(vm.backups.preview.value); assertEquals(2, vm.catalog.value.collections.size) }
        compose.onNodeWithText("Import backup").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.backups.preview.value != null && !vm.busy.value }
        compose.onNodeWithText("Replace collection").performClick()
        compose.waitUntil(10000) { vm.backups.preview.value == null && !vm.busy.value }
        assertEquals(original, vm.catalog.value)
        assertTrue(vm.recovery.value.copies.any { it.collections == 2 })
        compose.onNodeWithText("Close").performClick()
    }
    @Test fun aDamagedCollectionOffersExplicitRecoveryInsteadOfAnEmptyHome() {
        val original = seed(); val vm = HoddViewModel(app)
        compose.setContent { HoddTheme { HoddApp(vm) } }
        compose.waitUntil(10000) { vm.ready.value && vm.recovery.value.copies.isNotEmpty() }
        val id = vm.recovery.value.copies.single().id
        File(app.filesDir, "catalog.json").writeText("unreadable")
        compose.runOnIdle { vm.load() }
        compose.waitUntil(10000) { !vm.ready.value && vm.error.value != null }
        compose.onNodeWithText("Couldn’t open your collection").assertIsDisplayed()
        compose.onNodeWithText("Backups & recovery").performClick()
        compose.onNodeWithTag("recovery-$id").performScrollTo().performClick()
        compose.onNodeWithText("Replace collection").performClick()
        compose.waitUntil(10000) { vm.ready.value && !vm.busy.value }
        assertEquals(original, vm.catalog.value)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("About Hodd").assertIsDisplayed()
    }
}
