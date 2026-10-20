package com.example

import com.example.engine.OfflineMeshRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMeshRouterTest {
    @Test
    fun learnsMultiHopRouteThroughDirectNeighbor() {
        val router = OfflineMeshRouter("B")
        router.addDirectPeer("A", nowMillis = 1_000)
        val learned = router.learnAdvertisement(
            fromPeerId = "A",
            advertisement = OfflineMeshRouter.Advertisement("C", 1),
            nowMillis = 1_100
        )

        assertTrue(learned)
        val route = router.routeFor("C", nowMillis = 1_200)
        assertEquals("A", route?.nextHopId)
        assertEquals(2, route?.hopCount)
    }

    @Test
    fun rejectsAdvertisementFromUnknownPeerAndRouteToSelf() {
        val router = OfflineMeshRouter("B")
        assertFalse(
            router.learnAdvertisement(
                "Unknown",
                OfflineMeshRouter.Advertisement("C", 1),
                nowMillis = 1_000
            )
        )
        router.addDirectPeer("A", nowMillis = 1_000)
        assertFalse(
            router.learnAdvertisement(
                "A",
                OfflineMeshRouter.Advertisement("B", 1),
                nowMillis = 1_100
            )
        )
        assertNull(router.routeFor("C", nowMillis = 1_200))
    }

    @Test
    fun routeExpiresAfterTtl() {
        val router = OfflineMeshRouter("B", routeTtlMillis = 500)
        router.addDirectPeer("A", nowMillis = 1_000)
        router.learnAdvertisement(
            "A",
            OfflineMeshRouter.Advertisement("C", 1),
            nowMillis = 1_100
        )
        assertTrue(router.routeFor("C", nowMillis = 1_200) != null)
        assertNull(router.routeFor("C", nowMillis = 1_700))
    }

    @Test
    fun relayDecrementsHopLimitAndRejectsDuplicates() {
        val router = OfflineMeshRouter("B")
        router.addDirectPeer("A", nowMillis = 1_000)
        router.learnAdvertisement(
            "A",
            OfflineMeshRouter.Advertisement("C", 1),
            nowMillis = 1_100
        )
        val packet = OfflineMeshRouter.RelayEnvelope(
            messageId = "message-1",
            sourceId = "A",
            destinationId = "C",
            hopLimit = 4,
            payload = byteArrayOf(1, 2, 3)
        )

        val relayed = router.prepareRelay(packet, nowMillis = 1_200)
        assertEquals(3, relayed?.hopLimit)
        assertNull(router.prepareRelay(packet, nowMillis = 1_201))
    }

    @Test
    fun relayStopsAtDestinationOrHopLimit() {
        val router = OfflineMeshRouter("B")
        router.addDirectPeer("A", nowMillis = 1_000)
        router.learnAdvertisement(
            "A",
            OfflineMeshRouter.Advertisement("C", 1),
            nowMillis = 1_100
        )
        val atDestination = OfflineMeshRouter.RelayEnvelope(
            "message-destination", "A", "B", 4, byteArrayOf(1)
        )
        val noHopsLeft = OfflineMeshRouter.RelayEnvelope(
            "message-hop-limit", "A", "C", 1, byteArrayOf(1)
        )
        assertNull(router.prepareRelay(atDestination, nowMillis = 1_200))
        assertNull(router.prepareRelay(noHopsLeft, nowMillis = 1_200))
    }
}
