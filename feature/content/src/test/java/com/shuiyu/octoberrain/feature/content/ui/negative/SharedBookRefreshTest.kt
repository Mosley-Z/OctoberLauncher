package com.shuiyu.octoberrain.feature.content.ui.negative

import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.common.WereadSyncHost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SharedBookRefreshTest {
    @Test fun usesBookRefreshAndLeavesSharedBookSelectionToHost() =
        runBlocking {
            val host = FakeHost()
            assertEquals(true to "saved", SharedBookRefresh.request(host))
            assertEquals(WereadRefreshRequest("book"), host.received)
            assertFalse(host.fullSyncCalled)
        }

    @Test fun rejectedAndMissingHostDoNotHangOrClaimSuccess() =
        runBlocking {
            val host = FakeHost(accept = false)
            assertFalse(SharedBookRefresh.request(host).first)
            assertFalse(SharedBookRefresh.request(null).first)
        }

    private class FakeHost(
        private val accept: Boolean = true,
    ) : WereadSyncHost {
        var received: WereadRefreshRequest? = null
        var fullSyncCalled = false

        override fun isRunning(): Boolean = !accept

        override fun statusText(): String = ""

        override fun syncNow(onFinished: (Boolean, String) -> Unit): Boolean {
            fullSyncCalled = true
            error("Must use book refresh")
        }

        override fun refresh(
            request: WereadRefreshRequest,
            onFinished: (Boolean, String) -> Unit,
        ): Boolean {
            received = request
            if (accept) onFinished(true, "saved")
            return accept
        }
    }
}
