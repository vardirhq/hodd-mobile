package no.vardir.hodd.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

data class ProductPreview(
    val title: String,
    val description: String,
    val imageUrl: String,
    val brand: String,
    val price: String,
    val currency: String,
    val url: String,
)

/** Import public product metadata without an AI service or server. */
object ProductLinkImporter {
    private const val MAX_HTML = 2_000_000
    private const val MAX_IMAGE = 8_000_000

    private fun validatedUrl(raw: String): URL {
        val url = URL(raw.trim())
        require(url.protocol == "https") { "Use an HTTPS product link." }
        require(url.userInfo == null && url.port == -1) { "Unsupported product URL." }
        val host = url.host.lowercase()
        require(host.isNotBlank() && host.contains('.') && !host.endsWith(".local")) { "Use a public store URL." }
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.all { address ->
            !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                !address.isSiteLocalAddress && !address.isLinkLocalAddress &&
                !address.isMulticastAddress
        }) { "Local network addresses are not supported." }
        return url
    }

    private fun fetch(raw: String, maxBytes: Int, image: Boolean = false): Pair<ByteArray, String> {
        var url = validatedUrl(raw)
        repeat(5) {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Hodd/1.0)")
                connection.connect()
                if (connection.responseCode in 300..399) {
                    val location = connection.getHeaderField("Location") ?: error("Invalid redirect.")
                    url = validatedUrl(URL(url, location).toString())
                } else {
                    require(connection.responseCode == 200) { "Store returned HTTP ${connection.responseCode}." }
                    val type = connection.contentType.orEmpty().lowercase()
                    require(if (image) type.startsWith("image/jpeg") || type.startsWith("image/png") || type.startsWith("image/webp")
                        else type.contains("text/html")) { "The link did not return a supported ${if (image) "image" else "webpage"}." }
                    require(connection.contentLengthLong <= maxBytes) { "The download is too large." }
                    val bytes = connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
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

    private fun productObject(value: Any?): JSONObject? = when (value) {
        is JSONObject -> {
            val type = value.opt("@type")
            val matches = when (type) {
                is String -> type.substringAfterLast('/') == "Product"
                is JSONArray -> (0 until type.length()).any { type.optString(it).substringAfterLast('/') == "Product" }
                else -> false
            }
            if (matches) value else {
                for (key in listOf("@graph", "mainEntity", "itemListElement")) {
                    productObject(value.opt(key))?.let { return it }
                }
                null
            }
        }
        is JSONArray -> {
            for (i in 0 until value.length()) productObject(value.opt(i))?.let { return it }
            null
        }
        else -> null
    }

    private fun string(value: Any?): String = when (value) {
        is String -> value.trim()
        is JSONObject -> value.optString("name").trim()
        is JSONArray -> if (value.length() > 0) string(value.opt(0)) else ""
        else -> ""
    }

    fun parse(html: String, pageUrl: String): ProductPreview {
        val document: Document = Jsoup.parse(html, pageUrl)
        val product = document.select("script[type=application/ld+json]").firstNotNullOfOrNull { node ->
            runCatching { productObject(org.json.JSONTokener(node.data()).nextValue()) }.getOrNull()
        }
        fun meta(key: String): String = document.select("meta[property=$key], meta[name=$key]").firstOrNull()
            ?.attr("content")?.trim().orEmpty()
        val offers = product?.opt("offers")
        val offer = when (offers) {
            is JSONObject -> offers
            is JSONArray -> offers.optJSONObject(0)
            else -> null
        }
        val title = string(product?.opt("name")).ifBlank {
            meta("og:title").ifBlank { document.title() }
        }
        require(title.isNotBlank()) { "No product title found on this page." }
        val image = string(product?.opt("image")).ifBlank { meta("og:image") }
        val resolvedImage = runCatching { if (image.isBlank()) "" else URL(URL(pageUrl), image).toString() }.getOrDefault("")
        return ProductPreview(
            title = title.take(300),
            description = string(product?.opt("description")).ifBlank { meta("og:description") }.take(2000),
            imageUrl = resolvedImage,
            brand = string(product?.opt("brand")).take(120),
            price = string(offer?.opt("price")).take(40),
            currency = string(offer?.opt("priceCurrency")).take(8),
            url = pageUrl,
        )
    }

    suspend fun preview(url: String): ProductPreview = withContext(Dispatchers.IO) {
        val (html, finalUrl) = fetch(url, MAX_HTML)
        parse(html.toString(Charsets.UTF_8), finalUrl)
    }

    suspend fun downloadImage(url: String): ByteArray = withContext(Dispatchers.IO) {
        fetch(url, MAX_IMAGE, image = true).first
    }
}
