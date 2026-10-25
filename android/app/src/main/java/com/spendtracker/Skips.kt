package com.spendtracker

import android.content.Context

/**
 * Expenses marked "not required this month". The sheet row stays blank, so this is remembered on the phone
 * as "yyyy-MM|item name".
 */
object Skips {
    private const val KEY = "skipped_items"

    fun key(month: String, item: String) = month + "|" + item.trim().lowercase()

    fun all(c: Context): Set<String> =
        c.getSharedPreferences("spend_tracker", Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()

    fun save(c: Context, set: Set<String>) {
        c.getSharedPreferences("spend_tracker", Context.MODE_PRIVATE).edit().putStringSet(KEY, HashSet(set)).apply()
    }
}
