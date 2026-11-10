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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.crylo.mandi.R
import com.crylo.mandi.core.Money
import com.crylo.mandi.core.SeriesKey

@Composable
fun MandiApp(vm: MandiViewModel) {
    when (val screen = vm.backStack.last()) {
        Screen.Home -> HomeScreen(vm)
        Screen.States -> StatesScreen(vm)
        is Screen.Districts -> DistrictsScreen(vm, screen.slug)
        is Screen.Markets -> MarketsScreen(vm, screen.slug, screen.district)
        is Screen.Market -> MarketScreen(vm, screen)
        Screen.Search -> SearchScreen(vm)
        is Screen.Detail -> DetailScreen(vm, screen.key)
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
            isRefreshing = vm.refreshing,
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
                    if (vm.favourites.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.unit_note),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
        item {
            Text(
                stringResource(R.string.unit_note),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchScreen(vm: MandiViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
            val results = remember(vm.query, vm.states.size, vm.index) { vm.searchResults() }
            LazyColumn(Modifier.fillMaxSize()) {
                if (vm.query.trim().length >= 2 && results.isEmpty() && !vm.searchLoading) {
                    item { Message(stringResource(R.string.no_results), null) {} }
                }
                items(results, key = { it.key.encode() }) { row ->
                    PriceRowItem(row, showPlace = true) { vm.navigate(Screen.Detail(row.key)) }
                    HorizontalDivider()
                }
            }
        }
    }
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
            item {
                Text(
                    stringResource(R.string.unit_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
