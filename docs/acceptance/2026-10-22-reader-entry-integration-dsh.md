# 2026-10-22 RD015 基础阅读入口与最近阅读 验收记录

- 范围/验收者：RD015 本轮增量——最近阅读契约、书架最近阅读区、书架本地书统一入口、MVP 能力表；Lead 执行，设备由 DSH 侧操作。
- 源码提交或基线+差异范围：master `1379e8b` 之后工作区修改；本轮改动 `core/common` 的 ReaderContracts.kt 与 Contracts.kt（AppServices.recent）、`feature/reader` 的 ReaderModule.kt、`feature/content` 的 BookshelfScreen.kt、`app` 的 MainActivity.kt（入口诊断日志）。
- APK路径/版本/摘要：`app/build/outputs/apk/debug/app-debug.apk`，5,517,631 字节，SHA-256 `4e8537f795a7b868ac3e59b08479f046f05eb5fe4b886ea94f1ea3b08bc962fc`（16:03 构建）；release 1,440,273 字节，同轮 `check :app:assembleDebug :app:assembleRelease` 构建。
- 环境/工具链/设备serial：D:\DSHStation\.toolchain（JDK 17、Gradle 8.11.1、Android SDK），离线模式；设备 十雨S4，serial SA4B03CN2608081096X，480×800；界面观测与点击使用 `scripts/android-ui.ps1`（layout/annotate/tap），投递与截图使用 adb 与 android CLI。
- 关联任务/设计：[TASK-RD-015](../../task/TASK-RD-015-integration.md)、[34 方案](../design/34-files-reader-upgrade-plan.md)、[MVP 格式能力表](2026-10-22-reader-mvp-capability-dsh.md)。

| 用例/命令 | 前置条件与操作 | 预期 | 实际/退出码 | 证据路径 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `:core:common:testDebugUnitTest :feature:reader:testDebugUnitTest :feature:content:testDebugUnitTest :core:common:detekt :feature:reader:detekt :feature:content:detekt` | 工作区含本轮改动，离线工具链 | 编译与单测通过 | BUILD SUCCESSFUL 31s | 终端输出 | 通过 |
| `check :app:assembleDebug :app:assembleRelease` | 同上 | 全量检查与双 APK 构建 | BUILD SUCCESSFUL 3m18s，1564 tasks，EXIT=0 | 终端输出 | 通过 |
| 书架最近阅读点击 | 打开过 plain 后进入书架，点最近阅读第一条 `plain`（16:04 记录） | 阅读页打开并恢复位置 | ReaderTiming `prepareMs=442`；标题 `plain`，状态 `3% · UTF-8` | `_ui-out/rd015/`、logcat ReaderTiming | 通过 |
| 书架本地书打开 | 书架设置开启本地目录、关闭微信读书，点 `三国演义` | 进入统一阅读器并渲染 EPUB | ReaderTiming `prepareMs=3665`；标题 `三国演义.epub`，状态 `第1/122章 · 1/1`，封面可见 | `_ui-out/rd015/book-open.png` | 通过 |
| 最近阅读旧授权记录 | 点 13:29 的探针 URI 记录 `plain` | 授权失效给出明确提示 | 阅读页标题 `plain`，状态 `无法读取文档，请重新授权` | `_ui-out/bookshot.png` | 通过（错误语义） |
| 外部 VIEW 入口回归 | 探针 `--es sendFixture plain --es sendAction VIEW` | MainActivity 接收并进入阅读页 | logcat `MainIncoming valid=true opened=true`、`prepareMs=1561`，标题 `plain`、`3% · UTF-8` | logcat MainIncoming/ReaderTiming | 通过 |
| 来源开关恢复 | 验证后把本地目录关、微信读书开 | 与验证前一致 | 设置页标注：本地目录 `关`、微信读书 `开` | `_ui-out/rd015/screen-annotated.png` | 通过 |

## 说明

最近阅读通过 `RecentDocument(name, ref, updated)` 与 `RecentGateway.recent(limit)` 契约暴露，`ReaderModule` 在安装时注册，书架只读契约、不依赖阅读器实现。书架页在网格前渲染最近阅读区，最多 5 条，时间格式 `MM-dd HH:mm`，点击交给 `AppServices.reader`；无记录时不占位。书架本地书打开由原先的外部应用 Intent 改为 `AppServices.reader?.open(DocumentRef.LocalPath(path))`，微信读书深链保持不变，失败提示“文件阅读器尚未就绪”。

MainActivity 增加 `MainIncoming` 诊断日志，只记录 action、scheme、授权位与 open 结果，不记录 URI 路径。本轮据此确认外部入口在冷启动与重建场景下 `valid=true` 且 `opened=true`。

## 未覆盖与边界

- 文件页入口本轮未改代码，沿用既有设备验收；未重新执行图片、PDF、TXT、Markdown、ZIP 的设备全链路。
- 书架行数配置为 1 行时，合并列表只显示前 2 条；本地书与微信读书书的相对顺序取决于合并结果，本轮验证时通过关闭微信读书来源观察本地书。
- 复制、移动后阅读记录与实际文件路径的区分未做设备验证。
- 设备探针文档为临时夹具，验证后恢复来源开关并回到桌面；未删除设备上的探针安装与样本。
- 整包与设备门禁最终由 RD017 统筹，本记录只覆盖本轮增量。
