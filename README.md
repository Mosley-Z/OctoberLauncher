# 十雨桌面

> 开发接手从 [PROJECT.md](PROJECT.md) 开始；协作约定见 [AGENTS.md](AGENTS.md)，阅读器任务见 [task](task/README.md)，设计/验收分类索引见 [docs](docs/README.md)。下文历史版本与计数应按当前源码核实。

面向安卓墨水屏设备的桌面启动器，包名 `com.shuiyu.octoberrain` 与 `applicationId` 一致。版本以 `VERSION` 和 `app/build.gradle.kts` 为准。功能覆盖上游 TOMO 0.5.0 的主要能力，并新增文件管理、图片查看编辑与微信读书页面；屏幕硬门禁已移除，验证基线的墨水屏为 S4 480×800（Android 11）。

工程为原创实现，包名、类名、资源名、域名与 FileProvider authority 均与上游无共同片段；交互形态与功能范围参考上游，融合部分的来源与许可见 `docs/design/17-weread-fusion.md` 与 `feature/settings/.../ThirdPartyLicenses.kt`。

## 模块

工程由壳工程 `:app`、`core` 库、`feature` 库与 `:service` 组成，实际模块清单以 `settings.gradle.kts` 为准。依赖方向为 `app` 依赖各 feature 与 service，`feature` 依赖 `core`，`core` 内部单向依赖，模块之间不横向引用。模块说明见各目录下 `README.md`。

| 工程 | 职责 |
| --- | --- |
| `app` | Application、单 Activity、页面栈、路由、锁屏覆盖、引导、FileProvider 与清单 |
| `core:common` | 配置与数据模型、跨模块契约、`AppServices`、JSON 与时间工具、PIN 摘要 |
| `core:design` | 设计令牌、配色、通用控件、字形、对话框、主题编解码 |
| `core:device` | 设备画像、尺寸分级、显示类型、前光探测、主屏角色、权限中心 |
| `core:render` | 位图解码与采样、抖动、缩略图缓存、刷新调度、画布封装 |
| `core:data` | 配置存储与迁移、应用目录、壁纸、图标包、主题、通知、系统控制、更新与备份 |
| `core:widget-api` | 组件协议、宿主协议、分类与刷新策略、运行时注册表 |
| `feature:home` | 桌面宫格与列表、分页、整理、组件宿主、负一屏、布局度量 |
| `feature:widgets` | 18 条组件声明与实现、组件选择页、微信读书组件数据整理 |
| `feature:tray` | 状态栏、快捷托盘、通知列表、托盘偏好 |
| `feature:settings` | 设置首页与分组页、设置声明表、美化模式、各类选择器 |
| `feature:content` | 书架、微信读书、KOReader、天气、使用统计、最近使用与负一屏书架卡片 |
| `feature:files` | 文件浏览、相册、图片查看、图片编辑、外部查看与选图入口 |
| `feature:reader` | 离线文本/PDF/Markdown/EPUB/ZIP、受预算Office、统一搜索、进度与书签 |
| `service` | 通知监听服务、无障碍悬浮层服务与悬浮状态栏 |

## 构建

工具链放在 `D:\DSHStation\.toolchain`，包含 JDK 17、Android SDK 与 Gradle 8.11.1。工程不复制工具链，根目录 `toolchain.path` 指明位置，换机器只改该文件。工程没有 Gradle Wrapper，命令统一使用工具链中的 `gradle.bat` 或构建脚本。

```powershell
# 整包构建，脚本读取 toolchain.path 并设置 JAVA_HOME、ANDROID_HOME、GRADLE_USER_HOME
pwsh -NoProfile -File scripts\build.ps1 -Variant debug
pwsh -NoProfile -File scripts\build.ps1 -Variant release -Offline

# 执行 Gradle 任务
& "D:\DSHStation\.toolchain\gradle\gradle-8.11.1\bin\gradle.bat" :app:assembleDebug :app:assembleRelease --offline

# 装机并拉起主界面
pwsh -NoProfile -File scripts\install.ps1 -Serial SA4B03CN2608081096X
```

