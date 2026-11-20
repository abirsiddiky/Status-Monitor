package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChartRxColor
import com.example.ui.theme.ChartTxColor
import java.util.Locale

private val DefaultYLabelFormatter: (Float) -> String = { String.format(Locale.US, "%.0f", it) }
private val SharedDashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

/**
 * Pure Compose Canvas chart supporting smooth cubic Bezier lines,
 * translucent fill, dashed horizontal grid lines, and Y-axis labels.
 *
 * Reuses native Skia [Path] instances and pre-caches [Stroke] objects in [drawWithCache]
 * to avoid per-frame and per-sample allocations on 32-bit devices and sustain 90/120/144Hz.
 */
@Composable
fun SmoothLineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = lineColor.copy(alpha = 0.25f),
    minY: Float? = 0f,
    maxY: Float? = null,
    yLabelFormatter: (Float) -> String = DefaultYLabelFormatter,
    title: String? = null,
    currentValueLabel: String? = null
) {
    val downsampled = remember(points) {
        if (points.size > 60) points.subList(points.size - 60, points.size) else points
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp)
    ) {
        if (title != null || currentValueLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (currentValueLabel != null) {
                    Text(
                        text = currentValueLabel,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = lineColor
                    )
                }
            }
        }

        if (downsampled.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Awaiting samples…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        val effectiveMinY = remember(downsampled, minY) {
            minY ?: (downsampled.minOrNull() ?: 0f)
        }
        val effectiveMaxY = remember(downsampled, maxY, effectiveMinY) {
            val dataMax = downsampled.maxOrNull() ?: 100f
            (maxY ?: (dataMax * 1.1f)).coerceAtLeast(effectiveMinY + 1f)
        }
        val yRange = (effectiveMaxY - effectiveMinY).coerceAtLeast(0.001f)

        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        val linePath = remember { Path() }
        val fillPath = remember { Path() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .height(height)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = yLabelFormatter(effectiveMaxY),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = yLabelFormatter((effectiveMaxY + effectiveMinY) / 2f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = yLabelFormatter(effectiveMinY),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Drawing area using Spacer + drawWithCache with zero per-frame allocations
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val gridStrokePx = 1.dp.toPx()
                        val lineStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)

                        buildSmoothPaths(
                            data = downsampled,
                            linePath = linePath,
                            fillPath = fillPath,
                            width = w,
                            height = h,
                            minY = effectiveMinY,
                            yRange = yRange
                        )

                        val fillBrush = Brush.verticalGradient(
                            colors = listOf(fillColor, fillColor.copy(alpha = 0f)),
                            startY = 0f,
                            endY = h
                        )

                        val topStart = Offset(0f, 0f)
                        val topEnd = Offset(w, 0f)
                        val midStart = Offset(0f, h / 2f)
                        val midEnd = Offset(w, h / 2f)
                        val botStart = Offset(0f, h)
                        val botEnd = Offset(w, h)

                        onDrawBehind {
                            // 3 dashed grid lines (top, middle, bottom)
                            drawLine(
                                color = gridColor,
                                start = topStart,
                                end = topEnd,
                                pathEffect = SharedDashEffect,
                                strokeWidth = gridStrokePx
                            )
                            drawLine(
                                color = gridColor,
                                start = midStart,
                                end = midEnd,
                                pathEffect = SharedDashEffect,
                                strokeWidth = gridStrokePx
                            )
                            drawLine(
                                color = gridColor,
                                start = botStart,
                                end = botEnd,
                                pathEffect = SharedDashEffect,
                                strokeWidth = gridStrokePx
                            )

                            // Fill
                            drawPath(path = fillPath, brush = fillBrush)

                            // Line
                            drawPath(
                                path = linePath,
                                color = lineColor,
                                style = lineStroke
                            )
                        }
                    }
            )
        }
    }
}

/**
 * Dual line chart for Network Transfer (TX / RX) with legend.
 * Reuses native Skia [Path] objects and avoids intermediate coordinate list allocations.
 */
