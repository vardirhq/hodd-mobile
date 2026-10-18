package no.vardir.hodd.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import no.vardir.hodd.core.Catalog
import no.vardir.hodd.core.CatalogCodec
import java.io.File
import java.util.UUID

class CatalogRepository(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "catalog.json"))
    private val lock = Mutex()
    private val mutable = MutableStateFlow(Catalog())
    val catalog = mutable.asStateFlow()
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            // AtomicFile also recovers an interrupted write from its backup.
            val snapshot = try {
                file.openRead().bufferedReader().use { CatalogCodec.decode(it.readText()) }
            } catch (error: java.io.FileNotFoundException) {
                if (file.baseFile.exists()) throw error
                Catalog()
            }
            mutable.value = snapshot
            loaded = true
        }
    }

    suspend fun update(change: (Catalog) -> Catalog) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(loaded) { "Your collection is not loaded. Retry before changing it." }
            val next = change(mutable.value)
            val output = file.startWrite()
            try {
                output.write(CatalogCodec.encode(next).toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            mutable.value = next
        }
    }

    fun photo(name: String?): File? = name?.let { File(context.filesDir, "photos/$it") }

    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        val destination = photo(name)!!
        destination.parentFile!!.mkdirs()
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open this photo." }
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 20L * 1024 * 1024) { "Choose a photo smaller than 20 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            name
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }
}