首次构建需要网络解析插件标记，依赖进入共享缓存后可加 `--offline` 或 `-Offline`。release 开启 R8 与资源压缩，混淆规则集中在 `app/proguard-rules.pro`，只保留反射入口、序列化生成物与组件协议实现三类；按决策 D7 沿用调试签名配置，正式发布前替换签名与密钥。

## 门禁

提交前按顺序执行构建、测试与静态检查，三项均通过方可交付。静态检查自 0.9.2 起配置为失败即中止，新增告警先修复，确需更新基线时在同一次改动中说明理由。

```powershell
& $gradle :app:assembleDebug :app:assembleRelease --offline
& $gradle testDebugUnitTest --offline
& $gradle ktlintCheck detekt --offline
& $gradle check --offline
```

`check` 一次执行 ktlint、detekt、单元测试与 Android Lint。detekt 与 Android Lint 按模块维护基线，存量问题分别 237 条与 72 条，新增代码不进入基线。测试为纯 JVM 单元测试，当前 52 个测试类、469 例、1 例跳过，不设 `androidTest`。

## 版本与快照

版本号写在根 `VERSION` 与 `app/build.gradle.kts` 的 `versionName`、`versionCode`，变更记录在 `CHANGELOG.md`，条目后标注对应任务编号。每个交付版本生成快照：

```powershell
pwsh -NoProfile -File scripts\snapshot.ps1 -Tag 1.0.0
```

快照写入 `D:\DSHStation\_snapshots\OctoberLauncher-<版本>`，包含文件清单与摘要，供版本比对与回退。发布包与验收记录同时归档在 `_analysis/verify/`。

## 设备与权限

最小支持 Android 8.0（API 26），目标 API 35。基准形态为 480×800 墨水屏，适配目标三档：480×800、552×768、824×1648，断点按窗口宽度取 compact、medium、expanded，尺寸换算集中在 `DeviceProfile` 与 `InkTokens`，布局不使用写死像素。

桌面注册为默认主屏需要用户确认 HOME 角色；通知读取、无障碍、悬浮窗、精确闹钟、使用情况访问按需申请，均可跳过且不影响桌面基本功能。存储访问在 API 30 以上申请 `MANAGE_EXTERNAL_STORAGE`，用于书架、图片与文件页对公共目录的读写；API 26 至 28 走运行时存储权限分支，该分支未在真机验证。

## 文档

`docs/` 下的编号文档按主题排列，入口为 [docs/README.md](docs/README.md)。

| 文档 | 内容 |
| --- | --- |
| `docs/design/00-overview.md` | 工程总览与三条主线 |
| `docs/design/01-feature-inventory.md` | 按模块的功能与实现文件清单 |
| `docs/design/02-current-architecture.md` | 当前结构剖析 |
| `docs/design/03-target-architecture.md` | 目标架构与实现对照 |
| `docs/design/04-identity-removal.md` | 上游身份隔离清单 |
| `docs/design/05-device-adaptation.md` | 设备与断点适配方案 |
| `docs/design/06-development-roadmap.md` | 版本路线 |
| `docs/design/07-upstream-code-map.md` | 上游代码索引 |
| `docs/design/08-engineering-conventions.md` | 工程规范 |
| `docs/acceptance/09-M0骨架记录.md` | M0 骨架记录 |
| `docs/design/10-dev-handoff.md` | 开发交接 |
| `docs/design/11-552x768-adaptation.md` | 552×768 适配记录 |
| `docs/acceptance/12-验收记录.md` | 验收记录 |
| `docs/design/13-inkOS对照评估.md` | inkOS 对照评估 |
| `docs/acceptance/14-本批实施记录.md` | 本批实施记录 |
| `docs/design/15-V1.0开发计划.md` | V1.0 计划与任务编号 |
| `docs/design/16-墨盘移植决策.md` | 墨盘移植决策 |
| `docs/design/16-files-module.md` | 文件模块设计 |
| `docs/design/17-weread-fusion.md` | 微信读书融合实现说明 |

质量记录写入 `_analysis/verify/`：每版本静态检查、发布验证与真机截图按版本号归档，团队任务的中间报告在 `_analysis/team/`，快照与上游对照材料在 `_shots/` 与 `_tools/`。

