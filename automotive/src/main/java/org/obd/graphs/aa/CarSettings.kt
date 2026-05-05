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
package org.obd.graphs.aa

import android.graphics.Color
import android.util.Log
import androidx.car.app.CarContext
import org.obd.graphs.PREF_ALERTING_ENABLED
import org.obd.graphs.PREF_ALERT_LEGEND_ENABLED
import org.obd.graphs.PREF_DYNAMIC_SELECTOR_ENABLED
import org.obd.graphs.ViewPreferencesSerializer
import org.obd.graphs.bl.datalogger.dataLoggerSettings
import org.obd.graphs.preferences.*
import org.obd.graphs.renderer.api.ColorTheme
import org.obd.graphs.renderer.api.DragRacingScreenSettings
import org.obd.graphs.renderer.api.DynamicSelectorMode
import org.obd.graphs.renderer.api.GaugeProgressBarType
import org.obd.graphs.renderer.api.GaugeScreenSettings
import org.obd.graphs.renderer.api.GiuliaScreenSettings
import org.obd.graphs.renderer.api.GtiScreenSettings
import org.obd.graphs.renderer.api.Identity
import org.obd.graphs.renderer.api.PerformanceScreenSettings
import org.obd.graphs.renderer.api.RoutinesScreenSettings
import org.obd.graphs.renderer.api.ScreenSettings
import org.obd.graphs.renderer.api.SurfaceRendererType
import org.obd.graphs.renderer.api.TripInfoScreenSettings
import org.obd.graphs.runAsync
import org.obd.graphs.ui.common.COLOR_DYNAMIC_SELECTOR_ECO
import org.obd.graphs.ui.common.COLOR_DYNAMIC_SELECTOR_NORMAL
import org.obd.graphs.ui.common.COLOR_DYNAMIC_SELECTOR_RACE
import org.obd.graphs.ui.common.COLOR_DYNAMIC_SELECTOR_SPORT

private const val LAST_USER_SCREEN = "pref.aa.screen.last_used"

private const val PREF_PIDS_HISTORY_ENABLED = "pref.aa.pids.history.enabled"

private const val PREF_THEME_IN_ALLERT_VALUE_COLOR = "pref.aa.theme.inAlertValueColor"

private const val BACKGROUND_ENABLED = "pref.aa.theme.background.enabled"

private const val PREF_THEME_PROGRESS_BAR_COLOR = "pref.aa.theme.progressColor"
private const val PREF_THEME_DIVIDER_COLOR = "pref.aa.theme.dividerColor"
private const val PREF_THEME_CURR_VALUE_COLOR = "pref.aa.theme.currentValueColor"
private const val PREF_THEME_VIRTUAL_SCREEN_COLOR = "pref.aa.theme.btn.virtual-screen.color"

private const val PREF_SURFACE_FRAME_RATE = "pref.aa.surface.fps"
private const val PREF_STATUS_FPS_VISIBLE = "pref.aa.status.fps.enabled"

private const val DEFAULT_ITEMS_IN_COLUMN = "1"
private const val DEFAULT_FONT_SIZE = "32"
private const val DEFAULT_FRAME_RATE = "5"


enum class ScreenTemplateType {
    NAV, IOT
}

private data class DataPrefs(val virtualScreenPrefixKey: String,
                             val currentVirtualScreenKey: String,
                             val selectedPIDsKey: String,
                             val fontSizeKey: String)

private const val LOG_TAG = "CAR_SETTINGS"

class CarSettings(private val carContext: CarContext) : ScreenSettings {

    private var itemsSortOrder: Map<Long, Int>? = emptyMap()
    private val dragRacingScreenSettings = DragRacingScreenSettings()
    
    // Fixed list of PIDs available for rotation in the GTI Monitor.
    // Index 0 is always G-Force (0L).
    // Order: G-Force, RPM, Coolant, Turbo, Load, Throttle, IAT, Speed, Timing, Fuel Press, Voltage, STFT, LTFT, Lambda, Fuel Level, Ethanol
    private val gtiCyclePids: List<Long>
        get() = listOf(
            0L,    // 0: G-Force
            12L,   // 1: RPM
            5L,    // 2: Arrefecimento
            1002L, // 3: Turbo (boost)
            4L,    // 4: Carga
            17L,   // 5: Borboleta
            15L,   // 6: Admissão
            13L,   // 7: Velocidade
            14L,   // 8: Avanço
            35L,   // 9: Pressão Combustível
            66L,   // 10: Voltagem
            6L,    // 11: STFT
            7L,    // 12: LTFT
            52L,   // 13: Sonda Lambda
            47L,   // 14: Nível Tanque
            82L,   // 15: Etanol
            51L,   // 16: Atmosférica (Necessário para Turbo)
            11L    // 17: MAP (Necessário para Turbo)
        )

