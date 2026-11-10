package com.crylo.mandi.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/** Change of the modal price against the previous reported day. */
data class PriceChange(val absolute: Double, val percent: Double) {
    val direction: Int get() = if (absolute > 0.0) 1 else if (absolute < 0.0) -1 else 0

    companion object {
        fun of(current: Double, previous: Double?): PriceChange? {
            if (previous == null || previous <= 0.0) return null
            val diff = current - previous
            return PriceChange(diff, diff / previous * 100.0)
        }
    }
}

/**
 * Rupee formatting with Indian digit grouping (1,23,456), which is how farmers and
 * traders read prices. Mandi prices are quoted per quintal (100 kg).
 */
object Money {
    const val KG_PER_QUINTAL = 100

    fun perKg(perQuintal: Double): Double = perQuintal / KG_PER_QUINTAL

    /** Whole rupees per quintal: "₹2,450". */
    fun quintal(perQuintal: Double): String = "₹" + group(perQuintal.roundToLong())

    /** Rupees and paise per kg: "₹24.50". One paisa per kg is one rupee per quintal. */
    fun kg(perQuintal: Double): String {
        val paise = perQuintal.roundToLong()
        return "₹" + group(paise / 100) + "." + (paise % 100).toString().padStart(2, '0')
    }

    /** Signed change in whole rupees: "+₹50", "−₹120", "₹0". */
    fun signedQuintal(diff: Double): String {
        val rounded = diff.roundToLong()
        val sign = if (rounded > 0) "+" else if (rounded < 0) "−" else ""
        return sign + "₹" + group(abs(rounded))
    }

    /** "+2.1%", "−0.4%", "0.0%". */
    fun signedPercent(percent: Double): String {
        val tenths = (percent * 10).roundToLong()
        val sign = if (tenths > 0) "+" else if (tenths < 0) "−" else ""
        val a = abs(tenths)
        return "$sign${a / 10}.${a % 10}%"
    }

    fun group(value: Long): String {
        val negative = value < 0
        val digits = abs(value).toString()
        if (digits.length <= 3) return (if (negative) "-" else "") + digits
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        val groups = head.reversed().chunked(2).joinToString(",").reversed()
        return (if (negative) "-" else "") + groups + "," + tail
    }
}

object Freshness {
    /** Whole days between the arrival date and today; 0 for today, never negative. */
    fun daysOld(arrival: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(arrival, today).coerceAtLeast(0)
}

/** Must match `slugify` in pipeline/fetch_mandi.py: it names the published files. */
object Slug {
    private val nonAlnum = Regex("[^a-z0-9]+")
    fun of(name: String): String = name.lowercase().replace(nonAlnum, "-").trim('-')
}
