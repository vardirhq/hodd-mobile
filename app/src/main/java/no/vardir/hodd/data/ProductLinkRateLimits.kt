package no.vardir.hodd.data

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** In-memory per-host backoff shared by page and image requests; never retries automatically. */
class ProductLinkRateLimits(
    private val now: () -> Instant = { Instant.now() },
    private val elapsed: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private val blockedUntil = linkedMapOf<String, Long>()

    @Synchronized fun check(host: String) {
        val remaining = (blockedUntil[host.lowercase(Locale.ROOT)] ?: return) - elapsed()
        if (remaining > 0) error("The store is limiting requests. Wait ${(remaining + 999) / 1000} seconds before trying again, or open it in your browser.")
        blockedUntil.remove(host.lowercase(Locale.ROOT))
    }

    @Synchronized fun refused(host: String, retryAfter: String?, status: Int = 429): Nothing {
        val raw = retryAfter?.trim().orEmpty()
        val seconds = raw.toLongOrNull()?.takeIf { it >= 0 } ?: runCatching {
            val date = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            val wait = java.time.Duration.between(now(), date)
            (wait.seconds + if (wait.nano > 0) 1 else 0).coerceAtLeast(1)
        }.getOrDefault(60)
        // Saturate only to prevent arithmetic overflow for malformed/absurd server values.
        val delay = seconds.coerceIn(1, (Long.MAX_VALUE / 2) / 1000) * 1000
        val key = host.lowercase(Locale.ROOT)
        blockedUntil[key] = elapsed() + delay
        if (blockedUntil.size > 64) blockedUntil.remove(blockedUntil.keys.first())
        error("The store is limiting or temporarily refusing requests (HTTP $status). Wait at least ${delay / 1000} seconds before trying again. You can open it in your browser or add details manually.")
    }
}
