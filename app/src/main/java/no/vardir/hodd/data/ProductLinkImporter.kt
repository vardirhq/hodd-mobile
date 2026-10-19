package no.vardir.hodd.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import no.vardir.hodd.core.ItemSuggestion
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class ProductPreview(
    val title: String, val description: String, val imageUrl: String, val brand: String,
    val price: String, val currency: String, val url: String,
    val publisher: String = "", val sku: String = "", val priceIsFrom: Boolean = false,
) {
    val priceLabel get() = listOf(if (priceIsFrom) "From" else "", price, currency).filter(String::isNotBlank).joinToString(" ")
    fun suggestion(kind: String, checkedAt: String = java.time.Instant.now().toString()) = ItemSuggestion(
        title = title, kind = kind, notes = description, publisher = publisher,
        source = "Product link", sourceUrl = url,
        attributes = buildMap {
            if (brand.isNotBlank()) put("brand", brand)
            put("storeUrl", url)
            if (price.isNotBlank()) { put("storePrice", priceLabel); put("storePriceCheckedAt", checkedAt) }
        },
    )
}

interface ProductLinkSource {
    suspend fun preview(url: String): List<ProductPreview>
    suspend fun prepareImage(url: String, target: File)
}

/** Public product metadata is fetched locally. No AI service or account is involved. */
object ProductLinkImporter : ProductLinkSource {
    private const val MAX_HTML = 2_000_000
    private const val MAX_IMAGE = 8_000_000
    private fun validatedUrl(raw: String): URL {
        val url = URL(raw.trim())
        require(url.protocol == "https") { "Use an HTTPS product link." }
        require(url.userInfo == null && url.port in listOf(-1, 443)) { "Unsupported product URL." }
        val host = url.host.lowercase(Locale.ROOT)
        require(host.isNotBlank() && host.contains('.') && !host.trimEnd('.').endsWith(".local")) { "Use a public store URL." }
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.all { address ->
            val bytes = address.address
            !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isSiteLocalAddress &&
                !address.isLinkLocalAddress && !address.isMulticastAddress &&
                !(bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) &&
                !(bytes.size == 4 && (bytes[0].toInt() and 255) == 100 && (bytes[1].toInt() and 0xc0) == 64)
        }) { "Local network addresses are not supported." }
        return url
    }
    private suspend fun fetch(raw: String, maxBytes: Int, image: Boolean = false): Pair<ByteArray, String> {
        var url = validatedUrl(raw)
        val deadline = android.os.SystemClock.elapsedRealtime() + 30000
        repeat(5) {
            coroutineContext.ensureActive()
            require(android.os.SystemClock.elapsedRealtime() < deadline) { "The store took too long to respond." }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8000; connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "HoddMobile/0.1 (https://github.com/vardirhq/hodd-mobile)")
                connection.connect()
                if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                    url = validatedUrl(URL(url, connection.getHeaderField("Location") ?: error("Invalid redirect.")).toString())
                } else {
                    require(connection.responseCode == 200) { "Store returned HTTP ${connection.responseCode}." }
                    val type = connection.contentType.orEmpty().lowercase(Locale.ROOT).substringBefore(';').trim()
                    require(if (image) type in listOf("image/jpeg", "image/png", "image/webp") else type == "text/html") { "The link did not return a supported ${if (image) "image" else "webpage"}." }
                    require(connection.contentLengthLong <= maxBytes) { "The download is too large." }
                    val bytes = connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            coroutineContext.ensureActive()
                            require(android.os.SystemClock.elapsedRealtime() < deadline) { "The store took too long to respond." }
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= maxBytes) { "The download is too large." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    return bytes to url.toString()
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects.")
    }

    private fun typeIs(value: JSONObject, type: String): Boolean {
        val types = value.opt("@type")
        return when (types) {
            is String -> types.substringAfterLast('/').substringAfterLast(':') == type
            is JSONArray -> (0 until types.length()).any { types.optString(it).substringAfterLast('/').substringAfterLast(':') == type }
            else -> false
        }
    }
    private fun text(value: Any?): String = when (value) {
        is String -> Jsoup.parseBodyFragment(value).text().trim()
        is Number -> value.toString()
        is JSONObject -> text(value.opt("name"))
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { text(value.opt(it)).takeIf(String::isNotBlank) }.orEmpty()
        else -> ""
    }
    private fun absolute(raw: String, base: String): String = runCatching {
        if (raw.isBlank()) "" else URL(URL(base), raw).takeIf { it.protocol == "https" && it.userInfo == null && it.port in listOf(-1, 443) }?.toString().orEmpty()
    }.getOrDefault("")
    private fun identity(raw: String, base: String): String = runCatching {
        val url = URL(absolute(raw, base))
        val query = url.query.orEmpty().split('&').filter { it.isNotBlank() && !it.substringBefore('=').lowercase(Locale.ROOT).let { key -> key.startsWith("utm_") || key in listOf("gclid", "fbclid") } }.sorted().joinToString("&")
        "${url.host.lowercase(Locale.ROOT)}${url.path.trimEnd('/')}" + if (query.isNotEmpty()) "?$query" else ""
    }.getOrDefault("")

    fun parse(html: String, pageUrl: String): ProductPreview = parseCandidates(html, pageUrl).singleOrNull()
        ?: error("This page describes several products. Choose the matching product first.")
    fun parseCandidates(html: String, pageUrl: String): List<ProductPreview> {
        require(html.length <= MAX_HTML) { "The webpage is too large." }
        return parseDocument(Jsoup.parse(html, pageUrl), pageUrl)
    }
    private fun parseDocument(document: Document, pageUrl: String): List<ProductPreview> {
        val nodes = mutableListOf<JSONObject>()
        val parents = mutableMapOf<JSONObject, JSONObject>()
        fun collect(value: Any?, depth: Int = 0, group: JSONObject? = null) {
            if (depth > 24 || nodes.size >= 256) return
            when (value) {
                is JSONObject -> {
                    nodes += value
                    if (group != null && typeIs(value, "Product")) parents[value] = group
                    val nextGroup = if (typeIs(value, "ProductGroup")) value else group
                    listOf("@graph", "mainEntity", "itemListElement", "item", "hasVariant", "image", "offers", "brand", "publisher").forEach { collect(value.opt(it), depth + 1, nextGroup) }
                }
                is JSONArray -> for (i in 0 until minOf(value.length(), 256)) collect(value.opt(i), depth + 1, group)
            }
        }
        document.select("script[type=application/ld+json]").take(64).forEach { node ->
            // Ignore an individual broken script; other structured data/OG may still be useful.
            runCatching { org.json.JSONTokener(node.data()).nextValue() }.getOrNull()?.let { collect(it) }
        }
        val indexed = nodes.filter { it.optString("@id").isNotBlank() }.groupBy { absolute(it.optString("@id"), pageUrl) }
            .mapValues { (_, values) -> values.maxBy { it.length() } }
        fun resolve(value: Any?): Any? = if (value is JSONObject && value.optString("@id").isNotBlank()) indexed[absolute(value.optString("@id"), pageUrl)] ?: value else value
        fun named(value: Any?) = text(resolve(value))
        fun image(value: Any?, depth: Int = 0): String {
            if (depth > 8) return ""
            return when (val resolved = resolve(value)) {
                is String -> absolute(resolved, pageUrl)
                is JSONObject -> image(resolved.opt("contentUrl"), depth + 1).ifBlank { image(resolved.opt("url"), depth + 1) }
                is JSONArray -> (0 until resolved.length()).firstNotNullOfOrNull { image(resolved.opt(it), depth + 1).takeIf(String::isNotBlank) }.orEmpty()
                else -> ""
            }
        }
        fun meta(key: String) = document.select("meta[property=\"$key\"], meta[name=\"$key\"]").firstOrNull()?.attr("content")?.trim().orEmpty()
        val products = nodes.filter { typeIs(it, "Product") }.distinctBy { it.toString() }
        val mainIds = nodes.filter { it.has("mainEntity") && (it.optString("url").isBlank() || identity(it.optString("url"), pageUrl) == identity(pageUrl, pageUrl)) }
            .mapNotNull { resolve(it.opt("mainEntity")) as? JSONObject }
            .map { absolute(it.optString("@id"), pageUrl).ifBlank { it.toString() } }.toSet()
        fun score(product: JSONObject): Int {
            val id = absolute(product.optString("@id"), pageUrl).ifBlank { product.toString() }
            val productUrl = product.optString("url").ifBlank { product.optString("@id") }
            return (if (id in mainIds) 100 else 0) + (if (productUrl.isNotBlank() && identity(productUrl, pageUrl) == identity(pageUrl, pageUrl)) 50 else 0)
        }
        val bestScore = products.maxOfOrNull(::score) ?: 0
        val selected = if (bestScore > 0) products.filter { score(it) == bestScore } else products
        fun preview(product: JSONObject?): ProductPreview {
            val group = parents[product]
            fun property(key: String): Any? = product?.opt(key)?.takeUnless { it == JSONObject.NULL } ?: group?.opt(key)
            val offers = resolve(property("offers"))
            val offerList = if (offers is JSONArray) (0 until offers.length()).mapNotNull { resolve(offers.opt(it)) as? JSONObject } else listOfNotNull(offers as? JSONObject)
            val prices = offerList.mapNotNull { offer ->
                val raw = text(offer.opt("price")).ifBlank { if (typeIs(offer, "AggregateOffer")) text(offer.opt("lowPrice")) else "" }
                val amount = raw.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && raw.length <= 40 } ?: return@mapNotNull null
                Triple(amount.toPlainString(), text(offer.opt("priceCurrency")).uppercase(Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{3}")) }.orEmpty(), typeIs(offer, "AggregateOffer"))
            }.distinct()
            val price = prices.singleOrNull()
            val title = named(property("name")).ifBlank { text(meta("og:title")).ifBlank { document.title() } }
            require(title.isNotBlank()) { "No product title found on this page." }
            return ProductPreview(title.take(300), named(property("description")).ifBlank { text(meta("og:description")) }.take(2000),
                image(property("image")).ifBlank { absolute(meta("og:image:secure_url"), pageUrl).ifBlank { absolute(meta("og:image"), pageUrl) } },
                named(property("brand")).take(120), price?.first.orEmpty(), price?.second.orEmpty(), pageUrl,
                named(property("publisher")).take(300), text(property("sku")).take(120), price?.third ?: false)
        }
        return (if (selected.isEmpty()) listOf(preview(null)) else selected.map(::preview))
            .distinctBy { listOf(it.title, it.sku, it.imageUrl, it.price, it.currency) }.take(16)
    }
    override suspend fun preview(url: String): List<ProductPreview> = withContext(Dispatchers.IO) {
        val (html, finalUrl) = fetch(url, MAX_HTML)
        parseDocument(Jsoup.parse(ByteArrayInputStream(html), null, finalUrl), finalUrl)
    }
    override suspend fun prepareImage(url: String, target: File): Unit = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("store-image-", ".jpg", target.parentFile)
        try {
            val bytes = fetch(url, MAX_IMAGE, image = true).first
            temporary.writeBytes(bytes)
            coroutineContext.ensureActive()
            PhotoEdits.export(temporary, target, no.vardir.hodd.core.PhotoCrop(), 0f, 1f, 0)
            coroutineContext.ensureActive()
        } catch (error: Exception) { target.delete(); throw error }
        finally { temporary.delete() }
    }
}
