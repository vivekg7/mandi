package com.crylo.mandi.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crylo.mandi.core.MandiIndex
import com.crylo.mandi.core.PriceRow
import com.crylo.mandi.core.Search
import com.crylo.mandi.core.SearchResult
import com.crylo.mandi.core.SeriesKey
import com.crylo.mandi.core.StateData
import com.crylo.mandi.core.StateHistory
import com.crylo.mandi.data.Favourites
import com.crylo.mandi.data.MandiRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data object States : Screen
    data class Districts(val slug: String) : Screen
    data class Markets(val slug: String, val district: String) : Screen
    data class Market(val slug: String, val district: String, val market: String) : Screen
    data object Search : Screen
    data class Detail(val key: SeriesKey) : Screen
}

class MandiViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MandiRepository(File(app.filesDir, "data"))
    private val favouriteStore = Favourites(app)

    val backStack = mutableStateListOf<Screen>(Screen.Home)

    var index by mutableStateOf<MandiIndex?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    /** True when what is on screen is the saved copy because the last download failed. */
    var offline by mutableStateOf(false)
        private set

    /** True when there is nothing to show at all: no network and nothing cached. */
    var failed by mutableStateOf(false)
        private set

    val states = mutableStateMapOf<String, StateData>()
    val histories = mutableStateMapOf<String, StateHistory>()
    val loadingStates = mutableStateListOf<String>()
    val favourites = mutableStateListOf<SeriesKey>().apply { addAll(favouriteStore.load()) }

    var query by mutableStateOf("")
        private set
    var searchLoading by mutableStateOf(false)
        private set

    /** The state search is narrowed to, by slug; `null` is all India. */
    var searchState by mutableStateOf<String?>(null)
        private set

    /** Once the user picks a chip, stop choosing a state for them. */
    private var searchStatePicked = false

    var searchLimit by mutableIntStateOf(SEARCH_PAGE)
        private set

    init {
        viewModelScope.launch {
            // Show the saved copy straight away, then refresh over it.
            repository.cachedIndex()?.let { index = it }
            refresh()
        }
    }

    fun navigate(screen: Screen) {
        backStack.add(screen)
        when (screen) {
            is Screen.Districts -> ensureState(screen.slug)
            is Screen.Markets -> ensureState(screen.slug)
            is Screen.Market -> ensureState(screen.slug)
            is Screen.Detail -> {
                ensureState(screen.key.stateSlug)
                ensureHistory(screen.key.stateSlug)
            }
            Screen.Search -> {
                if (!searchStatePicked) searchState = homeState()
                if (query.isNotBlank()) loadAllStates()
            }
            else -> Unit
        }
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                val loaded = repository.index()
                index = loaded.value
                offline = loaded.offline
                failed = false
                // States already opened plus every state that holds a favourite.
                val slugs = (states.keys + histories.keys + favourites.map { it.stateSlug }).toSet()
                slugs.map { slug ->
                    async {
                        loadState(slug)
                        if (slug in histories || favourites.any { it.stateSlug == slug }) loadHistory(slug)
                    }
                }.awaitAll()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                offline = true
                failed = index == null
            } finally {
                refreshing = false
            }
        }
    }

    private fun generatedFor(slug: String): String? = index?.generated?.takeIf { index?.states?.any { it.slug == slug } == true }

    private suspend fun loadState(slug: String) {
        if (slug in loadingStates) return
        loadingStates.add(slug)
        try {
            val loaded = repository.state(slug, generatedFor(slug))
            states[slug] = loaded.value
            if (loaded.offline) offline = true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            offline = true
        } finally {
            loadingStates.remove(slug)
        }
    }

    private suspend fun loadHistory(slug: String) {
        try {
            histories[slug] = repository.history(slug, generatedFor(slug)).value
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // A missing trend line is not worth an error on screen.
        }
    }

    private fun ensureState(slug: String) {
        if (states[slug]?.generated != generatedFor(slug) || slug !in states) {
            viewModelScope.launch { loadState(slug) }
        }
    }

    private fun ensureHistory(slug: String) {
        if (histories[slug]?.generated != generatedFor(slug) || slug !in histories) {
            viewModelScope.launch { loadHistory(slug) }
        }
    }

    fun isFavourite(key: SeriesKey) = key in favourites

    fun toggleFavourite(key: SeriesKey) {
        if (!favourites.remove(key)) {
            favourites.add(key)
            favourites.sortWith(compareBy({ it.commodity }, { it.market }))
            ensureHistory(key.stateSlug)
        }
        favouriteStore.save(favourites)
    }

    fun rowFor(key: SeriesKey): PriceRow? = states[key.stateSlug]?.find(key)

    fun historyFor(key: SeriesKey): List<Double?> = histories[key.stateSlug]?.valuesFor(key).orEmpty()

    fun historyDays(key: SeriesKey) = histories[key.stateSlug]?.days.orEmpty()

    fun updateQuery(text: String) {
        if (text.trim() != query.trim()) searchLimit = SEARCH_PAGE
        query = text
        if (text.isNotBlank()) loadAllStates()
    }

    /** Commodity search is all-India, so it needs every state file (small, and cached). */
    private fun loadAllStates() {
        val all = index?.states?.map { it.slug } ?: return
        val missing = all.filter { states[it]?.generated != generatedFor(it) }
        if (missing.isEmpty() || searchLoading) return
        viewModelScope.launch {
            searchLoading = true
            try {
                missing.map { async { loadState(it) } }.awaitAll()
            } finally {
                searchLoading = false
            }
        }
    }

    fun searchResults(): SearchResult =
        Search.run(states.values.asSequence().flatMap { it.rows.asSequence() }, query, searchState, searchLimit)

    fun pickSearchState(slug: String?) {
        searchStatePicked = true
        searchState = slug
        searchLimit = SEARCH_PAGE
    }

    fun showMoreResults() {
        searchLimit += SEARCH_PAGE
    }

    /**
     * The state most of the favourites are in, taken as where the user is. Most people
     * follow their own area's markets, and without a filter they would scroll past
     * hundreds of Tamil Nadu rows to reach them.
     */
    fun homeState(): String? = favourites.groupingBy { it.stateSlug }.eachCount().maxByOrNull { it.value }?.key

    /** Display name for a state slug, for a chosen state that has no matches. */
    fun stateName(slug: String): String =
        index?.states?.firstOrNull { it.slug == slug }?.name ?: states[slug]?.name ?: slug

    private companion object {
        const val SEARCH_PAGE = 300
    }
}
