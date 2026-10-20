package com.example.model

data class GroupPeer(
    val id: String,
    val name: String,
    val model: String,
    val ipAddress: String,
    val isHost: Boolean = false,
    val progress: Float = 0f,
    val speedMbps: Float = 0f,
    val status: String = "Connected",
    val transferredBytes: Long = 0L
)

data class GroupRoomSession(
    val roomCode: String = "8829",
    val hostDeviceName: String = "Host Device",
    val hostIp: String = "192.168.49.1",
    val members: List<GroupPeer> = emptyList(),
    val isGroupDropActive: Boolean = false,
    val activeBatchTitle: String = "",
    val totalBatchBytes: Long = 0L
)
