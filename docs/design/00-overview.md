# 桌面应用开发方案

> 2026-10-22 整理说明：本文保留早期设计/交接语境。当前协作、Git、构建和验收流程以 [35开发流程](35-development-workflow.md) 为准；版本、模块数、测试数和设备状态须从源码/当轮证据复查。

对象：`tomo-launcher-0.5.0.apk`（`com.astraabove.tomo` 0.5.0，versionCode 13）。目标是开发一个功能对等、独立构建、便于长期扩展、覆盖更多亚安卓机型的桌面应用。

## 一、文档结构

| 文档 | 内容 |
| --- | --- |
| [01-feature-inventory.md](01-feature-inventory.md) | 十二个功能域的全量清单与上游缺失能力 |
| [02-current-architecture.md](02-current-architecture.md) | 上游的分层、装配、数据、渲染与结构性问题 |
| [03-target-architecture.md](03-target-architecture.md) | 新工程的模块、组件协议、适配层、数据层、渲染层与界面层 |
| [04-identity-removal.md](04-identity-removal.md) | 与上游隔离的逐项清单与差异化边界 |
| [05-device-adaptation.md](05-device-adaptation.md) | 多机型适配的探测、断点、令牌与机型矩阵 |
| [06-development-roadmap.md](06-development-roadmap.md) | 九个阶段的目标、工期、验证与决策点 |
| [07-upstream-code-map.md](07-upstream-code-map.md) | 上游类与文件索引，供移植对照 |
| [08-engineering-conventions.md](08-engineering-conventions.md) | 模块内分层、命名、风格、测试、提交与发布规范 |
| [09-M0骨架记录.md](../acceptance/09-M0骨架记录.md) | M0 骨架的工程结构、工具链复用、版本偏差、验证数据与遗留项 |
| [10-dev-handoff.md](10-dev-handoff.md) | 模块边界、公共契约、配置模型、组件协议与协作规则 |
| [11-552x768-adaptation.md](11-552x768-adaptation.md) | 目标尺寸的断点、行数算法、组件槽、验证矩阵与机型扩展 |
| [12-验收记录.md](../acceptance/12-验收记录.md) | 0.2.0 的构建、真机验收、截图与已知边界 |
| [13-inkOS对照评估.md](13-inkOS对照评估.md) | inkOS 的功能与实现方式、逐域对照、可吸收的功能与现有设计的可优化点 |
| [14-本批实施记录.md](../acceptance/14-本批实施记录.md) | 六项优先改造、三项排期功能、七项设计优化与负一屏的落地内容、验证与已知限制 |

逆向过程产物在 `_analysis\`：反编译源码 `jadx-out\sources\com\astraabove\tomo\`、资源 `jadx-out\resources\`、三份分工报告在 `reports\`（`ui.md` 610 行、`integrations.md` 416 行、`data-settings.md` 692 行）、REA 检查产物在 `package.json`、`manifest.xml`、`classes.txt`。

## 二、上游是什么

面向 480×800 墨水屏阅读器的极简桌面。核心是一屏一组件、两组宫格或列表分页、无动画、白底黑字、整屏闪烁刷新。功能面覆盖：应用区与整理、十三种阅读与信息组件、状态栏与快捷托盘、通知监听、无障碍悬浮层、前光控制、开机密码、书架与合集、文件管理、微信读书与 KOReader 接入、天气与使用统计、主题与图标包、在线更新。

代码规模 45348 行，253 个手写类，十五个 Activity，无依赖注入、无状态容器、无数据库、无动画。所有尺寸以像素围绕 800 高度写死，屏幕准入只接受 480×800±2，其余机型启动即退回原桌面并退出。

## 三、方案的三条主线

**结构重写。** 单 Activity 装配改为单 Activity 加 Fragment 与 ViewModel；25 参数构造函数改为 Hilt 注入；十二个预置组件容器加 `when` 分支改为组件协议加 KSP 注册表；十五个散落偏好文件改为按域分组的 DataStore 加 Room 缓存；逐行手写的设置页改为 `SettingsSpec` 数据驱动。界面仍然用 View 体系，保证墨水屏上的绘制可控。

**身份隔离。** 包名、类名、资源名前缀、字符串、域名、UA、无障碍配置、FileProvider authorities、尺寸常量、彩蛋、版本串全部替换；不沿用上游的类名体系与命名习惯；构建期开启 R8 与资源压缩，移除 Kotlin 元数据与调试探针；与上游产物对比不得有共享类与资源。逐项清单见 04。

**能力扩展。** 屏幕准入改为能力判定，任意分辨率可运行；尺寸由像素改为 dp 令牌加三档断点；组件区改为可插拔协议，新组件不触碰宿主；设置项以节点声明注册；数据源以接口抽象，天气与阅读接入可替换；补上上游缺失的媒体控制、定时全刷、多语言、配置备份、通知实时性。

## 四、当前进度

0.2.0 已实现桌面、组件、托盘、设置、内容与系统服务的完整链路，构建、装机与真机验收通过，验收记录见 [12-验收记录.md](../acceptance/12-验收记录.md)。

工程为单 Activity 加自绘视图，未引入 Fragment、ViewModel、Hilt、Room 与 DataStore。原方案中的这些组件用于多人协作下的状态管理与持久化，实际实现以 `AppServices` 服务定位器、StateFlow 与单键 JSON 配置达到相同目标，模块边界与组件协议保持不变，后续如需替换不影响功能模块。

界面与手势：宫格与列表两种形态、滑动与音量键翻页、页码、长按整理与菜单、13 类组件与组件选择页、状态栏、快捷托盘与悬浮层服务。

内容与系统：书架扫描与 EPUB 封面、文件管理、微信读书与 KOReader 数据源、天气与使用统计、通知监听、HOME 角色申请、前光、整屏与局部刷新调度、四位 PIN 锁屏、主题、字体与壁纸、在线更新。

适配：480×800 与 552×768 两档密度已在真机与显示覆写环境验证，断点、高度档与系统栏插入区集中在设备层与设计令牌层，新增机型通常只需补充墨水屏关键字。

文档 10 至 12 记录了协作接口、适配算法与验收结论。后续扩展在现有协议下进行：新增组件实现 `WidgetProvider`，新增页面实现 `Screen` 与 `ScreenProvider`，新增数据源实现 `ContentSource`。

## 五、工期与人力

单人全职约 22 周，三人并行约 11 周。九阶段的产出与验收标准见 06。三条可并行的主线是界面与手势（M1、M4）、组件与内容（M2、M6）、系统能力（M3、M5），三者只在 `core` 与 `app` 汇合。
