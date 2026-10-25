# TASK-UX-132 删除KOReader统计及专属集成代码

- 状态：READY_FOR_REVIEW
- 优先级/阶段：实现及本轮统一验证已交付；保留明确未覆盖项
- 执行者/集成者：Sol root集成；分工见UX120
- 分支/工作树/源码基线：master / D:\DSHStation\OctoberLauncher / f025b18及既有未提交差异；开工重查
- 依赖：UX121权限能力明确；公共契约/配置/路由由UX120主代理串行；UX129在本卡后移除分类残留。
- READY条件：用户2026-10-23第三轮“删除KOReader统计及相关代码，其他按建议落地TASK”；按UX120交接分派，不重复请求方案确认
- 设计依据：[已确认修订方案](../design/ux-corrections-proposal/revision-2.md)、[集成卡](TASK-UX-120-correction-integration.md)
- 允许修改：feature/content KOReader仓库/页面/探测、ContentSourceImpl/Module；core/common专属契约与模型、core/data配置迁移/主题/应用入口、home/settings/widgets相关引用、专属测试/过期baseline；文档由主代理另派。
- 共享入口/禁止范围：保留其他人改动；公共契约/配置/路由/看板由主代理串行。代码代理禁写文档库、根文档、memory和权限配置，文档建议交主代理；不升版/提交/发布。

## 需求与证据回读

1. 最终入口：用户第新增删除项，按下列闭环保留/移除原入口。
2. 控件位置：按已确认revision-2方案；纯删除无替代入口，横屏视觉不改竖屏。
3. 显示归属：十雨自有界面/能力，Android系统原设置不改；无扩张到外部应用数据。
4. 字段/消费者：以下实施清单逐链核对；共享点由集成者负责。
5. 迁移：退役koreaderEnabled与special/widget旧引用；兼容解析可保留最小识别/过滤代码和迁移夹具，不保留运行统计功能。仅清本应用已证实独占且无消费者的缓存，禁止删除外部数据。
6. 验收触发：源码检索无运行KOReader统计读取/注册；剩余匹配只允许退役迁移/历史资料并说明。旧配置/旧主题/备份含退役字段与当前组件的加载/导入/冷启；无悬空图标/选择项；微信读书快照/本地书架/普通应用发现回归；完整双变体门禁。

## 目标、实施步骤与交付物

- 删除十雨内置KOReader统计设置、专属统计页面/入口、热力数据能力及读取统计/书目/元数据的专属仓库与目录探测。不是仅隐藏开关。
- 已定位KoreaderRepository.kt、KoreaderScreen.kt、ContentPaths.koreaderRoot、ContentSourceImpl实例/entries/stats/heat/缓存失效，ContentModule注册与LauncherRouter空实现。
- readingSnapshot目前优先koreader.readingSnapshot()再回退weread；删除KOReader分支后直接沿用现有微信读书可用性规则，无数据返回原空态，不新增替代统计。
- 串行删除Destination.Koreader、SpecialEntry.KOREADER、KoreaderHeat、ContentSource.koreaderHeat及仅此用途类型；清DesktopEntries/常用应用候选/AppCatalog/IconTargets/主题允许列表。ReadingSnapshot等共享模型按剩余消费者保留。
- 检查koreaderEnabled、special:koreader、koreader_month在旧配置/主题/备份中的残留；只移除该专属入口/组件引用，保留其他顺序和值。被删项当前选中时回退现有默认有效组件；轮换/收藏过滤后不足按既有默认规则。
- 旧版JSON能加载并导入，失效枚举值不得让整份配置报错或恢复全部默认；迁移幂等、正常加载与导入同源。koreader_month目前有默认/测试引用，不能假设存在完整renderer；逐项核实后清专属残留。
- 移除仅被删除代码使用的依赖、测试与baseline条目，不误删共享SQLite/文件/书架能力。更新现行帮助/模块架构描述由主代理采纳后派文档代理；历史CHANGELOG、历史验收不伪改。
- 删除范围是本项目集成，不卸载用户外部KOReader App、不删除其外部目录/书籍/数据库；已安装KOReader仍可作为普通Android应用被发现和启动。不清全量用户偏好。

## 验收标准

源码检索无运行KOReader统计读取/注册；剩余匹配只允许退役迁移/历史资料并说明。旧配置/旧主题/备份含退役字段与当前组件的加载/导入/冷启；无悬空图标/选择项；微信读书快照/本地书架/普通应用发现回归；完整双变体门禁。

相关模块测试及最终check/debug/release由UX120唯一构建负责人安排；设备按scripts/android-ui.ps1操作，每个serial独占。只读分析与旧包观察不当最终验收。

## 执行与交接

- 已改文件/接口决定：专属仓库、页面、契约、能力、入口已删除；v15清理旧目标/组件；备份主题写入后二次清理。
- 验证命令及结果/证据：全部实现齐备后统一check/debug/release通过；删除标题隐藏后的最终包见[本批验收](../docs/acceptance/2026-10-23-ux120-corrections.md)，工程与设备包版本分别标明。
- 实际覆盖：专属仓库/页面/入口/契约删除，退役迁移/主题导入兼容测试通过；P4内容设置无KOReader专属开关。
- 未覆盖/阻塞/解除条件：未在设备导入所有历史备份或验证外部KOReader应用/文件；本次不删除外部应用和数据。
- 后台进程/设备占用/恢复情况：工程进程已结束；S4/P4均恢复原状态并释放，无设备占用。
- 下一步：实现与本轮验证已交付；后续只续测上述未覆盖项，不重复已有效门禁。没有待审批方案或代码阻塞。
- 提交/合并/发布：未执行；无升版，保留既有工作区差异。

## 实现交付（统一测试前）

- 实现负责人：Sol root审核集成；代码代理只提交暂存建议，文档由专职角色按采纳清单写入。
- 交付：专属仓库、页面、契约、能力、入口已删除；v15清理旧目标/组件；备份主题写入后二次清理。
- 全系列实现先交付再统一验证；READY_FOR_REVIEW表示实现及本轮验证交付，未覆盖项仍可追溯。
