# 十月桌面代码复核与两项融合评估

复核日期 2026-10-19。复核范围为本仓库全部 135 个 Kotlin 文件、13 个 Gradle 模块、构建脚本与清单文件；评估对象为墨盘（`D:\DSHStation\MoPan`）与微读墨记（`D:\WorkBuddyStation\WereadMoji`）。正文中的构建与测试结果由实机执行得出，两项融合评估全程只读，未改动任何外部工程。

复核由五条并行只读任务完成，详细材料位于 `_analysis/team/`：`review-core.md`、`review-home.md`、`review-settings-content.md`、`eval-mopan.md`、`eval-weread.md`。本文件承担归纳与判断，逐条证据以行号形式保留。

## 一 代码复核

### 1.1 工程现状实测

构建可成功运行，与文档记载的产物规模存在差异。debug 构建经 `scripts/build.ps1` 完成，381 个任务通过；release 构建经 `gradle :app:assembleRelease` 完成，耗时 56 秒，453 个任务通过，`lintVitalRelease` 通过，产物 `app/build/outputs/apk/release/app-release-unsigned.apk` 为 589KB，`README` 记载的 440KB 与实况有出入。

单元测试存在三处失败，当前状态无法作为质量门槛。

`feature/home/src/test/java/com/shuiyu/octoberrain/feature/home/DesktopMetricsTest.kt:52:9` 与 `:84:9` 触发编译错误 `No value passed for parameter 'widgetGap'`。构造方法在 `feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/DesktopMetrics.kt:36` 增加 `widgetGap` 参数（`:49`）时未同步测试。

`:core:common` 的 `LauncherConfigJsonTest.未知字段被忽略` 断言失败，实际为 `version=2`、期望为 `version=6`，测试写于 `CURRENT_VERSION=2` 时期，默认值与断言方向均已过时。

`:core:data` 的 `ConfigMigrationTest.迁移保留其余配置字段` 断言失败，实际隐藏列表比期望多出 `com.wetao.storage` 与 `com.astraabove.tomo`，原因是 `core/data/src/main/java/com/shuiyu/octoberrain/core/data/ConfigMigration.kt:107-114` 的 `migrateV2ToV3` 会并入 `DEFAULT_HIDDEN_PACKAGES`，测试未随规则更新。

通过项为 `MonthGridTest` 6 例、`TimeUtilsTest` 9 例、`IconPackResolverTest` 7 例、`ThemePaletteTest` 5 例、`ThemeTokensCodecTest` 9 例。`.github/workflows/build.yml` 在 ubuntu-latest 上依次执行四个模块的单元测试与 `:app:assembleDebug`，按上述状态每次都会中止。仓库无 `gradlew`，`scripts/build.ps1` 仅接受 `-Root`、`-Variant`、`-Offline` 三个参数，无法承载任意任务集合。

依赖配置极简且与文档存在落差。`gradle/libs.versions.toml` 登记 agp 8.7.3、kotlin 2.0.21、coroutines 1.7.3、serialization 1.7.3，编译与目标为 compileSdk 35、minSdk 26、targetSdk 35；外部依赖仅 `androidx-core-ktx`、`kotlinx-coroutines-core`、`kotlinx-coroutines-android`、`kotlinx-serialization-json` 四项。`docs/03-target-architecture.md` 规划的 Hilt、Room、DataStore、RecyclerView、KSP 编译期组件注册均未采用，`docs` 声称的 Kotlin 2.1 与实况 2.0.21 亦有偏差。测试依赖未进版本目录，`core/common/build.gradle.kts` 与 `feature/home/build.gradle.kts` 以注释说明后硬编码 `junit:junit:4.13.2`。

分层依赖方向成立：全部 feature 模块以 `implementation` 依赖 `:core:data`，`core:widget-api` 以 `api` 依赖 `:core:data`，`core:data` 以 `api` 依赖 device、design、render，core 层未反向依赖 feature。

### 1.2 高等级问题

**内置微信读书密钥随包分发。** `core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt:10` 定义 `const val DEFAULT_WEREAD_API_KEY: String = "wrk-V1vh9k64QKGxnU4P0hUc9QAA"`，`Models.kt:276` 将其作为默认配置值，`ConfigMigration.kt:87-96` 的 `ensureBuiltInIntegrations` 在用户清空密钥后自动回填并置 `wereadEnabled = true`。该值经 `BackupServiceImpl.kt:38-62` 导出的 config.json 与 SharedPreferences 一并外流，`_analysis/prefs-*.xml` 中可读出明文。密钥属于服务端可识别的凭据，公开分发的 APK 等同于公开凭据。

