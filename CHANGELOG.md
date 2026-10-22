## 1.0.31

- 修复更换图标时选择损坏图片会破坏原有自定义图标的问题，失败选择保留旧图。
- 继续使用内部测试签名，无正式发布；后置扩展保持搁置。

## 1.0.30

- 修复部分PPTX扩展元数据中的同名节点被误判为页面，导致预算内文档无法打开的问题。
- 补充双机真实授权、同源、性能、复杂文档及原片光学分析；性能未达目标项保留实际结果。
- 沿用内部测试签名，不作正式发布；后置扩展继续搁置。

## 1.0.29

- 文件入口统一接入TXT/PDF/Markdown/EPUB/ZIP、DOCX双模式、CSV/XLSX/XLS/ODS表格及PPTX阅读，按各格式能力显示定位、书签和搜索。
- 统一全文计数、上下个、取消与命中高亮；修复旧CSV位置/编码迁移、PPTX跨样式文字漏搜及外部文档重复打开失效。
- 本轮补齐第三方文档来源身份验证与双机授权、性能和状态回归；实际验收范围见任务卡及验收记录。
- 继续使用内部测试签名；后置格式扩展搁置，不作正式发布。

## 1.0.28

- 微读墨记顶部大标签由四项收敛为三项「本周」「设置」「待办」，原「实验室」页签及其容器整体移除（任务 2.8）。
- 原「实验室-续航优化」整段删除；「锁屏密码」并入「设置」页签，与「初始化」「自定义」并列为三个子标签，页面文案改为按十雨桌面共享锁的口径说明（任务 2.8）。
- 删除微信读书「统计」页与「阅读月历」页：移除桌面卡片正文热区跳转、书架页的「统计」按钮、月历路由与相关绘制代码，微信读书内置页页签剩余「总览」「笔记」「账单」「排行」（任务 2.8）。
- 锁屏校验期间熄屏或改密后旧结果不再弹出「密码错误」：作废结果以静默状态返回，清空输入回到等待输入（任务 2.8）。
- 锁定相关临时测速日志（`LockTiming`）全部移除，`LockGateImpl` 与 `LockSettingsScreen` 的计时变量同步清理（任务 2.8）。

## 1.0.27

- 桌面「本书」形态的划线笔记固定最多两行，超出部分在第二行末尾以省略号截断，字号不再随字数变化，笔记带高度随之回落，封面区不再被挤压（任务 2.7）。
- 桌面「本书」形态的中央与两侧书籍封面各加一圈细边框，线宽随组件缩放取整，描边落在封面内缘，占位封面沿用原有细框（任务 2.7）。
- 桌面阅读卡片的刷新按当前形态映射：本周走每周通道、本月走每月通道、本书走单本通道、本记走笔记通道，待办形态只重读本地不发远端请求（任务 2.7）。
- 修复三处审计问题：校验期间熄屏或改密后旧结果不再解锁（代次作废，且覆盖层静默回到输入状态）；历史十二万轮摘要成功校验后按同一盐与明文重派生为目标轮数并写回；壁纸解码前按采样后的 ARGB 占用做堆预算判断，超预算继续降采样，仍不满足时回退纸色（任务 2.7）。
- 桌面轮播的书架数据源改为逐级惰性回退，加载器命中后不再读取原盘与网络缓存（任务 2.7）。

## 1.0.26

- 负一屏书架与桌面书籍轮播改为按最近在读排序：解析原盘 `assets.json` 的 `readUpdateTime` 并按时间降序稳定排列，缺失时间的书排在后面，选中书不再置顶（任务 2.6）。
- 桌面「本书」形态底部信息整体放大两成：已读百分比、时长、进度条与「打开」「切换」「刷新」三个按钮同步放大，宽度不足时压缩按钮内边距而不裁切文字（任务 2.6）。
- 负一屏设置页调整排版：分组标题上下留白加宽、行名与右侧控件同基线、说明文字单独一行并按 1.3 倍行距换行、开关与加减号触控区放大到 40dp、分隔线只在分组之间出现（任务 2.6）。

## 1.0.25

- 桌面阅读卡片恢复「本周」「本月」「本书」「本记」「待办」五种形态：本书沿用自绘书籍轮播，其余四形态沿用各自旧版整卡渲染，「切换」按形态池顺序轮换并写回配置（任务 2.5）。
- 「设置-微读墨记-设置-自定义」恢复五个形态勾选项，决定切换按钮可轮换的形态，取消最后一项时保留本周；初始化页删除桌面卡片总开关（任务 2.5）。
- 锁屏 PIN 摘要默认迭代轮数由十二万降到两万，S4 实测单次校验由 6886 毫秒降到 1285 毫秒；已存旧摘要仍可校验且不自动重算，重新设置密码后按新轮数写入（任务 2.5）。
- 负一屏书架缓存上限提升到十本，并新增「书架行数」1 至 5 行调节，每行两本（任务 2.5）。

## 1.0.24

- 桌面阅读卡片右下角新增「切换」「刷新」两个描边按钮：切换在书籍轮播与单张之间切换并写入版式序号，刷新重绑内核并按刷新绑定的勾选重取数据（任务 2.4）。
- 「设置-微读墨记-设置-自定义」的「桌面显示的卡片」改为「书籍轮播」「单张」两项单选，与桌面按钮共用同一序号，并说明刷新按钮与「更新于…」遵循同一组勾选（任务 2.4）。
- 锁屏背景保存不再因像素总数超过八千万而判定失败，失败提示按读取失败、非图片、配置写入未确认分类给出原因（任务 2.4）。
- 锁屏背景的文件选择器新增缩略图与「网格/列表」切换，网格复用文件页四列布局，列表带行首缩略图（任务 2.4）。

## 1.0.23

- 锁屏长按删除键期间只保留删除键外圈进度弧，不再显示提示文字；十秒触发确认框的行为与文案保持原样（任务 2.3）。
- 锁屏背景入口从锁屏面板迁到「设置-微读墨记-实验室-锁屏密码-锁屏背景」，提供「选择图片」与「恢复默认」，背景状态行显示当前文件名或「默认纸色」（任务 2.3）。
- 微信读书 API Key 入口收敛到「设置-微读墨记-初始化」：输入框回填已存密钥、新增配置状态行与「扫描二维码录入」，删除桌面卡片无障碍分组，并移除书架设置与内置阅读页的密钥入口（任务 2.3）。
- 负一屏书架模块改为双列封面卡片，重建腰封、进度浮层与右上角百分比角标，取书与点击展开浮层语义保持不变（任务 2.3）。

## 1.0.22

- 书架展示区改为双列封面主导布局，书名与作者合并为覆盖封面的横向腰封，阅读进度改为贴底浮层并保留右下角状态图标，右上角新增百分比角标，界面内不再展示页数（任务 2.2）。
- 桌面阅读组件改为极简杂志版式：左上大号时间与日期、右上当前书籍划线笔记、中部五层封面轮播、中央下方衬线书名、底部进度与打开按钮，左右滑动切换并保持候选书目与选中书同步（任务 2.2）。
- 锁屏支持长按删除键十秒弹出确认框应急清除密码，确认后关闭锁屏进入桌面；设置页新增“长按删除键应急清除”开关，默认开启（任务 2.2）。
- 锁屏面板新增“选择图片”入口，经系统文件选择器设置背景，替换失败保留原图，失配的旧路径按未设置处理（任务 2.2）。
- 应用图标更换为剪纸云雨图，提供自适应图标前景与各密度方形、圆形位图（任务 2.2）。

## 1.0.21

- 锁屏 PIN 摘要校验移至后台线程，输入错误密码不再阻塞输入派发并触发无响应；校验期间显示等待提示，失败后进入抖动反馈（任务 2.1）。
- 锁屏面板样式与微读墨记对齐：白底网点或壁纸加面板白纱、圆形描边按键、错误红字与抖动、删除键置于右下角，输满四位自动校验并移除确认键（任务 2.1）。

## 1.0.20

- 实验室密码、自动锁定和背景接入十雨桌面唯一锁屏服务，支持确认设置、清除和立即锁定。
- App 内保留全部阅读形态与足迹，桌面卡片启用池只影响桌面。
- 内置页随十雨桌面统一更新，移除无执行效果的通道与备用源开关。
- 按用户要求移除远程翻页模块与页签，实验室保留锁屏和续航优化。

## 1.0.19

