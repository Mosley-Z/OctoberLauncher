# review-core 代码复核报告

复核对象：OctoberLauncher（`com.shuiyu.octoberrain`，versionName 0.9.0，minSdk 26，targetSdk 35，13 个 Gradle 模块）。
复核方式：只读阅读源码与构建脚本，未执行构建、未运行测试、未改动任何工程文件。debug 构建由其他成员验证通过（`app\build\outputs\apk\debug\app-debug.apk`）。
复核范围：`core\common`、`core\data`、`core\design`、`core\device`、`core\render`、`core\widget-api`、`app`、`service`，以及根构建脚本、`app\build.gradle.kts`、版本目录、清单、各级 proguard 规则、`gradle.properties`。

## 一、分层与依赖方向

模块划分（`settings.gradle.kts` 的 13 个 include）：`:app`、`:core:common`、`:core:design`、`:core:device`、`:core:render`、`:core:data`、`:core:widget-api`、`:feature:home`、`:feature:widgets`、`:feature:tray`、`:feature:settings`、`:feature:content`、`:service`。

依赖方向整体成立：`core:common` 只依赖 AndroidX core-ktx、协程与序列化；`core:data` 通过 `api` 暴露 `core:common/device/design/render`；`core:widget-api` 在 `core:data` 之上聚合界面契约；`app` 以 `implementation` 聚合五个 feature、六个 core 与 service；feature 模块不反向依赖 `app`。`core:design` 中的 `ThemePalette` 为纯 JVM 数据类，`ThemeTokensCodec` 只依赖 `core:common` 的 `AppJson` 与模型，具备单元测试条件。

存在的方向问题集中在装配层：

- `app\src\main\java\com\shuiyu\octoberrain\OctoberRainApp.kt:57-73` 在 `app` 模块内直接构造 `PlaceholderContentSource`、`PlaceholderTrayController` 并写入 `AppServices`，功能模块随后再覆盖同名字段。契约允许覆盖，装配职责分散在两个位置。
- `app\src\main\java\com\shuiyu\octoberrain\MainActivity.kt:100-102` 把 `AppServices.lock` 强制转换为具体类 `LockGateImpl` 以订阅锁状态，`LockGate` 契约本身没有状态观察入口。
- `app\src\main\java\com\shuiyu\octoberrain\MainActivity.kt:206-208` 直接 `OnboardingScreen(...)` 构造页面，未经过 `ScreenProvider`；同文件中 `ScreenRegistry.create` 与直接构造并存。

## 二、契约抽象

契约数量与职责划分总体合理：`ConfigStore` 用 `StateFlow<LauncherConfig>` 加纯函数式 `edit` 表达配置变更；内容、系统控制、通知、备份、字体、主题、图标覆盖、更新分别独立；`ScreenProvider`、`LauncherModule`、`NegativeModule` 提供模块接入点。

抽象层面的问题：

1. `AppServices` 的写入没有任何保护。`core\common\src\main\java\com\shuiyu\octoberrain\core\common\Contracts.kt:444-463` 定义 19 个 `lateinit var`，注释（`:441-442`）写明「之后全局只读」，实现上任何模块都能随时重新赋值。`isReady()`（`:466-483`）只检查 17 项，遗漏 `layoutProvider`（`:462`）与 `previewProvider`（`:463`），而后者另有 `hasLayoutProvider()`（`:504`）与 `hasPreviewProvider()`（`:507`）可用，说明就绪检查与字段清单已经不同步。
2. `LockGate` 契约（`Contracts.kt` 内）只声明 `isLocked`、`hasPin`、`lock`、`unlock`、`setPin`、`clearPin`、`createOverlay`、`onScreenOff`，没有观察通道，`LockGateImpl` 额外提供 `state: StateFlow<Boolean>` 与 `observer`，界面只能依赖实现类。
3. `AppCatalog.loadIcon` 与 `IconOverrideStore.loadOverride` 的线程语义未在契约中说明。`AppCatalogImpl.kt:56-105` 的 `loadIcon` 为 `suspend`，但 `loadOverride` 是同步方法（`IconOverrideStoreImpl.kt:105-126`），调用发生在 `withContext(Dispatchers.IO)` 之外。
4. `TimeUtils` 中的 `Outcome`（`core\common\...\InkLog.kt`）在实际代码中几乎未使用，错误处理统一改用 `runCatching` 与日志，存在一处未被采用的抽象。

## 三、生命周期与内存

`MainActivity.onDestroy`（`MainActivity.kt:471-483`）的收尾较完整：取消待执行刷新、清空 `mainHandler`、停止 `MinuteTicker`、注销屏幕熄灭接收器、解除锁状态订阅、解除文件选择器与路由挂载、清空页面栈、解除刷新控制器。`NotificationRelayService.onDestroy`（`NotificationRelayService.kt:74-81`）清空 `NotificationCenter` 处理器与 `MediaBridge`、移除媒体会话回调与消息队列。这两处属于正向实现。

仍存在的问题：

