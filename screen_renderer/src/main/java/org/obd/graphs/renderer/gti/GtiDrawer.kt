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

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
        typeface = vwTypeface
    }

    private var lastX = 0f
    private var lastY = 0f

    private val matrix = Matrix()
    private val targetRect = RectF()

    fun drawHeader(canvas: Canvas, area: Rect, headerHeight: Float) {
        headerPaint.textSize = headerHeight * 0.45f
        canvas.drawText("Monitor GTI", area.centerX().toFloat(), area.top + headerHeight * 0.7f, headerPaint)
    }

    fun drawGauge(
        canvas: Canvas,
        metric: Metric?,
        cx: Float,
        cy: Float,
        radius: Float,
        label: String,
        pidId: Long = -1L,
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
            renderObd(canvas, metric, cx, cy, radius, label, pidId, customValue)
        }
    }

    private fun renderGMeter(canvas: Canvas, cx: Float, cy: Float, radius: Float, gX: Float, gY: Float) {
        val innerR = radius * 0.85f
        
        // --- Draw Scale Rings & Crosshair ---
        crosshairPaint.style = Paint.Style.STROKE
        crosshairPaint.color = Color.DKGRAY
        canvas.drawCircle(cx, cy, innerR * 0.33f, crosshairPaint)
        canvas.drawCircle(cx, cy, innerR * 0.66f, crosshairPaint)
        canvas.drawCircle(cx, cy, innerR, crosshairPaint)
        canvas.drawLine(cx - innerR, cy, cx + innerR, cy, crosshairPaint)
        canvas.drawLine(cx, cy - innerR, cx, cy + innerR, crosshairPaint)

        // --- Draw Scale Labels ---
        labelPaint.textSize = radius * 0.1f
        canvas.drawText("0,3G", cx, cy - innerR * 0.33f + (radius * 0.05f), labelPaint)
        canvas.drawText("0,6G", cx, cy - innerR * 0.66f + (radius * 0.05f), labelPaint)
        canvas.drawText("1,0G", cx, cy - innerR * 1.0f + (radius * 0.05f), labelPaint)

        // --- Draw Axis Indicators ---
        labelPaint.textSize = radius * 0.12f
        labelPaint.color = Color.WHITE
        canvas.drawText("L", cx - innerR - (radius * 0.15f), cy + (radius * 0.04f), labelPaint)
        canvas.drawText("R", cx + innerR + (radius * 0.15f), cy + (radius * 0.04f), labelPaint)
        canvas.drawText("F", cx, cy - innerR - (radius * 0.1f), labelPaint)
        canvas.drawText("B", cx, cy + innerR + (radius * 0.2f), labelPaint)

        // --- Calculate Ball Position with Clamping ---
        val maxG = 1.0f
        var targetX = (gX / maxG) * innerR
        var targetY = (gY / maxG) * innerR

        val currentG = hypot(gX, gY)
        
        // Defensive clamping
        val dist = hypot(targetX, targetY)
        if (dist > innerR) {
            val scale = innerR / dist
            targetX *= scale
            targetY *= scale
        }

        // --- Draw Trail (Ghost Ball) ---
        gBallPaint.alpha = 100
        canvas.drawCircle(cx + lastX, cy + lastY, radius * 0.06f, gBallPaint)
        gBallPaint.alpha = 255
        
        lastX = targetX
        lastY = targetY

        // --- Draw Main Ball ---
        gBallPaint.setShadowLayer(20f, 0f, 0f, Color.RED)
        canvas.drawCircle(cx + targetX, cy + targetY, radius * 0.08f, gBallPaint)
        gBallPaint.clearShadowLayer()

        // --- Draw Value (Moved below to avoid overlap) ---
        gtiValuePaint.textSize = radius * 0.35f
        val gValueText = String.format("%.2f", currentG).replace(".", ",")
        canvas.drawText(gValueText, cx, cy + innerR + (radius * 0.5f), gtiValuePaint)
        
        gtiValuePaint.textSize = radius * 0.12f
        canvas.drawText("g", cx, cy + innerR + (radius * 0.65f), gtiValuePaint)
    }

    private fun renderObd(canvas: Canvas, metric: Metric?, cx: Float, cy: Float, radius: Float, label: String, pidId: Long, customValue: Double? = null) {
        // ---- Sensor type flags driven strictly by pidId ----
        val isTurbo   = pidId == 1002L  // customValue, so no pid object
        val isRpm     = pidId == 12L
        val isTiming  = pidId == 14L

        // ---- Ranges: prefer values from the PID definition (extra.json) ----
        //  Only override when the PID object is absent (Turbo uses customValue)
        val min: Double = when {
            isTurbo  -> -1.0
            isTiming -> -64.0
            else     -> metric?.pid?.min?.toDouble() ?: 0.0
        }
        val max: Double = when {
            isTurbo  -> 2.5
            isRpm    -> 7000.0  // safety cap to prevent scale compression
            else     -> metric?.pid?.max?.toDouble() ?: 100.0
        }

        // ---- Current value ----
        val current = customValue ?: (metric?.value?.toString()?.toDoubleOrNull() ?: 0.0)

        // ---- Value text ----
        val valueText: String = when {
            metric == null && customValue == null -> "---"
            // Integer display for coarse values
            isRpm || pidId == 4L || pidId == 17L || pidId == 67L ||
            pidId == 69L || pidId == 71L || pidId == 78L ||
            pidId == 13L || pidId == 47L || pidId == 48L ||
            pidId == 33L || pidId == 49L || pidId == 82L ||
            pidId == 94L || pidId == 11L ->
                String.format("%.0f", current)
            // One decimal for temperatures and small float values
            pidId == 5L || pidId == 15L || pidId == 60L ||
            pidId == 66L || pidId == 52L || pidId == 20L ||
            pidId == 68L || pidId == 102L ->
                String.format("%.1f", current)
            // Two decimals for turbo/fuel pressure/lambda/timing
            isTurbo || pidId == 35L || pidId == 58L || isTiming ||
            pidId == 6L || pidId == 7L ->
                String.format("%.2f", current)
            else -> metric?.source?.format(precision = 1) ?: String.format("%.1f", current)
        }

        // ---- Unit text: use pid.units from extra.json, override only for Turbo ----
        val unitText: String = when {
            isTurbo  -> "bar"
            else     -> metric?.pid?.units?.let {
                // Normalise "C" to "°C"
                if (it == "C") "°C" else it
            } ?: ""
        }

        // ---- Draw value, unit, label ----
        gtiValuePaint.textSize = radius * 0.38f
        canvas.drawText(valueText, cx, cy + (radius * 0.1f), gtiValuePaint)
        gtiValuePaint.textSize = radius * 0.13f
        canvas.drawText(unitText, cx, cy + (radius * 0.3f), gtiValuePaint)
        gtiValuePaint.textSize = radius * 0.12f
        canvas.drawText(label, cx, cy + (radius * 0.65f), gtiValuePaint)

        // ---- Needle rotation ----
        val startAngle = 135f
        val sweepAngle = 270f
        val range = max - min
        val progress = if (range > 0) ((current - min) / range).coerceIn(0.0, 1.0) else 0.0
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
