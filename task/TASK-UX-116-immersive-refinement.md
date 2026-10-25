# TASK-UX-116 沉浸桌面细节精修

- 状态：READY_FOR_REVIEW
- 执行者/集成者：Codex root；GPT-6.1 Sol/low分别处理本书排版和缓存统计；root唯一Gradle/ADB负责人。
- 分支/工作树/源码基线：master / D:\DSHStation\OctoberLauncher / f025b18及既有未提交差异，保留全部原工作。
- 依赖/READY条件：用户已批准最新参考图整改，明确删除十雨字样、背景跟随用户、自定义图标保留。
- 允许修改/共享入口/禁止范围：ImmersiveHome/HomeView沉浸分支、WereadImmersivePainter/BOOK沉浸桥接及统计缓存读取、必要测试/帮助/任务验收；不改普通桌面布局、用户书名数据、版本/Manifest/依赖，不提交发布。
- 设计依据：用户2026-10-23参考图与本次确认；[开发流程](../docs/design/35-development-workflow.md)。

## 需求与证据回读

1. 最终入口：原沉浸模式及抽屉，进入方式不迁移。
2. 控件位置：居中缩小时间/日期；书封主体上移、对称层叠；主书名/版次/作者分层；独立进度与阅读时长；三块统计卡；无粗线底栏。
3. 显示归属：沉浸共用桌面背景和自定义应用图标，删除十雨品牌字样；不强制白底或替换线稿图标。
4. 字段/消费者：原book缓存/选择状态＋已存在阅读统计缓存，数据后台读取；绘制和触点同源，缺失指标显示—而非替代数据。
5. 迁移：无配置/数据迁移，精简书名仅展示处理，原始书名/普通布局不变。
6. 验收触发：S4真实长书名/大数值问题对应合成夹具、双机竖横屏截图、相邻书封选择/抽屉、壁纸与自定义图标保留。

## 执行与交接

- 已改文件：ImmersiveHome/HomeView、WereadImmersivePainter、ImmersiveBookText及测试、WereadBoardCarousel/Frame/Widget及测试、ImmersiveReadingStats与Kernel桥接、ImmersiveStatsReader及测试、离线帮助。绘制分工见[交接](UX116-painter-notes.md)，最终集成以本卡和验收为准。
- 实际命令及结果/证据：最终check :app:assembleDebug :app:assembleRelease --offline --console=plain退出0；每变体914例、0失败、1既有Ignore。[本轮验收](../docs/acceptance/2026-10-23-immersive-refinement.md)记录APK摘要及S4竖屏切书、P4反向横屏/抽屉证据。
- 未覆盖/阻塞/解除条件：S4恢复后断开，未补拍恢复后真实数据页面；最终release合成截图保留原壁纸/图标。统计缺失/小书架等边界为JVM证据，实体矩阵未穷举；真实LAN/Key与休眠壁纸仍后置。
- 后台进程/设备占用/恢复情况：两机本轮原字段与原weread目录恢复断言通过，最终release实际摘要均f8db1e34…fdf36；已启动桌面，本轮构建/设备操作结束。
- 下一步/接收方：用户查看S4最终release截图；如需恢复后真实数据截图，S4重新连接后用android-ui补采，无需重复已通过门禁。
- 提交/合并/发布：未执行。
