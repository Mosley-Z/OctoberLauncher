# 交接文档：十雨桌面 V1.0.1 与微读墨记 V1.3.2-beta 同步开发

本文用于把全部已确认的需求、事实、契约与验收标准移交给新的开发团队。新团队按本文即可开工，不需要回溯此前对话。文中所有行号以交接时刻工作区为准，若代码已变动，以类名与方法名检索定位。

## 一 已定决策与红线

以下八条是最终决策，实施中不再讨论。

1. 交付形态采用内核化双宿主：微读墨记抽成宿主无关内核，原版应用与十雨桌面内置版分别作为宿主。
2. 用户可以单独安装微读墨记 V1.3.2-beta，在原生桌面与图墨桌面上功能与 V1.3.1-beta 一致。
3. 安装十雨桌面 V1.0.1 后，微读墨记隐藏图标并静默，功能与数据由十雨内置版接管；卸载十雨后恢复显示与运行。
4. 十雨桌面负一屏即墨台。默认顺序为书架、最近图片、墨单、排行、阅读指纹，五项全部开启。
5. 整体卡片的默认形态为本周，其余形态与待办形态保持原应用设置。
6. 遥控台只在手机端出现，内置版不移植，原版应用在未静默时继续提供。
7. 迁移包加密；MIT 署名写在关于页第三方许可分组；正式签名替换时间点在候选包产出后确认。
8. 两个工程都只产出候选包与验证记录，实际发布等待 Doow 明确指令。

红线：

- 不修改、不卸载用户设备上的既有数据。
- 十雨桌面目录不是 git 仓库，用 `scripts/snapshot.ps1` 做回溯，禁止自行初始化仓库。
- 微读墨记仓库禁止 `git add -A`，提交必须写显式路径。
- 真机操作前先备份设备配置文件（见第四节命令）。
- 微读墨记原版在十雨缺席时的行为不得回退。

## 二 工程现状速查

| 项目 | 路径 | 版本控制 | 当前版本 | 构建 |
| --- | --- | --- | --- | --- |
| 十雨桌面 | `D:\DSHStation\OctoberLauncher` | 无 git，快照目录 `D:\DSHStation\_snapshots` | versionCode 20，versionName 1.0.0，`VERSION` 文件 1.0.0 | Gradle 8.11.1，Kotlin 2.0.21，AGP 8.7.3 |
| 微读墨记 | `D:\WorkBuddyStation\WereadMoji\weread-widget` | git，分支 `beta`，HEAD `5c67da6`，tag `v1.3.1-beta` | VERSION_CODE 47，VERSION_NAME 1.3.1-beta | `tools/build.sh`，aapt2 + javac + d8 + apksigner |

十雨桌面结构：Gradle 多模块，共十四个工程。`:app` 与 `:service` 为宿主与系统服务；`:core:common` `:core:design` `:core:device` `:core:render` `:core:data` `:core:widget-api` 为基础层；`:feature:home` `:feature:widgets` `:feature:tray` `:feature:settings` `:feature:content` `:feature:files` 为界面与能力层。源码 293 个 Kotlin 文件，1.0.0 门禁记录 53 个测试套件 482 个用例。

微读墨记结构：纯 Java，包 `com.inkread.weekread`，源码 100 个文件约 2.26MB，主要目录 `app/src/main/java/com/inkread/weekread/{core,net,ui,feature,shell,a11y,remote,update}`；构建脚本 `tools/build.sh`；签名密钥在仓库外 `../_keys`。

设备：S4 墨水屏，序列号 `SA4B03CN2608081096X`，Android 11，480×800，219 dpi，armeabi-v7a。当前默认桌面为 E-Launcher 与图墨桌面，十雨桌面通过 `am start` 拉起。

## 三 环境与命令手册

工具链根目录写在 `D:\DSHStation\OctoberLauncher\toolchain.path`，内容为 `D:\DSHStation\.toolchain`。

