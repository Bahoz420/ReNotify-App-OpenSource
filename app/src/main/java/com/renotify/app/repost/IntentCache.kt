package com.renotify.app.repost

import android.app.PendingIntent

/**
 * "Boost": in-memory cache of the original notifications' click actions.
 * PendingIntents cannot be persisted - they die with a reboot, a process
 * restart, or when the sending app cancels them. This best-effort cache keeps
 * the real tap target working for recently captured notifications; the
 * Reposter falls back to the source app's launch intent otherwise. The
 * advertised window is [TTL_HOURS]; anything longer is technically impossible.
 */
object IntentCache {

    const val TTL_HOURS = 6
    private const val TTL_MS = TTL_HOURS * 60 * 60 * 1000L
    private const val MAX_ENTRIES = 400

    private class Entry(val intent: PendingIntent, val at: Long)

    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun put(sbnKey: String, intent: PendingIntent?) {
        if (intent == null) return
        cache[sbnKey] = Entry(intent, System.currentTimeMillis())
    }

    @Synchronized
    fun get(sbnKey: String): PendingIntent? {
        val entry = cache[sbnKey] ?: return null
        if (System.currentTimeMillis() - entry.at > TTL_MS) {
            cache.remove(sbnKey)
            return null
        }
        return entry.intent
    }

    fun has(sbnKey: String): Boolean = get(sbnKey) != null
}
