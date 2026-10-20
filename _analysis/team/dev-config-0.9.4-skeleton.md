# 0.9.4 task-21 文件模块骨架与数据容器报告

## 任务与范围

本任务对应计划 0.9.4 的 P1 批次：建立 `feature:files` 模块骨架，移植墨盘 P1 数据层与分页几何，注册文件页 Provider，并从 `feature:content` 移除文件页注册。相册、查看器、编辑器与相册页面属于 P2 至 P4，不在本次范围。

写作用域按 task-21 描述执行：`feature/files/**`（新建）、`core/common/.../Contracts.kt`、`settings.gradle.kts`、`app/src/main/java`、`app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/file_paths.xml`、`_analysis/team`。`Contracts.kt` 最终未修改，现有 `Screen`、`ScreenProvider`、`LauncherModule` 与 `Destination.Files` 已满足需要。

## 新增文件

模块构建与清单：

- `feature/files/build.gradle.kts`（43 行）：namespace `com.shuiyu.octoberrain.feature.files`，依赖 `api(:core:common)` 与 `:core:device`、`:core:design`、`:core:data`、`:core:render`，`testOptions.unitTests.isReturnDefaultValues = true`。
- `feature/files/src/main/AndroidManifest.xml`（41 行）：声明 `PhotoEntryActivity`（`ACTION_VIEW` 与 `image/*`）与 `PickEntryActivity`（`GET_CONTENT`、`OPEN_DOCUMENT`）两个导出薄入口。

数据层：

- `data/FileItem.kt`（102 行）：不可变条目，含名称、路径、目录标记、大小、时间、MIME 与可选的宽高；提供目录与文件两个工厂、`isImage`、`isEditableImage`、`extension`、`baseName`、`parentPath`。
- `data/FileSortKey.kt`（58 行）：排序键枚举与比较器，目录优先，名称比较忽略大小写，相等时按名称兜底。
- `data/FilePrefs.kt`（65 行）：文件页私有偏好，SharedPreferences 文件 `files_prefs`，保存视图模式、隐藏文件、排序键与方向、最后路径。
- `data/FileListing.kt`（214 行）：目录列举与路径换算，隐藏文件过滤、读取失败说明、上级路径、卷名、MIME 与大小时间格式化。
- `data/FileOps.kt`（187 行）：同名改名、复制、移动与内容比对，覆盖写入使用临时文件替换。
- `data/FileStreams.kt`（116 行）：8 KiB 缓冲的流式比对与写入，供 `FileOps` 使用。
- `data/StorageAccess.kt`（95 行）：存储权限判断、主存储与次要卷枚举、可读判断、书籍与图片目录、编辑输出目录。
- `data/FileIntents.kt`（44 行）：外部查看与打开意图，附加路径键改为 `com.shuiyu.octoberrain.extra.FILE_PATH`。

视图层：

- `ui/FileTokens.kt`（80 行）：尺寸与字号，dp 基准 18/56/52/44/30/6/16/12/44，字号 17/14/12 sp。
- `ui/FileMetrics.kt`（54 行）：按宿主内容区实测宽高推导内容区、列表行数、网格单元格与每页条目数。
- `ui/FilePager.kt`（558 行）：分页容器，含分页状态、页码夹取、350 ms 长按、48 dp 纵向滑动阈值与底栏命中。
- `ui/FilesScreen.kt`（61 行）：占位页面，标题「文件」与说明文案，有起始目录时追加显示。
- `FilesModule.kt`（31 行）：`LauncherModule` 实现，注册 `Destination.Files` 的 Provider。
- `ui/PhotoEntryActivity.kt`（34 行）、`ui/PickEntryActivity.kt`（31 行）：外部入口占位实现，记录日志后提示并结束。

测试：

- `FileMetricsTest.kt`（161 行，7 例）、`FileSortTest.kt`（115 行，8 例）、`FileItemTest.kt`（84 行，8 例）、`FilePagerTest.kt`（119 行，10 例），合计 33 例纯 JVM 测试。

## 几何基准与实算

宽度取宿主内容区 View 的实测宽度与高度，密度取 `DeviceProfile.density`，描边取 `InkTokens.stroke`。公式沿用墨盘：`contentTop = topBarH`，`contentBottom = heightPx - bottomBarH`，`listRows = max(1, contentH / rowH)`，`cellW = max(1, (widthPx - 2*padH - 3*gap) / 4)`，`cellH = max(1, cellW * 4 / 3)`，`cellTotalH = cellH + cellLabelH`，网格固定 4 列 4 行、每页 16 项。

三档设备实算值（按整屏高度计，宿主内容区需再减状态栏）：