```powershell
cd D:\DSHStation\OctoberLauncher
$tc = (Get-Content toolchain.path).Trim()
$jdk = Get-ChildItem "$tc\jdk" -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
       Sort-Object Name -Descending | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName          # 必须指向含 bin\javac.exe 的子目录，当前为 jdk17.0.20_12
$env:ANDROID_HOME = "$tc\android-sdk"
$env:GRADLE_USER_HOME = "$tc\gradle-home"
$gradle = "$tc\gradle\gradle-8.11.1\bin\gradle.bat"
& $gradle --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release.apk`、`app/build/outputs/mapping/release/mapping.txt`。

ADB 位于 `D:\DSHStation\.toolchain\android-sdk\platform-tools\adb.exe`。常用命令：

```powershell
$adb = 'D:\DSHStation\.toolchain\android-sdk\platform-tools\adb.exe'
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.shuiyu.octoberrain/.MainActivity
& $adb shell dumpsys meminfo com.shuiyu.octoberrain
& $adb exec-out screencap -p > shot.png
& $adb shell input tap 240 400
& $adb shell input swipe 40 420 430 420 250
```

设备配置文件在 `shared_prefs/octoberrain_config.xml`（键名 `launcher_config`），调试包可读写：

```powershell
& $adb shell "run-as com.shuiyu.octoberrain cat shared_prefs/octoberrain_config.xml" > cfg-backup.xml
& $adb shell "run-as com.shuiyu.octoberrain cp shared_prefs/octoberrain_config.xml shared_prefs/cfg-backup.xml"
# 注入测试配置：先写到临时文件，再 run-as cp 覆盖，最后 am force-stop 并重启
```

微读墨记构建：

```bash
cd /d/WorkBuddyStation/WereadMoji/weread-widget
bash tools/build.sh      # 签名密钥需存在于 ../_keys，缺失即失败
```

快照：

```powershell
pwsh -File scripts\snapshot.ps1 -Tag 1.0.1
```

脚本必须用 PowerShell 7 运行，Windows PowerShell 5.1 读取 UTF-8 脚本会报字符串未终结。

## 四 产品形态与共存规则

四态矩阵：

| 状态 | 微读墨记 | 十雨桌面 |
| --- | --- | --- |
| 只有微读墨记 | 完整功能，原生桌面与图墨桌面可用 | 未安装 |
| 只有十雨桌面 | 未安装，内置版空白启动，密钥二维码录入 | 完整功能 |
| 两者共存且微读 ≥ 1.3.2-beta | 静默：无图标、无卡片、无通知、无定时任务 | 内置版接管，数据已迁移 |
| 两者共存但微读版本过低 | 正常显示 | 内置版空白，提示升级后迁移 |

判断依据：十雨桌面查询 `com.inkread.weekread` 的 `versionCode`；微读墨记查询 `com.shuiyu.octoberrain` 的 `versionCode` 是否不低于 21（V1.0.1）。

静默动作四项：

1. 启动入口改为 `activity-alias`（如 `LauncherAlias`，`android:enabled="true"` 并挂 MAIN/LAUNCHER），`MainActivity` 只保留内部入口；静默时 `PackageManager.setComponentEnabledSetting(alias, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)`。
2. 禁用无障碍服务组件 `.a11y.CardA11yService`，停止悬浮窗与墨台入口。
3. 停止 `BillScheduler` 等定时任务，取消通知，冻结数据写入。
4. 写入 `silent_since` 与十雨版本号到 SharedPreferences。

恢复路径：应用启动检查、`BOOT_COMPLETED` 广播（需 `RECEIVE_BOOT_COMPLETED` 权限）、以及十雨卸载广播作为兜底；恢复时重新启用 alias 与无障碍服务组件并清除标记。

## 五 内核契约 v1

内核为 Java 源码集，包名 `com.inkread.weekread.kernel`，宿主无关，不引用 Activity、Window、DisplayMetrics 与固定屏幕坐标。版本标识 `1.3.2-k1`。

