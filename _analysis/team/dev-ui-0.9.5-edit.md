# 0.9.5 P4-B 编辑界面实施报告

任务：task-27「0.9.5 P4-B 编辑界面（裁剪、旋转、滤镜、另存）」。写作用域为
`feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/ui/edit/**`、
`feature/files/src/test/java/com/shuiyu/octoberrain/feature/files/ui/edit/**` 与 `_analysis/team`，
未修改 `ui/viewer/**`、`Contracts.kt`、`FilesModule.kt`、清单与 `file_paths.xml`。

## 一、交付文件

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `ui/edit/EditScreen.kt` | 58 | `Screen` 契约入口，组装 `FileTokens`，转发生命周期与返回 |
| `ui/edit/EditSurface.kt` | 759 | 自绘主体：顶栏、预览、控制条、四页签、裁剪拖动、后台预览与导出 |
| `ui/edit/CropView.kt` | 175 | 裁剪框暗区、三分线、四角手柄与比例提示绘制 |
| `ui/edit/CropFrame.kt` | 190 | `IntRect`、`CropTarget`、`NormRect`、`CropHitTest`、`CropDrag` 纯逻辑 |
| `ui/edit/EditState.kt` | 164 | `EditTab`、`CropAspect`、`NormRect`、`EditUiState` 与档位夹取 |
| `ui/edit/EditExportPlan.kt` | 72 | 导出操作序列、灰阶参数、可保存判定、摘要文案 |
| `ui/edit/PreviewSizing.kt` | 66 | 解码长边、滤镜预览缩放与尺寸 |
| `ui/edit/EditStrings.kt` | 92 | 页面文案常量 |
| `src/test/.../ui/edit/EditSurfaceLogicTest.kt` | — | 14 例纯 JVM 用例 |

`EditScreen` 构造签名为
`EditScreen(path: String, source: EditSource = EditSource.FILES, onClose: () -> Unit = { AppServices.router.back() })`，
`EditSource` 取 `FILES` 与 `VIEWER`。入口按 task-27 描述由 P3 在 task-23 接线，本任务不修改
`FilesModule.kt` 与 `Destination`。

## 二、页面结构与几何

`EditSurface.layoutChrome`（`EditSurface.kt:275`）按宿主内容区高度划分四段：

- 顶栏：`tokens.topBarH`（S4 为 77px），左侧取消命中区宽 `tokens.actionButtonWidth`，右侧保存同宽，标题居中。
- 预览区：顶栏下沿至控制条上沿，全部用于图片与裁剪浮层。
- 控制区：`tokens.rowH * 2`（S4 为 120px），两行按钮；另存页第二行改成两行摘要。
- 底栏：`tokens.bottomBarH`（S4 为 71px），四页签等宽「裁剪 / 旋转 / 滤镜 / 另存」。

全部矩形为像素 `IntRect`，命中用 `contains`；字号取 `FileTokens.titleText/bodyText/smallText`
（17sp/14sp/12sp），描边取 `tokens.stroke`（S4 为 2px），与 P2 浏览页共用令牌来源。

## 三、裁剪与命中顺序

`CropHitTest.hit`（`CropFrame.kt:61`）返回单个 `when` 表达式，优先级固定为：

1. 左上角 → 右上角 → 左下角 → 右下角（手柄半径内）；
2. 框内 `INSIDE`；
3. 其余 `OUTSIDE`。

角手柄范围是以框角为中心、边长为两倍半径的正方形，`handlePx` 非正时按 1 处理，
保证小触点仍可命中。`EditSurface.onDown`（`EditSurface.kt:406`）先判定触点是否落在
预览图片区域内，再调用命中函数；`OUTSIDE` 不进入拖动，`ACTION_UP` 按点击分发。

拖动由纯函数完成：`CropDrag.move` 保持宽高并夹取边界，`CropDrag.resize` 固定对角、
按纵横比与最小边长约束修正，目标不是四角时原样返回 `clamp` 结果。纵横比选项为自由、
1:1、4:3、3:4；提交裁剪前用 `CropMath.isLargeEnough` 与 `CropMath.minEdge` 校验，过小提示
「裁剪范围过小」，通过后 `session.push(EditOp.Crop(rect))` 并重置界面框。

## 四、旋转与滤镜

旋转按钮调用 `session.push(EditOp.Rotate(1))`，每次顺时针 90 度，`rotationQuarters` 同步；
旋转偏好由内核 `PrefsRotationStore` 写入 `edited_rotations:<路径>`，操作栈上限 20 步。
内核未提供 `EditSession.nextAngle`，`task-27` 描述中的名称与实现不一致，此处按 `EditOp.Rotate`
语义实现。

