package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.NotificationConfig
import com.shuiyu.octoberrain.core.common.NotificationFeed
import com.shuiyu.octoberrain.core.common.NotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 通知中心：聚合通知监听服务推送的条目，供托盘与桌面角标使用。
 *
 * 条目按时间倒序保留有限数量，界面只读状态流；撤销操作通过服务处理器下发，
 * 服务未连接时仅移除本地副本。写入阶段完成汇总过滤、会话归并与应用名解析，
 * 读取阶段按 [NotificationConfig] 完成应用折叠与角标计数。
 */
object NotificationCenter : NotificationFeed {
    private var appContext: Context? = null

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())

    override val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    /** 服务侧的撤销处理器，由监听服务安装。 */
    private var dismisser: ((String) -> Unit)? = null

    /** 服务侧的全量撤销处理器。 */
    private var dismissAllHandler: (() -> Unit)? = null

    /** 应用显示名缓存，按包名索引；安装包变化时保留旧值。 */
    private val labelCache = mutableMapOf<String, String>()

    /** 在 Application 启动时调用，注入上下文。 */
    fun install(context: Context) {
        appContext = context.applicationContext
    }

    override fun canRead(): Boolean {
        val context = appContext ?: return false
        val enabled =
            runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners",
                )
            }.getOrNull() ?: return false
        return enabled.contains(context.packageName)
    }

    override fun dismiss(key: String) {
        dismisser?.invoke(key)
        _items.update { current -> current.filterNot { it.key == key } }
    }

    override fun dismissAll() {
        dismissAllHandler?.invoke()
        _items.update { emptyList() }
    }

    /**
     * 撤销指定应用在当前列表内的全部通知，用于折叠条目的整组撤销。
     *
     * 参数 packageName 为通知所属应用包名；逐条下发系统撤销后移除本地副本。
     */
    fun dismissGroup(packageName: String) {
        val keys = _items.value.filter { it.packageName == packageName }.map { it.key }
        keys.forEach { key -> dismisser?.invoke(key) }
        _items.update { current -> current.filterNot { it.packageName == packageName } }
    }

    override fun openAccessSettings() {
        val context = appContext ?: return
        runCatching {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** 安装服务侧处理器，服务销毁时传 null 解除。 */
    fun installHandlers(
        dismissOne: ((String) -> Unit)?,
        dismissEverything: (() -> Unit)?,
    ) {
        dismisser = dismissOne
        dismissAllHandler = dismissEverything
    }

    /**
     * 推送或更新一条通知。
     *
     * 参数 item 为服务解析后的条目。配置开启 stripSummary 时丢弃汇总类通知；
     * 条目缺少应用名时按包名解析补齐；配置开启 mergeConversation 时并入
     * 同一 groupKey 的已有条目，数量累加并保留较新的标题与正文。
     */
    fun push(item: NotificationItem) {
        val config = currentConfig()
        if (config.stripSummary && item.isSummary) return
        val prepared = item.copy(appLabel = item.appLabel.ifEmpty { appLabelFor(item.packageName) })
        // 基于当前值原子更新：服务线程连续推送时避免读改写覆盖已写入的条目。
        _items.update { current ->
            val remaining = current.filterNot { it.key == prepared.key }
            val next =
                if (config.mergeConversation && prepared.groupKey.isNotEmpty()) {
                    val index = remaining.indexOfFirst { it.groupKey == prepared.groupKey }
                    if (index < 0) {
                        listOf(prepared) + remaining
                    } else {
                        remaining.toMutableList().apply {
                            this[index] = mergePair(this[index], prepared)
                        }
                    }
                } else {
                    listOf(prepared) + remaining
                }
            next.sortedByDescending { it.postedAt }.take(MAX_ITEMS)
        }
    }

    /** 移除指定通知。 */
    fun remove(key: String) {
        _items.update { current -> current.filterNot { it.key == key } }
    }

    /**
     * 全量替换通知列表。
     *
     * 参数 items 为系统当前挂起通知的解析结果；统一补齐应用名，
     * 按配置丢弃汇总类通知并合并同一会话的消息，按投递时间降序保留。
     */
    fun replaceAll(items: List<NotificationItem>) {
        val config = currentConfig()
        val prepared =
            items
                .filterNot { config.stripSummary && it.isSummary }
                .map { it.copy(appLabel = it.appLabel.ifEmpty { appLabelFor(it.packageName) }) }
                .sortedByDescending { it.postedAt }
        val merged =
            if (config.mergeConversation) {
                mergeByConversation(prepared)
            } else {
                prepared
            }
        _items.update { merged.take(MAX_ITEMS) }
    }

    /**
     * 计算界面可见的通知列表。
     *
     * 参数 config 为通知展示配置，省略时读取当前配置；
     * 返回经过汇总过滤、应用折叠并按投递时间降序排列的条目，
     * 折叠条目带 isGroup 标记与组内数量，正文为条数说明。
     */
    fun visibleItems(config: NotificationConfig = currentConfig()): List<NotificationItem> =
        computeVisible(_items.value, config)

    /**
     * 计算桌面角标数量。
     *
     * 参数 config 为通知展示配置，省略时读取当前配置；
     * 未启用角标返回 0，否则返回可见条目的数量合计，调用方按 badgeMax 控制显示。
     */
    fun badgeCount(config: NotificationConfig = currentConfig()): Int = countVisible(_items.value, config)

    /**
     * 生成角标文本。
     *
     * 参数 config 为通知展示配置，省略时读取当前配置；
     * 数量为零或未启用角标时返回空串，超过 badgeMax 时以加号表示溢出。
     */
    fun badgeText(config: NotificationConfig = currentConfig()): String {
        val count = badgeCount(config)
        if (count <= 0) return ""
        val max = config.badgeMax.coerceAtLeast(1)
        return if (count > max) "$max+" else count.toString()
    }

    /**
     * 解析通知所属应用的显示名。
     *
     * 参数 packageName 为应用包名；结果按包名缓存，查询失败时回退为包名本身。
     */
    fun appLabelFor(packageName: String): String {
        labelCache[packageName]?.let { return it }
        val context = appContext
        if (context == null) return packageName
        val label =
            runCatching {
                val manager = context.packageManager
                val info = manager.getApplicationInfo(packageName, 0)
                manager.getApplicationLabel(info).toString()
            }.getOrDefault(packageName)
        labelCache[packageName] = label
        return label
    }

    /**
     * 按 groupKey 合并同一会话的消息。
     *
     * 参数 items 为通知条目列表；返回合并后的新列表，顺序与入参一致。
     * 同一 groupKey 的多条条目保留投递时间较新者的标题与正文，数量累加；
     * groupKey 为空的条目各自独立，不参与合并。
     */
    private fun mergeByConversation(items: List<NotificationItem>): List<NotificationItem> {
        val result = mutableListOf<NotificationItem>()
        for (item in items) {
            val index = result.indexOfFirst { it.groupKey.isNotEmpty() && it.groupKey == item.groupKey }
            if (index < 0) {
                result.add(item)
                continue
            }
            result[index] = mergePair(result[index], item)
        }
        return result
    }

    /**
     * 合并两条同会话通知。
     *
     * 参数 existing 为已存在的条目，incoming 为新到达的条目；
     * 返回保留投递时间较新者的标题与正文、数量为两者之和的条目。
     */
    private fun mergePair(
        existing: NotificationItem,
        incoming: NotificationItem,
    ): NotificationItem {
        val newest = if (incoming.postedAt >= existing.postedAt) incoming else existing
        return newest.copy(count = existing.count + incoming.count)
    }

    /**
     * 按应用折叠同一包名的多条通知。
     *
     * 参数 items 为通知条目列表；返回每个包名一条代表条目，位置取该应用
     * 最先出现的分组顺序。组内条数大于 1 时标记为折叠条目，正文替换为
     * 条数说明，数量取组内各条数量之和；仅有一条时保留原正文。
     */
    private fun foldByApplication(items: List<NotificationItem>): List<NotificationItem> {
        val groups = LinkedHashMap<String, MutableList<NotificationItem>>()
        for (item in items) {
            groups.getOrPut(item.packageName) { mutableListOf() }.add(item)
        }
        return groups.values.map(::foldGroup)
    }

    /** 构造单条应用分组的代表条目，组内条目按投递时间择新。 */
    private fun foldGroup(group: List<NotificationItem>): NotificationItem {
        val newest = group.maxByOrNull { it.postedAt } ?: group.first()
        val total = group.sumOf { it.count.coerceAtLeast(1) }
        return if (group.size <= 1) {
            newest.copy(count = total)
        } else {
            newest.copy(
                isGroup = true,
                count = total,
                text = "$total 条通知",
                conversationTitle = null,
            )
        }
    }

    /**
     * 计算界面可见的通知列表。
     *
     * 参数 items 为通知中心内的原始条目，config 为通知展示配置；
     * 返回依次经过汇总过滤、应用折叠并按投递时间降序排列的列表。
     */
    private fun computeVisible(
        items: List<NotificationItem>,
        config: NotificationConfig,
    ): List<NotificationItem> {
        val filtered = if (config.stripSummary) items.filterNot { it.isSummary } else items
        val folded = if (config.foldByApp) foldByApplication(filtered) else filtered
        return folded.sortedByDescending { it.postedAt }
    }

    /**
     * 计算角标数量。
     *
     * 参数 items 为通知中心内的原始条目，config 为通知展示配置；
     * 未启用角标时返回 0；否则对可见条目按数量求和，折叠条目按组内条数计入。
     * 返回值是真实数量，显示上限由配置项 badgeMax 在展示层处理。
     */
    private fun countVisible(
        items: List<NotificationItem>,
        config: NotificationConfig,
    ): Int {
        if (!config.badgeEnabled) return 0
        return computeVisible(items, config).sumOf { it.count.coerceAtLeast(1) }
    }

    /** 读取当前通知展示配置，服务装配早期配置尚未就绪时使用默认值。 */
    private fun currentConfig(): NotificationConfig =
        runCatching { AppServices.config.state.value.notifications }.getOrNull()
            ?: NotificationConfig()

    private const val MAX_ITEMS = 20
}
