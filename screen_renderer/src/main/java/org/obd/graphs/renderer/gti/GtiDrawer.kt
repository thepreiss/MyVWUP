package org.obd.graphs.renderer.gti

import android.content.Context
import android.graphics.*
import org.obd.graphs.bl.collector.Metric
import org.obd.graphs.format
import org.obd.graphs.renderer.AbstractDrawer
import org.obd.graphs.renderer.api.ScreenSettings
import org.obd.graphs.renderer.R
import kotlin.math.*

class GtiDrawer(
    context: Context,
    settings: ScreenSettings
) : AbstractDrawer(context, settings) {

    private val dialBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.gti_dial_bg)
    private val needleBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.gti_needle)
    private val vwTypeface: Typeface = Typeface.createFromAsset(context.assets, "vw_font.ttf")

    private val gBallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = vwTypeface
    }

    private val gtiValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = vwTypeface
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 200, 200, 200)
        strokeWidth = 2f
    }

    private val matrix = Matrix()
    private val targetRect = RectF()

    fun drawHeader(canvas: Canvas, area: Rect, headerHeight: Float) {
        headerPaint.textSize = headerHeight * 0.45f
        canvas.drawText("Performance monitor", area.centerX().toFloat(), area.top + headerHeight * 0.7f, headerPaint)
    }

    fun drawGauge(
        canvas: Canvas,
        metric: Metric?,
        cx: Float,
        cy: Float,
        radius: Float,
        label: String,
        type: String = "OBD",
        gX: Float = 0f,
        gY: Float = 0f,
        customValue: Double? = null
    ) {
        if (type != "GFORCE") {
            targetRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawBitmap(dialBitmap, null, targetRect, null)
        }

        if (type == "GFORCE") {
            renderGMeter(canvas, cx, cy, radius, gX, gY)
        } else {
            renderObd(canvas, metric, cx, cy, radius, label, customValue)
        }
    }

    private fun renderGMeter(canvas: Canvas, cx: Float, cy: Float, radius: Float, gX: Float, gY: Float) {
        val innerR = radius * 0.85f
        crosshairPaint.style = Paint.Style.STROKE
        crosshairPaint.color = Color.DKGRAY
        canvas.drawCircle(cx, cy, innerR * 0.33f, crosshairPaint)
        canvas.drawCircle(cx, cy, innerR * 0.66f, crosshairPaint)
        canvas.drawCircle(cx, cy, innerR, crosshairPaint)
        canvas.drawLine(cx - innerR, cy, cx + innerR, cy, crosshairPaint)
        canvas.drawLine(cx, cy - innerR, cx, cy + innerR, crosshairPaint)

        val maxG = 1.0f
        var targetX = (gX / maxG) * innerR
        var targetY = (gY / maxG) * innerR

        val currentG = hypot(gX, gY)
        
        // Draw the glowing red ball (the "G" spot)
        gBallPaint.setShadowLayer(25f, 0f, 0f, Color.RED)
        canvas.drawCircle(cx + targetX, cy + targetY, radius * 0.15f, gBallPaint)
        gBallPaint.clearShadowLayer()

        // Draw Value
        gtiValuePaint.textSize = radius * 0.45f
        val gValueText = String.format("%.2f", currentG).replace(".", ",")
        canvas.drawText(gValueText, cx, cy + (radius * 0.15f), gtiValuePaint)
        
        gtiValuePaint.textSize = radius * 0.15f
        canvas.drawText("g", cx, cy + (radius * 0.35f), gtiValuePaint)
    }

    private fun renderObd(canvas: Canvas, metric: Metric?, cx: Float, cy: Float, radius: Float, label: String, customValue: Double? = null) {
        val isCoolant = label == "ARREFECIMENTO" || metric?.pid?.id == 5L
        val isTurbo = label == "TURBO" || metric?.pid?.id == 1002L
        val isVoltage = label == "VOLTAGEM" || metric?.pid?.id == 66L
        val isFuel = label == "COMBUSTÍVEL" || metric?.pid?.id == 35L
        val isTiming = label == "AVANÇO" || metric?.pid?.id == 14L
        val isEthanol = label == "ETANOL" || metric?.pid?.id == 82L
        val isCatalyst = label == "CATALISADOR" || metric?.pid?.id == 60L
        val isRpm = label == "RPM" || metric?.pid?.id == 12L
        val pidId = metric?.pid?.id
        
        val min = when {
            isTurbo -> -1.0
            isCoolant -> -20.0
            isTiming -> -20.0
            isVoltage -> 0.0
            isRpm -> 0.0
            else -> metric?.pid?.min?.toDouble() ?: 0.0
        }
        val max = when {
            isTurbo -> 2.5
            isCoolant -> 140.0
            isFuel -> 300.0 
            isTiming -> 50.0
            isVoltage -> 18.0
            isCatalyst -> 1000.0
            isEthanol -> 100.0
            isRpm -> 7000.0
            pidId == 52L -> 2.0
            label.contains("TORQUE", true) -> 250.0
            label.contains("CARGA", true) || label.contains("BORBOLETA", true) -> 100.0
            else -> metric?.pid?.max?.toDouble() ?: 100.0
        }
        
        val current = customValue ?: (metric?.source?.toString()?.toDoubleOrNull() ?: 0.0)
        
        val valueText = when {
            isCoolant || label == "ADMISSÃO" || isCatalyst -> String.format("%03d", current.toInt())
            isVoltage -> String.format("%.1f", current)
            isEthanol || label == "CARGA" || label == "BORBOLETA" || isRpm -> String.format("%.0f", current)
            else -> String.format("%.2f", current)
        }

        val unitText = when {
            isCoolant || label == "ADMISSÃO" || isCatalyst -> "°C"
            isTurbo || isFuel -> "bar"
            isTiming -> "°"
            isVoltage -> "V"
            isEthanol || label == "CARGA" || label == "BORBOLETA" -> "%"
            isRpm -> "rpm"
            pidId == 52L -> "λ"
            else -> metric?.pid?.units ?: ""
        }

        gtiValuePaint.textSize = radius * 0.4f
        canvas.drawText(valueText, cx, cy + (radius * 0.1f), gtiValuePaint)
        gtiValuePaint.textSize = radius * 0.12f
        canvas.drawText(unitText, cx, cy + (radius * 0.3f), gtiValuePaint)

        gtiValuePaint.textSize = radius * 0.12f
        canvas.drawText(label, cx, cy + (radius * 0.65f), gtiValuePaint)

        val startAngle = 135f
        val sweepAngle = 270f
        val progress = ((current - min) / (max - min)).coerceIn(0.0, 1.0)
        val angle = startAngle + (progress * sweepAngle).toFloat() + 90f

        matrix.reset()
        val scale = (radius * 1.8f) / needleBitmap.width
        matrix.postScale(scale, scale)
        val px = (needleBitmap.width * scale) / 2f
        val py = (needleBitmap.height * scale) / 2f
        matrix.postRotate(angle, px, py)
        matrix.postTranslate(cx - px, cy - py)
        canvas.drawBitmap(needleBitmap, matrix, null)
    }
}
