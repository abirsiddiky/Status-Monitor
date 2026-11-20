package com.example.util

import android.app.Activity
import android.os.Build
import android.view.Display
import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

/**
 * Immutable summary of the current display's refresh rate capabilities.
 */
@Immutable
data class RefreshRateInfo(
    val maxRefreshRateHz: Int = 60,
    val currentRefreshRateHz: Int = 60,
    val isHighRefreshRateSupported: Boolean = false
)

/**
 * Pure value representation of an Android [Display.Mode] for testable mode selection.
 */
data class DisplayModeSpec(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float
)

/**
 * Manages high refresh rate (90Hz / 120Hz / 144Hz+) display mode requests safely across
 * Android 7.0 (API 24) through Android 16+ while preserving native display resolution.
 */
object DisplayRefreshRateHelper {

    private const val HIGH_REFRESH_THRESHOLD_HZ = 65f

    /**
     * Pure selection algorithm: picks the highest refresh-rate mode matching the display's
     * current physical resolution so resolution is never altered.
     * Returns null when high refresh rate is disabled or not supported above 65 Hz.
     */
    fun selectBestDisplayMode(
        modes: List<DisplayModeSpec>,
        currentWidth: Int,
        currentHeight: Int,
        preferHighRefreshRate: Boolean
    ): DisplayModeSpec? {
        if (!preferHighRefreshRate || modes.isEmpty()) return null

        val matchingResolution = modes.filter {
            it.physicalWidth == currentWidth && it.physicalHeight == currentHeight
        }
        val candidates = matchingResolution.ifEmpty { modes }
        val best = candidates.maxByOrNull { it.refreshRate } ?: return null
        return if (best.refreshRate >= HIGH_REFRESH_THRESHOLD_HZ) best else null
    }

    /**
     * Queries the Activity's current display to detect maximum and current refresh rates.
     */
    fun getRefreshRateInfo(activity: Activity): RefreshRateInfo {
        return runCatching {
            val display = getActiveDisplay(activity) ?: return RefreshRateInfo()
            val currentMode = display.mode
            val currentHz = currentMode.refreshRate.roundToInt().coerceAtLeast(1)

            val specs = display.supportedModes.map { mode ->
                DisplayModeSpec(
                    modeId = mode.modeId,
                    physicalWidth = mode.physicalWidth,
                    physicalHeight = mode.physicalHeight,
                    refreshRate = mode.refreshRate
                )
            }

            val bestHighMode = selectBestDisplayMode(
                modes = specs,
                currentWidth = currentMode.physicalWidth,
                currentHeight = currentMode.physicalHeight,
                preferHighRefreshRate = true
            )

            val maxHz = bestHighMode?.refreshRate?.roundToInt()?.coerceAtLeast(currentHz) ?: currentHz
            RefreshRateInfo(
                maxRefreshRateHz = maxHz,
                currentRefreshRateHz = currentHz,
                isHighRefreshRateSupported = maxHz > 65
            )
        }.getOrDefault(RefreshRateInfo())
    }

    /**
     * Applies the user's refresh rate preference to the Activity window.
     *
     * When [highRefreshRateEnabled] is true and [reduceAnimations] is false, requests the
     * highest supported refresh rate mode at the current resolution. This ensures OEM ROMs
     * (MIUI/HyperOS, OxygenOS, One UI, etc.) unlock 90/120/144Hz instead of capping to 60Hz.
     *
     * When disabled or when [reduceAnimations] is active, resets to system default (0) so
     * adaptive / LTPO displays can idle down to save battery.
     */
    fun applyRefreshRatePreference(
        activity: Activity,
        highRefreshRateEnabled: Boolean,
        reduceAnimations: Boolean
    ) {
        runCatching {
            val window = activity.window ?: return
            val display = getActiveDisplay(activity) ?: return
            val currentMode = display.mode

            val specs = display.supportedModes.map { mode ->
                DisplayModeSpec(
                    modeId = mode.modeId,
                    physicalWidth = mode.physicalWidth,
                    physicalHeight = mode.physicalHeight,
                    refreshRate = mode.refreshRate
                )
            }

            val shouldUseHighRefresh = highRefreshRateEnabled && !reduceAnimations
            val targetMode = selectBestDisplayMode(
                modes = specs,
                currentWidth = currentMode.physicalWidth,
                currentHeight = currentMode.physicalHeight,
                preferHighRefreshRate = shouldUseHighRefresh
            )

            val params = window.attributes
            val desiredModeId = targetMode?.modeId ?: 0
            val desiredRefreshRate = targetMode?.refreshRate ?: 0f

            if (params.preferredDisplayModeId != desiredModeId ||
                params.preferredRefreshRate != desiredRefreshRate
            ) {
                params.preferredDisplayModeId = desiredModeId
                params.preferredRefreshRate = desiredRefreshRate
                window.attributes = params
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getActiveDisplay(activity: Activity): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display ?: activity.windowManager?.defaultDisplay
        } else {
            activity.windowManager?.defaultDisplay
        }
    }
}