| 设备 | 分辨率与密度 | padH/topBarH/bottomBarH/rowH | listRows | cellW/cellH/cellTotalH |
| --- | --- | --- | --- | --- |
| S4 | 480×800，219dpi，1.36875 | 25/77/71/60 | 10 | 101/134/156 |
| 通用 552 | 552×768，213dpi，1.33125 | 24/75/69/59 | 10 | 120/160/181 |
| 824 宽 | 824×1648，320dpi，2.0 | 36/112/104/88 | 16 | 179/238/270 |

824 宽设备的 4 列网格内容高度 1116 px，小于内容区 1432 px；552 宽设备 4 行内容高度 748 px，大于内容区 624 px，因此网格页按页绘制、不裁剪行，与墨盘横屏小屏行为一致。

## 工程注册改动

- `settings.gradle.kts`：在 `:feature:content` 后新增 `include(":feature:files")`。
- `app/build.gradle.kts`：在 feature 依赖后新增 `implementation(project(":feature:files"))`。该文件不在 task-21 声明的写作用域内，属模块注册必需的最小改动。
- `app/src/main/java/com/shuiyu/octoberrain/OctoberRainApp.kt`：反射模块名单新增 `com.shuiyu.octoberrain.feature.files.FilesModule`。
- `app/src/main/res/xml/file_paths.xml`：新增 `<external-path name="files_share" path="." />`，分享复用现有 `FileProvider`，authority 为 `${applicationId}.files`。
- `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ContentModule.kt`（52 行）：移除 `FilesScreen` 导入与 `Destination.Files` 分支，KDoc 由六页面改为五页面。旧 `feature/content/.../ui/FilesScreen.kt` 保留，按决策文档待新浏览页真机验收后删除。

## 验证结果

门禁命令：

```
gradle --offline :feature:files:testDebugUnitTest :app:assembleDebug check
```

结果 BUILD SUCCESSFUL，1005 个任务，无失败。模块内 `detekt`、`ktlintCheck`、`lintDebug` 与 `testDebugUnitTest` 均通过；源代码全部行不超过 120 列；`check` 覆盖全仓 detekt、ktlint、lint 与各模块单元测试。

静态检查首轮发现 24 项 detekt 问题与 4 个 ktlint 任务失败，处理方式：

- 函数数量超限：把流式读写拆到 `FileStreams`，`FileOps` 保留 4 个公开方法，其余流程函数改为文件级私有函数。
- 返回数量与复杂条件超限：比对、改名、列举与移动流程改为单出口或提前返回不超过两处。
- 异常吞掉告警：捕获处显式使用下划线参数并保留说明注释。
- `FileSort.kt` 文件名与主声明不匹配：重命名为 `FileSortKey.kt`。
- ktlint 问题：对本人文件执行格式化任务，未改动其他模块文件。

`lintDebug` 首次运行因基线文件缺失而中断，并在 `config/lint/baseline-feature-files.xml` 生成基线；该文件为根构建脚本按模块约定自动生成，位于声明写作用域之外。基线内容 1360 字节，保持为空警告基线。

## 真机验证

设备 S4，480×800，219 dpi，Android 版本以设备上报为准。安装 `app/build/outputs/apk/debug/app-debug.apk` 后从桌面进入文件页：

- 页面标题「文件」，正文「文件浏览功能正在迁移，当前版本仅显示占位内容。」
- logcat 输出 `D/OctoberRain: page=文件 create=21ms stack=1`，无 `FATAL EXCEPTION`，无模块加载失败日志。
- 占位页截图：`_analysis/team/files-placeholder-s4.png`。

桌面默认隐藏 `special:files` 入口，真机验收时临时从设备配置的 `hiddenSpecials` 移除该项，验证后已用备份 `_analysis/team/device-0.9.4-config-before.xml` 恢复，当前设备配置与验证前一致。

## 遗留事项

- 旧 `feature/content/.../ui/FilesScreen.kt` 保留，待 P2 浏览页真机验收后删除。
- 文件页偏好写入独立 SharedPreferences，未并入 `LauncherConfig`，与决策文档一致。
- 相册缩略图与图片查看属于 P2 至 P4，本次未实现；`PhotoEntryActivity` 与 `PickEntryActivity` 仅占位。
- 桌面文件入口默认隐藏，用户需通过配置解锁；是否需要设置页开关待后续批次决定。
- `file_paths.xml` 新增的外置存储根映射扩大了 `FileProvider` 可授权范围，授权仍由每次分享的 URI 授权控制，后续可收窄到具体目录。
- 手工真机验收依赖临时修改设备配置，后续可考虑提供调试入口或测试专用 Activity 以自动化复现。

## 参考

- `docs/16-墨盘移植决策.md`
- `_analysis/team/mopan-port-plan.md`
- `docs/15-V1.0开发计划.md`
