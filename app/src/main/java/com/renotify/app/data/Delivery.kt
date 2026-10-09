package com.renotify.app.data

/**
 * How ReNotify delivers one notification: heads-up banner, sound, vibration
 * and torch blink. Stored as a bitmask so the whole setup fits into a single
 * column on both [StoredNotification] and [Rule].
 */
object Delivery {

    /** Heads-up banner on top of the screen instead of a silent shade entry. */
    const val PUSH = 1
    const val SOUND = 2
    const val VIBRATE = 4
    const val FLASH = 8

    /** Used whenever nothing more specific is set. Torch stays off on purpose. */
    const val DEFAULT = PUSH or SOUND or VIBRATE

    /** No banner, no sound, no vibration: lands quietly in the shade. */
    const val SILENT = 0

    val ALL = listOf(PUSH, SOUND, VIBRATE, FLASH)

    fun has(mask: Int, flag: Int): Boolean = mask and flag != 0

    fun with(mask: Int, flag: Int, on: Boolean): Int =
        if (on) mask or flag else mask and flag.inv()

    /**
     * The setup on the entry itself wins; without one the rule covering that
     * app decides, and only then the default. Rules for "any app" stay out of
     * it, they would silently override every single notification.
     */
    fun resolve(item: StoredNotification, rules: List<Rule>): Int =
        item.delivery
            ?: rules.firstOrNull { it.enabled && it.packageName == item.packageName }?.delivery
            ?: DEFAULT
}
