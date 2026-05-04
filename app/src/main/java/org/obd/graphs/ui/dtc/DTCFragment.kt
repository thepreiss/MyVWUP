package org.obd.graphs.ui.dtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.obd.graphs.R
import org.obd.graphs.SCREEN_LOCK_PROGRESS_EVENT
import org.obd.graphs.SCREEN_UNLOCK_PROGRESS_EVENT
import org.obd.graphs.ScreenLock
import org.obd.graphs.bl.datalogger.DATA_LOGGER_DTC_ACTION_COMPLETED
import org.obd.graphs.bl.datalogger.DataLoggerRepository
import org.obd.graphs.bl.datalogger.VehicleCapabilitiesManager
import org.obd.graphs.preferences.dtc.DiagnosticTroubleCodeViewAdapter
import org.obd.graphs.sendBroadcastEvent
import org.obd.graphs.ui.common.toast
import org.obd.graphs.ui.withDataLogger
import org.obd.metrics.api.model.DiagnosticTroubleCode

class DTCFragment : Fragment() {

    private lateinit var adapter: DiagnosticTroubleCodeViewAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var scanButton: Button
    private lateinit var clearButton: Button

    private val dtcNotificationsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DATA_LOGGER_DTC_ACTION_COMPLETED) {
                refreshDTCList()
                setLoadingState(false)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_dtc, container, false)

        recyclerView = root.findViewById(R.id.dtc_recycler_view)
        emptyState = root.findViewById(R.id.dtc_empty_state)
        scanButton = root.findViewById(R.id.action_scan_dtc)
        clearButton = root.findViewById(R.id.action_clear_dtc)

        adapter = DiagnosticTroubleCodeViewAdapter(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Apply VW Font
        val vwTypeface = android.graphics.Typeface.createFromAsset(requireContext().assets, "vw_font.ttf")
        root.findViewById<TextView>(R.id.dtc_header_title).typeface = vwTypeface
        scanButton.typeface = vwTypeface
        clearButton.typeface = vwTypeface
        emptyState.typeface = vwTypeface

        scanButton.setOnClickListener {
            if (DataLoggerRepository.isRunning()) {
                setLoadingState(true)
                withDataLogger {
                    scheduleDTCRead()
                }
            } else {
                toast(R.string.pref_dtc_no_connection_established)
            }
        }

        clearButton.setOnClickListener {
            if (DataLoggerRepository.isRunning()) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.pref_dtc_clean_dialog_title)
                    .setMessage(R.string.pref_dtc_clean_dialog_confirm_message)
                    .setPositiveButton(R.string.dtc_action_clear) { dialog, _ ->
                        setLoadingState(true)
                        withDataLogger {
                            scheduleDTCCleanup()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                toast(R.string.pref_dtc_no_connection_established)
            }
        }

        refreshDTCList()

        return root
    }

    private fun refreshDTCList() {
        val codes = VehicleCapabilitiesManager.getDiagnosticTroubleCodes()
        adapter.submitList(codes)
        
        if (codes.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            sendBroadcastEvent(
                SCREEN_LOCK_PROGRESS_EVENT,
                ScreenLock(message = R.string.pref_dtc_screen_lock, showCancel = true)
            )
        } else {
            sendBroadcastEvent(SCREEN_UNLOCK_PROGRESS_EVENT)
        }
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(dtcNotificationsReceiver, IntentFilter(DATA_LOGGER_DTC_ACTION_COMPLETED))
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(dtcNotificationsReceiver)
    }
}
