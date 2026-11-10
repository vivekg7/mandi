package com.crylo.mandi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PricesTest {
    @Test
    fun perKgIsOneHundredthOfPerQuintal() {
        assertEquals(24.5, Money.perKg(2450.0), 1e-9)
        assertEquals("₹24.50", Money.kg(2450.0))
        assertEquals("₹0.75", Money.kg(75.0))
        assertEquals("₹1,234.57", Money.kg(123456.6))
    }

    @Test
    fun quintalUsesIndianGrouping() {
        assertEquals("₹950", Money.quintal(950.0))
        assertEquals("₹2,450", Money.quintal(2450.4))
        assertEquals("₹1,23,456", Money.quintal(123456.0))
        assertEquals("₹12,34,567", Money.quintal(1234567.0))
        assertEquals("-1,000", Money.group(-1000))
    }

    @Test
    fun changeAgainstPreviousDay() {
        val up = PriceChange.of(2450.0, 2400.0)!!
        assertEquals(50.0, up.absolute, 1e-9)
        assertEquals(2.0833, up.percent, 1e-3)
        assertEquals(1, up.direction)
        assertEquals("+₹50", Money.signedQuintal(up.absolute))
        assertEquals("+2.1%", Money.signedPercent(up.percent))

        val down = PriceChange.of(1800.0, 2000.0)!!
        assertEquals(-1, down.direction)
        assertEquals("−₹200", Money.signedQuintal(down.absolute))
        assertEquals("−10.0%", Money.signedPercent(down.percent))

        val flat = PriceChange.of(2000.0, 2000.0)!!
        assertEquals(0, flat.direction)
        assertEquals("₹0", Money.signedQuintal(flat.absolute))
        assertEquals("0.0%", Money.signedPercent(flat.percent))
    }

    @Test
    fun noChangeWithoutAUsablePreviousPrice() {
        assertNull(PriceChange.of(2000.0, null))
        assertNull(PriceChange.of(2000.0, 0.0))
    }

    @Test
    fun freshness() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(0, Freshness.daysOld(today, today))
        assertEquals(3, Freshness.daysOld(LocalDate.of(2026, 11, 7), today))
        assertEquals(0, Freshness.daysOld(LocalDate.of(2026, 11, 11), today))
    }

    @Test
    fun slugMatchesThePipeline() {
        assertEquals("andaman-and-nicobar", Slug.of("Andaman and Nicobar"))
        assertEquals("nct-of-delhi", Slug.of("NCT of Delhi"))
        assertEquals("jammu-and-kashmir", Slug.of(" Jammu and Kashmir "))
    }

    @Test
    fun hindiNames() {
        assertEquals("भिंडी", CommodityNames.hindi("Bhindi(Ladies Finger)"))
        assertEquals("अरहर", CommodityNames.hindi("Arhar (Tur/Red Gram)(Whole)"))
        assertEquals("धान", CommodityNames.hindi("Paddy(Dhan)(Common)"))
        assertNull(CommodityNames.hindi("Dragon fruit"))
        assertTrue(CommodityNames.matches("Onion", "oni"))
        assertTrue(CommodityNames.matches("Onion", "प्याज"))
        assertFalse(CommodityNames.matches("Onion", " "))
    }
}
