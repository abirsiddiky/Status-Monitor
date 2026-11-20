package com.example

import com.example.data.model.ChartHistory
import com.example.util.DeviceOptimizer
import com.example.util.DisplayModeSpec
import com.example.util.DisplayRefreshRateHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceAndRefreshRateTest {

    @Test
    fun `selectBestDisplayMode picks highest refresh rate at matching resolution`() {
        val modes = listOf(
            DisplayModeSpec(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeSpec(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 90f),
            DisplayModeSpec(modeId = 3, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f),
            DisplayModeSpec(modeId = 4, physicalWidth = 720, physicalHeight = 1600, refreshRate = 144f)
        )

        val selected = DisplayRefreshRateHelper.selectBestDisplayMode(
            modes = modes,
            currentWidth = 1080,
            currentHeight = 2400,
            preferHighRefreshRate = true
        )

        assertNotNull(selected)
        assertEquals(3, selected!!.modeId)
        assertEquals(120f, selected.refreshRate, 0.01f)
    }

    @Test
    fun `selectBestDisplayMode returns null when preference is disabled`() {
        val modes = listOf(
            DisplayModeSpec(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeSpec(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f)
        )

        val selected = DisplayRefreshRateHelper.selectBestDisplayMode(
            modes = modes,
            currentWidth = 1080,
            currentHeight = 2400,
            preferHighRefreshRate = false
        )

        assertNull(selected)
    }

    @Test
    fun `selectBestDisplayMode returns null on 60Hz-only displays`() {
        val modes = listOf(
            DisplayModeSpec(modeId = 1, physicalWidth = 1080, physicalHeight = 1920, refreshRate = 60f)
        )

        val selected = DisplayRefreshRateHelper.selectBestDisplayMode(
            modes = modes,
            currentWidth = 1080,
            currentHeight = 1920,
            preferHighRefreshRate = true
        )

        assertNull(selected)
    }

    @Test
    fun `computeChartPointLimit scales down for 32-bit and low-RAM devices`() {
        assertEquals(60, DeviceOptimizer.computeChartPointLimit(is32Bit = false, isLowRam = false))
        assertEquals(40, DeviceOptimizer.computeChartPointLimit(is32Bit = true, isLowRam = false))
        assertEquals(30, DeviceOptimizer.computeChartPointLimit(is32Bit = true, isLowRam = true))
        assertEquals(30, DeviceOptimizer.computeChartPointLimit(is32Bit = false, isLowRam = true))
    }

    @Test
    fun `ChartHistory enforces custom rolling maxPoints limit`() {
        var history = ChartHistory()
        for (i in 1..50) {
            history = history.addSample(
                cpuLoadVal = i.toFloat(),
                cpuTempVal = 40f + i * 0.1f,
                memPercentVal = 50f,
                storageInUseVal = 1000f,
                rxRateVal = i.toFloat(),
                txRateVal = i.toFloat(),
                maxPoints = 40
            )
        }

        assertEquals(40, history.cpuLoad.size)
        assertEquals(11f, history.cpuLoad.first(), 0.01f)
        assertEquals(50f, history.cpuLoad.last(), 0.01f)
        assertEquals(40, history.cpuTemp.size)
        assertEquals(40, history.networkRx.size)
    }
}
