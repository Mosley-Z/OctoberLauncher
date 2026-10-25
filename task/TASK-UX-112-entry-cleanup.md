# TASK-UX-112 设置重复入口清理

- 状态：READY_FOR_REVIEW
- 执行者：entry_cleanup；集成者：Codex root。
- 分支/工作树：master / D:\DSHStation\OctoberLauncher；保留全部已有未提交差异。
- 依赖/READY条件：用户授权入口清理；UX115负责整体验收。
- 文件范围：feature/settings、feature/weread-app、feature/widgets 的WidgetPickerScreen/ImageWidget/ImageWidgetSettings及帮助资产；不改共享契约、构建、设备、版本。
- 设计依据：[开发流程](../docs/design/35-development-workflow.md)、[需求对接](../docs/design/36-demand-handoff-contract.md)。

## 需求与证据回读

1. 最终入口：保留设置首页微读墨记和负一屏自己的设置；移除桌面偏好阅读与待办、桌面与布局的微读墨记/负一屏/组件与内容。
2. 控件位置：本周/本月/本书/本记卡片抬头不再显示或响应打开墨台。
3. 显示归属：桌面与布局只保留布局、图标文字、交互；负一屏内容仍由自身设置管理。
4. 字段/消费者：不新增或改持久化字段；保留桌面、组件和负一屏现有消费者。
5. 迁移：无配置迁移；保留旧值，不清用户图片或隐藏应用数据。
6. 验收触发：静态确认旧入口及无消费者辅助代码移除；root统一构建与真实入口设备验证。

## 执行与交接

- 已改文件：DesktopPrefsScreen、LauncherPrefsScreen、WeekCardView、CardInteraction、WereadReadingPage、WereadAppScreen、WereadMicroBridge、SettingsHomeScreen；删除WereadAppSpecs与无调用者的WereadCardView。
- 实际验证/证据：已读入口/流程/调用点与git status，构建及设备由root串行负责。
- 未覆盖/阻塞：未运行构建、格式化、ADB；等待集成验证。
- 后台进程/设备占用/恢复情况：无。
- 下一步/接收方：代码已交付root，统一格式/门禁和设备验收。
- 提交/合并/发布：未执行。

## 静态审计结论

- 设置首页仍是微读墨记唯一常规入口；负一屏自己的设置与其查看效果入口保留。
- 打开墨台通过WeekCardView.deskEnabled=false让内核停止绘制并清空命中矩形；回调和仅供该控件的桥接方法删除。
- 删除整个组件与内容后，隐藏应用管理迁至交互组、恢复本页默认独立置于页底；图片选择/清理与轮播间隔迁至组件库→图片组件设置。不删除用户数据或配置字段。
- WereadAppHost仍由洞察页使用，保留；NegativeSpecs仍由设置目录校验使用，保留。
- 尚未构建、格式化或操作设备；root须补最终APK入口可见性及本书选书/本记滚动回归。

- 集成修订：保留隐藏应用与恢复默认能力，避免删入口时丢唯一能力；设置首页提示去除不存在的实验室。

## 图片设置迁移增量

- 按root授权接手WidgetPickerScreen、ImageWidget、新ImageWidgetSettings及launcher-guide.md；未触碰其他组件或共享契约。
- 图片组件当前激活时，组件库显示“图片组件设置”按钮，提供选择/更换、清除、轮播间隔；首次选择组件仍立即返回，桌面空图片轻触仍直接选图。
- ImageWidget和设置按钮共用选择逻辑；复制至独立新文件、核实图片尺寸成功后才替换配置，取消和失败保留原路径。清除只删除当前配置指向且在应用两处图片目录内的单个文件，无目录遍历删除。
- 帮助更新隐藏应用的交互组路径、图片组件库设置路径，移除桌面与布局的负一屏书架旧捷径。
- 验证仍仅静态搜索与diff检查；统一格式、构建和最终APK图片选择/失败/清除/轮播由root执行。

## 重复跳转与死声明复查

- NegativeSpecs中剩余入口描述与SettingsCatalog负一屏组无真实界面消费者，已删除；扩展模块增删函数迁入NegativeSettingsLogic，原行为测试保留并重命名为NegativeSettingsLogicTest，移除两条旧入口描述测试。
- 继续追踪发现WereadInsightView自身也无调用者，真实阅读页实例化Java InsightPageView；因此删除WereadInsightView及仅由它消费的WereadAppHost，清除其中全部陈旧墨台跳转与文案。
- 真实本周/月/书/记仍使用WeekCardView，其deskEnabled=false且原onOpenDesk回调链已删除。没有修改真实Java洞察页的滚动、排行或数据行为。
- 未运行格式化、构建或设备；由root统一验证。

- root补查：负一屏自己的设置删除“查看效果→打开负一屏”自跳转组，返回即回原墨台，保留卡片开关/顺序的原生行式UI。

## 集成者最终交接

- 完整check/debug/release通过；双机本记/沉浸及入口、图片迁移实测范围见[统一验收](../docs/acceptance/2026-10-23-reading-desktop-visual-alignment.md)。
- 本批后台进程/设备占用/恢复情况：Gradle已结束、两机原测试字段/缓存恢复，最终release摘要均核对一致；无设备占用。
- 未覆盖/阻塞/解除条件：纯想法/混合与短文实体矩阵、长标题/空态全矩阵未穷举，微信读书真实LAN边界保留；不冒充全覆盖。
- 下一步/接收方：用户实际体验反馈与局部矩阵；无需重跑当前有效门禁。未提交/合并/发布。
