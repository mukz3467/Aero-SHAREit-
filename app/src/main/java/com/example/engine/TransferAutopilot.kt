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
            isPcPeer -> "PC bridge preview (file transfer pending)"
            isGroupSession -> "Sequential GroupDrop over local Wi-Fi"
            else -> "Local TCP transfer with SHA-256 verification"
        }

        // The current client uses a fixed 64 KiB buffer; recommendations must reflect reality.
        val chunkSizeKb = 64

        val headline = when {
            shouldLite -> "Battery-Smart Lite Mode Recommended (${batteryPct}%)"
            batchBytes > 500_000_000L -> "Large Transfer Detected (${StorageSmartManager.formatBytes(batchBytes)})"
            else -> "Optimal P2P Transfer Ready"
        }

        val details = when {
            shouldLite -> "Battery is below 20%. Consider Lite mode; automatic radio or animation throttling is not integrated yet."
            batchBytes > 500_000_000L -> "Large batch detected. Outgoing files are staged and streamed in 64 KiB chunks to reduce RAM use."
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
