package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusWarningAmber

private val GaugeAnimationSpec = tween<Float>(
    durationMillis = 320,
    easing = FastOutSlowInEasing
)

/**
 * High-performance Material 3 Arc Gauge.
 *
 * Defers animated fraction reads into the draw phase (`onDrawBehind`) so
 * 90Hz/120Hz/144Hz gauge sweeps run smoothly with ZERO recompositions per frame,
 * and skips animation machinery entirely when [reduceAnimations] is enabled for
 * 32-bit and low-RAM devices.
 */
@Composable
fun ArcGauge(
    value: Float, // 0..100
    maxValue: Float = 100f,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    size: Dp = 130.dp,
    strokeWidth: Dp = 10.dp,
    customColor: Color? = null,
    reduceAnimations: Boolean = false
) {
    val targetFraction = (value / maxValue).coerceIn(0f, 1f)
    val fractionState = if (reduceAnimations) {
        rememberUpdatedState(targetFraction)
    } else {
        animateFloatAsState(
            targetValue = targetFraction,
            animationSpec = GaugeAnimationSpec,
            label = "gauge_arc"
        )
    }

    val defaultColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val resolvedColor = customColor ?: when {
        targetFraction >= 0.90f -> StatusOfflineRed
        targetFraction >= 0.75f -> StatusWarningAmber
        else -> defaultColor
    }

    Box(
        modifier = modifier
            .size(size)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .padding(strokeWidth / 2)
                .drawWithCache {
                    val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                    val diameter = minOf(this.size.width, this.size.height)
                    val arcSize = Size(diameter, diameter)
                    val topLeft = Offset(
                        (this.size.width - diameter) / 2f,
                        (this.size.height - diameter) / 2f
                    )

                    onDrawBehind {
                        // Background track: 270 degree arc starting from 135 deg
                        drawArc(
                            color = trackColor,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )

                        // Progress arc — state read happens ONLY in draw phase
                        val sweep = 270f * fractionState.value
                        if (sweep > 0f) {
                            drawArc(
                                color = resolvedColor,
                                startAngle = 135f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = stroke
                            )
                        }
                    }
                }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
