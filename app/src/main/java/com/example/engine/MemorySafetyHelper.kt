package com.example.engine

import android.app.ActivityManager
import android.content.Context
import java.util.Locale

object MemorySafetyHelper {

    data class MemorySnapshot(
        val totalMemBytes: Long,
        val availMemBytes: Long,
        val isLowMemory: Boolean,
        val thresholdBytes: Long
    ) {
        val totalMemFormatted: String
            get() = formatBytes(totalMemBytes)

        val availMemFormatted: String
            get() = formatBytes(availMemBytes)
    }

    fun getMemorySnapshot(context: Context): MemorySnapshot {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        return MemorySnapshot(
            totalMemBytes = memInfo.totalMem,
            availMemBytes = memInfo.availMem,
            isLowMemory = memInfo.lowMemory,
            thresholdBytes = memInfo.threshold
        )
    }

    fun isMemoryRisky(modelSizeBytes: Long, context: Context): Boolean {
        val snapshot = getMemorySnapshot(context)
        // If the model file size exceeds 60% of currently available free RAM, or device is low on memory
        return snapshot.isLowMemory || modelSizeBytes > (snapshot.availMemBytes * 0.60)
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.1f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }
}
