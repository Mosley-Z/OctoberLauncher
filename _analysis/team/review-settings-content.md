# feature/settings 与 feature/content 只读复核报告

复核日期：2026-10-19

复核对象：feature/settings（24 个 Kotlin 文件）、feature/content（22 个文件），以及被直接引用的 core 模块文件与 AndroidManifest。

前置条件：debug 构建已通过（BUILD SUCCESSFUL），本次复核不参与构建。

方法：以静态阅读为主，辅以 grep 核对调用点数量、docs 与 Manifest 交叉核对；未在设备上运行，涉及运行时行为的判断均标注「待确认」。

## 一 复核范围与阅读深度

| 范围 | 文件 | 方式 |
| --- | --- | --- |
| settings | BeautifyScreen、PreviewScreen、DesktopPrefsScreen、SettingsUi、SettingsBaseScreen、SettingsHomeScreen、BackupScreen、BookshelfSettingsScreen、ImageDirectoryStore、BeautifyPreviewState、SettingsModule | 全文阅读 |
| settings | AppearanceScreen、LauncherPrefsScreen、TrayPrefsScreen、UpdateScreen、SettingsSystemScreen、LockSettingsScreen、PermissionsScreen、AboutScreen、ThemePickerScreen、FontPickerScreen、IconPackPickerScreen、IconPickerScreen、AppPackageInfo | 接口与调用点抽查 |
| content | WereadClient、ContentSourceImpl、FileLibrary、BookshelfRepository、KoreaderRepository、EpubCover、CoverResolver、ContentPaths、UsageRepository、RecentsRepository、ContentScreen、FilesScreen、RecentsScreen | 全文阅读 |
| content | BookshelfScreen、BookshelfNegativeModule、WereadScreen、KoreaderScreen、SearchScreen、ContentUi、ContentModule、ContentRegistry、WeatherClient | 结构抽查 |
| core 引用 | Contracts、Models、JsonConfigStore、ConfigMigration、BackupServiceImpl、UpdateServiceImpl、ThemeRepositoryImpl、IconOverrideStoreImpl、AndroidManifest | 全文或关键节 |

未覆盖：feature/home 的桌面分页实现、feature/tray 的托盘注入点。涉及分页一致性的判断以 core/common/Contracts.kt 的接口声明为限，相关条目附「待确认」。

## 二 发现清单

### F1 内置微信读书密钥编入安装包，并随配置与备份明文外传

级别：高

位置：core\common\src\main\java\com\shuiyu\octoberrain\core\common\Models.kt:10；同文件 :276；core\data\src\main\java\com\shuiyu\octoberrain\core\data\ConfigMigration.kt:89-92；core\data\src\main\java\com\shuiyu\octoberrain\core\data\BackupServiceImpl.kt:38-62。

依据：Models.kt:10 以 `const val DEFAULT_WEREAD_API_KEY: String = "wrk-V1vh9k64QKGxnU4P0hUc9QAA"` 形式写明密钥，注释（:5-9）说明首次启动与配置缺失时使用；IntegrationsConfig.wereadApiKey 默认值引用该常量（Models.kt:276）；ConfigMigration.kt:89-92 在密钥为空串时回填同一常量，因此密钥必然进入配置；备份导出的 config.json 为整份 LauncherConfig（BackupServiceImpl.kt:38-62），密钥随包外传；SharedPreferences 中以明文 JSON 保存，仓库内 _analysis\prefs-before-clear.xml 等真机导出文件可直接读到完整密钥串。

影响：密钥随安装包固定分发，任何持有安装包的人可提取；备份文件转发即等于交出密钥；密钥已进入仓库内的真机数据副本，无法通过移除代码回收。

建议：默认值改为空串，首次启动不注入密钥，由用户在设置页填写；备份导出剔除 integrations.wereadApiKey 或对整包加密；若必须内置，改为构建期注入并在服务端做调用代理；发布前轮换已外泄密钥。

### F2 BeautifyScreen 职责高度集中，并与设置基类并存第二套页面骨架

级别：高

