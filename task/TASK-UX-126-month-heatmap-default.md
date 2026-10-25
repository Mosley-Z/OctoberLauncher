# TASK-UX-126 本月缺省热力图与双端一致

- 状态：READY_FOR_REVIEW
- 优先级/阶段：实现及本轮统一验证已交付；保留明确未覆盖项
- 执行者/集成者：Sol root集成；分工见UX120
- 分支/工作树/源码基线：master / D:\DSHStation\OctoberLauncher / f025b18＋既有未提交差异；执行前重核
- 依赖：UX121明确执行权限；core/common改动由UX120集成者串行接入。
- READY条件：用户2026-10-23第二轮批准第8项先落TASK；取得执行分派后开工；跨文件所有权由UX120固定
- 设计依据：[修订方案](../design/ux-corrections-proposal/revision-2.md)、[前轮调查](../design/ux-corrections-proposal/proposal.md)；冲突以前者为准
- 允许修改：WereadAppPrefs、core/common/WereadAppearance及相关测试；WereadSettingsPage说明文字；禁止美化/重排竖屏组件。
- 共享入口/禁止范围：禁止覆盖既有差异；代码子代理不得写docs/根文档/memory/共享看板/权限配置，建议交主代理；任务记录由主代理维护。无版本、发布、系统设置修改。

## 目标与用户闭环
设置→微读墨记→设置→自定义→本月呈现，无显式选择时默认热力图，微读App与桌面本月卡一致。

## 需求与证据回读
1. 最终入口：原本月呈现入口，不新建设置页面。
2. 控件位置：原打卡/热力图选择保持，缺省选热力图。
3. 显示归属：App本月与桌面本月共享；只改默认语义，不改竖屏样式。
4. 字段/消费者：filesDir/weread/kernel_prefs.json中int.month_style；WereadAppPrefs、WereadAppearance→WereadReadingPage/WeekCardView及KernelWereadBoard/CardState。
5. 迁移：缺键默认1；显式0/1保留；无需全量偏好重写。不以值0推断用户从未选择。
6. 验收触发：新用户、旧缺键、显式0、显式1；改变其他自定义项并重启仍正确。

## 实施步骤与交付物
- 原CardPrefs默认已1；宿主settings/monthHeatmap/表单与WereadAppearance仍0，统一有效缺省。
- WereadAppPrefs.update会整表写回，必须全部默认路径一致，不能仅改单个UI初始值。
- View初始化false由配置覆盖，不把它当持久默认根源乱改。
- 说明文字改为“用于微读墨记与桌面卡片的本月月历”。
- 本卡明确允许显示模式默认变化；用户要求竖屏样式不变指不重绘版式，不否定本条已批准的缺省语义。

## 验收标准
- 原始缺键与显式值四路测试，改无关自定义项后保持；未知值沿现有容错规范。
- App与桌面本月同值，退出重进/进程重启；不访问凭据或重置用户正文数据。

## 执行与交接

- 已改文件/接口决定：WereadAppearance与WereadAppPrefs缺失month_style采用1，显式0/1保持；新增缺省兼容测试源码。
- 验证命令及结果/证据：全部实现齐备后统一check/debug/release通过；删除标题隐藏后的最终包见[本批验收](../docs/acceptance/2026-10-23-ux120-corrections.md)，工程与设备包版本分别标明。
- 实际覆盖：缺month_style及显式0/1自动测试通过；设备原热力图选择保持。
- 未覆盖/阻塞/解除条件：未清用户偏好模拟新用户，不宣称设备缺省迁移全矩阵通过。
- 后台进程/设备占用/恢复情况：工程进程已结束；S4/P4均恢复原状态并释放，无设备占用。
- 下一步：实现与本轮验证已交付；后续只续测上述未覆盖项，不重复已有效门禁。没有待审批方案或代码阻塞。
- 提交/合并/发布：未执行；无升版，保留既有工作区差异。

## 实现交付（统一测试前）

- 实现负责人：Sol root审核集成；代码代理只提交暂存建议，文档由专职角色按采纳清单写入。
- 交付：WereadAppearance与WereadAppPrefs缺失month_style采用1，显式0/1保持；新增缺省兼容测试源码。
- 全系列实现先交付再统一验证；READY_FOR_REVIEW表示实现及本轮验证交付，未覆盖项仍可追溯。
