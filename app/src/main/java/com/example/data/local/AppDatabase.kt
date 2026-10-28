package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.model.AlertFreezeFrame
import com.example.model.DashboardWidgetConfig
import com.example.model.MetricPid
import com.example.model.WidgetSpan
import com.example.model.WidgetStyle
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "dashboard_presets")
data class DashboardPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subtitle: String,
    val categoryTag: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
    val widgetsJson: String
)

@Entity(tableName = "vehicle_alerts")
data class VehicleAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertCode: String,
    val subsystem: String,
    val severity: String,
    val title: String,
    val description: String,
    val recommendedAction: String,
    val rawCanIdHex: String,
    val rawPayloadHex: String,
    val freezeFrameJson: String,
    val timestampMs: Long,
    val isActive: Boolean,
    val isAcknowledged: Boolean = false
)

@Entity(tableName = "telemetry_sessions")
data class TelemetrySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceMode: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val sampleCount: Int,
    val maxSpeedKph: Float,
    val peakPowerKw: Float,
    val maxRegenKw: Float,
    val minSocPct: Float,
    val maxSocPct: Float,
    val maxCellDeltaMv: Float,
    val maxStatorTempC: Float,
    val notes: String = ""
)

@Entity(
    tableName = "telemetry_samples",
    indices = [Index(value = ["sessionId"])]
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val elapsedMs: Long,
    val speedKph: Float,
    val netPowerKw: Float,
    val rearMotorKw: Float,
    val frontMotorKw: Float,
    val usableSocPct: Float,
    val packVoltageV: Float,
    val packCurrentA: Float,
    val cellDeltaMv: Float,
    val statorTempC: Float,
    val coolantBatteryInletC: Float,
    val lateralG: Float,
    val longitudinalG: Float,
    val brakePressureBar: Float
)

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard_presets ORDER BY sortOrder ASC, id ASC")
    fun observeAllDashboards(): Flow<List<DashboardPresetEntity>>

    @Query("SELECT COUNT(*) FROM dashboard_presets")
    suspend fun countDashboards(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDashboard(preset: DashboardPresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDashboards(presets: List<DashboardPresetEntity>)

    @Update
    suspend fun updateDashboard(preset: DashboardPresetEntity)

    @Query("DELETE FROM dashboard_presets WHERE id = :id")
    suspend fun deleteDashboardById(id: Long)

    @Query("DELETE FROM dashboard_presets")
    suspend fun deleteAllDashboards()
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM vehicle_alerts ORDER BY isActive DESC, timestampMs DESC")
    fun observeAllAlerts(): Flow<List<VehicleAlertEntity>>

    @Query("SELECT * FROM vehicle_alerts WHERE alertCode = :code AND isActive = 1 LIMIT 1")
    suspend fun findActiveAlertByCode(code: String): VehicleAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: VehicleAlertEntity): Long

    @Query("UPDATE vehicle_alerts SET isActive = 0, isAcknowledged = 1 WHERE isActive = 1")
    suspend fun clearAndAcknowledgeActiveAlerts()

    @Query("UPDATE vehicle_alerts SET isAcknowledged = 1 WHERE id = :id")
    suspend fun acknowledgeAlertById(id: Long)

    @Query("DELETE FROM vehicle_alerts WHERE isActive = 0")
    suspend fun purgeHistoricalAlerts()
}

@Dao
interface TelemetrySessionDao {
    @Query("SELECT * FROM telemetry_sessions ORDER BY startTimeMs DESC")
    fun observeAllSessions(): Flow<List<TelemetrySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TelemetrySessionEntity): Long

    @Update
    suspend fun updateSession(session: TelemetrySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: TelemetrySampleEntity)

    @Query("SELECT * FROM telemetry_samples WHERE sessionId = :sessionId ORDER BY elapsedMs ASC")
    suspend fun getSamplesForSession(sessionId: Long): List<TelemetrySampleEntity>

    @Query("DELETE FROM telemetry_samples WHERE sessionId = :sessionId")
    suspend fun deleteSamplesForSession(sessionId: Long)

    @Query("DELETE FROM telemetry_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM telemetry_samples")
    suspend fun deleteAllSamples()

    @Query("DELETE FROM telemetry_sessions")
    suspend fun deleteAllSessions()
}