滤镜页签列出五档与三档对比度，档位文案取自 `EditUiState.FILTER_LABELS` 与
`CONTRAST_LABELS`，与内核 `GrayKernels.LEVELS=5`、`CONTRASTS=3` 对齐。预览链路：

1. `ImageDecoder.decode(sourcePath, edge)`，`edge = PreviewSizing.decodeEdge(内容区宽, 高)`，
   结果夹取到 32 至 1080；
2. 会话操作应用在解码图上，几何结果保留在 `geometryBase`；切换滤镜档位只重算滤镜；
3. 滤镜前按 `PreviewSizing.previewSize` 缩放到长边 360，像素数组交 `Gray.apply`，
   `keepAlpha=false`，再用 `Bitmap.createBitmap(IntArray)` 生成预览位图；
4. 后台单线程加代际校验，过期结果回收，避免快速拖动或连点造成内存增长。

## 五、另存与刷新

`requestSave`（`EditSurface.kt:503`）先判定是否存在修改，无修改提示「没有可保存的修改」，
有修改弹 `InkDialogs.confirm` 确认；确认后 `save`（`EditSurface.kt:519`）：

- `EditExportPlan.ops(session.exportOps(), filterLevel, contrastLevel)` 在几何操作末尾追加
  `Filter` 与 `Contrast`，保证先几何后滤镜；
- `EditExport.export(context, sourcePath, ops)` 写入 `Pictures/十雨桌面`，命名
  `原名-编-yyyyMMdd-HHmmss.png`，重名追加序号；长边按 `getMemoryClass` 取 1600 或 2400；
- 成功后 `ThumbCacheRegistry.invalidate(context, File(sourcePath))`、
  `RefreshScheduler.notifyContentChanged()`、`AppServices.refresh.requestPartial()`，提示「已保存」
  并回调关闭；
- 失败提示「保存失败」与内核返回的原因。

放弃修改时同样弹确认；已保存或确认放弃后调用 `onFinish` 返回来源页面。
`detach`（`EditSurface.kt:181`）递增代际、关闭工作线程、回收预览、几何与显示位图。

## 六、测试与校验

新增 `EditSurfaceLogicTest`，14 例，覆盖：

- 四角手柄优先于框内、框内与框外、非正半径按 1 处理；
- 整体拖动保持尺寸并夹取、手柄按比例约束、框内拖动不变；
- 归一化矩形夹取的最小边长与边界；
- 页签循环、档位夹取、编辑状态判定、旋转重置裁剪框；
- 预览解码长边夹取、缩放比例与尺寸；
- 导出操作序列追加顺序、另存摘要与可保存判定。

已执行：

- `:feature:files:compileDebugKotlin` 通过；
- `:app:assembleDebug` 通过，APK `app/build/outputs/apk/debug/app-debug.apk`
  3,960,931 字节，生成时间 2026-10-20 01:17:59；
- `:feature:files:compileDebugUnitTestKotlin` 与定向测试
  `--tests 'com.shuiyu.octoberrain.feature.files.ui.edit.*'` 通过，14 例 0 失败；
- `ui/edit` 目录 detekt 0 条、ktlint 主源集 0 条、测试源集 0 条，超 120 列 0 行。

模块级 `:feature:files:detekt` 仍有 10 条权重告警，全部位于其他成员正在修改的文件：
`ui/PhotoEntryActivity.kt`（ReturnCount、NestedBlockDepth）、`ui/PickPane.kt`
（TooManyFunctions、ReturnCount、FunctionOnlyReturningConstant）、
`ui/viewer/PhotoScreen.kt`（ReturnCount、LoopWithTooManyJumpStatements）。这些不在本任务
写作用域，已通知 dev-core 清理；清理后需重跑完整门禁
`:feature:files:testDebugUnitTest :feature:files:ktlintCheck :feature:files:detekt :app:assembleDebug check`。

## 七、真机验收

入口已由 P3 接线：`core/common/.../Contracts.kt` 定义 `data class PhotoEditor(path, source)`，`FilesModule.kt:35` 分支返回 `EditScreen(path, EditSource.VIEWER)`；查看器底栏「旋转」「裁剪」「滤镜」「另存」与动作面板「旋转」均导航到编辑页。走查设备 S4 `SA4B03CN2608081096X`（480×800，density 219）。

走查路径：桌面第二页「文件」→ `Pictures/thumbs-bench` → 打开 `exif-orient-6.jpg` → 底栏「裁剪」。验证结果：

