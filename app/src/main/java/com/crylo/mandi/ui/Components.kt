package com.crylo.mandi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crylo.mandi.R
import com.crylo.mandi.core.CommodityNames
import com.crylo.mandi.core.Freshness
import com.crylo.mandi.core.Money
import com.crylo.mandi.core.PriceChange
import com.crylo.mandi.core.PriceRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val stampFormat get() = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())

fun formatDay(date: LocalDate): String = date.format(dayFormat)

fun formatStamp(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(stampFormat)
}.getOrDefault(iso)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandiTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                }
            }
        },
        actions = { actions() },
    )
}

/** "Wheat · गेहूं" when a Hindi name is known. */
fun commodityLabel(commodity: String): String =
    CommodityNames.hindi(commodity)?.let { "$commodity · $it" } ?: commodity

/**
 * Markets report late and some not every day, so the arrival date is always shown,
 * and highlighted once it is two or more days old.
 */
@Composable
fun ArrivalText(date: LocalDate, modifier: Modifier = Modifier) {
    val days = Freshness.daysOld(date, LocalDate.now())
    val text = when (days) {
        0L -> stringResource(R.string.arrival_today)
        1L -> stringResource(R.string.arrival_yesterday)
        else -> pluralStringResource(R.plurals.arrival_days_old, days.toInt(), formatDay(date), days.toInt())
    }
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = if (days >= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun changeColor(change: PriceChange): Color = when (change.direction) {
    1 -> if (MaterialTheme.colorScheme.background.luminanceIsDark()) Color(0xFF81C784) else Color(0xFF2E7D32)
    -1 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun Color.luminanceIsDark() = (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5

@Composable
fun ChangeText(change: PriceChange?, modifier: Modifier = Modifier) {
    if (change == null) {
        Text(
            stringResource(R.string.change_unknown),
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val arrow = when (change.direction) {
        1 -> R.drawable.ic_arrow_up
        -1 -> R.drawable.ic_arrow_down
        else -> null
    }
    val description = when (change.direction) {
        1 -> stringResource(R.string.change_up)
        -1 -> stringResource(R.string.change_down)
        else -> stringResource(R.string.change_none)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (arrow != null) Icon(painterResource(arrow), description, tint = changeColor(change))
        Text(
            "${Money.signedQuintal(change.absolute)} (${Money.signedPercent(change.percent)})",
            style = MaterialTheme.typography.labelMedium,
            color = changeColor(change),
        )
    }
}

/** A price line used by the market and search lists. */
@Composable
fun PriceRowItem(row: PriceRow, showPlace: Boolean, onClick: () -> Unit) {
    val key = row.key
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(commodityLabel(key.commodity), fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column {
                val variety = listOf(key.variety, key.grade).filter { it.isNotBlank() }.joinToString(" · ")
                if (variety.isNotEmpty()) Text(variety, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showPlace) {
                    Text(
                        stringResource(R.string.place_line, key.market, key.district, key.state),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ArrivalText(row.arrival)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.per_quintal, Money.quintal(row.modal)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.per_kg, Money.kg(row.modal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChangeText(row.change)
            }
        },
    )
}

@Composable
fun SimpleRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
    )
}

@Composable
fun HSpace(width: Int) = Spacer(Modifier.width(width.dp))
