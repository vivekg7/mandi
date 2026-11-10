package com.crylo.mandi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MandiJsonTest {
    @Test
    fun parsesIndex() {
        val index = MandiJson.parseIndex(
            """{"v":1,"generated":"2026-11-10T08:00:00+00:00","source":"x",
               "states":[{"slug":"uttar-pradesh","name":"Uttar Pradesh","markets":120,"rows":3400,"latest":"2026-11-10"}]}""",
        )
        assertEquals("2026-11-10T08:00:00+00:00", index.generated)
        assertEquals(StateInfo("uttar-pradesh", "Uttar Pradesh", 120, 3400, LocalDate.of(2026, 11, 10)), index.states.single())
    }

    @Test
    fun parsesStateRowsWithAndWithoutPrevious() {
        val state = MandiJson.parseState(
            """{"v":1,"state":"Uttar Pradesh","generated":"g","rows":[
                ["Agra","Achnera","Wheat","Dara","FAQ","2026-11-10",2400,2500,2450,"2026-11-09",2400],
                ["Agra","Agra","Onion","Red","FAQ","2026-11-09",1800.5,2000,1900,null,null],
                ["broken"]
            ]}""",
        )
        assertEquals(2, state.rows.size)
        val wheat = state.rows[0]
        assertEquals(SeriesKey("Uttar Pradesh", "Agra", "Achnera", "Wheat", "Dara", "FAQ"), wheat.key)
        assertEquals(LocalDate.of(2026, 11, 10), wheat.arrival)
        assertEquals(2450.0, wheat.modal, 0.0)
        assertEquals(LocalDate.of(2026, 11, 9), wheat.previousDate)
        assertEquals(50.0, wheat.change!!.absolute, 1e-9)
        val onion = state.rows[1]
        assertEquals(1800.5, onion.min, 0.0)
        assertNull(onion.previousDate)
        assertNull(onion.change)
        assertEquals(listOf("Agra"), state.districts)
        assertEquals(listOf("Achnera", "Agra"), state.markets("Agra"))
    }

    @Test
    fun parsesHistoryWithGaps() {
        val history = MandiJson.parseHistory(
            """{"v":1,"state":"Goa","generated":"g","days":["2026-11-08","2026-11-09","2026-11-10"],
               "series":[["North Goa","Mapusa","Onion","Red","FAQ",[2000,null,2100]]]}""",
        )
        val key = SeriesKey("Goa", "North Goa", "Mapusa", "Onion", "Red", "FAQ")
        assertEquals(listOf(2000.0, null, 2100.0), history.valuesFor(key))
        assertEquals(3, history.days.size)
        assertEquals(emptyList<Double?>(), history.valuesFor(key.copy(market = "Panaji")))
    }

    @Test(expected = MandiJson.FormatException::class)
    fun rejectsUnknownVersion() {
        MandiJson.parseIndex("""{"v":2,"generated":"g","states":[]}""")
    }

    @Test(expected = MandiJson.FormatException::class)
    fun rejectsGarbage() {
        MandiJson.parseIndex("<html>404</html>")
    }

    @Test
    fun seriesKeyRoundTrips() {
        val key = SeriesKey("Tamil Nadu", "Salem", "Salem(Uzhavar Sandhai )", "Tomato", "Hybrid", "")
        assertEquals(key, SeriesKey.decode(key.encode()))
        assertNull(SeriesKey.decode("not a key"))
    }
}
