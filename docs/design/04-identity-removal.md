# 身份脱敏与差异化实现边界

本文说明 tomo-launcher 0.5.0 在成品中留下的可检出痕迹，以及新工程逐项处理的方式。判定依据为 `_analysis\jadx-out` 全量反编译产物，涉及行号均为该目录内文件。

## 一、可检出面清点

| 类别 | 具体痕迹 | 出现位置 | 检出难度 |
| --- | --- | --- | --- |
| 包标识 | `com.astraabove.tomo` | AndroidManifest、全部源码、FileProvider authorities `com.astraabove.tomo.files`、自定义权限 `com.astraabove.tomo.permission.WRITE_FRONT_LIGHT` | 静态一眼可见 |
| 类名族 | `TomoChrome`、`TomoHttp`、`TomoWorkers`、`TomoPermissions`、`TomoPermission`、`TomoForeground`、`TomoNotificationService`、`TomoAccessibilityService` | 源码与 DEX 字符串表 | 静态可见 |
| Kotlin 元数据 | `@Metadata` 注解保留源文件名，如 `SupportedScreen.kt`、`EasterEgg.kt`；`resources\kotlin-tooling-metadata.json` 保留构建工具链版本；`resources\DebugProbesKt.bin` 表明开启协程调试探针 | classes.dex、resources 根 | 用 apkanalyzer 或 jadx 可见 |
| 资源名 | 样式 `Theme.TomoLauncher`、`Theme.TomoLauncher.Dialog`、`Theme.TomoLauncher.WidgetLibrary`、`Animation.TomoLauncher.None`、`TomoDialogRow`、`TomoPinKey`、`TomoTrayTile`；无障碍配置 `xml\tomo_accessibility_service.xml` | `res\values\styles.xml`、`res\xml\` | 静态可见 |
| 文案 | `app_name` 图墨桌面、`about_body` 图墨桌面、`pref_about` 关于图墨、`pref_tomo_files` 图墨文件管理、`pref_tomo_tray` 使用图墨下拉栏 | `res\values\strings.xml` | 静态可见 |
| 域名与邮箱 | `astraabove.com/tomo/`、`astraabove.com/tomo/update.json`、`astraabove.com/usetomo/changelog.json`、`support@astraabove.com` | `data\UpdateFeed.java`、`data\UpdateClient.java`、`res\values\strings.xml` | 抓包与字符串扫描可见 |
| 更新包文件名 | `tomo-launcher-<versionName>.apk` | `cacheDir\updates` 下载逻辑 | 运行期可见 |
| 标识串 | User-Agent `TomoLauncher/0.4.2`（`data\TomoHttp.java:27`）与 `TomoLauncher/1.0`（`data\UpdateClient.java:47`） | 网络请求头 | 抓包可见 |
| 字体资产 | `assets\fonts\` 下 bitter、fredoka、lexend、literata、oxanium、space grotesk 的子集字体与 `noto_serif_sc_medium_files.ttf`，附 `OFL.txt` | APK 内 assets | 解包可见 |
| 图标资产 | `assets\icons\` 39 组 `bm_*.bmp` 单色两态图标；`ic_settings_wordmark.png`、`ic_settings_logo.png`、`easter_egg.png`；`ic_launcher.xml` 黑色圆角方底加白色折角字形 | drawable-nodpi、assets | 解包可见 |
| 布局与尺寸 | `desktop_grid_height` 等以 px 为单位围绕 480×800 命名与取值；`data\IconPack.java:22-27` 硬编码圆角 22px、线宽 3px | `res\values\dimens.xml` | 静态可见 |
| 交互指纹 | 连续点击触发彩蛋（`ui\EasterEgg.java`，阈值 5、展示 2000ms）、三击 logo 进入无障碍引导（`DarkThemeUnlockActivity`）、两阶段长按返回、下拉托盘、非 480×800 机型跳转 `com.wetao.elauncher` 并退出 | 源码与运行期 | 行为比对可见 |
| 数据目录名 | `tomo_` 前缀的 15 个 SharedPreferences 文件、`desk_widget_image.img`、`theme_pack_apply` 等 | 运行期文件系统 | 运行期可见 |

## 二、分级处理方式

**必须替换。** 包名与 applicationId 改为自有命名空间；`Tomo*` 类名族按模块语义重命名，例如网络封装改 `net.HttpGateway`、工作线程池改 `core.IoExecutor`、通知与无障碍服务改 `service.TrayListener` 与 `service.EdgeOverlay`；资源名去掉品牌词；文案全部重写；域名、邮箱与更新包命名改用自建通道；User-Agent 统一为自有客户端标识；无障碍配置文件名与 `FileProvider` authorities 同步更名。

**建议替换。** 彩蛋与三击入口属于行为层指纹，成品中改为另一套触发方式或整体取消；托盘与长按手势的时序参数（间隔、阈值、判定窗口）调整到不同取值；字体改用另一组开源字体，避免字面组合同源；图标按自有图形语言重绘，覆盖包内 `assets\icons` 的 39 个应用别名与设置页字标。

**允许保留。** SharedPreferences 存储机制、协程调度、ViewBinding、单线程 io 池、SHA-256 校验更新包、BMP 单色图标思路，均为通用工程做法，单独出现不构成同源证据。

## 三、构建期手段

开启 R8 代码混淆与资源压缩，保留规则限定到最小集合；移除 `kotlin-tooling-metadata.json` 与 `DebugProbesKt.bin`；关闭 APK 内的构建位置与调试信息；签名使用自建证书，versionCode 从高位起算，避免与上游版本号序列重叠。

资源名混淆可用 AGP 的资源压缩与 `--shorten-resource-paths` 组合，退而求其次可用 andResGuard 处理 `res\` 目录名。字体与图标资产在替换后重新打包，资源哈希与原包全部不同。

## 四、代码层面的原创实现原则

本方案以**功能清单、交互规格与数据结构**为输入重写全部代码，逐类实现等价行为，不搬运字节码、不复制布局文件、不复制字符串资源。上游 APK 内的用户协议正文声明「著作权归开发者所有。未经书面许可，禁止破解、反编译、二次开发、修改后传播或用于商业用途」，因此成品在代码与资源两侧都与原包保持零复用，仅在功能层面覆盖同等能力。

字体选用 SIL OFL 授权且与上游字面不同的开源字体，图标自绘或采用 Apache 2.0 授权图标库，微信读书接入使用其公开接口 `https://i.weread.qq.com/api/agent/gateway`，KOReader 数据读取使用其公开的 `statistics.sqlite3` 表结构，均属公开可用的第三方接口。

## 五、发布前自检清单

用 apkanalyzer 与全量字符串导出，检索 `astraabove`、`tomo`、`图墨`、`wetao` 四个关键词，命中数应为零。对新旧 APK 的 `res\`、`assets\`、`lib\` 逐文件计算 SHA-256 并做交集，交集应为空集。启动过程录制屏幕，比对横竖屏切换、长按、连续点击、下拉等手势序列，确认响应时序与上游存在可测量差异。最后在 480×800 之外的三种分辨率上启动，确认进入正常桌面（上游在此处立即退出）。
