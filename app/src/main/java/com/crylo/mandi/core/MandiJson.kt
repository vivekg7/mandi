package com.crylo.mandi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

/**
 * Parses the files the pipeline publishes (format described in docs/data-format.md).
 * Rows are positional arrays to keep downloads small, so the indices below are the
 * contract with pipeline/fetch_mandi.py.
 */
object MandiJson {
    const val SUPPORTED_VERSION = 1

    class FormatException(message: String) : Exception(message)

    fun parseIndex(text: String): MandiIndex {
        val root = parseRoot(text)
        val states = root.array("states").map { element ->
            val o = element.jsonObject
            StateInfo(
                slug = o.string("slug"),
                name = o.string("name"),
                markets = (o["markets"] as? JsonPrimitive)?.intOrNull ?: 0,
                rows = (o["rows"] as? JsonPrimitive)?.intOrNull ?: 0,
                latest = o.stringOrNull("latest")?.let(::date),
            )
        }
        return MandiIndex(root.string("generated"), states)
    }

    fun parseState(text: String): StateData {
        val root = parseRoot(text)
        val state = root.string("state")
        val rows = root.array("rows").mapNotNull { element ->
            val r = element as? JsonArray ?: return@mapNotNull null
            if (r.size < 11) return@mapNotNull null
            PriceRow(
                key = SeriesKey(state, r.text(0), r.text(1), r.text(2), r.text(3), r.text(4)),
                arrival = date(r.text(5)) ?: return@mapNotNull null,
                min = r.number(6) ?: return@mapNotNull null,
                max = r.number(7) ?: return@mapNotNull null,
                modal = r.number(8) ?: return@mapNotNull null,
                previousDate = date(r.text(9)),
                previousModal = r.number(10),
            )
        }
        return StateData(state, root.string("generated"), rows)
    }

    fun parseHistory(text: String): StateHistory {
        val root = parseRoot(text)
        val state = root.string("state")
        val days = root.array("days").map { date((it as JsonPrimitive).content) ?: throw FormatException("bad day") }
        val series = root.array("series").mapNotNull { element ->
            val s = element as? JsonArray ?: return@mapNotNull null
            if (s.size < 6) return@mapNotNull null
            val key = SeriesKey(state, s.text(0), s.text(1), s.text(2), s.text(3), s.text(4))
            val values = (s[5] as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull } ?: return@mapNotNull null
            key to values
        }.toMap()
        return StateHistory(root.string("generated"), days, series)
    }

    private fun parseRoot(text: String): JsonObject {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            throw FormatException("not a JSON object: ${e.message}")
        }
        val version = (root["v"] as? JsonPrimitive)?.intOrNull
        // A newer pipeline may change the row layout; refuse rather than misread prices.
        if (version != SUPPORTED_VERSION) throw FormatException("unsupported format version $version")
        return root
    }

    private fun date(text: String?): LocalDate? =
        text?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun JsonObject.array(name: String): JsonArray =
        this[name] as? JsonArray ?: throw FormatException("missing $name")

    private fun JsonObject.stringOrNull(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun JsonObject.string(name: String): String =
        stringOrNull(name) ?: throw FormatException("missing $name")

    private fun JsonArray.text(i: Int): String =
        (this[i] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""

    private fun JsonArray.number(i: Int): Double? =
        (this[i] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
}
