package com.crylo.mandi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Modal price over the history window. Days a market did not report are skipped, so
 * the line joins the reported days; the x position still follows the state's day axis,
 * which keeps a long gap visible as a long segment rather than hiding it.
 */
@Composable
fun Sparkline(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    description: String,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidthDp: Float = 2f,
) {
    val points = values.withIndex().filter { it.value != null }.map { it.index to it.value!! }
    if (points.size < 2) return
    Canvas(modifier.semantics { contentDescription = description }) {
        val stroke = strokeWidthDp.dp.toPx()
        val low = points.minOf { it.second }
        val high = points.maxOf { it.second }
        val span = (high - low).takeIf { it > 0 } ?: 1.0
        val lastIndex = (values.size - 1).coerceAtLeast(1)
        fun at(index: Int, value: Double) = Offset(
            x = stroke + (size.width - 2 * stroke) * index / lastIndex,
            // A flat series draws through the middle instead of along the bottom edge.
            y = if (high == low) size.height / 2 else
                stroke + (size.height - 2 * stroke) * (1 - ((value - low) / span).toFloat()),
        )
        val path = Path()
        points.forEachIndexed { i, (index, value) ->
            val p = at(index, value)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val (lastI, lastV) = points.last()
        drawCircle(color, radius = stroke * 1.6f, center = at(lastI, lastV))
    }
}
