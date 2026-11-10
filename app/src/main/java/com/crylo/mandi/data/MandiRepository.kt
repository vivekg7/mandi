package com.crylo.mandi.data

import com.crylo.mandi.core.MandiIndex
import com.crylo.mandi.core.MandiJson
import com.crylo.mandi.core.StateData
import com.crylo.mandi.core.StateHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A value plus whether it came from the on-device copy because the network failed. */
data class Loaded<T>(val value: T, val offline: Boolean)

/**
 * Downloads the pipeline's JSON from the repository's `data` branch and keeps the last
 * good copy of every file on disk, so the app still shows prices without a connection.
 *
 * raw.githubusercontent.com rather than GitHub Pages: it serves any branch of a public
 * repository with no extra setup, sends gzip, and its five-minute cache is far shorter
 * than the pipeline's three-hour schedule.
 */
class MandiRepository(
    private val cacheDir: File,
    private val baseUrl: String = BASE_URL,
    private val download: (String) -> String = ::httpGet,
) {
    private val states = mutableMapOf<String, StateData>()
    private val histories = mutableMapOf<String, StateHistory>()

    /** Always tries the network: the index is what tells us whether anything changed. */
    suspend fun index(): Loaded<MandiIndex> = load("index.json", MandiJson::parseIndex)

    /** The cached index, without touching the network. */
    suspend fun cachedIndex(): MandiIndex? = readCache("index.json", MandiJson::parseIndex)

    /**
     * A state's latest prices. Served from memory or disk while their `generated` stamp
     * matches the index; otherwise downloaded again.
     */
    suspend fun state(slug: String, generated: String?): Loaded<StateData> =
        cachedOrLoad(states, "states/$slug.json", slug, generated, { it.generated }, MandiJson::parseState)

    suspend fun history(slug: String, generated: String?): Loaded<StateHistory> =
        cachedOrLoad(histories, "history/$slug.json", slug, generated, { it.generated }, MandiJson::parseHistory)

    private suspend fun <T> cachedOrLoad(
        memory: MutableMap<String, T>,
        path: String,
        slug: String,
        generated: String?,
        stamp: (T) -> String,
        parse: (String) -> T,
    ): Loaded<T> {
        memory[slug]?.let { if (generated == null || stamp(it) == generated) return Loaded(it, false) }
        readCache(path, parse)?.let {
            if (generated == null || stamp(it) == generated) {
                memory[slug] = it
                return Loaded(it, false)
            }
        }
        return load(path, parse).also { memory[slug] = it.value }
    }

    private suspend fun <T> load(path: String, parse: (String) -> T): Loaded<T> = withContext(Dispatchers.IO) {
        val file = File(cacheDir, path)
        try {
            val text = download("$baseUrl/$path")
            val value = parse(text) // Validate before replacing a good cached copy.
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) throw IOException("could not write $file")
            Loaded(value, offline = false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val cached = readCacheBlocking(file, parse) ?: throw e
            Loaded(cached, offline = true)
        }
    }

    private suspend fun <T> readCache(path: String, parse: (String) -> T): T? =
        withContext(Dispatchers.IO) { readCacheBlocking(File(cacheDir, path), parse) }

    private fun <T> readCacheBlocking(file: File, parse: (String) -> T): T? =
        if (!file.exists()) null else runCatching { parse(file.readText()) }.getOrNull()

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/vivekg7/mandi/data"

        fun httpGet(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            // A generic client name; nothing that identifies the user or device.
            connection.setRequestProperty("User-Agent", "Mandi/1.0")
            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for $url")
                // HttpURLConnection asks for gzip and inflates it transparently.
                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
