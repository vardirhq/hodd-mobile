package no.vardir.hodd.core

import kotlinx.serialization.json.*
import java.text.Normalizer
import java.util.Locale

/** Catalog facts are separate from OCR evidence. Never infer the user's physical edition. */
object WikidataMetadata {
    private val idPattern = Regex("Q[1-9][0-9]*")
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
    fun searchIds(text: String): List<String> = Json.parseToJsonElement(text).jsonObject.array("search")
        .mapNotNull { (it as? JsonObject)?.text("id")?.takeIf(idPattern::matches) }.distinct().take(8)
    fun entities(text: String): Map<String, JsonObject> =
        (Json.parseToJsonElement(text).jsonObject["entities"] as? JsonObject)?.entries.orEmpty()
            .mapNotNull { (id, value) -> (value as? JsonObject)?.takeIf { idPattern.matches(id) && "missing" !in it }?.let { id to it } }.toMap()

    private fun statements(entity: JsonObject, property: String): List<JsonObject> {
        val claims = entity["claims"] as? JsonObject ?: return emptyList()
        val valid = claims.array(property).mapNotNull { it as? JsonObject }.filter { it.text("rank") != "deprecated" }
        return valid.filter { it.text("rank") == "preferred" }.ifEmpty { valid }
    }
    private fun values(entity: JsonObject, property: String) = statements(entity, property).mapNotNull {
        ((it["mainsnak"] as? JsonObject)?.get("datavalue") as? JsonObject)?.get("value") as? JsonObject
    }
    fun ids(entity: JsonObject, property: String): List<String> = values(entity, property)
        .map { it.text("id") }.filter(idPattern::matches).distinct()
    fun relatedIds(entity: JsonObject): List<String> =
        listOf("P178", "P123", "P179", "P400", "P57", "P136").flatMap { ids(entity, it) }.distinct()
    private fun label(entity: JsonObject?): String {
        val labels = entity?.get("labels") as? JsonObject ?: return ""
        return listOf("en", "mul").firstNotNullOfOrNull { language ->
            (labels[language] as? JsonObject)?.text("value")?.takeIf(String::isNotBlank)
        }.orEmpty().take(300)
    }
    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun canonicalPlatform(text: String): String = FieldChoices.platforms.firstOrNull {
        normalized(it.name) == normalized(text)
    }?.name ?: text
    private fun samePlatform(a: String, b: String): Boolean {
        if (normalized(a) == normalized(b)) return true
        val known = FieldChoices.platforms.firstOrNull { normalized(it.name) == normalized(b) }
        return known != null && normalized(a) in listOf(normalized(known.name.removePrefix(known.brand + " ")))
    }
    private fun hasType(entity: JsonObject, root: String, all: Map<String, JsonObject>): Boolean {
        var types = ids(entity, "P31").toSet()
        repeat(3) {
            if (root in types) return true
            types = types.flatMap { id -> all[id]?.let { ids(it, "P279") }.orEmpty() }.toSet()
        }
        return false
    }

    fun suggestions(candidateIds: List<String>, all: Map<String, JsonObject>, kind: String, query: String, platform: String = ""): List<ItemSuggestion> {
        require(kind in listOf("Game", "Movie"))
        val root = if (kind == "Game") "Q7889" else "Q11424"
        val queryNormalized = normalized(query)
        val queryNumbers = Regex("\\b[0-9]+\\b").findAll(queryNormalized).map { it.value }.toSet()
        return candidateIds.mapNotNull { id ->
            val entity = all[id] ?: return@mapNotNull null
            if (!hasType(entity, root, all)) return@mapNotNull null
            val title = label(entity).takeIf(String::isNotBlank) ?: return@mapNotNull null
            val aliases = (entity["aliases"] as? JsonObject)?.array("en").orEmpty().mapNotNull { (it as? JsonObject)?.text("value") }
            val names = (listOf(title) + aliases).map(::normalized)
            // A recognized sequel number must not silently turn into another installment.
            if (queryNumbers.isNotEmpty() && names.none { name -> Regex("\\b[0-9]+\\b").findAll(name).map { it.value }.toSet() == queryNumbers }) return@mapNotNull null
            val queryTokens = queryNormalized.split(' ').filter(String::isNotBlank).toSet()
            val score = names.maxOf { name ->
                if (name == queryNormalized) 100.0 else {
                    val tokens = name.split(' ').toSet()
                    val overlap = queryTokens.intersect(tokens).size.toDouble()
                    80.0 * overlap / (queryTokens.size + tokens.size - overlap).coerceAtLeast(1.0)
                }
            }
            if (score < 20) return@mapNotNull null
            fun labels(property: String) = ids(entity, property).mapNotNull { label(all[it]).takeIf(String::isNotBlank) }.distinct()
            val platforms = labels("P400").map(::canonicalPlatform)
            val matchingPlatform = platforms.firstOrNull { samePlatform(platform, it) }
            if (kind == "Game" && platform.isNotBlank() && platforms.isNotEmpty() && matchingPlatform == null) return@mapNotNull null
            val chosenPlatform = matchingPlatform ?: platforms.singleOrNull().orEmpty()
            val dates = values(entity, "P577").mapNotNull { value ->
                value.text("time").takeIf { (value["precision"] as? JsonPrimitive)?.intOrNull?.let { it >= 9 } == true }
                    ?.let { Regex("^\\+([0-9]{4})-").find(it)?.groupValues?.get(1) }
            }.distinct().sorted()
            val developers = labels("P178").joinToString(", ").take(300)
            val directors = labels("P57").joinToString(", ").take(300)
            val attrs = buildMap {
                if (kind == "Game") {
                    if (developers.isNotBlank()) put("developer", developers)
                    if (chosenPlatform.isNotBlank()) {
                        put("platform", chosenPlatform)
                        FieldChoices.brandFor(chosenPlatform)?.let { put("platformBrand", it) }
                    }
                } else if (directors.isNotBlank()) put("director", directors)
            }
            ItemSuggestion(title = title, creator = if (kind == "Game") developers else directors, kind = kind,
                publisher = labels("P123").joinToString(", ").take(300), series = labels("P179").joinToString(", ").take(300),
                releaseYear = dates.firstOrNull().orEmpty(), attributes = attrs,
                source = "Wikidata", sourceId = id, sourceUrl = "https://www.wikidata.org/wiki/$id",
                detail = listOf("Work-level match; check your edition", if (dates.isNotEmpty()) "First catalog release: ${dates.first()}" else "",
                    if (platforms.isNotEmpty()) "Platforms: ${platforms.joinToString(", ")}" else "",
                    labels("P136").joinToString(", ")).filter(String::isNotBlank).joinToString(" · ")) to
                (score + if (matchingPlatform != null) 10 else 0)
        }.sortedByDescending { it.second }.map { it.first }.take(8)
    }
}
