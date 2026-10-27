package com.shuiyu.octoberrain.core.common

import android.content.Context
import android.graphics.Typeface
import android.view.View
import kotlinx.coroutines.flow.StateFlow

/**
 * 配置读写入口。
 *
 * state 为当前配置快照，edit 在最新快照上变换并持久化，
 * 变换函数必须是纯函数，避免读写相互干扰。
 */
interface ConfigStore {
    val state: StateFlow<LauncherConfig>

    /** 以事务方式修改配置并通知订阅者。 */
    fun edit(transform: (LauncherConfig) -> LauncherConfig)
}

/**
 * 应用目录入口。
 *
 * apps 为当前可见应用列表，refresh 在安装或卸载后重新枚举；
 * loadIcon 走内存与磁盘缓存，返回按目标尺寸解码的位图。
 */
interface AppCatalog {
    val apps: StateFlow<List<InstalledApp>>

    /** 重新枚举应用，应在包变化广播后调用。 */
    suspend fun refresh()

    /** 按包名加载图标，失败返回 null。 */
    suspend fun loadIcon(
        packageName: String,
        sizePx: Int,
    ): android.graphics.Bitmap?

    /** 加载内置特殊入口图标。 */
    suspend fun loadSpecialIcon(
        entry: SpecialEntry,
        sizePx: Int,
    ): android.graphics.Bitmap?

    /** 启动应用。 */
    fun launch(app: InstalledApp)

    /** 启动或打开内置入口。 */
    fun launchSpecial(entry: SpecialEntry)
}

/** 微信读书页面的分段，顺序与顶部页签一致。 */
enum class WereadSection {
    /** 总览：本月统计、书架与最近划线。 */
    OVERVIEW,

    /** 笔记：按书分组的划线。 */
    NOTES,

    /** 账单：月度阅读时长明细。 */
    BILL,

    /** 排行：书架阅读进度排行。 */
    RANK,
}

/** 微信读书统计周期，与数据层的请求模式一一对应。 */
enum class WereadStatsPeriod {
    /** 本周逐日统计。 */
    WEEKLY,

    /** 本月逐日统计。 */
    MONTHLY,

    /** 本年逐月统计。 */
    ANNUALLY,

    /** 累计统计。 */
    OVERALL,
}

/**
 * 单个统计分桶快照。
 *
 * @property startMillis 分桶起始时间戳，单位毫秒；无法定位日期时为 0
 * @property minutes 该分桶内的阅读与收听时长，单位分钟
 */
data class WereadBucketSnapshot(
    val startMillis: Long = 0L,
    val minutes: Int = 0,
)

/**
 * 微信读书周期统计快照，所有时长字段单位为分钟。
 *
 * @property period 统计周期
 * @property totalMinutes 周期总时长
 * @property readDays 有效阅读天数
 * @property dayAverageMinutes 自然日均时长
 * @property buckets 按时间升序排列的分桶列表
 */
data class WereadStatsSnapshot(
    val period: WereadStatsPeriod = WereadStatsPeriod.WEEKLY,
    val totalMinutes: Int = 0,
    val readDays: Int = 0,
    val dayAverageMinutes: Int = 0,
    val buckets: List<WereadBucketSnapshot> = emptyList(),
)

/**
 * 微信读书笔记快照。
 *
 * @property bookTitle 书名，缺失时为空字符串
 * @property chapterTitle 章节名，缺失时为空字符串
 * @property text 划线原文
 * @property thought 个人想法
 * @property createdAt 创建时间，单位毫秒
 */
data class WereadNoteSnapshot(
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val text: String = "",
    val thought: String = "",
    val createdAt: Long = 0L,
)

/**
 * 微信读书笔记分页快照。
 *
 * @property notes 当前页笔记，按创建时间降序排列
 * @property total 笔记本概览报告的总笔记数
 */
data class WereadNotesSnapshot(
    val notes: List<WereadNoteSnapshot> = emptyList(),
    val total: Int = 0,
)

/**
 * 微信读书月度账单快照。
 *
 * @property monthMinutes 本月阅读总时长，单位分钟
 * @property lastMonthMinutes 上月阅读总时长，单位分钟
 * @property days 本月逐日阅读分钟数，缺失日期为 0
 */
data class WereadBillSnapshot(
    val monthMinutes: Int = 0,
    val lastMonthMinutes: Int = 0,
    val days: List<Int> = emptyList(),
)

/**
 * 微信读书书架排行条目。
 *
 * @property title 书名
 * @property author 作者
 * @property progress 阅读进度，取值 0 到 1
 */
data class WereadRankEntry(
    val title: String = "",
    val author: String = "",
    val progress: Float = 0f,
)

/**
 * 阅读指纹快照：由累计统计与当月逐日数据生成指纹图案。
 *
 * @property totalMinutes 累计阅读时长，单位分钟
 * @property readDays 累计有效阅读天数
 * @property dayAverageMinutes 自然日均时长，单位分钟
 * @property days 当月逐日阅读分钟数
 */
data class WereadFingerprintSnapshot(
    val totalMinutes: Int = 0,
    val readDays: Int = 0,
    val dayAverageMinutes: Int = 0,
    val days: List<Int> = emptyList(),
)