- `core\data\src\main\java\com\shuiyu\octoberrain\core\data\SystemControlsImpl.kt:158-160` 的 `dispose()` 在全仓库只有定义、没有调用方（对 `.kt` 文件检索 `dispose()` 仅一处命中）。接收器在 `:60-63` 的 `init` 块内注册，生命周期与应用进程相同，不存在释放时机。
- `SystemControlsImpl.kt:173-179` 的 `refreshAll()` 在构造期同步执行，包含 `registerReceiver(null, batteryFilter)` 与 Wi-Fi、蓝牙状态读取，全部落在 `Application.onCreate` 的主线程。
- `core\data\...\AppCatalogImpl.kt:46` 的图标内存缓存为 `LruCache<String, Bitmap>(48)`，按条数计数，不设字节上限；`loadIcon` 的磁盘缓存文件名包含 `sizePx`（`:56-105` 内），同一应用不同尺寸各留一份，`filesDir/icons` 目录无清理逻辑。
- `core\data\...\NotificationCenter.kt` 的 `labelCache` 为可变 `Map`，无上限、无同步，条目随通知应用数量增长。
- `core\data\...\FontsRepository.kt` 的 `labelCache` 与 `labelCachedPath` 是两个独立字段，没有同步保护，字体导入与界面读取并发时可能读到旧 Typeface。

## 四、协程与线程模型

协程使用集中在 `core:data` 与 `app`。`fetch/import/export/refresh` 类方法普遍采用 `withContext(Dispatchers.IO)`，`OctoberRainApp` 使用 `SupervisorJob() + Dispatchers.Main.immediate` 监听配置变化，`RefreshScheduler` 用单 Handler 合并同帧的重绘通知，`TimeUtils` 的 `MinuteTicker` 与 `IntervalTicker` 依赖主线程 Handler。整体没有出现 `GlobalScope`、没有在协程中阻塞等待，取消传播由调用方负责，`MainActivity` 的收尾满足了这一约定。

问题集中在两处：

1. `AppCatalogImpl.kt:56-105`：`loadIcon` 先在调用线程执行 `AppServices.iconOverrides.loadOverride(packageName, sizePx)`，该方法内部包含 `inJustDecodeBounds`、`decodeFile`、`createBitmap`、`createScaledBitmap` 与逐级 `recycle`（`IconOverrideStoreImpl.kt:105-126`），随后才进入 `withContext(Dispatchers.IO)` 读取图标包、磁盘缓存与 PackageManager 图标。落在主线程时会形成磁盘读取与位图分配，墨水屏设备上表现为整屏刷新前的停顿。
2. `core\design\...\DesignRuntime.kt` 的 `install` 在 `OctoberRainApp.kt:52` 的 `Application.onCreate` 中调用，内部执行 `fonts?.labelTypeface()`，由 `Typeface.createFromFile` 解析文件；配置变化后 `OctoberRainApp.kt:115-120` 的收集器在主线程再次触发字体刷新。字体文件较大时会在启动与外观切换时占用主线程。
3. `core\design\...\InkColors.kt` 的 `apply` 逐字段写入 8 个 `@Volatile` 颜色值，绘制线程与配置线程并发时可能读到新旧混合的配色。

## 五、错误处理与降级

正向设计：`JsonConfigStore` 按节解析，单节损坏只回退该节默认值（`JsonConfigStore.kt:101-115`）；`AppearanceRepositoryImpl.decodeSampled` 与 `BitmapPipe.decodeFile` 对解码失败返回 `null` 并记日志；`RefreshScheduler` 在刷新控制器未挂载时静默跳过；`InstallModules` 对模块加载失败只记日志。

主要问题：

- `SystemControlsImpl.kt:191-208` 的 `updateWifi()` 没有 `runCatching`，内部直接调用 `manager.isWifiEnabled` 与 `manager.connectionInfo`。该函数经 `init` → `refreshAll()`（`:60-63`）在 `Application.onCreate` 执行，部分定制系统在缺少定位或附近设备权限时抛出 `SecurityException`，异常会沿构造链传出，形成启动崩溃。同文件的蓝牙与电池查询均包了异常，Wi-Fi 查询是唯一漏项。
- `app\src\main\java\com\shuiyu\octoberrain\OctoberRainApp.kt:85-102` 的模块加载把五个类名写成字符串常量（`HomeModule`、`WidgetsModule`、`TrayModule`、`SettingsModule`、`ContentModule`），`Class.forName` 或 `install` 抛出异常时只调用 `InkLog.w` 后继续。混淆规则或类名调整会让整个功能模块消失，界面表现为空桌面或无内容页，用户看不到任何提示。降级后的具体界面表现待确认（`feature:home` 未在复核范围内）。
- `core\data\...\BackupServiceImpl.kt:84-103` 的导入对 `theme.json` 采用整节替换（`appearance = theme`），`ConfigMigration` 之后不再校验字段边界，导入文件可以写入任意外观值；`manifest.json` 来源包不一致时只附加 `manifestWarning`，不阻断导入。
- `core\data\...\UpdateServiceImpl.kt:24-53` 的清单拉取失败返回 `null`，界面无失败原因；`:55-67` 的下载仅在 `info.sha256` 非空时校验，`:69-82` 的安装过程不校验 APK 签名，清单与安装包来自同一 URL 的两种资源。
- `core\data\...\IconPackResolver.kt` 的 `drawableToBitmap` 用 `runCatching` 包住 `drawable.draw`，失败返回 `null` 后由调用方回退系统图标，这一层降级是合理的；但 `packContext()`（`:94-120`）以 `Context.CONTEXT_IGNORE_SECURITY` 加载第三方图标包资源，未校验图标包来源与签名。
- `service` 模块的 `OverlayAccessibilityService.kt:323-342` 通过反射调用隐藏接口 `injectInputEvent`（先取 `input.javaClass.methods` 中名称与参数个数匹配的方法，再 `isAccessible = true` 调用）。Android 9 以上对非 SDK 接口的限制可能使该调用失败，失败时返回 `false`，音量键翻转功能静默失效。目标机型上的实际表现待确认。

## 六、配置持久化与迁移

