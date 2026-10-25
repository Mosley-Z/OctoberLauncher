# 文档导航

新会话从 [PROJECT](../PROJECT.md) 与 [当前状态](../memory/STATUS.md) 接手，再按任务读文档。

| 目录 | 职责 |
| --- | --- |
| [architecture](architecture/README.md) | 现存模块职责、依赖、状态来源与边界；具体实现沿源码入口查找 |
| [development](development/README.md) | 生效的开发、协作、需求回读与设备操作流程 |
| [design](design/README.md) | 产品方案与规格；目标不自动等于已经实现 |
| [acceptance](acceptance/README.md) | 某一源码和设备范围内的验证证据，不作为架构更新记录 |
| [archive](archive/README.md) | 上游分析、历史规范和被替代提案的分类入口；原文保留 |
| [templates](templates/README.md) | 任务、验收和交接模板 |

任务进度归 [task](../task/README.md)，短期状态与接力归 [memory](../memory/STATUS.md)，改动历史由 Git 保存。架构文档只忠实描述现存架构，不堆逐函数解释、测试计数或轮次流水。

当前小组件批次：[升级方案](design/40-widget-upgrade-plan.md)，关联UX139–144及候选设计稿；已按任务分阶段实施，实际状态与验证边界见任务卡和验收。

代码实现代理不得修改文档库、任务卡、根文档、记忆或权限配置，只向主代理提交建议。主代理决定采纳后，另派拥有明确文档范围的文档代理写入。独立入口显式加载 [角色权限](../scripts/agent-permissions.toml)，不以自动项目配置影响主会话。原生 spawn 继承父权限，本轮只读检查或提供暂存建议。独立角色边界与实际验证证据见 [开发流程](design/35-development-workflow.md)及 [UX121](../task/TASK-UX-121-documentation-governance.md)。

已有文档不整批搬移；旧路径按以下映射定位。新材料使用仓库内相对链接，历史验收保持原结论，后续验证另建记录关联。文档检查由本批主代理在统一验证阶段运行。

## 2026-10-22 迁移映射

40份原顶层Markdown已移入两类目录。机器可读清单：[migration-2026-10-22.json](migration-2026-10-22.json)。旧聊天、历史日志和代码注释遇到原路径时以本表定位；不创建40份重复正文。

只读规格24保持原文与只读属性，其中旧引用依本映射定位。已有缺失的历史材料不由本次整理伪造补齐。

