package org.obd.graphs.renderer.gauge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.renderer.AbstractSurfaceRenderer
import org.obd.graphs.renderer.api.Fps
import org.obd.graphs.renderer.api.ScreenSettings
import kotlin.math.min

class GaugeSurfaceRenderer(
    context: Context,
    private val settings: ScreenSettings,
    private val metricsCollector: MetricsCollector,
    private val fps: Fps
) : AbstractSurfaceRenderer(context) {

    private val drawer = GaugeDrawer(context, settings)
    
    // Layout State
    private var lastWidth = 0
    private var lastHeight = 0
    private var rows = 1
    private var cols = 4
    private var cellWidth = 0f
    private var cellHeight = 0f
    private var radius = 0f

    override fun onDraw(canvas: Canvas, drawArea: Rect?) {
        drawArea?.let { area ->
            if (area.isEmpty) return

            if (area.width() != lastWidth || area.height() != lastHeight) {
                updateLayout(area)
            }

            drawer.drawBackground(canvas, area)

            val metrics = metricsCollector.getMetrics()
            val itemsToDraw = min(metrics.size, rows * cols)

            for (i in 0 until itemsToDraw) {
                val row = i / cols
                val col = i % cols
                
                val cx = area.left + (col * cellWidth) + (cellWidth / 2f)
                val cy = area.top + (row * cellHeight) + (cellHeight / 2f)

                val metric = metrics[i]
                drawer.drawGauge(canvas, metric, cx, cy, radius, metric.source.command.pid.description)
            }
        }
    }

    private fun updateLayout(area: Rect) {
        lastWidth = area.width()
        lastHeight = area.height()
        val aspectRatio = lastWidth.toFloat() / lastHeight.toFloat()

        if (aspectRatio > 1.8f) {
            // Widescreen optimization: 1 row, 4 massive columns
            rows = 1
            cols = 4
        } else {
            // Standard optimization: 2x2 grid
            rows = 2
            cols = 2
        }

        cellWidth = lastWidth.toFloat() / cols
        cellHeight = lastHeight.toFloat() / rows

        // Radius should fit in the cell with some padding for labels and margins
        radius = min(cellWidth * 0.4f, cellHeight * 0.4f)
    }

    override fun recycle() {
        drawer.recycle()
    }

    override fun invalidate() {
        drawer.invalidate()
    }
}