`JsonConfigStore`（130 行）结构清晰：SharedPreferences 单键存放整份 JSON，`edit` 内比较相等后跳过、先更新 `StateFlow` 再异步写盘，加载时整体失败回退全部默认值、分节失败回退单节默认值。

存在的问题：

1. 内存与磁盘可能分叉。`core\data\...\JsonConfigStore.kt:42-56` 先 `_state.value = next`，再 `preferences.edit()...apply()`，写盘失败只记日志（`:52-54`），内存中的新配置在下次启动时丢失，且没有任何重试或提示。
2. 迁移结果不回写。`load()`（`:64-90`）末尾调用 `ConfigMigration.migrate(parsed)` 后直接返回，`version` 字段与迁移补丁只在下次 `edit` 时随整份配置写入。若用户升级后从未触发配置写入，每次启动都会重新执行迁移。
3. 高版本配置被就地改写。`core\data\...\ConfigMigration.kt:70-74` 在迁移结束后 `version != CURRENT_VERSION` 时记录「尚无迁移规则」并 `copy(version = CURRENT_VERSION)`。用户从更高版本降级安装时，配置文件中的版本号被改写成低版本号，后续再升级会跳过本应执行的迁移。
4. 内置密钥自动回填。`ConfigMigration.kt:87-96` 的 `ensureBuiltInIntegrations` 在 `wereadApiKey` 为空时写入内置密钥并把 `wereadEnabled` 置为 `true`，用户清空密钥后每次加载都会恢复。
5. 配置模型中的绝对路径。`Models.kt` 的 `AppearanceConfig.wallpaperPath`、`labelFontPath`、`DesktopConfig.widgetImagePath` 保存 `filesDir` 下的绝对路径，备份恢复、应用数据目录变化或路径迁移后这些字段失效，`AppearanceRepositoryImpl` 与 `FontsRepository` 均未在加载时校验文件存在性以外的兼容处理。

## 七、反射加载模块

五个功能模块通过固定类名反射实例化（`OctoberRainApp.kt:85-102`）：类名以字符串常量维护，实例化后强制转换为 `LauncherModule`，`install(context)` 完成后刷新设计运行时。失败处理只有日志。`app\proguard-rules.pro:2` 的 `-keep class com.shuiyu.octoberrain.** { *; }` 保留了全部自有类，因此当前 release 构建下反射目标不会被裁剪，混淆后类名变化也不会发生。该规则与「模块通过反射加载」是配套的，代价是 R8 对自有代码既不混淆也不裁剪，`InkLog` 注释中「发布构建由 R8 移除调试级调用」的描述缺少 `assumenosideeffects` 支撑，实际不会移除（`core\common\...\InkLog.kt`）。

若后续收紧 keep 规则，模块加载会静默失败。当前缺少模块接口版本校验、缺少加载结果汇总、缺少失败时的界面提示。feature 模块的类名与 `:app` 的硬编码清单之间没有编译期校验，重命名模块类不会产生构建错误。

## 八、全局可变单例

工程内进程级单例较多：`AppServices`、`DeviceRuntime`、`DesignRuntime`、`InkColors`、`InkColors` 的颜色字段、`RefreshScheduler`、`MediaBridge`、`NotificationCenter`、`ScreenRegistry`、`NegativeModuleRegistry`、`WidgetRegistry`、`AppServices` 的 19 个字段。

- `ScreenRegistry`（`Contracts.kt:292-307`）与 `NegativeModuleRegistry`（`:784-797`）为 `mutableListOf` 与 `LinkedHashMap`，注册无同步、无注销方法，重复安装模块会累积重复页面。
- `MediaBridge`（`core\data\...\MediaBridge.kt`，32 行）为 `object`，`install(handler)` 直接覆盖写入，没有注销约定。
- `DesignRuntime` 与 `DeviceRuntime` 用 `@Volatile lateinit var` 保存状态，未初始化时部分方法静默返回，部分方法抛 `UninitializedPropertyAccessException`，行为不一致。
- `DateTime` 相关工具未复用格式化器：`core\common\...\TimeUtils.kt` 的 `formatDay` 每次新建 `SimpleDateFormat`，`MinuteTicker`/`IntervalTicker` 的 Handler 与回调在调用方不调用 `stop()` 时长期持有外部对象。

这些单例对单进程桌面应用是可行的取舍，问题在于写入没有封装、就绪状态没有统一判断（见第二节第 1 条）。

## 九、密钥与 PIN 存储

1. 内置微信读书密钥硬编码：`core\common\src\main\java\com\shuiyu\octoberrain\core\common\Models.kt:10` 定义 `const val DEFAULT_WEREAD_API_KEY: String = "wrk-V1vh9k64QKGxnU4P0hUc9QAA"`，随 APK 分发，解包即可取得。该密钥同时作为 `IntegrationsConfig.wereadApiKey` 的默认值（`Models.kt` 的 `IntegrationsConfig` 部分），并参与备份导出（`BackupServiceImpl.kt:38-62` 的 `config.json` 条目）。
2. PIN 摘要强度：`LockConfig`（`Models.kt:259-263`）保存 `pinHash`、`salt`、`iterations = 1`；`LockGateImpl.kt:98-103` 的 `digest` 只做一次 `SHA-256(saltHex + pin)`，`iterations` 字段从未参与计算；`LockOverlayView.kt:112-121` 限制输入为 4 位数字，`:124-140` 校验失败仅清空输入并提示「密码错误，请重试」，没有失败次数上限、没有延时、没有锁定。4 位数字只有 1 万种组合，本地穷举成本极低。
3. 存储位置：以上字段以明文 JSON 存放于 SharedPreferences（`JsonConfigStore` 的 `octoberrain_config`），未使用 `EncryptedSharedPreferences` 或 Keystore；备份 zip 内同样明文包含这些字段。
4. 锁定状态仅存内存：`LockGateImpl.kt:24` 的 `_state = MutableStateFlow(false)`，只有 `lock()`（`:36-42`）在 `lockConfig.enabled && hasPin()` 时置 `true`，`lock()` 的调用来自 `MainActivity.kt:83-91` 的屏幕熄灭接收器；`MainActivity.kt:103` 在 `onCreate` 只根据 `isLocked` 决定是否显示 PIN 界面。进程被系统回收后重新进入桌面，`isLocked` 为 `false`，`autoLock` 开启状态下 PIN 界面不会出现。

