# 2026-10-22 RD015 本轮交接：基础阅读入口与最近阅读

## 入口

用户指令：完成 Android CLI 子命令接入后继续后续工作，并按最新工作流（`scripts/android-ui.ps1`、`scripts/build.ps1`）操作。本轮按交接文档推进 RD015，完成书架入口与最近阅读集成并做设备验证。

## 改动

- `core/common/src/main/java/com/shuiyu/octoberrain/core/common/ReaderContracts.kt`：新增 `RecentDocument(name, ref, updated)` 与 `fun interface RecentGateway { fun recent(limit: Int): List<RecentDocument> }`。
- `core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt`：AppServices 增加 `var recent: RecentGateway? = null`。
- `feature/reader/src/main/java/com/shuiyu/octoberrain/feature/reader/ReaderModule.kt`：安装时注册 `AppServices.recent`，映射 `ReadingRepository.recent(context, limit)`，名称取 `ReadingStateRules.displayName`，异常返回空列表。
- `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/BookshelfScreen.kt`：网格前增加最近阅读区（最多 5 条、`MM-dd HH:mm`、点击走 `AppServices.reader`）；本地书 `open` 改为 `AppServices.reader?.open(DocumentRef.LocalPath(path))`，失败提示“文件阅读器尚未就绪”。
- `app/src/main/java/com/shuiyu/octoberrain/MainActivity.kt`：`openIncomingDocument` 增加 `MainIncoming` 诊断日志，只记录 action、scheme、授权位与 open 结果。

## 验证

- 模块：`:core:common`、`:feature:reader`、`:feature:content` 的 `testDebugUnitTest` 与 `detekt` 通过（31s）。
- 整包：`check :app:assembleDebug :app:assembleRelease` 通过（3m18s，1564 tasks，EXIT=0）。debug 5,517,631 字节 SHA-256 `4e8537f795a7b868ac3e59b08479f046f05eb5fe4b886ea94f1ea3b08bc962fc`。
- 设备（S4 SA4B03CN2608081096X）：书架最近阅读点击 plain 恢复 `3% · UTF-8`；书架本地书三国演义.epub 打开为 `第1/122章 · 1/1`；外部 VIEW 探针 `MainIncoming valid=true opened=true`；旧探针 URI 显示“无法读取文档，请重新授权”；测毕来源开关恢复、回桌面。
- 验收记录：[2026-10-22-reader-entry-integration-dsh.md](../../docs/acceptance/2026-10-22-reader-entry-integration-dsh.md)；能力表：[2026-10-22-reader-mvp-capability-dsh.md](../../docs/acceptance/2026-10-22-reader-mvp-capability-dsh.md)。

## 工具经验

- CLI `screen capture --annotate` 在系统 TEMP 被拒时报 `Can't create cache file!`。`scripts/android-ui.ps1` 已把 `TMP`/`TEMP` 与默认 `ANDROID_USER_HOME` 指向工作区 `_ui-out/`，脚本保持 UTF-8 BOM。
- 书架行数配置为 1 行时合并列表只显示 2 条，验证本地书需临时关闭微信读书来源；测毕恢复。
- 墨水屏页面切换与 uiautomator 采样存在 2–4 秒滞后，阅读页就绪以 `ReaderTiming prepareMs` 日志与截图为准。

## 未覆盖

- 文件页非图片入口本轮未重跑（代码未改，沿用既有验收）。
- 复制、移动后阅读记录与实际路径的区分未做设备验证。
- 能力表五条入口文案建议（Office 入口拦截、UNKNOWN 提示、图片缩放说明、Markdown 纯文本提示、格式硬编码项）留待后续决策。
- 整包与设备门禁最终由 RD017 统筹。

## 设备状态

S4 已回到 MainActivity 桌面；探针与产品均已 force-stop 后重新启动；书架来源恢复为本地目录关、微信读书开。

## 后续排期更新（2026-10-22）

原直接进入 RD016 的建议已由用户最新整改优先级取代：FIX001 → FIX002 → FIX003 → RD016。见 [最新整改交接](2026-10-22-review-remediation-to-dsh.md)。本文件此前验证只代表当时旧书架入口，不可作为搬迁后验收。
