# 当前架构

返回[架构导航](README.md)或[文档导航](../README.md)。本页描述工作区现存源码的职责和边界；是否已经通过构建、设备验证或发布，分别查任务卡与验收记录。

## 模块与依赖

十雨是 Kotlin 与原生 View 构建的 Android Launcher。`app` 是组装根，负责宿主生命周期、导航、锁屏、壁纸和全局服务；功能模块通过公共契约协作，不横向依赖其他 feature。实际模块清单以 [settings.gradle.kts](../../settings.gradle.kts) 为准。

| 模块 | 职责 |
| --- | --- |
| core:common | 配置模型、页面目标、内容和宿主服务契约 |
| core:data | 配置持久化与兼容迁移、应用目录、主题、壁纸、图标及备份 |
| core:design | 原生页面、设计令牌与通用墨水屏控件 |
| core:device、core:render | 设备能力、权限、刷新策略与图像缓存 |
| core:weread-sync | 微信读书同步与本地数据存储 |
| core:widget-api | 桌面组件生命周期、绘制、交互与宿主接口 |
| feature:home、feature:widgets | 桌面分页、布局与手势；组件内容与交互 |
| feature:settings | 项目设置与系统设置入口 |
| feature:tray | 既有状态栏工厂，不提供私有通知下拉面板 |
| feature:content | 书架、微信读书、天气、使用统计等内容聚合及页面 |
| feature:files、feature:reader | 文件管理与按格式选择的阅读器 |
| feature:weread-app | 微读墨记原生页面及其内核适配 |
| service | 通知角标监听、悬浮状态栏、刷新及音量键翻转的系统服务适配 |

宿主服务组装从 [OctoberRainApp](../../app/src/main/java/com/shuiyu/octoberrain/OctoberRainApp.kt) 定位，公共接口从 [Contracts](../../core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt) 定位。页面和组件分别由注册入口接入宿主；实现细节沿对应模块查找。

## 状态与数据边界

桌面布局、外观、锁屏和集成配置由 `ConfigStore` 提供统一状态；配置存储、迁移和备份归 `core:data`。设置负责编辑配置，实际桌面、壁纸、状态栏等消费者订阅状态。运行时的临时整理状态不替代用户保存的桌面模式。通知角标、悬浮状态栏与刷新等共享能力独立于已退役的私有下拉菜单，系统原通知栏仍由 Android 管理。

微信读书由独立同步及本地存储提供数据，内容聚合层和组件通过宿主契约读取。微读墨记自身偏好由其适配层管理，本月样式在字段缺失时采用热力图，已有明确选择保持原值。应用目录中的普通第三方应用与内置内容入口分开管理。

沉浸阅读使用 [WereadImmersiveSync](../../core/weread-sync/src/main/java/com/shuiyu/octoberrain/core/wereadsync/WereadImmersiveSync.kt) 的专用批次：手动刷新仅对最近阅读的10本候选书补取进度、时长及候选划线；自动刷新复用进度缓存并跳过划线批次，保持轻量。[HostWereadSync](../../app/src/main/java/com/shuiyu/octoberrain/HostWereadSync.kt) 为全量同步及卡片刷新提供统一在途门，从持久成功时间恢复沉浸刷新状态；自动刷新同日成功后间隔至少10分钟，跨日允许更新，尝试间另有60秒冷却以限制失败重试。翻书只切换本地数据，不触发联网；后台不轮询，自动刷新由可见生命周期及前台跨日触发。

