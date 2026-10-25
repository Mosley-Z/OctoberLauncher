# 2026-10-24 UX140 六款时钟验收

关联[UX139](../../task/TASK-UX-139-widget-upgrade-integration.md)、[UX140](../../task/TASK-UX-140-clock-styles.md)、[方案40](../design/40-widget-upgrade-plan.md)与[候选稿](../../design/widgets-upgrade-proposal/widgets-design.png)。本轮仅UX139前置契约和UX140实现，UX141–144未实施；S4由用户明确延期。

## 源码、环境与产物

- 实现/集成/设备负责人：Codex主代理；保留master/f025b18及工作区原有差异，未重置他人修改。八个本轮源码/测试文件为ClockFaces、ClockWidget、ClockStyleScreen、ClockDate、ClockDesignFaces、ClockDesignText、ClockTypeface与ClockTimeTest。
- 工具链：toolchain.path配置的JDK17.0.20_12、Android SDK、Gradle8.11.1，离线构建；P4L202610130006在线，S4本轮未操作。
- 版本仍1.0.31/51，不升版、提交、推送或发布。
- 证据：`_analysis/verify/ux140/`，真实UI证据`_ui-out/ux140-*`；用户正文及全量偏好/备份只留仓库外私有目录。

| 产物 | 字节数 | SHA256 |
| --- | --- | --- |
| app/build/outputs/apk/debug/app-debug.apk | 7320143 | b03e7aaf67256f7c72b137ca9f2acff1270262ce24645fc63fa7d50be0cd3835 |
| app/build/outputs/apk/release/app-release.apk | 2213187 | e7598bad4caac24337ee9e9509f931156cb8088c97207f59aec1c2fbc382f6c8 |

## 工程与设计对照

| 触发 | 预期 | 实际/证据 | 结论 |
| --- | --- | --- | --- |
| widgets格式化后统一check、assembleDebug与assembleRelease | 全部门禁通过，不扩大基线 | verify.ps1、format.log、gate.log；最终退出0，debug/release各952例、0失败0错误、1既有跳过，final-summary.json | 工程通过 |
| 旧五款/未知ID、12/24小时、中午午夜、中文时间和跨年快照 | 旧ID/选择与回退保持，日期和时刻同源 | ClockTimeTest四例，两个变体通过；旧五款预览仍存在 | 规则范围通过 |
| P4 Android原生Canvas从debug APK加载生产绘制代码，固定2026-10-24 09:41 | 六款结构与稿一致，尺寸适配 | android-revised/中六款×336×164、420×96、145×200共18图；clock-design-comparison-final.png为并排辅助图 | 生产绘制范围通过 |
| 独立视觉评审 | 无裁切、重叠，小尺寸信息可读 | 第一轮7/10，发现窄日期和D紧凑盘偏小；提高小字可读性、允许日期换行、D紧凑左盘右日期、F收间距；第二轮8/10通过 | 静态视觉通过 |

探针只调用生产绘制函数，不读取应用偏好，白底由探针合成；18图不是实际桌面截图，也不代表S4验证。系统字体与Windows稿存在字形差异，E使用设备衬线汉字，并未引入Windows楷体字库；六款结构差异和整体层级保留。小尺寸日期仍有精修空间，独立评审未发现阻断阅读的问题。

## P4最终debug包实际闭环

| 操作 | 可观察结果 | 证据 |
| --- | --- | --- |
| 从桌面设置→桌面偏好→桌面组件→钟面样式 | 原入口可达，旧五款和六款新增项均可选 | ux140-final-open-*及ux140-final-reentry |
| 在实际选择器逐款键盘选择A–F并读回 | 六个稳定clockFace值均落盘一致 | ui-selection-results.json与ux140-ui-*；最后重测为最终debug包 |
| 触摸B预览 | clockFace读回divided，当前标记更新 | ux140-final-touch、ux140-touched-b |
| 冷启后进入已选A | 选择保留，已选项自动滚到可视区 | ux140-final-cold、ux140-final-reentry |
| 六款横屏桌面分别冷启 | 真实桌面绘制与对应预览结构一致，日期未重复，透明背景可见原壁纸 | desktop-results.json、ux140-desktop-各ID-landscape-default |
| 通过桌面方向配置切竖屏F | 左侧上下时分、右侧三行日期均可读，应用区在下 | ux140-desktop-time_date-portrait-default |
| 竖屏A指定mono字体与12小时制 | 等宽字体生效，21点显示9点；日期保留 | ux140-desktop-book_page-portrait-mono |

首次批量冷启截图抓到系统启动空窗，已弃用；采集脚本改为先观测十雨自身状态栏就绪，再保存有效截图。上述方向切换为P4应用方向配置切换，非物理旋转测试，也不是S4尺寸模拟。已检查最终有效图，不以截图文件存在代替看图。

## 安装、恢复及release边界

安装前旧release不可run-as，设备无su，allowBackup=false；旧APK已保存仓库外私有临时目录。既有配置导出曾触发但未确认导出文件，不宣称安装前完整数据备份。以同签名debug覆盖安装保留原数据后，在修改任何测试字段前，私有备份完整files/shared_prefs。

测试后只合并恢复appearance.clockFace/clockFont/twelveHour和desktop.widgetId/widgetCycleIds/mode/orientation/firstPageRows/firstPageColumns，读回与基线一致：default/default/false、weread_board/空轮换/NORMAL/LANDSCAPE/4行2列。未覆盖期间产生的其他字段、待办或阅读数据；原壁纸、微信读书形态和应用顺序保留。随后同签名覆盖安装本轮release，未清数据。

P4实际安装release的SHA256与上表完全一致，版本1.0.31/51，证据installed-release.json。release沿原设置入口打开钟面选择器，仅移动焦点、不确认选择，六款预览均实际绘制，见release-preview-results.json、release-preview-sheet.png及`_ui-out/ux140-release-preview-*`；主代理已逐图检查，当前选择保持default。这是release资源/入口/绘制回归，不宣称release下重复全部debug交互矩阵。

最终返回原第二页文件入口位置，原壁纸/横屏/组件配置保留，进程存活；`_ui-out/ux140-final-restored`为当前截图。restore-fields.json记录debug恢复后的原字段匹配；release复核只移动焦点未写偏好。P4的`/data/local/tmp/ux140-*`探针已清理，构建与设备操作均结束，无后台验证或设备占用。

## 未覆盖与交接

- S4离线，用户明确指定后续处理，本轮无S4通过结论。
- 00:00/23:59、跨年、中文分钟及12小时边界由JVM规则测试验证，未修改P4系统时间模拟午夜；真实午夜推进、系统时区切换、全量外部字体与备份导入实机未逐项覆盖。既有string配置编解码未改，新钟面无需配置版本迁移。
- P4常规六款及竖屏F/A抽查通过，不宣称所有方向和每种字体组合全部实机通过；紧凑/窄槽位主要为P4生产绘制探针证据。
- UX139前置字段/消费者/迁移契约已冻结，整批仍依赖UX141–144；卡保持IN_PROGRESS，不能用本轮钟面验收关闭整批。
- 收尾文档检查224份、40迁移、27张RD/FIX卡，2条既有历史WARN、0错误；新UX状态与证据另核。本轮实际修改文件限定git diff --check退出0；扩大到整个docs目录时仍提示原有35流程/旧验收导航等CRLF差异，未为本轮验收改动无关历史文件。
