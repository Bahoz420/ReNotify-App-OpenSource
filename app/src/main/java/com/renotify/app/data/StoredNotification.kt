package com.renotify.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class StoredNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sbnKey: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val removedAt: Long? = null,
    /** Non-null while a snooze/reminder alarm is pending for this entry. */
    val scheduledFor: Long? = null,
    /** Icon key from [com.renotify.app.NotificationIcons] for self-created entries. */
    val customIcon: String? = null,
    /** True when a rule hid this notification; it is archived here regardless. */
    @ColumnInfo(defaultValue = "0")
    val blocked: Boolean = false,
    /**
     * Per-entry delivery setup ([Delivery] bitmask). null = fall back to the
     * rule for this app, then to [Delivery.DEFAULT].
     */
    val delivery: Int? = null,
)