[PeriodReadings](../../core/common/src/main/java/com/shuiyu/octoberrain/core/common/PeriodReadings.kt) 将周期稀疏日桶转换为共享日数据；仅在存在 `readTimes`、获取时间有效、响应 `baseTime` 与请求周期起点匹配且 `totalReadTime` 为非负合法值时，把该周期截至今天与获取日的省略日解释为0。周期缺失或不完整时，未提供的日期保持未知。[ReadingStreak](../../core/common/src/main/java/com/shuiyu/octoberrain/core/common/ReadingStreak.kt) 以每天至少60秒计有效阅读；今天已知但未达标时从昨日起算，遇到缺失日返回未知。同步层按需补齐连续天数所需的历史月份；`feature:widgets` 在后台准备阶段通过 [ImmersiveStatsReader](../../feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/kernel/ImmersiveStatsReader.kt) 读取共享缓存，绘制不联网，也不将缺少周期等同于零。

源码不再提供 KOReader 专属统计、热力图页面或内置特殊入口；旧配置中的已退役引用在兼容边界清理。设备上安装的 KOReader 仍属于普通第三方应用，外部应用及其数据不归此移除范围。

文件阅读器与文件授权分别由 `feature:reader`、`feature:files` 承担。PDF 隔离进程不初始化桌面配置及内容服务；格式支持和实际保真范围以对应阅读器验收为准。

## 桌面展示与交互

普通桌面和沉浸桌面共用宿主配置与组件契约。普通桌面的有组件页复用首页几何，横屏采用组件与应用分区，无组件的后续应用页使用完整内容宽度。[PageWidgetConfig](../../core/common/src/main/java/com/shuiyu/octoberrain/core/common/PageWidgetConfig.kt) 与 [DesktopConfig.pageWidgets](../../core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt) 提供逐页配置投影和编辑：首页保留旧字段，其他页组件按固定页位独立保存，应用流动不改变组件归属；新页首次编辑继承首页选项，随后独立保存。组件关闭释放本页容量，应用按可变容量重新分页并向前回填。

首页宫格行列按横竖方向分别保存 nullable 覆盖：`null` 兼容旧共享显式值，`0` 表示自动；共有解析函数 `firstGridRows`、`firstGridColumns` 结合设备 model 决定默认值，自动结果不写回显式设置。按行×列计，P4竖屏为1×3、横屏为3×2，S4竖屏为2×3、横屏为3×2；未知机型沿用原默认（竖屏2×3、横屏4×2）。明确设置仍约束实际布局，列表模式保留自身配置。[DesktopMetrics](../../feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/DesktopMetrics.kt) 统一计算逐页几何与容量，[HomeDesktopLayoutProvider](../../feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/HomeDesktopLayoutProvider.kt) 经公共布局契约输出携带每页行列及条目顺序的快照，设置通过同一快照和预计算入口复用真实分页。

普通横屏有组件页应用区的绘制与命中使用同一几何来源；逐页组件设置与共享的首页几何参数分别管理。

沉浸模式中的封面轮播保留组件手势；封面外的桌面导航与组件点击分工。长按使用桌面菜单，整理以临时普通布局呈现。B版布局由 [ImmersiveHome](../../feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/ImmersiveHome.kt) 承担宿主时间与应用栏，[HomeView](../../feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/HomeView.kt) 将书籍区边界交给组件；组件据同一保留区安排书籍与统计，避开宿主时间及应用栏。[WidgetView](../../core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt) 携带宿主沉浸方向，避免组件按局部宽高误判方向；`BaseWidgetView.pageIndex` 保持所属页位，用于回写本页设置。

宿主按页缓存组件实例并传递可见性，离屏暂停计时；图片组件释放位图，本书组件停止封面解码请求并清理缓存。图片来源及轮播状态分页面保存，账号、待办等业务数据仍共用。空白区域透出壁纸；桌面白压层的绘制及设置入口已退役，沉浸态不应用变暗效果，普通模式的变暗偏好保持独立。

桌面壁纸由宿主底层统一覆盖全屏，使用完整源图按屏幕比例拉伸；横屏转向独立保存。主题及备份携带相应外观状态。锁屏有自己的壁纸呈现边界，不沿用桌面拉伸规则。

