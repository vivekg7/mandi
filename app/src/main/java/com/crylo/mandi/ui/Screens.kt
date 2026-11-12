package com.crylo.mandi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.crylo.mandi.R
import com.crylo.mandi.core.Money
import com.crylo.mandi.core.Search
import com.crylo.mandi.core.SearchResult
import com.crylo.mandi.core.StateMatches
import com.crylo.mandi.core.SeriesKey

@Composable
fun MandiApp(vm: MandiViewModel) {
    // Only the top screen is composed, so without this every list would be rebuilt at
    // the top on Back. Each back-stack entry keeps its own saved state (scroll position
    // and the like) under a key that includes its depth, so the same screen reached
    // twice by different routes does not share one, and a popped entry's state is
    // dropped rather than resurfacing the next time that screen is opened.
    val holder = rememberSaveableStateHolder()
    val keys = vm.backStack.mapIndexed { depth, screen -> "$depth/$screen" }
    val saved = remember { mutableSetOf<String>() }
    LaunchedEffect(keys) {
        (saved - keys.toSet()).forEach(holder::removeState)
        saved.retainAll(keys.toSet())
        saved += keys.last()
    }
    val screen = vm.backStack.last()
    holder.SaveableStateProvider(keys.last()) {
        when (screen) {
            Screen.Home -> HomeScreen(vm)
            Screen.States -> StatesScreen(vm)
            is Screen.Districts -> DistrictsScreen(vm, screen.slug)
            is Screen.Markets -> MarketsScreen(vm, screen.slug, screen.district)
            is Screen.Market -> MarketScreen(vm, screen)
            Screen.Search -> SearchScreen(vm)
            is Screen.Detail -> DetailScreen(vm, screen.key)
        }
    }
}

