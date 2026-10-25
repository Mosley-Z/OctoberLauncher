# 开发与协作

返回[文档导航](../README.md)。本目录导航现有生效文档，不搬移历史编号文件。

- [35 开发流程](../design/35-development-workflow.md)：职责、文件所有权、构建、验证、证据和交接。
- [36 需求对接约定](../design/36-demand-handoff-contract.md)：最终入口、显示归属、状态消费者、迁移及验收触发。
- [33 开发工作手册](../design/33-dsh-development-playbook.md)：工具、设备和性能方法；案例按原批次理解。
- [38 S4/P4性能参考](../design/38-device-performance-reference.md)：硬件事实、测量口径和预算边界。

独立代码代理通过 [run-project-agent.ps1](../../scripts/run-project-agent.ps1) 的 `-Role code` 启动，专职文档代理使用 `-Role docs`。入口显式加载 [agent-permissions.toml](../../scripts/agent-permissions.toml)，角色权限不作为自动项目配置影响主会话。

代码角色可写源码，文档、任务、记忆、根文档和策略保持只读；文档角色可写授权文档位置，源码和策略保持只读。实际允许与拒绝证据见 [UX121](../../task/TASK-UX-121-documentation-governance.md)。原生 spawn 继承父会话权限，本轮仅用于只读检查或提供暂存建议，由主代理审核应用，不将其称为硬隔离。
