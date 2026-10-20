# task-12 配置写入原子性与迁移回写

## 目标与范围

0.9.2 阶段把配置写入从「先发布状态、后异步落盘」改为「落盘确认后再发布」，并把迁移结果回写磁盘，同时让高于当前版本的配置保持原样。写作用域限定在 `core/data` 的配置存储实现、迁移入口与单元测试。

## 改动文件

`core/data/src/main/java/com/shuiyu/octoberrain/core/data/ConfigPersistence.kt`（新增，55 行）

接口 `ConfigPersistence` 定义 `read(): String?` 与 `write(value: String): Boolean`，把持久化从 `JsonConfigStore` 中抽出，使写盘失败分支可以在纯 JVM 测试中注入。实现 `SharedPreferencesConfigPersistence` 使用 `commit()` 同步写入并返回结果；`apply()` 无法把失败反馈给调用方，因此不采用，耗时取舍在类注释中说明。

`core/data/src/main/java/com/shuiyu/octoberrain/core/data/JsonConfigStore.kt`（重写，210 行）

主构造 `JsonConfigStore(persistence, onWriteError?)`，保留 `constructor(context: Context)` 供 `OctoberRainApp` 原有装配点使用。`edit` 在同步块内先调用 `persist`（第 78 行），失败调用 `notifyWriteError` 并直接返回，成功后才发布 `_state`（第 83 行）。`load` 中读到高版本配置时置 `newerVersionDetected`、记录日志并原样返回（第 127 至 134 行）；迁移结果版本变化时立即回写，回写失败仍按迁移结果运行（第 135 至 141 行）。对外新增 `writeError` 与 `newerVersionDetected` 两个 `StateFlow`，`ConfigStore` 接口未改动。

`core/data/src/main/java/com/shuiyu/octoberrain/core/data/ConfigMigration.kt`（尾部兜底，第 82 至 86 行）

原实现把版本号强行改写为 `CURRENT_VERSION`，现改为记录告警并保留原版本与字段。函数入口的高版本原样返回保持不变。

测试文件

`JsonConfigStoreTest.kt` 新增 8 个用例：写盘成功后发布新值、写盘失败保留旧值、写盘失败触发回调、写盘恢复后清除错误、相同配置不写盘、迁移结果立即回写、迁移回写失败仍按迁移结果运行、高版本配置不清洗版本号。后者在 JSON 中追加未知节 `futureSection`，断言版本号、可读字段与磁盘原文均保持。`ConfigMigrationTest.kt` 新增「高于当前版本配置原样返回」。

## 设计要点

状态发布顺序：写盘与状态发布在同一把锁内，写盘确认成功才发布，消除内存与磁盘长期不一致。迁移回写：读取阶段版本变化即写回，避免每次启动重复迁移；回写失败只影响磁盘版本，本次运行仍使用迁移结果。高版本保护：高于 `CURRENT_VERSION` 的配置按可读字段加载，版本号与未知字段保持，写入次数为零。失败可见性：错误通过 `StateFlow`、日志与可选回调三处暴露，回调异常被 `runCatching` 隔离。

## 验证

`gradle --offline --console=plain :core:data:testDebugUnitTest detekt ktlintCheck`：BUILD SUCCESSFUL，8 秒，`JsonConfigStoreTest` 8 例与 `ConfigMigrationTest` 12 例全部通过；`core/data` 的 detekt 报告 0 条记录。

`gradle --offline --console=plain :core:data:testDebugUnitTest`：BUILD SUCCESSFUL，9 秒。

ktlint：工程未提供 `.editorconfig`，ktlint 1.4.1 使用官方风格。本次改动的 5 个文件先经 `:core:data:ktlintMainSourceSetFormat` 与 `:core:data:ktlintTestSourceSetFormat` 格式化，随后用快照比对把 13 个非本任务文件逐字节还原，SHA-256 比对确认一致。格式化后 `:core:data:ktlintCheck` 对 5 个文件无告警。仓库根脚本 `build.gradle.kts:41` 仍为 `ignoreFailures = true`，存量告警未清零。

## 整体测试与并行改动

首次执行 `gradle --offline testDebugUnitTest` 在 `:feature:settings:compileDebugKotlin` 失败：`BookshelfSettingsScreen.kt:46:32 'return' is prohibited here`。该文件与同目录多个设置页文件的最后修改时间为 23:03 至 23:05，属于 task-7 正在进行的设置页改造，与本次 `core/data` 改动无关联。该改造落定后重跑整体测试：BUILD SUCCESSFUL，36 秒。

## 遗留

`ConfigStore` 接口只暴露 `state` 与 `edit`，调用方读取 `writeError` 需要具体类型 `JsonConfigStore`；如果后续需要统一提示，可在接口层增加错误流。`SharedPreferencesConfigPersistence.write` 在调用线程同步执行 `commit`，目前由设置操作触发且配置体积为数十 KB，主线程耗时在可接受范围；若配置继续增大，可改为后台调度并在完成后发布状态。