@Database(
    entities = [
        DashboardPresetEntity::class,
        VehicleAlertEntity::class,
        TelemetrySessionEntity::class,
        TelemetrySampleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dashboardDao(): DashboardDao
    abstract fun alertDao(): AlertDao
    abstract fun telemetrySessionDao(): TelemetrySessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "s3xy_cockpit_telemetry.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object CockpitJsonSerializer {
    fun encodeWidgets(widgets: List<DashboardWidgetConfig>): String {
        val array = JSONArray()
        widgets.forEach { w ->
            val obj = JSONObject()
            obj.put("id", w.id)
            obj.put("pid", w.pid.name)
            obj.put("style", w.style.name)
            obj.put("span", w.span.name)
            obj.put("warningThreshold", w.warningThreshold.toDouble())
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeWidgets(json: String): List<DashboardWidgetConfig> {
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<DashboardWidgetConfig>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pid = runCatching { MetricPid.valueOf(obj.getString("pid")) }
                    .getOrDefault(MetricPid.SPEED_KPH)
                val style = runCatching { WidgetStyle.valueOf(obj.getString("style")) }
                    .getOrDefault(pid.defaultStyle)
                val span = runCatching { WidgetSpan.valueOf(obj.getString("span")) }
                    .getOrDefault(pid.defaultSpan)
                val threshold = obj.optDouble("warningThreshold", pid.defaultWarningThreshold.toDouble()).toFloat()
                list.add(
                    DashboardWidgetConfig(
                        id = obj.optString("id", "${pid.name}_$i"),
                        pid = pid,
                        style = style,
                        span = span,
                        warningThreshold = threshold
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun encodeFreezeFrame(ff: AlertFreezeFrame): String {
        return JSONObject().apply {
            put("speedKph", ff.speedKph.toDouble())
            put("netPowerKw", ff.netPowerKw.toDouble())
            put("packVoltageV", ff.packVoltageV.toDouble())
            put("packCurrentA", ff.packCurrentA.toDouble())
            put("usableSocPct", ff.usableSocPct.toDouble())
            put("cellMinV", ff.cellMinV.toDouble())
            put("cellMaxV", ff.cellMaxV.toDouble())
            put("cellDeltaMv", ff.cellDeltaMv.toDouble())
            put("statorTempC", ff.statorTempC.toDouble())
            put("rearInverterTempC", ff.rearInverterTempC.toDouble())
            put("coolantBatteryInletC", ff.coolantBatteryInletC.toDouble())
            put("octovalvePosition", ff.octovalvePosition)
            put("gear", ff.gear)
        }.toString()
    }

    fun decodeFreezeFrame(json: String): AlertFreezeFrame {
        return try {
            val obj = JSONObject(json)
            AlertFreezeFrame(
                speedKph = obj.optDouble("speedKph", 0.0).toFloat(),
                netPowerKw = obj.optDouble("netPowerKw", 0.0).toFloat(),
                packVoltageV = obj.optDouble("packVoltageV", 0.0).toFloat(),
                packCurrentA = obj.optDouble("packCurrentA", 0.0).toFloat(),
                usableSocPct = obj.optDouble("usableSocPct", 0.0).toFloat(),
                cellMinV = obj.optDouble("cellMinV", 0.0).toFloat(),
                cellMaxV = obj.optDouble("cellMaxV", 0.0).toFloat(),
                cellDeltaMv = obj.optDouble("cellDeltaMv", 0.0).toFloat(),
                statorTempC = obj.optDouble("statorTempC", 0.0).toFloat(),
                rearInverterTempC = obj.optDouble("rearInverterTempC", 0.0).toFloat(),
                coolantBatteryInletC = obj.optDouble("coolantBatteryInletC", 0.0).toFloat(),
                octovalvePosition = obj.optInt("octovalvePosition", 0),
                gear = obj.optString("gear", "--")
            )
        } catch (e: Exception) {
            AlertFreezeFrame(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, "--")
        }
    }

    fun createFactoryDashboards(): List<DashboardPresetEntity> {
        val perfWidgets = listOf(
            DashboardWidgetConfig("perf_1", MetricPid.SPEED_KPH, WidgetStyle.RADIAL_GAUGE, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("perf_2", MetricPid.NET_POWER_KW, WidgetStyle.BI_DIRECTIONAL_ARC, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("perf_3", MetricPid.REAR_MOTOR_KW, WidgetStyle.SPARKLINE_CARD, WidgetSpan.WIDE_2X1),
            DashboardWidgetConfig("perf_4", MetricPid.FRONT_MOTOR_KW, WidgetStyle.SPARKLINE_CARD, WidgetSpan.WIDE_2X1),
            DashboardWidgetConfig("perf_5", MetricPid.REAR_TORQUE_NM, WidgetStyle.HORIZONTAL_BAR, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("perf_6", MetricPid.FRONT_TORQUE_NM, WidgetStyle.HORIZONTAL_BAR, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("perf_7", MetricPid.STATOR_TEMP_C, WidgetStyle.RADIAL_GAUGE, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("perf_8", MetricPid.REAR_INVERTER_TEMP_C, WidgetStyle.RADIAL_GAUGE, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("perf_9", MetricPid.ZERO_TO_SIXTY_SEC, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("perf_10", MetricPid.THROTTLE_PCT, WidgetStyle.HORIZONTAL_BAR, WidgetSpan.COMPACT_1X1)
        )

        val batteryWidgets = listOf(
            DashboardWidgetConfig("bat_1", MetricPid.USABLE_SOC_PCT, WidgetStyle.RADIAL_GAUGE, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("bat_2", MetricPid.CELL_DELTA_MV, WidgetStyle.CELL_BRICK_HISTOGRAM, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("bat_3", MetricPid.PACK_VOLTAGE_V, WidgetStyle.SPARKLINE_CARD, WidgetSpan.WIDE_2X1),
            DashboardWidgetConfig("bat_4", MetricPid.CHARGE_POWER_KW, WidgetStyle.SPARKLINE_CARD, WidgetSpan.WIDE_2X1),
            DashboardWidgetConfig("bat_5", MetricPid.CELL_MIN_V, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("bat_6", MetricPid.CELL_MAX_V, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("bat_7", MetricPid.PACK_CURRENT_A, WidgetStyle.BI_DIRECTIONAL_ARC, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("bat_8", MetricPid.BMS_SOC_PCT, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("bat_9", MetricPid.NOMINAL_FULL_PACK_KWH, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("bat_10", MetricPid.DEGRADATION_PCT, WidgetStyle.HORIZONTAL_BAR, WidgetSpan.COMPACT_1X1)
        )

        val thermalWidgets = listOf(
            DashboardWidgetConfig("thm_1", MetricPid.COOLANT_BATTERY_INLET_C, WidgetStyle.RADIAL_GAUGE, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("thm_2", MetricPid.COOLANT_POWERTRAIN_INLET_C, WidgetStyle.RADIAL_GAUGE, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("thm_3", MetricPid.BATTERY_COOLANT_LPM, WidgetStyle.SPARKLINE_CARD, WidgetSpan.WIDE_2X1),
            DashboardWidgetConfig("thm_4", MetricPid.POWERTRAIN_COOLANT_LPM, WidgetStyle.SPARKLINE_CARD, WidgetSpan.WIDE_2X1),
            DashboardWidgetConfig("thm_5", MetricPid.OCTOVALVE_POS, WidgetStyle.HORIZONTAL_BAR, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("thm_6", MetricPid.PACK_TEMP_MAX_C, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("thm_7", MetricPid.CABIN_TEMP_C, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("thm_8", MetricPid.HVAC_POWER_KW, WidgetStyle.HORIZONTAL_BAR, WidgetSpan.COMPACT_1X1)
        )

        val dynamicsWidgets = listOf(
            DashboardWidgetConfig("dyn_1", MetricPid.COMBINED_G_FORCE, WidgetStyle.G_FORCE_CIRCLE, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("dyn_2", MetricPid.BRAKE_PRESSURE_BAR, WidgetStyle.RADIAL_GAUGE, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("dyn_3", MetricPid.REGEN_SPLIT_PCT, WidgetStyle.HORIZONTAL_BAR, WidgetSpan.WIDE_2X1),
            DashboardWidgetConfig("dyn_4", MetricPid.STEERING_ANGLE_DEG, WidgetStyle.BI_DIRECTIONAL_ARC, WidgetSpan.WIDE_2X1),
            DashboardWidgetConfig("dyn_5", MetricPid.SPEED_KPH, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("dyn_6", MetricPid.NET_POWER_KW, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1)
        )

        return listOf(
            DashboardPresetEntity(
                id = 1,
                name = "Performance & Inverters",
                subtitle = "Dual Motor power, torque split, MOSFET/stator temps & 0-100 sprint",
                categoryTag = "PERFORMANCE",
                isBuiltIn = true,
                sortOrder = 1,
                widgetsJson = encodeWidgets(perfWidgets)
            ),
            DashboardPresetEntity(
                id = 2,
                name = "Battery, Cell Balance & Charge",
                subtitle = "Usable vs BMS SOC, brick voltage histogram, V/A & degradation",
                categoryTag = "BATTERY",
                isBuiltIn = true,
                sortOrder = 2,
                widgetsJson = encodeWidgets(batteryWidgets)
            ),
            DashboardPresetEntity(
                id = 3,
                name = "Thermal Loop & Octovalve",
                subtitle = "Octovalve step, battery/powertrain coolant flow & heat pump load",
                categoryTag = "THERMAL",
                isBuiltIn = true,
                sortOrder = 3,
                widgetsJson = encodeWidgets(thermalWidgets)
            ),
            DashboardPresetEntity(
                id = 4,
                name = "Braking, Regen & G-Force",
                subtitle = "2D friction circle G-plot, brake line bar, regen split & steering rack",
                categoryTag = "DYNAMICS",
                isBuiltIn = true,
                sortOrder = 4,
                widgetsJson = encodeWidgets(dynamicsWidgets)
            )
        )
    }
}
