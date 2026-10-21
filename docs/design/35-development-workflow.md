# 开发、协作与跨会话流程

生效日期：2026-10-22。适用于后续任务；旧报告保持当时语境。本流程取代旧交接/规范中关于无 Git、固定旧版本、旧模块数量及逐步审批的过时描述。代码风格仍沿用工程 `.editorconfig`、ktlint、detekt 与模块规范。

## 1. 事实与文档归属

用户最新明确需求决定范围；设计描述目标；源码描述实现；验收描述某一版本的实际结果。发现冲突，先核实并记录差异，不以较新的日期代替证据。

| 内容 | 权威位置 | 更新者 |
| --- | --- | --- |
| 设计、接口决策、产品边界 | docs/design/ | 设计负责人/集成者 |
| 任务范围、执行状态和交付 | 单张 task/TASK-*.md | 该卡执行者 |
| 依赖、开工批次、共享文件所有权 | task/README.md | 集成者 |
| 测试、评审、设备验收结论 | docs/acceptance/ | 验证者；补充结论用新记录关联旧记录 |
| 原始日志、截图、APK摘要等 | _analysis/verify/<批次或版本>/ | 构建/设备负责人 |
| 稳定经验与当前状态 | memory/MEMORY.md、memory/STATUS.md | 集成者汇总 |
| 单轮接力现场 | memory/sessions/<日期>-<主题>-<执行者>.md | 执行者 |

一项事实只维护一个主要位置，其他位置链接。任务卡保留依赖，不复制整份方案；短记忆只收结论，不堆命令输出。历史材料不是新任务的隐含承诺。

## 2. 开工

1. 读取 AGENTS.md 的最小入口、当前卡和相关源码，查看 `git status --short` 与相关 diff。
2. 在卡中记录执行者、实际分支/工作树、开始时间、已有修改边界及文件所有权。不得将自己的分支覆盖到已有脏工作区；需要隔离且有条件时使用 `codex/<主题>` 分支/工作树，并记录真实路径，不自动改变用户分支。
3. 核实依赖卡的交付，不只看“完成”两个字。契约由上游卡固定到文档和源码后，下游才接入。
4. 定义一个可观察的用户闭环：入口→操作→状态落盘→实际消费者→退出重进→进程重启；只有相关时追加同步、权限、取消、清空、恢复场景。
5. 已授权且信息足够就执行；真正缺失的信息写明影响，独立工作继续。没有用户授权的未来任务仅保留为 TODO。

## 3. 状态口径

任务卡中的状态字段是单卡权威；看板只列依赖及推荐批次，避免维护两份进度。

`TODO → IN_PROGRESS → READY_FOR_REVIEW → VERIFIED → DONE`

- TODO：尚未实施；标记 READY 条件说明依赖是否满足。
- IN_PROGRESS：负责人明确，允许有阶段性交接。
- READY_FOR_REVIEW：代码/文档交付已存在，附自测和未测项；不能表述为产品已验收。
- VERIFIED：卡要求的验证已通过；设备项没有设备就继续待验，不能写 VERIFIED。
- DONE：集成者确认已接入目标状态，证据与必要文档齐全。Git 提交/合并/发布分别记录，不能由 DONE 推断已发布。
- BLOCKED：遇到明确外部阻塞，记录原因、解除条件和可继续部分；恢复后回 IN_PROGRESS。
- DEFERRED / CANCELLED：范围明确后置/取消，记录决定来源和下游影响。

文档任务按文档验收完成；不强行跑 Android 构建。代码任务根据影响跑模块测试，集成/交付阶段执行完整门禁。

## 4. 多 Agent 分工

集成者分配任务时给出卡路径、允许文件、共享接口版本、禁止碰的共享入口、验证负责人和预期输出。执行者只修改拥有文件；需要跨界变更时提交具体接口/差异建议，集成者接入，不自行大范围重构。

共享入口默认由集成者拥有：`settings.gradle.kts`、版本目录、`app/build.gradle.kts`、Manifest、OctoberRainApp、core:common 公共契约、AppServices、路由、proguard-rules、全局配置与锁屏、发布清单。任务明确授予后才由该卡执行者临时拥有。

同一 `feature:reader` 下的引擎也不能同时改 ReaderRegistry、ReaderScreen、build.gradle.kts 和共享测试 fixture；各卡提供注册适配器，由集成者串行注册。共享文件进入下一卡前必须交接，不以包目录不同假定零冲突。

共享工作树只允许一个构建负责人跑 Gradle/格式化；独立工作树可以独立编译，但整包验收以集成输出为准。同一 ADB serial 单一操作者，租用写在当前状态或任务卡，包含负责人、开始时间、释放情况。不得仅凭记录判断设备仍在线或仍由自己占用。

