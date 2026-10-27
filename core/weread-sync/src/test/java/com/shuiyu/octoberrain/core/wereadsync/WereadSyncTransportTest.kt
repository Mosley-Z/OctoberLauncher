package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WereadSyncTransportTest {
    @Test fun `automatic network failure prevents subsequent requests`() {
        var requests = 0
        val transport =
            WereadSyncTransport({ _, _, timeout ->
                requests++
                assertEquals(5000, timeout)
                WereadGatewayResult.Failure(WereadGatewayError.TRANSPORT, "offline")
            }, AutomaticSyncBudget({ true }, { 0L }))
        repeat(4) { assertNull(transport.request("/test", JsonObject(emptyMap()))) }
        assertEquals(1, requests)
        assertNotNull(transport.failureMessage)
    }

    @Test fun `background task sends no request and reports paused`() {
        var requests = 0
        val transport =
            WereadSyncTransport({ _, _, _ ->
                requests++
                WereadGatewayResult.Success(JsonObject(emptyMap()))
            }, AutomaticSyncBudget({ false }, { 0L }))
        assertNull(transport.request("/test", JsonObject(emptyMap())))
        assertEquals(0, requests)
        assertNotNull(transport.failureMessage)
    }

    @Test fun `manual transport retains regular timeout and no automatic count cap`() {
        var requests = 0
        val transport =
            WereadSyncTransport({ _, _, timeout ->
                requests++
                assertEquals(15000, timeout)
                WereadGatewayResult.Success(JsonObject(emptyMap()))
            })
        repeat(28) { assertNotNull(transport.request("/test", JsonObject(emptyMap()))) }
        assertEquals(28, requests)
        assertNull(transport.failureMessage)
    }
}
