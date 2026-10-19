package no.vardir.hodd

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import no.vardir.hodd.core.*
import no.vardir.hodd.data.CatalogRepository
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RepositoryBackupTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun <T> storage(block: (File) -> T): T {
        val root = File(context.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        return try { block(root) } finally { root.deleteRecursively() }
    }
    private fun fixture(root: File): Catalog {
        val photo = File(root, "photos/cover.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val catalog = Catalog(collections = listOf(Collection("shelf", "Books")), items = listOf(CatalogItem(
            id = "one", collectionId = "shelf", title = "Keep my collection", notes = "My notes", photoName = photo.name)))
        File(root, "catalog.json").writeText(CatalogCodec.encode(catalog))
        return catalog
    }
    @Test fun exportedBackupRestoresDetailsAndPhotosWithSafeNewNames() = storage { root -> runBlocking {
        val source = File(root, "source").apply { mkdirs() }; val original = fixture(source)
        val repo = CatalogRepository(context, source); repo.load()
        val archive = File(root, "export.zip"); repo.exportBackup(Uri.fromFile(archive))
        assertNotNull(repo.recovery.value.lastExportAt)
        val destination = File(root, "destination").apply { mkdirs() }
        val other = CatalogRepository(context, destination); other.load(); other.update { it.addCollection("Previous") }
        val staged = other.stageBackup(Uri.fromFile(archive))
        assertEquals("Previous", other.catalog.value.collections.single().name)
        other.restoreBackup(staged); staged.discard()
        val restored = other.catalog.value
        assertEquals(original.items.single().notes, restored.items.single().notes)
        assertNotEquals("cover.jpg", restored.items.single().photoName)
        assertArrayEquals(byteArrayOf(1, 2, 3), other.photo(restored.items.single().photoName)!!.readBytes())
        assertTrue(other.recovery.value.copies.any { it.collections == 1 && it.items == 0 })
        val reopened = CatalogRepository(context, destination); reopened.load()
        assertEquals(restored, reopened.catalog.value)
    } }
    @Test fun invalidImportCannotChangeCurrentCatalogOrPhotos() = storage { root -> runBlocking {
        val original = fixture(root); val bytes = File(root, "catalog.json").readBytes()
        val repo = CatalogRepository(context, root); repo.load()
        val bad = File(root, "bad.zip").apply { writeText("not a backup") }
        try { repo.stageBackup(Uri.fromFile(bad)); fail("Invalid backup accepted") } catch (_: Exception) { }
        assertEquals(original, repo.catalog.value)
        assertArrayEquals(bytes, File(root, "catalog.json").readBytes())
        assertArrayEquals(byteArrayOf(1, 2, 3), repo.photo("cover.jpg")!!.readBytes())
    } }
    @Test fun unreadablePrimaryIsKeptWhileAValidatedCopyCanBeRecovered() = storage { root -> runBlocking {
        val original = fixture(root); val repo = CatalogRepository(context, root); repo.load()
        val copy = repo.recovery.value.copies.single()
        File(root, "catalog.json").writeText("damaged catalog")
        val reopened = CatalogRepository(context, root)
        try { reopened.load(); fail("Unreadable catalog opened") } catch (_: IllegalArgumentException) { }
        try { reopened.update { Catalog() }; fail("Unreadable catalog overwritten") } catch (_: IllegalStateException) { }
        reopened.restoreRecovery(copy.id)
        assertEquals(original, reopened.catalog.value)
        assertTrue(File(root, "preserved-catalogs").listFiles()!!.any { it.readText() == "damaged catalog" })
    } }
    @Test fun missingPrimaryWithHistoryDoesNotBecomeAnEmptyCollection() = storage { root -> runBlocking {
        val original = fixture(root); val repo = CatalogRepository(context, root); repo.load()
        File(root, "catalog.json").delete()
        val reopened = CatalogRepository(context, root)
        try { reopened.load(); fail("Missing catalog was treated as new") } catch (_: java.io.FileNotFoundException) { }
        reopened.restoreRecovery(repo.recovery.value.copies.single().id)
        assertEquals(original, reopened.catalog.value)
    } }
    @Test fun failedRecoveryWriteStopsTheCatalogSave() = storage { root -> runBlocking {
        val original = fixture(root)
        File(root, "recovery").writeText("Not a directory")
        val repo = CatalogRepository(context, root); repo.load()
        assertNotNull(repo.recovery.value.warning)
        try { repo.update { it.copy(items = emptyList()) }; fail("Save bypassed recovery failure") } catch (_: IllegalStateException) { }
        assertEquals(original, CatalogCodec.decode(File(root, "catalog.json").readText()))
        assertEquals(original, repo.catalog.value)
    } }
    @Test fun retentionIsBoundedAndAnAccidentalDeleteCanBeUndone() = storage { root -> runBlocking {
        fixture(root); val repo = CatalogRepository(context, root); repo.load()
        repeat(24) { index -> repo.update { it.copy(items = it.items.map { item -> item.copy(notes = "Revision $index") }) } }
        val beforeDelete = repo.catalog.value
        repo.update { it.copy(items = emptyList()) }
        assertTrue(File(root, "recovery").listFiles()!!.count { it.extension == "json" } <= 20)
        val saved = repo.recovery.value.copies.first { it.items == 1 }
        repo.restoreRecovery(saved.id)
        assertEquals(beforeDelete, repo.catalog.value)
        assertTrue(repo.photo("cover.jpg")!!.exists())
    } }
}
