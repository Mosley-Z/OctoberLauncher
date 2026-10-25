# 阅读桌面方案交接（Codex，2026-10-23）

## 授权与结果

用户要求1/2/3/4/6/7出具体方案、5/8做可行性测试、9做设计稿，另分析本记展开无效。本轮未获业务实施确认，不续开Android改造。

- [任务卡](../../task/TASK-UX-001-reading-desktop-proposal.md)
- [具体方案](../../docs/design/37-reading-desktop-proposal.md)
- [可行性](../../docs/acceptance/2026-10-23-desktop-feasibility.md)
- [可浏览稿](../../design/reading-desktop/index.html)、[说明](../../design/reading-desktop/README.md)

主方案：普通/沉浸模式分开，普通宫格/列表偏好保留；Key获取与录入分开、验证后选模式；透明图标明确标记、名称统一；独立首页/其他页容量与列表左右留白，取消手动间距和误导pageLimit入口；普通组件手势与内部书封/正文仲裁；本记推荐Home宿主白底阅读层覆盖到末排名称下缘，独立全文页后备。

## 证据与边界

两机API30，产品仍1.0.28/48。独立探针正反横屏成功：S4 rotation1/3 800×428、P4 rotation1/3 768×473。没有wm size模拟，没有实现未来左右布局。

P4通过合成PNG/ACTION_VIEW到厂商图库，真实菜单含待机/关机/恢复。产品content URI转交、最终应用和断电尚未测试。S4系统有配置/入口但SET_WALLPAPER没有处理应用；不承诺通用直设。标准WallpaperManager支持/允许true仅为查询，不算写入验证。

本记调用点与实际k6-host1 jar通过源码/javap静态核对，固定height、clip、绘制z序、原触摸区共同缺少扩高链路；没有读真实用户笔记。本轮没有修复Android。

设计为本地HTML/CSS/SVG合成数据，10状态；shoot生成双尺寸竖屏、双横屏、总览及交互截图；主Agent完成三轮视觉检查，无独立评审。浏览器原型本记收展、列表条件显示、抽屉进退验证；不持久化、不接真实账号/导出/应用启动。最终文档门禁以卡片记录为准。

## 现场与恢复

- 基线master/84a035e；原gradle.properties与大量分析/网站未跟踪文件保留。
- 设备恢复auto1/user0/display0/USER_ROTATION_FREE、十雨HOME；临时uxprobe已卸载，P4合成PNG删除。未改壁纸、账户、PIN；未关机；ADB资源释放。
- 无Gradle。Python HTTP server 127.0.0.1:8769、exec session21748用于本轮设计预览；本地HTML可独立打开，服务器停止后文件仍可用。
- `_analysis/verify/desktop-ux/`在.gitignore局部忽略，含设备系统APK、临时签名及技术输出；不入Git。`_ui-out/`沿既有忽略规则。
- Android CLI输出到_analysis时Java写权限失败，默认_ui-out成功；后续按标准脚本默认输出。原生面板截图即使宽高不变，内容可已旋转；用rotation/实际view尺寸与图共同判断。
- 未提交、未推送、未升版/发布。

## 下一步

用户确认待选口径及设计；然后以新的实施卡冻结状态/消费者/迁移和最终验收，再改业务。当前RD017未覆盖项仍按原看板，不能因本轮探针测试关闭旧卡。
