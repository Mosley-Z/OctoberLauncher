package com.shuiyu.octoberrain.feature.content.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Missing credentials must fail before Android JSON or transport, rather than clear a saved shelf. */
class WereadShelfRefreshTest {
    @Test fun strictRefreshRejectsDisabledOrUnconfiguredIntegration() =
        runBlocking {
            val transport = FakeTransport { error("No request expected without configuration") }
            listOf(testClient(transport, key = ""), testClient(transport, enabled = false)).forEach { client ->
                val failure = runCatching { client.shelf(force = true, requireFresh = true) }.exceptionOrNull()
                assertTrue(failure is IllegalStateException)
            }
            assertTrue(transport.requests.isEmpty())
        }
}
