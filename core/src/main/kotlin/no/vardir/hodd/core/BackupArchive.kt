package no.vardir.hodd.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupManifest(val formatVersion: Int = 1, val createdAt: Long, val appVersion: String, val entries: Map<String, String>)
data class StagedBackup(val directory: File, val catalog: Catalog, val manifest: BackupManifest) {
    val photoCount get() = manifest.entries.keys.count { it.startsWith("photos/") }
    fun discard() { directory.deleteRecursively() }
}

/** Portable, checksummed backups. Import never writes to the live collection. */
object BackupArchive {
    const val MAX_CATALOG_BYTES = 16L * 1024 * 1024
    const val MAX_PHOTO_BYTES = 20L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 2L * 1024 * 1024
    private const val MAX_ENTRIES = 10002
    private val photoPath = Regex("photos/[a-zA-Z0-9_-]+\\.(jpg|png|webp)")
    private val json = Json { encodeDefaults = true }
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    fun digest(input: InputStream): String = input.use {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) { val count = it.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        hash.digest().hex()
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    fun write(catalog: Catalog, photo: (String) -> File?, output: OutputStream, appVersion: String) {
        val bytes = CatalogCodec.encode(catalog).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_CATALOG_BYTES) { "The catalog is too large for a backup." }
        CatalogCodec.decode(bytes.toString(Charsets.UTF_8))
        val names = catalog.items.mapNotNull { it.photoName }.distinct().sorted()
        require(names.size + 2 <= MAX_ENTRIES) { "The backup has too many photos." }
        val hashes = linkedMapOf<String, String>()
        var total = 0L
        ZipOutputStream(output).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            fun entry(path: String, input: InputStream, limit: Long) {
                zip.putNextEntry(ZipEntry(path))
                val hash = MessageDigest.getInstance("SHA-256")
                var size = 0L
                input.use { source ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = source.read(buffer); if (count < 0) break
                        size += count; total += count
                        require(size <= limit && total <= MAX_TOTAL_BYTES) { "Backup limits exceeded (20 MB per photo, 2 GB total)." }
                        zip.write(buffer, 0, count); hash.update(buffer, 0, count)
                    }
                }
                zip.closeEntry(); hashes[path] = hash.digest().hex()
            }
            entry("catalog.json", bytes.inputStream(), MAX_CATALOG_BYTES)
            names.forEach { name ->
                val image = photo(name)
                require(image?.isFile == true) { "A cover photo is missing. Your collection has not been changed." }
                entry("photos/$name", image!!.inputStream(), MAX_PHOTO_BYTES)
            }
            val manifest = BackupManifest(createdAt = System.currentTimeMillis(), appVersion = appVersion, entries = hashes)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun stage(input: InputStream, destination: File): StagedBackup {
        require(!destination.exists()) { "Choose a fresh staging directory." }
        check(destination.mkdirs()) { "Could not prepare backup storage." }
        try {
            val hashes = linkedMapOf<String, String>()
            val seen = mutableSetOf<String>()
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(seen.add(entry.name) && seen.size <= MAX_ENTRIES) { "Duplicate entries or too many files in backup." }
                    val limit = when {
                        entry.name == "catalog.json" -> MAX_CATALOG_BYTES
                        entry.name == "manifest.json" -> MAX_MANIFEST_BYTES
                        photoPath.matches(entry.name) && !entry.isDirectory -> MAX_PHOTO_BYTES
                        else -> throw IllegalArgumentException("Unexpected or unsafe file in backup.")
                    }
                    val file = File(destination, entry.name)
                    check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Could not stage backup files." }
                    val hash = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer); if (count < 0) break
                            size += count; total += count
                            require(size <= limit && total <= MAX_TOTAL_BYTES) { "Backup limits exceeded (20 MB per photo, 2 GB total)." }
                            output.write(buffer, 0, count); hash.update(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                    hashes[entry.name] = hash.digest().hex()
                    zip.closeEntry()
                }
            }
            require("catalog.json" in hashes && "manifest.json" in hashes) { "This is not a complete Hodd backup." }
            val manifest = json.decodeFromString(BackupManifest.serializer(), File(destination, "manifest.json").readText())
            require(manifest.createdAt > 0 && manifest.appVersion.length in 1..100) { "Invalid backup metadata." }
            require(manifest.formatVersion == 1) { "This backup needs a newer version of Hodd." }
            require(manifest.entries == hashes.filterKeys { it != "manifest.json" }) { "Backup checksum mismatch. No collection data was replaced." }
            val catalog = CatalogCodec.decode(File(destination, "catalog.json").readText())
            val expected = catalog.items.mapNotNull { it.photoName }.map { "photos/$it" }.toSet() + "catalog.json"
            require(expected == manifest.entries.keys) { "The backup is missing photos or contains unreferenced files." }
            return StagedBackup(destination, catalog, manifest)
        } catch (error: Exception) {
            destination.deleteRecursively()
            throw error
        }
    }
}
