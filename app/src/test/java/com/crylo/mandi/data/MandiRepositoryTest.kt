package com.crylo.mandi.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class MandiRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val index = """{"v":1,"generated":"g1","states":[{"slug":"goa","name":"Goa","markets":1,"rows":1,"latest":"2026-11-10"}]}"""
    private val goa = """{"v":1,"state":"Goa","generated":"g1","rows":[["North Goa","Mapusa","Onion","Red","FAQ","2026-11-10",2000,2200,2100,null,null]]}"""

    @Test
    fun fallsBackToTheSavedCopyWhenOffline() = runBlocking {
        var online = true
        val files = mapOf("base/index.json" to index, "base/states/goa.json" to goa)
        val download = { url: String -> if (online) files.getValue(url) else throw IOException("offline") }

        val first = MandiRepository(folder.root, "base", download)
        assertFalse(first.index().offline)
        assertEquals(1, first.state("goa", "g1").value.rows.size)

        online = false
        val second = MandiRepository(folder.root, "base", download)
        val loaded = second.index()
        assertTrue(loaded.offline)
        assertEquals("g1", loaded.value.generated)
        // Same generated stamp: served from disk without a download.
        assertEquals("Goa", second.state("goa", "g1").value.name)
    }

    @Test
    fun aBadDownloadDoesNotReplaceAGoodCopy() = runBlocking {
        var body = index
        val repository = MandiRepository(folder.root, "base") { body }
        repository.index()
        body = "<html>rate limited</html>"
        val loaded = MandiRepository(folder.root, "base") { body }.index()
        assertTrue(loaded.offline)
        assertEquals("g1", loaded.value.generated)
    }

    @Test(expected = IOException::class)
    fun failsWhenOfflineWithNothingSaved(): Unit = runBlocking {
        MandiRepository(folder.root, "base") { throw IOException("offline") }.index()
    }
}
