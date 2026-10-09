package com.renotify.app

import android.content.Context
import android.content.Intent
import com.renotify.app.data.ReNotifyDatabase

/**
 * Complete app list for pickers (rules, app filter): all installed launcher
 * apps (visible through the MAIN/LAUNCHER `<queries>` intent, no
 * QUERY_ALL_PACKAGES needed) merged with every app that ever posted a
 * notification, so system apps without a launcher entry still show up.
 */
object AppCatalog {

    data class Entry(val packageName: String, val label: String)

    suspend fun all(context: Context): List<Entry> {
        val pm = context.packageManager
        val launcher = try {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).mapNotNull { info ->
                info.activityInfo?.packageName?.let { pkg ->
                    Entry(pkg, info.loadLabel(pm).toString())
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        val seen = try {
            ReNotifyDatabase.get(context).notificationDao().getApps()
                .map { Entry(it.packageName, it.appLabel) }
        } catch (_: Exception) {
            emptyList()
        }
        return (launcher + seen)
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
