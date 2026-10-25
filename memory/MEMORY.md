# 稳定项目记忆

当前进度见 [STATUS](STATUS.md)，历史轮次见[治理前快照](sessions/2026-10-23-pre-governance-history.md)。这里只保存持续适用的约束及可追溯经验。

| 规则或经验 | 来源与边界 |
| --- | --- |
| 正式版显示版本从1.0.0开始，versionCode52高于历史测试版51；生产密钥保存在仓库外，后续正式版必须复用，不再使用debug签名发布 | [REL001准备](../docs/acceptance/2026-10-26-rel001-v1-release-preparation.md)；仅本地产物，用户自行清理旧历史后再授权远端发布 |
| P4隐藏栏的系统边缘路径失败；现有无障碍服务实际连接时用自绘栏下滑主动展开，未连接/动作失败回退真实栏 | [UX151验收](../docs/acceptance/2026-10-26-ux151-p4-notification-gesture.md)、[UX150诊断](../docs/acceptance/2026-10-25-ux150-p4-statusbar-diagnosis.md)；release横竖远程通过，非ROM根因证明，盖板手指另验 |
| P4通知关闭过渡需让已连接服务的隐藏路径保持decorFits=false/FULLSCREEN，避免真实栏inset触发桌面重排；实际View尺寸与厂商中间窗口框应分别核对 | [UX152验收](../docs/acceptance/2026-10-26-ux152-p4-statusbar-transition.md)；未连接回退恢复正常避让，非逐显示帧/光学验收 |
| Android CLI持久UiAutomation会抑制其他无障碍服务；相关验收先释放com.android.cli.interact.instrumentation，核对实际绑定，按键/边缘输入用脚本纯ADB路径 | [UX151实测](../docs/acceptance/2026-10-26-ux151-p4-notification-gesture.md)；设置enabled不等于服务bound，强停后本机可撤销启用 |
| 阅读API完整周期可能省略零日；须验证周期标识和完整响应后才能补0，未取得周期仍为未知 | PeriodReadings源码及[UX149验收](../docs/acceptance/2026-10-25-ux149-implementation.md) |
| 盖板补偿用物理px并按方向映射，按厂商/型号识别；远程布局/事件通过不能冒充手指可达性通过 | 用户P4反馈及[UX148验收](../docs/acceptance/2026-10-25-ux148-safe-area-note-expansion.md) |
| Kotlin + 原生 View；feature 不横向依赖，公共契约归 core:common，宿主组装实现 | settings.gradle.kts、OctoberRainApp；[当前架构](../docs/architecture/current-architecture.md) |
| 配置写入必须追到实际消费者；取消、清空、重启和恢复保留用户语义 | [需求对接约定](../docs/design/36-demand-handoff-contract.md) |
| 新增功能或界面必须先取得用户明确确认；既有修复/删除按已授权范围推进，不自行设计额外页面 | 用户2026-10-24第4项明确要求；[UX133](../task/TASK-UX-133-desktop-followup-corrections.md) |
| 源码是实现事实，架构只描述职责与边界；代码代理仅提文档建议，主代理采纳后另派文档代理 | [开发流程](../docs/design/35-development-workflow.md)；权限是否硬隔离以实际验证为准 |
| 既有工作区修改保留；共享入口、Gradle输出及同一ADB serial均由明确唯一负责人操作 | AGENTS与开发流程；开工重新核实占用 |
| Wrapper已存在；构建脚本仍读取toolchain.path并调用配置工具链，不依赖全局JAVA_HOME | gradlew、gradle/wrapper、scripts/build.ps1；存在不等于本轮运行通过 |
| 反射注册与资源入口必须验证release；debug成功不能替代release | OctoberRainApp模块注册及开发流程 |
| 系统截图方向可能与逻辑输入方向不同；桌面自绘元素用android-ui标签与方向换算 | [工作手册§10](../docs/design/33-dsh-development-playbook.md)；截图不能替代触点验证 |
| S4/P4均为低内存设备；整机RAM不能直接作为Java堆预算 | [双机性能参考](../docs/design/38-device-performance-reference.md) |
| 配置迁移与原始字段存在性应区分，显式用户选择不能被新缺省覆盖 | [需求对接约定](../docs/design/36-demand-handoff-contract.md) |
| 绘制不反复全库IO；后台队列与缓存失效边界要明确 | [工作手册](../docs/design/33-dsh-development-playbook.md) |
| 微信读书由十雨独立获取与使用数据；负一屏与本书共用缓存和刷新入口 | [共享书架验收](../docs/acceptance/2026-10-22-shared-shelf-refresh.md)；原App不是运行前提 |
| 文件阅读器按格式选择原生或受控WebView；固定资源版本与兼容边界，不宣传全格式保真 | [阅读器方案](../docs/design/34-files-reader-upgrade-plan.md)、[最终收口](../docs/acceptance/2026-10-23-reader-final-closure.md) |
| 整个桌面已有INTERNET权限；阅读器离线由资源边界保证 | app Manifest、阅读器方案 |
| 文件身份与权限基于真实URI和授权，不猜测云端身份或以普通provider替代系统撤销证据 | [最终收口](../docs/acceptance/2026-10-23-reader-final-closure.md) |
| 换图先验证独立副本并确认配置读回，再删除旧图 | [最终收口](../docs/acceptance/2026-10-23-reader-final-closure.md)；不扩为所有媒体事务保证 |
| 密钥、用户正文、PIN、全量偏好和设备备份不进入文档或Git | AGENTS；历史入口归档已移除明文凭据 |
| 普通本书与沉浸轮播仅用最近阅读前10本并循环，不截断共享书架；初始最近第1本居中 | 用户2026-10-25四项追加；[UX145](../task/TASK-UX-145-desktop-settings-file-actions.md) |

格式、URI、PDF隔离、WebView和设备的详细案例在对应验收与[工作手册](../docs/design/33-dsh-development-playbook.md)，不在短记忆复制实现流水。
