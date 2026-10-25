# TASK-UX-120 桌面整改集成与授权边界

- 状态：READY_FOR_REVIEW
- 优先级/阶段：全系列实现与本轮统一验证已交付；未覆盖项明确保留
- 执行者/集成者：Sol root集成；代码代理提供暂存源码；独立文档代理负责已采纳文档
- 分支/工作树/源码基线：master / D:\DSHStation\OctoberLauncher / f025b18＋大量既有未提交改动；执行前重新核实
- 依赖：无；本卡负责协调UX121–132，不与分卡形成依赖环
- READY条件：用户已明确授权Sol执行；UX121–132实现齐备后统一测试
- 设计依据：[周/月C稿](../design/ux-corrections-proposal/index.html)、[本记/待办修订稿](../design/ux-corrections-proposal/revision-2.html)、[第二轮方案与完整设置树](../design/ux-corrections-proposal/revision-2.md)
- 允许修改：集成任务卡、看板、memory、公共契约/配置/注册与最终门禁；按分卡固定文件所有权
- 共享入口/禁止范围：版本、Manifest无需求不动；不提交推送发布；不改Android系统设置；保留所有既有差异

## 目标与用户闭环

用户切换GPT-6.1 Sol后可直接按卡接手，不必重读整段聊天猜授权。第2/3/9/10、横屏边距和KOReader删除均已确认并纳入UX127–132，用户已授权全部实施，无剩余方案审批。

## 需求与证据回读

1. 最终入口：UX121文档入口、UX122沉浸导航、UX123/124普通横屏分页、UX125原壁纸设置、UX126原本月呈现入口。
2. 控件位置：遵循分卡六行回读；横屏2列×4行；图片角度独立于桌面方向。
3. 显示归属：竖屏现有组件样式保留；本书轮播视觉不动；系统原有设置结构/行为全部保留。
4. 字段/消费者：UX125配置/主题/备份由主代理串行；UX126缺省值双端同源；其他分卡不增加持久字段。
5. 迁移：保留显式用户选择与竖屏存值；不得为新默认清全量偏好。沉浸有效罩层由UX128负责；KOReader退役迁移由UX132负责。
6. 验收触发：分卡测试＋最终check/debug/release＋S4/P4实际方向/操作；记录最终APK与源码匹配。

## 分工、顺序与文件所有权

| 顺序 | 卡 | 主要所有权/依赖 |
| --- | --- | --- |
| 先行 | [UX121](TASK-UX-121-documentation-governance.md) | 主代理核实权限与文档清单，再派独立文档代理；未落实硬隔离不启用可写代码子代理 |
| 可并行独立面 | [UX122](TASK-UX-122-immersive-negative-gesture.md) | HomeView/ImmersiveHome手势；与布局绘制变更先划文件范围 |
| 串行布局1 | [UX123](TASK-UX-123-landscape-full-width-pages.md) | DesktopMetrics非首页；完成后交接UX124 |
| 串行布局2 | [UX124](TASK-UX-124-landscape-home-eight-slots.md) | 同一DesktopMetrics首页；不得和UX123同时写 |
| 独立数据面 | [UX125](TASK-UX-125-wallpaper-stretch-rotation.md) | 壁纸加载/缓存；核心配置和主题由主代理先行串接 |
| 独立默认面 | [UX126](TASK-UX-126-month-heatmap-default.md) | WereadAppPrefs与双端消费者；core/common变更交主代理 |
| 手势后续 | [UX127](TASK-UX-127-immersive-desktop-menu.md) | UX122交接HomeView后接菜单与整理呈现 |
| 壁纸后续 | [UX128](TASK-UX-128-immersive-transparent-wallpaper.md) | UX125交接WallpaperView后接沉浸透明 |
| 设置收口 | [UX129](TASK-UX-129-project-settings-regroup.md) | UX132删KOReader后重组；UX125/128壁纸项契约先定 |
| 横屏组件 | [UX130](TASK-UX-130-landscape-weread-widgets.md) | UX131内容矩形交付后，改横屏四形态、保留按钮 |
| 布局后续 | [UX131](TASK-UX-131-landscape-status-side-spacing.md) | UX123→124→131串行改DesktopMetrics |
| 专属集成删除 | [UX132](TASK-UX-132-remove-koreader-integration.md) | 数据仓库/页面/入口清理，主代理串行迁移公共配置/契约 |

主代理唯一维护task/README、STATUS、公共契约/配置/主题与最终文档采纳决定；代码代理只提交代码与文档建议。专职文档代理按主代理批准文件清单写docs。若原生子代理继承权限无法阻止写文档，应只读调查并由主代理应用补丁；不得说已完成硬保护。

唯一负责人执行共享Gradle/格式化；同一serial只能一操作者。S4当前原竖屏沉浸、P4原普通横屏，但执行前重查，不按历史设备状态直接操作。

## 验证与交付

- 文档：检查导航、链接与新UX卡字段/索引/依赖；独立角色验证允许写入与越权拒绝。
- 代码：全部实现齐备后，由主代理按35流程统一完成check、assembleDebug、assembleRelease；修复失败而非扩大baseline。
- 同最终包验证S4沉浸手势、P4正反横屏布局/容量、两机全图壁纸及本月默认迁移；新能力未测不得写DONE。
- UX121纯文档验证独立，权限能力未满足保持明确未完成。
- 新增UX127–132按下表文件依赖实施；竖屏原样、系统设置原样、外部KOReader数据不动。

## 执行与交接

- 已改文件/接口决定：UX121–132实现全部集成；共享配置/契约/主题/迁移由root串行。用户2026-10-24取消标题长按与临时隐藏，相关菜单、状态、计时及分发已删除；标题点按/刷新和沉浸桌面菜单保留。
- 验证命令及结果/证据：实现齐备后统一check/debug/release通过；最终删除后门禁23秒，两个变体各922项、0失败、0错误、1跳过（相同用例不重复计数）。[本批验收](../docs/acceptance/2026-10-23-ux120-corrections.md)为结果入口。
- 源码/包：696份摘要，最终release SHA256 `1c53d3ca37f4ecd78b34153388d4cac1eb1bd872763fc8e598fe904a7d02572c`，2214487字节，版本1.0.31/51；源码清单及APK清单位于_analysis/verify/ux120。
- 设备：f683包完成严格对稿后主体矩阵（四形态、按钮、后页全宽、2×4/末格、90/270、四壁纸转向及冷启、八分类）；最终1c53包重新验证删除功能与受影响按钮、沉浸菜单和冷启。各包报告独立，不能合称最终包全部矩阵重测。
- 用户反馈：S4原壁纸/原竖屏沉浸现场肉眼“清晰可见”。
- 未覆盖/阻塞/解除条件：无未完成实现或代码阻塞；有内容待办完成/日期、长短混合笔记、全部主题罩层/备份导入、设备新用户缺省及所有布局组合等未穷举，分卡与验收分别明确。READY_FOR_REVIEW不宣称全场景产品验收。
- 后台进程/设备占用/恢复情况：Gradle结束；S4原书/原壁纸/竖屏沉浸HOME、P4本月/90°/1×3/纯白/图片0°均恢复释放，测试fixture已删。
- 下一步：本轮实现与统一验证已交付，接手仅按未覆盖边界补验或处理新反馈；无需重做方案审批。远程workspace requirements报错复盘见[memory记录](../memory/sessions/2026-10-23-ux120-runtime-error-review.md)，可疑自动配置已隔离，根因未确认。
- 提交/合并/发布：未执行，无升版；保留master/f025b18全部既有差异。
