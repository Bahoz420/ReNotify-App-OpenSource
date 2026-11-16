package com.renotify.app

import com.renotify.app.data.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTierTest {

    private fun rule(id: Long, createdAt: Long, enabled: Boolean = true) =
        Rule(id = id, createdAt = createdAt, enabled = enabled)

    // Made in this order: 2 first, then 3, then 1.
    private val rules = listOf(rule(1, 300), rule(2, 100), rule(3, 200))

    @After
    fun reset() {
        FreeTier.install { true }
    }

    @Test
    fun withProEveryRuleIsActiveOldestFirst() {
        assertEquals(listOf(2L, 3L, 1L), FreeTier.activeRules(rules, unlimited = true).map { it.id })
        assertTrue(FreeTier.pausedRuleIds(rules, unlimited = true).isEmpty())
    }

    @Test
    fun withoutProOnlyTheOldestRuleIsActive() {
        assertEquals(listOf(2L), FreeTier.activeRules(rules, unlimited = false).map { it.id })
        assertEquals(setOf(1L, 3L), FreeTier.pausedRuleIds(rules, unlimited = false))
    }

    @Test
    fun switchedOffRulesAreNeitherActiveNorPaused() {
        val list = listOf(rule(2, 100, enabled = false), rule(3, 200), rule(1, 300))
        // The oldest rule is off, so the next one takes its place.
        assertEquals(listOf(3L), FreeTier.activeRules(list, unlimited = false).map { it.id })
        assertEquals(setOf(1L), FreeTier.pausedRuleIds(list, unlimited = false))
    }

    @Test
    fun rulesWithoutCreationTimeAreOrderedById() {
        val list = listOf(rule(5, 0), rule(4, 0))
        assertEquals(listOf(4L), FreeTier.activeRules(list, unlimited = false).map { it.id })
    }

    @Test
    fun theInstalledCheckDecidesTheDefault() {
        FreeTier.install { false }
        assertEquals(1, FreeTier.activeRules(rules).size)
        FreeTier.install { true }
        assertEquals(3, FreeTier.activeRules(rules).size)
    }

    @Test
    fun withoutInstallNothingIsLimited() {
        // The open source edition never installs a check.
        assertTrue(FreeTier.unlimited)
    }
}
