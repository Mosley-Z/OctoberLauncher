# 十雨桌面 Agent 协作入口

适用范围：本仓库。用户本次及后续明确指令优先；历史方案不覆盖最新需求。

## 接手顺序

1. 读 `PROJECT.md`、`memory/STATUS.md`、`memory/MEMORY.md`。
2. 读分配给自己的 `task/TASK-*.md` 和它引用的设计；未分配时先看 `task/README.md`。
3. 代码修改前读 `docs/design/35-development-workflow.md`；按需查 `docs/design/33-dsh-development-playbook.md`。
4. 执行 `git status --short`，核实当前源码与工具链。不要默认历史版本、测试数量或设备连接仍有效。
5. 设备界面观测与点击用 `scripts/android-ui.ps1`，标准 View 走 `layout`/`find`/`click`，桌面自绘元素走 `annotate`/`resolve`/`tap -Label`；用法与限制见 `docs/design/33-dsh-development-playbook.md` §10。

执行跨卡整改前，在当前卡记录最终入口、控件位置、显示归属、状态字段与消费者、迁移和验收触发；见 [需求对接约定](docs/design/36-demand-handoff-contract.md)。不为这一步重复请求用户确认。

## 工作边界

- 任务卡是工作分解，不是新的用户授权；只有收到执行指令或授权范围内的分派才实施。已授权工作持续完成，不为普通可逆步骤反复确认。
- 保留现有修改，禁止为获得干净工作区而 reset、clean 或回滚其他人的工作。冲突先读差异再协调。
- 每张卡记录负责人、文件范围、依赖、证据和下一步。执行者负责自己的任务卡；集成者负责看板、共享入口和最终门禁。
- 共享契约、Manifest、模块注册、版本和发布入口由当批集成者串行修改。不能同时运行共享 Gradle 输出的构建或操作同一 ADB serial。
- 不把规划写成实现，不把 JVM 测试写成设备通过，不把历史验收当成当前版本验收。
- Kotlin + 原生 View，feature 不互相依赖，公共契约进 core:common。新依赖围绕任务必要性决策，记录版本、用途与兼容证据。
- 密钥、用户正文、PIN、偏好全量和设备备份不进任务、记忆、日志及 Git；记录技术字段、计数、尺寸与证据路径即可。

## 收尾与交接

按 `docs/design/35-development-workflow.md` 完成验证及交接。长任务在阶段完成、方向变化、等待外部条件和会话结束前更新自己的卡；共享状态由集成者汇总。不要等到上下文耗尽才沉淀。

稳定规则写 `memory/MEMORY.md`，当前状态写 `memory/STATUS.md`，本轮交接写 `memory/sessions/`，验收结论写 `docs/acceptance/`。每条结论附来源和验证边界，不自动注入或复制全部历史。
