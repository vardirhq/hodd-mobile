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
import no.vardir.hodd.BuildConfig
import no.vardir.hodd.core.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CatalogRepository(private val context: Context, private val root: File = context.filesDir) {
    private val file = AtomicFile(File(root, "catalog.json"))
    private val history = RecoveryStore(root)
    private val lock = locks.computeIfAbsent(root.absolutePath) { Mutex() }
    private val mutable = MutableStateFlow(Catalog())
    val catalog = mutable.asStateFlow()
    private val recoveryMutable = MutableStateFlow(RecoveryState())
    internal val recovery = recoveryMutable.asStateFlow()
    private var loaded = false
    private var recoveryWarning: String? = null
    private fun refreshRecovery() { recoveryMutable.value = history.state().copy(warning = recoveryWarning) }

    private fun readCurrent(): Catalog = try {
        file.openRead().use { input ->
            require(file.baseFile.length() <= BackupArchive.MAX_CATALOG_BYTES) { "The saved catalog is too large to open safely." }
            CatalogCodec.decode(input.bufferedReader().readText())
        }
    } catch (error: java.io.FileNotFoundException) {
        if (file.baseFile.exists() || history.hasCopies || File(root, "photos").listFiles()?.isNotEmpty() == true) throw error
        Catalog()
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            loaded = false
            try {
                val snapshot = readCurrent()
                recoveryWarning = if (file.baseFile.exists()) runCatching { history.remember(snapshot) }.exceptionOrNull()?.let {
                    "Could not create a recovery copy. Export a backup before making changes."
                } else null
                mutable.value = snapshot; loaded = true
            } finally { refreshRecovery() }
        }
    }

    private fun commit(next: Catalog, preserveUnreadable: Boolean = false) {
        val bytes = CatalogCodec.encode(next).toByteArray(Charsets.UTF_8)
        require(bytes.size <= BackupArchive.MAX_CATALOG_BYTES) { "The collection exceeds the supported size." }
        CatalogCodec.decode(bytes.toString(Charsets.UTF_8))
        try { history.remember(readCurrent()) }
        catch (error: Exception) {
            if (!preserveUnreadable) throw error
            // Preserve the original bytes before an explicitly confirmed recovery/import.
            if (file.baseFile.exists()) history.quarantine(file.baseFile)
            if (loaded) history.remember(mutable.value)
        }
        history.remember(next)
        atomicWrite(file, bytes)
        mutable.value = next
        loaded = true
        // Cleanup cannot turn a committed save into a reported failure.
        runCatching { history.prune() }
        recoveryWarning = null
        refreshRecovery()
    }

    suspend fun update(change: (Catalog) -> Catalog) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(loaded) { "Your collection is not loaded. Recover or retry before changing it." }
            try { commit(change(readCurrent())) }
            finally { refreshRecovery() }
        }
    }

    internal suspend fun restoreRecovery(id: String) = withContext(Dispatchers.IO) {
        lock.withLock { commit(history.read(id), preserveUnreadable = true) }
    }

    fun photo(name: String?): File? = name?.takeIf { Regex("[a-zA-Z0-9_-]+\\.(jpg|png|webp)").matches(it) }?.let { File(root, "photos/$it") }
    private fun workFile(name: String): File {
        val folder = File(context.cacheDir, "hodd-backup-work")
        check(folder.isDirectory || folder.mkdirs()) { "Could not prepare backup storage." }
        return File(folder, "${UUID.randomUUID()}-$name")
    }

    suspend fun stageBackup(uri: Uri): StagedBackup = withContext(Dispatchers.IO) {
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open the selected backup." }
        input.use { BackupArchive.stage(it, workFile("import")) }
    }

    suspend fun restoreBackup(staged: StagedBackup) = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                // New immutable photo names mean a failed restore cannot overwrite existing covers.
                val names = staged.catalog.items.mapNotNull { it.photoName }.distinct().associateWith { old ->
                    val name = "${UUID.randomUUID()}.${old.substringAfterLast('.')}"
                    val target = photo(name)!!
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Could not restore photos." }
                    File(staged.directory, "photos/$old").inputStream().use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output); output.fd.sync() }
                    }
                    require(BackupArchive.digest(target.inputStream()) == staged.manifest.entries["photos/$old"]) { "A staged photo is damaged. Nothing was replaced." }
                    name
                }
                val next = staged.catalog.copy(items = staged.catalog.items.map { item -> item.copy(photoName = item.photoName?.let { names.getValue(it) }) })
                commit(next, preserveUnreadable = true)
            } catch (error: Exception) {
                // A candidate recovery revision may already reference these files even if
                // the primary write failed. Retain photos for explicit later recovery.
                throw error
            }
        }
    }

    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(loaded) { "Open or recover your collection before exporting it." }
            val temporary = workFile("export.zip")
            try {
                val snapshot = readCurrent()
                FileOutputStream(temporary).use { output ->
                    BackupArchive.write(snapshot, { name -> photo(name) }, output, BuildConfig.VERSION_NAME)
                }
                val expected = BackupArchive.digest(temporary.inputStream())
                val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Could not write to the chosen location." }
                output.use { destination -> temporary.inputStream().use { it.copyTo(destination) } }
                val actual = context.contentResolver.openInputStream(uri)?.let { BackupArchive.digest(it) }
                check(actual == expected) { "The exported file could not be verified. Keep your current collection and try another location." }
                history.exported()
                refreshRecovery()
            } finally { temporary.delete() }
        }
    }

    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        val destination = photo(name)!!
        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs()) { "Could not prepare photo storage." }
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open this photo." }
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count
                        require(total <= BackupArchive.MAX_PHOTO_BYTES) { "Choose a photo smaller than 20 MB." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            name
        } catch (error: Exception) { destination.delete(); throw error }
    }

    companion object { private val locks = ConcurrentHashMap<String, Mutex>() }
}
