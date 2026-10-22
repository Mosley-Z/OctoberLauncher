package com.shuiyu.octoberrain.core.data

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.LockConfig
import com.shuiyu.octoberrain.core.common.NotificationConfig
import com.shuiyu.octoberrain.core.common.OnboardingConfig
import com.shuiyu.octoberrain.core.common.TrayConfig
import com.shuiyu.octoberrain.core.common.WereadAchievementConfig
import com.shuiyu.octoberrain.core.common.WereadMigrationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 配置存储实现。
 *
 * 整份配置以单个 JSON 字符串保存，修改时先写入持久化层，确认成功后再发布内存状态，
 * 写入失败时保留上一份配置并通过 [writeError] 与 [onWriteError] 告知调用方，
 * 避免内存状态与磁盘内容长期不一致。
 *
 * 读取时先解析为 [JsonObject]，再按桌面、外观、托盘、锁屏、集成、引导、通知七个配置节
 * 分别解码：单个配置节损坏只回退该节默认值，其余配置仍可恢复，从而把配置损坏的影响限制在局部。
 * 解码完成且版本低于当前版本时执行 [ConfigMigration] 升级，升级结果立即回写持久化层，
 * 避免每次启动重复迁移；读到高于当前版本的配置时按可读字段使用，原版本与磁盘内容保持不变，
 * 防止降级安装静默改写高版本数据。
 *
 * @param persistence 配置持久化实现，生产环境使用 [SharedPreferencesConfigPersistence]。
 * @param onWriteError 写盘失败回调，可为 null；回调异常不会影响配置写入流程。
 */