/**
 * 微信读书阅读资产快照，用于负一屏资产模块与整体卡片。
 *
 * 所有时长字段单位为分钟；分项之和允许大于累计值，累计值以微信读书返回为准。
 *
 * @property bookMinutes 文字阅读时长
 * @property audioMinutes 有声书收听时长
 * @property videoMinutes 视频学习时长
 * @property totalMinutes 累计阅读时长
 * @property readDays 累计有效阅读天数
 */
data class WereadAssetsSnapshot(
    val bookMinutes: Int = 0,
    val audioMinutes: Int = 0,
    val videoMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val readDays: Int = 0,
)

/**
 * 微信读书待办条目，来源为微读侧 todo.json 迁移后的本地数据。
 *
 * @property id 条目唯一标识，缺失时由实现方按序号生成
 * @property text 待办正文
 * @property done 是否已完成
 * @property updatedAt 最近更新时间，单位毫秒
 */
data class WereadTodoItem(
    val id: String = "",
    val text: String = "",
    val done: Boolean = false,
    val updatedAt: Long = 0L,
)

/**
 * 微信读书数据入口，由 [ContentSource] 继承以保持既有调用点不变。
 *
 * 所有方法在主线程之外调用；未配置密钥、来源关闭或数据缺失时返回 null 或空集合，
 * 不抛出异常。
 */
interface WereadContent {
    /** 微信读书书架。 */
    suspend fun wereadShelf(limit: Int = 20): List<WereadShelfItem>

    /** 微信读书月度统计。 */
    suspend fun wereadMonth(): WereadMonth?

    /** 微信读书最新摘录。 */
    suspend fun wereadHighlights(limit: Int = 5): List<WereadHighlight>

    /**
     * 微信读书周期统计，未配置或无数据时返回 null。
     *
     * 参数 period 选择统计周期；月周期的分桶按当月逐日分钟数生成，起始时间戳为 0。
     */
    suspend fun wereadPeriod(period: WereadStatsPeriod = WereadStatsPeriod.WEEKLY): WereadStatsSnapshot? = null

    /**
     * 微信读书最新笔记，未配置或无数据时返回空快照。
     *
     * 参数 limit 为请求条数，取值范围 1 到 50。
     */
    suspend fun wereadNotes(limit: Int = 3): WereadNotesSnapshot = WereadNotesSnapshot()

    /** 微信读书月度账单，未配置或无数据时返回 null。 */
    suspend fun wereadBill(): WereadBillSnapshot? = null

    /** 微信读书书架阅读进度排行，按进度降序取前 [limit] 本。 */
    suspend fun wereadRank(limit: Int = 3): List<WereadRankEntry> = emptyList()

    /** 阅读指纹数据，未配置或累计数据缺失时返回 null。 */
    suspend fun wereadFingerprint(): WereadFingerprintSnapshot? = null

    /** 阅读资产数据，未配置或累计数据缺失时返回 null。 */
    suspend fun wereadAssets(): WereadAssetsSnapshot? = null

    /** 待办列表，未配置或无数据时返回空集合；已完成条目排在末尾。 */
    suspend fun wereadTodos(): List<WereadTodoItem> = emptyList()
}

/**
 * 内容数据入口，覆盖本地书架、微信读书、天气与文件。
 *
 * 微信读书读取能力由 [WereadContent] 提供；所有方法在主线程之外调用，实现内部
 * 自行处理缓存与失败。
 */
interface ContentSource : WereadContent {
    /** 数据持久化或显式失效后递增，用于可见页面刷新本地快照。 */
    val dataVersion: StateFlow<Long>? get() = null

    /** 当前阅读快照，无数据返回 null。 */
    suspend fun readingSnapshot(): ReadingSnapshot?

    /** 当前天气，无网络或未授权返回 null。 */
    suspend fun weather(): WeatherSnapshot?

    /** 书架条目，按最近加入排序。 */
    suspend fun bookshelf(limit: Int = 20): List<BookItem>

    /** 搜索书籍。 */
    suspend fun searchBooks(query: String): List<BookItem>

    /** 刷新全部缓存。 */
    suspend fun refreshAll()
}

/**
 * 系统能力入口，涵盖 WiFi、蓝牙、前光、媒体与电池。
 *
 * 状态以 StateFlow 暴露，切换动作异步执行并通过状态回流反映结果。
 */
interface SystemControls {
    val wifi: StateFlow<WifiState>
    val bluetooth: StateFlow<BluetoothState>
    val frontLight: StateFlow<FrontLightState>
    val media: StateFlow<MediaState>
    val battery: StateFlow<BatteryState>

    /** 查询是否具备蓝牙硬件。 */
    fun hasBluetooth(): Boolean

    /** 查询是否具备前光通道。 */
    fun hasFrontLight(): Boolean

    /** 设置 WiFi 开关。 */
    fun setWifi(enabled: Boolean)

    /** 设置蓝牙开关。 */
    fun setBluetooth(enabled: Boolean)

    /** 设置前光冷光档位，取值 0 到 10。 */
    fun setFrontLight(
        cold: Int,
        warm: Int,
    )

    /** 播放或暂停媒体。 */
    fun mediaPlayPause()

