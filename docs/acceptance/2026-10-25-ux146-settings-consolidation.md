# UX146 设置合并验收与待选清单

- 验收者：Codex 主代理；来源为用户本轮六项要求。
- 任务：[UX146](../../task/TASK-UX-146-settings-consolidation.md)。在既有工作树上实施，保留接手时 423 项变更；未升版、提交、推送或发布。
- 改动范围：settings 的桌面偏好、内容页、锁屏、关于、更新和路由；widgets 的组件库路由；weread-app 删除锁屏页签与旧页面；宿主说明注释同步。
- 工具链：仓库 toolchain.path 对应 JDK17、Gradle8.11.1、Android35，离线构建。
- release：app/build/outputs/apk/release/app-release.apk，1.0.31/51，2217971 字节，SHA256 `364f855e0cc4e9bb6dce69e94c77af07e36945c0b40f4faf84bd7b6f646824ed`。
- debug：7243443 字节，SHA256 `ae0923c4541bedadc38aed69245823be1aa7d0c68656feb42be3ec6782175ff5`。
- 实体设备：P4 `P4L202610130006`，S4 `SA4B03CN2608081096X`；均覆盖安装上述最终 release，安装摘要匹配。

## 已落实

| 用户项 | 实际结果 | 证据与边界 |
| --- | --- | --- |
| 1 | 桌面偏好删除顶部品牌小字与底部保存说明；列表不再显示分组标题 | `_ui-out/ux146-s4-prefs`、`ux146-p4-prefs` |
| 2 | 统一为桌面偏好→锁屏密码，微读删除锁屏页签及旧实现；复用 AppServices.lock/config.lock/appearance，不迁移、清空密码或壁纸 | 保留启停、双次 PIN、清除确认、应急清除开关、熄屏自动锁定、立即锁定、壁纸选择/清除/灰阶；`ux146-s4-weread-custom`、`ux146-p4-lock-manual` |
| 2 | 补齐立即锁定，PIN 严格要求四个 ASCII 数字，保存与清除后核对真实状态 | P4 双次保存→立即锁定→正确测试 PIN 解锁→清除确认，`ux146-p4-pin-confirm2`、`ux146-p4-locked`、`ux146-p4-unlocked-success`、`ux146-p4-clear-confirm` |
| 3 | 删除关于页“应用”分组，桌面偏好删除关于入口；检查更新最底部单行“关于”打开剩余设备与许可证 | S4 `ux146-s4-update-bottom`、`ux146-s4-about` |
| 5 | 权限与备份页直接列权限、备份两行动作，删除天气及使用统计设置 | S4 `ux146-s4-permissions-backup`；既有兼容配置及其他消费者不自动删除 |
| 6 | 桌面组件直接进入同一组件库，删除中转页、组件选择解释与页尾重复说明；长按入口仍共用组件库 | S4 `ux146-s4-library`；ProjectWidgets 路由由 widgets provider 接管，不引入 feature 互相依赖 |

## 验证与恢复

- `_analysis/verify/ux146-20261025/verify.ps1` 执行受影响模块 ktlintFormat、全工程 check、assembleDebug/assembleRelease，最终退出码 0；gate.log 为 BUILD SUCCESSFUL。
- 最终每变体 1000 项 JVM 测试，0 失败、0 错误、1 项既有跳过；不作为实体机证据。summary.json 保存最终 APK 与测试汇总。
- 上表详细交互在本轮首个 debug 构建上执行；末次只调整清除密码的结果提示/状态核对及删除未使用的品牌标题字段，重新完整构建通过。最终 release 已安装匹配并进行入口复核；不把先前 debug 的密码操作冒称最终 release 上重测。
- P4 曾暂切竖屏以避免横屏逐键标注解析缺失，随后用稳定可解析的测试数字完成实际解锁；横屏立即锁定已真实触发。S4 本轮未重设密码；没有替换或清除两机原有锁屏壁纸，也未重测应急清除的十秒流程。
- 原始备份与初始化账号页观测存私有临时目录，不入任务正文或 Git。恢复只写本轮记录的六个锁屏字段和临时方向；未回滚阅读缓存或其他设置。
- restoration.json：两机全局配置与本轮原始备份差异均为 0，测试字段差异均为 0。恢复 P4 普通/横屏、S4 沉浸/竖屏及原组件；未新增休眠任务。
- 未修改额外候选说明、重复项或权限声明，以下均等待用户选择。

## 可选说明文字清理

