package org.obd.graphs.aa.screen.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.model.*
import androidx.lifecycle.LifecycleOwner
import org.obd.graphs.LocalizedStringProvider
import org.obd.graphs.aa.CarSettings
import org.obd.graphs.aa.R
import org.obd.graphs.aa.screen.CarScreen
import org.obd.graphs.aa.screen.createAction
import org.obd.graphs.aa.screen.withDataLogger
import org.obd.graphs.aa.toast
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.bl.datalogger.DATA_LOGGER_DTC_ACTION_COMPLETED
import org.obd.graphs.bl.datalogger.DataLoggerRepository
import org.obd.graphs.bl.datalogger.VehicleCapabilitiesManager
import org.obd.graphs.bl.datalogger.WorkflowStatus
import org.obd.graphs.registerReceiver
import org.obd.graphs.renderer.api.Fps
import org.obd.graphs.renderer.api.SurfaceRendererType

private const val LOG_TAG = "DiagnosticsScreen"

internal class DiagnosticsScreen(
    carContext: CarContext,
    settings: CarSettings,
    metricsCollector: MetricsCollector,
    fps: Fps
) : CarScreen(carContext, settings, metricsCollector, fps) {

    private val stringProvider = LocalizedStringProvider(carContext)
    private var isScanning = false

    private var broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DATA_LOGGER_DTC_ACTION_COMPLETED) {
                isScanning = false
                invalidate()
                toast.show(carContext, R.string.routine_executed_successfully)
            }
        }
    }

    override fun onGetTemplate(): Template = try {
        if (isScanning) {
            ListTemplate.Builder()
                .setLoading(true)
                .setTitle(stringProvider.getString(R.string.diagnostics_page_title))
                .setHeaderAction(Action.BACK)
                .build()
        } else {
            val codes = VehicleCapabilitiesManager.getDiagnosticTroubleCodes()
            val itemListBuilder = ItemList.Builder()

            if (codes.isEmpty()) {
                itemListBuilder.setNoItemsMessage(stringProvider.getString(R.string.diagnostics_no_errors))
            } else {
                codes.forEach { code ->
                    itemListBuilder.addItem(
                        Row.Builder()
                            .setTitle(code.standardCode)
                            .addText(code.description ?: "")
                            .build()
                    )
                }
            }

            ListTemplate.Builder()
                .setLoading(false)
                .setTitle(stringProvider.getString(R.string.diagnostics_page_title))
                .setSingleList(itemListBuilder.build())
                .setHeaderAction(Action.BACK)
                .setActionStrip(getActionStrip())
                .build()
        }
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to build template", e)
        PaneTemplate.Builder(Pane.Builder().setLoading(true).build())
            .setHeaderAction(Action.BACK)
            .setTitle(stringProvider.getString(R.string.pref_aa_car_error))
            .build()
    }

    private fun getActionStrip(): ActionStrip {
        return ActionStrip.Builder()
            .addAction(
                createAction(carContext, R.drawable.actions_connect, CarColor.GREEN) {
                    if (DataLoggerRepository.isRunning()) {
                        isScanning = true
                        invalidate()
                        withDataLogger {
                            scheduleDTCRead()
                        }
                    } else {
                        toast.show(carContext, R.string.pref_dtc_no_connection_established)
                    }
                }
            )
            .addAction(
                createAction(carContext, R.drawable.action_exit, CarColor.RED) {
                    if (DataLoggerRepository.isRunning()) {
                        isScanning = true
                        invalidate()
                        withDataLogger {
                            scheduleDTCCleanup()
                        }
                    } else {
                        toast.show(carContext, R.string.pref_dtc_no_connection_established)
                    }
                }
            )
            .build()
    }

    override fun dataLoggerStart() {
        withDataLogger {
            scheduleDTCRead()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        registerReceiver(carContext, broadcastReceiver) {
            it.addAction(DATA_LOGGER_DTC_ACTION_COMPLETED)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        carContext.unregisterReceiver(broadcastReceiver)
    }
}
