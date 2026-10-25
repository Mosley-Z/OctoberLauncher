# 2026-10-25 UX140 直接选择钟面与E/F重排验收

主代理在master/f025b18及原差异上增量实现。关联[UX140](../../task/TASK-UX-140-clock-styles.md)、[方案40](../design/40-widget-upgrade-plan.md)、[上一轮微调](2026-10-25-ux140-clock-adjustments.md)。用户最新要求覆盖原“六款不注册独立组件”方案，未启动UX141–144。

## 实现与兼容

- E改为小时、年月日星期、分钟三行；日期不再重复出现在底部，沿用原字号计算。F右栏保持左齐，距线由.05×block增为.09×block。
- 新增clock_book_page/clock_divided/clock_dot_matrix/clock_study_dial/clock_chinese/clock_time_date六个固定样式provider，共用ClockWidget，统一组件库及顺序清单同源；选择保存widgetId，不改旧appearance.clockFace。
- 旧clock仍读取旧clockFace，原clockFont/twelveHour值与消费者保留，未增加配置结构版本或做破坏性迁移。
- 删除ClockStyleScreen及独立路由/注册，删除设置内钟面区、字体/12小时控件与无消费者声明；外层摘要显示当前组件名。使用统计/滑动切组件等仍待UX143，本轮未提前改动。
- 代码范围：widgets绘制/ClockWidget/WidgetCatalog/WidgetsModule、settings的ProjectWidgetsScreen/LauncherPrefsScreen/AppearanceSpecs/SettingsSpecBuilders、core:common对应路由/标签及相关测试。无新依赖、Manifest/权限或应用版本修改。

## 工程与产物

项目JDK17.0.20_12、Android SDK、Gradle8.11.1。统一check+debug/release最终退出0，两变体各953例、0失败0错误、1既有跳过；本批verify.ps1、format.log、gate.log、summary.json在`_analysis/verify/ux140-direct-picker-20261025/`。目录测试覆盖12个provider分类/唯一性与六钟面身份；设置组/路由测试同步去掉已删入口。

| 产物（1.0.31/51） | 字节数 | SHA256 |
| --- | --- | --- |
| debug | 7054574 | c52b90553558181f521904ac2fe5097119700fdcec7c1b1a1c60bfd719770ff1 |
| release | 2211707 | 5a978829a5abc2fef582c46ec67154c3ed04a0a4064a53b1dcb3b070a7479765 |

## P4验收

| 触发 | 实际与证据 | 边界 |
| --- | --- | --- |
| 原时钟实际长按650ms | 打开选择组件，六款全部在时间分组直接列出；ux140-direct-longpress、ux140-direct-picker-list | 使用scripts/android-ui.ps1当前标注触点 |
| 逐款选择A–F | widgetId读回对应六个新ID，旧clockFace/default字体/24小时制不变，桌面样式对应；selection-results.json、_ui-out/ux140-direct-a至f | 实际debug交互；后续最后改动仅设置摘要，绘制/注册/选择逻辑未再变 |
| F进程冷启 | clock_time_date保留，桌面仍为F；ux140-direct-f-cold | 非只验证配置字符串 |
| E/F生产Canvas绘制 | 18张三尺寸图，主代理查看E常规、紧凑、窄与F间距；ef-comparison.png、android-render/ | 固定2026-10-24 09:41的绘制探针，非修改设备时间或S4模拟 |
| 设置钟面区清理 | 最终release实际设置页无钟面/字体/12小时控件，外层和组件库显示时钟名称；ux140-direct-release-absence、ux140-direct-release-settings-shot | 不以源代码删除替代设备结果 |
| release同包入口 | 安装摘要匹配上表，实际长按打开清单六款齐全，取消选择返回原组件；installed-release.json、release-entry-results.json、ux140-direct-release-list | 未在release重复debug六款写入矩阵，防止再次污染已恢复偏好 |

P4为P4L202610130006；S4本轮未操作。未进行新的独立视觉评分；本次是用户已明确指定的局部排版和入口变更，主代理看图与实际交互自检。

## 恢复与交接

旧APK保存仓库外私有目录，同签名debug覆盖保留数据后，在改测试字段前私有备份files/shared_prefs。测试后按字段合并恢复：widgetId=clock、widgetCycleIds=[clock]、clockFace/clockFont=default、twelveHour=false、NORMAL/LANDSCAPE及首页4行2列；读回与本轮技术基线一致，未覆盖无关用户新数据。随后覆盖安装最终release，返回原时钟桌面（ux140-direct-final-home）。

固定/data/local/tmp/ux140-direct-*探针已清理，构建/P4操作已结束并释放。无升版、提交、推送或发布。S4、所有方向/字体组合及真实备份导入未逐项实机覆盖；旧配置编解码未改。UX139整批仍待141–144，不从本轮钟面入口变化推断其余卡完成。