- 墨台按配置顺序展示五个模块，开关真实生效，关闭全部后保持为空，关闭的模块可重新启用并保留位置。
- 阅读指纹支持最多 50 本书的多选、搜索、清空和确认/取消，选择与字号持久保存，并兼容原版导入偏好。
- 手填价格与移出书目写入持久状态，清价和恢复书目不再被旧值覆盖；同步和重启保留人工编辑。
- 墨台内核计算与绘制使用串行后台队列，页面笔记缓存避免逐条摘录反复读取全库；补丁源码与构建方法随仓库保留。
- 壁纸按实际分区导出，阅读指纹使用原版 480×800 纸张，预览适配屏幕，保存提示使用实际文件名。
- 移除原 App 数据导入和配置回写入口，默认独立同步微信读书，旧缓存继续保留。

## 1.0.18

- 阅读成就使用原版目标和剩余/达成文案，桌面与内置阅读页共用，历史周期不显示当前成就。
- 桌面和阅读页刷新实际请求当前形态，并按偏好补拉周、月与本书；本记强制同步笔记，待办仅重读本地。
- 本记使用完整索引和共享持久状态，支持当日稳定抽取、访问历史、选书顺序与跨天推进；手动本书选择持久保存并补查真实进度。
- 桌面待办过滤已完成项；卡片切换支持启用池内循环或列表选择。

## 1.0.17

- 修复微读迁移被已有空目录或同步数据阻断；替换前备份，配置保存失败时恢复原目录，导入成功后使用导入来源。
- 宿主与内容层统一同步适配：阅读进度与累计时长改取逐书接口，自动本书按最近阅读时间选择，阅读资产不再用周期榜单近似。
- 笔记本索引完整分页，同步划线及个人想法，统一书名、作者、价格、进度与章节字段；逐本保存缓存，失败保留数据并支持续补。
- 同步与迁移互斥；部分同步明确报告待补项，网络失败后停止后续逐书请求。

## 1.0.14

- 桌面组件「本书」形态的选书弹层改用内核 `BookPicker`：遮罩与「点空白处关闭」、白色底板、标题行「选一本书」与关闭、浅灰行分隔线、选中行左侧黑竖条、四边墨框，行文字垂直居中；点击按 `hitTest` 分三支处理，拖动走 `scrollBy`，选中后按 `bookIdAt` 切书。删除组件内自绘浮层实现（`pickerLayout`、`isSelectedRow`、`selectBook(action)`、`ACTION_PICK_*`）。
- 内核依赖由 k5 升级到 `weread-kernel-1.3.2-k6.jar`（220647 字节，sha256 `0e8d30a95aa06039152d297879253ae1872ae9ac88beb1cc24553fba48c4ec3a`，`Kernel-Api-Version: 6`），新增 `com.inkread.weekread.kernel.pick.BookPicker`；k5 与 k6 的 `CardRenderer`、`CardState`、`CardGeometry`、`CardKernel` 四个类字节相同，未弹层时卡片版面不变。
- 设置自定义页补齐四段：本月呈现（打卡与热力图，写 `month_style`）、本记字号（滑块 12..24，显示号数与像素值，写 `note_card_size`）、卡片切换方式（循环与列表，写 `switch_mode`）、桌面显示的卡片（五个勾选，写 `card_pool_mask`，主导航下拉按掩码过滤，全清时提示并保持原值）。
- 洞察页底部刷新按钮在测量层加高度封顶，父容器以 `AT_MOST` 或 `UNSPECIFIED` 测量时把高度限制到屏幕高度，避免极端宿主下被挤出屏外。
- 版本号 34，versionName 1.0.14。

## 1.0.13

- 周期行挂载高度改为 `dpRound(44f)` 换算（`TypedValue` 转换后 `roundToInt`），S4 219dpi 得 60px、P4 210dpi 得 58px；两机基线量测写入 `docs/acceptance/27-weread-page-parity.md` 第六节。
- 待办对话框改为实例像素属性与固定尺寸描边按钮，面板、日期行、时间行、动作行与按钮左右边界与微读实测误差不超过 1px。
- 事实修正两项：「选记搜索行放大镜」不成立，微读 `NotePickView.java` 无图标绘制，十雨与之一致；「遮罩纯色无渐隐」不成立，两侧同为 `SCRIM = 0xFFD8D8D8` 单色 `drawRect`。两项均从差异表与 `docs/27` 第九节删除。
- 版本号 33，versionName 1.0.13。

## 1.0.12

- 时间选择改为自绘步进弹窗，按微读 `dialog_time_pick.xml` 重写：标题「选择时间」、时与分两行各为标签加减号与数字、输入框下划线、底部提示、取消与确定；每次加减 1，时按 24 循环、分按 60 循环，长按 450ms 后每 120ms 连发，数字可直输并在确定时夹取。修复两处实现错误：小时误用 1440 取模导致 00 减一显示 14、步进按钮文字被内边距压没。
- 待办列表行在条目有日期或时间时第二行显示，字体与颜色按微读次行样式。
- 本记导出补两机对照：P4（552×768）与 S4（480×800）六变体像素完全一致，手账中字 480×702、素白 480×680、卡纸 480×702、小字 480×496、大字 480×994、无署名日期 480×702；导出宽度恒为 480，与屏幕分辨率无关。
- 版本号 32，versionName 1.0.12。

## 1.0.11

- 待办编辑与新增对话框补齐六段：标题、输入行、日期行（状态文字 + 选日期 + 清除）、时间行（同）、底部按钮行；编辑态为删除、取消、保存，新增态为取消、保存；选日期与选时间走系统对话框，清除回到「未设日期」「未设时间」；日期时间写入待办持久化，列表次行显示日期与时间。
- 本记导出改用微读 `NoteExport.java` 原样，纸张底纹、字号、署名与日期四项经 `WereadMicroBridge` 读 `kernel_prefs`，与设置页联动；导出宽度固定 480，高度随手账与字号档位变化。
- 实验室页补齐三段页签「翻页」「锁屏密码」「续航优化」：翻页页保留连接方式分组形态与角色单选、状态胶囊、折叠说明与页尾无障碍入口；锁屏密码页支持启用、四位密码（8 字节盐加 SHA-256）与背景路径；续航优化页含授权命令复制与三项系统开关。
- 选记与选书拆成两套贴底半屏：占屏 0.70 与 0.62，上半屏蒙层可点关闭，左上标题与右上关闭区，选中行左侧 4dp 黑竖条，拖动阈值 12px；选记带 54dp 搜索行、行高 44dp，选书无搜索行、行高 46dp 且首行为「自动（最近在读）」。
- 周期行挂载高度由 46dp 收到 44dp，删除多余分隔线，只保留自绘上下两条 `#D8` 线。
- 版本号 31，versionName 1.0.11。

## 1.0.10

- 墨台五处修复：去掉内核分区重复抬头；阅读指纹进运行时默认装配集合；顶栏改为 `FrameLayout` 三段全宽定位并补 1px 底线；行分隔线内缩 24px 改 1px `#D8D8D8`；自建分区补 3px 墨竖线与 108px 固定高度空态；另修装配时序，`rebuildSections` 末尾补恢复回调。
- 内置页阅读页与本记进度补齐：`noteProgress` 与 `noteFetchedAt` 走 `WereadMicroBridge`，「（未命名）」按 bookId 回查 `nt_index` 补书名与作者。
- 版本号 30，versionName 1.0.10。

## 1.0.9