class JsonConfigStore(
    private val persistence: ConfigPersistence,
    private val onWriteError: ((Throwable) -> Unit)? = null,
) : ConfigStore {
    /**
     * 供 Android 环境使用的构造入口。
     *
     * @param context 应用上下文，内部只取 applicationContext。
     */
    constructor(context: Context) : this(SharedPreferencesConfigPersistence(context))

    private val _writeError = MutableStateFlow<Throwable?>(null)

    /** 最近一次写盘失败的原因；写盘成功后清空为 null。 */
    val writeError: StateFlow<Throwable?> = _writeError.asStateFlow()

    private val _newerVersionDetected = MutableStateFlow(false)

    /** 读取时是否发现配置版本高于当前版本；该情况下不执行迁移，也不回写磁盘。 */
    val newerVersionDetected: StateFlow<Boolean> = _newerVersionDetected.asStateFlow()

    private val _state = MutableStateFlow(load())

    override val state: StateFlow<LauncherConfig> = _state.asStateFlow()

    /**
     * 以事务方式修改配置。
     *
     * 在同一把锁内读取快照、计算新值并写盘；仅当持久化确认成功后发布新状态，
     * 写盘失败时保留旧状态并记录错误。变换结果与当前配置相等时不写盘。
     *
     * @param transform 配置变换函数，必须为纯函数。
     */
    override fun edit(transform: (LauncherConfig) -> LauncherConfig) {
        synchronized(this) {
            val current = _state.value
            val next = transform(current)
            if (next == current) return
            val error = persist(next)
            if (error != null) {
                notifyWriteError(error, "配置写入失败，已保留上一份配置")
                return
            }
            _state.value = next
            if (_writeError.value != null) {
                _writeError.value = null
            }
        }
    }

    /**
     * 读取配置、逐节解码并执行迁移。
     *
     * 字符串缺失、整体不是 JSON、根节点不是对象时使用全默认配置；单节解析失败时保留
     * 其余配置并记录日志。迁移结果与磁盘版本不一致时立即回写，回写失败不影响本次运行。
     *
     * @return 当前版本或按可读字段还原的配置。
     */
    private fun load(): LauncherConfig {
        val raw = persistence.read() ?: return LauncherConfig()
        val defaults = LauncherConfig()
        val element: JsonElement =
            runCatching {
                AppJson.format.parseToJsonElement(raw)
            }.getOrElse {
                InkLog.w(TAG, "配置整体解析失败，使用默认配置", it)
                return LauncherConfig()
            }
        val root =
            element as? JsonObject ?: run {
                InkLog.w(TAG, "配置根节点不是 JSON 对象，使用默认配置")
                return LauncherConfig()
            }
        val version =
            (root[KEY_VERSION] as? JsonPrimitive)?.content?.toIntOrNull()
                ?: defaults.version
        val parsed = decodeSections(root, version, defaults)
        if (parsed.version > LauncherConfig.CURRENT_VERSION) {
            _newerVersionDetected.value = true
            InkLog.w(
                TAG,
                "配置版本 ${parsed.version} 高于当前版本 ${LauncherConfig.CURRENT_VERSION}，按可读字段加载并保留原文件",
            )
            return parsed
        }
        val migrated = ConfigMigration.migrate(parsed, hasShelfSource(root))
        if (migrated.version != parsed.version) {
            val error = persist(migrated)
            if (error != null) {
                notifyWriteError(error, "迁移结果回写失败，本次仍按迁移结果运行")
            }
        }
        return migrated
    }

    /**
     * 逐节解码配置，单节失败时回退到该类默认值。
     *
     * 独立成方法使 [load] 保持短小；新增配置节时只需在此处追加一行，
     * 并保持与 [LauncherConfig] 的构造参数一一对应。
     *
     * @param root 配置根对象
     * @param version 磁盘上的版本号
     * @param defaults 默认配置，用于取各节默认值
     * @return 解码后的配置
     */
    private fun decodeSections(
        root: JsonObject,
        version: Int,
        defaults: LauncherConfig,
    ): LauncherConfig =
        LauncherConfig(
            version = version,
            desktop = readSection(root, KEY_DESKTOP, DesktopConfig.serializer(), defaults.desktop),
            appearance = readSection(root, KEY_APPEARANCE, AppearanceConfig.serializer(), defaults.appearance),
            tray = readSection(root, KEY_TRAY, TrayConfig.serializer(), defaults.tray),
            lock = readSection(root, KEY_LOCK, LockConfig.serializer(), defaults.lock),
            integrations =
                readSection(root, KEY_INTEGRATIONS, IntegrationsConfig.serializer(), defaults.integrations),
            onboarding = readSection(root, KEY_ONBOARDING, OnboardingConfig.serializer(), defaults.onboarding),
            notifications =
                readSection(root, KEY_NOTIFICATIONS, NotificationConfig.serializer(), defaults.notifications),
            wereadMigration =
                readSection(
                    root,
                    KEY_WEREAD_MIGRATION,
                    WereadMigrationState.serializer(),
                    defaults.wereadMigration,
                ),
            wereadAchievement =
                readSection(
                    root,
                    KEY_WEREAD_ACHIEVEMENT,
                    WereadAchievementConfig.serializer(),
                    defaults.wereadAchievement,
                ),
        )

    /**
     * 把配置序列化并写入持久化层。
     *
     * @param config 待持久化的配置。
     * @return 写入成功返回 null，序列化或写入失败返回对应的异常。
     */
    private fun persist(config: LauncherConfig): Throwable? =
        runCatching {
            val encoded = AppJson.format.encodeToString(LauncherConfig.serializer(), config)
            check(persistence.write(encoded)) { "配置持久化未确认写入成功" }
        }.exceptionOrNull()

    /**
     * 记录写盘失败并通知调用方。
     *
     * @param error 写盘失败原因。
     * @param message 写入日志的说明文字。
     */
    private fun notifyWriteError(
        error: Throwable,
        message: String,
    ) {
        _writeError.value = error
        InkLog.e(TAG, message, error)
        runCatching { onWriteError?.invoke(error) }.onFailure {
            InkLog.w(TAG, "配置写入失败回调执行异常", it)
        }
    }

    /**
     * 解码单个配置节。
     *
     * @param root 配置根对象。
     * @param key 配置节字段名。
     * @param serializer 该配置节的序列化器。
     * @param default 配置节缺失或解析失败时使用的默认值。
     * @return 解析成功时返回解码结果，否则返回默认值。
     */
    private fun <T> readSection(
        root: JsonObject,
        key: String,
        serializer: KSerializer<T>,
        default: T,
    ): T {
        val element = root[key] ?: return default
        if (element is JsonNull) return default
        return runCatching {
            AppJson.format.decodeFromJsonElement(serializer, element)
        }.getOrElse {
            InkLog.w(TAG, "配置节 $key 解析失败，已回退默认值", it)
            default
        }
    }

    private companion object {
        const val TAG = "配置存储"
        const val KEY_VERSION = "version"
        const val KEY_DESKTOP = "desktop"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_TRAY = "tray"
        const val KEY_LOCK = "lock"
        const val KEY_INTEGRATIONS = "integrations"
        const val KEY_ONBOARDING = "onboarding"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_WEREAD_MIGRATION = "wereadMigration"
        const val KEY_WEREAD_ACHIEVEMENT = "wereadAchievement"
    }
}
