package com.renotify.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.renotify.app.data.Delivery
import com.renotify.app.data.ReNotifyDatabase
import com.renotify.app.data.Rule
import com.renotify.app.data.StatRow
import com.renotify.app.data.StoredNotification
import com.renotify.app.repost.Reposter
import com.renotify.app.service.ReNotifyListenerService
import com.renotify.app.snooze.Snoozer
import com.renotify.app.widget.HistoryWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = ReNotifyDatabase.get(app).notificationDao()
    private val ruleDao = ReNotifyDatabase.get(app).ruleDao()

    val searchQuery = MutableStateFlow("")

    /** This edition has no free window: the whole history is always visible. */
    private val unlimitedHistory = flowOf(true)

    val notifications: StateFlow<List<StoredNotification>> =
        combine(searchQuery, unlimitedHistory) { query, unlimited ->
            query to unlimited
        }.flatMapLatest { (query, unlimited) ->
            dao.observeAll(
                query,
                if (unlimited) 0L else System.currentTimeMillis() - FREE_HISTORY_MS
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * History grouping: null shows the app overview, a package name shows that
     * app's notifications. Search bypasses the overview and lists hits directly.
     */
    private val _openedApp = MutableStateFlow<String?>(null)
    val openedApp: StateFlow<String?> = _openedApp

    fun openApp(packageName: String) {
        _openedApp.value = packageName
    }

    fun closeApp() {
        _openedApp.value = null
    }

    /** One row per app for the history overview, most recent app first. */
    val appGroups: StateFlow<List<AppGroup>> = notifications
        .map { list ->
            // The list arrives newest first, so the first row of a group is its latest.
            list.groupBy { it.packageName }
                .map { (pkg, rows) ->
                    AppGroup(pkg, rows.first().appLabel, rows.size, rows.first().postedAt)
                }
                .sortedByDescending { it.latestAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What the history tab lists right now: one app when opened, else everything. */
    val visibleNotifications: StateFlow<List<StoredNotification>> =
        combine(notifications, _openedApp) { list, pkg ->
            if (pkg == null) list else list.filter { it.packageName == pkg }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _page = MutableStateFlow(0)

    /** Number of pages for the current (filtered) list, always at least 1. */
    val pageCount: StateFlow<Int> = visibleNotifications
        .map { ((it.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    /** Current page, clamped when the list shrinks. */
    val currentPage: StateFlow<Int> =
        combine(_page, pageCount) { page, count -> page.coerceIn(0, count - 1) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The slice of [visibleNotifications] shown on the current page. */
    val pagedNotifications: StateFlow<List<StoredNotification>> =
        combine(visibleNotifications, currentPage) { list, page ->
            list.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // A new search or another app starts back on the first page.
        viewModelScope.launch {
            searchQuery.drop(1).collect { _page.value = 0 }
        }
        viewModelScope.launch {
            _openedApp.drop(1).collect { _page.value = 0 }
        }
    }

    fun nextPage() {
        _page.value = (currentPage.value + 1).coerceAtMost(pageCount.value - 1)
    }

    fun previousPage() {
        _page.value = (currentPage.value - 1).coerceAtLeast(0)
    }

    /** Pending reminders: snoozed entries and self-created future notifications. */
    val scheduledNotifications: StateFlow<List<StoredNotification>> =
        dao.observeScheduled()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    /** All notification rules, newest first. */
    val rules: StateFlow<List<Rule>> =
        ruleDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveRule(rule: Rule) {
        viewModelScope.launch(Dispatchers.IO) {
            if (rule.id == 0L) {
                ruleDao.insert(rule.copy(createdAt = System.currentTimeMillis()))
            } else {
                ruleDao.update(rule)
            }
            // A new rule should clear what it covers out of the status bar now,
            // not only from the next notification onwards.
            ReNotifyListenerService.sweepShade()
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { ruleDao.delete(id) }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleDao.setEnabled(id, enabled)
            if (enabled) ReNotifyListenerService.sweepShade()
        }
    }

    /** Delivery setup ([Delivery] mask) for every notification of this rule's app. */
    fun setRuleDelivery(id: Long, delivery: Int) {
        viewModelScope.launch(Dispatchers.IO) { ruleDao.setDelivery(id, delivery) }
    }

    /** Delivery setup for one single notification, overriding any rule. */
    fun setNotificationDelivery(id: Long, delivery: Int) {
        viewModelScope.launch(Dispatchers.IO) { dao.setDelivery(id, delivery) }
    }

    /** Aggregated stats for the Trends tab, null while loading. */
    val trends: StateFlow<TrendsData?> =
        dao.observeStatRows()
            .map { computeTrends(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _lastRepostCount = MutableStateFlow<Int?>(null)
    val lastRepostCount: StateFlow<Int?> = _lastRepostCount

    fun toggleSelection(id: Long) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    /** Select all of the tab currently on screen; the tabs never share a selection. */
    fun selectAllVisible(tab: Int) {
        _selectedIds.value = when (tab) {
            TAB_SNOOZED -> scheduledNotifications.value.map { it.id }
            TAB_RULES -> rules.value.map { it.id }
            else -> pagedNotifications.value.map { it.id }
        }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun repostSingle(item: StoredNotification) {
        viewModelScope.launch(Dispatchers.IO) {
            _lastRepostCount.value = Reposter.repost(getApplication(), listOf(item))
        }
    }

    fun repostSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // Oldest first so the newest ends up on top of the shade.
            val items = dao.getByIds(ids).sortedBy { it.postedAt }
            _lastRepostCount.value = Reposter.repost(getApplication(), items)
            _selectedIds.value = emptySet()
        }
    }

    fun repostAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _lastRepostCount.value = Reposter.repost(getApplication(), dao.getAll())
        }
    }

    /** "Resend all" while one app is open: only that app's entries go out. */
    fun repostOpenedApp() {
        val items = visibleNotifications.value.sortedBy { it.postedAt }
        if (items.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _lastRepostCount.value = Reposter.repost(getApplication(), items)
        }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteByIds(ids)
            _selectedIds.value = emptySet()
            HistoryWidget.refresh(getApplication())
        }
    }

    /** Snoozed tab: fire the selected reminders right away. */
    fun sendSelectedScheduled() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val items = dao.getByIds(ids).sortedBy { it.scheduledFor ?: it.postedAt }
            items.forEach { Snoozer.cancel(getApplication(), it.id) }
            _lastRepostCount.value = Reposter.repost(getApplication(), items)
            _selectedIds.value = emptySet()
        }
    }

    /** Snoozed tab: drop the selected reminders; the entries stay in the history. */
    fun cancelSelectedReminders() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { Snoozer.cancel(getApplication(), it) }
            _selectedIds.value = emptySet()
        }
    }

    fun deleteSelectedRules() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ruleDao.deleteByIds(ids)
            _selectedIds.value = emptySet()
        }
    }

    /**
     * "Delete all" from the action row: exactly what the list shows right now,
     * one app or one search, never more than that. Reminders that are still
     * pending on a deleted entry have to be called off with it.
     */
    fun deleteVisible() {
        val items = visibleNotifications.value
        if (items.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            items.filter { it.scheduledFor != null }.forEach { Snoozer.cancel(app, it.id) }
            // Chunked because every id is a bound statement parameter and
            // SQLite stops at a few hundred of them.
            items.map { it.id }.chunked(400).forEach { dao.deleteByIds(it) }
            _selectedIds.value = emptySet()
            HistoryWidget.refresh(app)
        }
    }

    fun deleteSingle(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteByIds(listOf(id))
            HistoryWidget.refresh(getApplication())
        }
    }

    /**
     * User-created notification with a custom timestamp. A future
     * timestamp schedules delivery instead of posting immediately.
     */
    fun createCustom(title: String, text: String, postedAt: Long, iconKey: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val future = postedAt > System.currentTimeMillis() + 60_000
            val notification = StoredNotification(
                sbnKey = "custom-$postedAt-${System.nanoTime()}",
                packageName = app.packageName,
                appLabel = "ReNotify",
                title = title,
                text = text,
                postedAt = postedAt,
                scheduledFor = if (future) postedAt else null,
                customIcon = iconKey,
            )
            val id = dao.insert(notification)
            HistoryWidget.refresh(app)
            if (future) {
                Snoozer.schedule(app, id, postedAt)
            } else {
                _lastRepostCount.value = Reposter.repost(app, listOf(notification.copy(id = id)))
            }
        }
    }

    /** Fires a pending reminder right now and removes it from the Snoozed tab. */
    fun sendScheduledNow(item: StoredNotification) {
        viewModelScope.launch(Dispatchers.IO) {
            Snoozer.cancel(getApplication(), item.id)
            _lastRepostCount.value = Reposter.repost(getApplication(), listOf(item))
        }
    }

    /** Cancels a pending reminder; the entry stays in the history. */
    fun cancelReminder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            Snoozer.cancel(getApplication(), id)
        }
    }

    fun consumeRepostMessage() {
        _lastRepostCount.value = null
    }

    companion object {
        /** Free tier keeps the last 7 days of history. */
        const val FREE_HISTORY_MS = 7 * 24 * 60 * 60 * 1000L

        /** Notifications shown per page in the history list. */
        const val PAGE_SIZE = 20

        const val TAB_HISTORY = 0
        const val TAB_SNOOZED = 1
        const val TAB_TRENDS = 2
        const val TAB_RULES = 3

        private const val DAY_MS = 24 * 60 * 60 * 1000L

        internal fun computeTrends(rows: List<StatRow>): TrendsData {
            val now = System.currentTimeMillis()
            val total = rows.size
            val oldest = rows.minOfOrNull { it.postedAt } ?: now
            val spanDays = ((now - oldest) / DAY_MS + 1).coerceAtLeast(1)
            val perDay = total.toDouble() / spanDays

            val thisWeek = rows.count { it.postedAt >= now - 7 * DAY_MS }
            val lastWeek = rows.count {
                it.postedAt >= now - 14 * DAY_MS && it.postedAt < now - 7 * DAY_MS
            }

            val byApp = rows.groupBy { it.packageName }
                .map { (pkg, list) -> AppCount(pkg, list.first().appLabel, list.size) }
                .sortedByDescending { it.count }
            val topApps = byApp.take(5)
            val othersCount = byApp.drop(5).sumOf { it.count }

            val cal = java.util.Calendar.getInstance()
            val hourHist = IntArray(24)
            for (row in rows) {
                cal.timeInMillis = row.postedAt
                hourHist[cal.get(java.util.Calendar.HOUR_OF_DAY)]++
            }

            val startOfToday = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            // Index 6 = today, 0 = six days ago.
            val dayCounts = IntArray(7)
            for (row in rows) {
                val daysAgo =
                    if (row.postedAt >= startOfToday) 0
                    else ((startOfToday - row.postedAt - 1) / DAY_MS + 1).toInt()
                if (daysAgo in 0..6) dayCounts[6 - daysAgo]++
            }

            return TrendsData(
                total = total,
                perDay = perDay,
                thisWeek = thisWeek,
                lastWeek = lastWeek,
                topApps = topApps,
                othersCount = othersCount,
                hourHist = hourHist.toList(),
                dayCounts = dayCounts.toList(),
            )
        }
    }
}

data class AppCount(val packageName: String, val appLabel: String, val count: Int)

/** One app in the history overview: how many entries, and how fresh the newest is. */
data class AppGroup(
    val packageName: String,
    val appLabel: String,
    val count: Int,
    val latestAt: Long,
)

data class TrendsData(
    val total: Int,
    val perDay: Double,
    val thisWeek: Int,
    val lastWeek: Int,
    val topApps: List<AppCount>,
    val othersCount: Int,
    /** 24 buckets, index = hour of day. */
    val hourHist: List<Int>,
    /** 7 buckets, index 6 = today. */
    val dayCounts: List<Int>,
)