    /** 上一曲。 */
    fun mediaPrevious()

    /** 下一曲。 */
    fun mediaNext()

    /** 打开系统 WiFi 设置页。 */
    fun openWifiSettings()

    /** 打开系统蓝牙设置页。 */
    fun openBluetoothSettings()

    /** 打开系统显示设置页。 */
    fun openDisplaySettings()
}

/** 通知数据入口，由通知监听服务写入。 */
interface NotificationFeed {
    val items: StateFlow<List<NotificationItem>>

    /** 是否已获得通知读取权限。 */
    fun canRead(): Boolean

    /** 撤销指定通知。 */
    fun dismiss(key: String)

    /** 撤销全部通知。 */
    fun dismissAll()

    /** 跳转通知读取授权页。 */
    fun openAccessSettings()
}

/** 页面跳转目标。 */
sealed interface Destination {
    /** 桌面首页，作为导航栈根页面。 */
    data object Home : Destination

    /** 负一屏：从首页继续右滑进入，当前用于展示微信读书书架。 */
    data object Negative : Destination

    data object Settings : Destination

    /**
     * 系统设置分组页。
     *
     * 部分定制系统的设置首页存在排版缺陷，本页改为逐项列出可直接打开的
     * 系统设置子页，作为系统设置的稳定入口。
     */
    data object SystemSettings : Destination

    /**
     * 十雨桌面自身偏好。
     *
     * 设置首页以系统设置卡片为主，本页面承载外观、布局、托盘、锁屏、
     * 权限、内容与关于等桌面专属条目。
     */
    data object LauncherPrefs : Destination

    data object ProjectWidgets : Destination

    data object ProjectRefresh : Destination

    data object ProjectContent : Destination

    data object ProjectAbout : Destination

    data object Appearance : Destination

    data object DesktopPrefs : Destination

    data object TrayPrefs : Destination

    data object LockSettings : Destination

    data object Permissions : Destination

    data object About : Destination

    data object Update : Destination

    data object WidgetPicker : Destination

    data class PageWidgetPicker(
        val page: Int,
    ) : Destination

    data object IconPackPicker : Destination

    /**
     * 美化模式：左侧按桌面顺序列出应用，右侧浏览图片为应用换图标，
     */
    data object Beautify : Destination

    /**
     * 美化预览：按美化模式中的临时顺序与图标覆盖展示应用网格，
     * 确认后把顺序写入桌面配置。
     */
    data object BeautifyPreview : Destination

    /**
     * 单条目图标选择：展示图片缩略图，选中后写入该条目的图标覆盖。
     *
     * @param targetId 图标目标标识：应用为包名，特殊入口为 special:名称。
     */
    data class IconPicker(
        val targetId: String,
    ) : Destination

    data object FontPicker : Destination

    data object Bookshelf : Destination

    /**
     * 书架设置：书籍来源、微信读书密钥与缓存刷新。
     *
     * 设置首页把本页与桌面偏好排在前列，微信读书相关项从桌面偏好迁移至此。
     */
    data object BookshelfSettings : Destination

    data class Files(
        val path: String? = null,
    ) : Destination

    /** 文档阅读来源，图片继续由原查看器处理。 */
    data class DocumentReader(
        val ref: DocumentRef,
    ) : Destination

    /**
     * 图片查看器。
     *
     * 路径列表限定在同一目录内，左右滑动在列表内切换；[index] 为起始下标，
     * 越界时由查看器夹取。页面由文件模块提供。
     *
     * @param paths 同目录图片的绝对路径列表。
     * @param index 起始图片下标。
     */
    data class PhotoViewer(
        val paths: List<String>,
        val index: Int = 0,
    ) : Destination

    /**
     * 图片编辑。
     *
     * 由查看器或文件列表打开，编辑页负责裁剪、旋转、滤镜与另存；关闭后回到来源页面。
     * 页面由文件模块提供。
     *
     * @param path 源图片绝对路径。
     * @param source 入口来源标识，供编辑页记录返回路径，空值表示未指定。
     */
    data class PhotoEditor(
        val path: String,
        val source: String? = null,
    ) : Destination

    /**
     * 相册。
     *
     * 以时间线方式浏览设备图片，入口在文件页；页面由文件模块提供。
     */
    data object Album : Destination

    data object Weread : Destination

    /**
     * 带起始分段的微信读书页面。
     *
     * 入口来自书架页与负一屏的「笔记」「统计」操作，打开后选中对应页签。
     *
     * @property section 进入页面时选中的分段，默认总览
     */
    data class WereadTab(
        val section: WereadSection = WereadSection.OVERVIEW,
    ) : Destination

    data object Search : Destination

    data class AppInfo(
        val packageName: String,
    ) : Destination

    /** 配置备份与恢复页。 */
    data object Backup : Destination

    /** 本书页：单本书的阅读统计与笔记汇总，入口来自负一屏书架与排行。 */
    data object Book : Destination

    /** 本记页：单本书的划线集合，入口来自书架与划线卡片。 */
    data object Note : Destination

    /** 负一屏设置页：五模块开关、排序与墨单配置。 */
    data object NegativeSettings : Destination