不要求为每张卡另起用户可见聊天。实际并行遵守当前用户授权与可用工具；任务文件本身不触发 Agent。

## 5. 构建与验证

先读 `scripts/build.ps1`、`toolchain.path` 及当前 Gradle 配置。无 Wrapper；Windows 工具链默认 JDK17 / Gradle8.11.1，但以文件实际配置为准。

```powershell
# 整包构建：脚本自行设置环境；离线只适用于依赖已缓存
pwsh -NoProfile -File scripts/build.ps1 -Variant debug -Offline
pwsh -NoProfile -File scripts/build.ps1 -Variant release -Offline
```

对任意 Gradle 任务，先在当前 shell 设置同一环境：

```powershell
$taskToolchain = (Get-Content -LiteralPath toolchain.path -TotalCount 1).Trim()
$taskJdk = Get-ChildItem -LiteralPath (Join-Path $taskToolchain 'jdk') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe') } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $taskJdk) { throw '缺少配置工具链 JDK' }
$env:JAVA_HOME = $taskJdk.FullName
$env:ANDROID_HOME = Join-Path $taskToolchain 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $taskToolchain 'gradle-home'
$taskGradle = Join-Path $taskToolchain 'gradle/gradle-8.11.1/bin/gradle.bat'
# 有格式修改时先单独运行受影响模块的 ktlintFormat，结束后再 check
& $taskGradle check :app:assembleDebug :app:assembleRelease --offline --console=plain
if ($LASTEXITCODE -ne 0) { throw "门禁失败: $LASTEXITCODE" }
```

`check` 含单元测试、格式、detekt、Lint；新增告警应修复，不能无解释扩大基线。格式化仅限自己修改的模块/文件，不能覆盖其他人的工作。命令退出码、实际输出和最终源码都要匹配，不能用筛选日志后的成功状态证明构建通过。

release 反射入口和资源必须验证；仅 debug 成功不满足整包交付。依赖首次下载或设备缺失如受环境限制，准确记录失败与未覆盖，不循环重跑无关门禁。

设备安装由当批负责人执行。复核 serial、目标 APK 的版本及摘要；采用覆盖安装，保留数据。必要备份存私有位置；测试后按字段合并恢复受影响状态，不覆盖期间产生的新用户数据。验证至少覆盖目标设备，不把尺寸覆写当作实体机证据。

## 6. 证据与审查

验收记录使用 `docs/templates/acceptance.md`，写清源码状态（提交或基线+改动范围）、APK、命令/退出码、设备、操作、预期、实际、截图/日志路径、未覆盖和恢复情况。不能只贴“测试通过”。

纯文档批次检查目录、索引、任务依赖、内部链接、迁移完整性和 diff 即可。已有历史断链应明确归档，不能借整理捏造对应文件。

在仓库根运行 `python scripts/check-project-docs.py`，检查本地文档链接、迁移映射、任务必需交接字段和依赖无环。两个明确登记的历史已删除源码引用只提示WARN，新增断链仍返回非零退出码。再运行 `git diff --check -- README.md AGENTS.md PROJECT.md docs task memory scripts/check-project-docs.py .github/workflows/build.yml settings.gradle.kts`；已有业务diff告警不应通过改动无关源码消除。

审查重点是实际入口、数据权威来源、失败语义、生命周期、性能与保留用户状态。发现问题回卡修复，验证覆盖最终修改。一个人的“代码完成”不等于多个引擎已经集成。

## 7. 长会话与记忆

每完成一个可交付阶段、切换方案、遇阻或准备结束会话时，在自己的任务卡更新：已改文件、实际验证、失败、后台进程、未提交状态和下一步。需要接力时写 session 交接，用 `docs/templates/handoff.md`。

集成者汇总 STATUS，记录各卡负责人/共享资源；不要让多个 Agent 整篇覆盖同一记忆文件。下一会话先验证 STATUS 中的动态事实，再继续最后一个未完成步骤，不重复已有效的验证。

稳定经验写明“现象→原因→处理→验证边界→来源”。未经验证的推测标为待核实；更新结论时保留取代关系。短记忆超过约两屏时把案例细节链接到专题或 session，历史轮次按日期保留，避免不断复制全量记忆。

自动记忆并非本流程前提。AGENTS.md 明确读取仓库内记忆入口，让不同 Agent 都能接续。聊天摘要只作定位线索，不能替代真实文件和证据。

## 8. 完成与发布

执行者交付修改结果、验证、限制、集成点和下一步；集成者验收并更新卡/STATUS。版本号、CHANGELOG、快照、签名及发布动作只在发布任务范围内执行。本次管理整理和未来单个模块任务不自动升版本、提交、推送或发布。

新文档命名与迁移规则见 docs/README.md。历史设计只有被当前任务明确引用时才成为该任务目标；取消的导入/遥控翻页需求不得从旧档恢复。
