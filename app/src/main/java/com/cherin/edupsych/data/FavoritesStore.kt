package com.cherin.edupsych.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Set<String> of favorite paper IDs, persisted in SharedPreferences.
 * Single source of truth — UI mirrors via mutableStateOf and writes through.
 */
object FavoritesStore {
    private const val KEY = "favorites"

    fun load(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("edupsych", Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
    }

    fun save(context: Context, ids: Set<String>) {
        context.getSharedPreferences("edupsych", Context.MODE_PRIVATE).edit {
            putStringSet(KEY, ids)
        }
    }

    fun toggle(current: Set<String>, id: String): Set<String> =
        if (id in current) current - id else current + id
}
