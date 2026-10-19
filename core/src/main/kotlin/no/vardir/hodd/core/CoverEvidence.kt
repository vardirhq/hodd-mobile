package no.vardir.hodd.core

import java.text.Normalizer
import java.util.Locale

/** OCR layout is evidence, not a list of already identified titles or authors. */
data class CoverLine(val text: String, val height: Int = 0, val order: Int = 0)
data class CoverPlatform(val name: String, val brand: String, val imperfect: Boolean)

class CoverEvidence(val lines: List<CoverLine>, val requestedKind: String) {
    companion object {
        private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        private fun distance(a: String, b: String): Int {
            var row = IntArray(b.length + 1) { it }
            a.forEachIndexed { i, x ->
                val next = IntArray(b.length + 1); next[0] = i + 1
                b.forEachIndexed { j, y -> next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (x == y) 0 else 1) }
                row = next
            }
            return row[b.length]
        }
        private fun platform(text: String): CoverPlatform? {
            val clean = normalized(text)
            if (clean.length !in 3..40) return null
            FieldChoices.brandFor(text)?.let { brand ->
                val exact = FieldChoices.platforms.firstOrNull { it.name.equals(text.trim(), true) }
                val alias = when (clean) { "switch" -> "Nintendo Switch"; "ps5" -> "PlayStation 5"; "ps4" -> "PlayStation 4"; "ps3" -> "PlayStation 3"; "ps2" -> "PlayStation 2"; "ps1" -> "PlayStation"; "snes" -> "Super Nintendo Entertainment System"; "nes" -> "Nintendo Entertainment System"; "n64" -> "Nintendo 64"; "gamecube" -> "Nintendo GameCube"; "psp" -> "PlayStation Portable"; else -> null }
                return CoverPlatform(exact?.name ?: alias ?: text.trim(), brand, false)
            }
            // Only tolerate a damaged brand prefix when the distinguishing system word
            // is intact. "Nintendo Switch Sports" must remain a possible game title.
            val lastWord = text.trim().substringAfterLast(' ')
            return FieldChoices.platforms.asSequence().filter {
                it.name.contains(' ') && clean.length >= 8 &&
                    it.name.substringAfterLast(' ').equals(lastWord, true) &&
                    text.trim().split(Regex("\\s+")).size == it.name.split(' ').size
            }.map { it to distance(clean, normalized(it.name)) }
                .filter { (entry, edits) -> edits <= (normalized(entry.name).length * 0.30).toInt() }
                .minByOrNull { it.second }?.let { (entry, edits) -> CoverPlatform(entry.name, entry.brand, edits > 0) }
        }
    }
    val detectedPlatform: CoverPlatform? = lines.asSequence().take(8).mapNotNull { platform(it.text) }.firstOrNull()
    val kind get() = if (detectedPlatform != null) "Game" else requestedKind
    private fun branding(text: String) = platform(text) != null || FieldChoices.brands.any { normalized(it.value) == normalized(text) }
    private fun noise(text: String) = text.trim().matches(Regex("(?i)(PEGI\\s*\\d+|[0-9]{1,2}\\+?|ESRB|PAL|NTSC|DVD|BLU[- ]?RAY|4K UHD|www\\..*|https?://.*|[©®™].*)"))
    val titleLines: List<CoverLine> get() = lines.filter { it.text.trim().length in 3..200 && !branding(it.text) && !noise(it.text) }
        .sortedWith(compareByDescending<CoverLine> { it.height }.thenBy { it.order })
    val text get() = lines.joinToString("\n") { it.text }.take(3000)
    private val content get() = lines.filterNot { branding(it.text) || noise(it.text) }.joinToString(" ") { it.text }
    private fun supported(value: String) = value.isNotBlank() && normalized(value).length >= 2 && normalized(content).contains(normalized(value))

    /** Image output can correct OCR spelling; text-only output must stay grounded in OCR. */
    fun validate(proposal: ItemSuggestion, imageBased: Boolean = false): ItemSuggestion {
        require(proposal.title.isNotBlank() && !branding(proposal.title) && !noise(proposal.title)) { "The model mistook branding for the item title. Review the recognized text or try a closer photo." }
        require(imageBased || supported(proposal.title)) { "The model's title was not supported by the cover text. Review the text or try another photo." }
        val safeCreator = proposal.creator.takeIf { value ->
            val knownCompany = (FieldChoices.publishers + FieldChoices.developers).any { normalized(it.value) == normalized(value) }
            val gameCredit = knownCompany || Regex("(?i)(developed|published|created)\\s+by").containsMatchIn(content)
            (kind != "Game" || gameCredit) && supported(value) && normalized(value) != normalized(proposal.title) &&
            !(normalized(value).length > 5 && normalized(proposal.title).contains(normalized(value))) }.orEmpty()
        val resolvedKind = if (imageBased && proposal.kind in FieldChoices.kinds.map { it.value }) proposal.kind else kind
        val platform = detectedPlatform.takeIf { resolvedKind == "Game" }
        val safeAttributes = proposal.attributes.filter { (key, value) ->
            key in setOf("isbn", "pages", "binding", "developer", "region", "edition", "director", "runtime", "resolution", "label", "catalogNumber", "tracks", "country", "denomination", "mintYear", "mintMark", "material") && supported(value)
        }.toMutableMap()
        if (platform != null) { safeAttributes["platform"] = platform.name; safeAttributes["platformBrand"] = platform.brand }
        return proposal.copy(kind = resolvedKind, creator = safeCreator, status = "", lentTo = "", notes = "", barcode = proposal.barcode.takeIf(::supported).orEmpty(),
            format = proposal.format.takeIf(::supported).orEmpty(), subtitle = proposal.subtitle.takeIf(::supported).orEmpty(),
            series = proposal.series.takeIf(::supported).orEmpty(), volume = proposal.volume.takeIf(::supported).orEmpty(),
            publisher = proposal.publisher.takeIf(::supported).orEmpty(), language = proposal.language.takeIf(::supported).orEmpty(),
            releaseYear = proposal.releaseYear.takeIf { it.matches(Regex("[12][0-9]{3}")) && supported(it) }.orEmpty(),
            condition = "", attributes = safeAttributes, detail = if (platform?.imperfect == true) "Possible platform from imperfect cover text: ${platform.name}. Check it." else proposal.detail)
    }
    fun alternatives(): List<ItemSuggestion> = titleLines.map { it.text.trim() }.distinct().take(6).map {
        ItemSuggestion(title = it, kind = kind, source = "On-device text recognition", detail = "Raw text line; not a verified identification.",
            attributes = detectedPlatform?.let { p -> mapOf("platform" to p.name, "platformBrand" to p.brand) }.orEmpty())
    }
}