    /** 待办页：阅读待办与编号，入口来自负一屏墨单卡片。 */
    data object Todo : Destination

    /** 微读墨记内置应用页：在设置内打开，页面结构与微读墨记应用一致。 */
    data object WereadApp : Destination

    /** 直达微读墨记中已有的接口密钥设置。 */
    data object WereadSettings : Destination
}

/**
 * 单例页面目标的展示标签。
 *
 * 键为页面目标，值为状态栏与占位页使用的中文名称；
 * 带参数的页面在 [displayLabel] 中单独处理。
 */
private val destinationLabels: Map<Destination, String> =
    mapOf(
        Destination.Home to "桌面",
        Destination.Settings to "设置",
        Destination.SystemSettings to "系统设置",
        Destination.LauncherPrefs to "桌面偏好",
        Destination.ProjectWidgets to "桌面组件",
        Destination.ProjectRefresh to "墨水屏刷新",
        Destination.ProjectContent to "内容、权限与备份",
        Destination.ProjectAbout to "关于十雨",
        Destination.Appearance to "外观",
        Destination.DesktopPrefs to "桌面设置",
        Destination.TrayPrefs to "状态栏与通知",
        Destination.LockSettings to "锁屏设置",
        Destination.Permissions to "权限",
        Destination.About to "关于",
        Destination.Update to "更新",
        Destination.WidgetPicker to "选择组件",
        Destination.IconPackPicker to "图标包",
        Destination.Beautify to "美化模式",
        Destination.BeautifyPreview to "预览",
        Destination.FontPicker to "字体",
        Destination.Bookshelf to "书架",
        Destination.BookshelfSettings to "书架设置",
        Destination.Weread to "微信读书",
        Destination.Search to "搜索",
        Destination.Negative to "负一屏",
        Destination.Backup to "备份与恢复",
        Destination.Book to "本书",
        Destination.Note to "本记",
        Destination.NegativeSettings to "负一屏设置",
        Destination.Todo to "待办",
        Destination.WereadApp to "微读墨记",
        Destination.WereadSettings to "微信读书设置",
    )

/**
 * 页面目标的展示标签。
 *
 * 返回值用于状态栏标题与占位页文案；同一目标在不同入口下保持同一名称，
 * 不同目标使用不同名称，避免状态栏出现重复或歧义文案。
 */
fun Destination.displayLabel(): String =
    when (this) {
        is Destination.IconPicker -> "更换图标"
        is Destination.Files -> "文件"
        is Destination.DocumentReader -> "文件阅读"
        is Destination.PhotoViewer -> "图片查看"
        is Destination.PhotoEditor -> "图片编辑"
        is Destination.Album -> "相册"
        is Destination.WereadTab -> "微信读书"
        is Destination.AppInfo -> "应用信息"
        is Destination.PageWidgetPicker -> "选择组件"
        else -> destinationLabels[this] ?: "页面"
    }

/** 路由入口，由宿主 Activity 实现。 */
interface Router {
    /** 打开内置页面。 */
    fun navigate(destination: Destination)

    /** 返回上一页，返回 false 表示已在根页面。 */
    fun back(): Boolean

    /** 启动应用。 */
    fun openApp(packageName: String)

    /** 打开外部链接。 */
    fun openUrl(url: String)

    /** 展示轻提示。 */
    fun toast(message: String)
}

/**
 * 二级页面契约。
 *
 * 页面由各自 feature 模块提供，宿主只负责创建与生命周期回调。
 */
interface Screen {
    /** 创建页面视图，仅在首次进入时调用。 */
    fun createView(context: Context): View

    /** 页面进入前台。 */
    fun onResume() {}

    /** 页面离开前台。 */
    fun onPause() {}

    /** 处理返回，返回 true 表示已消费。 */
    fun onBack(): Boolean = false

    /** 页面销毁，释放位图与监听。 */
    fun onDestroy() {}
}

/** 页面工厂，由各 feature 模块实现并在启动时注册。 */
interface ScreenProvider {
    /** 创建目标页面，无法处理返回 null。 */
    fun create(destination: Destination): Screen?
}

/** 页面工厂注册表。 */
object ScreenRegistry {
    private val providers = mutableListOf<ScreenProvider>()

    /** 注册工厂，重复注册同一实例会被忽略。 */
    fun register(provider: ScreenProvider) {
        if (!providers.contains(provider)) providers.add(provider)
    }

    /** 按注册顺序查找可处理的目标。 */
    fun create(destination: Destination): Screen? {
        for (provider in providers) {
            provider.create(destination)?.let { return it }
        }
        return null
    }
}

/**
 * 功能模块入口。
 *
 * 每个 feature 模块提供一个实现，在 Application 启动时注册页面、组件与服务；
 * 宿主通过类名反射加载，模块缺失时跳过，保证并行开发期间可构建。
 */
interface LauncherModule {
    /** 执行模块安装，可注册页面工厂与组件，或替换全局服务实现。 */
    fun install(context: Context)
}

/** 锁屏入口，由设置模块实现。 */
interface LockGate {
    /** 是否处于锁定状态。 */
    val isLocked: Boolean

    /** 是否已设置 PIN。 */
    fun hasPin(): Boolean