位置：feature\settings\src\main\java\com\shuiyu\octoberrain\feature\settings\BeautifyScreen.kt:51（直接实现 Screen）、:101-172（自建根布局与整页重绘）、:412-530（应用排序编辑）、:604-786（图片目录与缩略图）、:1002-1025（写入排序）、:1069-1100（图标覆盖）、:1181-1198（预览交接）、:1201-1433（主题库的另存、应用、导出、导入、删除、重置）、:1454-1487（自建桌面分页）。对照 SettingsBaseScreen.kt:28-100 与 SettingsUi.kt:128-153。

依据：单文件约 1700 行，含 30 余个私有方法与 20 余个可变成员；页面自行处理重建时机（:117-123）与整页 removeAllViews（:144-172），绕开基类统一的生命周期与重建策略；区域级局部重建（:294-315、:921-957）通过 indexOfChild 定位视图后替换，依赖视图索引稳定。

影响：新增一种图标来源或一项主题能力要在同一文件内多处插改；局部重建与整页重建两套机制并存，任一处的结构变化都可能使索引假设失效；页面状态分散在可变成员中，行为难以单独验证。

建议：按排序编辑、图标来源、图片目录、主题库拆为四个子组件，页面改用 SettingsBaseScreen 承担生命周期与重建；可变成员收敛为不可变状态类，局部重建改为按 key 查找或整体重建。

### F3 预览分页存在三处实现，未统一使用 DesktopLayoutProvider

级别：高

位置：feature\settings\...\BeautifyScreen.kt:1454-1487；feature\settings\...\PreviewScreen.kt:105-126、:134-139；core\common\src\main\java\com\shuiyu\octoberrain\core\common\Contracts.kt:528（DesktopLayoutProvider 声明）、:539-546（DesktopPreviewProvider 声明）。

依据：Contracts.kt 已提供 DesktopLayoutProvider.snapshot() 供统一分页；PreviewScreen 在绘制主体时确实调用了 previewProvider.createPreviewView（PreviewScreen.kt:213-242），注释（:32-39）声明组件与真实桌面一致；但 BeautifyScreen.snapshot 自行按 columns/rows/firstPageRows 计算分页，columns 与 rows 为 0 时取 5×5 兜底，FirstPageRows 缺省等于 rows；PreviewScreen.loadState 与 fullOrder 又各自拼接页序。真实桌面的分页由 feature/home 计算（未读，待确认）。

影响：桌面列数与行数为 0（默认值）时，美化页按 5 列 5 行估算容量，与真实桌面排布可能不一致，拖动排序显示的第几页第几格与拖动完成后的实际位置可能错位；排序写入 order 后预览页的页数与桌面页数对应关系未经同一处计算。

建议：分页与页序统一由 layoutProvider.snapshot() 提供，美化页只维护 order；预览页直接使用 snapshot 结果；删除 BeautifyScreen 内的第二套计算。

### F4 配置写入先改内存后异步写盘，失败只留日志

级别：高

位置：core\data\src\main\java\com\shuiyu\octoberrain\core\data\JsonConfigStore.kt:42-56。

依据：edit 在 synchronized 内先 `_state.value = next`（:47），随后 `preferences.edit().putString(KEY_CONFIG, 整份 JSON).apply()`（:48-51），apply 为异步提交；异常仅 InkLog.e("配置写入失败")（:52-54），无回滚、无重试、无界面提示；同文件 :65-75 读取失败或根节点非对象时直接返回默认配置。

影响：存储空间不足或 SharedPreferences 提交失败时，界面显示已生效，进程重启后回到旧值；每次修改都序列化整份配置（order 与 iconOverrides 全量），拖动排序与逐个设置图标会反复重写同一份 JSON；解析整体失败时用户配置被默认值覆盖且原文不留。

建议：写盘失败时回滚内存状态或标记待重写并在下一次成功写入时补齐，同时提示用户；order 与 iconOverrides 等大集合单独存储；解析失败时保留原文副本以便恢复。

### F5 内容域缓存字段的并发保护不一致，互斥字段闲置

级别：中

位置：feature\content\src\main\java\com\shuiyu\octoberrain\feature\content\data\BookshelfRepository.kt:43、:44-46；KoreaderRepository.kt:52-53；WereadClient.kt:50-53。对照 ContentSourceImpl.kt:82-87 使用 @Volatile。

