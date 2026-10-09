package com.renotify.app

/**
 * Category icons the user can pick for self-created notifications. Keys are stored in
 * [com.renotify.app.data.StoredNotification.customIcon]; unknown keys fall back to none.
 */
object NotificationIcons {

    val all: List<Pair<String, Int>> = listOf(
        "bell" to R.drawable.ic_cat_bell,
        "calendar" to R.drawable.ic_cat_calendar,
        "chat" to R.drawable.ic_cat_chat,
        "cart" to R.drawable.ic_cat_cart,
        "euro" to R.drawable.ic_cat_euro,
        "heart" to R.drawable.ic_cat_heart,
        "work" to R.drawable.ic_cat_work,
        "flight" to R.drawable.ic_cat_flight,
        "gift" to R.drawable.ic_cat_gift,
        "home" to R.drawable.ic_cat_home,
    )

    const val DEFAULT = "bell"

    fun res(key: String?): Int? = all.firstOrNull { it.first == key }?.second
}
