package com.crylo.mandi.core

import java.time.LocalDate

/**
 * One price series: a commodity of a given variety and grade at one market. This is
 * the unit a favourite points at and the unit history is kept for.
 */
data class SeriesKey(
    val state: String,
    val district: String,
    val market: String,
    val commodity: String,
    val variety: String,
    val grade: String,
) {
    val stateSlug: String get() = Slug.of(state)

    /** Stable string form for storing favourites. */
    fun encode(): String = listOf(state, district, market, commodity, variety, grade)
        .joinToString(SEPARATOR)

    companion object {
        // Unit separator: never appears in place or commodity names.
        private const val SEPARATOR = "\u001F"

        fun decode(text: String): SeriesKey? {
            val parts = text.split(SEPARATOR)
            if (parts.size != 6) return null
            return SeriesKey(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
        }
    }
}

/** Latest reported prices for one series, all in rupees per quintal. */
data class PriceRow(
    val key: SeriesKey,
    val arrival: LocalDate,
    val min: Double,
    val max: Double,
    val modal: Double,
    val previousDate: LocalDate?,
    val previousModal: Double?,
) {
    val change: PriceChange? get() = PriceChange.of(modal, previousModal)
}

data class StateInfo(
    val slug: String,
    val name: String,
    val markets: Int,
    val rows: Int,
    val latest: LocalDate?,
)

data class MandiIndex(val generated: String, val states: List<StateInfo>)

data class StateData(val name: String, val generated: String, val rows: List<PriceRow>) {
    val districts: List<String> by lazy { rows.map { it.key.district }.distinct().sorted() }

    fun markets(district: String): List<String> =
        rows.filter { it.key.district == district }.map { it.key.market }.distinct().sorted()

    fun rowsAt(district: String, market: String): List<PriceRow> = rows
        .filter { it.key.district == district && it.key.market == market }
        .sortedWith(compareBy({ it.key.commodity }, { it.key.variety }, { it.key.grade }))

    fun find(key: SeriesKey): PriceRow? = rows.firstOrNull { it.key == key }
}

/** Modal prices per day for every series of one state; `null` where a market did not report. */
data class StateHistory(
    val generated: String,
    val days: List<LocalDate>,
    val series: Map<SeriesKey, List<Double?>>,
) {
    fun valuesFor(key: SeriesKey): List<Double?> = series[key] ?: emptyList()
}