## 十、构建配置与测试覆盖

构建脚本（`app\build.gradle.kts`，52 行）：

- `versionCode = 9`、`versionName = "0.9.0"` 硬编码（`:14-15`）。
- `debug` 不开启 minify；`release` 开启 `isMinifyEnabled = true`、`isShrinkResources = true`，`proguardFiles` 引用 `proguard-android-optimize.txt` 与本地规则（`:20-26` 附近，`:25`）。
- 全文件没有 `signingConfigs`，也没有 CI 或版本注入入口。
- 依赖以 `implementation` 引入 12 个工程模块（`:39` 起）；`libs.versions.toml` 只声明 AndroidX core-ktx、协程、序列化三项库，测试依赖（JUnit 4.13.2）直接写在各模块脚本内。
- `gradle.properties` 开启 `parallel`、`caching`，关闭 `configuration-cache`；无 lint 相关开关。

清单（`app\src\main\AndroidManifest.xml`，101 行）：

- `REQUEST_INSTALL_PACKAGES`（`:7`）、`MANAGE_EXTERNAL_STORAGE`（`:29`）、`QUERY_ALL_PACKAGES`（`:32`）、`PACKAGE_USAGE_STATS`、`SYSTEM_ALERT_WINDOW` 同时声明。
- `BLUETOOTH` 与 `BLUETOOTH_ADMIN` 未设置 `maxSdkVersion = 30`，在 API 31 以上仍会出现在权限声明中。
- `MainActivity` 声明 `clearTaskOnLaunch=true`、`launchMode=singleTask`、`stateNotNeeded=true`、`screenOrientation=behind` 与 `configChanges` 覆盖方向、尺寸、加密、密度等，HOME 与 LAUNCHER 两组 intent-filter 均存在。
- `NotificationRelayService` 与 `OverlayAccessibilityService` 均 `exported=true` 并绑定系统权限；`FileProvider` 的 `authorities` 为 `${applicationId}.files`，与 `UpdateServiceImpl` 中 `FileProvider.getUriForFile(context, "${packageName}.files", file)` 一致。
- `allowBackup=false`，与备份服务自行实现导出导入的设计一致。

测试现状：`core:common` 有 `LauncherConfigJsonTest`、`MonthGridTest`、`TimeUtilsTest`；`core:data` 有 `ConfigMigrationTest`、`IconPackResolverTest`；`core:design` 有 `ThemePaletteTest`、`ThemeTokensCodecTest`；`core:device`、`core:render`、`core:widget-api`、`app`、五个 feature、`service` 均无测试目录。没有 lint 规则调整、没有静态检查插件、没有 CI 配置文件。

## 十一、发现清单

编号规则：F1-F9 为高，F10-F19 为中，F20-F27 为低。每条给出位置、级别、问题、依据、建议。

**F1 内置微信读书密钥硬编码且被自动回填**
位置：`core\common\src\main\java\com\shuiyu\octoberrain\core\common\Models.kt:10`；`core\data\src\main\java\com\shuiyu\octoberrain\core\data\ConfigMigration.kt:87-96`。
级别：高。
问题：密钥常量随 APK 分发，任何解包者都能取得并直接调用第三方接口；用户清空密钥后每次加载都会被重新写入并重新启用功能。
依据：`const val DEFAULT_WEREAD_API_KEY: String = "wrk-V1vh9k64QKGxnU4P0hUc9QAA"`；`ensureBuiltInIntegrations` 中 `if (integrations.wereadApiKey.isNotBlank()) return config`，否则写入内置密钥并 `wereadEnabled = true`。
建议：移除内置密钥，功能默认关闭；密钥由用户输入，写入前做格式校验，并考虑迁出到加密存储。

**F2 PIN 摘要强度不足且无失败限制**
位置：`app\src\main\java\com\shuiyu\octoberrain\LockGateImpl.kt:58-71、98-103`；`app\src\main\java\com\shuiyu\octoberrain\LockOverlayView.kt:112-121、124-140`；`core\common\...\Models.kt:259-263`。
级别：高。
问题：4 位纯数字、单次 SHA-256、字段 `iterations` 未参与计算、校验失败无次数与延时限制，本地穷举 1 万种组合即可还原 PIN；摘要与盐以明文 JSON 存于 SharedPreferences 与备份包。
依据：`digest(salt, pin)` 返回单次 `MessageDigest.getInstance("SHA-256")` 结果；`LockConfig.iterations` 默认 1 且在 `LockGateImpl` 中未被读取；`LockOverlayView.submit` 失败分支只清空 `digits` 并提示。
建议：改用 PBKDF2 或 Argon2，把 `iterations` 纳入计算；失败累计后增加延时与次数锁定；凭据迁入 Keystore 保护的存储，备份中剔除。

