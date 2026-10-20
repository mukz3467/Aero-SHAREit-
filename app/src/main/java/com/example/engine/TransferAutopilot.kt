package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class AutopilotRecommendation(
    val recommendedBand: String,
    val recommendedMode: String,
    val shouldEnableLiteMode: Boolean,
    val optimalChunkSizeKb: Int,
    val adviceHeadline: String,
    val adviceDetails: String
)

object TransferAutopilot {
    fun analyzeAndRecommend(
        context: Context,
        batchBytes: Long,
        isPcPeer: Boolean,
        isGroupSession: Boolean,
        isLiteMode: Boolean = false
    ): AutopilotRecommendation {
        val (batteryPct, isCharging) = getBatteryStatus(context)
        val shouldLite = batteryPct < 20 && !isCharging
        val band = "Local Wi-Fi (band managed by Android)"
        val mode = when {
            isPcPeer -> "PC bridge browser upload/download over local HTTP (unencrypted)"
            isGroupSession -> "Sequential GroupDrop over local Wi-Fi"
            else -> "Local TCP transfer with SHA-256 verification"
        }
        // The Lite Mode setting is wired into the sender's staging and socket buffers.
        val chunkSizeKb = if (isLiteMode) 16 else 64
        val headline = when {
            shouldLite && !isLiteMode -> "Battery-Smart Lite Mode Recommended (${batteryPct}%)"
            batchBytes > 500_000_000L -> "Large Transfer Detected (${StorageSmartManager.formatBytes(batchBytes)})"
            isLiteMode -> "Lite Mode Active · 16 KiB Transfer Buffers"
            else -> "Local Transfer Ready"
        }
        val details = when {
            shouldLite && !isLiteMode -> "Battery is below 20%. Lite Mode uses smaller 16 KiB sender buffers; Android still controls radio power and Wi-Fi band."
            isLiteMode -> "Lite Mode uses 16 KiB buffers for staging and socket streams to reduce per-operation memory use. This may reduce throughput and does not guarantee lower battery use."
            batchBytes > 500_000_000L -> "Large batch detected. Outgoing files are staged to disk and streamed in bounded chunks to reduce RAM use."
            else -> "Peers must share the same local Wi-Fi network. File contents are not encrypted yet; SHA-256 checks verify received files."
        }
        return AutopilotRecommendation(
            recommendedBand = band,
            recommendedMode = mode,
            shouldEnableLiteMode = shouldLite,
            optimalChunkSizeKb = chunkSizeKb,
            adviceHeadline = headline,
            adviceDetails = details
        )
    }

    private fun getBatteryStatus(context: Context): Pair<Int, Boolean> {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 85
            Pair(pct, isCharging)
        } catch (_: Exception) {
            Pair(85, false)
        }
    }
}
