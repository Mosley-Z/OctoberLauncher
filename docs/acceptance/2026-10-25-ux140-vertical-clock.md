# 2026-10-25 UX140 E中文时刻竖排验收

关联[任务140](../../task/TASK-UX-140-clock-styles.md)、[方案40](../design/40-widget-upgrade-plan.md)。本轮只调整E绘制；此前入口、A–D/F与设置清退的验收仍见[前轮记录](2026-10-25-ux140-direct-clock-picker.md)。

小时、分钟分别逐字从上到下，字形不旋转；日期横排及其字号、居中位置保持。上下区域段距随高度变化，时分共用字号，矮组件根据字数和可用高度同步缩小。修改仅为ClockDesignFaces、ClockDesignText，无配置、依赖、权限或版本变化。

## 工程结果

项目工具链JDK17.0.20_12、Gradle8.11.1；统一check、assembleDebug、assembleRelease退出0。Debug/Release各953例、0失败、0错误、1既有跳过。证据位于`_analysis/verify/ux140-vertical-20261025/`的verify.ps1、format.log、gate.log及summary.json。

| 1.0.31/51产物 | 字节数 | SHA256 |
| --- | --- | --- |
| debug | 7385019 | 5af53ee47dc8ab2302e25e1897474fae4781d7951cf67646ca25f8e6670dce89 |
| release | 2211831 | 386e25d8f1c5e485665be2850c6a95bf475374f1e97d48d2c561bca1c54478c5 |

## 绘制与设备

- Android生产Canvas探针：336×164、420×96、145×200、420×450，分别绘制09:41、00:41、23:59，共12图。主代理查看vertical-size-matrix.png，确认竖排、日期横排及无重叠裁切；短槽位长小时文本会缩小，本结论不等同于所有尺寸同等易读。固定日期2026-10-24，未改设备时间。
- P4（P4L202610130006）debug实际长按原时钟→选择中文时刻；widgetId读回clock_chinese，桌面时分竖排、日期横排居中。截图`_ui-out/ux140-vertical-e/screen-annotated.png`，正向副本p4-e-upright.png。未做新的独立视觉评分，按用户明确局部要求自检。
- 测试前仓库外私有备份APK及数据；测试后仅合并恢复本轮字段，读回与baseline-fields.json一致：clock/[clock]、default字体/旧钟面、24小时制、NORMAL/LANDSCAPE、首页4×2。未覆写无关数据。
- 最终release覆盖安装成功，installed-release.json摘要匹配；实际启动返回原时钟桌面，证据`_ui-out/ux140-vertical-final-home/`。E实际交互证据来自debug，release只验安装摘要与启动，不混称release全矩阵实测。

固定设备探针已清理，构建与P4占用释放。S4按用户要求延期；UX141–144未实施。保留master/f025b18及原差异，无升版、提交、推送或发布。