依据：BookshelfRepository.kt:43 定义 `private val scanning = AtomicBoolean(false)`，全模块检索仅此一处出现，从未读取或写入；三个仓库的缓存时间戳与缓存值均为普通 var，写入点位于 withContext(Dispatchers.IO) 内（如 BookshelfRepository.kt:70-116、WereadClient.kt:67-84），书架页、负一屏与搜索页可并发进入。

影响：并发调用时可能读到只写了一半的列表引用或拿着过期时间戳；原本打算用于单次扫描互斥的字段闲置，重复扫描无法避免。

建议：统一使用 Mutex 或 @Volatile 加原子替换；缺省 single-flight 时把 scanning 接上 compareAndSet，或直接删除该字段。

### F6 书架扫描无结果时清空既有缓存，空书架场景反复扫描

级别：中

位置：feature\content\...\BookshelfRepository.kt:80-85、:126-129。

依据：:80-85 在 collectBooks 返回空时执行 `cached = emptyList()`、`cachedCollections = buildCollections(root, emptyList())`，而 collectBooks 受 deadline 限制（:207-219），超时未扫到文件同样返回空；:126-129 以 `cachedCollections.isEmpty() || 超过 60 秒` 触发重新扫描。

影响：一次扫描超时或书库目录暂时不可用，即清空已有书目，页面回到空状态；空书架场景下每次进入页面都重新扫盘。

建议：区分「扫描超时」与「确实为空」，超时保留旧缓存并记录时间；collections() 不把空结果视为过期条件。

### F7 内容读取超时上限过长，最长可静默等待一分钟

级别：中

位置：feature\content\...\ContentSourceImpl.kt:44（38000ms）、:47（35000ms）、:50（65000ms）、:53（42000ms）；调用点 :261、:274、:285、:215；统一封装 :342-357。

依据：书架、月度统计、笔记、阅读快照四类请求分别按 38 秒、35 秒、65 秒、42 秒设限，guarded 在超时后返回降级值；页面在协程内等待结果，期间无进度反馈。

影响：网络缓慢时页面长时间停在加载态，墨水屏整屏无变化，用户无法判断是否仍在处理。

建议：首屏读取与后台刷新分别设限，首屏控制在数秒内并先展示缓存；后台刷新不随页面生命周期等待结果。

### F8 微信读书空结果不入缓存，深链未编码

级别：中

位置：feature\content\...\data\WereadClient.kt:72-74、:112、:191、:224、:448。

依据：:72 以 `cachedShelf.isNotEmpty()` 作为命中前提，接口返回空书架时缓存判定失效，每次调用重新请求；:448 `private fun deepLink(bookId: String): String = "weread://reading?bId=$bookId"` 直接拼接，未做 Uri 编码。

影响：未订阅或书架为空时，每次进入书架页、负一屏都发起一次网络请求；bookId 含特殊字符时深链无法打开。

建议：空结果按较短有效期缓存；拼接前调用 Uri.encode。

### F9 更新下载的摘要校验可为空，下载地址来源为可配置项

级别：中

位置：core\data\src\main\java\com\shuiyu\octoberrain\core\data\UpdateServiceImpl.kt:61、:69-82、:132-137。

依据：:61 `if (info.sha256.isNotBlank() && !sha256(file).equals(...))`，清单未提供 sha256 时跳过校验直接返回文件；:132-137 用 `URL(url).openConnection()` 构造连接，未限制协议与主机；:69-82 通过 FileProvider 触发系统安装器；updateUrl 来自 integrations 配置（:34），可经备份导入改写。

影响：更新清单被替换或地址指向明文 HTTP 时，任意安装包可被下载并进入安装流程；应用已声明 REQUEST_INSTALL_PACKAGES（app\src\main\AndroidManifest.xml:5-7）。

建议：sha256 设为必填，缺失即拒绝下载；要求 https 并校验主机；安装前再校验一次摘要。

### F10 新增一项设置需改五处，文档既定的数据驱动方案未实现

级别：中

位置：docs\03-target-architecture.md:136-149；docs\13-inkOS对照评估.md:128；feature\settings\...\SettingsUi.kt:128-153、:327；feature\settings\...\DesktopPrefsScreen.kt:508-553；feature\settings\...\BookshelfSettingsScreen.kt:206-209。

