# 2026-10-22 DSH 接手 RD-002 增量

## 入口与规范

按交接文档顺序阅读 `PROJECT.md`、`memory/STATUS.md`、`memory/MEMORY.md`、`task/README.md` 与 `task/TASK-RD-002-sources.md`，并核对 `docs/design/35-development-workflow.md`。本轮起点为 master 1379e8b 加既有未提交差异，未提交、未升版本、未推送。

## 团队分工

三名 teammate 以独立上下文执行，各自持有任务卡与文件范围，禁止执行 Gradle、adb 与工作区回滚。

| 成员 | 任务卡 | 范围 |
| --- | --- | --- |
| zip-signature | task-1 | `ZipContainerProbe`、`DocumentKinds` 与对应测试 |
| external-entry | task-2 | `ExternalEntry`、`DocumentEntryActivity`、`SourceResolver` 与对应测试 |
| device-driver | task-3 | `_analysis/reader-probe` 探针、合成样本与设备检查脚本 |

集成、Manifest、构建与设备执行由 Lead 串行完成。

## 代码改动

新增 `feature/reader/src/main/java/com/shuiyu/octoberrain/feature/reader/source/ZipContainerProbe.kt`：`ZipContainer` 枚举与 `ZipContainerProbe.probe(File): ZipContainer?`，先经 `ZipPreflight.check` 的条目预算，再以 UTF-8 打开 `ZipFile` 读取内部标记；候选路径经 `ArchivePaths.normalize`，异常统一返回 null。

`DocumentKinds.kt` 增加 `isZipHeader(ByteArray)` 与四参数 `detect(name, mime, header, container)`，容器标记优先于扩展名；三参数版本委托并传 null。

新增 `feature/reader/src/main/java/com/shuiyu/octoberrain/feature/reader/source/ExternalEntry.kt`：`ExternalIntentFacts`、`ExternalEntry.Single/Rejected` 与 `ExternalEntryPolicy.resolve`。VIEW 取 data 与 ClipData，SEND 取 EXTRA_STREAM 与 ClipData；未知 action 与 SEND_MULTIPLE 返回 UNSUPPORTED，零候选返回 MISSING，非 content scheme 与无读授权返回 PERMISSION，多 URI 返回 UNSUPPORTED。

`DocumentEntryActivity.kt` 重写为策略分发：转发 `ACTION_VIEW` 到 `com.shuiyu.octoberrain.MainActivity`，保留读授权与 ClipData，仅在 persistable 时调用 `takePersistableUriPermission`；拒绝路径以 Toast 呈现，并写 `DocumentEntry` 日志（`forward` 与 `reject=`）。

`SourceResolver.kt` 的 content 查询整体容错并保留名称与空大小；新增 SecurityException/IllegalArgumentException 到 PERMISSION、FileNotFoundException 与空返回到 MISSING 的映射。

`feature/reader/src/main/AndroidManifest.xml` 为 DocumentEntryActivity 增加 `ACTION_SEND` filter，mime 清单与 VIEW 一致且不带 scheme。

## 验证

- `:feature:reader:testDebugUnitTest`：退出 0，53 用例 0 失败。
- `:feature:reader:detekt` 与 `:feature:reader:ktlintCheck`：退出 0。
- `check :app:assembleDebug :app:assembleRelease`：退出 0，52s。debug 5,846,784 字节，SHA-256 `77bd08bfae2d604edc0a15476c4f7074ef4665ee87f542f031d1dad4d327b03d`；release 1,434,377 字节，SHA-256 `308fc6eb0c6648c30a2145f8f005d03b326b2c2beffce4562d34455ffd8b6a35`；apksigner 验证通过（CN=Android Debug，沿用项目既有签名配置）。
- aapt2：DocumentEntryActivity exported，含 VIEW（scheme=content）与 SEND（无 scheme）两个 filter。
- S4（SA4B03CN2608081096X）：七例全过，证据见 `_analysis/verify/sources-002/` 与[验收记录](../../docs/acceptance/2026-10-22-reader-sources-002.md)。

## 审查修复

CODEX 审查提出三项并补齐门禁要求，均已实施：

- P1 类型安全：`streamUri()` 按动作读取，`Parcelable` 取出后再判断 `Uri`，非 Uri 按缺失处理；探针新增 `sendBadStream`，设备复核得到 `reject=MISSING` 且无崩溃。
- P2 主文档与附带授权：`ExternalEntryPolicy` 以 data 或 `EXTRA_STREAM` 为主文档，ClipData 存在主文档时全部视为附带授权；补充三组回归用例，`ExternalEntryPolicyTest` 共 11 例。设备以 sample.md 加父目录 ClipData 复核，打开成功且 `reader_status=42/47`。
- P3 日志脱敏：转发日志只保留 scheme、MIME 前 64 字符与授权状态。
- 门禁：完整 `check` 与双 APK 构建退出 0；release 包沿用 debug 签名属仓库既有配置，已写入验收边界。

## 探针与平台观察

`_analysis/reader-probe/src/ProbeActivity.java` 增加 `sendFixture` 分支，参数含 `sendFixture`、`sendAction`、`sendMime`、`uriPrefix`、`readGrant`、`persistable`、`revokeAfter`，目标组件固定为产品的 `DocumentEntryActivity`，结果写 `ReaderProbe` 日志。

`FixtureProvider` 白名单扩展到 24 项并显式声明合成样本 MIME；`generate_send_fixtures.py` 生成 plain、epubbin、docxzip、pdfbin 与 multi-a/b；`check_send_device.py` 汇总七例结果到 `s4-send-flow.json`。

设备观察：产品拒绝类路径的 Toast 不在 uiautomator 层级，结论改由产品日志的 `reject=` 判断；`uiautomator dump` 在 S4 上滞后于实际渲染，采样改为窗口结束前重采样一次。

授权链路发现：探针 `revokeUriPermission` 后 `checkUriPermission` 对产品 UID 返回 -1，但 FixtureProvider 的 `openFile` 仍被调用，说明导出 Provider 的读取未在该设备上由系统按 URI 授权拦截；撤销用例因此由探针显式拒绝后续读取来重现产品错误路径，系统层真实 SAF 撤销后的重选行为留给 017 与后续卡。

`SEND_MULTIPLE` 冷启动出现 `W Bundle` 警告栈，`streamUri` 对 ArrayList 取单值返回 null，流程按预期拒绝。

## 设备状态

S4 保留 debug 包与探针，探针进程已停止，产品停在首页。设备操作只用显式组件启动与只读命令。

Markdown 复核样本通过 `adb push` 到 `/data/local/tmp` 后由 `run-as ... cp` 写入私有 `files` 目录；直接以 `adb shell run-as ... cat >` 传输会经过 Windows 控制台的换行转换，破坏二进制样本字节数。

## 下一步

本轮已提交并推送 `origin/master`，等待用户最终确认；通过后按 `task/README.md` 依赖继续下一张卡，整包与设备门禁由 017 统筹。
