# P3 图片查看与编辑入口交付报告

任务：0.9.4 计划 3.4（图片查看器与编辑入口），对应共享任务 task-23。
交付时间：2026-10-20。
执行者：dev-core。

## 一、目标与结果

1. 查看器具备三档缩放（适配、填充、原尺寸）、左右滑动换图、动作面板与详情面板、分享。
2. 浏览页图片点击进入查看器，路径列表限定同一目录，越界时提示并回弹。
3. 查看器底栏四个动作（旋转、裁剪、滤镜、另存）导航到编辑页，携带当前图片绝对路径与来源标识。
4. 外部 `ACTION_VIEW` 与 `GET_CONTENT` / `OPEN_DOCUMENT` 两个入口可打开图片与回传文件。
5. 真机（SA4B03CN2608081096X，API 30）完成主要路径验证，首屏解码耗时 112 ms 至 242 ms。

## 二、代码改动

核心契约与路由：

| 文件 | 位置 | 改动 |
| --- | --- | --- |
| `core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt` | 260 | 新增 `Destination.PhotoEditor(path: String, source: String? = null)` |
| 同上 | 336 | `displayLabel()` 返回「图片编辑」 |
| `core/common/.../Contracts.kt` | 246 | 前阶段新增 `Destination.PhotoViewer(paths, index)` |
| `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/FilesModule.kt` | 35 | 新增 `PhotoEditor` 分支，返回 `EditScreen(path, editSourceOf(source))` |

查看器与列表接入：

| 文件 | 行数 | 说明 |
| --- | --- | --- |
| `feature/files/.../ui/viewer/PhotoScreen.kt` | 651 | 查看器页面与视图，含绘制、手势、面板、分享、预取、解码代际校验 |
| `feature/files/.../ui/viewer/ViewerState.kt` | 214 | 档位、下标与预取范围的纯逻辑 |
| `feature/files/.../ui/viewer/ViewerChrome.kt` | 234 | 顶栏、底栏绘制与命中判定 |
| `feature/files/.../ui/viewer/ViewerDecoder.kt` | 61 | 按档位目标像素解码并按开关量化或灰度 |
| `feature/files/.../ui/BrowsePane.kt` | — | 新增 `itemSnapshot()` 供点击时取同页条目 |
| `feature/files/.../ui/BrowseScreen.kt` | — | 图片点击改为 `PhotoViewer(同目录图片路径, index)` |

入口 Activity 与选图页：

| 文件 | 行数 | 说明 |
| --- | --- | --- |
| `feature/files/.../ui/PhotoEntryActivity.kt` | 120 | `ACTION_VIEW` 入口；显式路径直接打开，`content` 或 `file` Uri 复制到 `cacheDir/viewer/`，上限 64 MiB |
| `feature/files/.../ui/PickEntryActivity.kt` | 150 | `GET_CONTENT` / `OPEN_DOCUMENT` 入口；选择后经 `FileProvider` 回传 `content://` |
| `feature/files/.../ui/PickPane.kt` | 191 | 继承 `FilePager`，行数按内容区换算，单目录上限 400 行 |
| `feature/files/src/main/AndroidManifest.xml` | — | 两个入口的 intent-filter 保持原声明，注释改为已实现说明 |
| `app/src/main/res/xml/file_paths.xml` | — | 保留 `external-path files_share`，新增 `cache-path files_share_cache path="share/"` |

兼容保留：`feature/files/.../data/Edits.kt`（200 行）在查看器改为进入编辑页后不再被查看器调用，旋转与导出统一由编辑页的 `EditSession` 与 `EditExport` 承担；该文件保留为兼容 API，已有 7 例单元测试覆盖角度循环与输出命名，后续版本可评估移除。

## 三、行为说明

缩放三档：适配取内容区短边等比，填充取长边铺满并裁去溢出部分，原尺寸按 1:1 中心裁切显示，解码目标长边取视口长边的两倍并受 `ImageSizing` 上限约束，不实现拖动平移。

换图与预取：水平位移超过 48 dp 判定换图，前后各预取一张，上限 2 张，解码结果按代际与下标双重校验；首图再向左、末图再向右时提示「已到第一张」或「已到最后一张」并停止换图。

动作与详情：长按打开动作面板，四行依次为分享、旋转、详情、量化；点击详情显示尺寸、大小、时间、路径、格式五行。长按后同一次抬起不再按点击处理，避免面板被立即关闭。

分享：经 `FileProvider` authority `${applicationId}.files` 生成 `content://`，附加 `FLAG_GRANT_READ_URI_PERMISSION`；主存储之外的文件先把副本写入 `cacheDir/share/` 再生成 Uri。

选图回传：结果格式为 `content://com.shuiyu.octoberrain.files/...`，`Intent.data` 携带 Uri 并带 `FLAG_GRANT_READ_URI_PERMISSION`；若 `callingPackage` 非空，另外对该包名执行 `grantUriPermission`。调用方按 `contentResolver.openInputStream(data)` 读取即可，不必依赖文件路径。