**图片解码与抖动管线未接入。** `core/render/src/main/java/com/shuiyu/octoberrain/core/render/Ditherer.kt:12` 与 `BitmapPipe.kt:13` 在全仓库内仅命中声明行，无调用方。`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/ImageWidget.kt:138-149` 与同样位于该包的 `CoversWidget.kt:141-152` 各自复制了一份两遍解码逻辑并固定 `ARGB_8888`，`BitmapPipe.kt:53-56` 的 `MAX_PIXELS` 像素总量限制因此未生效。全工程图片解码分散在七处（`BitmapPipe.kt:24-61`、`ImageWidget.kt:137-150`、`CoversWidget.kt:141-152`、`ImageDirectoryStore.kt:76-88`、`ContentUi.kt:451-457`、`IconOverrideStoreImpl.kt:54-115`、`AppearanceRepositoryImpl.kt:210-234`），均未做 EXIF 方向归一。墨水屏设备上未经量化的照片会呈现为中间灰，这是自绘桌面在用图片时的观感上限所在。

**锁屏强度不足且状态无法恢复。** `app/src/main/java/com/shuiyu/octoberrain/LockGateImpl.kt:98-103` 的摘要为单次 `SHA-256(salt + pin)`，`Models.kt:262` 声明的 `iterations` 参数未被读取。四位数字 PIN 只有一万种组合，缺少失败计数与延时，逐次尝试即可穷举。`LockGateImpl.kt:24` 的 `_state` 初值为 false，仅熄屏广播经 `:87-90` 的 `onScreenOff` 置位，进程重启后 `isLocked` 即为 false，`autoLock` 设定在冷启动后失效，`MainActivity.kt:88` 与 `:103` 依赖该状态决定是否展示 PIN 界面。

**启动期主线程承担网络与系统查询。** `core/data/src/main/java/com/shuiyu/octoberrain/core/data/SystemControlsImpl.kt:60-63` 在构造期注册接收器并执行 `refreshAll()`（`:191-208`），调用点在 `app/src/main/java/com/shuiyu/octoberrain/OctoberRainApp.kt:54`，即 `Application.onCreate`。`updateWifi` 内读取 `manager.connectionInfo` 未捕获异常，定制系统上可抛出 `SecurityException`，后果是应用启动即失败。

**混淆与裁剪对自有代码失效。** `app/proguard-rules.pro:2` 只有一行 `-keep class com.shuiyu.octoberrain.** { *; }`，而 `app/build.gradle.kts` 的 release 构建启用了 `isMinifyEnabled = true` 与 `isShrinkResources = true`。自有代码被完整保留，混淆只在第三方库上生效，包体与反编译难度均未改善。