**F3 锁门状态仅存内存，进程重启后可绕过**
位置：`app\src\main\java\com\shuiyu\octoberrain\LockGateImpl.kt:24、36-42`；`app\src\main\java\com\shuiyu\octoberrain\MainActivity.kt:83-91、103`。
级别：高。
问题：`_state` 初值为 `false`，只有屏幕熄灭广播触发 `onScreenOff()` → `lock()` 时置 `true`；进程被系统回收后重新启动，`isLocked` 为 `false`，`autoLock` 开启状态下也不会出现 PIN 界面。
依据：`private val _state = MutableStateFlow(false)`；`onCreate` 中 `if (AppServices.lock.isLocked) showLock()`；`lock()` 的唯一调用点在屏幕熄灭接收器内。
建议：启动装配阶段按 `lock.enabled && lock.autoLock` 恢复锁定状态；或把锁定状态持久化并在桌面恢复前检查。

**F4 启动路径存在未捕获异常**
位置：`core\data\src\main\java\com\shuiyu\octoberrain\core\data\SystemControlsImpl.kt:60-63、191-208`；`app\src\main\java\com\shuiyu\octoberrain\OctoberRainApp.kt:54`。
级别：高。
问题：`SystemControlsImpl` 在构造期注册接收器并同步执行 `refreshAll()`，其中 `updateWifi()` 未包异常，`manager.connectionInfo` 在缺少定位或附近设备权限的定制系统上可抛出 `SecurityException`，异常沿 `Application.onCreate` 传出形成启动崩溃。
依据：`init { register(); refreshAll() }`；`updateWifi` 内直接调用 `manager.isWifiEnabled` 与 `manager.connectionInfo`，同文件 `updateBluetooth` 与 `updateBattery` 均有 `runCatching`。
建议：构造器只做依赖注入，接收器注册与状态读取移到应用级协程的 `IO` 上下文；`updateWifi` 与其他系统查询统一包异常并保留降级值。

**F5 图标覆盖读取发生在 IO 上下文之外**
位置：`core\data\src\main\java\com\shuiyu\octoberrain\core\data\AppCatalogImpl.kt:56-105`；`core\data\...\IconOverrideStoreImpl.kt:105-126`。
级别：高。
问题：`loadIcon` 在进入 `withContext(Dispatchers.IO)` 之前先调用同步的 `loadOverride`，后者执行两次 `BitmapFactory` 解码、一次 `createBitmap` 裁切与 `createScaledBitmap` 缩放；主线程调用 `loadIcon` 时会出现磁盘读取与位图分配。
依据：`if (AppServices.hasIconOverrides()) { AppServices.iconOverrides.loadOverride(packageName, sizePx) }` 位于 `withContext` 之外；`loadOverride` 为普通函数，内部无协程切换。
建议：把覆盖图标的读取并入 `withContext(Dispatchers.IO)` 分支，或把 `loadOverride` 改为 `suspend` 并在内部切换上下文。

**F6 发布配置缺少签名，权限面超出常规分发要求**
位置：`app\build.gradle.kts:14-15、20-26`（全文件 52 行无 `signingConfigs`）；`app\src\main\AndroidManifest.xml:7、29、32`；`app\proguard-rules.pro:2`。
级别：高。
问题：release 构建未配置签名，产物无法直接分发；清单同时声明 `REQUEST_INSTALL_PACKAGES`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`PACKAGE_USAGE_STATS` 等受严格审查的权限；`-keep class com.shuiyu.octoberrain.** { *; }` 使 R8 对自有代码既不混淆也不裁剪，`minifyEnabled` 与 `shrinkResources` 的实际收益仅剩第三方依赖。
依据：`buildTypes` 内只有 `debug` 与 `release` 两个块，均无 `signingConfig`；`proguard-rules.pro` 第 2 行保留全部自有类；`InkLog.kt` 注释声称发布构建会移除调试日志，但规则中没有 `-assumenosideeffects`。
建议：补充签名配置或明确由 CI 注入；按功能收敛权限，`QUERY_ALL_PACKAGES` 考虑改用 `<queries>` 声明；keep 规则收窄到反射入口（五个 `*Module` 类）、序列化模型与 `WidgetProvider` 实现，并添加 `-assumenosideeffects class android.util.Log`。

**F7 更新通道校验薄弱**
位置：`core\data\src\main\java\com\shuiyu\octoberrain\core\data\UpdateServiceImpl.kt:24-53、55-67、69-82、131-138`。
级别：中。
问题：清单与安装包同源获取，`sha256` 为空时跳过校验，安装前不校验 APK 签名；`openConnection` 允许明文 `http`；下载过程无字节上限，`sizeBytes` 只用于展示。
依据：`info.sha256.isNotBlank()` 才比对；`install` 直接 `ACTION_VIEW` 拉起安装器；`httpGet` 使用 `HttpURLConnection` 并设置 15 秒超时，未强制 `https`。
建议：强制 `https` 并校验清单签名；下载完成后校验签名证书指纹与 `versionCode`；设置下载字节上限并在 `sizeBytes` 不符时终止。

**F8 备份包包含凭据且导入无上限**
位置：`core\data\src\main\java\com\shuiyu\octoberrain\core\data\BackupServiceImpl.kt:38-62、84-103、144-157`。
级别：中。
问题：导出 zip 的 `config.json` 含 `lock.pinHash/salt/iterations` 与 `integrations.wereadApiKey`；导入用 `ZipInputStream` 逐条 `readBytes()`，没有条目数、单条大小与总大小限制，恶意压缩包可耗尽内存；壁纸、字体、图标覆盖只保存绝对路径，恢复后资源缺失。
依据：`config.json` 为完整 `LauncherConfig` 序列化结果；`readEntries` 中 `result[entry.name] = zip.readBytes()`；`theme.json` 与 `manifest.json` 之外的资源未打包含入。
建议：导出前剔除凭据字段；导入时限制条目数、单条与总量，校验条目名；把壁纸、字体、图标等资源打包入 zip 并在恢复时重写路径。

