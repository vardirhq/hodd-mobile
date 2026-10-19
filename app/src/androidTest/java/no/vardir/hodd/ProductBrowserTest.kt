package no.vardir.hodd

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import no.vardir.hodd.data.ProductBrowserMetadata
import no.vardir.hodd.data.ProductLinkImporter
import no.vardir.hodd.data.ProductPreview
import no.vardir.hodd.ui.HoddTheme
import no.vardir.hodd.ui.ProductBrowser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProductBrowserTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val url = "https://shop.example/item"
    private var result: List<ProductPreview>? = null
    private var closed = false
    private fun open(html: String) {
        compose.setContent { HoddTheme {
            var visible by remember { mutableStateOf(true) }
            if (visible) ProductBrowser(url, close = { closed = true; visible = false }, imported = { result = it; visible = false },
                validate = { require(ProductBrowserMetadata.isPublicHttps(it)) },
                load = { view, address -> view.loadDataWithBaseURL(address, html, "text/html", "UTF-8", address) })
        } }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Import this product").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
    }
    @Test fun importsJavascriptGeneratedMetadataFromDisplayedPage() {
        open("""<html><head><title>Loading</title><script>
            document.addEventListener('DOMContentLoaded',function(){
              var node=document.createElement('script');node.type='application/ld+json';
              node.textContent=JSON.stringify({'@type':'Product',name:'Rendered game',brand:{name:'Nintendo'},image:'/cover.jpg',offers:{price:'599',priceCurrency:'NOK'}});
              document.head.appendChild(node);
            });</script></head><body><h1>Rendered game</h1></body></html>""")
        val folder = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, "23-store-browser.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Import this product").performClick()
        compose.waitUntil(10000) { result != null }
        assertEquals("Rendered game", result!!.single().title)
        assertEquals("Nintendo", result!!.single().brand)
        assertEquals("599 NOK", result!!.single().priceLabel)
        assertEquals("https://shop.example/cover.jpg", result!!.single().imageUrl)
        assertEquals(url, result!!.single().url)
    }
    @Test fun challengePagesStayOpenAndCloseDoesNotImportAnything() {
        open("<html><head><title>Just a moment</title></head><body>Please wait</body></html>")
        compose.onNodeWithText("Import this product").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("No product metadata is available yet.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertNull(result)
        compose.onNodeWithText("Close browser").performClick()
        assertTrue(closed); assertNull(result)
        compose.onNodeWithText("Store browser").assertDoesNotExist()
    }
    @Test fun snapshotEscapingSizeAndUrlRulesDoNotTreatPageDataAsExecutableHtml() {
        val snapshot = JSONObject().put("title", "Fallback").put("jsonLd", org.json.JSONArray().put(
            """{"@type":"Product","name":"Game & Fun","description":"<b>Text</b>","image":"/cover.jpg"}"""))
        val decoded = ProductBrowserMetadata.decodeJavascriptResult(JSONObject.quote(snapshot.toString()))
        val item = ProductLinkImporter.parseBrowserSnapshot(decoded, url).single()
        assertEquals("Game & Fun", item.title); assertEquals("Text", item.description)
        for (address in listOf("http://shop.example", "https://user:pass@shop.example", "file:///data/user/0", "https://127.0.0.1", "https://192.168.1.2", "https://172.16.0.1", "https://host.local", "https://[::1]")) {
            assertFalse(address, ProductBrowserMetadata.isPublicHttps(address))
        }
        assertTrue(ProductBrowserMetadata.isPublicHttps(url))
        assertTrue(runCatching { ProductBrowserMetadata.decodeJavascriptResult("x".repeat(2_100_001)) }.isFailure)
        assertTrue(runCatching { ProductBrowserMetadata.decodeJavascriptResult(JSONObject.quote("""{"error":"Too large"}""")) }.isFailure)
    }
}
