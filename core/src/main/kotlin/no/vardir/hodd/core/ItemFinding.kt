package no.vardir.hodd.core

/** Remove only a separated, known platform suffix; preserve sequel numbers and edition words. */
fun catalogSearchTitle(productTitle: String): String {
    val title = productTitle.trim().take(300)
    val suffix = Regex("\\s+(?:[-–—|])\\s+([^|]+)$").find(title) ?: return title
    val platform = suffix.groupValues[1].trim()
    return if (FieldChoices.platforms.any { it.name.equals(platform, ignoreCase = true) })
        title.substring(0, suffix.range.first).trim() else title
}

/** A work's earliest release is evidence, not necessarily the release of the user's copy. */
fun ItemSuggestion.forReviewedCopy(platform: String, edition: String, useFirstReleaseYear: Boolean): ItemSuggestion {
    if (source != "Wikidata" || kind != "Game") return this
    val facts = attributes.toMutableMap().apply {
        remove("platform"); remove("platformBrand"); remove("edition")
        if (platform.isNotBlank()) {
            put("platform", platform)
            FieldChoices.brandFor(platform)?.let { put("platformBrand", it) }
        }
        if (edition.isNotBlank()) put("edition", edition)
        if (releaseYear.isNotBlank()) put("firstReleaseYear", releaseYear)
    }
    return copy(attributes = facts, releaseYear = releaseYear.takeIf { useFirstReleaseYear }.orEmpty())
}
