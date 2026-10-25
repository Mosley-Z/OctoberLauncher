# UX116 沉浸绘制交接

- 执行者：Codex immersive_polish_painter；工作树 master / D:\DSHStation\OctoberLauncher；保留接手时全部差异。
- 文件范围：WereadImmersivePainter.kt、ImmersiveBookText.kt、ImmersiveBookTextTest.kt；共享契约、宿主、轮播与触点由 root 集成。
- 依赖：ImmersiveReadingStats(todayMinutes, finishedBooks, streakDays) 三可空统计；缺失为 —，零为有效值，不采用书架或轮播书数。
- 最终入口/位置/归属：原沉浸首页；书封略小上移，标题/作者/4px进度/紧凑进度时长行/三个浅灰独立统计卡；组件仅绘制书封和信息，宿主显示用户壁纸、时钟及常用应用。
- 状态与消费者：只读原书 title/author/progressPercent/readingSeconds 与统计DTO；短标题仅显示层剥离含套装/文集/版本标记的括号组，源数据不变。
- 迁移：无配置或用户数据迁移；无动画，无全画布白底。
- 证据：已查看用户参考与 _ui-out/20261023-145351-887-SA4B03CN2608081096X/screen.png 实际基线；新增标题与时长纯函数回归用例。
- 验证边界：本执行者未运行 Gradle/格式/ADB；root 统一门禁与双机竖横屏实际显示、书封点击和空统计验收。
- 后台进程/设备占用/恢复：无；未提交/合并/发布。
- 下一步：root 接入统计、对称书堆及同源触点，统一格式/编译/设备截图审查。