    /** 立即上锁。 */
    fun lock()

    /** 尝试解锁，成功返回 true。 */
    fun unlock(pin: String): Boolean

    /** 设置或更新 PIN。 */
    fun setPin(pin: String)

    /** 清除 PIN 并关闭锁屏。 */
    fun clearPin()

    /**
     * 创建锁屏覆盖视图，未启用锁屏返回 null。
     *
     * @param context 视图上下文
     * @param onEmergencyReset 长按删除键满十秒后的应急清除请求，由宿主弹出确认框并执行清除
     */
    fun createOverlay(
        context: Context,
        onEmergencyReset: () -> Unit = {},
    ): View?

    /** 执行一次屏幕关闭后的上锁决策。 */
    fun onScreenOff()
}

/** Existing desktop status-bar factory. */
interface TrayController {
    fun createStatusBar(context: Context): View
}

/** 整屏刷新入口，由宿主实现。 */
interface RefreshRequester {
    /** 执行黑白两次整屏闪烁。 */
    fun requestFullRefresh()

    /** 合并局部重绘。 */
    fun requestPartial()
}

/** 整屏刷新原因，用于按原因决定是否闪烁与延迟。 */
enum class RefreshReason {
    /** 用户主动点击刷新。 */
    MANUAL,

    /** 定时整屏刷新到点。 */
    INTERVAL,

    /** 从其他应用返回桌面。 */
    APP_EXIT,

    /** 外观配置变化。 */
    APPEARANCE,

    /** 内容数据更新。 */
    CONTENT,
}

/**
 * 刷新策略入口。
 *
 * 在 [RefreshRequester] 之上补充刷新原因、前后台切换与节流信息：
 * 宿主在 Activity 生命周期中调用 [onForegrounded] 与 [onBackgrounded]，
 * 由实现决定是否在返回桌面后触发整屏刷新；
 * [millisSinceFullRefresh] 供设置页展示距上次整刷的时间。
 */
interface RefreshPolicy : RefreshRequester {
    /** 按原因请求整屏刷新。 */
    fun requestFullRefresh(reason: RefreshReason)

    /** 回到前台时调用，用于判断是否需要补一次整屏刷新。 */
    fun onForegrounded()

    /** 离开前台时调用，记录离开时间。 */
    fun onBackgrounded()

    /** 距离上次整屏刷新的毫秒数，从未刷新返回 Long.MAX_VALUE。 */
    fun millisSinceFullRefresh(): Long
}

/** 配置备份结果。 */
data class BackupResult(
    val ok: Boolean,
    val message: String,
    val sections: List<String> = emptyList(),
)

/**
 * 配置备份入口。
 *
 * 导出把配置、主题令牌与内置外观项打包为 zip；
 * 导入按节恢复，某节解析失败时保留原值并在结果中说明。
 */
interface BackupService {
    /** 导出到指定 Uri，成功返回 true。 */
    suspend fun export(uri: android.net.Uri): Boolean

    /** 从指定 Uri 导入，返回逐节结果。 */
    suspend fun import(uri: android.net.Uri): BackupResult
}

/**
 * 微信读书数据迁移入口。
 *
 * 由应用层装配到 [AppServices.wereadImport]，设置页据此发起一次迁移请求；
 * 数据来源为已安装微读墨记的导出组件，迁移过程不依赖网络。
 */
interface WereadImporter {
    /**
     * 发起一次迁移。
     *
     * @param onFinished 迁移结束回调，参数为可展示的结果说明；宿主缺失时不回调。
     * @return 是否成功发起请求，导出组件缺失时返回 false。
     */
    fun requestImport(onFinished: (String) -> Unit): Boolean
}

/**
 * 微读墨记配置回写入口。
 *
 * 由应用层装配到 [AppServices.wereadPrefs]，负一屏设置与整体卡片修改墨单外观或分区顺序后
 * 调用一次，把当前配置推送给微读墨记，保持两侧一致。推送不阻塞界面，失败时仅记录结果。
 */
interface WereadPrefsWriter {
    /**
     * 把当前配置推送到微读墨记。
     *
     * @param onFinished 结果回调，参数为可展示的说明；导出组件缺失时不会回调。
     * @return 是否成功发起请求
     */
    fun push(onFinished: (String) -> Unit): Boolean
}

/**
 * 全局服务表。
 *
 * 由应用在启动早期按 config、catalog、system、notifications、content、router、lock、tray、refresh、
 * permissions、appearance、updates、fonts 的顺序装配，模块只依赖接口，不直接依赖实现类型。
 */
object AppServices {
    var reader: ReaderGateway? = null

    /** 最近阅读查询入口，由阅读器模块注册；未安装时为空。 */
    var recent: RecentGateway? = null
    lateinit var config: ConfigStore
    lateinit var catalog: AppCatalog
    lateinit var content: ContentSource
    lateinit var system: SystemControls
    lateinit var notifications: NotificationFeed
    lateinit var router: Router
    lateinit var lock: LockGate
    lateinit var tray: TrayController
    lateinit var refresh: RefreshPolicy
    lateinit var permissions: Permissions
    lateinit var appearance: AppearanceRepository
    lateinit var updates: UpdateService
    lateinit var fonts: FontProvider
    lateinit var picker: FilePicker
    lateinit var backup: BackupService
    lateinit var iconOverrides: IconOverrideStore
    lateinit var themes: ThemeRepository
    lateinit var layoutProvider: DesktopLayoutProvider
    lateinit var previewProvider: DesktopPreviewProvider