**F9 通知列表读改写非原子**
位置：`core\data\src\main\java\com\shuiyu\octoberrain\core\data\NotificationCenter.kt:103、120、134、145`。
级别：中。
问题：`push`、`replaceAll`、`dismiss`、`dismissAll` 均读取 `_items.value`、计算新列表后整体写回，没有同步或 `update` 事务；通知监听服务线程与界面线程并发时后写的覆盖先写的。
依据：`_items.value = next.sortedByDescending { it.postedAt }.take(MAX_ITEMS)` 等语句全部为「读—算—写」三步；`_items` 为普通 `MutableStateFlow`。
建议：改用 `_items.update { ... }`，或为写入加互斥并让服务层串行提交。

**F10 配置写入与迁移不完整**
位置：`core\data\src\main\java\com\shuiyu\octoberrain\core\data\JsonConfigStore.kt:42-56、64-90、101-115`；`core\data\...\ConfigMigration.kt:70-74`。
级别：中。
问题：先更新内存再 `apply()` 异步写盘，写盘失败只记日志，内存与磁盘分叉且无重试；迁移结果不回写，未发生配置编辑时每次启动重复迁移；版本高于当前时版本字段被改写成 `CURRENT_VERSION`，降级安装会破坏版本记录。
依据：`edit` 中 `_state.value = next` 在前、`preferences.edit()...apply()` 在后、失败分支只有 `InkLog.e`；`migrate` 末尾 `copy(version = CURRENT_VERSION)`。
建议：写盘成功后再发布新状态或失败时回滚；迁移完成后立即持久化；高版本配置保留原版本号并记录降级标记。

**F11 反射加载模块失败静默**
位置：`app\src\main\java\com\shuiyu\octoberrain\OctoberRainApp.kt:85-102`。
级别：中。
问题：五个模块类名硬编码为字符串常量，实例化或 `install` 抛异常时只记日志；模块缺失时界面为空桌面或无内容页，缺少提示；类名与 feature 模块之间没有编译期校验。
依据：`Class.forName(name)`、`getDeclaredConstructor().newInstance()`、`install` 均在 `runCatching` 内，失败分支为 `InkLog.w("模块", "加载 $name 失败", it)`。
建议：把模块清单改为编译期可校验的注册表（`LauncherModule` 的 service loader 或显式引用），加载结果汇总为可观察状态，失败时在设置页与启动日志中呈现。

**F12 全局服务写入无保护**
位置：`core\common\src\main\java\com\shuiyu\octoberrain\core\common\Contracts.kt:444-463、466-483`。
级别：中。
问题：19 个 `lateinit var` 可被任意模块重新赋值，注释声称「之后全局只读」；`isReady()` 遗漏 `layoutProvider` 与 `previewProvider`，其他调用方使用 `hasLayoutProvider()` 自行判断，就绪判断分裂。
依据：字段声明全部为 `lateinit var`；`isReady` 的返回链止于 `themes`（`:483`）。
建议：装配完成后封装为不可变持有对象或通过受控方法赋值；`isReady()` 覆盖全部字段并作为唯一就绪判断入口。

**F13 系统接收器无释放时机**
位置：`core\data\...\SystemControlsImpl.kt:60-63、158-160`。
级别：中。
问题：`dispose()` 在全仓库无调用方，接收器随进程常驻；`SystemControlsImpl` 由 `Application` 构造，生命周期与进程一致，接口上的释放约定形同虚设。
依据：检索 `.kt` 文件中的 `dispose()` 仅命中定义一处；`init` 块内 `register()`。
建议：移除 `dispose()` 或改为进程级显式管理；如保留，在 `Application.onTerminate()` 与测试中调用。

**F14 前光判定与亮度键写入缺少机型校验**
位置：`core\device\src\main\java\com\shuiyu\octoberrain\core\device\DeviceProfileDetector.kt:17-21、51、75-81`；`core\data\...\SystemControlsImpl.kt:226-240、243-249`。
级别：中。
问题：`hasFrontLight` 直接取 `display.isEink`，无前光的墨水屏设备同样被判定为有前光；`setFrontLight` 把 0..10 映射为 0..255 后写入 `screen_brightness` 与 `screen_brightness1`，不具备前光硬件或键名不同的机型上会改写系统亮度；`EINK_MARKERS` 中的 `s4`、`s5`、`s6` 两字符片段容易命中无关机型字符串。
依据：`hasFrontLight = display.isEink`；`frontLightHardwareAvailable()` 在 `DeviceRuntime` 未初始化时返回 `true`；键常量 `screen_brightness`、`screen_brightness1`。
建议：前光能力改为机型白名单或读取系统节点；写入前探测键是否存在并保留原值；短标识改为按词边界匹配。

**F15 无障碍服务反射隐藏接口**
位置：`service\src\main\java\com\shuiyu\octoberrain\service\OverlayAccessibilityService.kt:323-342`。
级别：中。
问题：通过反射调用隐藏接口 `injectInputEvent`，Android 9 以上的非 SDK 接口限制可能使调用失败，失败返回 `false` 后音量键翻转静默失效；同时该功能依赖无障碍服务过滤按键，行为对用户不透明。
依据：`input.javaClass.methods.firstOrNull { it.name == "injectInputEvent" && it.parameterTypes.size == 2 }` 后 `method.isAccessible = true` 与 `method.invoke(...)`。
建议：改用公开 API 或直接调整音量键语义；在设置页标注功能依赖无障碍服务；失败时给出可见提示。目标机型表现待确认。

