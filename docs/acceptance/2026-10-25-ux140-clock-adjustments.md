# 2026-10-25 UX140 钟面微调验收

用户本轮四项调整，覆盖旧稿对应细节；主代理在master/f025b18及原差异上增量实现，不启动UX141–144。关联[UX140](../../task/TASK-UX-140-clock-styles.md)、[方案40](../design/40-widget-upgrade-plan.md)、[前轮验收](2026-10-24-ux140-clock-styles.md)。

## 实现与工程证据

- D数字圈半径系数.84→.94（约12%），指针长度/角度/粗细参数不变。
- E以繁体時结尾，分钟固定两位逐字；09:41为九時/四一，00:41为零時/四一；去掉时段和分字，小时沿12/24制且午夜始终零時。
- F年月日星期在右栏局部左对齐；数字宽度只作用A/B/F默认字体的时间数字（textScaleX=1.18），日期恢复1，明确自选字体为1，不新增字体资产/依赖。
- 预览和桌面共用绘制/字宽选择。修改ClockDate、ClockDesignText、ClockTypeface、ClockDesignFaces、ClockWidget、ClockStyleScreen与ClockTimeTest，原ID和配置结构不改。
- 项目JDK17.0.20_12/Android SDK/Gradle8.11.1，版本仍1.0.31/51。证据`_analysis/verify/ux140-adjustment-20261025/`，无升版/提交/推送/发布。

| 产物 | 字节数 | SHA256 |
| --- | --- | --- |
| debug | 7320400 | ac988953a326900be01a6827adf7896d552302b30bd672f2d48b550f2cf0cd2e |
| release | 2213379 | 421c9c0cd831d7c6104a9571b28a004208193ed58efe2c6bc29595eab6a15d48 |

统一check与debug/release构建退出0；两变体各953例、0失败0错误1既有跳过（verify.ps1、format.log、gate.log、summary.json）。更新中文09:41/00:41/00:00/23:59/12小时制规则，增加默认与指定字体字宽规则测试；午夜为规则测试，未修改P4系统时间。

## 绘制与设备

| 触发 | 实际结果 | 证据/边界 |
| --- | --- | --- |
| P4生产绘制探针固定09:41，六款×三尺寸 | 18图已查看常规及受影响紧凑D/窄F；E九時/四一、F左齐、A/B/F加宽可见，无裁切 | android-render及before-after.png；探针白底合成，不是完整桌面截图 |
| 同时刻D调整前后 | 数字圈扩大，中央60×60指针区域像素一致 | summary.json的dialCenterPixelsUnchanged=true；源码指针参数无变化 |
| P4实际安装release | 覆盖安装成功，安装包摘要与上表一致 | installed-release.json，P4L202610130006；保留数据 |
| 本轮release实际预览 | 六款实际绘制已逐图查看，E当前一時/一七、F右栏左齐，数字圈与字宽规则生效；只移动焦点、不确认选择 | release-preview-results.json、release-preview-sheet.png及_ui-out/ux140-adjust-release-preview-* |
| S4 | 设备本次也在线，沿此前延期范围未操作 | 不写离线，也不宣称S4本轮通过 |

设备原包已保存仓库外私有目录。本轮未写应用偏好、未切换组件/字体/方向，沿原入口进行预览；不需要全量回灌旧配置，不覆写用户新数据。前轮完整交互矩阵只作旧版本事实，本轮不冒充全部重新实测。

最终已返回用户本轮原时钟桌面，原组件、钟面选择、方向与壁纸保持；截图`_ui-out/ux140-adjust-final-home`。固定`/data/local/tmp/ux140-adjust-*`探针与图片已清理，构建/P4操作结束并释放；S4未操作。文档由专职代理只读草拟，主代理审核应用，不宣称原生硬隔离。小幅明确修正采用主代理逐图自检，未另做独立评分。
