# 2026-10-22 RD-002 文件来源、类型识别与外部授权 验收记录

- 范围/验收者：RD-002 增量（ZIP 内部标记鉴别、ACTION_SEND 入口、授权与缺失矩阵），DSH 集成者执行
- 源码提交或基线+差异范围：提交 `8ca69db`，基于 master `1379e8b`；新增 ZipContainerProbe.kt、ExternalEntry.kt、ExternalEntryPolicyTest.kt，修改 DocumentKinds.kt、ReaderSession.kt、DocumentEntryActivity.kt、SourceResolver.kt、feature/reader/src/main/AndroidManifest.xml；审查修复后 DocumentEntryActivity.kt 与 ExternalEntry.kt 二次修改（类型安全、主文档与附带授权、日志脱敏）
- APK路径/版本/摘要（如适用）：debug `app/build/outputs/apk/debug/app-debug.apk`，5,846,784 字节，SHA-256 `77bd08bfae2d604edc0a15476c4f7074ef4665ee87f542f031d1dad4d327b03d`，2026-10-22 09:41:19；release `app/build/outputs/apk/release/app-release.apk`，1,434,377 字节，SHA-256 `308fc6eb0c6648c30a2145f8f005d03b326b2c2beffce4562d34455ffd8b6a35`，09:42:00，apksigner 验证通过（CN=Android Debug，沿用项目既有签名配置）
- 环境/工具链/WebView/设备serial（如适用）：JDK 17.0.20_12、Android SDK 35、Gradle 8.11.1 `--offline`；设备 S4，serial SA4B03CN2608081096X，Android 版本以 `inspect_adb_device` 记录为准
- 关联任务/设计：[TASK-RD-002](../../task/TASK-RD-002-sources.md)、[34 总方案](../design/34-files-reader-upgrade-plan.md)、[35 协作流程](../design/35-development-workflow.md)

| 用例/命令 | 前置条件与操作 | 预期 | 实际/退出码 | 证据路径 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `:feature:reader:testDebugUnitTest` | 离线 Gradle | 全部通过 | BUILD SUCCESSFUL，退出 0，53 用例 0 失败（ZipContainerProbeTest 9、ExternalEntryPolicyTest 11、DocumentKindsTest 5、其余既有 28） | `feature/reader/build/test-results/testDebugUnitTest/` | 通过 |
| `:feature:reader:detekt` | 同上 | 无告警 | BUILD SUCCESSFUL，退出 0 | 构建日志 | 通过 |
| `:feature:reader:ktlintCheck` | 同上 | 无格式问题 | BUILD SUCCESSFUL，退出 0（修复测试导入顺序与链式调用缩进后） | 构建日志 | 通过 |
| `check :app:assembleDebug :app:assembleRelease` | 同上，完整检查 | 检查与双 APK 构建通过 | BUILD SUCCESSFUL，52s，退出 0；debug 与 release 摘要见文首 | `_analysis/verify/release-gate.log` | 通过 |
| `:app:assembleDebug` | 同上 | 构建成功 | BUILD SUCCESSFUL，退出 0，APK 5,846,784 字节 | 上述 APK 摘要 | 通过 |
| `aapt2 dump xmltree app-debug.apk --file AndroidManifest.xml` | build-tools 35.0.0 | DocumentEntryActivity exported 且含 VIEW、SEND 两个 filter | VIEW 带 scheme=content，SEND 无 scheme，mime 清单与 VIEW 一致 | 命令输出 | 通过 |
| 设备 SEND text/plain（探针 plain） | 冷启动产品，投递 `ACTION_SEND`，等待后采样 | 进入阅读页，标题 plain，状态含百分比 | reader_title=plain，reader_status="0% · UTF-8"，Log.i forward 记录 URI | `_analysis/verify/sources-002/s4-send-text.png`、`case-send-text.xml` | 通过 |
| 设备 VIEW epub（探针 epubbin） | 投递 `ACTION_VIEW`，MIME application/epub+zip | 走 EPUB 阅读页 | reader_title=epubbin，reader_status="第1/2章 · 1/1" | `s4-view-epub.png`、`case-view-epub.xml` | 通过 |
| 设备 SEND application/zip（DOCX 容器） | 探针 docxzip，MIME application/zip | 提示不支持此文档类型 | "暂不支持此文档类型，可使用其他应用打开" | `s4-send-docxzip.png` | 通过 |
| 设备 SEND_MULTIPLE 两个 URI | 探针 multi-a.txt | 拒绝多文件投递 | 未进入阅读页，Log.i reject=UNSUPPORTED | `case-send-multi-logcat.txt` | 通过 |
| 设备 SEND 无读授权 | 探针 readGrant=false | 拒绝缺少读取授权 | 未进入阅读页，Log.i reject=PERMISSION | `case-send-nogrant-logcat.txt` | 通过 |
| 设备 SEND 缺失文件 missing.txt | Provider 返回名称与 null 大小，文件不存在 | 阅读页提示源文件不存在 | reader_title=missing.txt，reader_status="源文件不存在" | `s4-missing.png`、`case-missing.xml` | 通过 |
| 设备 撤销授权后读取 | 探针投递后 `revokeUriPermission` 并让 Provider 拒绝后续读取 | 阅读页提示授权失效 | reader_title=plain，reader_status="无法读取文档，请重新授权"；探针日志 revoke="ok"、revokeCheck=-1 | `manual-revoke.xml`、`case-revoke-logcat.txt` | 通过 |
| ZIP 内部标记单元用例 | JVM 用例 | EPUB、DOCX、XLSX、PPTX、普通 ZIP 与坏容器区分 | 9 例通过 | `ZipContainerProbeTest` | 通过 |
| 设备 SEND 非 Uri 类型 EXTRA_STREAM | 探针 `sendBadStream=true`，EXTRA_STREAM 为 Intent 实例 | 按缺失处理，不崩溃、不提前抛类型转换异常 | Log.i reject=MISSING，产品进程存活，crash 缓冲无产品记录 | `_analysis/verify/sources-002/` 用例日志 | 通过 |
| 设备主文档加目录授权 | 探针 `openFixture sample.md --ez grantFolder true`，data 为单个 Markdown，ClipData 为父目录 | 打开主文档，目录作为附带授权，不按多文件拒绝 | forward scheme=content mime=text/markdown persistable=false；reader_title=sample.md，reader_status="42/47" | `_analysis/verify/sources-002/s4-view-folder.png`、`manual-view-folder.xml` | 通过 |
| 设备日志字段核对 | 上述投递的 logcat | 仅含 scheme、MIME、授权状态，无路径与查询参数 | 记录形如 forward scheme=content mime=text/markdown persistable=false | 用例 logcat | 通过 |