```java
package com.inkread.weekread.kernel;

public interface KernelHost {
    java.io.File filesDir();          // 缓存与临时文件的唯一来源
    float scale();                    // 相对 368×346 基准的缩放，范围 0.6–2.0
    void requestRedraw();             // 数据变化后请求宿主重绘
    void openPage(String target);     // "bill" | "assets" | "fingerprint" | "book" | "note" | "todo"
}

public interface CardSurface {
    android.graphics.Canvas canvas();
    int width();
    int height();
    int hitTest(float x, float y);    // 返回热区编号，未命中返回 0
    void onAction(int action);        // 热区动作回调
}

public interface DataSource {
    ShelfSnapshot shelf();
    PeriodSnapshot period(String mode);            // "week" | "month" | "year" | "overall"
    java.util.List<BillItem> bill(String mode, int offset);
    java.util.List<AssetItem> assets(int sort, boolean folded);  // sort: 0 时长 1 原价 2 收入
    FingerprintGrid fingerprint(int bookCount);
    java.util.List<NoteItem> notes(int limit);
}

public final class CardKernel {
    public static final int FORM_WEEK = 0;
    public static final int FORM_MONTH = 1;
    public static final int FORM_BOOK = 2;
    public static final int FORM_NOTE = 3;
    public static final int FORM_TODO = 4;

    public CardKernel(KernelHost host, DataSource source);
    public void bind();
    public void render(CardSurface surface, int form);
    public void setForm(int form);
    public int form();
    public void onTick();        // 宿主整分或数据变化时调用
    public void release();
}
```

数据模型字段以现有 `core/StatsStore.java`、`core/NoteStore.java`、`core/BillStore.java`、`core/AssetStore.java` 为准，抽取时保持字段名与单位（时长秒、金额分、进度千分比）。`FingerprintGrid` 暴露 485 个顶点坐标与 256 级墨量查表结果，几何数据整体复制，不重算。

产物：`weread-kernel-1.3.2-k1.jar` 与 `weread-kernel-1.3.2-k1.sha256`，放入十雨 `libs/`，Gradle 以 `implementation(files("libs/weread-kernel-1.3.2-k1.jar"))` 引入。内核升级按 `k` 号递增并成对发布。

## 六 微读墨记 V1.3.2-beta 工作分解

### A1 内核抽取

目标：把数据访问、派生算法与卡片渲染移入 `kernel/`，原版应用通过适配层调用。

范围：`core/StatsStore.java`、`core/NoteStore.java`、`core/BillStore.java`、`core/BillScheduler.java`、`core/AssetStore.java`、`core/AssetRefresher.java`、`core/PeriodStats.java`、`core/BillEstimator.java`、`core/BillMoney.java`、`feature/CardRenderer.java`、`feature/CardLayout.java`、`feature/CardInteraction.java`、`feature/CardContentController.java`。

做法：接口先落在 `kernel/`，实现由现有类迁入或委托；`CardRenderer` 的绘制入口改为接收 `CardSurface`，坐标不再读取 `CardSpec` 常量，改由 `CardSurface.width()/height()` 与 `scale()` 推导；热区改为 `hitTest` 返回编号。

验收：原版卡片五形态渲染与 V1.3.1-beta 截图比对，像素差异集中在缩放后的边缘；`kernel/` 不引用 `android.app.Activity`、`android.view.WindowManager`、`android.util.DisplayMetrics`、`android.accessibilityservice.*`。

### A2 原版宿主适配

目标：原版悬浮窗卡片继续可用。

范围：`a11y/OverlayController.java`、`a11y/CardA11yService.java`、`feature/WeekCardView.java`、`feature/OverlayWindow.java`。

做法：宿主实现 `KernelHost` 与 `CardSurface`，把 `TYPE_ACCESSIBILITY_OVERLAY` 窗口与固定 480×800 坐标适配到内核；页面探测与让位逻辑保留在宿主侧，不进内核。

验收：原生桌面与图墨桌面真机回归，让位、隐藏时长、切应用消失等行为与 V1.3.1-beta 一致。

