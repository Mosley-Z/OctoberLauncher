# 2026-10-22 RD-003/004/005 阅读宿主、书签状态与文本索引 DSH 增量 验收记录

- 范围/验收者：RD-003 旋转与窗口重排、RD-004 书签编辑删除、RD-005 稀疏索引与搜索高亮，DSH 集成者与任务执行者共同完成
- 源码提交或基线+差异范围：master `cfeae53`；修改 `feature/reader/src/main/java/com/shuiyu/octoberrain/feature/reader/ReaderChrome.kt`、`ReaderScreen.kt`、`ReaderSession.kt`、`session/ReadingRepository.kt`、`text/TextEngine.kt`、`text/TextSearch.kt`、`text/TextWindow.kt`，新增 `text/TextHighlight.kt`、`session/ReadingStateRulesTest.kt`、`text/SparseTextIndexTest.kt`；测试与任务卡增量段同步更新
- APK路径/版本/摘要（如适用）：debug `app/build/outputs/apk/debug/app-debug.apk`，5,853,173 字节，SHA-256 `6d0bfa9f8a03f1e49c40e7f297e84fbb5a7a3ce6f374c16742d3daf8f9a3cd32`，2026-10-22 12:42；release `app/build/outputs/apk/release/app-release.apk`，SHA-256 `5213b45851ab2221cc439da83fd181fe664a00ea2f91deba08c193b30e12cc8f`，12:42
- 环境/工具链/WebView/设备serial（如适用）：JDK 17.0.20_12、Android SDK 35、Gradle 8.11.1 `--offline`；设备 S4 serial SA4B03CN2608081096X 在验收时离线，设备矩阵未执行
- 关联任务/设计：[TASK-RD-003](../../task/TASK-RD-003-reader-shell.md)、[TASK-RD-004](../../task/TASK-RD-004-reading-state.md)、[TASK-RD-005](../../task/TASK-RD-005-text.md)、[34 总方案](../design/34-files-reader-upgrade-plan.md)、[35 协作流程](../design/35-development-workflow.md)

| 用例/命令 | 前置条件与操作 | 预期 | 实际/退出码 | 证据路径 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `:feature:reader:testDebugUnitTest` | 离线 Gradle | 全部通过 | BUILD SUCCESSFUL，退出 0，70 用例 0 失败（新增 ReadingStateRulesTest 9、SparseTextIndexTest 6、TextSearchTest 2） | `feature/reader/build/test-results/testDebugUnitTest/` | 通过 |
| `:feature:reader:detekt` | 同上 | 无静态检查问题 | BUILD SUCCESSFUL，退出 0（修复 onPageSize 与 alignToCharBoundary 的 ReturnCount、书签重命名的 MaxLineLength 后） | `_analysis/verify/reader-003/module-gate.log` | 通过 |
| `:feature:reader:ktlintCheck` | 同上 | 无格式问题 | BUILD SUCCESSFUL，退出 0（`:feature:reader:ktlintFormat` 后） | 同上 | 通过 |
| `check :app:assembleDebug :app:assembleRelease` | 同上，完整检查 | 检查与双 APK 构建通过 | BUILD SUCCESSFUL，41s，退出 0 | `_analysis/verify/reader-003/full-gate.log` | 通过 |
| 稀疏索引块表与首屏读取 | JVM 断言程序（Kotlin 2.0.21） | 50MiB 文件 200 块、只读首块、块首直达、文件尾短块 | PASS: 43 checks，退出 0 | `feature/reader/src/test/.../SparseTextIndexTest.kt` | 通过 |
| 搜索高亮区间 | JVM 用例 | 当页区间、重复与相邻匹配、越界截断 | 8 例通过 | `TextSearchTest.kt`、`TextHighlight.kt` | 通过 |
| 书签标题规则 | JVM 用例 | 去空白、空拒绝、按码点截断 80、不拆代理对 | 9 例通过 | `ReadingStateRulesTest.kt` | 通过 |
| 设备 480×800 打开 plain 文本 | 探针 VIEW 投递 plain，等待后采样 | 标题 plain、状态含百分比、上一页与下一页可用 | reader_title=plain，reader_status="3% · UTF-8"，两个翻页按钮 enabled | `_analysis/verify/reader-003/s4-open_plain.png` | 通过 |
| 设备音量键前后翻页 | 阅读页内发送 KEYCODE_VOLUME_DOWN/UP | 状态百分比前后变化 | 3%→4%→3%；`tray.volumeKeyFlip` 临时置 true 后验证，测毕恢复 false | `s4-volume_next.png`、`s4-volume_prev.png` | 通过 |
| 设备正文左右区域点按翻页 | 阅读页内按宽度比例点击正文 | 左右区域分别前后翻页 | 3%→4%→3% | `s4-tap_next.png`、`s4-tap_prev.png` | 通过 |
| 设备运行中 `wm size 552x768` 重排与恢复 | 阅读页保持打开时覆盖尺寸，结束后复位 | 标题与按钮可用，正文按新尺寸重排，复位成功 | 552×768 下标题 plain、状态 3% 保持，正文行宽随尺寸变化；复位回 480×800 | `s4-size_552x768.png`、`size-552x768-live.png` | 通过 |
| 设备系统旋转 | `settings put system user_rotation 1` 后采样 | 窗口旋转并重排 | `user_rotation` 写入后读回 0，display rotation 保持 0；设备固定竖屏且第三方 Rotation 应用接管，旋转路径以尺寸变化验证 | 命令输出 | 未生效，记录限制 |
| 设备书签添加、重命名与删除 | 阅读页书签对话框逐项操作 | 列表刷新，源文件不变 | 添加、列出、重命名、删除共 16 个步骤全部通过 | `_analysis/verify/reader-003/check_bookmarks_device.py` 输出 | 通过 |

