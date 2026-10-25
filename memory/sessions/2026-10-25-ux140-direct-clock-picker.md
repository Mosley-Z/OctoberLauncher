# 2026-10-25 UX140直接选择与E/F重排

最新[验收](../../docs/acceptance/2026-10-25-ux140-direct-clock-picker.md)、[方案40](../../docs/design/40-widget-upgrade-plan.md)、[任务140](../../task/TASK-UX-140-clock-styles.md)。用户最新要求覆盖旧六款非独立组件/钟面样式页方案；本轮未启动141–144，139整批仍IN_PROGRESS。

主代理在master/f025b18脏区直接实施，保留原修改。E时/年月日周/分三行、原字号计算；F右栏距线增加。六个clock_加book_page/divided/dot_matrix/study_dial/chinese/time_date的provider共用ClockWidget固定face，长按统一组件库直接选择，旧clock与appearance.clockFace仍兼容。删除独立ClockStyle页/路由/注册及设置钟面区、字体/制式控件/无消费者声明，外层摘要改当前组件名称；已有字体/制式值保留。不新增依赖、配置版本、Manifest权限或应用版本。

统一check+debug/release最终通过，各953例0失败0错误1既有跳过。证据`_analysis/verify/ux140-direct-picker-20261025`，release2211707字节、SHA256 5a978829a5abc2fef582c46ec67154c3ed04a0a4064a53b1dcb3b070a7479765、1.0.31/51；P4安装摘要匹配。

P4实际长按→六款各自选择→widgetId读回/桌面对应、F冷启保持通过；旧clockFace/default字体/24小时值未改。18张生产绘制图用于E/F三尺寸自检，不冒作桌面/S4。最后仅改设置摘要显示名称，绘制/注册/选择相关证据仍有效；最终release实际长按清单六款齐全、取消无改选，设置钟面区缺席及当前组件名显示经节点树/看图确认。无新独立视觉评分。

原APK私有备份；同签名debug覆盖后在测试修改前私有备份完整files/shared_prefs。选择测试后按字段合并恢复为clock/[clock]、clockFace/clockFont=default、twelveHour=false、NORMAL/LANDSCAPE、4行2列，读回与基线一致。最终release返回原时钟桌面，探针清理、构建与P4释放，无后台工作。S4本轮未操作；不宣称所有方向/字体/真实备份导入已实机覆盖。

文档代理只读草拟，主代理审核应用，不宣称原生硬隔离。未升版/提交/推送/发布。下一步等待用户反馈或141/142分派，143→144依原宿主交接；后续不能恢复已被本轮明确删除的钟面设置入口。