    /** 微信读书数据迁移入口，由应用层装配；为空表示当前构建未包含迁移能力。 */
    var wereadImport: WereadImporter? = null

    /** 微读墨记配置回写入口，由应用层装配；为空表示当前构建不含该能力。 */
    var wereadPrefs: WereadPrefsWriter? = null

    /** 二维码录密钥服务，由应用层装配；为空表示当前构建不含该入口。 */
    var wereadKeyEntry: WereadApiKeyEntry? = null

    /** 独立模式的数据同步入口，应用层装配，设置页与内容层共用。 */
    var wereadSync: WereadSyncHost? = null

    /** 待办数据入口，应用层装配；为空表示当前构建不含待办功能。 */
    var wereadTodos: WereadTodoHost? = null

    /** 检查必需服务是否已就绪。 */
    fun isReady(): Boolean =
        this::config.isInitialized &&
            this::catalog.isInitialized &&
            this::content.isInitialized &&
            this::system.isInitialized &&
            this::notifications.isInitialized &&
            this::router.isInitialized &&
            this::lock.isInitialized &&
            this::tray.isInitialized &&
            this::refresh.isInitialized &&
            this::permissions.isInitialized &&
            this::appearance.isInitialized &&
            this::updates.isInitialized &&
            this::fonts.isInitialized &&
            this::picker.isInitialized &&
            this::backup.isInitialized &&
            this::iconOverrides.isInitialized &&
            this::themes.isInitialized

    /** 路由是否已装配，模块安装早期可能尚未就绪。 */
    fun hasRouter(): Boolean = this::router.isInitialized

    /** 文件选择器是否已装配。 */
    fun hasPicker(): Boolean = this::picker.isInitialized

    /** 内容服务是否已装配。 */
    fun hasContent(): Boolean = this::content.isInitialized

    /** 托盘服务是否已装配。 */
    fun hasTray(): Boolean = this::tray.isInitialized

    /** 图标覆盖服务是否已装配。 */
    fun hasIconOverrides(): Boolean = this::iconOverrides.isInitialized

    /** 美化主题服务是否已装配。 */
    fun hasThemes(): Boolean = this::themes.isInitialized

    /** 桌面布局服务是否已装配，美化模式据此获取各页应用。 */
    fun hasLayoutProvider(): Boolean = this::layoutProvider.isInitialized

    /** 桌面预览服务是否已装配，预览页据此获取真实比例的桌面视图。 */
    fun hasPreviewProvider(): Boolean = this::previewProvider.isInitialized
}

/**
 * 桌面分页布局快照。
 *
 * 由 feature/home 按真实绘制公式计算并直接复用桌面条目顺序，
 * 美化模式通过该快照展示首页或指定页应用，避免布局算法在模块间重复。
 */
data class DesktopLayoutSnapshot(
    /** 宫格列数。 */
    val columns: Int,
    /** 首页应用行数。 */
    val firstPageRows: Int,
    /** 其余页面应用行数。 */
    val rowsPerPage: Int,
    /** 按页划分的条目，首页为下标 0。 */
    val pages: List<List<LauncherEntry>>,
    /** 首页可采用独立列数；旧调用默认沿用其他页列数。 */
    val firstPageColumns: Int = columns,
    val pageColumns: List<Int> = emptyList(),
    val pageRows: List<Int> = emptyList(),
)

/** 桌面布局入口，由 feature/home 实现。 */
interface DesktopLayoutProvider {
    /** 读取当前桌面分页快照，应用或配置变化后实时反映。 */
    fun snapshot(): DesktopLayoutSnapshot

    /** 以当前实际视口预计算配置，供设置限制容量；不得写配置。 */
    fun preview(config: LauncherConfig): DesktopLayoutSnapshot = snapshot()
}

/**
 * 桌面预览视图入口，由 feature/home 实现。
 *
 * 预览直接复用桌面绘制代码，按照当前屏幕像素尺寸完成测量与排布，
 * 调用方只需把返回视图缩放到可用区域，即可得到与真实桌面一致的比例。
 */
interface DesktopPreviewProvider {
    /**
     * 创建一页桌面的预览视图。
     *
     * 参数 pageIndex 为页码，从 0 开始；order 为预览使用的条目顺序，
     * 为空时沿用当前配置。返回视图不接收触摸，也不更新全局布局快照。
     */
    fun createPreviewView(
        context: Context,
        pageIndex: Int = 0,
        order: List<String>? = null,
    ): View?
}

/** 字体加载入口，由数据层实现并挂载到全局。 */
interface FontProvider {
    /** 应用名称字体，未设置返回 null。 */
    fun labelTypeface(): Typeface?

    /** 按钟面标识加载字体。 */
    fun clockTypeface(id: String): Typeface

    /** 已安装字体列表。 */
    fun fonts(): List<FontInfo>

