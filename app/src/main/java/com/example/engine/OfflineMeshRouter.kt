package com.example.engine

/**
 * Platform-independent routing table for Aero's offline, multi-hop mesh layer.
 *
 * This class does not create radio links. A transport (for example Wi-Fi Direct)
 * must establish a direct link to each neighbor and exchange advertisements.
 */
class OfflineMeshRouter(
    private val nodeId: String,
    private val routeTtlMillis: Long = DEFAULT_ROUTE_TTL_MS,
    private val seenMessageTtlMillis: Long = DEFAULT_SEEN_TTL_MS,
    private val maxHops: Int = DEFAULT_MAX_HOPS
) {
    data class Route(
        val destinationId: String,
        val nextHopId: String,
        val hopCount: Int,
        val expiresAtMillis: Long
    )

    data class Advertisement(val destinationId: String, val hopCount: Int)

    data class RelayEnvelope(
        val messageId: String,
        val sourceId: String,
        val destinationId: String,
        val hopLimit: Int,
        val payload: ByteArray
    )

    private val directPeers = linkedSetOf<String>()
    private val routes = linkedMapOf<String, Route>()
    private val seenMessages = linkedMapOf<String, Long>()

    @Synchronized
    fun addDirectPeer(peerId: String, nowMillis: Long = System.currentTimeMillis()) {
        require(peerId.isNotBlank() && peerId != nodeId) { "Invalid peer id." }
        directPeers.add(peerId)
        routes[peerId] = Route(peerId, peerId, 1, nowMillis + routeTtlMillis)
    }

    @Synchronized
    fun removeDirectPeer(peerId: String) {
        directPeers.remove(peerId)
        routes.entries.removeAll { it.key == peerId || it.value.nextHopId == peerId }
    }

    /**
     * Learn a destination advertised by a directly connected neighbor.
     * Advertisements claiming this node as a destination are ignored to avoid loops.
     */
    @Synchronized
    fun learnAdvertisement(
        fromPeerId: String,
        advertisement: Advertisement,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (fromPeerId !in directPeers) return false
        if (advertisement.destinationId.isBlank() || advertisement.destinationId == nodeId) return false
        if (advertisement.destinationId == fromPeerId) return false
        if (advertisement.hopCount < 1 || advertisement.hopCount >= maxHops) return false

        val candidate = Route(
            destinationId = advertisement.destinationId,
            nextHopId = fromPeerId,
            hopCount = advertisement.hopCount + 1,
            expiresAtMillis = nowMillis + routeTtlMillis
        )
        val current = routes[advertisement.destinationId]
        val shouldReplace = current == null ||
            current.expiresAtMillis <= nowMillis ||
            candidate.hopCount < current.hopCount ||
            (candidate.hopCount == current.hopCount && candidate.expiresAtMillis > current.expiresAtMillis)

        if (shouldReplace) routes[advertisement.destinationId] = candidate
        return shouldReplace
    }

    @Synchronized
    fun routeFor(destinationId: String, nowMillis: Long = System.currentTimeMillis()): Route? {
        prune(nowMillis)
        if (destinationId == nodeId) return null
        return routes[destinationId]
    }

    /** Advertisements to send to a neighbor; never advertise a route back through that neighbor. */
    @Synchronized
    fun advertisementsFor(peerId: String, nowMillis: Long = System.currentTimeMillis()): List<Advertisement> {
        prune(nowMillis)
        return routes.values
            .asSequence()
            .filter { it.destinationId != peerId && it.nextHopId != peerId }
            .filter { it.hopCount < maxHops }
            .map { Advertisement(it.destinationId, it.hopCount) }
            .distinctBy { it.destinationId }
            .toList()
    }

    /**
     * Returns true only once per message ID during the deduplication window.
     * Callers must also check that hopLimit > 0 and that a route exists before relaying.
     */
    @Synchronized
    fun markMessageSeen(messageId: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (messageId.isBlank()) return false
        pruneSeen(nowMillis)
        if (seenMessages.containsKey(messageId)) return false
        seenMessages[messageId] = nowMillis + seenMessageTtlMillis
        while (seenMessages.size > MAX_SEEN_MESSAGES) {
            val oldest = seenMessages.entries.minByOrNull { it.value }?.key ?: break
            seenMessages.remove(oldest)
        }
        return true
    }

    fun prepareRelay(envelope: RelayEnvelope, nowMillis: Long = System.currentTimeMillis()): RelayEnvelope? {
        if (envelope.destinationId == nodeId || envelope.hopLimit <= 1) return null
        // Do not consume the deduplication slot until a viable route exists;
        // otherwise a transient route outage permanently blocks a retry.
        if (routeFor(envelope.destinationId, nowMillis) == null) return null
        if (!markMessageSeen(envelope.messageId, nowMillis)) return null
        return envelope.copy(hopLimit = envelope.hopLimit - 1)
    }

    @Synchronized
    private fun prune(nowMillis: Long) {
        routes.entries.removeAll { it.value.expiresAtMillis <= nowMillis }
        directPeers.forEach { peer ->
            if (peer !in routes) {
                routes[peer] = Route(peer, peer, 1, nowMillis + routeTtlMillis)
            }
        }
        pruneSeen(nowMillis)
    }

    private fun pruneSeen(nowMillis: Long) {
        seenMessages.entries.removeAll { it.value <= nowMillis }
    }

    companion object {
        const val DEFAULT_MAX_HOPS = 8
        const val DEFAULT_ROUTE_TTL_MS = 30_000L
        const val DEFAULT_SEEN_TTL_MS = 60_000L
        const val MAX_SEEN_MESSAGES = 2_048
    }
}
