package no.vardir.hodd

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebResourceResponse
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
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

@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
class ProductBrowserTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val url = "https://shop.example/item"
    @Volatile private var result: List<ProductPreview>? = null
    @Volatile private var closed = false
    @Volatile private var pageView: WebView? = null
    private fun open(html: String) {
        compose.setContent { HoddTheme {
            var visible by remember { mutableStateOf(true) }
            if (visible) ProductBrowser(url, close = { closed = true; visible = false }, imported = { result = it; visible = false },
                validate = { require(ProductBrowserMetadata.isPublicHttps(it)) },
                intercept = { view, request ->
                    pageView = view
                    val body = if (request.url.toString() == url) html else ""
                    WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
                })
        } }
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithText("Import this product").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        } catch (failure: Throwable) {
            val folder = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            File(folder, "24-store-browser-load-failure.png").outputStream().use {
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                println("Browser semantics: " + node.children.map { it.config.toString() })
            }
            compose.runOnIdle {
                println("Browser fixture view=" + (pageView?.url ?: "no intercepted request") + " progress=" + pageView?.progress)
                pageView?.evaluateJavascript("JSON.stringify({url:location.href,title:document.title,body:document.body?document.body.innerText:''})") { println("Browser fixture document: " + it) }
            }
            throw failure
        }
    }
    @Test fun importsJavascriptGeneratedMetadataFromDisplayedPage() {
        open("""<html><head><title>Loading</title><script>
            document.addEventListener('DOMContentLoaded',function(){
              var node=document.createElement('script');node.type='application/ld+json';
              node.textContent=JSON.stringify({'@type':'Product',name:'Rendered game',brand:{name:'Nintendo'},image:'/cover.jpg',offers:{price:'599',priceCurrency:'NOK'}});
              document.head.appendChild(node);
            });</script></head><body><h1>Rendered game</h1></body></html>""")
        val button = compose.onNodeWithTag("store-browser-import").assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
        compose.runOnIdle {
            val metrics = compose.activity.windowManager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsets(android.view.WindowInsets.Type.navigationBars())
            assertTrue("This regression must run with visible Android navigation controls", bars.bottom > 0)
            val origin = IntArray(2); pageView!!.rootView.getLocationOnScreen(origin)
            assertTrue("Import button must sit above Android navigation", button.boundsInWindow.bottom + origin[1] <= metrics.bounds.bottom - bars.bottom)
        }
        val folder = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, "23-store-browser.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
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