**F16 第三方图标包资源加载未校验来源**
位置：`core\data\...\IconPackResolver.kt:78-91、94-120、160-182`。
级别：中。
问题：`createPackageContext(packId, Context.CONTEXT_IGNORE_SECURITY)` 加载任意已安装图标包资源，未校验包签名与来源；`appfilter.xml` 解析无条目上限，切包后重建索引；`launchActivities` 缓存无上限。
依据：`packContext()` 中的 `CONTEXT_IGNORE_SECURITY`；`readAppFilter` 使用 `Xml.newPullParser` 全量读入 `PackIndex`；`cachedPack`/`cachedIndex` 单份缓存。
建议：校验图标包签名或限制在已知包名集合内；appfilter 条目数设上限；缓存改为有界结构。

**F17 主题应用语义不一致**
位置：`core\data\src\main\java\com\shuiyu\octoberrain\core\data\ThemeRepositoryImpl.kt:115-159`（`applyTheme`）、`:245-`（`importTheme`）。
级别：中。
问题：`applyTheme` 读取 `theme.json` 后只复制图标、覆盖布局字段，不写入 `themeId` 与 `themeTokensJson`，导入主题的配色在应用后不会改变，只有经 `importTheme` 直接写入的令牌生效；壁纸不在主题范围内。
依据：`applyTheme` 的配置写入为 `order/columns/rows/columnSpacingDp/rowSpacingDp/iconSizeDp/labelTextSp/firstPageRows/gridAlign`，未见 `themeTokensJson` 或 `themeId` 赋值；`DesignRuntime.applyThemeTokens` 只在 `themeTokensJson` 非空时使用主题令牌。
建议：把主题令牌与 `themeId` 纳入 `applyTheme`，或在界面明确「主题只应用图标与布局」。具体行为待确认。

**F18 主线程字体解析**
位置：`core\design\...\DesignRuntime.kt` 的 `install` 与 `update`；调用点 `app\src\main\java\com\shuiyu\octoberrain\OctoberRainApp.kt:52、115-120`。
级别：低。
问题：`DesignRuntime.install` 在 `Application.onCreate` 主线程执行 `fonts?.labelTypeface()`，由 `Typeface.createFromFile` 解析字体文件；外观变化时配置收集器在主线程再次触发同一过程
依据：`install` 中 `labelTypefaceCache = fonts?.labelTypeface()`；收集器为 `scope.launch { config.state.map { it.appearance }.distinctUntilChanged().collect { DesignRuntime.update(it) } }`。
建议：字体解析移入 `Dispatchers.IO`，完成后再发布令牌；启动阶段使用内置字体占位。

**F19 应用目录与缓存无清理**
位置：`core\data\...\AppCatalogImpl.kt:46、56-105`；`core\data\...\IconOverrideStoreImpl.kt:105-126`；`core\data\...\ThemeRepositoryImpl.kt` 的图标副本目录。
级别：低。
问题：`filesDir/icons` 按 `包名-sizePx.png` 存放，永不清除；`icon-overrides` 与主题目录下的图标副本只增不减；内存缓存按条数而非字节计。
依据：磁盘缓存文件名包含 `sizePx`；`cacheAndPersist` 无清理分支；`LruCache<String, Bitmap>(48)`。
建议：为图标目录设置 LRU 清理或容量上限；内存缓存改为按字节计量。

**F20 页面状态无保存**
位置：`app\src\main\java\com\shuiyu\octoberrain\MainActivity.kt:61-63、471-483`。
级别：低。
问题：页面栈与当前页只存在内存中，没有 `onSaveInstanceState`；进程回收后重新进入回到首页，多级设置页的浏览状态丢失。`configChanges` 覆盖了方向与尺寸变化，旋转不会重建，因此该问题只在进程回收时出现。
依据：`private val stack = ArrayDeque<Entry>()`；全文件检索无 `onSaveInstanceState`。
建议：把页面栈的 `Destination` 序列化进 `onSaveInstanceState` 并从恢复状态重建。

**F21 绘制辅助与注释不符**
位置：`core\render\src\main\java\com\shuiyu\octoberrain\core\render\InkCanvas.kt:61-63`。
级别：低。
问题：`fillInverted` 的注释描述为「先铺底色再以纸白绘制文字」，实现只按给定颜色填充矩形，未涉及文字与纸白。
依据：方法体只有一次 `canvas.drawRect` 与 `paint` 颜色设置。
建议：修正注释，或补齐实现。

**F22 全局颜色写入非原子**
位置：`core\design\src\main\java\com\shuiyu\octoberrain\core\design\InkColors.kt:26-58` 附近的 `apply`。
级别：低。
问题：`apply(palette)` 逐字段写入 8 个静态颜色值，重绘线程可能读取到新旧混合的配色，表现为单帧颜色跳变；`reset()` 同样逐字段回填。
依据：8 个 `@Volatile` 字段逐个赋值。
建议：以不可变配色对象整体替换引用，绘制侧读取同一个快照。

**F23 日期格式化器重复创建**
位置：`core\common\src\main\java\com\shuiyu\octoberrain\core\common\TimeUtils.kt:64-65`（`formatDay`），同类问题见 `:61`（`formatDateTime`）。
级别：低。
问题：每次调用新建 `SimpleDateFormat`，在列表与状态栏高频刷新路径上产生额外对象分配。
依据：`formatDay` 方法体内 `SimpleDateFormat(...)` 构造。
建议：改为线程内复用的格式化器或改用 `java.time` 的格式化器缓存。

