package no.vardir.hodd

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import no.vardir.hodd.core.*
import no.vardir.hodd.data.CatalogRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: File
    @org.junit.Before fun prepareStorage() { root = File(context.cacheDir, "persistence-${java.util.UUID.randomUUID()}").apply { mkdirs() } }
    @org.junit.After fun removeStorage() { root.deleteRecursively() }
    @Test fun savesSurviveRepositoryRestart() = runBlocking {
        File(root, "catalog.json").delete()
        val repository = CatalogRepository(context, root)
        repository.load()
        repository.update { it.addCollection("Books") }
        repository.update { it.save(CatalogItem(collectionId = it.collections.single().id, title = "My book")) }
        val reopened = CatalogRepository(context, root)
        reopened.load()
        assertEquals(repository.catalog.value, reopened.catalog.value)
    }
    @Test fun corruptFileIsPreservedAndCannotBeOverwritten() = runBlocking {
        val file = File(root, "catalog.json")
        file.writeText("broken")
        val repository = CatalogRepository(context, root)
        try { repository.load(); fail("Expected load failure") } catch (_: IllegalArgumentException) { }
        try { repository.update { Catalog() }; fail("Expected write refusal") } catch (_: IllegalStateException) { }
        assertEquals("broken", file.readText())
        file.delete()
        Unit
    }
}
