package com.dilipkumarkv.localgguf.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

data class HardwareHealthSnapshot(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val batteryTempCelsius: Float,
    val thermalStatusText: String,
    val isThrottling: Boolean,
    val primaryAbi: String,
    val totalCores: Int
)

object HardwareMonitor {

    fun getSnapshot(context: Context): HardwareHealthSnapshot {
        // 1. Battery Information
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, batteryFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) ((level * 100) / scale) else 100

        val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTempCelsius = rawTemp / 10.0f

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // 2. Thermal Status
        var isThrottling = false
        val thermalText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val thermalStatus = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
            when (thermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal (Cool)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                PowerManager.THERMAL_STATUS_MODERATE -> {
                    isThrottling = true
                    "Moderate Heat (Throttling)"
                }
                PowerManager.THERMAL_STATUS_SEVERE -> {
                    isThrottling = true
                    "Severe Throttling"
                }
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> {
                    isThrottling = true
                    "Critical Heat Alert"
                }
                else -> "Normal"
            }
        } else {
            // Fallback estimation based on battery temperature sensor
            when {
                batteryTempCelsius >= 45.0f -> {
                    isThrottling = true
                    "Severe Throttling (~${batteryTempCelsius.toInt()}°C)"
                }
                batteryTempCelsius >= 40.0f -> {
                    isThrottling = true
                    "Moderate Heat (~${batteryTempCelsius.toInt()}°C)"
                }
                batteryTempCelsius >= 36.0f -> "Light Warmth"
                else -> "Nominal (Cool)"
            }
        }

        // 3. System ABI & Core Count
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Generic"
        val totalCores = Runtime.getRuntime().availableProcessors()

        return HardwareHealthSnapshot(
            batteryLevel = batteryPercent,
            isCharging = isCharging,
            batteryTempCelsius = batteryTempCelsius,
            thermalStatusText = thermalText,
            isThrottling = isThrottling,
            primaryAbi = primaryAbi,
            totalCores = totalCores
        )
    }
}
