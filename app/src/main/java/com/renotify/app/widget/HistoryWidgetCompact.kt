package com.renotify.app.widget

import com.renotify.app.R

/**
 * The single row version of [HistoryWidget], for people who want the latest
 * notification on the home screen without giving up a whole block of it.
 *
 * Same list, same service, same resend on tap. Only the frame differs: no app
 * name row, because at one cell high it would leave no room for the entry. The
 * list still scrolls, so a single row reaches the whole recent history.
 */
class HistoryWidgetCompact : HistoryWidget() {
    override val layoutRes: Int = R.layout.widget_compact
    override val hasHeader: Boolean = false
}
