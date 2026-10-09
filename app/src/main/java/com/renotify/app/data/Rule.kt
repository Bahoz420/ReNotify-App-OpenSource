package com.renotify.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A notification rule ("When a notification arrives from X that contains Y
 * between HH:MM and HH:MM, then hide it / silence it").
 *
 * Hide: the entry is archived with `blocked = true` and dismissed from the
 * shade. Silence: the entry is archived normally and stays in the shade, but
 * as a quiet copy without banner, sound or vibration.
 */
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null = any app. */
    val packageName: String? = null,
    val appLabel: String? = null,
    val mode: String = MODE_ANY,
    /** Newline-separated keywords; empty when [mode] == [MODE_ANY]. */
    val keywords: String = "",
    /** Where keywords are matched: title, text, or both. */
    @ColumnInfo(defaultValue = "both")
    val scope: String = SCOPE_BOTH,
    /** Minute of day (0..1439); both null = all day. start > end wraps midnight. */
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val enabled: Boolean = true,
    /**
     * Delivery setup ([Delivery] bitmask) for notifications of this rule's app
     * whenever ReNotify sends one, e.g. a resend from the history.
     */
    @ColumnInfo(defaultValue = "7")
    val delivery: Int = Delivery.DEFAULT,
    /** What a match does: [ACTION_HIDE] or [ACTION_SILENCE]. */
    @ColumnInfo(defaultValue = "hide")
    val action: String = ACTION_HIDE,
    val matchCount: Int = 0,
    val createdAt: Long = 0,
) {
    fun keywordList(): List<String> =
        keywords.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    val silences: Boolean get() = action == ACTION_SILENCE

    companion object {
        const val MODE_ANY = "any"
        const val MODE_CONTAINS = "contains"
        const val MODE_NOT_CONTAINS = "not_contains"

        const val SCOPE_BOTH = "both"
        const val SCOPE_TITLE = "title"
        const val SCOPE_TEXT = "text"

        const val ACTION_HIDE = "hide"
        const val ACTION_SILENCE = "silence"
    }
}