- 内置页阅读页按微读墨记 activity_main.xml 的 mp_reader 结构重排：主导航 SegTabView（本周▽/设置/实验室/待办）、周期行 PeriodPickerView（左箭头 + 日期区间 + 右箭头）、WeekCardView 卡片、InsightPageView 洞察、底部整宽刷新按钮；删除自建的四段子页签与 2026-W43 文本周期行。
- 复用微读 UI 层源码 24 个 Java 文件（eature/weread-app/src/main/java/com/inkread/weekread/），保留原包名；WeekCardView 的五处内部读盘改走新增 WereadMicroBridge，统一到 AppServices.config 与 WereadAppData。
- 形态切换改由主导航首项下拉（本周/本月/本书/本记/足迹），选「足迹」进洞察页。
- 门禁对 **/com/inkread/weekread/** 排除 detekt 与 ktlint，Kotlin 胶水代码照常受检。
- 版本号 29，versionName 1.0.9。
## 1.0.8

- 设置页改为「初始化」「自定义」两个常显页签，顺序与微读墨记 1.3.2-beta 一致；初始化页含本机角色、API Key、桌面卡片无障碍、桌面卡片总开关与重新同步、怎么用、版本与更新，自定义页含仅一页模式、启用墨台、刷新绑定、阅读成就提示、本记导出图、书籍排名。
- 偏好键改为内核同名（install_role、single_page_mode、cb_desk_enabled、bind_week、bind_month、bind_book、cb_achv、achv_*、export_tpl、paper、nsize、note_sign、note_date、rank_shelf_only），页内勾选与单选即写即生效。
- 检查更新跳转十雨更新入口；无障碍按钮跳系统无障碍设置；保存密钥后返回阅读页。
- 版本号 28，versionName 1.0.8。
# 变更记录

本文件按版本记录变更，条目后标注对应任务编号。版本号规则见 `docs/design/15-V1.0开发计划.md`。

## 0.9.0 基线

versionCode 9，versionName 0.9.0，2026-10-19。

目录快照 `D:\DSHStation\OctoberLauncher-V0.9`，含 2893 个文件的 SHA-256 清单，清单摘要 `f96d95de10f2ac6521a55ad195034ded7bee3f080f14a182b7de1b04c4476454`。

本版为 V1.0 开发的起点，代码复核与两项融合评估的结果记录在 `_analysis/code-review-and-fusion-eval.md`，开发计划见 `docs/design/15-V1.0开发计划.md`。已知状态：debug 与 release 均可构建，release 未签名，单元测试三处失败，持续集成配置按该状态无法通过。

## 0.9.1

versionCode 10，versionName 0.9.1，2026-10-19。

工程基线与复核第一批。

- task-7 修复 `DesktopMetricsTest` 的 `widgetGap` 参数缺失并补三档宽度用例，补齐 `feature/settings` 与 `feature/content` 的首批纯逻辑测试，接入 detekt 与 ktlint 并生成 12 份模块基线，重写持续集成为构建、测试、静态检查三道门禁。
- task-8 移除内置微信读书密钥，配置迁移升至 v7，备份导出剔除密钥与锁屏凭据、导入沿用本机凭据，`feature/settings` 新增密钥格式校验与设置项，`WereadClient` 取消密钥回退。
- task-9 收窄 R8 keep 规则至模块入口、序列化模型与组件实现，保留类由 594 收窄至 63，release 采用调试签名以便安装验证。
- task-6 新增 `VERSION`、`CHANGELOG.md` 与 `scripts/snapshot.ps1`，生成 0.9.0 与 0.9.0-b 两份基线快照。

门禁复核（Lead 独立执行）：构建 `assembleDebug` 与 `assembleRelease` 成功，发布包 473991 字节并经 `apksigner` 校验；六模块 85 个用例通过、跳过 1 条；`detekt` 基线后 0 条，`ktlintCheck` 通过并记录 3046 条告警；S4 真机安装发布包后桌面、组件与图标绘制正常，点击图标与 HOME 返回均符合预期。复核记录见 `_analysis/verify/0.9.1-gate.md`，静态检查统计见 `_analysis/verify/0.9.1-static.md`，发布构建数据见 `_analysis/verify/0.9.1-release.md`。

已知状态：`ktlint` 告警待 0.9.2 前格式化清零，`detekt` 基线 234 条待逐步消减；release 签名沿用调试证书，正式发布前替换。

目录快照 `D:\DSHStation\_snapshots\OctoberLauncher-0.9.1`，含 2919 个文件的 SHA-256 清单，清单摘要 `c7872ee43c4c46479368bb9764e2026f5835beacbe8afd465806befe9732bbf8`。摘要值在快照生成后补写，与快照内本文件相差本行内容。

## 0.9.2

versionCode 11，versionName 0.9.2，2026-10-19。

复核第二批、静态门禁落地与 Lint 缺陷修复。

- task-10 锁屏改 PBKDF2（`PinHasher` 读取配置中的迭代次数），增加失败计数与递延、`PinHasherTest` 10 例；`LockGateImpl` 初值按 `enabled && autoLock` 恢复，冷启动与熄屏后 PIN 界面正常出现。状态恢复为显式状态机并解决 `isLocked` 抖动；`Contracts.kt` 新增锁状态接口，`MainActivity` 经接口读取。
- task-11 启动期工作移出构造器：`SystemControlsImpl` 的接收器注册与 `refreshAll` 改由 `start()` 触发，`updateWifi` 统一捕获异常；`NotificationCenter` 改用 `_items.update`。
- task-12 配置写入改为落盘成功后发布新状态，失败保留旧值并通过 `ConfigPersistence` 抽象暴露错误；迁移结果回写，高版本配置原样保留。
- task-13 界面可用性：`MainActivity` 页面栈判重与复用、`Destination` 标签去重、补 `onNewIntent`；`HomeView` 占位固化与整理模式手势修正；`HomeScreen` 按组件声明区分刷新范围；`WidgetApi` 扩充 `category`、`refreshPolicy`、`requiredCapabilities`，`WidgetProviderDefaultsTest` 7 例。
- task-14 设置项声明化：`SettingsSpec` 声明表取代散落的 44 处 `section` 与 15 处 `edit`，新增一项设置只改声明表一处；分页统一走 `DesktopLayoutProvider`；`SettingsSpecTest` 11 例与 `DesktopPaginationTest` 10 例。
- task-15 设置项遗留清理：声明表按页面拆为 7 个文件（合计 19 组 60 条，逐项一致），两条与分组重名的设置项标识改为 `system.display.page` 与 `bookshelf.rows.count`，`BeautifyIconPanel.kt` 由 599 行降至 402 行。
- task-16 静态门禁落地：全仓 171 个文件完成 `ktlintFormat`，ktlint 告警由 3046 条清零；detekt 与 ktlint 的 `ignoreFailures` 全部改为 false；新增 Android Lint 按模块基线 13 份，`gradle check` 成为真实门禁，人为违规可使构建失败。
- task-17 修复 Lint 暴露的缺陷：三处 `unsafeCheckOpNoThrow` 调用（要求 API 29）在 Android 8 与 9 上被异常吞掉、使用统计权限恒为未授予，统一改经 `core:device` 的 `UsageAccessChecker` 按版本分支；行宽恢复为 120，由新增 `.editorconfig` 与 detekt 规则共同约束，超限行清零。

门禁复核（Lead 独立执行）：`gradle --offline --rerun-tasks testDebugUnitTest` 为 19 个套件、149 个用例、0 失败、1 跳过；`gradle --offline check` 通过，`ktlintCheck` 与 `detekt` 未抑制告警均为 0；`assembleDebug` 产出 3504754 字节，`assembleRelease` 产出 487679 字节并经 `apksigner` 校验（`CN=Android Debug`，证书摘要 `8980fa5bc316ad2ce7439b6af3c9c0042d10a0b4472669218e9a80c3480c7c73`），APK 摘要 `2a0f336145d8813e02fc70b7c6997a9adff27ed6d2056e640a3f9accf78b286c`。真机 API 30 上验证冷启动 PIN 界面、解锁、引导页与权限状态一致性，截图见 `_analysis/verify/shots/`。

已知状态：detekt 基线 237 条、Android Lint 基线 82 条（其中错误级别 7 条）按模块逐步消减；Android 8 与 9 仅代码路径覆盖，未实机验证；截图测试与覆盖率门槛未接入；release 签名沿用调试证书。

目录快照 `D:\DSHStation\_snapshots\OctoberLauncher-0.9.2`，含 3043 个文件的 SHA-256 清单，清单摘要 `41805e74c54b2f3fac00f1675108d51c8864e10a06bf7fc5276a3ef88e9dc33c`。摘要值在快照生成后补写，与快照内本文件相差本行内容。

## 0.9.3

versionCode 12，versionName 0.9.3，2026-10-20。

渲染管线合并与缩略图缓存。

- task-18 统一解码：`core/render` 新增 `ImageDecoder`（EXIF 方向归一、长边限幅、像素与堆预算）、`ImageSizing`、`ThumbKey`、`ThumbCache`、`DitherCore`，`BitmapPipe` 改为委托，七处分散解码改走统一入口，新增 28 例测试；`Ditherer` 支持两级量化并保留原有接口。
- task-20 缓存接入：`ThumbCacheRegistry` 按目标长边与抖动开关复用缓存实例，`ThumbCacheLoader` 统一「命中返回、未命中解码写入」，书封网格、图片目录、书架封面组件、图片组件四处接入；目录变更、图标写入与壁纸写入接入失效与 `RefreshScheduler.notifyContentChanged`；真机两轮浏览各 200 项，PSS 48185 KB 至 76344 KB 再至 75673 KB，磁盘缓存约 872 KB，EXIF 方向样张由 320×240 归一为 240×320。
- task-24 索引隔离：索引文件改为按实例命名 `index-<长边>-dither<0|1>.txt`，修正多实例共用 `index.txt` 时互相覆盖条目，新增 `ThumbIndexTest` 4 例。
- task-19 与决策：墨盘移植分析完成（源码 9320 行，建议移植 6968 行、重写 2352 行、放弃 1497 行，分四批次），据此形成 `docs/design/16-墨盘移植决策.md`，明确文件模块归属、权限模型、Provider authority、三档几何、相册与编辑范围。
- task-21 提前入库：`feature:files` 模块骨架与数据层（八个数据层文件、`FilePager`、33 例测试）随本版入库，文件入口默认隐藏，页面接入自 0.9.4 起；该项计入 0.9.4 第一阶段。

门禁复核（Lead 独立执行）：`gradle --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease` 通过，27 个套件、214 个用例、0 失败、1 跳过；`check` 含 `ktlintCheck`、`detekt`、`lint` 全部通过，超 120 列行 0；调试包 3554769 字节，发布包 484535 字节，签名 `CN=Android Debug`；S4 真机安装最终调试包后启动、桌面绘制与组件选择页正常。门禁数据、真机过程与遗留事项见 `_analysis/verify/0.9.3-gate.md`。

已知状态：缩略图抖动开关尚未提供设置入口，多尺寸缓存实例的内存上限累加待 0.9.4 复核；计划任务 3.3 的 500 张浏览口径待文件页与相册可用后补测。

目录快照 `D:\DSHStation\_snapshots\OctoberLauncher-0.9.3`，含 3100 个文件的 SHA-256 清单，清单摘要 `57007b9c3057b286f6e5f9cf9bfb561d684649c1f4d3f42a42f7f46656845e5d`。摘要值在快照生成后补写，与快照内本文件相差本行内容。

## 0.9.4

versionCode 13，versionName 0.9.4，2026-10-20。

文件模块第一阶段，编辑页与相册随本版并行入库并验收，壁纸部分留待 0.9.5 补齐。

- task-22 浏览页与缩略图：`feature/files` 新增 `BrowseScreen`、`BrowsePane`、`GridPane`、`ListPane`、`FilePaging`、`FilePagerChrome`、`FilePagerTouch`、`ThumbPrefetcher`、`BrowsePanel`、`FileText`、`PaneDraw`、`FileMetrics`；几何改经 `DeviceProfile` 与设计令牌计算，三档宽度各有一组实算值；滚动时前后各预取一页缩略图，缩略图统一走 `ThumbCacheRegistry`；真机 500 张图 32 页，滚动 20 页耗时 40.37 秒，PSS 54.8 MB 至 58.3 MB。
- task-23 查看器与入口：`PhotoScreen` 651 行实现三档缩放、48 dp 滑动换图、邻图预取、长按动作面板、详情与分享；`PickPane` 提供选图页；`PhotoEntryActivity` 与 `PickEntryActivity` 承接外部 `ACTION_VIEW` 与选图请求；`file_paths.xml` 增加 `files_share` 与 `files_share_cache`；`Contracts.kt` 新增 `PhotoViewer`、`PhotoEditor`、`Album` 三个目的地。真机 `create=27ms`，首屏解码 112 至 242 ms，换图命中预取。
- task-25 编辑内核：`Gray` 五档灰阶与三档对比度、`GrayKernels` 的 8×8 Bayer 与 Floyd–Steinberg、`CropMath` 的裁剪与旋转矩阵、`EditSession` 操作栈、`EditExport` 导出命名与长边档位，44 例测试。
- task-27 编辑页：`ui/edit` 下 13 个文件，`EditSurface` 与非破坏式控制条五小件；旋转、裁剪、滤镜、另存全流程可用，导出文件写入 `Pictures/十雨桌面`。真机首屏 35 ms，修正位图回收竞争、保存后重复确认、滤镜面板回收后读宽、浅色图上裁剪框不可见四处缺陷。
- task-28 相册：`MediaIndex` 建立日与月两级分桶，媒体库与目录扫描两路始终合并，`refresh(since)` 做增量；`AlbumScreen`、`AlbumPane`、`AlbumPaging` 实现一天九张一段、每页三段时间轴；修正首版漏扫未入库图片与单日超过九张无法翻页两处缺陷。真机 1058 张，首次索引 1454 ms，二次读取 494 ms，41 页。
- task-26 阅读数据层：`WereadTransport`、`WereadStats`、`WereadParsing`、`WereadBucketParsing`、`WereadCache` 与 `WereadClient` 扩展，覆盖统计、笔记、账单、排行四类接口与六期缓存轮转，30 例测试，为 0.9.6 组件与页面提供数据。

门禁复核（Lead 独立执行）：`gradle --offline --rerun-tasks testDebugUnitTest :app:assembleDebug :app:assembleRelease` 与 `check` 通过，45 个套件、384 个用例、0 失败、1 跳过；超 120 列行 0；调试包 3743072 字节，发布包 501465 字节，发布包 SHA-256 `1cf1c0446ea311376fa3edfaa15419f3a08d0766994ac70e11f72b0a09a737c0`，签名 `CN=Android Debug`，`mapping.txt` 77289 行。门禁数据与真机走查索引见 `_analysis/verify/0.9.4-gate.md`。

已知状态：壁纸设置与量化未实现；编辑页旋转后裁剪框重置为全图，导出耗时与内存未采集；相册对已删除文件只在下次 `rebuild` 剔除；选图回传消费方尚未接入；`MediaIndex` 630 行超出单文件 600 行约定；API 26 至 28 未实测。

目录快照 `D:\DSHStation\_snapshots\OctoberLauncher-0.9.4`，含 3298 个文件的 SHA-256 清单，清单摘要 `75bad824116e46c5ad78b1637b0660b8dddb5d0b5c5fa14d01fe97b59a797b49`。摘要值在快照生成后补写，与快照内本文件相差本行内容。
## 0.9.5

versionCode 14，versionName 0.9.5，2026-10-20。

锁屏壁纸、图片量化与微信读书组件集。

- task-29 锁屏壁纸与量化：`AppearanceConfig` 新增 `wallpaperQuantize`、`lockWallpaperPath`、`lockWallpaperQuantize` 三字段且不升配置版本；`AppearanceRepositoryImpl` 增加锁屏壁纸的设置、清除与加载，整幅拉伸改为居中裁剪后量化；新增 `WallpaperGeometry` 67 行、`WallpaperQuantize` 58 行与 `LockWallpaperView` 137 行；`LockOverlayView` 背景改透明，`MainActivity` 锁屏层固定第 0 层壁纸并让锁屏状态栏透明；设置页壁纸分组新增四项声明。真机四档量化肉眼差异明确，重启后壁纸与档位保留，无壁纸回退纸色，旧文件在锁屏替换路径验证删除。
- task-26 微信读书数据层：`WereadTransport`、`WereadStats`、`WereadParsing`、`WereadBucketParsing`、`WereadCache` 与 `WereadClient` 扩展，覆盖统计、笔记、账单、排行四类接口与六期缓存轮转，30 例测试。
- task-30 组件集与接口扩展：`Contracts.kt` 新增 `WereadContent` 接口与八个读取方法及快照类型，`ContentSource` 继承该接口；`ContentSourceImpl` 五个新方法沿用既有超时与来源开关；新增 `WereadNoteWidget`、`WereadStatsWidget`、`WereadBillWidget`、`WereadRankWidget`、`WereadFingerprintWidget` 与三态基类 `WereadCardWidget`、纯逻辑 `WereadWidgetData` 与 `WereadWidgetLogic`；`HeatMonthWidget` 增加整月与按周两视图，点击切换；五个组件登记 `WidgetCatalog`，分类 WEREAD、刷新策略 EVENT、能力 WEREAD。480×800 下组件占 4 列 × 2 行，约 430×250 像素；无数据态「暂无阅读数据」，未配置态「微信读书未配置」。

门禁复核（Lead 独立执行）：`gradle --offline testDebugUnitTest check` 与 `gradle --offline --rerun-tasks testDebugUnitTest :app:assembleDebug :app:assembleRelease` 通过，48 个套件、420 个用例、0 失败、1 跳过；超 120 列行 0；调试包 3781692 字节，发布包 519929 字节，发布包 SHA-256 `ae197ca08394dc8b479d7836ba7ae791b3a5e121dfa803ba4f60f38d308a7df4`，签名 `CN=Android Debug`，`mapping.txt` 80521 行。门禁数据、真机过程与遗留事项见 `_analysis/verify/0.9.5-gate.md`。

已知状态：`refreshPolicy` 的宿主调度接入与组件选择页的能力过滤未完成；微信读书有数据态未在真机验证，需配置密钥后复验；壁纸桌面替换路径的旧文件删除未复验；552×768 与 824×1648 设备不在手边。
目录快照 `D:\DSHStation\_snapshots\OctoberLauncher-0.9.5`，含 3339 个文件的 SHA-256 清单，清单摘要 `ea58185c6b053b4894cb9e59c8d0e0b7d12e42f70e02e421f811443c772ac930`。摘要值在快照生成后补写，与快照内本文件相差本行内容。
## 0.9.6 并入 0.9.5

阅读数据与组件集不单独发版，内容随 0.9.5 的 versionCode 14 一并入库，任务编号 task-26 与 task-30，验收数据见 `_analysis/verify/0.9.5-gate.md`。

## 0.9.7

versionCode 15，versionName 0.9.7，2026-10-20。

阅读页面、设置项与负一屏文件区，其中 0.9.8 的任务 8.1 提前入库并计入本版验收。

- task-31 阅读页面：`WereadScreen` 拆五分段（总览、统计、笔记、账单、排行），按需加载并缓存、周期切换、阅读指纹并入统计；新增 `WereadCalendarScreen` 276 行与 `WereadSectionViews.kt` 512 行，渲染与状态计算移入 `WereadSections.kt` 以便 JVM 测试；书架与负一屏增加笔记与统计入口；`WereadSectionsTest` 14 例。
- task-32 设置与许可：`LauncherConfig` 新增 `wereadRefreshMinutes` 与 `wereadStatsRange` 两字段并配解码后回退，`CURRENT_VERSION` 保持 7；声明表新增两项（四档刷新间隔、三档统计范围）；新增 `SettingEffects.kt` 把 `Toggle / Choice / Stepper / Slider / Info / Action / Button / Custom` 八类控件的生效统一分发；关于页新增「数据来源」与「开源许可」，`ThirdPartyLicenses.kt` 登记 `WereadMoji — MIT — 2026 — Mosley-Z`；`WereadReadingSettingsTest` 12 例。
- task-33 负一屏文件区：`MediaIndex` 新增只读 `recent(limit)`（内存索引、过滤非图片、时间倒序、同时间路径升序）；新增 `FilesNegativeModule` 360 行实现两行三列最近图片卡片与空态、全部图片入口，缩略图统一 `ThumbCacheRegistry.get(context, 127)`；`FilesModule` 安装时按标识幂等注册；`RecentImagesTest` 13 例。真机卡片、空态与点击进入查看器均通过。
- 静态检查修复：`BookshelfNegativeModule.buildCard` 拆为骨架与表头两函数，`feature/content` 的 detekt 与 ktlint 告警清零，未使用抑制注解、未改阈值与基线。

门禁复核（Lead 独立执行）：`gradle --offline --rerun-tasks testDebugUnitTest check :app:assembleDebug :app:assembleRelease` 通过，51 个套件、459 个用例、0 失败、1 跳过；ktlint 与 detekt 无告警，超 120 列行 0；调试包 3819820 字节，发布包 529313 字节，发布包 SHA-256 `df9fb854f4fe7ac5a59e951af1e9b8d60003d3bc1c97fbc66a455dc29e2b75c9`，签名 `CN=Android Debug`，证书摘要 `8980fa5bc316ad2ce7439b6af3c9c0042d10a0b4472669218e9a80c3480c7c73`，`mapping.txt` 82993 行。门禁数据与真机走查索引见 `_analysis/verify/0.9.7-gate.md`。

已知状态：负一屏卡片仅在创建与 `onResume` 刷新，负一屏模块无销毁回调；微信读书有数据态未在真机复验，需配置有效密钥；`refreshPolicy` 的宿主调度接入与组件选择页能力过滤未完成；`MediaIndex` 643 行超单文件 600 行约定；552×768 与 824×1648 设备不在手边；API 26 至 28 未实测。

目录快照 `D:\DSHStation\_snapshots\OctoberLauncher-0.9.7`，含 3438 个文件的 SHA-256 清单，清单摘要 `a076f8fa17f17c169f444d224007e0e7fb112fbcda2680d2b892515939e11239`。摘要值在快照生成后补写，与快照内本文件相差本行内容。

## 1.0.0

versionCode 20，versionName 1.0.0，2026-10-20。

首个正式版本。内容为 0.9.8 的桌面联动收尾与 9.1 至 9.4 的验收、文档、整理与发布准备；0.9.8 不单独发版。

- task-35 桌面壁纸复验：桌面壁纸替换路径、旧文件删除与锁屏壁纸在 S4 上通过，量化四档观感与锁屏压层正常，重启后保留。
- task-34 设置项补齐：`appearance.image` 分组新增六条声明（图片目录信息与更换、缩略图质量、缩略图抖动、缓存占用与清理）；`Models.kt:210` 新增 `thumbnailQuality` 与 `thumbnailDither` 两字段，配置版本保持 7；`core/render/ThumbProfile.kt` 提供三档参数与像素映射，`ThumbCache.clear()` 与 `ThumbIndex.reset()` 支撑缓存清理；settings 模块 44 例测试。
- task-39 负一屏卡片开关：新增 `NegativeSpecs.kt` 101 行与分组 `negative.cards`，两条 Toggle 维护 `config.desktop.negativePage.modules`，未知标识顺序保留；`Models.kt:97` 默认模块改为 `["bookshelf","negative_files"]`，`CURRENT_VERSION` 未变、无迁移；`NegativeSpecsTest` 8 例。
- task-36 文档新增：`docs/design/16-files-module.md`（数据层、页层、查看器、编辑、相册、负一屏卡片、测试与已知问题）与 `docs/design/17-weread-fusion.md`（融合边界、数据层、组件、页面与设置、许可、差异与后续）。
- task-37 文档重写：`docs/design/03-target-architecture.md` 13 节按实现重写；`docs/design/08-engineering-conventions.md` 增加静态检查与 Lint 基线章节；`docs/design/01-feature-inventory.md` 按 Destination、组件目录与设置声明重写。
- task-38 仓库整理：根 `README.md` 重写为 150 行，上游 APK 移至 `_tools/upstream/` 并注明来源，13 个模块 README 补齐；`_analysis` 与 `_shots` 移出工程目录需 Doow 许可，本版未执行。
- 9.1 全量回归：`gradle --offline -q --rerun-tasks testDebugUnitTest check :app:assembleDebug :app:assembleRelease` 退出码 0，73.5 秒；53 个套件、482 个用例、0 失败、1 跳过；detekt 14 个模块 0 条，Lint 基线 72 条无新增，ktlint 通过，超 120 列 0 行；调试包 3833368 字节、SHA-256 `c4b83072033292e29d65737ff34d55aa0369174c0902f09bc0c0637d18aabfd3`，发布包 567265 字节、SHA-256 `cb20c8aa9af620828f3c68f00b4cecc823b4910513cc71ba83bf04243b4a9064`；`mapping.txt` 90247 行 9622056 字节。
- 9.4 发布准备：版本号置 `1.0.0`、`versionCode` 20，`VERSION` 同步；S4 上安装混淆后的发布包，冷启动 1053 毫秒，桌面、负一屏两张卡片、文件页与查看器正常，PSS 30761 KB，日志无崩溃与反序列化异常。门禁数据、真机过程与遗留见 `_analysis/verify/1.0.0-gate.md`。

已知状态：壁纸与图片管线只在 480×800 实测，552×768 与 824×1648 未验证；微信读书有数据态需配置有效密钥复验；负一屏卡片在创建与 `onResume` 刷新；`refreshPolicy` 的宿主调度接入与组件选择页能力过滤留待 1.0.1；`_analysis` 与 `_shots` 仍在工程目录内；release 沿用调试证书，对外分发前替换。

目录快照 `D:\DSHStation\_snapshots\OctoberLauncher-1.0.0`，含 3513 个文件的 SHA-256 清单，清单摘要 `5e6346a83908ae471a2fe435866118a449ec48d67db4b819fab8253766f41009`；`OctoberLauncher-0.9.3` 按保留四份的规则移入 archive。摘要值在快照生成后补写，与快照内本文件相差本行内容。

## 1.0.1

versionCode 21，versionName 1.0.1，2026-10-20。

与微读墨记 1.3.2-beta 同步发布。桌面侧接入微读内核、扩展负一屏与组件契约；微读侧抽取内核、新增静默进程与导出桥。

- 内核抽取（A1 至 A5，微读墨记仓）：`com.inkread.weekread.kernel` 独立为 32 个 class 的 `weread-kernel-1.3.2-k1.jar`，67810 字节，SHA-256 `95dddcac3c40f1404b5c1bf1aa925cfe9fbed556f4bf2e1437136128eeb58536`，构建两次字节一致；`KernelHost`、`CardSurface`、`DataSource`、`CardKernel` 四类接口与 `FORM_WEEK` 至 `FORM_TODO` 五种形态；宿主适配层 `WeekCardView` 实现内核 `CardView`，`feature/CardRenderer` 改薄壳；逐像素比对五种形态差异像素为 0，差异集中在状态栏时钟与刷新图标等动态区域。
- 导出桥与静默（A3、A4，微读墨记仓）：显式组件 `com.inkread.weekread/.bridge.ExportActivity` 以 `startActivityForResult` 输出单文件迁移包，清单行加 AES-256-GCM 密文，条目含原盘书架与笔记文件、墨单三份偏好与 `kernel_prefs.json`；静默进程 `:silent` 承载启动守卫与数据冻结，`PACKAGE_REMOVED` 监听十雨卸载；候选包 `dist/weread-stats-1.3.2-beta.apk` 592624 字节，versionCode 48。
- 内核接入（B1）：`core/common`、`core/data` 之外的 `feature/content`、`feature/widgets` 引入 `libs/weread-kernel-1.3.2-k1.jar`，`app/proguard-rules.pro` 保留内核类；`mapping.txt` 中内核标识 435 行保持原文。
- 负一屏扩展（B2）：`Contracts.kt` 的 `NegativeModule` 增 `defaultEnabled`、`orderHint`、`onDestroy`，`NegativeHost` 增 `context`、`widthDp`；`Models.kt` 增 `DEFAULT_NEGATIVE_MODULES` 五模块与 `NegativePageConfig.forms`、`cardOrderInitialized`，配置版本升至 8；新增账单、排行、指纹、资产四个模块，负一屏右上角进入设置页，设置页承载六模块开关与顺序、墨单外观十三项。
- 组件契约与收敛（B3、B6）：`WidgetApi.kt` 增 `WidgetHitRegion`、`WidgetHitRegionSource.hitRegions()`、`hitRegionAt` 与坐标版 `onLongPress`；新增整体卡片 `weread_board` 承载内核五形态；组件由 19 条收敛至 6 条，删除 13 个组件源文件，旧标识迁移改写入 `weread_board` 或 `clock`；时钟吸收紧凑形态，图片组件改为多图轮播，间隔 5 至 60 秒可选。
- 页面与导入（B4、B5）：`Destination` 增本书、本记、负一屏设置、待办四项目标并补标签；新增 `WereadImportReader`、`WereadImportStore` 与 `WereadMigrationCoordinator` 完成迁移包解析、解密、落位与幂等记录，设置页书架分组增导入入口。
- 录密钥（B10、B11）：自写二维码编码器输出字节模式纠错 L 的矩阵，局域网页面在随机端口接收密钥，令牌存活五分钟、请求体上限 4 KB、五次尝试，写入 `integrations.wereadApiKey` 后触发内容刷新。
- 门禁数据：`gradle --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease` 退出码 0，ktlintCheck 与 detekt 十四模块零告警；调试包 3997921 字节，SHA-256 `d23b7411e9d7539ab81a361a10d21e5934adf7cae6382a7d82fe2adf8fef2a0d`；发布包 631322 字节，SHA-256 `c21a3f20c8325cb0637336db3fbeffbe942027b103867d4095c08b4c3cb6350d`；`mapping.txt` 10623723 字节，SHA-256 `8e18e41e14b59f847556b66c07f2ca036f2d524804d9a2b6cb7b1e9cb9b808e0`。S4 装发布包后冷启动三次 1010、996、1129 毫秒，中位 1010 毫秒；PSS 35628 KB，较 1.0.0 增 4867 KB。
- 修复：内核 `CardState.setPhMain(null)` 把空串写回，`CardRenderer.draw` 以非空判断占位，录入密钥后整体卡片只剩白底；渲染器改为同时排除空串，重建 `weread-kernel-1.3.2-k1.jar`（67810 字节，SHA-256 `95dddcac3c40f1404b5c1bf1aa925cfe9fbed556f4bf2e1437136128eeb58536`）并重出微读候选包（`dist/weread-stats-1.3.2-beta.apk` 592624 字节，SHA-256 `b110bc62791c574ce6fe1c9687b8253bd15106b6651087337ec9dc6ad3c3e1b0`），十雨发布包同步重建。
- 验收记录见 `_analysis/verify/20261020-121428/report.md`，两工程分工的成员记录见 `_analysis/team/`。

已知状态：组件选择页截图、552×768 与 824×1648 设备、微信读书有数据态、迁移与共存四种状态均未在本次执行，列入后续核验；负一屏最近图片的少量缩略图呈 180 度倒置，解码链路无额外变换，待按系统相册对照确认；录入密钥后整体卡片空白的问题已定位并修复，S4 真机复验通过；release 沿用调试证书，对外分发前替换。

目录快照 `D:\DSHStation\_snapshots\OctoberLauncher-1.0.1`，含 3595 个文件的 SHA-256 清单，清单摘要 `35d7b6f19e73ac6fd8ceddd8a0f28db1a67557cc8ee62d960fff668577fc0a0a`；`OctoberLauncher-0.9.7` 按保留四份的规则移入 archive。摘要值在快照生成后补写，与快照内本文件相差本行内容。

## 1.0.7

versionCode 27，versionName 1.0.7，2026-10-20。

- 外观八项即时生效：新建 core/common/src/main/java/com/shuiyu/octoberrain/core/common/WereadAppearance.kt 作为微读墨记外观偏好的只读视图，eature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/kernel/WereadAppearanceCache.kt 按文件长度与修改时间缓存，避免逐帧解析；KernelWereadBoard 每帧注入本记字号、全屏字号、月历 heatmap 与成就目标文案，WereadBoardWidget 按纸色填充底色，KernelDataSourceImpl 按 
ank_shelf_only 过滤榜单。
- 周期窗口扩到二十八期：WereadSyncService.defaultPeriods() 覆盖最近十二周、十二月、三年与全时段，etchStats 增加四十五秒截止保护，账单仍只写周与月两类；WereadAppData 增加 hasPeriod 与 
equestPeriod，周期选择器在目标期无缓存时触发按需同步并显示「同步中…」。
- 负一屏 ANR 三处修复：WereadDeskSectionView.startMeasure 核算失败时也记录宽度，消除每帧重起线程；DeskContainerView 分区构建改为首帧后执行；ShelfDeskSection.reload 取数切到 Dispatchers.IO。
- 组件 ANR 两处修复：KernelHostImpl.requestRedraw 改为可注入回调并按 
endering 标记只在非渲染期转发，消除无限重绘；内核创建与绑定从 drawWidget 移入 loadData 的 IO 线程。
- 设备初始状态处置：两台设备均执行 db shell cmd package set-home-activity com.shuiyu.octoberrain/.MainActivity 与 db shell appops set com.shuiyu.octoberrain SYSTEM_ALERT_WINDOW allow，
esolve-activity 与 ppops get 复核通过。
- 新增 scripts/check-devices.ps1 走查脚本，按屏幕宽高比例换算坐标，一次导出十五屏并生成 index.md。
- 门禁：gradle --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease 退出码 0，125 套件 1097 用例 0 失败 2 跳过。
- 产物：pp/build/outputs/apk/release/app-release.apk 761821 字节，SHA-256 c8cbf71683deaf0e1837abfae40926d9ce2366e04e9325b316aac574c0b0b0b4；pp/build/outputs/apk/debug/app-debug.apk 4285847 字节，SHA-256 1cfeff9e8fd03bdca85c1dcd0eca82a855c74381dd57abe4775ca57d5e16c018。
- 设备验证：S4（480×800，219 dpi）与 P4（552×768，210 dpi）均装 1.0.7 实测，负一屏墨台五分区抬头与导入按钮在两种尺寸上都留有内边距，内置页四页签与紧凑周期行无裁切，负一屏连续两轮进出并滚动后 logcat 中无 ANR。
- 已知状态：card_pool_mask 尺寸档与 switch_mode 切换动画在内核 k5 卡片上没有对应绘制元素，配置可写但呈现位置在墨单页；署名与榜单范围两项在 k5 卡片同样无绘制元素，属内核渲染偏差，记入偏差清单。
- 目录快照 D:\DSHStation\_snapshots\OctoberLauncher-1.0.7，清单摘要 `866b8938b6049c684a095663ba29c03c20d8aa60780bfe5386273f36a55b9c2d`。
## 1.0.6

versionCode 26，versionName 1.0.6，2026-10-20。

- 设置首页新增「微读墨记」卡片，作为内置页面的主入口；桌面偏好页新增同名分组作为次入口。此前入口只挂在桌面偏好页底部，用户从设置首页无法到达。
- 内置页顶部四段页签改为常显，实验室页签不再隐藏，内容仍由设置页连点版本号五次展开，连点窗口由三秒放宽到八秒以适配墨水屏刷新。
- 内置页页头增加统一内边距，周期标签改为紧凑 ISO 周号与月份（如 2026-W43），「刷新」不再被屏幕右缘裁切。
- 本记页筛选由直接切换改为弹出「全部 / 只看想法」菜单；选书浮层首屏限制八十行并提示关键字过滤，消除书目较多时的输入超时。
- 周期选择器可切换范围改为解析 stats.json 的 d_<mode>:<base> 键。内核 KernelStore.listText() 列出的是目录文件而非偏好键，原实现扫不到已缓存周期，导致左箭头始终禁用。
- 洞察页改为按偏移量调用内核渲染器整页绘制，修复滚动后空白；排行分区支持分区内滚动。
- 负一屏分区抬头与空态引导增加左右内边距，由 DeskSectionStyle.paddingXPx 统一计算；设置态模块行的上移与下移按钮改为半间距，模块名改为单行省略，消除窄屏上右侧箭头被挤出。
- 新增 docs/acceptance/26-device-adaptation-report.md，记录 P4（552×768）与 S4（480×800）的双设备检查、七项问题、反思对照与处置。
- 发布构建的模块反射入口补上 WereadAppModule 保留规则。该类以类名字符串经 Class.forName 装配，R8 重命名后发布包进入内置页只显示「页面模块尚未接入」；同时保留内部类名便于日志对照。
- 门禁：gradle --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease 退出码 0，126 套件 1100 用例 0 失败 2 跳过；`app-release.apk` 758041 字节，SHA-256 `89a4c0c6545ecdc93b61f96347fcd9060dc2c76656d1f24b9be0f27596ce3968`；`app-debug.apk` 4406511 字节，SHA-256 `2711c8eb627eb8e658b8301018972d3005b8fa6fc944afd787400840757df641`。
- 真机验证：S4 上设置首页卡片进入内置页，四页签常显、周期切换与长按跳年、本记筛选菜单、选书过滤、导出长图、洞察滚动、待办增删改查均正常；P4 上设置首页入口与负一屏留白正常。
- 已知状态：纸色与字号使用自定义偏好键、成就目标未按 achv 分组写入，待与微读墨记侧确认；周期选择器只能切到本地已缓冲的周期。
- 目录快照 D:\DSHStation\_snapshots\OctoberLauncher-1.0.6，清单摘要 `d829e568ebbd2a856617e25d22f9376db03d4cccb52254a7475b5f78e9b54547`。

## 1.0.5

versionCode 25，versionName 1.0.5，2026-10-20。

- 卡片交互内化：抬头热区拆为形态切换与更新于两块，待办形态带抬头且可切回本周；九类热区统一经 hitRegions 与 hitRegionAt 按坐标派发，长按抬头弹出隐藏菜单；卡片抬头「打开墨台」按钮的绘制与命中移除，正文点击按形态打开对应页面。
- 负一屏墨台化：取消卡片外壳与全部详情页跳转，改为墨台列表态五分区（墨单、阅读资产、阅读指纹、书架、最近图片），顶栏三单位高含设置、标题、更新于与返回；设置态提供模块开关与排序、阅读资产过滤、阅读指纹选书与字号；书架与最近图片点击在页内浮层展开。
- 设置内微读墨记模块：新增 feature/weread-app，设置页「微读墨记」入口进入全屏页面，主容器四页签（阅读、设置、实验室、待办），阅读页四段页签与周期选择器、五档下拉、整卡全屏绘制，设置页三组表单，本记页筛选与选书与导出，洞察页四节，实验室页连点版本号五次展开。
- 待办数据层：WereadTodoStore 读写 kernel_prefs.json 的 todo_list 与 todo_next_id，WereadTodoHost 接口与 HostWereadTodos 装配，卡片待办勾选与待办页共用一份数据；单测十九用例。
- 内核 k5：libs/weread-kernel-1.3.2-k5.jar，215433 字节，SHA-256 4db239c41947cae41beeeea62a617687f79a24a0b9e5198f1052805a6884fb6，79 class，Kernel-Api-Version 5；新增洞察页渲染、卡片全屏档字号档、待办条目命中区间与 DeskStyle 版式常量。
- 门禁：gradle --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease 退出码 0，126 个测试套件 1100 个用例 0 失败 2 跳过；发布包 app-release.apk 737201 字节，SHA-256 963d5a1e521cdd5da95f279a8cd3fd62cbfb1a91d47efc4b736d72147b68a46；调试包 app-debug.apk 4269855 字节，SHA-256 8e1868eb3d8a3249791036969bbd3f1a441eed30e9c6c4d06a4a81f87e00748c。
- 目录快照 D:\DSHStation\_snapshots\OctoberLauncher-1.0.5，含 4080 个文件的 SHA-256 清单，清单摘要 d826f018259d6ab0a07f314707e661269a63fec5eb7c444f8aad79d6038af10e。
## 1.0.4

versionCode 24，versionName 1.0.4，2026-10-20。

独立承载微读墨记数据，负一屏墨台三模块改为页内直接交互，桌面卡片全部改用内核排版渲染。

- 独立同步（W5）：新增 `core/weread-sync` 模块，`WereadStore` 把网关响应写成与微读原盘同构的目录（`shelf.json`、`book.json`、`stats.json`、`bills.json`、`assets.json`、`nt_index.json`、`nt_<id>.json`、`kernel_prefs.json`），`WereadGateway` 负责 `POST https://i.weread.qq.com/api/agent/gateway`（`skill_version 1.0.5`），`WereadSyncService` 按书架、统计、账单、资产、笔记的顺序同步并支持周期起点计算与并发互斥；设置页新增「数据来源」三项（迁移导入、独立同步、仅本地缓存）与「立即同步」按钮，启动时按缓存时长自动同步。
- 卡片内补划线：网关书架响应不带逐书时长，统计的阅读榜单只在 `readLongest` 中给出前十至二十本；同步按 `longestByBook` 合并时长，「本书」卡片在有阅读记录且未读完的书里取时长最大的一本，`readingSec` 与 `book.json` 均由统计补齐。
- 划线与本记：新增 `/user/notebooks` 概览与 `/book/bookmarklist` 逐本划线合并，落盘 `nt_<bookId>.json` 保留原始条目的 `book` 子对象与 `chapterTitles` 并附 `marks` 数组；组件本记形态与负一屏阅读指纹因此读到真实划线原文。
- 负一屏重构（W2、W3、W4）：墨单、阅读资产、阅读指纹三个模块改为内核 `DeskPage` 分区渲染，触摸由 `DeskTouchDispatcher` 状态机处理（`IDLE`、`PRESSED`、`SCROLLING`、`KEYPAD`、`LONG_CONSUMED`），550 毫秒长按直接执行，纵向拖动超阈值转内滚；分区渲染结果缓存为位图，`height(width)` 在后台线程测量，消除导入数据后滑动触发的输入超时；三模块的「详情」入口改为「壁纸、选书、设置」，不再跳转详情页。
- 墨台壁纸：新增 `WereadWallpaperDialog` 与 `WereadWallpaperStore`，内核 `renderWallpaper` 生成 1080×1920 海报，预览后「保存到相册」写 `Pictures/TenRain/tenrain-desk-<时间戳>.png` 并通知媒体扫描，「分享」写缓存后走系统分享。
- 内核升级：`libs/weread-kernel-1.3.2-k4.jar` 186818 字节，SHA-256 `edc65b121224c5e146d2fe7bf7479d77cc59109934e8ef89cf0a8f721656d0b7`，64 个 class，`Kernel-Api-Version: 4`；新增 `BillSheet.fromStats`、`BillSheet.buildBillSheet`、`AssetWriter.writeAssetsFromShelf` 与 `KernelDataLoader.assets` 的书架合成分支。
- 门禁数据：`gradle --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease` 退出码 0，122 个测试套件 1078 个用例 0 失败 2 跳过，ktlintCheck 与 detekt 全模块零告警；调试包 4155131 字节，SHA-256 `eabedd727887295df6a58187cea18172608a98071ab2f0fd9c6fca970d46babe`；发布包 713009 字节，SHA-256 `2b24d914d2db4e135802d44dd8a15e6f037abf8eb6511b5ff15b02e186226c72`。
- 真机验证：S4 装发布包后，未安装微读墨记的状态下录入密钥即可同步，负一屏墨单显示周结与逐条书目、阅读资产显示 20 本与合计金额、阅读指纹按真实划线生成文字指纹；保存壁纸生成 `tenrain-desk-20261020-171806.png` 191553 字节；截图存 `_analysis/verify/20261020-w5/`。
- 已知状态：独立同步模式下书目的阅读进度依赖网关返回，网关书架接口不提供 `progress`，因此「本书」进度可能显示 0%；书籍进度与手动价单需要用户导入或补录后才完整。
- 目录快照：`D:\DSHStation\_snapshots\OctoberLauncher-1.0.4`，含 3952 个文件的 SHA-256 清单，清单摘要 `2495440c7d63344f47b7908728b577cd99da73f3ff84c0021286e317a60f920b`。摘要值在快照生成后补写，与快照内本文件相差本行内容。

## 1.0.3

versionCode 23，versionName 1.0.3，2026-10-20。

修复 1.0.2 真机反馈的三处缺陷。

- 书封显示：新增 `core/render` 的 `WereadCoverStore`，按书目标识缓存封面到 `files/weread_covers`，组件「本书」形态经 `KernelHostImpl.coverBitmap` 取图，负一屏书架卡片经 `ContentUi.decodeCover` 取图，两处共用同一缓存；原先两处均只显示书名首字。
- 待办空态：`KernelCardFiller` 的占位文案按形态区分，待办形态无数据时显示「暂无待办」与「下拉刷新或稍后再试」，已配置密钥时不再提示填写密钥。
- 卡片互动：待办形态正文区点击由无响应改为打开待办页，本记与本书同理打开对应页面。
- 独立承载方案：新增 `docs/design/22-weread-standalone-plan.md`，给出不安装微读墨记时由十雨独立提供取数、存储与渲染的模块划分与五阶段计划。
- 门禁数据：`gradle --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease` 退出码 0，118 个测试套件 1054 个用例 0 失败 2 跳过，ktlintCheck 与 detekt 全模块零告警；调试包 4077023 字节，SHA-256 `49dbb907a4ddd62f9d2e053504bed5e82b51d6cf61bf661a6dec7eebb68dc5a8`；发布包 679429 字节，SHA-256 `3c8a2507617d36134681b90146c38e839e17ceacd4d36a454200f9272ec028dd`。
- 真机验证：S4 装发布包后，负一屏书架卡片与组件「本书」形态均显示书封，待办形态显示「暂无待办」与「下拉刷新或稍后再试」，点击待办正文进入待办页；截图存 `_analysis/verify/20261020-135930/`，文件名为 `rel-1.0.3-` 前缀；启动日志无 FATAL EXCEPTION 与 ANR。
- 目录快照：D:\DSHStation\_snapshots\OctoberLauncher-1.0.3，清单摘要 `3aa249045a278c1fca877da8e8f1a4ebcddc5cdc602a10a758f7372f2bbcde1a`。


## 1.0.2

versionCode 22，versionName 1.0.2，2026-10-20。

与微读墨记 1.3.2-beta 同步的第二版，主题是把微读墨记的桌面卡片与墨台模块按原版排版一比一纳入十雨桌面，不再自带一套简化渲染。整改方案见 `docs/design/21-weread-native-adaptation.md`。

- 组件一比一（P0、P1）：`WereadBoardWidget` 删除十二个自绘方法与数据装配，由 738 行降至 380 行，绘制只剩内核 `render` 一条路径；`KernelCardFiller` 补齐 `CardState` 三十二个 setter 中缺失的十九项（`setNoteHint`、`setAchievementText`、`setNoteCardSize`、`setNoteFullSize`、`setMonthHeatmap`、`setNoteSlot`、`setCoverBitmap`、`setErrorText`、`setEmptyNote`、`setMonthScrollMax`、`setNoteScrollY`、`setNoteLineH`、`setNoteViewH`、`setNoteContentH`、`setNoteScrollMax`、`setDeskEnabled`、`setDeskOpenLabel`）；交互按原版 `CardInteraction.java` 复刻，触摸序列经 `WidgetTouchAction` 与 `WidgetTouchTarget` 传入卡片，长按回组件选择器。
- 墨台渲染入内核（P2）：微读侧新增 `kernel.desk` 的 `DeskHost`、`DeskPage`、`DeskRenderer`、`DeskModules`、`DeskPrefs` 与账单、阅读资产、阅读指纹三个分区，另新增 `kernel.load.KernelDataLoader` 与 `KernelDataSource` 直接读原盘文件；产出 `weread-kernel-1.3.2-k2.jar`，163494 字节，SHA-256 `a9855a4fb493a1b3c86f3c941aa46cacc7715557a5267b8b2155cf3a3b260241`，57 个 class，`Kernel-Api-Version: 2`，两次构建字节一致；`CardView` 与 `CardState` 补齐 `setMonthScrollY`、`setMonthScrollMax`、`setNoteScrollY`、`setNoteLineH`、`setNoteViewH`、`setNoteContentH`、`setNoteScrollMax`、`setFullscreen`、`setNoteExpanded` 九个状态写回方法，月历滚动不再依赖反射。
- 负一屏改分区渲染（P3）：账单、阅读资产、阅读指纹三个模块改为逐模块一个 `DeskPage` 的 `WereadDeskSectionView`，跳过墨台顶栏直接绘制分区，命中、动作、释放均走内核；十三项墨单外观经 `WereadDeskMenu` 映射到内核键；默认模块改为书架、最近图片、墨单、阅读资产四项，与微读 `DEFAULT_ON_MASK` 一致，阅读指纹默认关闭。
- 配置与迁移（P4b）：`LauncherConfig` 增 `WereadDeskConfig` 与 `WereadMenuConfig`，配置版本升至 9；迁移把旧模块标识 `weread_bill`、`weread_rank`、`weread_fingerprint`、`weread_assets` 改写为 `bill`、`rank`、`fp`，用户保留 v8 默认五项未改动时回落 v9 默认四项。
- 取数同源（P4b）：组件与负一屏的数据源改为原盘优先，`feature/content` 新增 `KernelWereadReader` 读 `filesDir/weread` 下书架、统计、账单、资产、笔记与内核偏好，`KernelDataSourceImpl` 先走内核读取器、失败或为空时回退网络接口，账单与资产不再恒为空态。
- 回写与共存（P4a）：`WereadPrefsWriter` 与 `HostWereadWritePrefs` 把卡片形态、墨台排序与开关、十三项墨单外观经 `WRITE_PREFS` 动作写回微读偏好；微读侧静默策略改为共存模式，装十雨且 versionCode 不低于 21 时保留桌面图标与数据刷新，只停用无障碍服务避免重复渲染。
- 迁移与回写链路修复：`HostWereadImport` 的 `target_uri` 改为字符串并按 `grantUriPermissions` 授予读权限，导出桥由「缺少 target_uri」失败改为成功导入，`filesDir/weread` 落位原盘十二类文件；`WereadMigrationCoordinator` 把资产书目包装成 `asset_books` 键，阅读资产分区由空态变为完整渲染。
- 负一屏性能修复：`WereadDeskSectionView` 的页面绑定、排版核算与分区渲染全部移出主线程并加位图缓存，`ContentUi` 封面解码改为单线程后台加载，快速滚动不再触发 `Input dispatching timed out`。
- 门禁数据：`gradle --offline testDebugUnitTest check :app:assembleDebug :app:assembleRelease` 退出码 0，118 个测试套件 1054 个用例 0 失败 2 跳过，ktlintCheck 与 detekt 全模块零告警；调试包 6804313 字节，SHA-256 `efeae1f208ca3233c916c0ab4931e5595adc2a4dea737c3f7c1a43dc2d542878`；发布包 677713 字节，SHA-256 `80a11acae7970ad938e6314c1f8a549184b3717a8ca2eb79a9c46354362ae754`。
- 真机验证：S4 装发布包后组件五种形态齐全（本周、本月、本书、本记、待办），点击抬头循环切换，长按约四百毫秒进组件选择器；负一屏导入微读墨记数据后，墨单显示周结与逐条书目、阅读资产显示一百一十九本与合计金额，均为内核按原版排版绘制；截图存 `_analysis/verify/20261020-135930/`；启动日志无 FATAL EXCEPTION 与 ANR。
- 遗留：本记卡片只展示最新一条摘录，卡片内笔记切换待数据层补多笔记列表；阅读指纹分区需在负一屏设置开启并选书，未选书时显示导入引导；书籍封面依赖网络地址，原盘无封面缓存时显示书名首字占位；微读候选包在最终 k2 出来后重建为 641776 字节，SHA-256 `63084c009fd7d32a848f9c07f887b002de131f5a87c5406ea3588e0d72f1500b`。
- 目录快照：D:\DSHStation\_snapshots\OctoberLauncher-1.0.2，3733 个文件，清单摘要 `25d9c5dbce9a0de4ed10fc3ade251d0f2f55ad4baa58587462c06a293f7a9579`。

