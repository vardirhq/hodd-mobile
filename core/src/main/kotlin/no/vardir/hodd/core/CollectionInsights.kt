package no.vardir.hodd.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.YearMonth

/** Statistics are derived from the local catalog; no additional stored data is required. */
data class CollectionInsights(
    val total: Int,
    val owned: Int,
    val wanted: Int,
    val lent: Int,
    val byKind: List<Pair<String, Int>>,
    val byPlatform: List<Pair<String, Int>>,
    val byCollection: List<Pair<String, Int>>,
    val monthlyAdditions: List<Pair<YearMonth, Int>>,
    val tagged: Int,
    val located: Int,
)

fun Catalog.insights(collectionId: String? = null): CollectionInsights {
    val selected = items.filter { collectionId == null || it.collectionId == collectionId }
    fun ranked(values: List<String>): List<Pair<String, Int>> =
        values.groupingBy { it }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    val monthly = selected.mapNotNull { item ->
        if (item.addedAt < 0) null else runCatching {
            YearMonth.from(Instant.ofEpochMilli(item.addedAt).atZone(ZoneOffset.UTC))
        }.getOrNull()
    }.groupingBy { it }.eachCount().toList().sortedBy { it.first }
    val names = collections.associate { it.id to it.name }
    return CollectionInsights(
        total = selected.size,
        owned = selected.count { it.status != ItemStatus.WANTED },
        wanted = selected.count { it.status == ItemStatus.WANTED },
        lent = selected.count { it.status == ItemStatus.LENT },
        byKind = ranked(selected.map { it.kind.ifBlank { "Other" } }),
        byPlatform = ranked(selected.filter { it.kind == "Game" }
            .map { it.attributes["platform"]?.takeIf(String::isNotBlank) ?: "Unspecified" }),
        byCollection = ranked(selected.map { names[it.collectionId] ?: "Unknown collection" }),
        monthlyAdditions = monthly,
        tagged = selected.count { it.tags.isNotEmpty() },
        located = selected.count { it.location.isNotBlank() },
    )
}