## 使用与界面

桌面提供宫格与列表两种形态，左右滑动或音量键翻页，长按图标进入整理菜单，长按空白处打开页面菜单与美化模式入口。状态栏点击空白处展开快捷托盘，托盘内可调冷光与暖光、切换无线开关、查看通知与进入负一屏。

设置中心按卡片分组：书架设置、桌面偏好、美化与图标、网络与互联网、蓝牙与连接、应用管理、电源与显示、存储空间，其余卡片跳转系统设置页。桌面偏好再分外观、桌面与布局、快捷托盘、通知展示、钟面样式、锁屏与安全、权限与授权、内容与数据。

负一屏为模块化宿主，当前提供书架卡片、最近图片卡片与统计入口，模块顺序在配置中决定。锁屏为四位 PIN 覆盖层，启用后按屏幕关闭时机自动锁定。

## 扩展方式

新增桌面组件实现 `WidgetProvider` 与 `BaseWidgetView`，在所属模块的 `install` 中调用 `WidgetRegistry.register`，宿主会自动出现在组件选择页并按配置创建，不需要修改桌面代码。

新增设置页实现 `Screen` 与 `ScreenProvider` 并注册到 `ScreenRegistry`；新增设置项在对应 `*Specs.kt` 追加一条声明并指定生效方式。

新增内容来源实现 `ContentSource` 后在 `ContentModule` 中替换 `AppServices.content`；新增负一屏卡片实现 `NegativeModule` 并注册到 `NegativeModuleRegistry`。

模块入口类为 `HomeModule`、`WidgetsModule`、`TrayModule`、`SettingsModule`、`ContentModule`、`FilesModule`，由 `OctoberRainApp.installModules` 反射加载，混淆规则须保留对应类名与无参构造。

## 目录结构

```
app                     壳工程：Application、单 Activity、路由、锁屏覆盖、引导、清单
core/                   六个库模块：契约、设计令牌、设备画像、渲染、数据实现、组件协议
feature/                六个业务模块：桌面、组件、托盘、设置、内容、文件与图片
service/                通知监听与无障碍服务
docs/                   编号文档，入口 00-overview.md
scripts/                build、install、snapshot、verify-screens 四个 PowerShell 脚本
_analysis/              逆向产物、验证报告、团队任务报告与真机截图
_shots/                 上游与 inkOS 的界面截图对照
_tools/                 解析工具与上游对照材料
toolchain.path          工具链位置，换机器只改这一行
```

## 已知限制

壁纸设置条目 8.2 只完成裁切、量化与替换，替换后的复核与旧文件清理未完成。

组件 `refreshPolicy` 的事件档尚无宿主调度，能力声明未接入宿主过滤。

552×768 与 824×1648 只有单元测试与显示尺寸覆写验证，未在实体机复核；API 26 至 28 的存储与使用统计分支未实测。

调度入口未抽统一 `IoDispatcher`，协程默认使用 `Dispatchers.IO`；覆盖率门槛、截图测试与 UIAutomator 冒烟未接入。

`app/proguard-rules.pro` 已补齐六个模块的保留规则，新增模块时须同步追加，否则 release 包反射加载失败。

## 许可与来源

本工程业务代码为自有实现。阅读器原样打包固定docx-preview/JSZip、SheetJS CE和PPTXjs完整资源链；来源与摘要分别见 `feature/reader/src/main/assets/reader/licenses/docx-assets.json` 、`reader/web/sheets/LICENSES.txt`及`reader/web/slides/vendor-manifest.json`，完整许可随包保留；没有复制Gander业务Kotlin代码。实际支持与预算边界见[Office与搜索验收](docs/acceptance/2026-10-23-reader-office-search-integration.md)。其他上游对照材料（`_tools/upstream/` 中的 APK 与逆向产物）仅用于行为对照，不参与构建与运行；第三方许可入口为 `feature/settings/.../ThirdPartyLicenses.kt`，微信读书融合来源与边界见 `docs/design/17-weread-fusion.md`。
