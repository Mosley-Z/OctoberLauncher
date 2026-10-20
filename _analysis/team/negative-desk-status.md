# 负一屏墨台接入现状

本文件记录十雨桌面负一屏墨台三个模块（bill、rank、fp）的接入完成度与数据依赖，供验收记录引用。

## 已通：DeskPage 分区渲染

`feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/WereadDeskSectionView.kt` 为每个模块构造一个独立 `DeskPage`，页面掩码只包含当前模块对应的位（`DeskHost.deskModuleMask()` 返回 `DeskPrefs.bitOf(moduleId)`，bill 为 1、rank 为 2、fp 为 8），`deskOrder()` 只返回当前模块标识，因此内核虽然按墨台的完整排版流程构建页面，实际只会绘制一个分区。

绘制路径：`onMeasure` 以卡片宽度调用 `page.height(width)` 得到整页高度，减去 `renderer.headerHeight(unit)` 得到分区高度；`onDraw` 先 `clipRect` 到分区高度，再 `translate(0, -headerHeight)`，使墨台顶栏落在可视区域之外，只呈现分区标题与正文。分区宽度由实际视图宽度决定，字号与行距随 `scale()` 返回的排版单位等比缩放。

## 已通：命中与动作分派

分区点击由 `SectionCanvas.onTouchEvent` 接收，抬起时把坐标换算到墨台页面坐标系（`hitTest(x, y + headerHeight)`），命中结果交给 `page.onAction(action)`，随后请求负一屏宿主重绘。`ACTION_BACK`、`ACTION_SETTINGS`、账单周期左右切换、阅读资产排序切换等动作均由内核 `DeskPage` 内部处理；需要页面跳转的动作经 `DeskHost.openPage(tab)` 映射到 `Destination.WereadTab` 的账单、排行、统计分段或 `Destination.NegativeSettings`。

## 已通：资源释放

`WereadDeskSectionView.reload()` 释放旧页面并重新排版，供墨单外观配置变化与数据导入完成后调用；`release()` 在模块 `onDestroy` 中调用，释放 `DeskPage` 持有的数据缓存与图像资源。

## 已通：宿主契约实现

`WereadDeskHostImpl.kt` 实现内核 `DeskHost` 的全部方法：`filesDir()` 指向 `context.filesDir/weread`；`menuPrefs`、`menuBool`、`menuInt` 经 `WereadDeskMenu.kt` 映射到 `LauncherConfig.integrations.wereadMenu` 的有效值，字符串选项只认 cfg 分组；`fpFontPx()` 返回 48；`styleColor` 把墨台令牌映射到当前主题的 INK、INK_2、PAPER、INK_4、INK_3；`requestRedraw` 转负一屏宿主；`openPage` 完成页面跳转映射。

## 已改：scale 口径

`WereadDeskHostImpl.scale()` 曾按「负一屏像素宽度除以 368 并钳制 0.6 至 2.0」计算，与微读墨台的原意不同。现改为 `max(displayMetrics.widthPixels, displayMetrics.heightPixels) * UNIT_RATIO`，`UNIT_RATIO = 0.0015f` 放在伴生对象并注明与微读 `InkTheme.UNIT_RATIO` 一致；480×800 面板上结果为 1.2，与微读墨台字号和行距一致。原 `widthPx` 字段、`BASE_WIDTH_PX` 常量与钳制区间已删除，`widthDp` 参数一并从 `WereadDeskHost` 移除。

## 空态：数据未落盘

墨单、阅读资产、阅读指纹三个分区在真机上均显示空态，原因在数据侧：十雨仓内只有 `feature/content/.../data/KernelWereadReader.kt` 读取 `context.filesDir/weread`，没有写入该目录的代码，真机 `run-as com.shuiyu.octoberrain ls files` 也不存在 `weread` 目录。桌面整体卡片 `weread_board` 能显示阅读数据，是因为 `KernelDataSourceImpl` 在文件缺失时回退到 `ContentSource` 的网络结果，而 `DeskPage` 只读原盘文件，不走该回退。

## 空态：导入引导

`WereadDeskSectionView` 在 `page.height(width) - headerHeight` 不足以承载内容，或按模块判断数据为空（墨单按账单条目、阅读资产按资产条目、阅读指纹按可绘制书目）时，隐藏绘制区域并显示「导入微读墨记数据」按钮与结果文案。按钮经 `AppServices.wereadImport?.requestImport { message -> ... }` 发起迁移，回调文案显示在按钮下方，随后 `reload()` 重新排版并请求负一屏重绘，使数据就绪后立即绘制分区。导入组件缺失或未找到导出组件时显示对应说明。

## 真机复验：导入按钮

真机负一屏（2026-10-20 14:08）验证结果记录如下。

按钮为真实控件而非 Canvas 绘制：空态判定、隐藏绘制区域与显示引导区域均在 `WereadDeskSectionView.onMeasure` 内完成，按钮与结果文案同属该视图的 `guideRow` 子视图；`SectionCanvas` 只负责分区正文绘制与点击命中，不承担按钮绘制。

点击按钮经 `AppServices.wereadImport.requestImport` 拉起微读墨记导出组件。日志确认 `ActivityTaskManager: START u0 cmp=com.inkread.weekread/.bridge.ExportActivity (has extras)`，说明调用通道生效，未经过任何页面跳转。

导出组件返回的结果文案显示在按钮下方：实测回调文案为「缺少 target_uri」，说明 `HostWereadImport` 附带的 extra 键与微读墨记 1.3.2 导出组件读取的键不一致。该文件位于 app 层，不属于本任务写域。

此前记录的「点击按钮跳到微信读书账单页」未能复现。按 `uiautomator dump` 得到的按钮 bounds 中心（约 x=130、y=577）点击时正常发起导入；点击 x=240、y=577 落在引导行空白区域，无任何响应。该次跳转推测来自点击落在墨单卡片标题行「详情」区域，命中详情目标 `Destination.WereadTab(WereadSection.BILL)`，与按钮无关。



## 未覆盖

微读原盘数据由迁移导入通道落盘，落盘责任不在本任务；数据就绪后需复验三个分区的实际排版、点击动作与滚动表现。阅读指纹模块默认关闭（`DEFAULT_NEGATIVE_MODULES` 为 bookshelf、negative_files、bill、rank），开启后才出现在负一屏列表中。

## W3 与 W4 真机记录（2026-10-20 16:25 至 16:30）

壁纸弹层已验证：`11-wallpaper-dialog.png` 为墨单票据预览，`12-wallpaper-saved.png` 为保存后的界面，落盘文件为 `/sdcard/Pictures/TenRain/tenrain-desk-20261020-162855.png`；`13-wallpaper-share.png` 为系统分享面板，说明 `FileProvider` 授权路径有效。

指纹选书已验证：`04-fp-picker-wait.png` 列出候选书目与搜索框，`05-fp-selected.png` 为选中后回到负一屏的状态。

模块标题行的「壁纸」「设置」「选书」动作在 `02-modules.png`、`10-bill-header.png` 中可见；墨单「壁纸」点击后弹出预览，说明动作从模块按钮到内核 `renderWallpaper` 的链路成立。

内滚待数据就绪后复验：分区高度上限 320dp，超过部分交分区内滚；当前资产数据缺失，无法构造长列表。
