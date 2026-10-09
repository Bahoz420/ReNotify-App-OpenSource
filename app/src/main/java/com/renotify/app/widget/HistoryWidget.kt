package com.renotify.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.renotify.app.MainActivity
import com.renotify.app.R
import com.renotify.app.data.ReNotifyDatabase
import com.renotify.app.repost.Reposter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home screen widget: the latest notifications, one tap sends an entry again.
 * The list itself is served by [HistoryWidgetService]; this provider only builds
 * the frame and turns item taps into resends.
 *
 * [HistoryWidgetCompact] is the same widget one row high, so it only overrides
 * the frame it is painted into.
 */
open class HistoryWidget : AppWidgetProvider() {

    /** The frame around the list. Must contain widget_list and widget_empty. */
    protected open val layoutRes: Int = R.layout.widget_history

    /** False for frames without the app name row on top. */
    protected open val hasHeader: Boolean = true

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RESEND) {
            // The receiver has to be exported for APPWIDGET_UPDATE, so treat the
            // id as untrusted: the worst a foreign broadcast achieves is that one
            // of the user's own notifications shows up again.
            val id = intent.getLongExtra(EXTRA_ID, -1L)
            if (id > 0) resend(context.applicationContext, id)
        }
        super.onReceive(context, intent)
    }

    private fun resend(context: Context, id: Long) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val items = ReNotifyDatabase.get(context).notificationDao().getByIds(listOf(id))
                if (items.isNotEmpty()) Reposter.repost(context, items)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, layoutRes)

        if (hasHeader) {
            views.setOnClickPendingIntent(
                R.id.widget_header,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }

        val listIntent = Intent(context, HistoryWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // Unique data, otherwise several widgets share one factory instance.
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list, listIntent)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // Collection items cannot own a PendingIntent; they fill in this template.
        // It must stay mutable so the entry id survives the merge.
        views.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, HistoryWidget::class.java).setAction(ACTION_RESEND),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )

        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        const val ACTION_RESEND = "com.renotify.app.widget.RESEND"
        const val EXTRA_ID = "entry_id"

        /** Entries the widget keeps in memory; every one carries an app icon bitmap. */
        const val MAX_ITEMS = 15

        /**
         * Repaints every placed widget, in all three shapes, after the history
         * changed. Free when none is placed, so callers do not have to check
         * first. The one entry point: nobody should have to know which widget
         * shapes exist.
         */
        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, HistoryWidget::class.java)
                ) + manager.getAppWidgetIds(
                    ComponentName(context, HistoryWidgetCompact::class.java)
                )
                if (ids.isNotEmpty()) {
                    manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
                }
            } catch (_: Exception) {
            }
            // Painted by hand instead of served by a factory, so it needs its own call.
            HistoryWidgetSummary.refresh(context)
        }
    }
}
