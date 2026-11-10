package com.crylo.mandi.data

import android.content.Context
import androidx.core.content.edit
import com.crylo.mandi.core.SeriesKey

/** Favourite series, stored on the device only. A handful of strings needs nothing heavier. */
class Favourites(context: Context) {
    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    fun load(): List<SeriesKey> = (prefs.getStringSet(KEY, emptySet()) ?: emptySet())
        .mapNotNull(SeriesKey::decode)
        .sortedWith(compareBy({ it.commodity }, { it.market }))

    fun save(keys: Collection<SeriesKey>) {
        prefs.edit { putStringSet(KEY, keys.map { it.encode() }.toSet()) }
    }

    private companion object {
        const val KEY = "series"
    }
}