物理盖板补偿由 `core:device` 的 [DisplaySafeArea](../../core/device/src/main/java/com/shuiyu/octoberrain/core/device/DisplaySafeArea.kt) 提供：Eink P4取12px，竖屏映射左右、横屏映射上下，其余机型不施加此补偿。该职责限定为布局坐标映射，物理下拉及盖板边缘可触达性属于设备验收边界。

微信读书桌面组件在竖屏、沉浸及本书使用原路径；普通横屏的本周、本月、本记与待办使用独立左列，依次承载切换矢量、竖排标题、无框刷新及逐字竖排更新时间。标题/切换与刷新触点独立，正文和日期基线保持。周/月更新时间来自周期数据获取时刻，本记和待办来自各自后台取出或快照装配时刻，不在绘制时改写，未知显示`--:--`。内容及动作归组件，模式与方向边界由宿主管理。

桌面本记的临时阅读层由桌面宿主承载，组件提供当前笔记及换条能力。竖屏沿用原阅读层；横屏保留当前C式来源、正文锚点、字号及行距，溢出时通过可见句尾入口向右借用应用区，原文按新宽度重排，长文竖向滚动。阅读层拦截全部触摸并管理关闭与恢复，正式独立笔记管理页保持自身职责。横屏切换箭头沿用原矢量符号，与竖屏保持一致。

普通横屏本记展开纸面覆盖到首页完整右边界，包含两列应用；纸面范围与正文右留白分别管理。上一条/换一条的视觉样式与实际触区分别管理，保持原交互能力。

横屏周/月图表按头部与实际统计行之外的剩余高度布局，月历使用真实4/5/6行；统计区靠底部安全边界，不以整卡缩字利用留白。极矮空间优先保留头部和统计，容纳不下的日期/图表乃至总量行可不显示，不新增滚动或模式，也不保证任意高度都能显示全部内容。

## 设置归属

项目新增设置统一由 `feature:settings` 提供八类入口：桌面与布局、外观与个性化、桌面组件、状态栏与通知、墨水屏刷新、十雨锁屏与安全、内容/权限/备份、关于十雨。“状态栏与通知”沿用既有设置页与路由，不新增页面；`feature:tray` 提供既有状态栏工厂，设置路由由设置模块统一提供。

系统原有设置入口仍调用系统能力，不因项目设置重组而改写系统分类。微读墨记保留自身设置入口；项目桌面组件的布局归桌面宿主，内容偏好归微读适配层。

内置文件入口新装默认显示，显隐由既有布局设置中的隐藏管理承载，文件及安装应用的开关统一为开显示、关隐藏；内置入口与安装应用身份和存储边界独立。配置兼容迁移一次恢复旧配置中的文件入口，后续用户重新隐藏的选择保留，其他入口及布局不受影响；具体契约见[文件模块](../design/16-files-module.md)。

## 文档与代理权限

架构文档记录现存职责和边界，设计描述目标，任务卡记录工作与证据，Git 保存变更历史。文档导航由 [docs/README](../README.md) 维护。

代码实现代理只提交文档建议；主代理采纳后向专职文档代理分配明确文件。独立代理入口 [run-project-agent.ps1](../../scripts/run-project-agent.ps1) 显式加载 [agent-permissions.toml](../../scripts/agent-permissions.toml)，不将角色权限作为自动项目配置加载到主会话。代码角色可写源码，文档、任务、记忆、根文档和策略保持只读；文档角色可写授权文档位置，源码和策略保持只读。文件授权范围之外仍须遵守本次分配的文件所有权。

原生 spawn 继承父会话权限，不具备上述独立角色边界，本轮仅用于只读检查或提供暂存建议，由主代理审核应用。独立角色的允许写入与越权拒绝证据见 [UX121](../../task/TASK-UX-121-documentation-governance.md)，不由代理名称、提示词或插件声明推断隔离。完整协作规则见[开发流程](../design/35-development-workflow.md)。
