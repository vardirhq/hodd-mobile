package no.vardir.hodd.data

import java.net.URI
import java.util.Locale

/** Small read-only extraction script. No Android JavaScript bridge is exposed to store pages. */
object ProductBrowserMetadata {
    fun isPublicHttps(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        val parts = host.split('.')
        val ipv4 = parts.takeIf { it.size == 4 && it.all { part -> part.toIntOrNull()?.let { value -> value in 0..255 } == true } }?.map(String::toInt)
        val privateIp = ipv4?.let { it[0] in listOf(0, 10, 127) || (it[0] == 192 && it[1] == 168) ||
            (it[0] == 169 && it[1] == 254) || (it[0] == 172 && it[1] in 16..31) ||
            (it[0] == 100 && it[1] in 64..127) || it[0] >= 224 } == true
        uri.scheme.equals("https", true) && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            host.contains('.') && !host.contains(':') && !host.endsWith(".local") && !host.endsWith(".localhost") && !privateIp
    }.getOrDefault(false)

    val script = """
        (function() {
          var meta = {}, blocks = [], total = 0;
          ['og:title','og:description','og:image','og:image:secure_url','og:type'].forEach(function(key) {
            var node = document.querySelector('meta[property="'+key+'"],meta[name="'+key+'"]');
            meta[key] = node ? (node.content || '').slice(0,4000) : '';
          });
          var nodes = document.querySelectorAll('script[type="application/ld+json"]');
          for (var i=0; i<Math.min(nodes.length,64); i++) {
            var text = nodes[i].textContent || '';
            total += text.length;
            if (total > 1000000) return JSON.stringify({error:'The page metadata is too large.'});
            blocks.push(text);
          }
          return JSON.stringify({title:(document.title || '').slice(0,300),meta:meta,jsonLd:blocks});
        })();
    """.trimIndent()

    fun decodeJavascriptResult(raw: String): String {
        require(raw.length <= 2_100_000) { "The page metadata is too large." }
        val value = org.json.JSONTokener(raw).nextValue() as? String ?: error("Could not read this page. Wait for it to load and try again.")
        val data = org.json.JSONObject(value)
        require(!data.has("error")) { data.optString("error").take(200) }
        return value
    }
}
