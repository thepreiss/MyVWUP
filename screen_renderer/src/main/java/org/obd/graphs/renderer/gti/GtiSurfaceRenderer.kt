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
import kotlin.math.*

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
    
    // Two-stage filter variables
    private var rawGX = 0.0f
    private var rawGY = 0.0f

    private var lastArea = Rect()
    private var headerHeight = 0f
    private var gaugeRadius = 0f
    private var cx1 = 0f
    private var cx2 = 0f
    private var cx3 = 0f
    private var cy = 0f

    init {
        val linearSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager?.registerListener(this, linearSensor, SensorManager.SENSOR_DELAY_UI)
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
            val baro = metrics.find { it.pid.id == 51L }?.value?.toString()?.toDoubleOrNull() ?: 1.0
            val gtiSettings = settings.getGtiScreenSettings()

            // Draw Gauges
            drawGtiGauge(canvas, metrics, gtiSettings.leftPid, cx1, cy, baro)
            drawGtiGauge(canvas, metrics, gtiSettings.centerPid, cx2, cy, baro)
            drawGtiGauge(canvas, metrics, gtiSettings.rightPid, cx3, cy, baro)
        }
    }

    private fun drawGtiGauge(canvas: Canvas, metrics: List<org.obd.graphs.bl.collector.Metric>, pidId: Long, cx: Float, cy: Float, baro: Double) {
        if (pidId == 0L) {
            drawer.drawGauge(canvas, null, cx, cy, gaugeRadius, "G-FORCE", type = "GFORCE", gX = gForceX, gY = gForceY)
            return
        }

        val metric = metrics.find { it.pid.id == pidId }
        var customValue: Double? = null

        // Only Turbo needs a runtime calculation (MAP - Baro = boost pressure).
        // All other PIDs already have their formula applied via extra.json.
        if (pidId == 1002L) {
            val manifold = metrics.find { it.pid.id == 11L }?.value?.toString()?.toDoubleOrNull() ?: baro
            customValue = (manifold - baro).coerceAtLeast(-1.0)
        }

        drawer.drawGauge(canvas, metric, cx, cy, gaugeRadius, getLabel(pidId), pidId = pidId, customValue = customValue)
    }

    private fun getLabel(pid: Long): String = when(pid) {
        1002L -> "TURBO"
        35L -> "PRESSÃO COMBUSTÍVEL"
        58L -> "PRESSÃO ALTA"
        12L -> "RPM"
        4L -> "CARGA"
        67L -> "CARGA ABSOLUTA"
        17L -> "BORBOLETA"
        69L -> "BORBOLETA RELATIVA"
        71L -> "BORBOLETA ABS B"
        66L -> "VOLTAGEM"
        5L -> "ARREFECIMENTO"
        15L -> "ADMISSÃO"
        60L -> "CATALISADOR"
        82L -> "ETANOL"
        13L -> "VELOCIDADE"
        14L -> "AVANÇO"
        52L -> "SONDA 1 RAZÃO"
        102L -> "SONDA 1 CORRENTE"
        20L -> "SONDA 2 TENSÃO"
        6L -> "STFT"
        7L -> "LTFT"
        47L -> "NÍVEL TANQUE"
        70L -> "AMBIENTE"
        73L -> "PEDAL D"
        74L -> "PEDAL E"
        78L -> "ATUADOR"
        11L -> "MAP"
        33L -> "DISTÂNCIA MIL"
        48L -> "WARM-UPS"
        49L -> "DISTÂNCIA CLEAR"
        68L -> "AR/COMB COMANDADO"
        94L -> "PURGA EVAP"
        else -> metricsCollector.getMetrics().find { it.pid.id == pid }?.pid?.description?.uppercase() ?: "PID $pid"
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
                
                // Device coordinates: X is lateral, Y is longitudinal (for portrait mount)
                val latG = it.values[0] / gravity
                val lonG = it.values[1] / gravity

                // --- Stage 1: Pre-filter (Agressive noise reduction for vibrations) ---
                val preFilterGain = 0.08f
                rawGX = rawGX * (1f - preFilterGain) + latG * preFilterGain
                rawGY = rawGY * (1f - preFilterGain) + lonG * preFilterGain

                // --- Stage 2: Polar Clamping & Dead Zone ---
                var finalX = rawGX
                var finalY = -rawGY // Invert Y for screen coordinates (forward is negative Y)
                
                val magnitude = sqrt(finalX * finalX + finalY * finalY)
                
                // Dead zone (0.03G)
                if (magnitude < 0.03f) {
                    finalX = 0f
                    finalY = 0f
                } else if (magnitude > 1.0f) {
                    // Polar clamping: keep direction, limit magnitude to 1.0G
                    finalX *= (1.0f / magnitude)
                    finalY *= (1.0f / magnitude)
                }

                // --- Stage 3: Display Filter (Smooth visual movement) ---
                val displayFilterGain = 0.20f
                gForceX = gForceX * (1f - displayFilterGain) + finalX * displayFilterGain
                gForceY = gForceY * (1f - displayFilterGain) + finalY * displayFilterGain
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