## 四、验证记录

构建与静态检查：

| 命令 | 结果 |
| --- | --- |
| `gradle --offline :feature:files:compileDebugKotlin` | 通过 |
| `gradle --offline :feature:files:detekt` | 通过，本任务三个文件告警清零 |
| `gradle --offline :feature:files:ktlintCheck` | 本任务文件 0 条；剩余 6 条全部位于 `ui/edit/**` |
| `gradle --offline :app:assembleDebug` | 通过，`app-debug.apk` 3961648 字节 |
| `gradle --offline :feature:files:testDebugUnitTest` | 16 套件 130 例 0 失败（阶段二数据，本次改动未新增测试文件） |

真机验证与截图（`_analysis/verify/shots/`）：

| 截图 | 内容 |
| --- | --- |
| `t23-page3.png` | 桌面特殊条目「设置」「文件」 |
| `t23-browse.png`、`t23-browse2.png`、`t23-pictures.png`、`t23-bench.png` | 文件页列表形态与目录进入，`thumbs-bench` 共 51 页 |
| `t23-viewer-fit.png` | 打开 `exif-orient-6.jpg`，顶栏「返回 文件名 1/501」，底栏四动作 |
| `t23-zoom2.png`、`t23-zoom3.png` | 填充与原尺寸档位 |
| `t23-swipe-0.png`、`t23-swipe-1.png` | 首图与左滑后的 `img-001.jpg`（2/501），命中预取无重复解码 |
| `t23-boundary.png` | 首图继续右滑提示「已到第一张」 |
| `t23-actions.png` | 长按动作面板四行与提示行 |
| `t23-editor.png` | 底栏动作进入编辑页，标题「编辑图片」，底部裁剪、旋转、滤镜、另存 |
| `t23-share.png` | 分享经选择器交由已安装接收应用打开 |
| `t23-picker.png` | 选图页列出 `Pictures` 目录与文件，底栏「取消」 |
| `t23-external-view.png` | 外部 `ACTION_VIEW` 打开，副本文件名带时间戳 |

首屏解码耗时来自 logcat：

| 场景 | 日志 |
| --- | --- |
| 浏览页进入查看器 | `page=图片查看 create=27ms`、`解码 exif-orient-6.jpg 下标 0 耗时 242ms` |
| 外部 ACTION_VIEW 副本 | `page=图片查看 create=28ms`、`解码 1792430230518_exif-orient-6.jpg 下标 0 耗时 112ms` |
| 早前一轮外部入口 | `解码 ... 耗时 234ms` |

EXIF 方向：主机 `D:\DSHStation\_tmp\exif-orient-6.jpg` 原始像素 320×240 横图并带 Orientation=6，查看器显示为 240×320 竖图，`ImageDecoder` 归一结果与主机端阅读方向一致；设备文件 md5 `2f57230163a56092c737bbd9872a3302` 与主机一致。

## 五、判断记录

1. 任务描述要求「另存」按钮在编辑页交付前后表现不同，故在编辑页可用前提下直接把四个动作统一导航到编辑页，不保留占位提示；编辑页由 dev-ui 在 task-27 交付，入口签名按 `EditSource` 区分来源。
2. 分享的二次存储卷路径：`external-path` 覆盖不到 `/storage/XXXX-XXXX/` 卷根，`external-media-path` 也不覆盖卷根，因此采用复制到缓存再分享的回退方案，避免声明过宽的分享路径。
3. 长按与点击冲突：`ACTION_UP` 在长按触发后会再次按点击处理并关闭刚打开的面板，增加 `longPressFired` 标记并在同一次抬起中跳过点击分发。
4. 面板行区域由 `ViewerMetrics.rowArea` 划分；此前误用底栏等分区域导致五行挤压与提示文字截断，已改为按面板区域计算。
5. 查看器不再回收位图所有权由缓存持有；`Edits` 保留而不删除，避免破坏既有测试与兼容调用。

## 六、遗留

1. 完整门禁（`check` 与全模块 detekt、ktlint）由 dev-ui 在本轮静态清理完成后统一执行；当前模块 ktlint 失败点全部位于 `ui/edit/**`，不在本任务写作用域。
2. 编辑页的裁剪、旋转、五档滤镜与另存交互由 dev-ui 在真机验收，本报告只覆盖入口跳转与返回。
3. 选图回传的消费方尚未接入，`content://` 读取路径已在代码与报告中写明，待后续页面使用。
4. 原尺寸档位不提供拖动平移，属于 e-ink 连续重绘与内存预算的取舍，已按 lead 裁决记录。
5. API 26 至 28 设备未覆盖，查看器仅依赖 `ImageDecoder` 与 `FileProvider`，风险集中在系统选择器行为差异。
