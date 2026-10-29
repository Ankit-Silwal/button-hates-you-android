package com.ankitsilwal.buttonhatesyou.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The games the player starred, in the order they starred them. Saved on the
 * device (not in the profile: it's a preference, not progress) and observable,
 * so the Games room re-sorts the moment one is toggled.
 */
class Favorites private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    var ids: List<String> by mutableStateOf(prefs.getString(KEY, "").orEmpty().split(',').filter { it.isNotBlank() })
        private set

    operator fun contains(id: String) = id in ids

    /** Stars [id] (added last) or un-stars it; returns whether it's a favorite now. */
    fun toggle(id: String): Boolean {
        val on = id !in ids
        ids = if (on) ids + id else ids - id
        prefs.edit().putString(KEY, ids.joinToString(",")).apply()
        return on
    }

    companion object {
        private const val KEY = "games"

        @Volatile private var instance: Favorites? = null

        fun get(context: Context): Favorites =
            instance ?: synchronized(this) { instance ?: Favorites(context).also { instance = it } }
    }
}
