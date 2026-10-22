# 十雨桌面：新会话导览

从本文件开始，按任务读资料。这里是仓库内人工维护的入口，不依赖特定 Agent 的私有记忆功能。

| 需要什么 | 入口 |
| --- | --- |
| 最新工作状态、风险、下一步 | [memory/STATUS.md](memory/STATUS.md) |
| 稳定约束与已知经验 | [memory/MEMORY.md](memory/MEMORY.md) |
| 领任务、看依赖与并行边界 | [task/README.md](task/README.md) |
| 开发、集成、验收、交接流程 | [开发流程](docs/design/35-development-workflow.md) |
| 全部设计和历史验收索引 | [docs/README.md](docs/README.md) |
| 设备、状态与性能问题的具体经验 | [DSH 工作手册](docs/design/33-dsh-development-playbook.md) |
| 本轮文件阅读器总方案 | [阅读器升级方案](docs/design/34-files-reader-upgrade-plan.md) |
| 构建脚本与产品介绍 | [README.md](README.md)、[scripts/build.ps1](scripts/build.ps1) |

十雨是 Android 墨水屏 Launcher，包名 `com.shuiyu.octoberrain`；Kotlin + View，工具链以 `toolchain.path` 为准。源码结构以 `settings.gradle.kts` 为准，版本以 VERSION 和 app Gradle 为准，不能依赖旧文档中的模块/测试计数。

当前仓库根就是本目录，已经存在 Git；2026-10-22 接手时分支是 `master`，工作区有大量未提交代码和资源修改。不要按旧文档“未启用 Git”操作，也不要直接切换分支丢失现场。

微信读书的当前产品边界是十雨独立使用数据，原 App 不是运行前提。文件阅读器已有原生文本/PDF、ZIP、Markdown与EPUB首轮；DOCX正文/原版式、CSV/XLSX/XLS/ODS原生窗口及PPTX单页/提纲已接入，统一搜索见[集成交付](docs/acceptance/2026-10-23-reader-office-search-integration.md)。各引擎的实际完成状态与兼容边界以任务卡和验收为准，不能因方案或目录存在而宣称全部格式支持。

目录分工：`docs/design/` 保存设计与规格；`docs/acceptance/` 保存评审、实施、验收结论；`task/` 保存任务卡；`memory/` 保存短记忆和交接；`_analysis/verify/` 等原有目录继续保存证据产物，不随本次整理迁移。

本次参考 WereadMoji 的“导览→任务卡→证据→记忆”结构；不移植它的非 Gradle 构建方式、外部路径、站点发布规则或每步审批要求。