1. 编辑页首屏 35ms（logcat `page=图片编辑 create=35ms stack=3`），截图 `edit-02-editor.png`。
2. 裁剪：四档纵横比可选（`edit-03-crop-aspect.png`）；拖动角手柄调整裁剪框（`edit-14-crop-drag.png` 与更早的 `edit-03b`）；应用裁剪后按操作重建预览并把裁剪框重置为全图；两次导出的内容偏移与裁剪区域一致。
3. 旋转：顺时针 90°（`edit-04-rotate-90.png`）与连续两次（`edit-05-rotate-180.png`）；无操作时「撤销一步」置灰。
4. 滤镜：五档滤镜与三档对比度选择态（`edit-06-filter.png`），预览在长边 360 的像素数组上计算。
5. 另存：确认对话框（`edit-07-save-dialog.png`）；保存成功后关闭并返回查看器（`edit-09-save-flow.png`）；产物写入 `/sdcard/Pictures/十雨桌面`，名称为 `exif-orient-6-编-20261020-013859.png`（1734B）、`…-014115.png`（9490B）、`…-014446.png`（9623B），符合 `EditExport` 的命名与目录规则。
6. 裁剪框对比度：白底图片上原始的纸白手柄不可见，已改为墨色线条加纸白衬底、手柄纸白填充加墨色描边（`edit-12-crop-overlay.png`、`edit-13-crop-applied.png`）。

走查中发现并修复的缺陷：

1. 旋转时后台预览任务与旧源位图回收竞争，抛出 `IllegalArgumentException: cannot use a recycled source in createBitmap`（`data/EditSession.kt:275`）。修复为重建入口检查 `isRecycled`、复用几何结果前校验、`detach` 不再回收预览源图而交由内存回收（`ui/edit/EditSurface.kt:232`、`:181`）。复测旋转、滤镜、裁剪均无崩溃。
2. 保存成功后 `onFinish` 回调重新进入 `EditScreen.onBack`，弹出「放弃编辑」对话框。修复为 `EditScreen` 增加关闭标记与 `close()`，保存成功路径标记关闭中，宿主弹栈不再二次确认（`ui/edit/EditScreen.kt`）。
3. `FilterPanel` 在回收缩放位图后仍读取其宽高，改为先保存宽高再回收（`ui/edit/FilterPanel.kt`）。
4. 裁剪覆盖层配色在浅色图片上不可见，改为墨色线条加纸白衬底（`ui/edit/CropView.kt`）。

## 八、性能与门禁记录

- 编辑页首屏 35ms，图片查看页 25 至 41ms；预览长边 360，导出长边 1600 或 2400；操作栈 20 步。
- 导出耗时与 PSS 本轮未采集：编辑页为常驻自绘页面，位图随预览缩放，建议由统一性能任务采集。
- 五档滤镜与三档对比度切换时预览刷新正常，截图保留高反差 + 对比度高的组合（`edit-06-filter.png`）；逐档截图因设备被其他工作流并行占用而中断，未保留完整五张。
- 走查期间设备默认桌面角色曾被临时改为十雨桌面以规避系统选择器，结束时已恢复为 `com.wetao.elauncher`，并删除 `files_prefs.xml` 与本任务临时截图。
- 门禁：`gradle --offline :feature:files:testDebugUnitTest :feature:files:detekt :feature:files:ktlintCheck check :app:assembleDebug` 通过（45 秒，1005 任务）；模块测试 19 套件 170 例 0 失败；`ui/edit/**` detekt 0 条、ktlint 0 条、超 120 列 0 行；APK 3,841,445 字节。
- dev-core 已清理 P4 文件的 import-ordering、function-signature、multiline 与 TooManyFunctions/ReturnCount 告警，模块 `ktlintCheck` 与 `detekt` 均通过，完整门禁已一次通过。

## 九、遗留问题

1. 裁剪四角手柄的真机拖动以截图确认，触摸回归仅覆盖纯函数命中判定，未增加仪器用例。
2. 旋转后裁剪框统一重置为全图，不保留旋转前的归一化范围。
3. `EditSource` 仅记录来源，保存后统一返回查看器，不决定列表定位。
4. 未提供「保存并继续编辑」，另存页只有一个保存按钮。
5. 完整门禁在 P4 文件清理后通过；此前阻塞项已由 dev-core 处理。
6. 真机导出耗时与 PSS 未采集。

## 十、修订记录

初版按单文件 `EditSurface`（759 行）描述；按 lead 要求已拆分为 `EditChip.kt`、`CropPanel.kt`、`RotatePanel.kt`、`FilterPanel.kt`、`SavePanel.kt`，`EditSurface.kt` 保留布局、绘制、手势与保存流程。另修正入口名称：`Destination.ImageEdit` 实为 `Destination.PhotoEditor`，接线由 dev-core 完成。


