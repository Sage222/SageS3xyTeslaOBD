package com.example.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.DashboardPresetEntity
import com.example.data.local.TelemetrySampleEntity
import com.example.data.local.TelemetrySessionEntity
import com.example.data.local.VehicleAlertEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TelemetryExportManager {

    private fun ensureExportDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun exportSessionToCsv(
        context: Context,
        session: TelemetrySessionEntity,
        samples: List<TelemetrySampleEntity>
    ): File {
        val dir = ensureExportDir(context)
        val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(28)
        val file = File(dir, "s3xy_session_${session.id}_${safeTitle}.csv")

        val sb = StringBuilder()
        sb.appendLine("# S3XY Cockpit - 2023 Tesla Model 3 Telemetry Session Log")
        sb.appendLine("# Session: ${session.title}")
        sb.appendLine("# Source: ${session.sourceMode}")
        sb.appendLine("# Peak Power (kW): ${"%.1f".format(session.peakPowerKw)}, Max Speed (km/h): ${"%.1f".format(session.maxSpeedKph)}, Max Cell Delta (mV): ${"%.1f".format(session.maxCellDeltaMv)}")
        sb.appendLine("elapsed_ms,speed_kph,net_power_kw,rear_motor_kw,front_motor_kw,usable_soc_pct,pack_voltage_v,pack_current_a,cell_delta_mv,stator_temp_c,coolant_batt_inlet_c,lateral_g,longitudinal_g,brake_pressure_bar")

        for (s in samples) {
            sb.append(s.elapsedMs).append(',')
                .append("%.1f".format(Locale.US, s.speedKph)).append(',')
                .append("%.2f".format(Locale.US, s.netPowerKw)).append(',')
                .append("%.2f".format(Locale.US, s.rearMotorKw)).append(',')
                .append("%.2f".format(Locale.US, s.frontMotorKw)).append(',')
                .append("%.1f".format(Locale.US, s.usableSocPct)).append(',')
                .append("%.1f".format(Locale.US, s.packVoltageV)).append(',')
                .append("%.1f".format(Locale.US, s.packCurrentA)).append(',')
                .append("%.1f".format(Locale.US, s.cellDeltaMv)).append(',')
                .append("%.1f".format(Locale.US, s.statorTempC)).append(',')
                .append("%.1f".format(Locale.US, s.coolantBatteryInletC)).append(',')
                .append("%.3f".format(Locale.US, s.lateralG)).append(',')
                .append("%.3f".format(Locale.US, s.longitudinalG)).append(',')
                .append("%.1f".format(Locale.US, s.brakePressureBar))
                .appendLine()
        }

        file.writeText(sb.toString())
        return file
    }

    fun exportFullBundleToJson(
        context: Context,
        session: TelemetrySessionEntity?,
        samples: List<TelemetrySampleEntity>,
        dashboards: List<DashboardPresetEntity>,
        alerts: List<VehicleAlertEntity>
    ): File {
        val dir = ensureExportDir(context)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "s3xy_cockpit_export_$stamp.json")

        val root = JSONObject()
        root.put("vehicle", "2023 Tesla Model 3 (S3XY_OBD)")
        root.put("exportedAtMs", System.currentTimeMillis())

        if (session != null) {
            val sessionObj = JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("sourceMode", session.sourceMode)
                put("startTimeMs", session.startTimeMs)
                put("endTimeMs", session.endTimeMs)
                put("sampleCount", session.sampleCount)
                put("maxSpeedKph", session.maxSpeedKph.toDouble())
                put("peakPowerKw", session.peakPowerKw.toDouble())
                put("maxRegenKw", session.maxRegenKw.toDouble())
                put("minSocPct", session.minSocPct.toDouble())
                put("maxSocPct", session.maxSocPct.toDouble())
                put("maxCellDeltaMv", session.maxCellDeltaMv.toDouble())
                put("maxStatorTempC", session.maxStatorTempC.toDouble())
            }
            val sampleArray = JSONArray()
            samples.forEach { s ->
                sampleArray.put(
                    JSONObject().apply {
                        put("elapsedMs", s.elapsedMs)
                        put("speedKph", s.speedKph.toDouble())
                        put("netPowerKw", s.netPowerKw.toDouble())
                        put("usableSocPct", s.usableSocPct.toDouble())
                        put("packVoltageV", s.packVoltageV.toDouble())
                        put("packCurrentA", s.packCurrentA.toDouble())
                        put("cellDeltaMv", s.cellDeltaMv.toDouble())
                        put("statorTempC", s.statorTempC.toDouble())
                        put("lateralG", s.lateralG.toDouble())
                        put("longitudinalG", s.longitudinalG.toDouble())
                    }
                )
            }
            sessionObj.put("samples", sampleArray)
            root.put("telemetrySession", sessionObj)
        }

        val dashArray = JSONArray()
        dashboards.forEach { d ->
            dashArray.put(
                JSONObject().apply {
                    put("name", d.name)
                    put("subtitle", d.subtitle)
                    put("categoryTag", d.categoryTag)
                    put("widgets", JSONArray(d.widgetsJson))
                }
            )
        }
        root.put("dashboards", dashArray)

        val alertArray = JSONArray()
        alerts.forEach { a ->
            alertArray.put(
                JSONObject().apply {
                    put("alertCode", a.alertCode)
                    put("subsystem", a.subsystem)
                    put("severity", a.severity)
                    put("title", a.title)
                    put("description", a.description)
                    put("recommendedAction", a.recommendedAction)
                    put("rawCanIdHex", a.rawCanIdHex)
                    put("rawPayloadHex", a.rawPayloadHex)
                    put("isActive", a.isActive)
                    put("isAcknowledged", a.isAcknowledged)
                    put("freezeFrame", JSONObject(a.freezeFrameJson))
                }
            )
        }
        root.put("decodedAlerts", alertArray)

        file.writeText(root.toString(2))
        return file
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "S3XY Cockpit Telemetry Export (${file.name})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Export S3XY Telemetry Log").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }
}