### A3 共存检测与静默

目标：按第四节规则静默与恢复。

范围：`app/src/main/AndroidManifest.xml`（新增 activity-alias、BOOT_COMPLETED 广播）、新增 `silent/SilentMode.java`、`silent/SilentBootReceiver.java`。

做法：`SilentMode.evaluate(context)` 查询十雨版本，决定启用或恢复；所有入口调用同一处判定；静默时依次执行四项动作，恢复时逆序执行。

验收：安装十雨后重启，图标消失、卡片消失、通知清空、无网络请求；卸载十雨后重启，图标与卡片恢复，数据未丢。

### A4 数据导出桥

目标：供十雨一次性迁移。

范围：新增 `bridge/ExportActivity.java`、`bridge/ExportPack.java`。

做法：接收 `Intent` 参数 `target_uri`（十雨 cache 目录的 FileProvider Uri）与一次性令牌；生成清单与数据包，AES-GCM 加密后写入目标；完成后 `setResult(RESULT_OK)` 并在结果中回传清单摘要与条目数；失败回传错误码。

验收：与十雨联合测试，共 2.9MB 数据在 3 秒内完成导出与校验；错误码可区分权限拒绝、空间不足、数据为空。

### A5 版本与发布候选

`version.properties` 改 `VERSION_NAME=1.3.2-beta`、`VERSION_CODE=48`；`NOTES` 写清静默与导出入口；`bash tools/build.sh` 产出 APK；记录 sha256 到 `_analysis/verify/`；打 tag 的时机等待指令。

## 七 十雨桌面 V1.0.1 工作分解

### B1 内核接入

目标：十雨编译内核并在组件中调用。

范围：`app/build.gradle.kts` 增加 `implementation(files("libs/weread-kernel-1.3.2-k1.jar"))`；`feature/content` 增加适配类。

验收：`:app:assembleDebug` 通过；R8 后内核类未被裁掉（`mapping.txt` 校验）；启动耗时增量不超过 30ms。

### B2 负一屏五模块

目标：负一屏即墨台，五项默认开启，顺序书架、最近图片、墨单、排行、指纹。

现状：`feature/home/.../NegativeScreen.kt`（298 行，`createView:74`、`onDestroy:134`、`applyConfig:161`）；契约 `core/common/.../Contracts.kt:1089` 的 `NegativeModule`、`:1110` 的 `NegativeHost`；默认列表 `core/common/.../Models.kt:97`；已有注册点 `feature/content/.../ContentModule.kt:34` 与 `feature/files/.../FilesModule.kt:38`；设置项 `feature/settings/.../NegativeSpecs.kt`（101 行）与 `DesktopPrefsScreen.kt:69-75`。

改动：

1. `NegativeModule` 增 `onDestroy()`、`defaultEnabled`、`orderHint`；`NegativeHost` 增 `context`、`widthDp`。
2. `NegativePageConfig` 增 `forms: Map<String, Int>` 与 `cardOrderInitialized`；`CURRENT_VERSION` 7→8。
3. 新增三模块 `WereadBillNegativeModule`、`WereadAssetsNegativeModule`、`WereadFingerprintNegativeModule`，位于 `feature/content/.../ui/negative/`，各含纯逻辑类与卡片视图。
4. `NegativeScreen` 顶部增齿轮按钮与 `Destination.NegativeSettings` 入口；`onDestroy` 转发模块销毁。
5. 设置页新增 `NegativeSettingsScreen`：五项开关、上移下移，以及墨单 13 项 `menu_*` 配置。
6. `SettingsModule` 分派表增加一行。

验收：五项开关与排序即时生效；负一屏滚动流畅；卡片在三档设备宽度下不重叠。

### B3 整体卡片

目标：`weread_board` 单组件承载五形态。

改动：