    /** 从外部文件导入字体，成功返回新字体信息。 */
    suspend fun importFont(uri: android.net.Uri): FontInfo?
}

/** 字体条目。 */
data class FontInfo(
    val id: String,
    val label: String,
    val path: String,
) {
    companion object {
        /** 内置字体标识。 */
        val BUILTIN =
            listOf(
                FontInfo("default", "系统默认", ""),
                FontInfo("serif", "宋体", ""),
                FontInfo("mono", "等宽", ""),
                FontInfo("condensed", "窄体", ""),
                FontInfo("light", "细体", ""),
                FontInfo("bold", "粗体", ""),
                FontInfo("cursive", "手写", ""),
            )
    }
}

/** 图标包条目。 */
data class IconPackInfo(
    val id: String,
    val label: String,
    val path: String?,
)

/** 主题信息。 */
data class ThemeInfo(
    val id: String,
    val label: String,
    val description: String,
)

/**
 * 系统文件选择入口，由宿主 Activity 实现。
 *
 * 页面通过它与系统文件选择器交互，结果以 Uri 回调返回，取消或失败时回调 null。
 */
interface FilePicker {
    /** 选择既有文件，mimeTypes 为空表示任意类型，返回是否成功发起请求。 */
    fun pick(
        mimeTypes: Array<String>,
        onPicked: (android.net.Uri?) -> Unit,
    ): Boolean

    /** 选择保存位置，用于导出主题与字体，返回是否成功发起请求。 */
    fun save(
        fileName: String,
        mimeType: String,
        onPicked: (android.net.Uri?) -> Unit,
    ): Boolean

    /** 选择目录，用于书架与字体目录，返回是否成功发起请求。 */
    fun pickDirectory(onPicked: (android.net.Uri?) -> Unit): Boolean
}

/** 可选按键接收者，页面实现后由宿主转发音量键。 */
interface VolumeKeySink {
    /** 处理音量键，返回 true 表示已消费。 */
    fun onVolumeKey(up: Boolean): Boolean
}

/** 设置页需要的权限状态入口。 */
interface Permissions {
    /** 采集全部权限状态。 */
    fun snapshot(): Map<String, Boolean>

    /** 打开通知监听设置。 */
    fun openNotificationListenerSettings()

    /** 打开无障碍设置。 */
    fun openAccessibilitySettings()

    /** 打开电池优化设置。 */
    fun openBatterySettings()

    /** 打开本应用设置页。 */
    fun openAppSettings()

    /** 打开存储管理设置。 */
    fun openStorageSettings()
}

/**
 * 外观与主题资源入口，由数据层实现。
 *
 * 桌面壁纸与锁屏壁纸的读取、设置、清除成对声明，是拆分方法的直接原因；
 * 接口聚合的是同一份外观状态，拆成多个接口会强迫调用方同时持有多个服务引用。
 */
@Suppress("TooManyFunctions")
interface AppearanceRepository {
    /** 从 Uri 设置桌面壁纸，成功返回 true。 */
    suspend fun setWallpaper(uri: android.net.Uri): Boolean

    /** 清除桌面壁纸恢复纯白。 */
    fun clearWallpaper()

    /**
     * 按目标尺寸加载桌面壁纸，无壁纸返回 null。
     *
     * 参数 quantize 为量化档位，0 关闭、1、2、3 分别对应二级灰、四级灰、八级灰；
     * 默认值 0 保持既有调用方的行为。
     */
    fun loadWallpaper(
        width: Int,
        height: Int,
        quantize: Int = 0,
    ): android.graphics.Bitmap?

    /** 从 Uri 设置锁屏壁纸，成功返回 true。 */
    suspend fun setLockWallpaper(uri: android.net.Uri): Boolean

    /** 清除锁屏壁纸，锁屏恢复纯纸色。 */
    fun clearLockWallpaper()

    /** 按目标尺寸加载锁屏壁纸，无壁纸返回 null。 */
    fun loadLockWallpaper(
        width: Int,
        height: Int,
    ): android.graphics.Bitmap?

    /** 导出主题包到 Uri。 */
    suspend fun exportTheme(uri: android.net.Uri): Boolean

    /** 从 Uri 导入主题包。 */
    suspend fun importTheme(uri: android.net.Uri): Boolean

    /** 可用图标包。 */
    fun iconPacks(): List<IconPackInfo>

    /** 内置主题列表。 */
    fun themes(): List<ThemeInfo>

    /** 应用内置主题。 */
    fun applyTheme(id: String)

    /**
     * 从当前图标包取应用图标。
     *
     * 参数 packageName 为应用包名；sizePx 为目标边长。
     * 未启用图标包或图标包未覆盖该应用时返回 null，由调用方回退系统图标。
     */
    suspend fun loadPackIcon(
        packageName: String,
        sizePx: Int,
    ): android.graphics.Bitmap?

    /** 当前图标包是否可用，供设置页展示状态。 */
    fun hasIconPack(): Boolean
}

/**
 * 单应用图标覆盖入口，由数据层实现。
 *
 * 美化模式与长按菜单把用户选择的图片复制到应用私有目录并登记到外观配置，
 * 图标加载时优先使用覆盖图片；不修改原图片文件。
 */