**F24 动画与计时器依赖调用方停止**
位置：`core\common\...\TimeUtils.kt` 的 `MinuteTicker` 与 `IntervalTicker`。
级别：低。
问题：两者持有主线程 `Handler` 与外部回调，调用方未在 `onPause`/`onDestroy` 调用 `stop()` 时回调持续执行并持有外部对象；`MainActivity` 已正确停止，其他界面需逐个确认。
依据：两个类均有 `private val handler = Handler(Looper.getMainLooper())` 与 `running` 标志，`stop()` 移除回调。
建议：在基类或 `BaseScreen` 中统一托管计时器生命周期。

**F25 测试覆盖不均**
位置：`core\device`、`core\render`、`core\widget-api`、`app`、五个 feature、`service` 均无 `src\test` 目录；有测试的模块共 7 个测试类。
级别：低。
问题：设备画像判定、位图采样与抖动、刷新调度、配置存储与备份导入均无自动化测试；`ConfigMigrationTest` 覆盖迁移，但 `JsonConfigStore` 的容错分支与 `ThemeTokensCodec.decode` 的非法颜色分支无法通过现有测试验证。
依据：`ops_glob` 的 `*Test.kt` 清单；各模块 `build.gradle.kts` 中测试依赖分布。
建议：优先为 `DeviceProfileDetector`、`BitmapPipe.sampleSize`、`Ditherer`、`JsonConfigStore.readSection` 补充单元测试，并接入 CI。

**F26 构建版本号与依赖声明分散**
位置：`app\build.gradle.kts:14-15`；`gradle\libs.versions.toml`。
级别：低。
问题：`versionCode` 与 `versionName` 硬编码在模块脚本；JUnit 版本写在各模块脚本内而非版本目录；没有 CI 与发布流程配置。
依据：构建脚本行号；`libs.versions.toml` 只声明三项库。
建议：版本号迁入版本目录或由 CI 注入，测试依赖统一进版本目录。

**F27 清单声明与权限适配**
位置：`app\src\main\AndroidManifest.xml:5-32`。
级别：低。
问题：`BLUETOOTH` 与 `BLUETOOTH_ADMIN` 未设置 `maxSdkVersion = 30`，`READ_EXTERNAL_STORAGE` 与 `WRITE_EXTERNAL_STORAGE` 分别设置了 32 与 28，`MANAGE_EXTERNAL_STORAGE` 同时存在，存储权限声明三层叠加；`PACKAGE_USAGE_STATS` 为特殊权限，清单声明之外还依赖用户手动授权。
依据：清单行号与权限条目。
建议：`BLUETOOTH` 系列补 `maxSdkVersion`；存储访问统一走 `MANAGE_EXTERNAL_STORAGE` 或 MediaStore 其中一种；用量统计权限在首次使用处引导授权。

## 十二、值得肯定之处

1. 模块依赖方向清晰。`core:common` 无 UI 依赖，`core:design` 的 `ThemePalette` 为纯 JVM 数据类并有单元测试，`core:device`、`core:render` 只依赖下层，`:app` 作为唯一装配层聚合全部模块，符合 13 模块的划分初衷（`settings.gradle.kts`、各模块 `build.gradle.kts`）。
2. 装配集中且可读。`OctoberRainApp.onCreate`（`app\...\OctoberRainApp.kt:41-82`）把设备画像、设计运行时、数据服务、宿主服务、功能模块的装配顺序写成线性序列，配合 `AppServices` 的单一赋值入口，便于按顺序排查初始化问题；`DesignRuntime` 与 `DeviceRuntime` 在 `Application` 首个阶段安装，早于任何界面创建。
3. 配置容错分层。`JsonConfigStore.load` 对整体损坏与单节损坏分别处理（`:64-90`、`:101-115`），`ConfigMigration` 为无 IO 的纯函数并配有 `ConfigMigrationTest`，升级路径有据可查。
4. 生命周期收尾较完整。`MainActivity.onDestroy`（`:471-483`）与 `NotificationRelayService.onDestroy`（`:74-81`）对称地释放了接收器、计时器、协程订阅、媒体回调与全局处理器，未发现 Activity 泄漏到进程级单例的直接引用。
5. 墨水屏渲染的抽象层次合适。`DeviceProfile` 不可变并按 `SizeClass`、`HeightClass`、`DisplayClass` 三个断点收敛界面尺寸，`InkTokens` 统一 dp、sp 与笔触宽度，`BitmapPipe.sampleSize` 有像素总量上限，`RefreshScheduler` 合并同帧重绘并按间隔调度全刷，渲染适配逻辑集中在两个模块内，便于逐项调整。

## 十三、未覆盖范围与待确认事项

1. feature 模块（`feature:home`、`feature:widgets`、`feature:tray`、`feature:settings`、`feature:content`）的界面实现未纳入本次复核，模块加载失败后的界面表现、`PlaceholderContentSource` 与真实实现的替换顺序需结合实际运行确认。
2. `ThemeRepositoryImpl.applyTheme` 是否在其他调用点补写 `themeTokensJson`，以及导入主题后重启是否保持配色，需在设备上验证。
3. `DeviceProfileDetector` 的机型标识在目标设备上的命中情况、`hasFrontLight` 的实际取值，需在真机读取 `Build` 字段后确认。
4. `OverlayAccessibilityService` 反射隐藏接口在目标 Android 版本上的可用性，需在设备上观察。
5. 本次未运行单元测试与 lint，测试有效性与静态检查结果以构建机实际输出为准。
