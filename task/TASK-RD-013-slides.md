# TASK-RD-013 PPTX单页预览与提纲

- 状态：READY_FOR_REVIEW（7d8a680已集成，最终核心与477包200有效翻页通过）
- 优先级/阶段：P1 / R4
- 执行者/集成者：slides子智能体 / Codex根集成者
- 分支/工作树/源码基线：master / D:/DSHStation/OctoberLauncher / 0a1534c；保留已有修改
- 依赖：[004](TASK-RD-004-reading-state.md)、[007](TASK-RD-007-web-host.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/office/slides、专属assets与样本；依赖、许可及引擎注册交集成者。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

PPTX按幻灯片阅读，并能查看提纲及搜索文字。

## 实施步骤与交付

1. 冻结PPTXjs完整资源链，核对传递JS/CSS/字体，不通过公网加载。
2. 按页挂载与卸载，支持跳页、页Locator、无动画翻页和文字提纲。
3. 实现SearchProvider与命中定位，对无法绘制的图表/EMF/WMF等给占位说明。
4. 处理超限、坏包和旧PPT，宏不执行；旧PPT转换建议不算内置支持。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [ ] 含中文、图片、表格和不支持图形的样本在WebView83实测，缺失对象可识别。
- [ ] 200页级样本按预算处理，不常驻全部页DOM；进度重启恢复和提纲搜索可用。

- [ ] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [ ] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 需求回读

- 最终入口：文件/外部统一阅读器，PPTX由根集成者注册SlideEngine；旧PPT仍提示外部转换。
- 控件位置：沿用阅读器翻页/跳页/书签/搜索；工具栏增加原版式与提纲选择，根接线。
- 显示归属：原版式只挂当前页DOM；文字提纲为本页可提取文本，明确复杂对象缺失。
- 状态字段与消费者：Slide(slide,outline,textOffset,fraction)由引擎产出，既有repository保存并供重进/书签消费。
- 迁移：新增slide类型默认值，不更改旧locator或数据库；关闭丢弃单页包，zip由引擎关闭。
- 验收触发：3页混合样本、200页末页/跨页搜索/模式切换/强停恢复；根构建与设备执行，子智能体不占Gradle/ADB。

## 执行与接力记录

- 开始/最近更新：2026-10-23；根已授权RD013并确认冻结Office契约。
- 已改文件/接口决定：office/slides/四个Kotlin文件、专属test、assets/reader/web/slides/原样9项资源及host；create(source,locator,font)、slideCount、outline()（切模式由宿主copy locator.outline）。单页关系闭包重打包，不把整200页传JSZip；无动画，宏/外部关系不执行。host已补受控检索mark与scrollTop比例恢复/180ms用户滚动保存、固定复杂对象提示；公共fraction消息由根接线。
- 验证命令及结果/证据：node --check slides-host.js退出0；9资源摘要与probe vendor-manifest相符；生成mixed.pptx与deck-200.pptx成功；JVM解析/页序/路径/节点/200页包5例交根运行。证据_analysis/verify/slides-013/。
- 未覆盖/阻塞/解除条件：根已完成Office模块门禁/双构建、reader113例0失败；S4真实Office版首轮验证完成，见2026-10-23-reader-office-s4验收。P4由另一子智能体验证；RD014新搜索版与真实复杂PPTX图形/字体仍待。
- 构建进程/设备占用/恢复：子智能体未运行Gradle；根授权独占S4后执行覆盖安装及测试，结束全刷仍6、尺寸480×800/219dpi、HOME并释放serial。未操作P4。
- 下一步：已按根RD014契约适配SearchProvider.scan/showHit、页内matchOrdinal和cancel；3项JVM检索测试交根执行，host6项ordinal测试与node语法通过。新统一搜索版需再门禁/设备复验；现Office APK验证不能冒充新统一搜索版。
- 接收方/集成点：Codex根；许可入口建议登记reader/web/slides/LICENSES.txt；vendor-manifest.json携带精确来源摘要。
- 提交/合并/发布：子智能体未提交/升版/发布；根统一管理。

### 最终集成补验（2026-10-23）

7d8a680全仓check/debug/release通过，reader各132例0失败；S4最终477包完成PPTX跨run完整词、页内ordinal、200条精确统计及64/65双向窗口，200有效翻页逐次页号+标题断言通过（1→200再回199）。见[最终S4报告](../docs/acceptance/2026-10-23-reader-search-s4.md)。随后发现外部Entry任务复用，2da644b仅Manifest修affinity，阅读引擎/资源不变；fbd4包30次真实隐式开关与必要smoke单列，不冒充477历史压力。复杂真实图表/字体与物理EPD等留RD017。