| 编号 | 位置与现有内容 | 建议选择 |
| --- | --- | --- |
| A1 | 设置首页底部：从“书架设置位于负一屏设置”开始的整段入口导览 | 删除整段，卡片与列表已有名称 |
| A2 | 桌面与布局底部：“自动档位按屏幕尺寸反算，布局改动在返回桌面后生效。” | 只保留“返回桌面后生效”或整段删除 |
| A3 | 沉浸模式下：“沉浸固定本书，组件由组件库选择，宿主不提供滑动切换。” | 删除重复说明 |
| A4 | 外观与个性化的“字体文件导入后保存在应用私有目录”“字号缩放会整体重建界面”等实现描述 | 删除实现部分；保留字号/灰阶等选择的实际效果说明 |
| A5 | 备份与恢复底部：“恢复操作在后台线程读写文件，完成后立即刷新外观与界面。” | 删除这句，保留“备份包含哪些内容”和覆盖恢复提醒 |
| A6 | 权限页底部：“授权状态在页面恢复时刷新；从系统设置返回后再次进入本页可看到最新结果。” | 删除内部刷新叙述；如保留权限页，提供准确的状态刷新行为 |
| A7 | 关于底部：“本应用为墨水屏阅读设备定制，界面按纸面阅读场景设计” | 删除定位宣传；安装授权提示只留在检查更新页 |
| A8 | 时钟卡片反复出现“时间与日期，长按可切换组件”等描述 | 时钟卡仅留样式名，其他组件保留能区分用途的简介 |
| A9 | 锁屏“行为”下关于摘要存储的技术说明及“连续输入错误不会锁死设备，返回桌面重新进入即可” | 改为实际锁屏限制/解锁提示；后半句与现有 PinLockPolicy 退避行为不一致，不建议原样保留 |

上述证据分别位于 SettingsHomeScreen、DesktopPrefsScreen、DesktopModeSettings、AppearanceSpecs、BackupScreen、PermissionsScreen、AboutScreen、WidgetCatalog、LockSpecs；尚未删除。

## 可选重复项整理

| 编号 | 重复或接近的入口 | 核对结果与建议 |
| --- | --- | --- |
| B1 | 微读墨记→设置→初始化中的桌面版本和检查更新；设置首页→检查更新 | 同一 Destination.Update，版本也同属桌面。建议删除初始化页该整块，统一从设置首页更新 |
| B2 | 外观与个性化→主题模板；美化与图标→主题库/保存主题 | 名称近似但不是同一数据服务：前者 AppServices.appearance 的配色/外观主题，后者 AppServices.themes 的桌面布局/图标方案。建议先分别命名“外观配色”“桌面方案”，不直接删除其一 |
| B3 | 外观主题页→导入主题文件；备份与恢复→恢复主题 | 普通主题文件最终都调用 appearance.importTheme；备份入口还额外支持 ZIP 只恢复外观。建议收拢主题文件导入入口，并保留 ZIP 局部恢复能力 |

锁屏壁纸和状态栏高度虽然在 AppearanceSpecs 声明，但外观页明确过滤 project.* 分组，实际分别只在锁屏页/状态栏页显示；没有把声明复用误判为可见重复。

## 权限必要性审计（现状，不自动删权限）

| 编号/权限 | 当前实际消费者 | 结论与建议 |
| --- | --- | --- |
| P1 通知读取 | NotificationRelayService 读取其他应用通知，桌面通知与角标消费 | 按需保留；不使用通知/角标时不必授权 |
| P2 无障碍 | OverlayAccessibilityService 的 TYPE_ACCESSIBILITY_OVERLAY 与按键回调 | 外部应用上的悬浮状态栏/音量键翻转按需需要；基础桌面和 PIN 锁屏不需要 |
| P3 存储/所有文件 | ContentPaths、文件浏览、目录扫描、本地书籍路径访问 | 文件管理与扫描按需保留；系统文件选择器导入单个文件不等于必须授权所有文件。建议改成准确用途提示 |
| P4 使用情况访问 | RecentsRepository 的 queryEvents/queryUsageStats，“最近应用”仍使用 | 保留为最近应用的可选权限；删除使用统计设置不等于可删除这个权限 |
| P5 悬浮窗 | 当前仅采集 canDrawOverlays 状态，未发现 TYPE_APPLICATION_OVERLAY 消费者；悬浮服务用无障碍层，锁屏是 Activity 内 View | 建议删除此权限行及无实际用途的声明；现有“悬浮球与锁屏覆盖层需要”提示不符合实现 |
| P6 应用通知 | 仅状态查询和未使用的申请方法，未发现当前应用主动发布系统通知的 notify/startForeground 调用 | 当前功能未找到用途，建议删除此权限行与声明；它不等于读取其他应用通知的授权 |
| P7 精确闹钟 | 仅 canScheduleExactAlarms 状态查询，无 setExact 等调度调用，也无对应特殊权限声明 | 当前不需要，建议删除此权限行；现有“定时刷新与提醒需要”提示无实际调用依据 |
| P8 电池优化（系统入口） | 影响后台悬浮服务等存活，由系统决定 | 属于排障/后台行为选项，不是桌面运行必需权限；建议按需显示 |

平台依据：[悬浮层类型](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)、[通知发送权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)、[所有文件访问](https://developer.android.com/training/data-storage/manage-all-files)、[精确闹钟](https://developer.android.com/develop/background-work/services/alarms)。项目必要性是结合当前源码调用得出的结论，并非平台对本应用功能的保证。

没有发现所有列出权限都必须授予的依据。P5–P7 本轮仅形成候选，不自动撤销用户授权或改 Manifest；如选定删除，再同步移除权限页条目、计数和无消费者实现。