    private val gtiScreenSettings: GtiScreenSettings = object: GtiScreenSettings() {
        override fun setVirtualScreen(id: Int) {
            // Not used for individual rotation
        }
        override fun getVirtualScreen(): Int = 0
        
        override val selectedPIDs: Set<Long>
            get() = gtiCyclePids.toSet()
    }
    private val colorTheme = ColorTheme()

    private val gaugeScreenSettings = object: GaugeScreenSettings(){
        val dataPrefs = DataPrefs(
            virtualScreenPrefixKey="pref.aa.gauge.pids.profile_",
            currentVirtualScreenKey = "pref.aa.gauge.pids.vs.current",
            selectedPIDsKey = "pref.aa.gauge.pids.selected",
            fontSizeKey = "pref.aa.gauge.screen_font_size"
            )

        override fun getFontSize(): Int = Prefs.getS("${dataPrefs.fontSizeKey}.${getCurrentVirtualScreenId(dataPrefs)}", DEFAULT_FONT_SIZE).toInt()
        override fun setVirtualScreen(id: Int) = setVirtualScreenById(dataPrefs=dataPrefs, screenId=id)
        override fun getVirtualScreen(): Int =  getCurrentVirtualScreenId(dataPrefs)
        override fun isPIDsSortOrderEnabled(): Boolean = Prefs.getBoolean("pref.aa.virtual_screens.sort_order.enabled", false)
        override fun getPIDsSortOrder(): Map<Long, Int>? = if (isPIDsSortOrderEnabled()) itemsSortOrder else null
    }

    private val giuliaScreenSettings = object:  GiuliaScreenSettings(){
        val dataPrefs = DataPrefs(
            virtualScreenPrefixKey="pref.aa.pids.profile_",
            currentVirtualScreenKey = "pref.aa.pids.vs.current",
            selectedPIDsKey = "pref.aa.pids.selected",
            fontSizeKey = "pref.aa.screen_font_size"
        )

        override fun getFontSize(): Int = Prefs.getS("${dataPrefs.fontSizeKey}.${getCurrentVirtualScreenId(dataPrefs)}", DEFAULT_FONT_SIZE).toInt()
        override fun setVirtualScreen(id: Int) = setVirtualScreenById(screenId=id, dataPrefs=dataPrefs)
        override fun getVirtualScreen(): Int =  getCurrentVirtualScreenId(dataPrefs)
        override fun isPIDsSortOrderEnabled(): Boolean = Prefs.getBoolean("pref.aa.virtual_screens.sort_order.enabled", false)
        override fun getPIDsSortOrder(): Map<Long, Int>? = if (isPIDsSortOrderEnabled()) itemsSortOrder else null
    }

    private val tripInfoScreenSettings = TripInfoScreenSettings()
    private val routinesScreenSettings = RoutinesScreenSettings()
    private val performanceScreenSettings = PerformanceScreenSettings()

    init {
        copyGiuliaSettings()
        initGaugesDefaultProfiles()
    }

    override fun handleProfileChanged() {
        copyGiuliaSettings()
        initGaugesDefaultProfiles()
    }
    