依据：docs\03 规定以 SettingsSpec 与 SettingsNode（Toggle、Choice、Action、Group）配合 RecyclerView 渲染，节点只引用字段名并由 KSP 校验；全仓库检索不到 SettingsSpec 或 SettingsNode 的实现。实测 SettingsUi.section 调用约 44 处（DesktopPrefs 6、Appearance 5、BookshelfSettings 5、UpdateScreen 4、SettingsSystem 4、TrayPrefs 3、LauncherPrefs 3、Backup 3、About 3，其余 1 至 2 处），SettingsUi.edit 调用 15 处；选项常量成对维护为 `*_VALUES` 与 `*_OPTIONS` 两个并列列表。

影响：新增一项设置需同时修改配置模型与默认值、Screen 中的行声明、选项常量两份列表、摘要文案，必要时还有迁移；同一字段在摘要、选项与写入处的含义靠人工保持一致。

建议：按文档引入 SettingsSpec，以一份声明同时生成行控件、默认值、摘要与迁移判断；过渡期可先抽出「字段名 + 选项 + 读写」的描述对象，减少两列表重复。

### F11 KOReader 书目接受 Lua 文件中的任意绝对位置，统计库每次复制

级别：中

位置：feature\content\...\data\KoreaderRepository.kt:217-228、:127-157、:231-248。

依据：toLocalFile 先尝试 /sdcard 与 /storage/emulated/0 前缀映射，最后直接 `File(path)` 返回，路径来自 KOReader 的 settings.reader.lua 与 history.lua；stats() 无缓存，每次调用都经 prepareDatabase 把 statistics.sqlite3 连同 -wal、-shm 复制到 cacheDir 后只读打开。

影响：Lua 文件中记录的任意绝对位置都会被当作书籍读取（相对可信的第三方应用写入的文件，属边界放宽）；统计页与负一屏每次刷新都重复复制文件，书库较大时产生额外 IO。

建议：把位置限制在书籍根目录或 KOReader 根目录内（复用 FileLibrary.kt:320-323 的目录包含判断）；统计结果按数据库修改时间缓存。

### F12 文件选择回退遗留未取消的协程作用域与废弃存储接口

级别：中

位置：feature\settings\...\SettingsUi.kt:237-254（:243 新建作用域）、:268-278、:232-233；feature\settings\...\ImageDirectoryStore.kt:61-73；app\src\main\AndroidManifest.xml:29。

依据：fallbackPick 每次调用新建 `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`，没有任何取消点；scanRoots 依赖 Environment.getExternalStorageDirectory() 并固定扫描 Pictures、Download、DCIM、Documents、Books、Fonts 六个目录；saveFile 在系统选择器不可用时回调 filesDir/exports 下文件的 `Uri.fromFile`；ImageDirectoryStore.directoryOf 把 SAF 的 tree Uri 降级为 /storage/<volume> 形式的文件位置，非 primary 卷直接按卷名拼接。

影响：选择文件后退出页面，扫描协程继续运行并持有 Activity 上下文直到扫描结束；tree Uri 的授权信息被丢弃，实际读写依赖 MANAGE_EXTERNAL_STORAGE（Manifest 未设 maxSdkVersion）；回退保存得到 file:// 形式 Uri，若后续交给外部组件会触发 FileUriExposedException（当前调用方只在本进程内读写，暂未触发，待确认）。

建议：回退扫描复用页面作用域并在页面销毁时取消；外部目录访问改用 SAF 或 MediaStore，保留 tree Uri 与持久授权；回退保存改为应用内导出入口。

### F13 封面缓存无容量上限，列表缩略图每次重新解码

级别：低

位置：feature\content\...\data\CoverResolver.kt:18-27；feature\settings\...\BeautifyScreen.kt:776、:761。

依据：CoverResolver 把 EPUB 内嵌封面解出后写入 `cacheDirectory(context, "book_covers")`，未见清理逻辑；BeautifyScreen.loadThumbnail 每次构建单元格时对文件做采样解码，未见缓存容器。对照 FileLibrary.kt:265-286 的打开副本保留 6 份并按时间清理，做法正确。

影响：书籍较多时封面缓存持续增长；图片网格反复滚动会重复解码，墨水屏刷新本就频繁，解码耗时叠加。

