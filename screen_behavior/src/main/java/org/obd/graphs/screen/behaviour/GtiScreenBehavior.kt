package org.obd.graphs.screen.behaviour

import android.content.Context
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.bl.query.QueryStrategyType
import org.obd.graphs.renderer.api.Fps
import org.obd.graphs.renderer.api.ScreenSettings
import org.obd.graphs.renderer.api.SurfaceRendererType

internal class GtiScreenBehavior(
    context: Context,
    metricsCollector: MetricsCollector,
    settings: Map<SurfaceRendererType, ScreenSettings>,
    fps: Fps
) : ScreenBehavior(
    context,
    metricsCollector,
    settings[SurfaceRendererType.GTI]!!,
    fps,
    SurfaceRendererType.GTI
) {

    override fun queryStrategyType(): QueryStrategyType = QueryStrategyType.INDIVIDUAL_QUERY

    override fun syncFilters() {
        query.setStrategy(queryStrategyType())
        val gtiSettings = settings.getGtiScreenSettings()
        query.update(gtiSettings.selectedPIDs)
        metricsCollector.applyFilter(enabled = query.getIDs())
    }
}
