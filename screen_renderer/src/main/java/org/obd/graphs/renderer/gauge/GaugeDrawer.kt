package org.obd.graphs.renderer.gauge

import android.content.Context
import android.graphics.*
import org.obd.graphs.bl.collector.Metric
import org.obd.graphs.format
import org.obd.graphs.renderer.AbstractDrawer
import org.obd.graphs.renderer.api.GaugeProgressBarType
import org.obd.graphs.renderer.api.ScreenSettings
import kotlin.math.*

data class DrawerSettings(
    var gaugeProgressBarType: GaugeProgressBarType = GaugeProgressBarType.LONG,
    var startAngle: Float = 135f,
    var sweepAngle: Float = 270f
)

class GaugeDrawer(
    context: Context,
    settings: ScreenSettings,
    val drawerSettings: DrawerSettings = DrawerSettings()
) : AbstractDrawer(context, settings) {

    private val bgArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(255, 40, 40, 40)
    }

    private val progressArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        color = Color.WHITE
    }

    private val mainValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f
    }

    private val arcBounds = RectF()

    // NEW SIGNATURE FOR RESPONSIVE GAUGE (Used by GaugeSurfaceRenderer)
    fun drawGauge(
        canvas: Canvas,
        metric: Metric?,
        cx: Float,
        cy: Float,
        radius: Float,
        label: String
    ) {
        val strokeWidth = radius * 0.15f
        bgArcPaint.strokeWidth = strokeWidth
        progressArcPaint.strokeWidth = strokeWidth
        
        val startAngle = 135f
        val sweepAngle = 270f
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, bgArcPaint)

        for (i in 0..10) {
            val angleRad = Math.toRadians((startAngle + (i * (sweepAngle / 10))).toDouble())
            val innerR = radius * 0.85f
            val outerR = radius * 1.0f
            canvas.drawLine(
                cx + innerR * cos(angleRad).toFloat(),
                cy + innerR * sin(angleRad).toFloat(),
                cx + outerR * cos(angleRad).toFloat(),
                cy + outerR * sin(angleRad).toFloat(),
                tickPaint
            )
        }

        metric?.let {
            val pidId = it.pid.id
            val isTurbo = pidId == 11L || pidId == 1002L
            val isCoolant = pidId == 5L
            val isVoltage = pidId == 66L
            val isRpm = pidId == 12L
            val isFuelPressure = pidId == 35L
            val isEthanol = pidId == 82L
            val isCatalyst = pidId == 60L

            val min = when {
                isTurbo -> -1.0
                isCoolant -> -20.0
                isVoltage -> 0.0
                isRpm -> 0.0
                else -> it.pid.min.toDouble()
            }
            val max = when {
                isTurbo -> 2.5
                isCoolant -> 140.0
                isFuelPressure -> 300.0
                isVoltage -> 18.0
                isRpm -> 7000.0
                isEthanol -> 100.0
                isCatalyst -> 1000.0
                pidId == 52L -> 2.0
                label.contains("TORQUE", true) -> 250.0
                label.contains("CARGA", true) || label.contains("BORBOLETA", true) -> 100.0
                else -> {
                    val m = it.pid.max.toDouble()
                    if (m == 0.0) 100.0 else m
                }
            }

            val current = it.source.toString().toDoubleOrNull() ?: min
            val progress = ((current - min) / (max - min)).coerceIn(0.0, 1.0)
            
            canvas.drawArc(arcBounds, startAngle, (progress * sweepAngle).toFloat(), false, progressArcPaint)

            mainValuePaint.textSize = radius * 0.5f
            val valueText = if (isVoltage) String.format("%.1f", current) else it.source.format(castToInt = false)
            canvas.drawText(valueText, cx, cy + (mainValuePaint.textSize * 0.15f), mainValuePaint)

            unitPaint.textSize = radius * 0.18f
            val unitText = when {
                isTurbo || isFuelPressure -> "bar"
                isVoltage -> "V"
                isRpm -> "rpm"
                pidId == 52L -> "λ"
                else -> it.pid.units ?: ""
            }
            canvas.drawText(unitText, cx + (radius * 0.4f), cy + (radius * 0.4f), unitPaint)
        }

        labelPaint.textSize = radius * 0.15f
        canvas.drawText(label, cx, cy + radius + (radius * 0.2f), labelPaint)
    }

    // COMPATIBILITY SIGNATURE (Used by DragRacing, BrakeBoosting, etc.)
    fun drawGauge(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        metric: Metric?,
        label: String = ""
    ) {
        // Map old coordinates to new radial system for unified look
        val radius = width / 2.5f
        val cx = left + width / 2f
        val cy = top + width / 2f
        drawGauge(canvas, metric, cx, cy, radius, label)
    }
}