    private fun initGaugesDefaultProfiles() {
        // Migration/Reset to ensure we use correct IDs and data types
        if (Prefs.getInt("pref.aa.pids.init.version", 0) < 15) {
            Log.i(LOG_TAG, "Applying migration to version 15")
            val cleanResources = listOf("mode01.json", "mode01_2.json", "extra.json")
            Prefs.updateStringSet("pref.pids.registry.list", cleanResources)
            Prefs.updateStringSet("profile_1.pref.pids.registry.list", cleanResources)
            Prefs.updateStringSet("profile_2.pref.pids.registry.list", cleanResources)
            
            // Define 8 tabs for the smartphone gauges (matching what works in AA)
            val tab1 = listOf(12L, 1002L, 5L, 15L, 13L, 14L)
            val tab2 = listOf(4L, 67L, 17L, 69L, 73L, 74L)
            val tab3 = listOf(11L, 51L, 1002L, 35L, 58L)
            val tab4 = listOf(5L, 15L, 60L, 66L)
            val tab5 = listOf(52L, 102L, 6L, 7L)
            val tab6 = listOf(47L, 82L, 13L, 33L, 49L)
            val tab7 = listOf(71L, 78L, 94L, 20L)
            val tab8 = listOf(12L, 1002L, 11L, 4L)

            val allTabs = listOf(tab1, tab2, tab3, tab4, tab5, tab6, tab7, tab8)
            allTabs.forEachIndexed { index, list ->
                val suffix = "${index + 1}"
                Prefs.updateLongSet("profile_1.pref.gauge.pids.selected.$suffix", list)
                Prefs.updateLongSet("pref.gauge.pids.selected.$suffix", list)
            }

            Prefs.updateLongSet("pref.aa.trip_info.pids.selected", listOf(13L, 12L, 5L, 11L, 47L, 66L))
            
            val p1 = listOf(12L, 13L, 11L, 4L, 17L, 15L) 
            val p2 = listOf(5L, 60L, 66L, 82L, 51L, 35L) 
            Prefs.updateLongSet("pref.aa.performance.pids.selected", p1 + p2)
            
            // Map them to top and bottom sections for Performance Screen
            val topPerfPids = listOf(13L, 5L, 60L) 
            val bottomPerfPids = listOf(12L, 1002L, 4L) 
            
            Prefs.updateLongSet("pref.query.performance.top", topPerfPids)
            Prefs.updateLongSet("pref.query.performance.bottom", bottomPerfPids)
            
            Prefs.updateInt("pref.aa.pids.init.version", 15)
            Prefs.updateInt("profile_1.pref.aa.pids.init.version", 15)
        }
    }

    override fun getDragRacingScreenSettings(): DragRacingScreenSettings = dragRacingScreenSettings.apply {
        metricsFrequencyReadEnabled = Prefs.getBoolean("pref.aa.drag_race.debug.display_frequency", true)
        vehicleSpeedDisplayDebugEnabled = Prefs.getBoolean("pref.aa.drag_race.debug.vehicle_speed_measurement", false)
        displayMetricsEnabled = Prefs.getBoolean("pref.aa.drag_race.vehicle_speed.enabled", true)
        shiftLightsEnabled = Prefs.getBoolean("pref.aa.drag_race.shift_lights.enabled", false)
        shiftLightsRevThreshold = Prefs.getS("pref.aa.drag_race.shift_lights.rev_value", "5000").toInt()
        displayMetricsExtendedEnabled = dataLoggerSettings.instance().gmeExtensionsEnabled
        fontSize = Prefs.getS("pref.aa.drag_race.font_size", "30").toInt()
        brakeBoostingSettings.viewEnabled = Prefs.getBoolean("pref.aa.drag_race.break_boosting.enabled", true)
    }


    override fun getRoutinesScreenSettings(): RoutinesScreenSettings = routinesScreenSettings.apply {
        viewEnabled = Prefs.getBoolean("pref.aa.routines.enabled", true)
    }

    override fun getTripInfoScreenSettings(): TripInfoScreenSettings = tripInfoScreenSettings.apply {
        fontSize = Prefs.getS("pref.aa.trip_info.font_size", "24").toInt()
        viewEnabled = Prefs.getBoolean("pref.aa.trip_info.enabled", true)
    }

    override fun getPerformanceScreenSettings(): PerformanceScreenSettings = performanceScreenSettings.apply {
        fontSize = Prefs.getS("pref.aa.performance.font_size", "24").toInt()
        viewEnabled = Prefs.getBoolean("pref.aa.performance.enabled", true)
        brakeBoostingSettings.viewEnabled = Prefs.getBoolean("pref.aa.performance.break_boosting.enabled", true)
    }

    override fun getColorTheme(): ColorTheme = colorTheme.apply {
        progressColor = Prefs.getInt(PREF_THEME_PROGRESS_BAR_COLOR, COLOR_DYNAMIC_SELECTOR_SPORT)
        dividerColor = Prefs.getInt(PREF_THEME_DIVIDER_COLOR, Color.WHITE)
        valueColor = Prefs.getInt(PREF_THEME_CURR_VALUE_COLOR, Color.WHITE)
        valueInAlertColor = Prefs.getInt(PREF_THEME_IN_ALLERT_VALUE_COLOR, COLOR_DYNAMIC_SELECTOR_SPORT)
        actionsBtnVirtualScreensColor = Prefs.getInt(PREF_THEME_VIRTUAL_SCREEN_COLOR, Color.WHITE)
    }

