package com.renotify.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.renotify.app.MainActivity
import com.renotify.app.R
import com.renotify.app.data.ReNotifyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * The summary widget: how many notifications arrived today and which apps sent
 * them. No titles, no text, nothing to read on the home screen.
 *
 * Unlike [HistoryWidget] this one has no list and no service behind it. Its
 * whole content is painted here, so every change to the history has to come
 * back through [refresh].
 */
class HistoryWidgetSummary : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Reading the history is a database call, which must not happen on the
        // main thread, so the broadcast is held open until the paint is done.
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                render(app, appWidgetManager, appWidgetIds)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** As many app icons as fit next to the number at the smallest size. */
        private const val MAX_ICONS = 4

        /** Small on purpose: every bitmap travels through the widget binder call. */
        private const val ICON_PX = 64

        private val ICON_IDS = intArrayOf(
            R.id.summary_icon_1,
            R.id.summary_icon_2,
            R.id.summary_icon_3,
            R.id.summary_icon_4,
        )

        /** Repaints every placed summary widget. Free when none is placed. */
        fun refresh(context: Context) {
            try {
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(app, HistoryWidgetSummary::class.java)
                )
                if (ids.isEmpty()) return
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        render(app, manager, ids)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        /** Blocking database read, so never call this on the main thread. */
        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val since = startOfToday()
            val dao = ReNotifyDatabase.get(context).notificationDao()
            val count = runBlocking { dao.countSince(since) }
            val apps = runBlocking { dao.appsSince(since, MAX_ICONS) }

            val views = RemoteViews(context.packageName, R.layout.widget_summary)
            views.setTextViewText(
                R.id.summary_count,
                if (count > 999) "999+" else count.toString()
            )
            views.setTextViewText(
                R.id.summary_label,
                context.getString(R.string.widget_summary_today)
            )
            ICON_IDS.forEachIndexed { index, viewId ->
                val icon = apps.getOrNull(index)?.let { loadIcon(context, it.packageName) }
                if (icon == null) {
                    views.setViewVisibility(viewId, View.GONE)
                } else {
                    views.setImageViewBitmap(viewId, icon)
                    views.setViewVisibility(viewId, View.VISIBLE)
                }
            }
            views.setOnClickPendingIntent(
                R.id.summary_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        private fun loadIcon(context: Context, packageName: String): Bitmap? = try {
            context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX)
        } catch (_: Exception) {
            null
        }
    }
}
