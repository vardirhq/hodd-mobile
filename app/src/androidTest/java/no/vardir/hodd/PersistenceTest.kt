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
    @Test fun savesSurviveRepositoryRestart() = runBlocking {
        File(context.filesDir, "catalog.json").delete()
        val repository = CatalogRepository(context)
        repository.load()
        repository.update { it.addCollection("Books") }
        repository.update { it.save(CatalogItem(collectionId = it.collections.single().id, title = "My book")) }
        val reopened = CatalogRepository(context)
        reopened.load()
        assertEquals(repository.catalog.value, reopened.catalog.value)
    }
    @Test fun corruptFileIsPreservedAndCannotBeOverwritten() = runBlocking {
        val file = File(context.filesDir, "catalog.json")
        file.writeText("broken")
        val repository = CatalogRepository(context)
        try { repository.load(); fail("Expected load failure") } catch (_: IllegalArgumentException) { }
        try { repository.update { Catalog() }; fail("Expected write refusal") } catch (_: IllegalStateException) { }
        assertEquals("broken", file.readText())
        file.delete()
        Unit
    }
}
