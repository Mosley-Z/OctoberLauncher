# 2026-10-24 UX136 文件宫格与缩略图

本轮最终采用缩略图优先宫格，统一工程门禁通过，S4完成实际横竖屏图片及EPUB样本观察，双机恢复并结束占用；不宣称P4文件页或Picker实机通过。

- 关联：[UX136](../../task/TASK-UX-136-files-grid-thumbnails.md)、[文件模块契约](../design/16-files-module.md)。
- 集成者：Sol root；保留原有工作区修改。未升版、提交、推送或发布。
- 最终源码与资源共708份；相对UX135新增4份、删除0份、改动15份，由集成者核对。
- 最终release `1.0.31 / 51`，2209739字节，SHA-256 `9b4ca3a4f3957b828186de8acd4ffb422d43bb001977fff806ad0c11ea067d39`，见包摘要（本地证据：`_analysis/verify/ux136/apk-manifest.json`）。首方图包`fdd…`不是最终大图包，不沿用其测试数字或固定宫格结果。

## 交付范围

宫格竖屏3列、横屏5列，按宿主空间确定行数与页容量；居中方形缩略图优先填格，11sp单行短名省略视觉文件冗余后缀，过长省略，完整名沿用详情，整格可命中。浏览与Picker共用几何，旋转按原首项所在页重新分页；列表不改。

图片不显示“图”角标。EPUB宫格预览复用core:render共有封面提取及既有原路径/修改时间缓存；EPUB2/3有封面显示封面，无封面或损坏时保留EPUB占位，不把文件变成图片，不改打开语义。

## 工程证据

最终`check :app:assembleDebug :app:assembleRelease`成功，耗时1分6秒；1566任务中71执行、1495 up-to-date。Debug、release最终报告各943例、0失败、0错误、1跳过；同一用例不因两种构建重复计为1886个独立用例，缓存结果与实机结果分别记录。

证据：gate.log（本地证据：`_analysis/verify/ux136/gate.log`）、format.log（本地证据：`_analysis/verify/ux136/format.log`）、测试汇总（本地证据：`_analysis/verify/ux136/test-summary.json`）。

## 实机范围

| 项目 | 实际结果与边界 |
| --- | --- |
| S4实际图片目录 | 经项目方向设置切换普通横屏800×480与竖屏480×800；大图及短单行名可见。横屏`_ui-out/ux136-s4-pictures-final/upright.png`，竖屏`_ui-out/ux136-s4-pictures-portrait/screen-annotated.png` |
| S4六个自建样本 | 宽/长PNG、EPUB2/3、无封面与损坏EPUB分别观察；两类封面成功，异常EPUB保留占位。横屏`_ui-out/ux136-s4-samples-landscape/raw.png`及`upright.png`；竖屏可辨识图案最终图`_ui-out/ux136-s4-samples-portrait/final-raw.png` |
| P4 | 只覆盖安装；内置文件入口被现有配置隐藏，墨台为content最近图片卡且无全部图片入口。未修改配置或新增导航来完成测试，不称文件页设备通过；尺寸几何由JVM覆盖 |
| Picker | 实机未覆盖；共享几何及门禁不等于Picker实机通过 |

## 恢复与结束占用

S4通过项目方向页实证恢复普通模式、横屏90°，见`_ui-out/ux136-s4-direction-confirmed/layout.json`；从测试目录Back至原Pictures后离开，原文件浏览偏好已保存。仅本轮6个样本逐一删除并移除空目录，远端明确返回`REMOVED`，重复测试子目录也按明确路径清理，未扩大删除范围。临时方向已复原；音量测试误走系统的操作已逆向恢复一步，不算项目音量功能通过。

双机均运行最终`9b4…`包并回原桌面，主代理核对`_ui-out/ux136-s4-restored.png`、`_ui-out/ux136-p4-restored.png`，进程均存活，无遗留设备占用。P4全程未改配置。只记录样本类型、路径及技术结果，不转写用户文件名或正文。

本轮实现及有限验证已交付，无待填恢复项。P4文件页与Picker实机仍未覆盖；当前结论也不包含全部尺寸、真实授权撤销、所有EPUB结构或列表全矩阵回归。
