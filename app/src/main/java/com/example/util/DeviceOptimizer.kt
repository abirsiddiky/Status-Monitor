package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Runtime device capability detection for optimizing memory usage, thread pools,
 * chart buffers, and animation defaults on 32-bit and low-RAM Android devices.
 */
object DeviceOptimizer {

    /**
     * True when the device only supports 32-bit ABIs (e.g. armeabi-v7a, x86)
     * and has no 64-bit ABI support.
     */
    val is32BitDevice: Boolean by lazy {
        Build.SUPPORTED_64_BIT_ABIS.isEmpty()
    }

    /**
     * True when Android marks this device as low-RAM (e.g. Android Go / <= 1-2 GB RAM).
     */
    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.isLowRamDevice == true
    }

    /**
     * True when running on a 32-bit-only or low-RAM device where smaller buffers
     * and leaner GC pressure measurably improve responsiveness.
     */
    fun isConstrainedDevice(context: Context): Boolean {
        return is32BitDevice || isLowRamDevice(context)
    }

    /**
     * Returns the optimal rolling sample window size for Canvas telemetry charts.
     * Uses 36 samples on 32-bit / low-RAM devices to reduce Float list churn and
     * Bezier path complexity, and 60 samples on standard 64-bit devices.
     */
    fun recommendedChartPoints(context: Context): Int {
        return computeChartPointLimit(
            is32Bit = is32BitDevice,
            isLowRam = isLowRamDevice(context)
        )
    }

    /**
     * Pure helper for computing rolling chart history limit.
     */
    fun computeChartPointLimit(is32Bit: Boolean, isLowRam: Boolean): Int {
        return when {
            isLowRam -> 30
            is32Bit -> 40
            else -> 60
        }
    }
}