@Composable
fun DualSmoothLineChart(
    rxPoints: List<Float>,
    txPoints: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    rxLabel: String = "Download (RX)",
    txLabel: String = "Upload (TX)",
    yLabelFormatter: (Float) -> String
) {
    val downsampledRx = remember(rxPoints) {
        if (rxPoints.size > 60) rxPoints.subList(rxPoints.size - 60, rxPoints.size) else rxPoints
    }
    val downsampledTx = remember(txPoints) {
        if (txPoints.size > 60) txPoints.subList(txPoints.size - 60, txPoints.size) else txPoints
    }

    val maxCount = maxOf(downsampledRx.size, downsampledTx.size)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp)
    ) {
        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ChartRxColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = rxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ChartTxColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = txLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (maxCount < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Awaiting network samples…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        val effectiveMaxY = remember(downsampledRx, downsampledTx) {
            val rxMax = downsampledRx.maxOrNull() ?: 0f
            val txMax = downsampledTx.maxOrNull() ?: 0f
            (maxOf(rxMax, txMax) * 1.15f).coerceAtLeast(10f)
        }
        val yRange = effectiveMaxY.coerceAtLeast(0.001f)

        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        val rxLinePath = remember { Path() }
        val rxFillPath = remember { Path() }
        val txLinePath = remember { Path() }
        val txFillPath = remember { Path() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .height(height)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = yLabelFormatter(effectiveMaxY),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = yLabelFormatter(effectiveMaxY / 2f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = yLabelFormatter(0f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Dual Canvas area with Spacer + drawWithCache and zero per-frame allocations
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val gridStrokePx = 1.dp.toPx()
                        val lineStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)

                        buildSmoothPaths(
                            data = downsampledRx,
                            linePath = rxLinePath,
                            fillPath = rxFillPath,
                            width = w,
                            height = h,
                            minY = 0f,
                            yRange = yRange
                        )
                        buildSmoothPaths(
                            data = downsampledTx,
                            linePath = txLinePath,
                            fillPath = txFillPath,
                            width = w,
                            height = h,
                            minY = 0f,
                            yRange = yRange
                        )

                        val rxBrush = Brush.verticalGradient(
                            colors = listOf(ChartRxColor.copy(alpha = 0.25f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                        val txBrush = Brush.verticalGradient(
                            colors = listOf(ChartTxColor.copy(alpha = 0.25f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )

                        val topStart = Offset(0f, 0f)
                        val topEnd = Offset(w, 0f)
                        val midStart = Offset(0f, h / 2f)
                        val midEnd = Offset(w, h / 2f)
                        val botStart = Offset(0f, h)
                        val botEnd = Offset(w, h)

                        onDrawBehind {
                            // Grid lines
                            drawLine(gridColor, topStart, topEnd, pathEffect = SharedDashEffect, strokeWidth = gridStrokePx)
                            drawLine(gridColor, midStart, midEnd, pathEffect = SharedDashEffect, strokeWidth = gridStrokePx)
                            drawLine(gridColor, botStart, botEnd, pathEffect = SharedDashEffect, strokeWidth = gridStrokePx)

                            // RX (Download)
                            drawPath(rxFillPath, rxBrush)
                            drawPath(rxLinePath, ChartRxColor, style = lineStroke)

                            // TX (Upload)
                            drawPath(txFillPath, txBrush)
                            drawPath(txLinePath, ChartTxColor, style = lineStroke)
                        }
                    }
            )
        }
    }
}

/**
 * Populates [linePath] and [fillPath] in-place using primitive Float arithmetic
 * without allocating any intermediate List<Offset>.
 */
private fun buildSmoothPaths(
    data: List<Float>,
    linePath: Path,
    fillPath: Path,
    width: Float,
    height: Float,
    minY: Float,
    yRange: Float
) {
    linePath.reset()
    fillPath.reset()
    val count = data.size
    if (count < 2) return

    val stepX = width / (count - 1).coerceAtLeast(1)
    var prevX = 0f
    var prevY = height - (((data[0] - minY) / yRange).coerceIn(0f, 1f) * height)

    linePath.moveTo(prevX, prevY)
    fillPath.moveTo(prevX, height)
    fillPath.lineTo(prevX, prevY)

    for (i in 1 until count) {
        val nextX = i * stepX
        val nextY = height - (((data[i] - minY) / yRange).coerceIn(0f, 1f) * height)
        val controlX = (prevX + nextX) * 0.5f

        linePath.cubicTo(controlX, prevY, controlX, nextY, nextX, nextY)
        fillPath.cubicTo(controlX, prevY, controlX, nextY, nextX, nextY)

        prevX = nextX
        prevY = nextY
    }

    fillPath.lineTo(prevX, height)
    fillPath.close()
}