1. `core/widget-api/.../WidgetApi.kt`：`WidgetView` 增 `hitRegions()`（默认空表）与 `onLongPress(x: Float, y: Float)`（带坐标版本，保留无参版本作兼容）；新增 `data class WidgetHitRegion(x, y, width, height, action)`。
2. 新增 `WereadBoardWidget`（feature/widgets），内部持有 `CardKernel`，按槽宽 `scale = width / 368f`，最小 0.6、最大 2.0。
3. 形态持久化到 `NegativePageConfig.forms["weread_board"]`，默认本周。
4. 长按菜单三项：隐藏、刷新、打开墨台。

验收：三档设备缩放后文字可读；五形态循环切换；点击与长按热区与可见元素一致。

### B4 页面补齐

目标：保留原应用页面能力。

页面清单：阅读页五形态与周期选择（现有 `WereadScreen` 扩展）、本书页、本记页、设置分组、待办。墨台不再作为独立页面，改负一屏。遥控台不移植。

行为：

1. `Contracts.kt` 增加 `Destination.Book`、`Destination.Note`、`Destination.NegativeSettings`、`Destination.Todo`。
2. `ContentModule` 注册意图与页面工厂；`displayLabel()` 补标签。
3. 设置声明表新增分组：微读墨记（密钥、刷新间隔、统计范围）、墨台（13 项 `menu_*`）、负一屏（五项开关与排序）。

验收：页面栈返回正确；无死链；设置项立即生效。

### B5 数据导入与共存

目标：一次性迁移与静默引导。

流程：启动检测微读版本 → 满足则显示迁移页 → 调 `ExportActivity` → 校验清单 → 写 `filesDir/weread/tmp-<ts>/` → 校验 sha256 与条目数 → 原子替换 → 保留七天备份 → 写 `wereadMigration` 元数据。

降级：未安装微读则跳过；版本过低提示升级；导出失败提示可重试，界面保留「空态继续使用」。

验收：迁移幂等；断网可迁移（数据来自本地）；重复启动不重复导入；失败后系统可用。

### B6 组件删减与迁移

最终保留五项：`clock`（吸收 `clock_compact`）、`calendar`、`image`、`placeholder`、`usage`，并新增 `weread_board` 作为整体卡片。

删除十三项：`clock_compact`、`covers`、`weread_covers`、`weread_month`、`koreader_month`、`weread_mark`、`weread_note`、`weread_stats`、`weread_bill`、`weread_rank`、`weread_fingerprint`、`reading`、`weather`。开工首日必须用 `WidgetCatalog.kt:38-221` 的十八条声明逐条核对本清单，核对结果与本节不一致时以代码为准并回写本节。图片组件改为多选与桌面点击轮播，轮播间隔进设置声明表。

迁移规则：旧 `widgetId` 命中被删项时，微信读书类改写为 `weread_board`，其余改写为 `clock`；宿主 `HomeView` 已有「未安装组件」占位，不会崩溃。

清理点：`WidgetCatalog` 条目与两个分组列表（`:230`、`:253`）、被删组件源文件、`WidgetCatalogTest` 断言、`docs/15`、`docs/08`、`CHANGELOG`、proguard 规则。

### B7 门禁与候选发布

`app/build.gradle.kts` 的 `versionCode` 改 21、`versionName` 改 `1.0.1`，`VERSION` 同步；CHANGELOG 增条目；`docs/15` 补完成记录；`_analysis/verify/1.0.1-gate.md` 记录门禁数据；`scripts/snapshot.ps1 -Tag 1.0.1` 生成快照。发布等待指令。

## 八 数据迁移规范

本节按交付时的实现记录，与第二节的初步设计相比有两处调整：清单的 `encrypted` 由布尔改为对象，条目名与落位路径按内核实际读取位置确定。

清单格式：

```json
{
  "schemaVersion": 1,
  "sourceVersionName": "1.3.2-beta",
  "sourceVersionCode": 48,
  "exportedAt": 1760912345678,
  "encrypted": {
    "cipher": "AES/GCM/NoPadding",
    "keyBits": 256,
    "tagBits": 128,
    "container": "WRMK"
  },
  "entries": [
    { "name": "nt_index.json", "bytes": 78945, "sha256": "…" },
    { "name": "nt_1001.json", "bytes": 12043, "sha256": "…" }
  ],
  "counts": { "books": 220, "notes": 55, "bills": 25 }
}
```

