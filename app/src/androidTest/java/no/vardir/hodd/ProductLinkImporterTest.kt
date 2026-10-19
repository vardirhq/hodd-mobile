package no.vardir.hodd

import androidx.test.ext.junit.runners.AndroidJUnit4
import no.vardir.hodd.data.ProductLinkImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductLinkImporterTest {
    @Test fun readsProductJsonLd() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Game &amp; Fun",
             "image":["https://example.com/cover.jpg"],"brand":{"name":"Nintendo"},
             "offers":{"@type":"Offer","price":499,"priceCurrency":"NOK"}}
            </script></head></html>
        """.trimIndent()
        val result = ProductLinkImporter.parse(html, "https://example.com/game")
        assertEquals("Nintendo", result.brand)
        assertEquals("499", result.price)
        assertEquals("NOK", result.currency)
        assertEquals("https://example.com/cover.jpg", result.imageUrl)
    }

    @Test fun fallsBackToOpenGraph() {
        val html = """<html><head><meta property="og:title" content="A board game">
            <meta property="og:image" content="/cover.png"></head></html>"""
        val result = ProductLinkImporter.parse(html, "https://shop.example.com/products/1")
        assertEquals("A board game", result.title)
        assertEquals("https://shop.example.com/cover.png", result.imageUrl)
        assertTrue(result.price.isEmpty())
    }
}
