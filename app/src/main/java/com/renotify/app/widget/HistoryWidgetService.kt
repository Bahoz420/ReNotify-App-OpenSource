package com.renotify.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import com.renotify.app.R
import com.renotify.app.data.ReNotifyDatabase
import com.renotify.app.data.StoredNotification
import kotlinx.coroutines.runBlocking

/** Feeds [HistoryWidget]'s list with the newest history entries. */
class HistoryWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        HistoryWidgetFactory(applicationContext)
}

private class HistoryWidgetFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<StoredNotification> = emptyList()
    private val icons = mutableMapOf<String, Bitmap?>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Called on a binder thread, so blocking here is fine and keeps the
        // launcher from painting a half-filled list.
        items = try {
            runBlocking {
                ReNotifyDatabase.get(context).notificationDao()
                    .getRecent(HistoryWidget.MAX_ITEMS)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        items = emptyList()
        icons.clear()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_item)
        val views = RemoteViews(context.packageName, R.layout.widget_item)

        views.setTextViewText(R.id.item_app, item.appLabel.uppercase())
        views.setTextViewText(
            R.id.item_time,
            DateUtils.getRelativeTimeSpanString(
                item.postedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        )

        // A notification without a title puts its text on the title line, so the
        // row never starts with an empty first line.
        val title = item.title.ifBlank { item.text }
        val subtitle = if (item.title.isBlank()) "" else item.text
        views.setTextViewText(R.id.item_title, title)
        views.setTextViewText(R.id.item_text, subtitle)
        views.setViewVisibility(
            R.id.item_text,
            if (subtitle.isBlank()) View.GONE else View.VISIBLE
        )

        val icon = icons.getOrPut(item.packageName) { loadIcon(item.packageName) }
        if (icon != null) {
            views.setImageViewBitmap(R.id.item_icon, icon)
            views.setViewVisibility(R.id.item_icon, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.item_icon, View.GONE)
        }

        views.setOnClickFillInIntent(
            R.id.item_root,
            Intent().putExtra(HistoryWidget.EXTRA_ID, item.id)
        )
        return views
    }

    /** Small on purpose: every bitmap travels through the widget binder call. */
    private fun loadIcon(packageName: String): Bitmap? = try {
        context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX)
    } catch (_: Exception) {
        null
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        const val ICON_PX = 48
    }
}