    override fun dynamicSelectorChangedEvent(mode: DynamicSelectorMode) {
        runAsync {
            if (isDynamicSelectorThemeEnabled()) {
                when (mode) {
                    DynamicSelectorMode.NORMAL -> Prefs.updateInt(PREF_THEME_PROGRESS_BAR_COLOR, COLOR_DYNAMIC_SELECTOR_NORMAL)
                    DynamicSelectorMode.SPORT -> Prefs.updateInt(PREF_THEME_PROGRESS_BAR_COLOR, COLOR_DYNAMIC_SELECTOR_SPORT)
                    DynamicSelectorMode.ECO -> Prefs.updateInt(PREF_THEME_PROGRESS_BAR_COLOR, COLOR_DYNAMIC_SELECTOR_ECO)
                    DynamicSelectorMode.RACE -> Prefs.updateInt(PREF_THEME_PROGRESS_BAR_COLOR, COLOR_DYNAMIC_SELECTOR_RACE)
                }
            }
        }
    }

    fun isAutomaticConnectEnabled(): Boolean = Prefs.getBoolean("pref.aa.connection.auto.enabled", false)

    fun isLoadLastVisitedScreenEnabled(): Boolean = Prefs.getBoolean("pref.aa.screen.load_last_visited.enabled", false)

    fun isConnectionDialogEnabled(): Boolean = Prefs.getBoolean("pref.aa.connect_dialog.enabled", true)

    fun getLastVisitedScreen(): Identity = SurfaceRendererType.fromInt(Prefs.getInt(LAST_USER_SCREEN, 0))

    fun setLastVisitedScreen(identity: Identity){
        if (identity is SurfaceRendererType) {
            Prefs.updateInt(LAST_USER_SCREEN, identity.id())
        }
    }

    override fun getGaugeScreenSettings(): GaugeScreenSettings = gaugeScreenSettings.apply {
        gaugeProgressBarType =  GaugeProgressBarType.valueOf(Prefs.getS("pref.aa.virtual_screens.screen.gauge.progress_type", GaugeProgressBarType.LONG.name))
        topOffset = Prefs.getS("pref.aa.virtual_screens.gauge.top_offset.${getCurrentVirtualScreenId(dataPrefs = this.dataPrefs)}","0").toInt()
        updateSelectedPIDs(Prefs.getStringSet(dataPrefs.selectedPIDsKey).map { s -> s.toLong() }.toSet())
    }

    override fun getGiuliaScreenSettings(): GiuliaScreenSettings = giuliaScreenSettings.apply {
        updateSelectedPIDs(Prefs.getStringSet(dataPrefs.selectedPIDsKey).map { s -> s.toLong() }.toSet())
    }

    override fun getGtiScreenSettings(): GtiScreenSettings = gtiScreenSettings.apply {
        val cycle = gtiCyclePids
        // Defaults: Left=RPM(1), Center=Turbo(3), Right=Coolant(2)
        val leftIdx = Prefs.getInt("pref.aa.gti.pids.left.idx", 1)
        val centerIdx = Prefs.getInt("pref.aa.gti.pids.center.idx", 3)
        val rightIdx = Prefs.getInt("pref.aa.gti.pids.right.idx", 2)
 
        leftPid = cycle.getOrElse(leftIdx % cycle.size) { 12L }
        centerPid = cycle.getOrElse(centerIdx % cycle.size) { 1002L }
        rightPid = cycle.getOrElse(rightIdx % cycle.size) { 5L }
    }

    fun rotateGtiGauge(slot: Int) {
        val key = when(slot) {
            0 -> "pref.aa.gti.pids.left.idx"
            1 -> "pref.aa.gti.pids.center.idx"
            else -> "pref.aa.gti.pids.right.idx"
        }
        val currentIdx = Prefs.getInt(key, if (slot == 1) 1 else if (slot == 2) 3 else 0)
        Prefs.updateInt(key, (currentIdx + 1) % gtiCyclePids.size)
    }

    override fun getMaxItems(): Int  =  Prefs.getS("pref.aa.virtual_screens.screen.max_items","6").toInt()