建议：封面与缩略图统一到受控缓存目录，按数量或容量清理；网格缩略图加内存缓存。

### F14 内容域纯逻辑未抽离，两个模块均无测试

级别：低

位置：feature\content\...\data\BookshelfRepository.kt:225-242（侧边车解析）、EpubCover.kt:165-180（压缩包内路径规范化）、KoreaderRepository.kt:45-49（正则）、feature\settings\...\BeautifyScreen.kt:1454-1487（分页）、feature\content\RecentsRepository.kt:240-258（区间起点）。

依据：全仓库 src/test 共 8 个文件，集中在 core\common、core\data、core\design 与 feature\home 的 DesktopMetricsTest；feature\settings 与 feature\content 无 src\test、无 androidTest。

影响：侧边车正则、EPUB 目录解析、分页计算、周起点计算都是纯函数，出现偏差时只能靠真机观察；改动设置页与内容页缺少回归手段。

建议：把纯函数抽到模块内 internal 或 core，补 JVM 测试，优先覆盖 EPUB 路径规范化、侧边车解析、分页与区间起点。

## 三 值得肯定之处

P1 内容页面共用骨架。feature\content\ui\ContentScreen.kt:74-92 统一处理加载态、错误态与取消，CancellationException 单独重抛（:84-85），onPause 取消渲染任务（:170-172），各页面无需重复实现。

P2 降级封装一致。ContentSourceImpl.kt:342-357 的 guarded 对超时与异常统一返回降级值并记录日志，同时不吞掉取消异常。

P3 压缩包解析有防护。EpubCover.kt:165-180 手工规范化 `.` 与 `..` 段，:19-20 限制 XML 为 512KB、图片为 4MB，:91-105 限制条目读取大小，压缩包内目录穿越被阻断。

P4 文件操作校验完整。FileLibrary.kt:189-211 在复制与移动前做双向目录包含判断并把移动实现为复制成功后删除，:265-286 限制打开副本数量并按时间清理，:214-224 过滤目录名中的分隔符。

P5 最近任务数据层并发与降级处理正确。RecentsRepository.kt:72-77 与 :261-294 的缓存读写统一加锁，:85-87 在权限服务未装配时回退 AppOps 判断，无权限返回空结果由页面显示授权入口。

P6 外来文件名与目录选择有净化。WereadClient.kt:443 的 sanitize 与 core\data\IconOverrideStoreImpl.kt:129-130 的 safeName 均只保留有限字符；ImageDirectoryStore.kt:35-41 在保存前校验目录存在。

## 四 与文档的差距

docs\03-target-architecture.md:136-149 设定设置中心为 SettingsSpec 数据驱动的列表渲染，docs\06-development-roadmap.md:53、98 与 docs\00-overview.md:34 沿用同一设定；实际实现为每个设置页一个 Screen，由 SettingsUi 手工拼装控件。docs\13-inkOS对照评估.md:128 已记录该差距与「新增一项需同时改 Screen、配置模型、默认值与摘要文案」的代价。本次复核确认差距仍在，且调用规模（section 约 44 处、edit 15 处）比文档记录时更大。

## 五 待确认事项

一、真实桌面的分页与页序由 feature/home 的何处计算，BeautifyScreen.snapshot 的 5×5 兜底是否与之一致。

二、BeautifyScreen.openPreview 传入的 pages 与 BeautifyPreviewState 残留数据在多次进入美化页后的实际表现，需要真机观察。

三、PreviewScreen.applyOrder 连续两次 back() 依赖导航栈深度，栈内页数变化时是否出现返回异常。

四、MANAGE_EXTERNAL_STORAGE 在目标固件上的实际授予情况，以及未授予时文件管理与书架的降级表现。

五、saveFile 回退得到的 file:// 形式 Uri 是否被任何外部组件消费。

六、KoreaderRepository.toLocalFile 的路径来源是否仅由 KOReader 自身写入。

## 六 复核限制

本次复核为只读静态分析，未在设备上运行，未执行构建；运行时并发表现、权限授予后的实际行为、导航栈异常均需在设备上验证。settings 与 content 的界面绘制部分按结构抽查，未逐行核对样式与度量常量。core 模块仅阅读与 settings、content 直接相关的文件，其余部分未纳入。
