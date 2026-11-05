package com.shuiyu.octoberrain.core.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import com.shuiyu.octoberrain.core.common.BatteryState
import com.shuiyu.octoberrain.core.common.BluetoothState
import com.shuiyu.octoberrain.core.common.MediaState
import com.shuiyu.octoberrain.core.common.SystemControls
import com.shuiyu.octoberrain.core.common.WifiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统能力实现：监听 WiFi、蓝牙、电池与媒体状态。
 *
 * 构造过程只初始化状态流，不注册接收器、不读取系统设置；调用 [start] 后才开始监听并刷新一次状态。
 */
class SystemControlsImpl(
    private val context: Context,
) : SystemControls {
    private val appContext = context.applicationContext

    /** 是否已完成启动，避免重复注册接收器。 */
    private var started = false

    private val _wifi = MutableStateFlow(WifiState(false, false, "", -100))
    override val wifi: StateFlow<WifiState> = _wifi.asStateFlow()

    private val _bluetooth = MutableStateFlow(BluetoothState(false, null))
    override val bluetooth: StateFlow<BluetoothState> = _bluetooth.asStateFlow()

    private val _media = MutableStateFlow(MediaState.IDLE)
    override val media: StateFlow<MediaState> = _media.asStateFlow()

    private val _battery = MutableStateFlow(BatteryState(100, false))
    override val battery: StateFlow<BatteryState> = _battery.asStateFlow()

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                receiverContext: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
                    WifiManager.WIFI_STATE_CHANGED_ACTION,
                    WifiManager.NETWORK_STATE_CHANGED_ACTION,
                    -> updateWifi()
                    BluetoothAdapter.ACTION_STATE_CHANGED -> updateBluetooth()
                    else -> Unit
                }
            }
        }

    /**
     * 启动监听：注册广播接收器并刷新一次状态。
     *
     * 由应用在服务装配完成后调用；重复调用不重复注册接收器。
     */
    fun start() {
        if (started) return
        started = true
        register()
        refreshAll()
    }

    /** 由通知监听服务写入媒体状态。 */
    fun updateMedia(state: MediaState) {
        _media.value = state
    }

    /**
     * 注销广播接收器，进程退出前调用。
     *
     * 与 [start] 对称：释放后再次调用 [start] 会重新注册接收器。
     */
    fun dispose() {
        runCatching { appContext.unregisterReceiver(receiver) }
        started = false
    }

    private fun register() {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
        runCatching { appContext.registerReceiver(receiver, filter) }
    }

    /** 全量刷新一次状态；系统服务读取失败时保留上次已知值或降级为未知状态。 */
    fun refreshAll() {
        updateWifi()
        updateBluetooth()
        val sticky =
            runCatching {
                appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
        if (sticky != null) updateBattery(sticky)
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (scale > 0) level * 100 / scale else level
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging =
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        _battery.value = BatteryState(percent.coerceIn(0, 100), charging)
    }

    /**
     * 刷新 WiFi 状态。
     *
     * 高版本系统对读取网络信息有限制，权限不足或系统服务异常时降级为关闭且未连接，
     * 避免异常沿状态刷新路径向上传播。
     */
    private fun updateWifi() {
        val state =
            runCatching {
                val manager = appContext.getSystemService(WifiManager::class.java)
                if (manager == null) {
                    null
                } else {
                    val enabled = manager.isWifiEnabled

                    @Suppress("DEPRECATION")
                    val info = manager.connectionInfo
                    val ssid = info?.ssid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" } ?: ""
                    WifiState(
                        enabled = enabled,
                        connected = enabled && ssid.isNotEmpty(),
                        ssid = ssid,
                        rssi = info?.rssi ?: -100,
                    )
                }
            }.getOrNull()
        _wifi.value = state ?: WifiState(false, false, "", -100)
    }

    private fun updateBluetooth() {
        val adapter = bluetoothAdapter()
        val enabled = adapter?.isEnabled == true
        val connectedName =
            runCatching {
                if (!enabled) return@runCatching null
                val connected = adapter?.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP)
                if (connected == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                    adapter?.bondedDevices?.firstOrNull()?.name
                } else {
                    null
                }
            }.getOrNull()
        _bluetooth.value = BluetoothState(enabled, connectedName)
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        runCatching { appContext.getSystemService(BluetoothManager::class.java)?.adapter }
            .getOrNull()
}
