/*
 * Copyright 2019-2026, Tomasz Żebrowski
 *
 * <p>Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.obd.graphs.renderer.performance

import android.content.Context
import android.graphics.*
import org.obd.graphs.bl.collector.Metric
import org.obd.graphs.renderer.AbstractDrawer
import org.obd.graphs.renderer.api.GaugeProgressBarType
import org.obd.graphs.renderer.api.ScreenSettings
import org.obd.graphs.renderer.gauge.DrawerSettings
import org.obd.graphs.renderer.gauge.GaugeDrawer
import org.obd.graphs.renderer.trip.TripInfoDrawer
import org.obd.graphs.isNumber
import org.obd.metrics.pid.ValueType

 private const val MAX_ITEMS_IN_ROW = 5

@Suppress("NOTHING_TO_INLINE")
internal class PerformanceDrawer(context: Context, settings: ScreenSettings) :
    AbstractDrawer(context, settings) {
    private val vwTypeface: Typeface = Typeface.createFromAsset(context.assets, "vw_font.ttf")

    private val telemetryHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textAlign = Paint.Align.LEFT
        typeface = vwTypeface
    }

    private val telemetryLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = vwTypeface
    }

    private val telemetryValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(vwTypeface, Typeface.BOLD)
    }

    private val telemetryMinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4287f5") // Blueish
        textAlign = Paint.Align.RIGHT
        typeface = vwTypeface
    }

    private val telemetryMaxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#f54242") // Reddish
        textAlign = Paint.Align.RIGHT
        typeface = vwTypeface
    }

    private val background: Bitmap =
        BitmapFactory.decodeResource(
            context.resources,
            org.obd.graphs.renderer.R.drawable.drag_race_bg
        )

    override fun invalidate() {
        super.invalidate()
    }

    override fun getBackground(): Bitmap = background

    override fun recycle() {
        this.background.recycle()
    }

    fun drawScreen(
        canvas: Canvas,
        area: Rect,
        left: Float,
        top: Float,
        performanceInfoDetails: PerformanceInfoDetails
    ) {
        val metrics = (performanceInfoDetails.topMetrics + performanceInfoDetails.bottomMetrics).distinctBy { it.pid.id }
        if (metrics.isEmpty()) return

        val margin = 20f
        val startLeft = left + margin
        val availableWidth = area.width() - (2 * margin)
        
        val colWidths = floatArrayOf(
            availableWidth * 0.45f, // Label
            availableWidth * 0.18f, // Min
            availableWidth * 0.19f, // Current
            availableWidth * 0.18f  // Max
        )

        val headerTop = top + 20f
        telemetryHeaderPaint.textSize = area.height() * 0.04f
        
        // Draw Headers
        canvas.drawText("PARÂMETRO", startLeft, headerTop, telemetryHeaderPaint)
        canvas.drawText("MÍN", startLeft + colWidths[0] + colWidths[1], headerTop, telemetryHeaderPaint.apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("ATUAL", startLeft + colWidths[0] + colWidths[1] + colWidths[2], headerTop, telemetryHeaderPaint.apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("MÁX", startLeft + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3], headerTop, telemetryHeaderPaint.apply { textAlign = Paint.Align.RIGHT })

        drawDivider(canvas, startLeft, availableWidth, headerTop + 15f, Color.DKGRAY)

        val rowHeight = (area.height() - (headerTop - area.top)) / (metrics.size + 1).coerceAtLeast(10).toFloat()
        val textSize = (rowHeight * 0.5f).coerceAtMost(area.height() * 0.06f)
        
        telemetryLabelPaint.textSize = textSize
        telemetryValuePaint.textSize = textSize
        telemetryMinPaint.textSize = textSize * 0.8f
        telemetryMaxPaint.textSize = textSize * 0.8f

        var currentRowTop = headerTop + rowHeight

        metrics.forEach { metric ->
            val pid = metric.pid
            val label = pid.description.uppercase()
            
            // Draw Label
            canvas.drawText(label, startLeft, currentRowTop, telemetryLabelPaint)

            // Current Value
            val currentValue = metric.value?.toString()?.toDoubleOrNull() ?: 0.0
            val precision = if (pid.id == 52L || pid.id == 66L) 1 else 0
            val currentText = String.format("%.${precision}f", currentValue)
            canvas.drawText(currentText, startLeft + colWidths[0] + colWidths[1] + colWidths[2], currentRowTop, telemetryValuePaint)

            // Min/Max
            val minText = String.format("%.${precision}f", metric.min)
            val maxText = String.format("%.${precision}f", metric.max)
            canvas.drawText(minText, startLeft + colWidths[0] + colWidths[1], currentRowTop, telemetryMinPaint)
            canvas.drawText(maxText, startLeft + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3], currentRowTop, telemetryMaxPaint)

            drawDivider(canvas, startLeft, availableWidth, currentRowTop + (rowHeight * 0.3f), Color.parseColor("#1AFFFFFF"))
            currentRowTop += rowHeight
        }
    }

}
