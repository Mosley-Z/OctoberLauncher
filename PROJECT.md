# 十雨桌面协作入口

十雨是 Android 墨水屏 Launcher，使用 Kotlin 与原生 View，包名 `com.shuiyu.octoberrain`。本文件只负责导航。

| 需要什么 | 入口 |
| --- | --- |
| 接手顺序与执行边界 | [AGENTS.md](AGENTS.md) |
| 当前工作与资源占用 | [memory/STATUS.md](memory/STATUS.md) |
| 稳定约束与经验 | [memory/MEMORY.md](memory/MEMORY.md) |
| 任务范围与依赖 | [task/README.md](task/README.md) |
| 现存架构与文档导航 | [docs/README.md](docs/README.md) |
| 开发、验证与交接 | [开发流程](docs/design/35-development-workflow.md) |
| 产品与构建入口 | [README.md](README.md)、[构建脚本](scripts/build.ps1) |

模块以 `settings.gradle.kts` 为准，工具链以 `toolchain.path` 与构建脚本为准，版本以 `VERSION` 和 app Gradle 为准。开工重新核实源码、工具链和设备；保留工作区既有修改。

前入口累积的阶段记录保存在[治理前历史快照](memory/sessions/2026-10-23-pre-governance-history.md)，不作为当前状态。
