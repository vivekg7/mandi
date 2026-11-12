package com.crylo.mandi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchTest {
    private val day = LocalDate.of(2026, 11, 12)

    private fun row(state: String, market: String, commodity: String = "Onion") = PriceRow(
        key = SeriesKey(state, "District", market, commodity, "Other", "FAQ"),
        arrival = day,
        min = 1000.0,
        max = 2000.0,
        modal = 1500.0,
        previousDate = null,
        previousModal = null,
    )

    private val rows = (1..5).map { row("Andhra Pradesh", "Market $it") } +
        (1..2).map { row("West Bengal", "Market $it") } +
        row("Haryana", "Hansi", "Tomato")

    @Test
    fun queriesShorterThanTwoLettersFindNothing() {
        assertEquals(SearchResult.EMPTY, Search.run(rows.asSequence(), " o ", null, 300))
    }

    @Test
    fun theLimitCutsRowsButTheTotalSaysHowManyMatched() {
        val result = Search.run(rows.asSequence(), "onion", null, 3)
        assertEquals(3, result.rows.size)
        assertEquals(7, result.total)
        assertEquals(7, result.allIndia)
        // Sorted by state name, so West Bengal is what the limit cut.
        assertTrue(result.rows.all { it.key.state == "Andhra Pradesh" })
    }

    @Test
    fun theStateFilterRunsBeforeTheLimit() {
        val result = Search.run(rows.asSequence(), "onion", "west-bengal", 3)
        assertEquals(listOf("West Bengal", "West Bengal"), result.rows.map { it.key.state })
        assertEquals(2, result.total)
        assertEquals(7, result.allIndia)
    }

    @Test
    fun perStateCountsCoverAllIndiaMostFirst() {
        val result = Search.run(rows.asSequence(), "onion", "west-bengal", 3)
        assertEquals(
            listOf(StateMatches("andhra-pradesh", "Andhra Pradesh", 5), StateMatches("west-bengal", "West Bengal", 2)),
            result.perState,
        )
    }

    @Test
    fun aStateWithoutMatchesIsEmptyNotAllIndia() {
        val result = Search.run(rows.asSequence(), "onion", "haryana", 300)
        assertTrue(result.rows.isEmpty())
        assertEquals(0, result.total)
        assertEquals(7, result.allIndia)
    }

    @Test
    fun hindiNamesMatchToo() {
        assertEquals(7, Search.run(rows.asSequence(), "प्याज", null, 300).total)
    }
}
