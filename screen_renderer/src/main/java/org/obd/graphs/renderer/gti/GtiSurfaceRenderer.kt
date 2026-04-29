package org.obd.graphs.renderer.gti

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.renderer.AbstractSurfaceRenderer
import org.obd.graphs.renderer.api.Fps
import org.obd.graphs.renderer.api.GtiScreenSettings
import org.obd.graphs.renderer.api.ScreenSettings
import kotlin.math.min
import kotlin.math.abs

class GtiSurfaceRenderer(
    context: Context,
    private val settings: ScreenSettings,
    private val metricsCollector: MetricsCollector,
    private val fps: Fps
) : AbstractSurfaceRenderer(context), SensorEventListener {

    private val drawer = GtiDrawer(context, settings)
    private var sensorManager: SensorManager? = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    
    private var gForceX = 0.0f
    private var gForceY = 0.0f

    private var lastArea = Rect()
    private var headerHeight = 0f
    private var gaugeRadius = 0f
    private var cx1 = 0f
    private var cx2 = 0f
    private var cx3 = 0f
    private var cy = 0f

    init {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onSurfaceClick(x: Float, y: Float) {
        // Disabled, using mobile configuration instead
    }

    override fun onDraw(canvas: Canvas, drawArea: Rect?) {
        drawArea?.let { area ->
            if (area.isEmpty) return

            if (area != lastArea) {
                updateLayout(area)
            }

            drawer.drawBackground(canvas, area)
            drawer.drawHeader(canvas, area, headerHeight)

            val metrics = metricsCollector.getMetrics()
            val baro = metrics.find { it.pid.id == 51L }?.source?.toString()?.toDoubleOrNull() ?: 1.0
            
            val gtiSettings = settings.getGtiScreenSettings()
            
            // Left Gauge
            val leftPid = gtiSettings.leftPid
            if (leftPid == 0L) {
                drawer.drawGauge(canvas, null, cx1, cy, gaugeRadius, "FORÇA G", type = "GFORCE", gX = gForceX, gY = gForceY)
            } else {
                val metric = metrics.find { it.pid.id == leftPid }
                drawer.drawGauge(canvas, metric, cx1, cy, gaugeRadius, getLabel(leftPid))
            }

            // Center Gauge
            val centerPid = gtiSettings.centerPid
            val centerMetric = metrics.find { it.pid.id == centerPid }

            if (centerPid == 0L) {
                drawer.drawGauge(canvas, null, cx2, cy, gaugeRadius, "FORÇA G", type = "GFORCE", gX = gForceX, gY = gForceY)
            } else if (centerPid == 1002L) {
                val manifold = metrics.find { it.pid.id == 11L }?.source?.toString()?.toDoubleOrNull() ?: baro
                val boost = manifold - baro
                drawer.drawGauge(canvas, centerMetric, cx2, cy, gaugeRadius, "TURBO", customValue = boost)
            } else if (centerPid == 35L && centerMetric != null) {
                val rawValue = centerMetric.source.toString().toDoubleOrNull() ?: 0.0
                val barValue = if (rawValue > 1000) rawValue / 100.0 else rawValue
                drawer.drawGauge(canvas, centerMetric, cx2, cy, gaugeRadius, "COMBUSTÍVEL", customValue = barValue)
            } else {
                drawer.drawGauge(canvas, centerMetric, cx2, cy, gaugeRadius, getLabel(centerPid))
            }

            // Right Gauge
            val rightPid = gtiSettings.rightPid
            if (rightPid == 0L) {
                drawer.drawGauge(canvas, null, cx3, cy, gaugeRadius, "FORÇA G", type = "GFORCE", gX = gForceX, gY = gForceY)
            } else {
                val rightMetric = metrics.find { it.pid.id == rightPid }
                drawer.drawGauge(canvas, rightMetric, cx3, cy, gaugeRadius, getLabel(rightPid))
            }
        }
    }

    private fun getLabel(pid: Long): String = when(pid) {
        1002L -> "TURBO"
        35L -> "COMBUSTÍVEL"
        12L -> "RPM"
        4L -> "CARGA"
        17L -> "BORBOLETA"
        66L -> "VOLTAGEM"
        5L -> "ARREFECIMENTO"
        15L -> "ADMISSÃO"
        60L -> "CATALISADOR"
        82L -> "ETANOL"
        12L -> "RPM"
        14L -> "AVANÇO"
        else -> "G-FORCE"
    }

    private fun updateLayout(area: Rect) {
        lastArea = Rect(area)
        headerHeight = area.height() * 0.15f
        val bodyHeight = area.height() * 0.85f 
        val columnWidth = area.width() / 3f
        cx1 = area.left + columnWidth * 0.5f
        cx2 = area.left + columnWidth * 1.5f
        cx3 = area.left + columnWidth * 2.5f
        cy = area.top + headerHeight + (bodyHeight * 0.45f)
        gaugeRadius = min(columnWidth * 0.42f, bodyHeight * 0.42f)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                val gravity = 9.80665f
                val rawX = it.values[0] / gravity
                // Detect longitudinal axis (usually Y or Z)
                val rawY = (if (abs(it.values[1]) > abs(it.values[2])) it.values[1] else it.values[2]) / gravity
                
                gForceX = gForceX * 0.85f + rawX * 0.15f
                gForceY = gForceY * 0.85f + (-rawY) * 0.15f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun recycle() {
        sensorManager?.unregisterListener(this)
        drawer.recycle()
    }

    override fun invalidate() {
        drawer.invalidate()
    }
}
