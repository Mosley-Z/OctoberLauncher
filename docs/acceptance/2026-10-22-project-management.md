# 2026-10-22 项目管理与阅读器任务拆分验收

- 范围：文档、任务、记忆与必要的文档路径引用；未实现阅读器。
- 源码现场：master分支，VERSION起始观察值1.0.24，收尾观察值1.0.25（app versionCode 45），工作区已有大量未提交业务改动。本记录不替这些代码做验收。
- APK/设备：本次不适用；未构建、未安装、未操作设备。
- 关联设计：[34阅读器方案](../design/34-files-reader-upgrade-plan.md)、[35开发流程](../design/35-development-workflow.md)。

## 交付

| 项目 | 实际结果 |
| --- | --- |
| 任务卡 | task/TASK-RD-000至020共21张，状态均为TODO；000–017覆盖首轮，018–020明确后置 |
| 协作入口 | AGENTS、PROJECT、任务看板、契约草案；定义公共文件所有权、依赖、串行构建与设备操作者 |
| 文档整理 | 40份旧文档分类迁移；原名/编号保留；迁移映射、分类索引和主要引用已更新 |
| 经验与记忆 | MEMORY稳定规则、STATUS现场、session接力，链接既有DSH工作手册，不复制全量聊天 |
| 模板与检查 | task/acceptance/handoff模板；scripts/check-project-docs.py检查本地链接、任务依赖及迁移 |

## 验证记录

| 检查 | 命令/方法 | 结果 |
| --- | --- | --- |
| 文档链接、任务字段/依赖与迁移 | `python scripts/check-project-docs.py` | 77份Markdown，40条迁移，21张任务，0错误、2条已登记历史WARN；退出码0 |
| 任务初始状态 | 逐卡解析状态字段 | 21张均为TODO，未创建虚假执行记录 |
| 本轮管理文件diff | `git diff --check -- README.md AGENTS.md PROJECT.md docs task memory scripts/check-project-docs.py .github/workflows/build.yml settings.gradle.kts` | 退出码0 |
| Android构建/设备 | 本轮只整理管理材料 | 未执行，不适用 |

文档检查输出：原始结果（本地证据：`_analysis/verify/project-management-2026-10-22/docs-check.txt`）。以上不是Android构建或真机验证证据。

## 历史边界

- 24-weread-spec-reference.md原本为只读文件，分类后保持原文和只读属性；其原路径引用通过迁移表定位。
- 历史CODE REVIEW包含已删除的WereadGatewayProvider.kt和KeyEntryServer.kt，保留当时评审内容，检查器仅对这两条具体历史引用提示WARN；新文档断链仍报错。
- 最初迁移检查发现30号记录到tools的相对链接需多返回一级，已修复为../../tools；本轮新增入口的链接均须实际存在。
- 全仓git diff --check发现既有WereadBoardWidget.kt、KernelWereadBoard.kt等业务代码空白告警；本次不修改这些代码。受影响管理文件单独检查，不宣称全仓diff干净。
- 不替历史报告更新旧版本、测试数量或验收结论；00/08/10旧设计/规范/交接增加历史语境提示，当前流程见35。
- 无提交、推送、发布、版本变更；没有新后台进程或设备占用，未复制WereadMoji凭据及设备私有数据。