## 说明与边界

旋转与窗口尺寸变化由 `ReaderPageView.sizeListener` 上报到 `ReaderScreen.onPageSize`。项目 `MainActivity` 声明处理 `orientation|screenSize|screenLayout|smallestScreenSize` 等配置变化，旋转时 Activity 不重建，因此不能依赖 `onResume` 重新打开；尺寸回调在可见且有会话时按当前 `frame.locator` 重开，动作执行中先置 `relayoutPending`，结束后补一次，旧代次回调继续由 `generation` 拦截。

书签对话框改用带主键的 `bookmarkRows()`，条目可跳转、重命名或删除；重命名预填原标题、输入上限 80，删除需二次确认，两种操作只更新阅读记录，不删除源文件。最近阅读入口按任务边界留给 RD-015 集成，本增量只交付存储与查询接口。

高亮以当前页可见文本为范围，使用不透明浅灰底色，不构造整文件字符序列；GB18030 的尾字节可能本身是合法单字节字符，索引只跳过解码报错的错位，该局限写入任务卡。设备上的墨水屏对比度与字号重排效果需要真机确认。

设备矩阵在 S4 于 13:13 恢复连接后执行，六项全部通过。墨水屏的 uiautomator dump 相对渲染滞后数秒，驱动脚本以“动作后重采样直到状态变化”的方式判定翻页，单次 dump 不足以作为证据。音量键翻页受 `tray.volumeKeyFlip` 控制，默认 false，验证时临时置 true，测毕恢复 false，未把该临时配置留在设备上。系统旋转设置写入 `user_rotation` 后读回 0，display rotation 保持 0，设备为固定竖屏且第三方 Rotation 应用接管方向，旋转路径改以运行中 `wm size 552x768` 的尺寸变化覆盖验证；两者都经过 `ReaderPageView.sizeListener` 到 `ReaderScreen.onPageSize` 的同一重排入口。书签的添加、列出、重命名与删除在设备上按 16 个步骤逐项操作并通过，数据库行为以该链路为证据。

reader 模块没有独立 detekt 基线文件，静态检查结论以整包 `check` 为准；release APK 沿用项目既有 debug 签名配置。
