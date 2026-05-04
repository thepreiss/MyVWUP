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
package org.obd.graphs.renderer.trip

import android.content.Context
import android.graphics.*
import org.obd.graphs.bl.collector.Metric
import org.obd.graphs.bl.trip.tripManager
import org.obd.graphs.renderer.AbstractDrawer
import org.obd.graphs.renderer.api.ScreenSettings

private const val CURRENT_MIN = 22f
private const val CURRENT_MAX = 72f
private const val NEW_MAX = 1.6f
private const val NEW_MIN = 0.6f

const val MAX_ITEM_IN_THE_ROW = 6

internal class TripMetricDescriptor(
    val fetcher: (TripInfoDetails) -> Metric?,
    val castToInt: Boolean = false,
    val statsEnabled: Boolean = true,
    val unitEnabled: Boolean = true,
    val valueDoublePrecision: Int = 2,
    val statsDoublePrecision: Int = 2
)

internal class BottomMetricDescriptor(
    val fetcher: (TripInfoDetails) -> Metric?,
    val castToInt: Boolean
)

internal class TripInfoLayoutCache {
    val area = Rect()
    var valueTextSize: Float = 0f
    var textSizeBase: Float = 0f
    var bottomRowTextSizeBase: Float = 0f
    var bottomColWidth: Float = 0f
    var activeBottomMetricsCount: Int = -1

    fun requiresLayoutUpdate(newArea: Rect, newBottomMetricsCount: Int): Boolean {
        return area != newArea || activeBottomMetricsCount != newBottomMetricsCount
    }
}

@Suppress("NOTHING_TO_INLINE")
internal class TripInfoDrawer(
    context: Context,
    settings: ScreenSettings
) : AbstractDrawer(context, settings) {

    private val vwTypeface: Typeface = Typeface.createFromAsset(context.assets, "vw_font.ttf")

    private val tripHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textAlign = Paint.Align.CENTER
        typeface = vwTypeface
    }

    private val tripLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = vwTypeface
    }

    private val tripValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(vwTypeface, Typeface.BOLD)
    }

    private val tripUnitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textAlign = Paint.Align.LEFT
        typeface = vwTypeface
    }

    private val background: Bitmap =
        BitmapFactory.decodeResource(
            context.resources,
            org.obd.graphs.renderer.R.drawable.background
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
        tripInfo: TripInfoDetails
    ) {
        val margin = 30f
        val startLeft = left + margin
        val availableWidth = area.width() - (2 * margin)
        
        // Header
        tripHeaderPaint.textSize = area.height() * 0.05f
        canvas.drawText("COMPUTADOR DE BORDO", area.centerX().toFloat(), top + 20f, tripHeaderPaint)
        drawDivider(canvas, startLeft, availableWidth, top + 35f, Color.DKGRAY)

        val rowHeight = area.height() * 0.16f
        val colWidth = availableWidth / 2f
        var currentRowTop = top + 100f
        
        val labelSize = (area.height() * 0.045f).coerceAtMost(35f)
        val valueSize = (area.height() * 0.08f).coerceAtMost(70f)
        val unitSize = (area.height() * 0.035f).coerceAtMost(25f)
        
        tripLabelPaint.textSize = labelSize
        tripValuePaint.textSize = valueSize
        tripUnitPaint.textSize = unitSize

        // 1. DISTANCE & TRIP TIME
        val distValue = tripInfo.distance?.value?.toString() ?: "---"
        drawValueWithLabel(canvas, "DISTÂNCIA", distValue, startLeft, currentRowTop, colWidth, "km")
        
        // Trip Time calculation
        val startTs = tripManager.getCurrentTrip().startTs
        val durationMs = if (startTs > 0) System.currentTimeMillis() - startTs else 0
        val hours = durationMs / 3600000
        val minutes = (durationMs % 3600000) / 60000
        val seconds = (durationMs % 60000) / 1000
        val timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        
        canvas.drawText("TEMPO", startLeft + colWidth, currentRowTop, tripLabelPaint)
        canvas.drawText(timeText, startLeft + availableWidth, currentRowTop + (valueSize * 0.8f), tripValuePaint)
        
        currentRowTop += rowHeight
        drawDivider(canvas, startLeft, availableWidth, currentRowTop - (rowHeight * 0.35f), Color.parseColor("#1AFFFFFF"))

        // 2. SPEED (AVG & MAX)
        val speedMetric = tripInfo.vehicleSpeed
        val avgSpeed = speedMetric?.mean?.toInt()?.toString() ?: "---"
        val maxSpeed = speedMetric?.max?.toInt()?.toString() ?: "---"
        
        drawValueWithLabel(canvas, "VELOC. MÉDIA", avgSpeed, startLeft, currentRowTop, colWidth, "km/h")
        drawValueWithLabel(canvas, "VELOC. MÁXIMA", maxSpeed, startLeft + colWidth, currentRowTop, colWidth, "km/h")

        currentRowTop += rowHeight
        drawDivider(canvas, startLeft, availableWidth, currentRowTop - (rowHeight * 0.35f), Color.parseColor("#1AFFFFFF"))

        // 3. CONSUMPTION (AVG & INSTANT)
        val consMetric = tripInfo.fuelConsumption
        val avgCons = if (consMetric != null && consMetric.mean > 0) String.format("%.1f", consMetric.mean) else "---"
        val instCons = consMetric?.value?.toString() ?: "---"

        drawValueWithLabel(canvas, "CONSUMO MÉDIO", avgCons, startLeft, currentRowTop, colWidth, "km/l")
        drawValueWithLabel(canvas, "CONSUMO INST.", instCons, startLeft + colWidth, currentRowTop, colWidth, "km/l")

        currentRowTop += rowHeight
        drawDivider(canvas, startLeft, availableWidth, currentRowTop - (rowHeight * 0.35f), Color.parseColor("#1AFFFFFF"))

        // 4. FUEL & RANGE
        val fuelLevelMetric = tripInfo.fuellevel
        val fuelLevel = fuelLevelMetric?.value?.toString() ?: "---"
        drawValueWithLabel(canvas, "COMBUSTÍVEL", fuelLevel, startLeft, currentRowTop, colWidth, "%")
        
        // Range estimation (simplistic: 45L tank * level * avgCons)
        val range = if (fuelLevel != "---" && avgCons != "---") {
            val level = fuelLevel.toDoubleOrNull() ?: 0.0
            val cons = avgCons.replace(",", ".").toDoubleOrNull() ?: 0.0
            (0.45 * level * cons).toInt().toString()
        } else "---"
        
        drawValueWithLabel(canvas, "AUTONOMIA", range, startLeft + colWidth, currentRowTop, colWidth, "km")
    }

    private fun drawValueWithLabel(canvas: Canvas, label: String, value: String, left: Float, top: Float, width: Float, unit: String) {
        canvas.drawText(label, left, top, tripLabelPaint)
        val valuePadding = 75f
        canvas.drawText(value, left + width - valuePadding, top + (tripValuePaint.textSize * 0.8f), tripValuePaint)
        canvas.drawText(unit, left + width - (valuePadding - 5f), top + (tripValuePaint.textSize * 0.8f), tripUnitPaint)
    }
}