| 原路径 | 新位置 |
| --- | --- |
| `docs/00-overview.md` | [00-overview.md](<design/00-overview.md>) |
| `docs/01-feature-inventory.md` | [01-feature-inventory.md](<design/01-feature-inventory.md>) |
| `docs/02-current-architecture.md` | [02-current-architecture.md](<design/02-current-architecture.md>) |
| `docs/03-target-architecture.md` | [03-target-architecture.md](<design/03-target-architecture.md>) |
| `docs/04-identity-removal.md` | [04-identity-removal.md](<design/04-identity-removal.md>) |
| `docs/05-device-adaptation.md` | [05-device-adaptation.md](<design/05-device-adaptation.md>) |
| `docs/06-development-roadmap.md` | [06-development-roadmap.md](<design/06-development-roadmap.md>) |
| `docs/07-upstream-code-map.md` | [07-upstream-code-map.md](<design/07-upstream-code-map.md>) |
| `docs/08-engineering-conventions.md` | [08-engineering-conventions.md](<design/08-engineering-conventions.md>) |
| `docs/09-M0骨架记录.md` | [09-M0骨架记录.md](<acceptance/09-M0骨架记录.md>) |
| `docs/10-dev-handoff.md` | [10-dev-handoff.md](<design/10-dev-handoff.md>) |
| `docs/11-552x768-adaptation.md` | [11-552x768-adaptation.md](<design/11-552x768-adaptation.md>) |
| `docs/12-验收记录.md` | [12-验收记录.md](<acceptance/12-验收记录.md>) |
| `docs/13-inkOS对照评估.md` | [13-inkOS对照评估.md](<design/13-inkOS对照评估.md>) |
| `docs/14-本批实施记录.md` | [14-本批实施记录.md](<acceptance/14-本批实施记录.md>) |
| `docs/15-V1.0开发计划.md` | [15-V1.0开发计划.md](<design/15-V1.0开发计划.md>) |
| `docs/16-墨盘移植决策.md` | [16-墨盘移植决策.md](<design/16-墨盘移植决策.md>) |
| `docs/16-files-module.md` | [16-files-module.md](<design/16-files-module.md>) |
| `docs/17-weread-fusion.md` | [17-weread-fusion.md](<design/17-weread-fusion.md>) |
| `docs/18-handoff-v1.0.1-and-weread-v1.3.2.md` | [18-handoff-v1.0.1-and-weread-v1.3.2.md](<acceptance/18-handoff-v1.0.1-and-weread-v1.3.2.md>) |
| `docs/19-weread-export-bridge.md` | [19-weread-export-bridge.md](<design/19-weread-export-bridge.md>) |
| `docs/20-weread-fidelity-remediation.md` | [20-weread-fidelity-remediation.md](<design/20-weread-fidelity-remediation.md>) |
| `docs/202610210141来自CODEX的CODE REVIEW.md` | [202610210141来自CODEX的CODE REVIEW.md](<acceptance/202610210141来自CODEX的CODE REVIEW.md>) |
| `docs/21-weread-native-adaptation.md` | [21-weread-native-adaptation.md](<design/21-weread-native-adaptation.md>) |
| `docs/22-weread-standalone-plan.md` | [22-weread-standalone-plan.md](<design/22-weread-standalone-plan.md>) |
| `docs/23-weread-standalone-execution.md` | [23-weread-standalone-execution.md](<design/23-weread-standalone-execution.md>) |
| `docs/23-weread-standalone-workplan.md` | [23-weread-standalone-workplan.md](<design/23-weread-standalone-workplan.md>) |
| `docs/24-weread-spec-reference.md` | [24-weread-spec-reference.md](<design/24-weread-spec-reference.md>) |
| `docs/25-weread-internalization-plan.md` | [25-weread-internalization-plan.md](<design/25-weread-internalization-plan.md>) |
| `docs/26-device-adaptation-report.md` | [26-device-adaptation-report.md](<acceptance/26-device-adaptation-report.md>) |
| `docs/27-weread-page-parity.md` | [27-weread-page-parity.md](<acceptance/27-weread-page-parity.md>) |
| `docs/28-weread-batch1-remediation.md` | [28-weread-batch1-remediation.md](<acceptance/28-weread-batch1-remediation.md>) |
| `docs/29-weread-batch2-remediation.md` | [29-weread-batch2-remediation.md](<acceptance/29-weread-batch2-remediation.md>) |
| `docs/30-weread-batch3-remediation.md` | [30-weread-batch3-remediation.md](<acceptance/30-weread-batch3-remediation.md>) |
| `docs/31-weread-batch4-remediation.md` | [31-weread-batch4-remediation.md](<acceptance/31-weread-batch4-remediation.md>) |
| `docs/32-weread-review-remediation-summary.md` | [32-weread-review-remediation-summary.md](<design/32-weread-review-remediation-summary.md>) |
| `docs/33-dsh-development-playbook.md` | [33-dsh-development-playbook.md](<design/33-dsh-development-playbook.md>) |
| `docs/34-files-reader-upgrade-plan.md` | [34-files-reader-upgrade-plan.md](<design/34-files-reader-upgrade-plan.md>) |
| `docs/开发任务：重构自定义 Launcher 桌面阅读组件，实现极简杂志风格的多书籍轮播.md` | [开发任务：重构自定义 Launcher 桌面阅读组件，实现极简杂志风格的多书籍轮播.md](<design/开发任务：重构自定义 Launcher 桌面阅读组件，实现极简杂志风格的多书籍轮播.md>) |
| `docs/书架重构任务目标.md` | [书架重构任务目标.md](<design/书架重构任务目标.md>) |
