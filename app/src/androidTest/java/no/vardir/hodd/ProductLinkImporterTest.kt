package no.vardir.hodd

import androidx.test.ext.junit.runners.AndroidJUnit4
import no.vardir.hodd.data.ProductLinkImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductLinkImporterTest {
    @Test fun retryAfterStopsSameHostRequestsButNotOtherStores() {
        var elapsed = 1000L
        val limits = no.vardir.hodd.data.ProductLinkRateLimits(
            now = { java.time.Instant.parse("2026-10-19T22:00:00.500Z") }, elapsed = { elapsed })
        fun refusal(header: String?) = runCatching { limits.refused("SHOP.example", header) }.exceptionOrNull()!!
        assertTrue(refusal("120").message!!.contains("120 seconds"))
        assertTrue(runCatching { limits.check("shop.example") }.isFailure)
        limits.check("another.example")
        elapsed += 120000
        limits.check("shop.example")
        assertTrue(refusal("Mon, 19 Oct 2026 22:02:00 GMT").message!!.contains("120 seconds"))
        elapsed += 120000
        limits.check("shop.example")
        assertTrue(refusal("bad value").message!!.contains("60 seconds"))
        elapsed += 60000
        limits.check("shop.example")
        assertTrue(refusal("0").message!!.contains("1 seconds"))
    }
    @Test fun elkjopProductShapeKeepsConsumerAndBusinessPricesSeparate() {
        val html = """<script type="application/ld+json">{"@type":"Product",
          "@id":"https://www.elkjop.no/product/game/SWISUPERSMAB",
          "name":"Super Smash Bros. Ultimate - SSBU (Switch)","sku":"SWISUPERSMAB",
          "image":"https://next-media.elkjop.com/image/cover.jpg","brand":{"@type":"Brand","name":"Nintendo"},
          "offers":[{"@type":"Offer","price":"619","priceCurrency":"NOK","eligibleCustomerType":{"@id":"https://schema.org/Public"}},
          {"@type":"Offer","price":"495.2","priceCurrency":"NOK","eligibleCustomerType":{"@id":"https://schema.org/Business"}}]}</script>"""
        val result = ProductLinkImporter.parse(html, "https://www.elkjop.no/product/game/SWISUPERSMAB?qid=tracking")
        assertEquals("Super Smash Bros. Ultimate - SSBU (Switch)", result.title)
        assertEquals("Nintendo", result.brand)
        assertEquals("SWISUPERSMAB", result.sku)
        assertEquals("https://next-media.elkjop.com/image/cover.jpg", result.imageUrl)
        assertTrue(result.price.isBlank()) // Do not guess between consumer and ex-VAT business offers.
    }
    @Test fun readsProductJsonLd() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Game &amp; Fun",
             "image":["https://example.com/cover.jpg"],"brand":{"name":"Nintendo"},
             "offers":{"@type":"Offer","price":499,"priceCurrency":"NOK"}}
            </script></head></html>
        """.trimIndent()
        val result = ProductLinkImporter.parse(html, "https://example.com/game")
        assertEquals("Game & Fun", result.title)
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
    @Test fun imageObjectsResolveGraphReferencesAndDoNotUseCaptionsAsUrls() {
        val html = """<script type="application/ld+json">{"@graph":[
          {"@type":"Product","name":"Example","image":{"@id":"#image"}},
          {"@id":"#image","@type":"ImageObject","name":"A nice caption","contentUrl":"/actual.webp"}
        ]}</script>"""
        assertEquals("https://shop.example/actual.webp", ProductLinkImporter.parse(html, "https://shop.example/item").imageUrl)
    }
    @Test fun mainEntityWinsOverEarlierRecommendations() {
        val html = """<script type="application/ld+json">{"@graph":[
          {"@type":"Product","@id":"#recommendation","name":"Wrong product"},
          {"@type":"WebPage","url":"https://shop.example/item","mainEntity":{"@id":"#main"}},
          {"@type":"Product","@id":"#main","name":"Right product"}
        ]}</script>"""
        assertEquals("Right product", ProductLinkImporter.parse(html, "https://shop.example/item").title)
    }
    @Test fun matchingProductUrlWinsAndTrackingParametersDoNotChangeIdentity() {
        val html = """<script type="application/ld+json">[
          {"@type":"Product","url":"https://shop.example/other","name":"Wrong product"},
          {"@type":["Thing","https://schema.org/Product"],"url":"https://shop.example/item","name":"Right product"}
        ]</script>"""
        assertEquals("Right product", ProductLinkImporter.parse(html, "https://shop.example/item?utm_source=share").title)
    }
    @Test fun ambiguousProductsRequireExplicitSelection() {
        val html = """<script type="application/ld+json">[
          {"@type":"Product","name":"Blue","sku":"B"}, {"@type":"Product","name":"Red","sku":"R"}
        ]</script>"""
        assertEquals(listOf("Blue", "Red"), ProductLinkImporter.parseCandidates(html, "https://shop.example/item").map { it.title })
        try { ProductLinkImporter.parse(html, "https://shop.example/item"); org.junit.Assert.fail("Must choose a product") }
        catch (_: IllegalStateException) { }
    }
    @Test fun productGroupVariantsInheritBrandButKeepPublisherSeparate() {
        val html = """<script type="application/ld+json">{"@type":"ProductGroup","brand":{"name":"Brand"},"publisher":{"name":"Actual publisher"},"hasVariant":[
          {"@type":"Product","name":"Small","sku":"S"}, {"@type":"Product","name":"Large","sku":"L"}
        ]}</script>"""
        val candidates = ProductLinkImporter.parseCandidates(html, "https://shop.example/item")
        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.brand == "Brand" && it.publisher == "Actual publisher" })
        val brandOnly = ProductLinkImporter.parse("""<script type="application/ld+json">{"@type":"Product","name":"Item","brand":"Brand"}</script>""", "https://shop.example/item")
        assertEquals("", brandOnly.suggestion("Other").publisher)
        assertEquals("Brand", brandOnly.suggestion("Other").attributes["brand"])
    }
    @Test fun aggregatePricesAreLabeledAndConflictingOffersAreNotGuessed() {
        val html = """<script type="application/ld+json">{"@type":"Product","name":"Item","offers":{"@type":"AggregateOffer","lowPrice":"99.50","priceCurrency":"nok"}}</script>"""
        val result = ProductLinkImporter.parse(html, "https://shop.example/item")
        assertEquals("From 99.50 NOK", result.priceLabel)
        val conflicting = html.replace("{\"@type\":\"AggregateOffer\",\"lowPrice\":\"99.50\",\"priceCurrency\":\"nok\"}", "[{\"price\":100,\"priceCurrency\":\"NOK\"},{\"price\":200,\"priceCurrency\":\"NOK\"}]")
        assertEquals("", ProductLinkImporter.parse(conflicting, "https://shop.example/item").price)
    }
    @Test fun descriptionIsPlainTextAndUnsafeImageSchemesAreDiscarded() {
        val html = """<script type="application/ld+json">{"@type":"Product","name":"Item","description":"<p>One &amp; two</p>","image":{"url":"javascript:alert(1)"}}</script>"""
        val result = ProductLinkImporter.parse(html, "https://shop.example/item")
        assertEquals("One & two", result.description)
        assertTrue(result.imageUrl.isBlank())
        assertFalse(result.suggestion("Other").attributes.containsKey("storePrice"))
    }

    @Test fun linkedVariantsInheritGroupMetadataAndExtremePricesAreRejected() {
        val html = """<script type="application/ld+json">{"@graph":[
          {"@type":"ProductGroup","brand":"Brand","hasVariant":[{"@id":"#small"},{"@id":"#large"}]},
          {"@type":"Product","@id":"#small","name":"Small","offers":{"price":"1e1000000000","priceCurrency":"NOK"}},
          {"@type":"Product","@id":"#large","name":"Large"}
        ]}</script>"""
        val products = ProductLinkImporter.parseCandidates(html, "https://shop.example/item")
        assertEquals(2, products.size)
        assertTrue(products.all { it.brand == "Brand" && it.price.isBlank() })
    }

}
