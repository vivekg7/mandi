package com.crylo.mandi.core

/** How many matches one state has for the current query. */
data class StateMatches(val slug: String, val name: String, val count: Int)

data class SearchResult(
    /** The rows to show, sorted, cut to the limit asked for. */
    val rows: List<PriceRow>,
    /** Matches in the chosen scope (one state, or all India) before the limit. */
    val total: Int,
    /** Matches across all India, whichever state is chosen. */
    val allIndia: Int,
    /** Matches per state across all India, most first. */
    val perState: List<StateMatches>,
) {
    companion object {
        val EMPTY = SearchResult(emptyList(), 0, 0, emptyList())
    }
}

/**
 * Commodity search over every loaded state.
 *
 * The state filter is applied before the limit, and the result always says how many
 * rows matched. Cutting first would silently drop whole states: rows are ordered by
 * state name within a commodity, and Tamil Nadu alone reports more than half of the
 * rows for common vegetables, so states late in the alphabet fell past the limit.
 */
object Search {
    const val MIN_QUERY = 2

    private val ORDER = compareBy<PriceRow>({ it.key.commodity })
        .thenByDescending { it.arrival }
        .thenBy { it.key.state }
        .thenBy { it.key.market }

    fun run(rows: Sequence<PriceRow>, query: String, stateSlug: String?, limit: Int): SearchResult {
        val q = query.trim()
        if (q.length < MIN_QUERY) return SearchResult.EMPTY
        val matches = rows.filter { CommodityNames.matches(it.key.commodity, q) }.toList()
        val bySlug = matches.groupBy { it.key.stateSlug }
        val perState = bySlug
            .map { (slug, list) -> StateMatches(slug, list.first().key.state, list.size) }
            .sortedWith(compareByDescending<StateMatches> { it.count }.thenBy { it.name })
        val scoped = if (stateSlug == null) matches else bySlug[stateSlug].orEmpty()
        return SearchResult(
            rows = scoped.sortedWith(ORDER).take(limit),
            total = scoped.size,
            allIndia = matches.size,
            perState = perState,
        )
    }
}