**release 未签名与权限声明过宽。** `app/build.gradle.kts:14-15`、`:19-27` 未定义 `signingConfigs`，release 产物恒为 unsigned。`app/src/main/AndroidManifest.xml` 声明 `MANAGE_EXTERNAL_STORAGE`（无 `maxSdkVersion`）、`QUERY_ALL_PACKAGES`、`REQUEST_INSTALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`PACKAGE_USAGE_STATS`。桌面启动器的功能确实需要读取应用列表，但后两项与 `REQUEST_INSTALL_PACKAGES` 的必要性取决于更新模块的形态，`MANAGE_EXTERNAL_STORAGE` 在 Google Play 上需要专项审核。

**更新与备份通道缺少必要的校验。** `core/data/.../UpdateServiceImpl.kt:61` 在 `sha256` 为空时跳过校验，`:132-137` 未限制协议，`:69-82` 以隐式 Intent 拉起安装。`BackupServiceImpl.kt:38-62` 导出的压缩包包含 `pinHash`、`salt` 与 `wereadApiKey`，`:84-103` 导入时对条目数与总字节数无上限，`:144-157` 对壁纸与字体只记录文件位置而不带上资源本体。

**单元测试失败且持续集成无法通过。** 见 1.1 的三条失败；此外 `feature/settings` 与 `feature/content` 完全没有 `src/test`，`feature/widgets` 与 `feature/tray` 亦无，全工程测试文件 8 个。

### 1.3 中等级别问题

**全局服务定位器缺少写保护。** `core/common/.../Contracts.kt:444-463` 的 `object AppServices` 持有 19 个 `lateinit var`，注释声明装配后只读；`isReady()`（`:466-483`）未覆盖 `layoutProvider` 与 `previewProvider`，两个字段缺失时仍判定为就绪。`OctoberRainApp.kt:86-99` 以 `Class.forName` 加载五个硬编码类名，失败仅 `InkLog.w` 后跳过，界面无提示，类名与 feature 模块之间缺少编译期校验。

**配置写入顺序与迁移回写。** `core/data/.../JsonConfigStore.kt:42-56` 在 `synchronized` 内先改 `_state.value`（`:47`）再 `preferences.edit().apply()`，写盘失败仅记日志（`:52-54`），内存与磁盘可能长期不一致，且每次写入重写整份 JSON。`:64-90` 的 `load()` 逐节解码后交 `ConfigMigration.migrate`，迁移结果不回写磁盘；`ConfigMigration.kt:70-74` 对版本高于当前值的情形以 `copy(version = CURRENT_VERSION)` 抹平，未来版本的数据会被静默降级。

**页面栈与具体实现耦合。** `app/.../MainActivity.kt:220-223` 的 `navigate` 无判重即入栈，返回前旧页面 View 始终挂在 `contentFrame` 上。`:100` 与 `:476` 以 `(AppServices.lock as? LockGateImpl)` 引用 app 层具体实现，契约层抽象被穿透。`:327` 与 `:333` 两个目的地标签同为「书架」；`:334` 行缩进异常；`:341-344` 重写了已废弃的 `onBackPressed`；无 `onNewIntent` 处理，`singleTask` 下的复用缺少状态刷新。

**通知中心的并发写。** `core/data/.../NotificationCenter.kt:103-120` 的 `push` 与 `:134-145` 的 `replaceAll` 均以读取 `_items.value` 后整体覆盖的方式写入，无同步，通知服务线程与界面线程并发时丢失更新。

**美化模式单体与基类缺口。** `feature/settings/.../BeautifyScreen.kt:51`、`:101-172`、`:412-530`、`:604-786`、`:1069-1100`、`:1201-1433`、`:1454-1487` 合计约 1700 行承担七类职责，并绕开 `SettingsBaseScreen.kt:28-100` 提供的统一骨架。`:1454-1487` 自行实现了 5×5 分页兜底，与 `PreviewScreen.kt:105-126`、`:134-139` 构成三处分页实现，`Contracts.kt:528` 的 `DesktopLayoutProvider` 未被使用。

**新增设置项需改五处。** `docs/03-target-architecture.md:136-149` 规划的 `SettingsSpec` 未实现，`feature/settings/.../SettingsUi.kt` 内 `section` 约 44 处、`edit` 15 处，新增一项设置要同时改 `SettingsUi`、对应 Screen、`DesktopPrefsScreen.kt:508-553` 或 `BookshelfSettingsScreen.kt:206-209`、`Models.kt` 与配置文件。

**内容域缓存并发与失效策略。** `feature/content/.../BookshelfRepository.kt:43` 的 `AtomicBoolean scanning` 从未被使用，`:44-46` 的缓存字段为普通 `var`，与 `KoreaderRepository.kt:52-53`、`WereadClient.kt:50-53` 同属并发读写；`ContentSourceImpl.kt:82-87` 反而正确使用了 `@Volatile`。`:80-85` 在超时后返回空列表并清空缓存，`:126-129` 空合集又触发重扫，弱网下会反复往返。`WereadClient.kt:72-74` 空结果不入缓存，`:448` 深链参数未编码。

**超时设置与缺少进度。** `ContentSourceImpl.kt:44`、`:47`、`:50`、`:53` 分别为 38、35、65、42 秒，界面无进度提示，用户只能等待。

**首页分钟刷新触发整屏失效。** `feature/home/.../HomeScreen.kt:47` 每分钟调用 `refreshCurrentWidget(true)`，`HomeView.kt:218-227` 随即整屏 `invalidate()` 加 `requestPartial()`，`:371-374` 的 `requestWidgetDraw` 忽略传入参数。组件创建落空后状态固化：`HomeView.kt:357-369` 先写 `widgetId` 再调用创建，失败后 `:358` 的相等判断使后续同配置回调提前返回，模块注册晚于桌面创建时该位置永久停留占位。`:784` 的 `!editHoldTriggered` 与 `:974-981` 的 `enterEditMode` 相互抵消，长按进入整理模式后同一次手势内无法拖动，`:842-855` 抬手时两个分支均不成立。

**预览对象无释放入口。** `feature/home/.../HomeDesktopPreviewProvider.kt:36-42` 每次创建预览都新建 `HomeView`，后者自带 `IconStore`、`Handler` 与协程作用域；`Contracts.kt:539-546` 的 `DesktopPreviewProvider` 只提供创建方法，`HomeView.release()`（`HomeView.kt:257-264`）在预览位置没有调用者。

**存储模型绑定全文件访问权限。** `feature/settings/.../ImageDirectoryStore.kt:61-73` 把 `OPEN_DOCUMENT_TREE` 得到的 tree Uri 降级为文件位置，`:92` 又使用 `Context.getSharedPreferences`，两者都依赖声明中无 `maxSdkVersion` 的 `MANAGE_EXTERNAL_STORAGE`。`app/.../HostFilePicker.kt:94-103` 的回调只回传 `data.data`，未调用 `takePersistableUriPermission`，重启后授权失效。

**组件协议字段不全。** `core/widget-api/.../WidgetApi.kt:116-123` 的 `WidgetProvider` 仅有 `id`、`label`、`description`、`create`，缺少 `category`、`refreshPolicy`、`requiredCapabilities`，与 `docs/03` 的规划不符；宿主的分钟刷新无法按组件声明区分，只能对整屏操作。

### 1.4 低等级问题

`feature/home/.../DesktopMetrics.kt:164`、`:167` 以 `30f/480f`、`28f/480f` 定义间距比值，552 宽设备上的留白比例与文档「不沿用 480×800 画布」的表述存在解释空间。`:146-152` 的宫格命中只判断 `x < left`，列下标被夹到最右列，内容区右侧空白仍会触发最右列启动。

`feature/home/.../NegativeScreen.kt:159-160` 的提前返回使 `:115-116` 的补建分支失效，模块晚注册时停留空状态。`feature/tray/.../TrayPanelView.kt:60`、`:670` 在 `dispatchTouchEvent` 内同步调用 `controller.close()`，与 `NegativeScreen.kt:275-279` 的 `post` 延后移除写法不一致，前者存在重入隐患。

文本截断有两套实现：`HomeView.kt:707-717`、`:575-590` 与 `feature/widgets/.../InkWidgetView.kt:217-240`。封面缓存无上限（`feature/content/.../CoverResolver.kt:18-27`），美化模式缩略图每次解码（`BeautifyScreen.kt:776`）。`KoreaderRepository.kt:217-228` 在前缀映射失败后以 `File(path)` 构造对象，`:127-157` 的统计库在每次打开时整体复制。`SettingsUi.kt:237-254` 每次新建协程作用域且不取消，`:268-278` 使用已废弃的存储接口扫盘。`FileLibrary.kt:51`、`:52` 的递归深度与打开缓存上限为常量，大目录下的统计成本固定。

### 1.5 规范与工程化落差

`docs/08-engineering-conventions.md` 的六项约定中有四项与实况不符。

协程调度要求统一经由 `IoDispatcher`，工程内不存在该对象，`Dispatchers.IO` 出现 48 处，其中 feature 层 22 处。feature 层禁用 `Context.getSharedPreferences` 的要求被 `ImageDirectoryStore.kt:92` 破坏。feature 层禁用 `DisplayMetrics` 的要求被 `HomeView.kt:579` 与 `BeautifyScreen.kt:1061` 破坏。目录约定要求 feature 内固定 `ui`、`domain`、`data`、`di` 四层，实际仅 `feature/content` 具备 `ui` 与 `data` 子包，其余平铺。

静态检查与覆盖率门槛缺失：根 `build.gradle.kts` 只声明四个插件别名，无 ktlint、无 detekt，`check` 任务不包含风格校验。`docs/08:79` 要求每个模块提供百行以内的 `README.md`，core、feature、service、app 下均无。

仓库体积与内容需要清理：工程目录 272.3MB，其中 `_analysis/` 79.05MB、2480 文件（含 `jadx-out/`、`reports/`、`EMReader.apk`、`inkdisk.apk`、`prefs-*.xml`、`shot-*.png`），`_shots/` 19.9MB、208 文件，根目录留有上游包 `tomo-launcher-0.5.0.apk`（2.31MB）与 `_tools/jadx-headless-mcp-0.7.1-all.jar`。`prefs-*.xml` 内含明文密钥。工程无 git 版本控制，上述内容一旦纳入版本库，清理成本会随历史累积。

文档与实现的一致性需要校正：`docs/03` 的三项技术选型（Hilt、Room、DataStore、RecyclerView、KSP）与五项 section 配置未实施；`docs` 对 Kotlin 版本、release 包体的记载与实况不一致；`README` 对 CI 的描述与持续集成配置实际执行的任务不一致。

### 1.6 值得肯定之处

依赖方向成立，core 层未反向依赖 feature，`ThemePalette` 为纯 JVM 数据类并配有测试。装配逻辑集中于 `OctoberRainApp.kt:41-82`，设备画像、设计运行时、数据服务、宿主服务与模块按线性顺序写入单一入口。配置容错按节独立处理（`JsonConfigStore.kt:101-115`），`ConfigMigration` 保持无 IO 的纯函数形态并配有测试，迁移链 v1 至 v6 可读性良好。

生命周期收尾对称：`MainActivity.kt:471-483` 与 `NotificationRelayService.kt:74-81` 完整释放了接收器、计时器、订阅与全局处理器。墨水屏抽象集中且可测：`DeviceProfile` 以三段断点划分设备，`InkTokens` 统一换算，`BitmapPipe.sampleSize` 设有像素总量限制，`RefreshScheduler` 合并同帧重绘并提供整屏闪烁入口。

若干安全校验做得扎实：`feature/content/.../EpubCover.kt:165-180` 阻断压缩包内的目录穿越；`FileLibrary.kt:189-211`、`:265-286` 在复制前后双向校验包含关系并限制副本数量；`RecentsRepository.kt:72-77`、`:261-294` 的缓存访问加锁并在权限丢失时降级；`IconOverrideStoreImpl.kt:129-130` 与 `WereadClient.kt:443` 对文件名做了净化。

界面层有可复用的骨架：`ContentScreen.kt:74-92` 统一处理加载与取消，`ContentSourceImpl.kt:342-357` 的降级逻辑不吞掉协程取消；`InkWidgetView.kt:29-176` 将加载、裁剪与生命周期收拢到基类，`onDestroy`（`:167-172`）确实取消协程，宿主两处调用齐备；`HomeView.kt:450-461` 以 `runCatching` 隔离单个组件的绘制异常；预览复用真实绘制并以 `previewMode` 屏蔽触摸（`HomeView.kt:723-725`、`:283-290`）；`IconStore.kt:16-19`、`:110` 设有容量上限与最近最少使用淘汰；`NegativeScreen.kt:271-280` 给出了延后移除的正确范例。

### 1.7 处置批次

第一批处理随包分发的凭据与构建完整性：移除 `Models.kt:10` 常量与 `ConfigMigration.kt:87-96` 的回填逻辑，改为用户输入并在设置页给出格式校验；收窄 `proguard-rules.pro:2` 至五个 `*Module`、序列化模型与 `WidgetProvider` 实现；补齐 `signingConfigs`；修复三处单元测试并把 CI 恢复为有效门槛。

第二批处理可用性缺陷：锁屏改为 `PBKDF2` 并增加失败计数与延时，冷启动时按 `enabled && autoLock` 恢复锁定态；系统状态查询移出构造期并统一捕获异常；首页分钟刷新按组件声明区分脏区；修复 `HomeView.kt:357-369` 的占位固化；`ImageDecode` 与 `BitmapPipe` 合并为单一解码入口并接入 `Ditherer`。

第三批处理结构性问题：设置项声明化、`SettingsSpec` 实施、`BeautifyScreen` 拆分、`ContentSourceImpl` 超时与进度、通知中心并发写、配置写盘顺序与迁移回写、`AppServices` 冻结为只读持有。`docs/03` 与 `docs/08` 需按实际实现重写，或按文档补齐缺失项，两者取其一，避免长期分叉。

## 二 墨盘集成评估

### 2.1 能力对照

墨盘工程位于 `D:\DSHStation\MoPan\inkdisk`，26 个 Java 文件合计 9320 行（core 12 个 3011 行、shell 6 个 2427 行、ui 8 个 3882 行），构建依赖 `inkdisk\tools\build.sh` 的 aapt2、javac、d8、jar、zipalign、apksigner 手工链，minSdk 23、targetSdk 30，包名 `com.inkread.inkdisk`。

十月雨现有文件页已覆盖重命名、复制、移动、删除与新建目录（`feature/content/.../data/FileLibrary.kt:169-232`），并具备最近文件与存储占用统计，范围超过墨盘 P0 声明（`MoPan\inkdisk\CONTRACT.md:15` 明确 P0 不做这三项操作）。

墨盘独有能力集中在浏览与查看：`Thumbs.java` 的内存缩略图缓存（`L61-63` 上限取 6MB 与堆上限的十六分之一中的较小值，失败表 `L30`、`L328`）、`ImageView.java` 1380 行的查看器（三档缩放 `L596`、双线程解码与预取 `L693-713`、`L890`、分享 `L1108`、旋转另存 `L1143`）、`ImageDecode.java` 454 行解码（EXIF 归一 `L281`、`L334`，长边 3200 限幅 `L32`，8000 万像素上限 `L38`，堆预算判断 `L426`）、`PathModel.java` 341 行的目录列举与命名，以及 `PickActivity.java` 与 `OutProvider.java` 构成的对外只读选择通道。

两侧共同缺失的能力是图片编辑与相册。墨盘的编辑只有 `Edits.rotate90`（`Edits.java:57`），`docs/16_墨盘_文件管理器与相册_评估与设计.md:518-522` 把裁剪、五档黑白滤镜、操作栈撤销、相册分桶与 MediaStore 索引列为 P1，源码中没有 `CropView`、`Editor`、`Gray`、`EditExport`、`MediaIndex`、`GalleryActivity` 对应文件。系统壁纸两侧都未申请 `SET_WALLPAPER`，墨盘以 `Wallpaper.exportForWetao`（`L326`）与无障碍服务 `AssistService.java` 采用厂商通道，只对 XTEINK 设备成立。

重复建设的规模可以量化：墨盘 `GridPage` 814 行、`ListPage` 819 行、`PagerView` 318 行、`Ink` 95 行、`Hit` 37 行、`Metrics` 145 行合计 2228 行，与 `FilesScreen.kt` 467 行加 `FileLibrary.kt` 419 行在浏览与文件操作语义上重叠约 2000 行。

### 2.2 集成方式比较

整包并入 `feature:content` 引入 9320 行，四个 Activity 壳 1882 行中约 700 行装配逻辑需重写为 `Screen`，改写面 2200 至 3400 行，模块规模将增至约 15400 行。同进程可以共享缩略图缓存与 `RefreshScheduler`，代价是文件、相册与阅读混在一个模块，与 `docs/03-target-architecture.md:48` 的职责划分相悖。

新建 `feature:files` 模块以源码依赖引入，改造量在上述基础上另加模块骨架与注册 150 至 250 行、`Screen` 包装 300 至 500 行，模块边界清晰，依赖仅需 `core:common`、`core:render`、`core:device`，符合现有依赖方向。

独立 APK 经 Intent 与 ContentProvider 互调，十月雨侧改动 100 至 250 行、墨盘侧 0 至 100 行，`ViewerActivity` 已接管 `ACTION_VIEW`，`PickActivity` 已接管 `GET_CONTENT` 与 `OPEN_DOCUMENT`，`OutProvider` 已提供只读通道。跨进程无法共享 `Thumbs` 的 `LruCache`，无法嵌入负一屏与桌面图片组件，也无法复用桌面几何与刷新策略，且两套构建体系与两套界面规范需长期并行维护。

选件移植只接查看器与缩略图：迁入 `ImageDecode`、`Thumbs`、`PagerView`、`ImageView`、`GridPage`、`ListPage`、`FileItem`、`Sort`、`PathModel`、`Storage`、`Prefs`、`Ink`、`Hit`、`Metrics` 合计 5263 行，适配改写 1500 至 2000 行，模块骨架与包装 500 至 800 行；文件页沿用现有实现，编辑与相册按阶段新建。

| 维度 | 整包并入 content | 新建 files 模块 | 独立 APK | 选件移植 |
| --- | --- | --- | --- | --- |
| 引入代码行数 | 9320 | 9320 | 100 至 350 | 5263 |
| 同期改写行数 | 2200 至 3400 | 2400 至 3600 | 0 | 1500 至 2000 |
| 新增模块 | 0 | 1 | 0 | 1 |
| 进程内共享缓存 | 是 | 是 | 否 | 是 |
| 嵌入负一屏 | 是 | 是 | 否 | 是 |
| 模块职责划分 | 混入 content | 清晰 | 分离 | 清晰 |

选择建议以选件移植为第一阶段形态，目标形态为独立的 `feature:files` 模块。判断依据三条：文件操作语义以现有 `FileLibrary` 更完整，整包并入会重复约 2000 行；编辑与相册在墨盘源码中不存在，移植量与现有可用代码匹配即可；同进程是嵌入负一屏与桌面图片组件的前提，独立 APK 形态无法满足。独立 APK 形态可作为不合并时的短期替代，适用于优先获得查看器与选图的安排。

### 2.3 技术衔接

包名、资源与 authority 必须整体改名。墨盘清单 `L4` 为 `com.inkread.inkdisk`，主题 `InkTheme` 在 `L27-33`，`OutProvider` authority 为 `com.inkread.inkdisk.files` 在 `L103-107`；十月雨已有 `${applicationId}.files`（`app/src/main/AndroidManifest.xml:90-98`）。`Ink.java` 的四档灰（`CONTRACT.md:54-66`）需映射到 `InkColors`，避免同一含义对应不同色值。

文件访问模型两侧一致，均采用 `MANAGE_EXTERNAL_STORAGE` 加 `java.io.File` 直读（墨盘清单 `L7`、`Storage.java:36-134`；十月雨清单 `L29`、`FileLibrary.kt:100`），并入后无需引入 SAF 树授权。墨盘清单 `L31` 的 `requestLegacyExternalStorage` 在 targetSdk 35 下失效，独立运行时依赖的 legacy 行为需重测。

Uri 授权两侧都只做临时授权。墨盘 `finishWith`（`PickActivity.java:248-263`）设置 `FLAG_GRANT_READ_URI_PERMISSION` 并调用 `grantUriPermission`，未调用 `takePersistableUriPermission`，与 `CONTRACT.md:182-188` 要求调用方自行持久化的约定不一致；十月雨 `HostFilePicker.kt:94-103` 同样未持久化。并入同进程后内部页面以 `File` 直读，授权问题只影响对外分享。

缩略图缓存需从纯内存改为磁盘缓存。建议置于 `context.cacheDir/thumbs`，键取绝对位置、修改时间与目标像素的摘要，失效经 `RefreshScheduler.notifyContentChanged`（`RefreshScheduler.kt:74`）与文件写操作后的主动清理触发；`Thumbs.java:350` 的脏表接口当前无登记方，接入后即可承担失效通知。查看器同时保留当前图与邻图预取（`ImageView.java:890` 已有实现）。

解码与抖动需要合并为单一入口。建议把 `ImageDecode` 移入 `core:render`，`BitmapPipe` 改为委托，`Ditherer` 在查看器与缩略图环节启用。抖动是否作用于查看器影响观感与翻页耗时，需要在阶段一结束前确定。

刷新策略保持分工：墨盘每个自绘页面绘制前铺纸白（`PagerView.java:224`）且不主动触发整屏闪烁（`CONTRACT.md:150-157`），十月雨已有局部合并与整屏闪烁两档（`RefreshScheduler.kt:74`、`:87`），查看器翻页采用局部合并，整屏闪烁交既有定时器（`:21` 默认 10 分钟）与手动入口。

入口与组件侧需要三处配合：`Destination` 枚举（`Contracts.kt:147-243`）增加图片与相册两项；`ImageWidget.kt:82-153` 的选图、复制、解码三条行为改为调用模块内选择器，省去复制到内部目录；`FilePicker` 接口含 `pick`、`save`、`pickDirectory`（`Contracts.kt:602-616`），墨盘选择器只覆盖读取，`save` 与目录选择继续由 `HostFilePicker` 承担。负一屏文件区需要新增模块或扩展 `BookshelfNegativeModule.kt`（现有 507 行仅书目条目，`L419-445`）。

### 2.4 分期与规模

| 阶段 | 内容 | 引入与移植 | 净新增与改写 |
| --- | --- | --- | --- |
| 一 只读查看器与缩略图 | 模块骨架、`PhotoScreen`、`ThumbCache`、列表与网格、入口 | 移植 5263 行 | 净新增 505 至 700 行，改写 1500 至 2000 行 |
| 二 编辑与保存 | `EditScreen`、`CropView`、`Gray`、`EditExport`、查看器面板扩展 | 复用 `Edits.java:57` | 净新增 1240 至 1500 行，改写 60 至 100 行 |
| 三 相册与壁纸 | `MediaIndex`、`AlbumScreen`、壁纸入口 | 厂商通道另加 `Wallpaper.java` 458 行、`AssistService.java` 545 行 | 净新增 700 至 900 行，厂商通道另改写 300 行 |
| 四 桌面联动 | 负一屏文件区、`ImageWidget` 改造、`HostFilePicker` 扩展、设置项 | — | 净新增 700 至 1100 行 |

合计净新增 3145 至 4200 行，改写 1560 至 2100 行，移植 5508 行。计入厂商通道则另加移植 1003 行与改写约 300 行。并入形态的总改动量约 4700 至 6300 行，与 `docs/16:518-522` 对独立应用 P0 与 P1 的估算 4600 至 5400 行处于同一数量级，按阶段依赖计约为其 1.0 至 1.2 倍。

### 2.5 障碍与待决

几何与设计令牌是改造面最大的一项。`Metrics.java:111` 的 `of` 读取 `DisplayMetrics`，十月雨要求 feature 层经 `DeviceProfile` 取尺寸（`docs/03-target-architecture.md:76-89`）；墨盘另有固定 4 列 4 行与每页 16 项的常量（`CONTRACT.md:71-84`），第二台 824×1648 与 S4 480×800 的 dp 基准在 `docs/16:245-264` 与 `:462-471`，第三台宽度超出 350 至 430dp 时需按 `Build.MODEL` 覆盖（`:473-475`）。

Java 与 Kotlin 混编本身无障碍，AGP 8.7.3 与 Java 17 支持同模块混编（`gradle/libs.versions.toml`、`app/build.gradle.kts:30-35`），主要成本在资源与主题迁移；墨盘「无 androidx、无 Kotlin、无第三方依赖」的自检约束（`CONTRACT.md:192-197`）在并入后不再成立，需要重新界定模块约束。

构建体系、SDK 版本与权限审核三项属于工程前提：手工链与十三模块 Gradle 并行会带来界面代码双份维护；minSdk 23 与 targetSdk 30 对 26 与 35 的差异会让 `requestLegacy` 与部分 API 分支成为冗余；两侧都声明 `MANAGE_EXTERNAL_STORAGE`，上架渠道的审核要求需要确认。

来源与许可是并入前必须明确的事项。墨盘工程目录内未检索到 LICENSE 文件，`README.md:3` 说明该工作区独立于微读墨记开发，`:5` 说明 `docs/` 前六份文档原样复制自微读墨记。并入前需要确定授权方式与署名位置。

需要提前决定的事项包括：目标设备集合是否覆盖 S4 与第二台；墨盘独立 APK 是否保留及外部调用方清单；文件访问模型维持全文件访问或改 SAF；壁纸采用系统 `SET_WALLPAPER` 或厂商导出加无障碍；抖动归属；编辑范围先做旋转另存或一次做到裁剪与五档滤镜；文件操作保留 `FileLibrary` 语义或改用 `Ops.java:41`、`:93` 的内容比对与重名规则；并入后墨盘原工程的处置方式；以及 `docs/16:545-552` 列出的设备实测项，包括 32 位设备解码与滤镜耗时、MediaStore 删除同步、中文二值可读性、第二台自带图库对选图入口的抢占。

## 三 微读墨记融合评估

### 3.1 两侧定位差异

微读墨记以无障碍悬浮窗为唯一呈现方式（`D:\WorkBuddyStation\WereadMoji\docs\00_项目宪法.md:48-50`），起因是目标环境不支持标准 AppWidget（`docs/04_桌面让位判据.md:9-12`），工程自述 Gradle 多模块不采用、以手写构建脚本与纯 Java 零依赖为约束（`00_项目宪法.md:43`）。十月雨是自绘桌面，组件协议以 `core/widget-api/.../WidgetApi.kt:29` 的 `WidgetView.draw` 为核心，数据获取与绘制已在 `InkWidgetView.kt:92-120` 分离。

这一差异决定融合方式：两侧的呈现层不可互换，但数据获取与渲染规格可以复用。把微读墨记的 Java 源码并入，等于在自绘桌面内维持一套悬浮窗几何与触摸模型，收益低于按组件协议重绘。

### 3.2 推荐形态

保留十月雨作为宿主与微信读书的唯一实现，把微读墨记的独有能力按组件协议重绘为组件与页面，规模约 2000 至 3100 行 Kotlin，工期 9 至 12 人日。

第一阶段数据层 2 至 3 人日：在 `feature/content/.../data/WereadClient.kt` 增加 `/review/list/mine`、weekly、annually、overall 与 `/book/bestbookmarks` 接口，新增 `WereadCache.kt` 按模式与周期起点分键并轮转六期，`core/common/.../Models.kt` 扩充周期与笔记字段。第二阶段组件 3 至 4 人日：`feature/widgets/.../HeatMonthWidget.kt` 增加周视图与月历数字，新增笔记卡、统计、账单、排行、阅读指纹等组件并登记 `WidgetCatalog.kt:91`。第三阶段页面与设置 2 至 3 人日：`feature/content/.../ui/WereadScreen.kt` 增加统计分段与账单入口，新增全屏月历页，设置页增加刷新间隔。第四阶段验证 2 人日：480×800 像素对照、墨色主题、并发刷新、身份检索、启动耗时与内存。

### 3.3 衔接改造点

几何需要从绝对像素改为按 `onSizeChanged` 得到的宽高与设计令牌计算，微读墨记的 `core/CardSpec.java:39-49` 以 480×800 画布写死坐标。「号 × 单位」的排版方式需换成 dp 与 sp（`ui/InkTheme.java:38-39`）。INK、GRAY、LIGHT 三级色需映射到 `InkColors` 的四级灰（`WereadMoji` 侧 `feature/CardRenderer.java:39-41`）。触摸窗常量改为 `onClick` 坐标配合 `WidgetApi.kt:32` 提供的绘制协议。刷新策略交由 `core/render/.../RefreshScheduler.kt:21`。数据刷新沿用 `ContentSourceImpl.kt:102-131` 的超时包裹与来源开关。

### 3.4 障碍与待决

许可层面已核实：微读墨记包根没有 LICENSE，主工程 `D:\WorkBuddyStation\WereadMoji\weread-widget\LICENSE` 为 MIT 全文（`Copyright (c) 2026 Mosley-Z`，21 行），包根与 `docs` 无声明。按 MIT 条款，重绘与再分发需要保留版权与许可声明，融合后应在仓库内保留该声明并在界面关于页注明来源。

其余障碍包括：两套应用的密钥与登录态若并存会产生双份缓存与凭据，融合后应统一到单套配置键与缓存目录；构建体系差异（手写脚本对 Gradle 十三模块、minSdk 23 对 26、targetSdk 30 对 35）意味着源码并入需要整体迁移；480×800 绝对坐标与 dp 三档换算涉及全部卡片绘制；公开网关的字段与 errcode 变化需要按现行接口校对；身份隔离要求意味着不得沿用微读墨记的包名、authority 与数据目录。

融合后应保留的实现是十月雨侧 `WereadClient.kt`、`BookshelfRepository.kt`、`WereadScreen.kt`、`BookshelfScreen.kt` 与四个组件，删除的候选是微读墨记的悬浮窗装配、无障碍服务与手写构建链；两者的数据取用范围相反，微读墨记覆盖统计与笔记，十月雨覆盖书架与阅读进度，重绘时按并集设计而不合并实现。

## 四 两项融合的共同前提

解码与量化能力应下沉至 `core:render`。墨盘的 `ImageDecode` 与十月雨现有多处解码合并为单一入口，`Ditherer` 与 `InkCanvas` 承担量化与基础绘制，`RefreshScheduler` 统一刷新，图片查看器、组件封面与书封共用同一条通道。这一项同时解决代码复核中的高等级问题之一。

模块边界需要一次确定：文件与图片入 `feature:files`，阅读入 `feature:content`，可复用绘制入 `core:render`，组件入 `feature:widgets`。三项工作并行时，任何 feature 之间互不引用，共享件全部下沉。

构建体系统一到 Gradle 与 minSdk 26 基线。两个外部工程的独立 APK 若继续存在，界面代码将长期双份维护；若停用，外部调用方需要改。

身份隔离需覆盖包名、FileProvider authority、数据目录与凭据。墨盘的 `com.inkread.inkdisk.files` 与微读墨记的悬浮窗配置均需改名，桌面侧 `special:files`、`special:bookshelf` 两个隐藏项（`Models.kt:29-34`）在能力补齐后重新评估是否保留。

许可与来源需要先行确认。墨盘工程内无许可文件，微读墨记主工程为 MIT 但包根无声明，两者与十月雨的授权方式需一并明确，融合产物应保留必要声明。

三项工作之间存在顺序依赖：代码复核的第二批与第三批决定了解码管线与模块边界，两项融合依赖这两项先行完成。若并行推进，`core:render` 的合并会成为两侧共同的前置，宜作为第一项独立完成。
