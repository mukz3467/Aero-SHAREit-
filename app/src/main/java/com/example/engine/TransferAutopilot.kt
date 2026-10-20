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
        isGroupSession: Boolean
    ): AutopilotRecommendation {
        val (batteryPct, isCharging) = getBatteryStatus(context)
        val shouldLite = batteryPct < 20 && !isCharging

        val band = if (shouldLite) {
            "2.4GHz Power-Saving Mode"
        } else {
            "5GHz Turbo Band (Ultra Low Latency)"
        }

        val mode = when {
            isPcPeer -> "Phone-to-PC WebShare Bridge (Port 8844)"
            isGroupSession -> "Live Group Room Multi-Cast"
            else -> "Direct Offline P2P Stream"
        }

        val chunkSizeKb = when {
            shouldLite -> 32
            batchBytes > 500_000_000L -> 128
            else -> 64
        }

        val headline = when {
            shouldLite -> "Battery-Smart Lite Mode Recommended (${batteryPct}%)"
            batchBytes > 500_000_000L -> "Large Transfer Detected (${StorageSmartManager.formatBytes(batchBytes)})"
            else -> "Optimal P2P Transfer Ready"
        }

        val details = when {
            shouldLite -> "Device battery is below 20%. Background beacon animations and high-power radio bursts are throttled."
            batchBytes > 500_000_000L -> "Using 128 KB chunk streams with real-time SHA-256 verification and offset resume protection."
            else -> "Zero cloud routing, 5GHz hardware link, and direct peer socket acceleration active."
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