interface IconOverrideStore {
    /** 当前全部覆盖，键为包名，值为本地图片绝对路径。 */
    fun overrides(): Map<String, String>

    /**
     * 覆盖变更计数。
     *
     * 每次设置、清除或整体清空覆盖时递增，桌面等展示方据此清空图标缓存并重绘。
     */
    val version: StateFlow<Int>

    /** 从 Uri 为指定应用设置图标，成功返回 true。 */
    suspend fun setOverride(
        packageName: String,
        uri: android.net.Uri,
    ): Boolean

    /** 切换透明状态；启用时移除图片覆盖。 */
    fun setTransparent(
        targetId: String,
        enabled: Boolean,
    )

    /** 清除指定应用的覆盖图标。 */
    fun clearOverride(packageName: String)

    /** 清除全部覆盖，恢复系统图标。 */
    fun clearAll()

    /** 读取覆盖图片并缩放到边长 sizePx，缺失或解码失败返回 null。 */
    fun loadOverride(
        packageName: String,
        sizePx: Int,
    ): android.graphics.Bitmap?
}

/**
 * 美化主题入口，由数据层实现。
 *
 * 主题保存桌面顺序与图标覆盖映射；应用主题时写入配置，目标应用已卸载时
 * 跳过其图标覆盖，顺序条目自然失效，不影响其余应用。
 */
interface ThemeRepository {
    /** 已保存主题，按创建时间倒序。 */
    fun themes(): List<BeautifyTheme>

    /** 保存当前顺序、布局与图标映射为主题；同名主题覆盖，成功返回保存结果。 */
    suspend fun saveTheme(
        name: String,
        scope: ThemeScope = ThemeScope.ALL,
    ): BeautifyTheme?

    /** 应用主题，成功返回 true。 */
    suspend fun applyTheme(id: String): Boolean

    /** 删除主题。 */
    suspend fun deleteTheme(id: String)

    /** 清空桌面个性化，恢复默认排布与系统图标。 */
    suspend fun resetDesktop(): Boolean

    /** 导出主题为单文件，图片以 Base64 内嵌，便于备份与迁移。 */
    suspend fun exportTheme(
        id: String,
        uri: android.net.Uri,
    ): Boolean

    /** 从单文件导入主题，成功返回 true。 */
    suspend fun importTheme(uri: android.net.Uri): Boolean
}

/**
 * 负一屏模块。
 *
 * 模块自行创建视图并处理内部交互，宿主只负责容器、滚动与生命周期；
 * 新增模块无需修改桌面代码，注册后即可出现在负一屏。
 */
interface NegativeModule {
    /** 稳定标识，用于配置中保存启用顺序。 */
    val id: String

    /** 用户可见名称。 */
    val label: String

    /** 新增模块的默认启用状态，默认启用；用户开关优先于本值。 */
    val defaultEnabled: Boolean get() = true

    /** 同一负一屏内的排序权重，数值小的排在前面，默认一百。 */
    val orderHint: Int get() = 100

    /** 创建模块视图，返回 null 表示当前不可用。 */
    fun createView(
        context: Context,
        host: NegativeHost,
    ): View?

    /** 进入负一屏时调用。 */
    fun onResume()

    /** 离开负一屏时调用。 */
    fun onPause()

    /** 宿主销毁负一屏时调用，用于释放模块持有的资源，默认无操作。 */
    fun onDestroy() {}
}

/** 负一屏宿主能力。 */
interface NegativeHost {
    /** 负一屏所在上下文，供模块创建视图与解析资源。 */
    val context: Context

    /** 负一屏当前可用宽度，单位 dp，模块据此选择紧凑或宽松布局。 */
    val widthDp: Int

    /** 请求宿主重绘。 */
    fun requestRedraw()

    /** 打开内置页面。 */
    fun open(destination: Destination)
}

/** 负一屏模块注册表，模块在 install 时注册。 */
object NegativeModuleRegistry {
    private val modules = LinkedHashMap<String, NegativeModule>()

    /** 注册模块，重复 id 覆盖旧实现。 */
    fun register(module: NegativeModule) {
        modules[module.id] = module
    }

    /** 全部模块，按注册顺序返回。 */
    fun all(): List<NegativeModule> = modules.values.toList()

    /** 按 id 查找模块。 */
    fun get(id: String): NegativeModule? = modules[id]
}

/**
 * 更新检查与安装入口。
 */
interface UpdateService {
    /** 检查更新，无更新返回 null。 */
    suspend fun check(force: Boolean = false): UpdateInfo?

    /** 下载更新包，成功返回本地文件。 */
    suspend fun download(info: UpdateInfo): java.io.File?

    /** 触发系统安装器。 */
    fun install(file: java.io.File)

    /** 读取更新说明。 */
    suspend fun changelog(): List<ChangelogEntry>
}

/** 更新信息。 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    /** 相同签名、版本及摘要的备用下载地址，供国内镜像使用。 */
    val downloadUrls: List<String> = emptyList(),
)

/** 更新日志条目。 */
@kotlinx.serialization.Serializable
data class ChangelogEntry(
    val version: String,
    val date: String,
    val items: List<String>,
)
