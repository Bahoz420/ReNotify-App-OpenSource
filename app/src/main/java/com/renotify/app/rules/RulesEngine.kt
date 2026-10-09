package com.renotify.app.rules

import android.content.Context
import com.renotify.app.data.ReNotifyDatabase
import com.renotify.app.data.Rule
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Keeps an in-memory snapshot of the enabled rules so the notification
 * listener can match synchronously (no DB round-trip on the hot path).
 */
object RulesEngine {

    @Volatile
    private var rules: List<Rule> = emptyList()

    /** False until the snapshot below has actually seen the database. */
    @Volatile
    private var loaded = false

    @Volatile
    private var started = false

    /** Call once from Application.onCreate. */
    fun init(context: Context) {
        if (started) return
        started = true
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ReNotifyDatabase.get(context.applicationContext).ruleDao()
                .observeAll().collect {
                    rules = it
                    loaded = true
                }
        }
    }

    /**
     * The enabled rule that decides about this notification, or null. When a
     * hide rule and a silence rule both match, hiding wins: it is the stronger
     * instruction, and the user wrote it for a reason.
     */
    fun match(context: Context, packageName: String, title: String, text: String): Rule? {
        val active = if (loaded) rules else loadNow(context)
        if (active.isEmpty()) return null
        val cal = Calendar.getInstance()
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val matching = active.filter { rule ->
            rule.enabled &&
                matchesApp(rule, packageName) &&
                matchesTime(rule, minuteOfDay) &&
                matchesKeywords(rule, haystackFor(rule, title, text))
        }
        return matching.firstOrNull { !it.silences } ?: matching.firstOrNull()
    }

    /**
     * The observer in [init] needs one database round trip before it delivers
     * anything, and the system starts this process from scratch every time it
     * has reclaimed it, which overnight is most of the time. A notification
     * arriving in that window used to find an empty rule set and go through
     * untouched: the rule looked broken exactly when the phone had been idle
     * for a while. One small blocking query on the listener callback is the
     * cheaper mistake.
     */
    private fun loadNow(context: Context): List<Rule> = try {
        runBlocking {
            ReNotifyDatabase.get(context.applicationContext).ruleDao().getAll()
        }.also {
            rules = it
            loaded = true
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun haystackFor(rule: Rule, title: String, text: String): String =
        when (rule.scope) {
            Rule.SCOPE_TITLE -> title.lowercase()
            Rule.SCOPE_TEXT -> text.lowercase()
            else -> "$title\n$text".lowercase()
        }

    private fun matchesApp(rule: Rule, packageName: String): Boolean =
        rule.packageName == null || rule.packageName == packageName

    private fun matchesTime(rule: Rule, minuteOfDay: Int): Boolean {
        val start = rule.startMinute ?: return true
        val end = rule.endMinute ?: return true
        return if (start <= end) {
            minuteOfDay in start until end
        } else {
            // Overnight window, e.g. 22:00 to 08:00.
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    private fun matchesKeywords(rule: Rule, haystack: String): Boolean {
        val keywords = rule.keywordList()
        return when (rule.mode) {
            Rule.MODE_CONTAINS ->
                keywords.isNotEmpty() && keywords.any { haystack.contains(it.lowercase()) }
            Rule.MODE_NOT_CONTAINS ->
                keywords.isNotEmpty() && keywords.none { haystack.contains(it.lowercase()) }
            else -> true // MODE_ANY
        }
    }
}
