# TASK-RD-007 WebView83离线宿主与资源边界

- 状态：DONE（本卡首轮；跨设备最终门禁见017）
- 优先级/阶段：P0 / R3
- 执行者/集成者：Codex（本聊天，串行集成）
- 分支/工作树/源码基线：master / 1379e8b + 已验证PERF与原生TEXT/PDF差异，保留全部修改
- 依赖：[002](TASK-RD-002-sources.md)、[003](TASK-RD-003-reader-shell.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/web、离线公共assets与测试；Gradle依赖由集成者合入。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

提供EPUB和Office可复用的单WebView宿主，不依赖公网或系统更新。

## 实施步骤与交付

1. 用000冻结的androidx.webkit建立AssetLoader HTTPS资源路由，未命中返回阻断，不回退网络。
2. 定义每会话文档资源URL、CSP、可信脚本与不可信内容边界；限制导航、file协议、下载和新窗口。
3. 实现固定消息协议及来源/会话校验，处理ready、Locator、翻页和错误；移除任意路径读写接口。
4. 仅保留一个活动WebView，退出销毁并处理onRenderProcessGone；输出每格式包装页接入约定和兼容探针。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] 断网不影响本地资源，远程图片/脚本请求不能穿透；宿主应用有INTERNET权限仍保持阅读页边界。
- [x] WebView83可收发会话消息，页面销毁旧消息失效；文档脚本不执行；异常后可返回并重开。

- [x] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [x] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 最近更新：2026-10-22；执行/集成者Codex，本聊天串行构建与ADB。
- 已实现：feature/reader/web、markdown、assets，ReaderRegistry/Screen和授权入口；原生PDF仍独立进程。
- 验证命令及结果/证据：全量check/debug/release及末轮reader:check/debug/release退出0；S4离线、重排、搜索/书签/恢复、私有URI授权、native renderer崩溃、R8宽表格通过。
- 证据与边界：[Web与Markdown验收](../docs/acceptance/2026-10-22-reader-web-markdown.md)。P4完整交互、压力测试、跨本地文档链接等未覆盖项逐条保留，不作全矩阵通过结论。
- 设备：S4最新release，HOME；无线状态恢复。P4仍为前一PDF release，只计安装启动。
- 下一步：RD008，后续010复用该宿主；017统一收敛设备门禁。
- 提交/合并/发布：未执行。