    override fun isStatusPanelEnabled(): Boolean = Prefs.getBoolean("pref.aa.virtual_screens.status_panel.enabled", true)

    override fun isScaleEnabled(): Boolean = Prefs.getBoolean("pref.aa.virtual_screens.scale.enabled", true)

    override fun getMaxColumns(): Int =
        Prefs.getS("pref.aa.max_pids_in_column.${getCurrentVirtualScreenId(giuliaScreenSettings.dataPrefs)}", DEFAULT_ITEMS_IN_COLUMN).toInt()

    override fun getBackgroundColor(): Int = if (carContext.isDarkMode) Color.BLACK else Color.BLACK

    override fun isBackgroundDrawingEnabled(): Boolean = Prefs.getBoolean(BACKGROUND_ENABLED, true)

    override fun isDynamicSelectorThemeEnabled(): Boolean = Prefs.getBoolean(PREF_DYNAMIC_SELECTOR_ENABLED, false)

    override fun isAlertingEnabled(): Boolean = Prefs.getBoolean(PREF_ALERTING_ENABLED, false)

    override fun isAlertLegendEnabled(): Boolean = Prefs.getBoolean(PREF_ALERT_LEGEND_ENABLED, false)

    override fun isStatisticsEnabled(): Boolean = Prefs.getBoolean(PREF_PIDS_HISTORY_ENABLED, true)

    override fun isFpsCounterEnabled(): Boolean = Prefs.getBoolean(PREF_STATUS_FPS_VISIBLE, false)

    override fun getSurfaceFrameRate(): Int = Prefs.getS(PREF_SURFACE_FRAME_RATE, DEFAULT_FRAME_RATE).toInt()

    override fun isBreakLabelTextEnabled(): Boolean = Prefs.getBoolean("pref.aa.break_label.${getCurrentVirtualScreenId(giuliaScreenSettings.dataPrefs)}", true)

    private fun setVirtualScreenById( screenId: Int, dataPrefs: DataPrefs) {
        val value = "${dataPrefs.virtualScreenPrefixKey}${screenId}"
        Prefs.updateString(dataPrefs.currentVirtualScreenKey, value)
        Prefs.updateStringSet(dataPrefs.selectedPIDsKey, Prefs.getStringSet(value).toList())
        itemsSortOrder = loadItemsSortOrder(value)
    }

    fun initItemsSortOrder() {
        itemsSortOrder = loadItemsSortOrder(getCurrentVirtualScreen(giuliaScreenSettings.dataPrefs))
    }

    fun isVirtualScreenEnabled(id: Int): Boolean = Prefs.getBoolean("pref.aa.virtual_screens.enabled.$id", true)

    fun getScreenTemplate(): ScreenTemplateType = ScreenTemplateType.NAV

    private fun getCurrentVirtualScreenId(dataPrefs: DataPrefs): Int = getCurrentVirtualScreen(dataPrefs).last().digitToInt()
    private fun getCurrentVirtualScreen(dataPrefs: DataPrefs): String = Prefs.getS(dataPrefs.currentVirtualScreenKey, "pref.aa.pids.profile_1")

    private fun loadItemsSortOrder(key: String) = ViewPreferencesSerializer("${key}.view.settings").getItemsSortOrder()

    private fun copyGiuliaSettings() {
        try {
            val gauge = gaugeScreenSettings.dataPrefs
            if (!Prefs.contains(gauge.selectedPIDsKey)) {
                Log.i(LOG_TAG, "No Gauge settings found. Copy Giulia Settings...")
                val giulia = giuliaScreenSettings.dataPrefs

                (1..4).forEach {
                    Prefs.getStringSet("${giulia.virtualScreenPrefixKey}$it").toList().let { list ->
                        Log.i(LOG_TAG, "Giulia virtual screen $it=$list")
                        Prefs.updateStringSet("${gauge.virtualScreenPrefixKey}$it", list)
                    }
                }

               Prefs.getStringSet(giulia.selectedPIDsKey).toList().let { list ->
                    Log.i(LOG_TAG, "Updating Gauge Selected PIDs $list")
                    Prefs.updateStringSet(gauge.selectedPIDsKey, list)
               }
            }
        } catch (e: Exception){
            Log.e(LOG_TAG, "Failed to set copy Giulia settings",e)
        }
    }
}
