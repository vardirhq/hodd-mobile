package no.vardir.hodd.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.vardir.hodd.data.ProductBrowserMetadata
import no.vardir.hodd.data.ProductLinkImporter
import no.vardir.hodd.data.ProductPreview
import java.io.ByteArrayInputStream

/** Visible user-driven browsing. Store scripts never receive an Android/native bridge. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ProductBrowser(
    initialUrl: String, close: () -> Unit, imported: (List<ProductPreview>) -> Unit,
    validate: suspend (String) -> Unit = { ProductLinkImporter.validatedUrl(it); Unit },
    load: (WebView, String) -> Unit = { view, url -> view.loadUrl(url) },
) {
    val available = remember { runCatching { WebView.getCurrentWebViewPackage() != null }.getOrDefault(false) }
    if (!available) {
        AlertDialog(onDismissRequest = close, title = { Text("Browser unavailable") },
            text = { Text("Enable or update Android System WebView to use browser imports. You can still fetch details directly or keep editing.") },
            confirmButton = { TextButton(onClick = close) { Text("Close browser") } })
        return
    }
    val scope = rememberCoroutineScope()
    val currentImported by rememberUpdatedState(imported)
    var browser by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reading by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    // Generation + URL checks reject results from a page navigated away from or a closed browser.
    val lifetime = remember { BrowserLifetime() }
    fun navigate(view: WebView, url: String) {
        val generation = ++lifetime.generation
        ready = false; loading = true; error = null; location = url
        scope.launch {
            try {
                require(ProductBrowserMetadata.isPublicHttps(url)) { "Only public HTTPS pages can be opened here." }
                withContext(Dispatchers.IO) { validate(url) }
                if (lifetime.alive && lifetime.generation == generation) load(view, url)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (lifetime.alive && lifetime.generation == generation) { error = failure.message; loading = false }
            }
        }
    }
    fun readPage() {
        val view = browser ?: return
        val pageUrl = view.url?.takeIf { it == location } ?: return
        val generation = lifetime.generation
        reading = true; error = null
        view.evaluateJavascript(ProductBrowserMetadata.script) { raw ->
            if (!lifetime.alive || generation != lifetime.generation || view.url != pageUrl) { reading = false; return@evaluateJavascript }
            scope.launch {
                try {
                    val found = withContext(Dispatchers.IO) {
                        ProductLinkImporter.parseBrowserSnapshot(ProductBrowserMetadata.decodeJavascriptResult(raw), pageUrl)
                    }
                    if (lifetime.alive && generation == lifetime.generation && view.url == pageUrl) currentImported(found)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (lifetime.alive) error = failure.message ?: "Could not read this page."
                } finally { if (lifetime.alive) reading = false }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            lifetime.alive = false; lifetime.generation++
            browser?.apply { stopLoading(); webChromeClient = null; destroy() }
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = close) { Text("Close browser") }
                    TextButton(onClick = { browser?.let { navigate(it, location) } }, enabled = !loading && !reading) { Text("Reload") }
                }
                Text("Store browser", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                Text(location, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Text("Load the product, then import its details for review. This browser uses its own store cookies; your collection stays private.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
                if (loading || reading) LinearProgressIndicator(Modifier.fillMaxWidth())
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth().testTag("store-webview"), factory = { context ->
                    WebView(context).apply {
                        browser = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.setGeolocationEnabled(false)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return !ProductBrowserMetadata.isPublicHttps(request.url.toString())
                                navigate(view, request.url.toString())
                                return true
                            }
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                // Runs off the UI thread. Validate subresources too; never access app files/content URIs.
                                return if (runCatching { ProductLinkImporter.validatedUrl(request.url.toString()) }.isSuccess) null
                                else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            }
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                lifetime.generation++; location = url; loading = true; ready = false; reading = false; error = null
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                if (!lifetime.alive || view.url != url || location != url) return
                                loading = false; ready = error == null && ProductBrowserMetadata.isPublicHttps(url)
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                if (request.isForMainFrame) { error = "This page could not be loaded. You can close the browser and keep editing."; loading = false; ready = false }
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame) { error = "The store returned HTTP ${response.statusCode}. Wait before reloading, or close the browser and keep editing."; ready = false }
                            }
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, failure: SslError) {
                                handler.cancel(); error = "The page's secure connection could not be verified."; loading = false; ready = false
                            }
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                lifetime.generation++; error = "The browser stopped. Close it and try again."; ready = false; loading = false
                                (view.parent as? android.view.ViewGroup)?.removeView(view); view.destroy(); browser = null
                                return true
                            }
                        }
                        navigate(this, initialUrl)
                    }
                })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                Button(onClick = ::readPage, enabled = ready && !loading && !reading,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp)) {
                    Text(if (reading) "Reading product details…" else "Import this product")
                }
            }
        }
    }
}

private class BrowserLifetime { var alive = true; var generation = 0 }