## 说明与边界

设备矩阵的撤销用例中，探针先调用 `revokeUriPermission` 撤销系统授权（探针 `revokeCheck=-1` 表明产品 UID 对 URI 已无读授权），同时让 FixtureProvider 对后续读取抛 SecurityException。第二项是必要的补充：该 Provider 为 exported 且 `grantUriPermissions`，Android 在 provider 侧没有强制校验调用者授权时，撤销记录本身不会阻止已投递的读取请求，因此以显式拒绝重现产品在授权失效后的错误路径。真实 SAF 授权在系统存储中撤销后的重选行为未在设备上完整重现，留给 017 整包门禁与后续 SAF 重选用例。

Toast 不在 uiautomator 层级，拒绝类用例的结论依据产品 `DocumentEntry` 日志中的 `reject=` 记录，截图与 XML 仅作页面状态证据。

SEND_MULTIPLE 冷启动时出现 `W Bundle` 警告栈（`DocumentEntryActivity.streamUri` 对 ArrayList 取单值返回 null），流程不崩溃且按预期拒绝；该防御路径保留，后续可在 streamUri 中显式识别集合类型以减少警告。

外部入口在动作层面区分主文档与附带授权：VIEW 以 data 为主文档，SEND 以 EXTRA_STREAM 为主文档；主文档存在时，ClipData 中的其余 URI 视为附带授权，不参与文件数量判定，也不作为打开对象。主文档缺失时才回退到 ClipData 首项，多于一项仍按不支持处理。该规则覆盖 Markdown 正文引用同目录图片的既有场景。

日志只记录 scheme、MIME 前 64 字符与授权状态，路径与查询参数不进入 logcat。EXTRA_STREAM 的读取受动作约束，并以类型安全方式取出 Uri；其他 Parcelable 在策略阶段按缺失处理，不抛出未捕获异常。

Office 相关内容在当前实现中只做容器标记识别，未接入 Office 渲染引擎，不构成 Office 支持结论。

release 包沿用项目既有 debug 签名配置，属于当前仓库构建设置，不代表发布签名通过。

S4 验证后产品保持首页，探针进程停止；设备上的 debug 包为本次构建版本。
