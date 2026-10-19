package no.vardir.hodd.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.*

class BackupArchiveTest {
    private val catalog = Catalog(collections = listOf(Collection("books", "Books")), items = listOf(
        CatalogItem(id = "one", collectionId = "books", title = "A borrowed book", creator = "Author", photoName = "cover.jpg",
            status = ItemStatus.LENT, lentTo = "A friend", notes = "Keep this note", attributes = mapOf("pages" to "123"))))
    private fun temporary(block: (File) -> Unit) {
        val root = Files.createTempDirectory("hodd-backup-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun backup(root: File): ByteArray {
        val photo = File(root, "cover.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        return ByteArrayOutputStream().also { BackupArchive.write(catalog, { photo }, it, "0.1.0-alpha.2") }.toByteArray()
    }
    private fun entries(bytes: ByteArray): List<Pair<String, ByteArray>> = ZipInputStream(bytes.inputStream()).use { zip ->
        buildList { while (true) { val entry = zip.nextEntry ?: break; add(entry.name to zip.readBytes()) } }
    }
    private fun zip(entries: List<Pair<String, ByteArray>>) = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry() } }
    }.toByteArray()

    @Test fun `catalog details and photos survive a portable round trip`() = temporary { root ->
        val staged = BackupArchive.stage(backup(root).inputStream(), File(root, "staged"))
        assertEquals(catalog, staged.catalog)
        assertEquals(1, staged.photoCount)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), File(staged.directory, "photos/cover.jpg").readBytes())
    }
    @Test fun `tags locations and saved views survive a portable backup`() = temporary { root ->
        val organized = catalog.copy(
            items = catalog.items.map { it.copy(tags = listOf("Signed", "Rare"), location = "Shelf A") },
            savedViews = listOf(SavedView(id = "rare-shelf", name = "Rare shelf", collectionId = "books", tag = "Rare", location = "Shelf A")),
        )
        val photo = File(root, "cover.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(organized, { photo }, it, "0.1.0-alpha.2") }.toByteArray()
        val staged = BackupArchive.stage(bytes.inputStream(), File(root, "organized"))
        assertEquals(organized, staged.catalog)
        assertEquals(listOf("one"), staged.catalog.itemsForView(staged.catalog.savedViews.single()).map { it.id })
    }
    @Test fun `legacy catalog without organization fields still loads`() {
        val encoded = CatalogCodec.encode(catalog)
            .replace(Regex(",\\\"tags\\\":\\\\[\\\\]"), "")
        val restored = CatalogCodec.decode(encoded)
        assertTrue(restored.items.single().tags.isEmpty())
        assertTrue(restored.items.single().location.isEmpty())
        assertTrue(restored.savedViews.isEmpty())
    }
    @Test fun `modified content fails checksum validation and staging is removed`() = temporary { root ->
        val changed = entries(backup(root)).map { if (it.first == "catalog.json") it.first to CatalogCodec.encode(catalog.copy(items = emptyList())).toByteArray() else it }
        val destination = File(root, "staged")
        assertFailsWith<IllegalArgumentException> { BackupArchive.stage(zip(changed).inputStream(), destination) }
        assertFalse(destination.exists())
    }
    @Test fun `missing photos cannot be silently discarded`() = temporary { root ->
        val changed = entries(backup(root)).filterNot { it.first.startsWith("photos/") }
        assertFailsWith<IllegalArgumentException> { BackupArchive.stage(zip(changed).inputStream(), File(root, "staged")) }
    }
    @Test fun `path traversal is rejected before writing outside staging`() = temporary { root ->
        assertFailsWith<IllegalArgumentException> { BackupArchive.stage(zip(listOf("../escaped.json" to byteArrayOf(1))).inputStream(), File(root, "staged")) }
        assertFalse(File(root, "escaped.json").exists())
    }
    @Test fun `duplicate names are rejected`() = temporary { root ->
        val bytes = zip(listOf("photos/a.jpg" to byteArrayOf(1), "photos/b.jpg" to byteArrayOf(2)))
        val duplicate = bytes.toString(Charsets.ISO_8859_1).replace("photos/b.jpg", "photos/a.jpg").toByteArray(Charsets.ISO_8859_1)
        assertFailsWith<IllegalArgumentException> { BackupArchive.stage(duplicate.inputStream(), File(root, "staged")) }
    }
    @Test fun `oversized decompressed entries are bounded`() = temporary { root ->
        val oversized = zip(listOf("catalog.json" to ByteArray(BackupArchive.MAX_CATALOG_BYTES.toInt() + 1)))
        assertFailsWith<IllegalArgumentException> { BackupArchive.stage(oversized.inputStream(), File(root, "staged")) }
        assertFalse(File(root, "staged").exists())
    }
    @Test fun `unsupported backup versions are refused`() = temporary { root ->
        val changed = entries(backup(root)).map { if (it.first == "manifest.json") it.first to it.second.toString(Charsets.UTF_8).replace("\"formatVersion\":1", "\"formatVersion\":99").toByteArray() else it }
        assertFailsWith<IllegalArgumentException> { BackupArchive.stage(zip(changed).inputStream(), File(root, "staged")) }
    }
    @Test fun `export refuses to omit a missing cover`() = temporary { _ ->
        assertFailsWith<IllegalArgumentException> { BackupArchive.write(catalog, { null }, ByteArrayOutputStream(), "0.1.0-alpha.2") }
    }
}