文件清单与用途：

| 来源条目 | 十雨落位 | 说明 |
| --- | --- | --- |
| `kernel_prefs.json` 的 `string.api_key` | `LauncherConfig.integrations.wereadApiKey` | 换钥清数据 |
| `achievement.json` | `LauncherConfig.wereadAchievement.rawJson` 与 `filesDir/weread/achievement.json` | 成就原文 |
| `nt_index.json` | `filesDir/weread/nt_index.json` | 划线索引 |
| `nt_<id>.json` | `filesDir/weread/nt_<id>.json` | 每本划线，约 2.3MB |
| `ni_<id>.json` | `filesDir/weread/ni_<id>.json` | 每本笔记，240KB |
| `nt_batch.json`、`nt_batch_i.json`、`nt_batch_p.json` | `filesDir/weread/` 同名 | 批量索引 |
| `shelf.json`、`shelf_ids.json`、`book.json` | `filesDir/weread/` 同名 | 书架快照与书籍元信息 |
| `menu_notes.json`、`menu_prices.json`、`menu_progress.json` | `filesDir/weread/` 同名 | 手写页脚、标价、进度映射 |
| `kernel_prefs.json` | `filesDir/weread/kernel_prefs.json` | 内核键值层，含密钥、周期、账单与统计 |

落位以文件名平铺在 `filesDir/weread/` 下，不建 `nt/`、`ni/`、`menu/` 子目录，原因是内核以相对根目录的原盘文件名读取。清单之外的文件名（`.db`、`.xml`）导出端不采集。

加密：随机 256 位密钥经 `Intent` 额外字段 `bridge_key` 以 Base64 传递，只存活单次迁移；清单不加密，用于校验。文件布局为首行清单 JSON 加换行，随后 12 字节 IV 与 AES-256-GCM 密文（16 字节 tag）；密文解出 4 字节魔数 `WRMK`、版本号 1、条目数与逐条名称与数据。

校验：逐文件 sha256、条目数、必填项存在；失败删除临时目录并提示原因，不进入部分导入状态。

回滚：成功导入后保留 `filesDir/weread/backup-<时间戳>/` 七天；设置页提供「清除迁移数据」。

幂等：`LauncherConfig.wereadMigration = { sourceVersionCode, importedAtMillis, manifestDigest, complete }`；摘要一致则跳过。

未迁移项：微读原版的锁屏盐值与哈希位于其偏好文件，导出桥未采集，十雨锁屏凭据沿用本机配置。

## 九 负一屏契约与迁移细节

```kotlin
interface NegativeModule {
    val id: String
    val label: String
    val defaultEnabled: Boolean get() = true
    val orderHint: Int get() = 100
    fun createView(context: Context, host: NegativeHost): View
    fun onResume() {}
    fun onPause() {}
    fun onDestroy() {}
}

interface NegativeHost {
    val context: Context
    val widthDp: Int
    fun requestRedraw()
    fun open(destination: Destination)
}
```

配置 v8 迁移伪代码：

```kotlin
private fun migrateV7ToV8(config: LauncherConfig): LauncherConfig {
    val defaults = listOf("bookshelf", "negative_files", "weread_bill", "weread_rank", "weread_fingerprint")
    val known = defaults + listOf("weread_assets")
    val kept = config.desktop.negativePage.modules.filter { it in known }
    val merged = (defaults.filter { it in kept } + (kept - defaults.toSet()))
        .ifEmpty { defaults }
    return config.copy(
        desktop = config.desktop.copy(
            negativePage = config.desktop.negativePage.copy(modules = merged),
            widgets = rewriteRemovedWidgetIds(config.desktop.widgets),
        ),
        version = 8,
    )
}
```

