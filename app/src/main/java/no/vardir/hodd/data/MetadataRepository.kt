package no.vardir.hodd.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import no.vardir.hodd.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

/** Human-triggered, keyless lookups. No catalog or photos are uploaded. */
interface ItemMetadataSource {
    suspend fun lookup(kind: String, barcode: String, title: String, creator: String, platform: String = ""): List<ItemSuggestion>
    suspend fun importCover(suggestion: ItemSuggestion): String
}

class MetadataRepository(private val context: Context, private val transport: ((String, Int) -> ByteArray)? = null) : ItemMetadataSource {
    companion object {
        private val requestLock = Mutex()
        private var lastRequest = 0L
        private const val USER_AGENT = "HoddMobile/0.1 (https://github.com/vardirhq/hodd-mobile)"
    }

    override suspend fun lookup(kind: String, barcode: String, title: String, creator: String, platform: String): List<ItemSuggestion> = withContext(Dispatchers.IO) {
        val code = cleanBarcode(barcode)
        if (kind in listOf("Game", "Movie")) return@withContext lookupWikidata(kind, title, platform)
        val music = kind in listOf("Vinyl", "CD", "Music")
        require(music || kind in listOf("Book", "Comic")) { "Online lookup supports books, music, games and movies. Use a cover photo or enter details for this type." }
        require(code.isNotBlank() || title.isNotBlank()) { "Scan a barcode or enter a title to search." }
        if (code.isNotBlank() && title.isBlank()) require(if (music) isGtin(code) else isIsbn(code)) { "${if (music) "Barcode" else "ISBN"} check digit is invalid. Check the code or clear it to search by title." }
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
        fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").take(300) + "\""
        val url = if (music) {
            val query = if (title.isBlank()) "barcode:${quoted(code)}" else "release:${quoted(title)}" + if (creator.isNotBlank()) " AND artist:${quoted(creator)}" else ""
            "https://musicbrainz.org/ws/2/release/?query=${encode(query)}&fmt=json&limit=8"
        } else {
            val query = if (title.isBlank()) "isbn=${encode(code)}" else "q=${encode(listOf(title, creator).filter(String::isNotBlank).joinToString(" ").take(500))}"
            "https://openlibrary.org/search.json?$query&fields=key,title,author_name,cover_i,first_publish_year&limit=8"
        }
        val cacheDir = File(context.cacheDir, "metadata").apply { mkdirs() }
        val key = MessageDigest.getInstance("SHA-256").digest((kind + url).toByteArray()).joinToString("") { "%02x".format(it) }
        val cache = File(cacheDir, "$key.json")
        requestLock.withLock {
            if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < 24L * 60 * 60 * 1000) {
                try { return@withLock SuggestionCodec.decode(cache.readText()) } catch (_: Exception) { cache.delete() }
            }
            val elapsed = android.os.SystemClock.elapsedRealtime() - lastRequest
            if (elapsed < 1100) delay(1100 - elapsed)
            lastRequest = android.os.SystemClock.elapsedRealtime()
            val body = readUrl(url, 1024 * 1024).toString(Charsets.UTF_8)
            val results = if (music) MetadataParser.music(body, kind) else MetadataParser.books(body, if (title.isBlank()) code else "").map { it.copy(kind = kind) }
            // A cache failure must not discard a successful lookup.
            try {
                cache.writeText(SuggestionCodec.encode(results))
                cacheDir.listFiles()?.sortedByDescending(File::lastModified)?.drop(100)?.forEach(File::delete)
            } catch (_: IOException) { /* Re-fetch next time if device storage is full. */ }
            results
        }
    }

    private suspend fun lookupWikidata(kind: String, title: String, platform: String): List<ItemSuggestion> {
        require(title.isNotBlank()) { "Enter or recognize a title first. Wikidata cannot search by EAN." }
        val query = title.trim().take(300)
        val cacheDir = File(context.cacheDir, "metadata").apply { mkdirs() }
        val key = MessageDigest.getInstance("SHA-256").digest(("wikidata-v1|$kind|$query|$platform").toByteArray()).joinToString("") { "%02x".format(it) }
        val cache = File(cacheDir, "$key.json")
        return requestLock.withLock {
            if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < 24L * 60 * 60 * 1000) {
                try { return@withLock SuggestionCodec.decode(cache.readText()) } catch (_: Exception) { cache.delete() }
            }
            suspend fun request(parameters: String): String {
                coroutineContext.ensureActive()
                val elapsed = android.os.SystemClock.elapsedRealtime() - lastRequest
                if (elapsed < 1100) delay(1100 - elapsed)
                lastRequest = android.os.SystemClock.elapsedRealtime()
                return readUrl("https://www.wikidata.org/w/api.php?format=json&$parameters", 1024 * 1024).toString(Charsets.UTF_8)
            }
            val candidates = WikidataMetadata.searchIds(request("action=wbsearchentities&language=en&type=item&limit=8&search=${URLEncoder.encode(query, "UTF-8")}"))
            val all = mutableMapOf<String, JsonObject>()
            suspend fun load(ids: List<String>, labelsOnly: Boolean = false) {
                ids.distinct().filter { it !in all }.chunked(50).forEach { chunk ->
                    val props = if (labelsOnly) "labels" else "labels%7Caliases%7Cclaims"
                    all.putAll(WikidataMetadata.entities(request("action=wbgetentities&languages=en%7Cmul&languagefallback=1&props=$props&ids=${chunk.joinToString("%7C")}")))
                }
            }
            if (candidates.isEmpty()) return@withLock emptyList()
            load(candidates)
            var types = candidates.flatMap { all[it]?.let { entity -> WikidataMetadata.ids(entity, "P31") }.orEmpty() }.distinct().take(40)
            repeat(2) {
                load(types)
                types = types.flatMap { all[it]?.let { entity -> WikidataMetadata.ids(entity, "P279") }.orEmpty() }.distinct().take(40)
            }
            load(candidates.flatMap { all[it]?.let(WikidataMetadata::relatedIds).orEmpty() }, labelsOnly = true)
            val results = WikidataMetadata.suggestions(candidates, all, kind, query, platform)
            try {
                cache.writeText(SuggestionCodec.encode(results))
                cacheDir.listFiles()?.sortedByDescending(File::lastModified)?.drop(100)?.forEach(File::delete)
            } catch (_: IOException) { /* Lookup remains usable without a writable cache. */ }
            results
        }
    }

    override suspend fun importCover(suggestion: ItemSuggestion): String = withContext(Dispatchers.IO) {
        val url = suggestion.coverUrl
        require(url.startsWith("https://covers.openlibrary.org/") || url.startsWith("https://coverartarchive.org/")) { "Unknown cover provider." }
        val bytes = readUrl(url, 5 * 1024 * 1024)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No usable cover image was found." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
        val name = "${UUID.randomUUID()}.jpg"
        val file = File(context.filesDir, "photos/$name")
        try {
            file.parentFile!!.mkdirs()
            file.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            name
        } catch (error: Exception) { file.delete(); throw error }
        finally { bitmap.recycle() }
    }

    private fun readUrl(address: String, limit: Int): ByteArray {
        transport?.let { return it(address, limit) }
        var next = URL(address)
        repeat(5) {
            require(next.protocol == "https") { "Only secure metadata connections are supported." }
            val connection = (next.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000; readTimeout = 15000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json,image/*")
            }
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    next = URL(next, connection.getHeaderField("Location") ?: throw IOException("Missing redirect target."))
                } else {
                    if (status == 404) throw IOException("No cover is available for this release.")
                    if (status == 429 || status == 503) throw IOException("The metadata service is busy. Please wait and try again.")
                    if (status !in 200..299) throw IOException("The metadata service could not complete this request ($status).")
                    return connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= limit) { "The metadata response was too large." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        throw IOException("Too many metadata redirects.")
    }
}
