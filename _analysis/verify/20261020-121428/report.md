# V1.0.1 验收记录 20261020-121428

JDK：D:\DSHStation\.toolchain\jdk\jdk17.0.20_12
SDK：D:\DSHStation\.toolchain\android-sdk
设备：SA4B03CN2608081096X，480×800
配置备份：cfg-backup.xml，真机步骤前导出

## 构建与测试

命令：Gradle 8.11.1 --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease

结果：构建与单元测试通过，ktlintCheck 与 detekt 全模块零告警。

## 产物

| 文件 | 字节 | SHA-256 |
| --- | --- | --- |
| app-debug.apk | 3997921 | d23b7411e9d7539ab81a361a10d21e5934adf7cae6382a7d82fe2adf8fef2a0d |
| app-release.apk | 631322 | c21a3f20c8325cb0637336db3fbeffbe942027b103867d4095c08b4c3cb6350d |
| mapping.txt | 10623723 | 8e18e41e14b59f847556b66c07f2ca036f2d524804d9a2b6cb7b1e9cb9b808e0 |

## 内核保留校验

`mapping.txt` 中 `com.inkread.weekread.kernel` 命中 435 行，类名与方法名保持原文，形如 `com.inkread.weekread.kernel.AssetItem -> com.inkread.weekread.kernel.AssetItem`。R8 未裁剪内核实现，`app/proguard-rules.pro` 的保留规则生效。

## 真机验证

发布包装机后测量，容器处于微信读书未配置状态。

冷启动取 ActivityTaskManager 的 Displayed 行，连续三次 1010 毫秒、996 毫秒、1129 毫秒，中位 1010 毫秒，低于 1500 毫秒上限。1.0.0 基线 1053 毫秒，本版持平。

内存：首页稳定后 TOTAL PSS 35628 KB、TOTAL RSS 127476 KB。1.0.0 基线 30761 KB，增量 4867 KB，低于 8 MB 限制。调试包同场景 PSS 为 61328 KB，差异来自调试运行时与未优化字节码。

截图：

| 文件 | 字节 | 页面 |
| --- | --- | --- |
| rel-01-home.png | 54720 | 桌面首页，整体卡片显示微信读书未配置 |
| rel-02-negative.png | 71637 | 负一屏，书架与最近图片两张卡片 |
| rel-03-negative-settings.png | 81642 | 负一屏设置，六个模块开关与顺序、墨单外观分组 |
| rel-04-app-menu.png | 31607 | 桌面图标长按菜单 |

启动日志无 FATAL EXCEPTION 与 ANR 记录。

## 遗留与人工核验项

组件选择页未纳入自动截图。入口位于设置的桌面偏好之下，坐标依赖设置页滚动位置，本次以负一屏设置页替代，选择页截图列入人工核验。

负一屏「最近图片」中部分缩略图呈 180 度倒置。解码链路已核对：`ImageDecoder` 的方向映射表包含 `ORIENTATION_ROTATE_180`，`ThumbCache` 与缩略图视图均无额外变换，缓存位图与屏幕显示方向一致。样本为设备相册中的证件与杂志照片，源文件 EXIF 方向与实际像素可能不一致，需按系统相册的显示结果对照确认，本次未判定为缺陷。

三档设备只在 480×800 验证，552×768 与 824×1648 未测。

微信读书仅在未配置状态验证，有数据态需要有效密钥后复验整体卡片的五种形态。

迁移与共存四种状态未在真机执行，需要微读墨记侧数据与二维码录密钥配合，列入后续验收。

## 结论

构建、单元测试、静态检查、发布包安装与启动、负一屏与设置页加载、冷启动与内存指标通过。组件选择页截图、三档设备、微信读书有数据态与迁移共存四项列入后续核验。

## 故障复现与修复（录入密钥后整体卡片空白）

现象：在 S4 真机录入微信读书 API Key 后，桌面顶部整体卡片 `weread_board` 只剩白底与边框，无任何文案与图形；负一屏书架数据正常，说明密钥与网络无问题。

定位：`WereadBoardWidget.drawWidget` 在有内核时直接转交 `KernelWereadBoard.render`；`KernelCardFiller.apply` 在取到周期数据后调用 `cardState.setPhMain(null)` 清空占位，而内核 `CardState.setPhMain` 把 `null` 归一为空串；`CardRenderer.draw` 以 `host.phMain() != null` 判定占位，空串命中该分支，画一个空字符串后立即返回，整张卡片不再绘制。未配置密钥时占位文案非空，因此故障只在录入密钥后出现。

修复：`CardRenderer.draw` 的占位判断改为同时排除 `null` 与空串，`phSub` 同步处理；重新构建 `weread-kernel-1.3.2-k1.jar`（67810 字节，SHA-256 `95dddcac3c40f1404b5c1bf1aa925cfe9fbed556f4bf2e1437136128eeb58536`，连续两次构建字节一致），复制到 `libs/` 并更新校验文件；微读原版候选包重建为 592624 字节（SHA-256 `b110bc62791c574ce6fe1c9687b8253bd15106b6651087337ec9dc6ad3c3e1b0`），`dist/update.json` 与 `.last_version_code` 已恢复为仓库版本。

验证：调试包与发布包均在 S4 重新安装，启动后整体卡片显示「本周 19 分钟」「阅读 1/7 天 · 日均 9 分」与周柱状图，截图 `fix-home2.png` 与 `fix-release.png`。

## 微读墨记图标消失的说明

该现象属静默共存设计：`SilentMode.SHIYU_MIN_VERSION_CODE = 21`，检测到十雨桌面 versionCode 不低于 21 时执行四项动作——停用 `.silent.LauncherAlias` 隐藏桌面图标、停用 `.a11y.CardA11yService`、写入冻结标记停止定时任务、记录静默状态。真机 `dumpsys package com.inkread.weekread` 的 `disabledComponents` 已列出 `com.inkread.weekread.silent.LauncherAlias`，与设计一致。

恢复方式：卸载十雨桌面或把十雨降级到 21 以下，`SilentBootReceiver` 在 `PACKAGE_REMOVED`、`BOOT_COMPLETED`、`MY_PACKAGE_REPLACED` 时重新评估并恢复图标与无障碍服务；临时恢复可用 `adb shell pm enable com.inkread.weekread/.silent.LauncherAlias`。