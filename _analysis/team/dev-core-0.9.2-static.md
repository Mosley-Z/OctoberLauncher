# dev-core 0.9.2 静态检查门禁执行报告

## 交付内容

本次完成全仓库格式化、detekt 基线重建、Android Lint 基线接入与门禁开关切换，完整记录见 `_analysis/verify/0.9.2-static.md`。

## 关键数据

| 项目 | 数值 |
| --- | --- |
| 源文件总数 | 181（Kotlin 166、构建脚本 15） |
| ktlint 格式化前告警 | 3215 条 |
| ktlint 自动修复后 | 15 条 |
| ktlint 最终 | 0 条 |
| detekt 基线条目 | 234 → 237 条（12 份文件） |
| Android Lint 基线条目 | 84 条（13 份文件） |
| 格式化变更文件 | 171 个 |
| 单元测试 | 19 套件、149 用例、0 失败、1 跳过（格式化前后一致） |
| `gradle --offline check` | 通过，增量 3.3 秒 |
| 发布包 | `app-release.apk` 487839 字节，签名 `CN=Android Debug` |

## 手工处理明细

自动修复无法处理的 15 条：`Json.kt` 因文件名与顶层声明不一致重命名为 `AppJson.kt`；`InkColors.kt` 的 8 条全大写配色令牌采用文件级抑制并注释说明；`HomeModule.kt` 的私有对象 `provider` 采用声明级抑制；`BeautifyIconPanel.kt` 等 5 个文件的文件级 KDoc 改写为 `//` 行注释。

## 门禁与验证

`build.gradle.kts` 中 detekt 与 ktlint 的 `ignoreFailures` 全部改为 false，`check` 依赖 `ktlintCheck` 与 `detekt`；CI 测试步骤改为 `gradle testDebugUnitTest --continue`，静态检查步骤在违规时返回非零。

临时新增 `TempViolation.kt` 验证失败能力：ktlint 退出码 1，报 `Class body should not start with blank line`；detekt 退出码 1，报 `TooGenericExceptionCaught` 与 `SwallowedException` 共 2 条。临时文件已删除。

`assembleDebug` 与实际发布包 `assembleRelease` 均成功；`docs/08-engineering-conventions.md` 的行宽、静态检查说明与 `check` 组成已同步为现状。
