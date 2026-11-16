package com.renotify.app

import com.renotify.app.data.Rule

/**
 * The limits of the free version in the code the open source edition shares
 * with the Play edition: the notification listener and the rules.
 *
 * The Play edition hands in its purchase and trial state when the app starts,
 * see [install] in ReNotifyApp. The open source edition never calls it, so
 * there nothing is ever limited.
 *
 * Until 1.9 the limits were only checked when something was switched on.
 * Rules and the status bar filter set up during the trial then kept working
 * for good, which left little reason to buy what the trial had shown.
 */
object FreeTier {

    /** Rules that work without Pro: the oldest one that is switched on. */
    const val FREE_RULES = 1

    @Volatile
    private var check: () -> Boolean = { true }

    fun install(unlimited: () -> Boolean) {
        check = unlimited
    }

    /** True with Pro, during the trial, and always in the open source edition. */
    val unlimited: Boolean get() = check()

    /**
     * The switched-on rules that take effect, oldest first. Without Pro that
     * is the first one only. The others keep their settings and work again
     * with Pro: they are paused, never deleted.
     */
    fun activeRules(rules: List<Rule>, unlimited: Boolean = this.unlimited): List<Rule> {
        val on = rules.filter { it.enabled }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        return if (unlimited) on else on.take(FREE_RULES)
    }

    /** Switched-on rules that do nothing right now because Pro is missing. */
    fun pausedRuleIds(rules: List<Rule>, unlimited: Boolean = this.unlimited): Set<Long> {
        if (unlimited) return emptySet()
        val active = activeRules(rules, unlimited = false).map { it.id }.toSet()
        return rules.filter { it.enabled && it.id !in active }.map { it.id }.toSet()
    }
}