设置页声明：沿用 `SettingsCatalog.GROUP_NEGATIVE_CARDS`（`SettingsSpec.kt:77`），在 `NegativeSpecs` 增加五项开关与上移下移 Action；`afterWrite` 调 `RefreshSchedulerBridge.notifyContentChanged()`。

## 十 组件契约扩展

```kotlin
data class WidgetHitRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val action: String,
)

interface WidgetView {
    // 既有方法保持不变
    fun hitRegions(): List<WidgetHitRegion> = emptyList()
    fun onLongPress(x: Float, y: Float) {}
}
```

宿主 `HomeView` 在触摸时先调 `hitRegions()` 命中判定，再转发 `onClick(x, y)`；长按转发带坐标版本。缩放公式：`scale = (width - padding) / 368f`，字号按 `round(基准字号 × scale)`，热区同乘。

## 十一 密钥二维码录入组件

用途：内置版无微读墨记时的密钥入口；也可供设置页复用。

协议：设置页启动 `ServerSocket` 绑定随机端口；生成 128 位一次性令牌；二维码内容 `http://<设备IPv4>:<port>/k?t=<token>`；手机在同一热点下打开，页面提供文本框与提交按钮。

服务端行为：`GET /k` 返回极简 HTML 表单；`POST /k` 读取 `Content-Length`，上限 4KB，校验令牌、时间九十秒、尝试次数五次；成功写入 `LauncherConfig.integrations.wereadApiKey` 并触发内容刷新，返回成功页；结束后关闭服务与端口。

实现约束：不引入第三方 HTTP 库；`ServerSocket` 自带；二维码用 ZXing core 3.5.3 或自写编码器，前者包体增量约 250 至 400KB（R8 后），后者约 350 至 450 行。

安全：不写日志、不回显完整密钥、局域网内仅单次有效、失败五次即关闭。

## 十二 排期与里程碑

| 周 | 微读墨记 | 十雨桌面 | 里程碑 |
| --- | --- | --- | --- |
| W0 | 契约冻结、分支创建 | 内核接入验证、libs 占位 | M0 第 0.5 天 |
| W1 | A1 内核抽取 | B2 负一屏模块 | M1 第 6 天内核快照可用 |
| W2 | A1 收尾、A2 原版适配 | B2 收尾、B3 整体卡片 | M2 第 9 天原版静默前可用 |
| W3 | A3 静默、A4 导出桥 | B4 页面、B5 导入接线 | M3 第 11 天数据桥通 |
| W4 | A5 候选包与回归 | B5 收尾、B6 删减、B7 门禁 | M4 第 16 天、M5 第 19 天、M6 第 22 天 |

工作量：微读墨记 12 至 16 人日，十雨桌面 28 至 38 人日。

人员分工建议：一人负责内核与静默（A1 至 A5），一人负责数据桥与十雨数据层（A4 与 B5），两人负责十雨界面（B2 至 B4），一人负责契约、集成与门禁。共享任务板的写作用域必须互斥，`core/common`、`core/widget-api`、`app/build.gradle.kts` 为单写者。

## 十三 验收矩阵

设备：S4 480×800（现有）、552×768、824×1648（另行准备）。

必需项：

1. 仅装微读墨记：原生桌面与图墨桌面卡片五形态、墨台三模块、待办、设置全部可用。
2. 仅装十雨桌面：负一屏五模块空白态、二维码录密钥、卡片显示。
3. 共存迁移：导出导入成功、数据一致（书架本数、划线条数、账单条数、资产合计四项数值比对）、静默生效。
4. 卸载十雨：微读恢复图标与无障碍服务，数据仍在。
5. 微读版本过低：十雨不迁移并给出提示。
6. 迁移幂等：重复启动不重复导入。
7. 换钥清数据：内置版与原版行为一致。
8. 三档设备：负一屏卡片无重叠，整体卡片文字可读。
9. 门禁：`gradle --offline testDebugUnitTest check` 通过，超 120 列 0 行，detekt 与 lint 无新增告警。
10. 性能：冷启动不超过 1.5 秒，PSS 增量不超过 8MB。

