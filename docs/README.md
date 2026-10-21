# 文档导览与分类索引

新会话先读 [PROJECT](../PROJECT.md)、[STATUS](../memory/STATUS.md) 和指定任务。这里不要求从00号读到最后；按需定位。

| 分类 | 内容 | 索引 |
| --- | --- | --- |
| 项目设计 | 目标、架构、功能规格、开发流程与经验 | [design](design/README.md) |
| 验收记录 | 评审、实施、适配、构建和交付记录 | [acceptance](acceptance/README.md) |
| 复用模板 | 任务卡、验收、会话交接 | [任务](templates/task.md)、[验收](templates/acceptance.md)、[交接](templates/handoff.md) |
| 任务与记忆 | 当前任务和跨会话接力，独立于历史报告 | [task](../task/README.md)、[memory](../memory/MEMORY.md) |

## 维护约定

1. 延用docs目录，不另建并行doc目录。已有文件保留原名/编号，分类不改变历史结论；实施与验收混合文档归验收，规格与设计混合材料归设计，按索引标明用途。
2. 新设计用清楚的主题名或下一个可用编号，不给本轮每张任务重复编设计号；新验收用 YYYY-MM-DD-TASK或主题.md，同日多份加后缀。
3. 历史验收按原版本理解，后续补验用新文件链接旧文件，不能把旧报告改成当前已通过。分析原始产物仍放_analysis/verify等原路径。
4. 活跃规则是AGENTS和35开发流程；旧规范、旧交接中的工具/版本/无Git声明按现状核实。动态状态只写STATUS和任务卡。
5. 文档使用仓库内相对链接；跨项目资料只作参考，不复制凭据与用户内容。迁移时检查链接，无法确认的历史路径写明来源和映射。

更新后运行 `python scripts/check-project-docs.py`，检查本地链接、迁移映射与任务依赖。检查器不调用Android构建或设备，不将历史记录升格为当前验收。

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
