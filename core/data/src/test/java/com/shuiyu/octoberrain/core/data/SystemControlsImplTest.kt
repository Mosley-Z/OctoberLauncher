package com.shuiyu.octoberrain.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [SystemControlsImpl] 生命周期与降级行为的单元测试。
 *
 * 使用 [ContextWrapper] 子类记录注册与注销调用次数，验证构造期不触碰系统服务，
 * 以及启动与释放的对称性。
 */
class SystemControlsImplTest {
    /** 记录系统服务与接收器调用的假上下文。 */
    private class FakeContext : ContextWrapper(null) {
        var registerCalls = 0
        var stickyCalls = 0
        var unregisterCalls = 0
        var serviceCalls = 0

        override fun getApplicationContext(): Context = this

        override fun getSystemService(name: String): Any? {
            serviceCalls += 1
            return null
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
        ): Intent? {
            if (receiver == null) stickyCalls += 1 else registerCalls += 1
            return null
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            unregisterCalls += 1
        }
    }

    @Test
    fun `构造不注册接收器且不读取系统服务`() {
        val context = FakeContext()
        val controls = SystemControlsImpl(context)
        assertEquals(0, context.registerCalls)
        assertEquals(0, context.unregisterCalls)
        assertEquals(0, context.serviceCalls)
        assertFalse(controls.hasFrontLight())
    }

    @Test
    fun `启动注册一次且重复启动不重复注册`() {
        val context = FakeContext()
        val controls = SystemControlsImpl(context)
        controls.start()
        controls.start()
        assertEquals(1, context.registerCalls)
    }

    @Test
    fun `释放与启动对称`() {
        val context = FakeContext()
        val controls = SystemControlsImpl(context)
        controls.start()
        controls.dispose()
        assertEquals(1, context.registerCalls)
        assertEquals(1, context.unregisterCalls)
        controls.start()
        assertEquals(2, context.registerCalls)
    }

    @Test
    fun `无前光设备上设置前光不改变状态`() {
        val context = FakeContext()
        val controls = SystemControlsImpl(context)
        controls.start()
        controls.setFrontLight(8, 6)
        val state = controls.frontLight.value
        assertEquals(0, state.cold)
        assertEquals(0, state.warm)
        assertFalse(state.available)
    }

    @Test
    fun `全量刷新在服务缺失时保持未知状态`() {
        val context = FakeContext()
        val controls = SystemControlsImpl(context)
        controls.start()
        controls.refreshAll()
        val wifi = controls.wifi.value
        assertFalse(wifi.enabled)
        assertFalse(wifi.connected)
        assertEquals("", wifi.ssid)
        assertEquals(-100, wifi.rssi)
    }
}