## 十四 交接协作协议

任务板沿用共享任务列表，每个任务写清写作用域、验收标准、门禁命令。成员把详细报告写入 `_analysis/team/<name>.md`，回传不超过 1500 字摘要。

报告必须包含：改动文件与行数、行为变化、验证命令与结果、已知偏差、遗留项。

门禁未通过不得标记完成；静态检查不得用 `@Suppress` 或降低阈值规避。

真实设备操作前备份配置，操作后还原并核对字节数；测试图与临时目录清理干净。

代码注释用规范书面中文，函数注释说明功能、参数与预期行为。

## 十五 未决项与默认取值

| 事项 | 默认取值 | 备注 |
| --- | --- | --- |
| 正式签名替换时间 | 候选包产出后确认 | 沿用调试签名直到指令 |
| 迁移包加密 | 开启 | AES-GCM，密钥单次传递 |
| 遥台台在内置版 | 不移植 | 仅手机端原版 |
| MIT 署名位置 | 关于页第三方许可分组 | 文本 `WereadMoji — MIT — 2026 — Mosley-Z` |
| 微读原版静默期数据 | 冻结 | 卸载十雨后以冻结时间为准 |
| 旧组件 id 迁移目标 | 微信读书类 `weread_board`，其余 `clock` | 见 B6 |

## 十六 附录 关键文件与行号索引

十雨桌面：

- `core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt:188` 的 `WereadContent`、`:228` 的 `ContentSource`、`:432` 的 `Destination.Album`、`1089` 的 `NegativeModule`、`:1110` 的 `NegativeHost`
- `core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt:86` 的 `NegativePageConfig`、`:97` 默认模块、`:133` 默认 `widgetId`、`:300` 的 `wereadApiKey`、`:374` 的 `CURRENT_VERSION`
- `core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt:18` 的 `WidgetView`、`:145` 的 `WidgetRefreshPolicy`、`:171` 的 `WidgetProvider`
- `feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/NegativeScreen.kt:74`、`:134`、`:161`
- `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/ui/negative/FilesNegativeModule.kt:107`、`:130`、`:358`
- `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ContentModule.kt:34`
- `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/FilesModule.kt:38`
- `feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WidgetCatalog.kt:38` 至 `:221` 的 18 条声明、`:230` 与 `:253` 分组列表
- `feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/SettingsSpec.kt:77`
- `app/src/main/java/com/shuiyu/octoberrain/OctoberRainApp.kt` 装配入口与反射加载列表

微读墨记：

- `core/CardPrefs.java`、`core/StatsStore.java`、`core/NoteStore.java`、`core/BillScheduler.java`、`core/BillStore.java`、`core/AssetStore.java`
- `feature/CardRenderer.java`、`feature/CardLayout.java`、`feature/CardInteraction.java`、`feature/WeekCardView.java`
- `feature/OverlayWindow.java`、`a11y/CardA11yService.java`、`a11y/OverlayController.java`
- `shell/DeskActivity.java`、`feature/DeskPageView.java`、`feature/BillSection.java`、`feature/AssetSection.java`、`feature/PagePrefs.java`
- `app/src/main/AndroidManifest.xml`（activity-alias 与无障碍服务声明）
- `tools/build.sh`（构建链）、`version.properties`（版本唯一来源）

参考物料：`_analysis/team/wereadmoji-desk-inventory.md`、`wereadmoji-data-inventory.md`、`wereadmoji-user-logic.md`、`wereadmoji-delivery-options.md`、`launcher-negative-contract.md`、`widget-inventory.md`、`apikey-entry-eval.md`、`edit-floating-panel-design.md`、`weread-whole-and-moyu-plan.md`；方案类文档 `_analysis/plans/weread-embed-and-refactor-plan-v2.md` 与 `v1.0.1-wereadmoji-v1.3.2-plan.md`。
