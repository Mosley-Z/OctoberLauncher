# 2026-10-25 UX140四项微调

主入口[UX140](../../task/TASK-UX-140-clock-styles.md)，[最新方案](../../docs/design/40-widget-upgrade-plan.md)、[新包验收](../../docs/acceptance/2026-10-25-ux140-clock-adjustments.md)。用户本轮四项明确调整覆盖旧稿对应细节；未启动UX141–144，139整批仍IN_PROGRESS。

## 实现与现场

master/f025b18及原差异保留，主代理直接修改ClockDate/ClockDesignText/ClockTypeface/ClockDesignFaces/ClockWidget/ClockStyleScreen与ClockTimeTest。D数字圈系数.84→.94，指针参数不变；E09:41=九時/四一、00:41=零時/四一，分钟固定两位逐字，小时沿12/24制、午夜零時；F右栏左齐；A/B/F默认数字textScaleX1.18，日期与主动指定字体1。无需新增配置版本、字体依赖或页面。

统一check+debug/release退出0，各953例0失败0错误1既有跳过；source与产物证据归`_analysis/verify/ux140-adjustment-20261025`。release为2213379字节、SHA256 421c9c0cd831d7c6104a9571b28a004208193ed58efe2c6bc29595eab6a15d48，版本仍1.0.31/51，P4安装摘要已核匹配。

## 验证边界

P4生产绘制探针固定09:41、六款三尺寸共18图，对比前包图看四项变化；D中央60×60区域RGB像素一致，不能只看RGBA差分的空alpha。E零点与自选字体不加宽由规则测试覆盖，未改设备系统时间或偏好。before-after.png为辅助对照，不是完整桌面截图。本轮小幅明确调整采用主代理自检，未新增独立视觉评分。

P4本轮只在release预览页移动焦点，不按确认，不写组件/钟面/字体/方向偏好；原APK私有备份后同签名覆盖安装。S4也被设备列表列为在线，但沿此前延期范围未操作，不记录为离线或已验。原完整交互矩阵与8分独立评分属于前包，不能冒作本轮重新完成。

最终release六款预览均已查看，返回用户原时钟桌面（_ui-out/ux140-adjust-final-home）；原选择/字体/方向未写，临时探针清理，构建与P4释放，无后台工作。文档专职代理只读草拟，主代理审核应用，不宣称原生硬权限隔离。未升版/提交/推送/发布。

## 下一步

等待用户反馈或后续UX141/142分派；143→144按原宿主所有权交接。S4后续按用户安排补验。不得从本次钟面微调扩做其他组件或迁移。
