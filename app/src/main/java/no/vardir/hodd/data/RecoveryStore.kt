package no.vardir.hodd.data

import android.util.AtomicFile
import no.vardir.hodd.core.BackupArchive
import no.vardir.hodd.core.Catalog
import no.vardir.hodd.core.CatalogCodec
import java.io.File

internal data class RecoveryCopy(val id: String, val createdAt: Long, val collections: Int, val items: Int)
internal data class RecoveryState(val copies: List<RecoveryCopy> = emptyList(), val lastExportAt: Long? = null, val warning: String? = null)

/** Catalog revisions share immutable photos; photo files are never removed by restore/delete. */
internal class RecoveryStore(private val root: File) {
    private val directory = File(root, "recovery")
    val hasCopies get() = directory.listFiles()?.any { it.extension == "json" } == true
    fun remember(catalog: Catalog) {
        val bytes = CatalogCodec.encode(catalog).toByteArray(Charsets.UTF_8)
        require(bytes.size <= BackupArchive.MAX_CATALOG_BYTES) { "The catalog exceeds the supported size." }
        val hash = BackupArchive.digest(bytes)
        if (directory.listFiles()?.any { it.name.endsWith("-$hash.json") && runCatching { BackupArchive.digest(it.inputStream()) == hash }.getOrDefault(false) } == true) return
        check(directory.isDirectory || directory.mkdirs()) { "Could not create a recovery copy. Nothing was replaced." }
        val target = AtomicFile(File(directory, "${System.currentTimeMillis()}-$hash.json"))
        atomicWrite(target, bytes)
    }
    fun read(id: String): Catalog {
        require(Regex("[0-9]+-[0-9a-f]{64}\\.json").matches(id)) { "Invalid recovery copy." }
        val file = File(directory, id)
        require(file.isFile && file.length() <= BackupArchive.MAX_CATALOG_BYTES) { "Recovery copy is unavailable." }
        val bytes = file.readBytes()
        require(BackupArchive.digest(bytes) == id.substringAfter('-').removeSuffix(".json")) { "This recovery copy is damaged." }
        return CatalogCodec.decode(bytes.toString(Charsets.UTF_8))
    }
    fun state() = RecoveryState(
        copies = directory.listFiles().orEmpty().filter { it.extension == "json" }.sortedByDescending { it.lastModified() }.mapNotNull { file ->
            runCatching { val catalog = read(file.name); RecoveryCopy(file.name, file.name.substringBefore('-').toLong(), catalog.collections.size, catalog.items.size) }.getOrNull()
        },
        lastExportAt = runCatching { File(root, "last-backup-export").takeIf { it.length() <= 64 }?.readText()?.trim()?.toLong() }.getOrNull(),
    )
    fun exported() = atomicWrite(AtomicFile(File(root, "last-backup-export")), System.currentTimeMillis().toString().toByteArray())
    fun prune() {
        directory.listFiles().orEmpty().filter { it.extension == "json" }.sortedByDescending { it.lastModified() }.drop(20).forEach { it.delete() }
    }
    fun quarantine(file: File) {
        require(file.length() <= BackupArchive.MAX_CATALOG_BYTES) { "The existing file is too large to preserve safely. It has not been replaced." }
        val folder = File(root, "preserved-catalogs")
        check(folder.isDirectory || folder.mkdirs()) { "Could not preserve the existing catalog." }
        atomicWrite(AtomicFile(File(folder, "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.json")), file.readBytes())
    }
}

internal fun atomicWrite(file: AtomicFile, bytes: ByteArray) {
    val output = file.startWrite()
    try {
        output.write(bytes); output.fd.sync(); file.finishWrite(output)
        check(file.openRead().use { it.readBytes() }.contentEquals(bytes)) { "Could not verify the saved file." }
    }
    catch (error: Exception) { file.failWrite(output); throw error }
}
