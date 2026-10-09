package com.renotify.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class AppEntry(val packageName: String, val appLabel: String)

/** Slim projection for the Trends tab - no content columns. */
data class StatRow(val postedAt: Long, val packageName: String, val appLabel: String)

/** One app in the summary widget: the icon to draw, and how recent it is. */
data class WidgetApp(val packageName: String, val latestAt: Long)

@Dao
interface NotificationDao {

    @Query(
        """SELECT * FROM notifications
           WHERE (title LIKE '%' || :query || '%'
              OR text LIKE '%' || :query || '%'
              OR appLabel LIKE '%' || :query || '%')
              AND postedAt >= :minPostedAt
           ORDER BY postedAt DESC"""
    )
    fun observeAll(query: String, minPostedAt: Long): Flow<List<StoredNotification>>

    @Query("SELECT DISTINCT packageName, appLabel FROM notifications ORDER BY appLabel COLLATE NOCASE")
    suspend fun getApps(): List<AppEntry>

    @Query("SELECT * FROM notifications WHERE sbnKey = :sbnKey AND removedAt IS NULL LIMIT 1")
    suspend fun findActiveByKey(sbnKey: String): StoredNotification?

    /**
     * Exact match on the one notification, removed or not. The catch-up sweep
     * uses it to tell "this arrived while the listener was gone" from "this is
     * already in the history", where [findActiveByKey] would answer wrongly
     * because dismissing an entry marks it removed.
     */
    @Query("SELECT * FROM notifications WHERE sbnKey = :sbnKey AND postedAt = :postedAt LIMIT 1")
    suspend fun findByKeyAt(sbnKey: String, postedAt: Long): StoredNotification?

    @Insert
    suspend fun insert(notification: StoredNotification): Long

    @Update
    suspend fun update(notification: StoredNotification)

    @Query("UPDATE notifications SET removedAt = :removedAt WHERE sbnKey = :sbnKey AND removedAt IS NULL")
    suspend fun markRemoved(sbnKey: String, removedAt: Long)

    @Query("SELECT * FROM notifications WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<StoredNotification>

    @Query("SELECT * FROM notifications ORDER BY postedAt ASC")
    suspend fun getAll(): List<StoredNotification>

    /** Newest first for the home screen widget; pending reminders stay out of it. */
    @Query(
        """SELECT * FROM notifications WHERE scheduledFor IS NULL
           ORDER BY postedAt DESC LIMIT :limit"""
    )
    suspend fun getRecent(limit: Int): List<StoredNotification>

    @Query("SELECT * FROM notifications WHERE scheduledFor IS NOT NULL ORDER BY scheduledFor ASC")
    fun observeScheduled(): Flow<List<StoredNotification>>

    @Query("SELECT * FROM notifications WHERE scheduledFor IS NOT NULL")
    suspend fun getScheduled(): List<StoredNotification>

    @Query("UPDATE notifications SET scheduledFor = :triggerAt WHERE id = :id")
    suspend fun setScheduled(id: Long, triggerAt: Long)

    @Query("UPDATE notifications SET scheduledFor = NULL WHERE id = :id")
    suspend fun clearScheduled(id: Long)

    @Query("UPDATE notifications SET delivery = :delivery WHERE id = :id")
    suspend fun setDelivery(id: Long, delivery: Int)

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Retention: everything older than the window, except pending reminders. */
    @Query("DELETE FROM notifications WHERE postedAt < :cutoff AND scheduledFor IS NULL")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM notifications WHERE sbnKey LIKE 'demo-%'")
    suspend fun deleteDemos()

    @Query("SELECT postedAt, packageName, appLabel FROM notifications")
    fun observeStatRows(): Flow<List<StatRow>>

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun count(): Int

    /** Summary widget: how many arrived since midnight, reminders excluded. */
    @Query(
        """SELECT COUNT(*) FROM notifications
           WHERE scheduledFor IS NULL AND postedAt >= :since"""
    )
    suspend fun countSince(since: Long): Int

    /** Summary widget: the apps behind those entries, most recent first. */
    @Query(
        """SELECT packageName, MAX(postedAt) AS latestAt FROM notifications
           WHERE scheduledFor IS NULL AND postedAt >= :since
           GROUP BY packageName ORDER BY latestAt DESC LIMIT :limit"""
    )
    suspend fun appsSince(since: Long, limit: Int): List<WidgetApp>

    @Query("SELECT COUNT(*) FROM notifications WHERE postedAt < :cutoff")
    fun observeOlderCount(cutoff: Long): Flow<Int>
}
