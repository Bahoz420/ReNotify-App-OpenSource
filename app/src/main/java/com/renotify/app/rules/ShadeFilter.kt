package com.renotify.app.rules

import android.content.Context

/**
 * Allow list for the status bar: while it is on, only the apps the user picked
 * keep their notifications in the shade. Everything else is pulled out right
 * away and lives on in the history, exactly like a rule match.
 *
 * The inverse of the app filter, which decides what gets recorded at all. This
 * one decides what stays visible.
 *
 * Protected notifications (calls, alarms, calendar reminders, navigation,
 * media sessions, running services) are never touched, see
 * ReNotifyListenerService.
 */
object ShadeFilter {

    private const val PREFS = "settings"
    private const val KEY_ENABLED = "shade_filter_enabled"
    private const val KEY_ALLOWED = "shade_allowed_packages"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Off by default; switching it on with an empty list clears the whole shade. */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun allowed(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED, emptySet())?.toSet() ?: emptySet()

    fun setAllowed(context: Context, packages: Set<String>) {
        // A copy: SharedPreferences must never be handed a set it keeps a reference to.
        prefs(context).edit().putStringSet(KEY_ALLOWED, packages.toSet()).apply()
    }

    /** True when this app's notifications have to leave the status bar. */
    fun hides(context: Context, packageName: String): Boolean =
        isEnabled(context) && packageName !in allowed(context)
}