@Composable
private fun StatusLine(vm: MandiViewModel) {
    val index = vm.index ?: return
    val text = if (vm.offline) {
        stringResource(R.string.offline_status, formatStamp(index.generated))
    } else {
        stringResource(R.string.updated_status, formatStamp(index.generated))
    }
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (vm.offline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: MandiViewModel) {
    Scaffold(
        topBar = {
            MandiTopBar(stringResource(R.string.app_name), onBack = null) {
                IconButton(onClick = { vm.navigate(Screen.Search) }) {
                    Icon(painterResource(R.drawable.ic_search), stringResource(R.string.search))
                }
                IconButton(onClick = vm::refresh) {
                    Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.navigate(Screen.States) },
                icon = { Icon(painterResource(R.drawable.ic_list), null) },
                text = { Text(stringResource(R.string.browse_markets)) },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            // Before the first index arrives the centred spinner below already says
            // "loading"; a second, pull-to-refresh spinner on top of it is noise.
            isRefreshing = vm.refreshing && vm.index != null,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                vm.failed -> Message(stringResource(R.string.load_failed), stringResource(R.string.retry), vm::refresh)
                vm.index == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item { StatusLine(vm) }
                    if (vm.favourites.isEmpty()) {
                        item {
                            Message(stringResource(R.string.no_favourites), stringResource(R.string.browse_markets)) {
                                vm.navigate(Screen.States)
                            }
                        }
                    }
                    items(vm.favourites.toList(), key = { it.encode() }) { key ->
                        FavouriteCard(vm, key)
                    }
                    item { SourceNote(Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FavouriteCard(vm: MandiViewModel, key: SeriesKey) {
    val row = vm.rowFor(key)
    Card(
        onClick = { vm.navigate(Screen.Detail(key)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    commodityLabel(key.commodity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.market_line, key.market, key.district),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val variety = listOf(key.variety, key.grade).filter { it.isNotBlank() }.joinToString(" · ")
                if (variety.isNotEmpty()) {
                    Text(
                        variety,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row != null) ArrivalText(row.arrival, Modifier.padding(top = 4.dp))
            }
            if (row == null) {
                val loading = key.stateSlug in vm.loadingStates || vm.refreshing
                Text(
                    stringResource(if (loading) R.string.loading else R.string.no_recent_report),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(120.dp),
                )
            } else {
                Sparkline(
                    vm.historyFor(key),
                    modifier = Modifier.size(width = 64.dp, height = 32.dp),
                    description = stringResource(R.string.trend_description),
                )
                HSpace(12)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.per_quintal, Money.quintal(row.modal)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.per_kg, Money.kg(row.modal)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChangeText(row.change)
                }
            }
        }
    }
}

private const val SOURCE_URL =
    "https://www.data.gov.in/resource/current-daily-price-various-commodities-various-markets-mandi"

/**
 * Units, the data source and the disclaimer, under every list of prices. Google Play
 * requires an app that presents government information to say in the app itself that it
 * is not a government app, and to link the source, so the home screen shows this even
 * before anything is starred.
 */
@Composable
private fun SourceNote(modifier: Modifier = Modifier) {
    val linkStyle = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val text = buildAnnotatedString {
        append(stringResource(R.string.unit_note))
        append("\n\n")
        append(stringResource(R.string.not_affiliated))
        append(" ")
        withLink(LinkAnnotation.Url(SOURCE_URL, linkStyle)) { append(stringResource(R.string.source_link)) }
    }
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Message(text: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun ListScaffold(
    vm: MandiViewModel,
    title: String,
    loading: Boolean,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Scaffold(topBar = {
        MandiTopBar(title, onBack = { vm.back() }) {
            IconButton(onClick = { vm.navigate(Screen.Search) }) {
                Icon(painterResource(R.drawable.ic_search), stringResource(R.string.search))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize(), content = content)
        }
    }
}

@Composable
private fun StatesScreen(vm: MandiViewModel) {
    val states = vm.index?.states.orEmpty()
    ListScaffold(vm, stringResource(R.string.choose_state), loading = vm.index == null && vm.refreshing) {
        item { StatusLine(vm) }
        items(states, key = { it.slug }) { state ->
            SimpleRow(
                state.name,
                pluralStringResource(R.plurals.market_count, state.markets, state.markets) +
                    (state.latest?.let { " · " + formatDay(it) } ?: ""),
            ) { vm.navigate(Screen.Districts(state.slug)) }
        }
    }
}

@Composable
private fun DistrictsScreen(vm: MandiViewModel, slug: String) {
    val data = vm.states[slug]
    val name = data?.name ?: vm.index?.states?.firstOrNull { it.slug == slug }?.name.orEmpty()
    ListScaffold(vm, name, loading = slug in vm.loadingStates) {
        if (data == null && slug !in vm.loadingStates) item { Message(stringResource(R.string.load_failed), null) {} }
        items(data?.districts.orEmpty(), key = { it }) { district ->
            val markets = data!!.markets(district).size
            SimpleRow(district, pluralStringResource(R.plurals.market_count, markets, markets)) {
                vm.navigate(Screen.Markets(slug, district))
            }
        }
    }
}

@Composable
private fun MarketsScreen(vm: MandiViewModel, slug: String, district: String) {
    val data = vm.states[slug]
    ListScaffold(vm, district, loading = slug in vm.loadingStates) {
        items(data?.markets(district).orEmpty(), key = { it }) { market ->
            val rows = data!!.rowsAt(district, market)
            SimpleRow(
                market,
                pluralStringResource(R.plurals.commodity_count, rows.size, rows.size) +
                    " · " + formatDay(rows.maxOf { it.arrival }),
            ) { vm.navigate(Screen.Market(slug, district, market)) }
        }
    }
}

@Composable
private fun MarketScreen(vm: MandiViewModel, screen: Screen.Market) {
    val data = vm.states[screen.slug]
    ListScaffold(vm, screen.market, loading = screen.slug in vm.loadingStates) {
        val rows = data?.rowsAt(screen.district, screen.market).orEmpty()
        items(rows, key = { it.key.encode() }) { row ->
            PriceRowItem(row, showPlace = false) { vm.navigate(Screen.Detail(row.key)) }
            HorizontalDivider()
        }
        item { SourceNote(Modifier.padding(16.dp)) }
    }
}

@Composable
private fun SearchScreen(vm: MandiViewModel) {
    val focus = remember { FocusRequester() }
    // Open the keyboard on the way in, not again on coming back from a result.
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focused) {
            focused = true
            focus.requestFocus()
        }
    }
    Scaffold(topBar = { MandiTopBar(stringResource(R.string.search_title), onBack = { vm.back() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::updateQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.updateQuery("") }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.height(8.dp))
            if (vm.searchLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val listState = rememberLazyListState()
            val atTop by remember {
                derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            }
            val results = remember(vm.query, vm.states.size, vm.index, vm.searchState, vm.searchLimit) {
                vm.searchResults()
            }
            if (vm.query.trim().length >= Search.MIN_QUERY) StateChips(vm, results)
            // States load one by one after the first keystrokes, and results are sorted by
            // state, so new rows usually land above the first visible one. A keyed list
            // holds that row in place, which opened the results mid-list with no hint that
            // anything sat above. Stay pinned to the top unless the user has scrolled away.
            val pinnedFor = remember { PinnedResults() }
            SideEffect {
                // After composition, before this frame's layout applies the new rows.
                if (pinnedFor.results !== results) {
                    pinnedFor.results = results
                    if (atTop) listState.requestScrollToItem(0)
                }
            }
            // A new query or state starts at the top; coming back from a result keeps the place.
            val scope = "${vm.query.trim()}/${vm.searchState}"
            var scrolledFor by rememberSaveable { mutableStateOf(scope) }
            LaunchedEffect(scope) {
                if (scope != scrolledFor) {
                    scrolledFor = scope
                    listState.scrollToItem(0)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                if (vm.query.trim().length >= Search.MIN_QUERY && results.rows.isEmpty() && !vm.searchLoading) {
                    item {
                        val text = vm.searchState?.let { stringResource(R.string.no_results_in_state, vm.stateName(it)) }
                            ?: stringResource(R.string.no_results)
                        Message(text, null) {}
                    }
                }
                items(results.rows, key = { it.key.encode() }) { row ->
                    PriceRowItem(row, showPlace = true) { vm.navigate(Screen.Detail(row.key)) }
                    HorizontalDivider()
                }
                if (results.rows.size < results.total) {
                    item {
                        Message(
                            stringResource(R.string.search_showing, results.rows.size, results.total),
                            stringResource(R.string.search_show_more),
                            vm::showMoreResults,
                        )
                    }
                }
            }
        }
    }
}

/**
 * All India, then the user's home state, then every state with a match, most first,
 * each with its number of matches. A chosen state with no match keeps its chip, so
 * the filter that emptied the list stays visible and can be undone.
 */
@Composable
private fun StateChips(vm: MandiViewModel, results: SearchResult) {
    val counts = results.perState.associateBy { it.slug }
    val pinned = listOfNotNull(vm.homeState(), vm.searchState).distinct()
        .map { counts[it] ?: StateMatches(it, vm.stateName(it), 0) }
    val chips = pinned + results.perState.filter { it.slug !in pinned.map(StateMatches::slug) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "") {
            FilterChip(
                selected = vm.searchState == null,
                onClick = { vm.pickSearchState(null) },
                label = { Text(stringResource(R.string.search_chip, stringResource(R.string.all_india), results.allIndia)) },
            )
        }
        items(chips, key = { it.slug }) { chip ->
            FilterChip(
                selected = vm.searchState == chip.slug,
                onClick = { vm.pickSearchState(chip.slug) },
                label = { Text(stringResource(R.string.search_chip, chip.name, chip.count)) },
            )
        }
    }
}

/** The result list the search screen last kept at the top; not state, just a marker. */
private class PinnedResults {
    var results: SearchResult? = null
}

@Composable
private fun DetailScreen(vm: MandiViewModel, key: SeriesKey) {
    val row = vm.rowFor(key)
    val favourite = vm.isFavourite(key)
    Scaffold(topBar = {
        MandiTopBar(key.commodity, onBack = { vm.back() }) {
            IconButton(onClick = { vm.toggleFavourite(key) }) {
                Icon(
                    painterResource(if (favourite) R.drawable.ic_star else R.drawable.ic_star_outline),
                    stringResource(if (favourite) R.string.remove_favourite else R.string.add_favourite),
                    tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(commodityLabel(key.commodity), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.place_line, key.market, key.district, key.state),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (row == null) {
                item {
                    Text(
                        stringResource(
                            if (key.stateSlug in vm.loadingStates) R.string.loading else R.string.no_recent_report,
                        ),
                    )
                }
                return@LazyColumn
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.modal_price), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(R.string.per_quintal, Money.quintal(row.modal)),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(stringResource(R.string.per_kg, Money.kg(row.modal)), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.range_line, Money.quintal(row.min), Money.quintal(row.max)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.arrival_date_line, formatDay(row.arrival)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        ArrivalText(row.arrival)
                        Spacer(Modifier.height(4.dp))
                        ChangeText(row.change)
                        row.previousDate?.let {
                            Text(
                                stringResource(R.string.previous_line, formatDay(it), Money.quintal(row.previousModal ?: 0.0)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { TrendCard(vm, key) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Fact(stringResource(R.string.variety), key.variety)
                        Fact(stringResource(R.string.grade), key.grade)
                        Fact(stringResource(R.string.market), key.market)
                        Fact(stringResource(R.string.district), key.district)
                        Fact(stringResource(R.string.state), key.state)
                    }
                }
            }
            item { SourceNote() }
        }
    }
}

@Composable
private fun TrendCard(vm: MandiViewModel, key: SeriesKey) {
    val values = vm.historyFor(key)
    val days = vm.historyDays(key)
    val reported = values.withIndex().filter { it.value != null }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.trend_title), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            if (reported.size < 2) {
                Text(
                    stringResource(R.string.trend_not_enough),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val low = reported.minOf { it.value!! }
            val high = reported.maxOf { it.value!! }
            Text(
                stringResource(R.string.trend_range, Money.quintal(low), Money.quintal(high)),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Sparkline(
                values,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                description = stringResource(R.string.trend_description),
                strokeWidthDp = 2.5f,
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    days.getOrNull(reported.first().index)?.let(::formatDay).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    days.getOrNull(reported.last().index)?.let(::formatDay).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                pluralStringResource(R.plurals.reported_days, reported.size, reported.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text(label, Modifier.width(96.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
