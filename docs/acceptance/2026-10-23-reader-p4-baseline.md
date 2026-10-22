# 2026-10-23 RD017 P4 阅读器基线补验

- 范围/验收者：P4 测试子智能体；仅补六种已集成格式的代表合成样本，不代替 Office 后续最终 APK 门禁。
- 源码/APK：提交 `0a1534c` 的稳定副本 `_analysis/verify/office-next/baseline-release.apk`；SHA-256 `c2d9efcd09348c51663a2068a7b78ac1031130d18bc79681fd9951927a6d55ab`，1.0.28 / versionCode 48。工作树并行开发差异未打入本 APK，未运行 Gradle。
- 设备：`P4L202610130006`，model P4，Android 11，物理 552×768，无尺寸覆写；WebView 83.0.4103.120。初检系统 keyguard showing=false、十雨 MainActivity 前台，无锁屏绕过。
- 设备资源：本子智能体独占 P4；S4 未操作。默认沙箱 adb 初始化失败，使用获准的提权调用；界面观测/点击全部走 `scripts/android-ui.ps1` 并显式 Serial。
- 关联：[RD017](../../task/TASK-RD-017-release-gate.md)、[总方案](../design/34-files-reader-upgrade-plan.md)。

| 用例 | 前置/操作 | 实际 | 证据目录/文件 | 结论 |
| --- | --- | --- | --- | --- |
| 安装 | 摘要核对，`adb -s P4L202610130006 install -r` | Success，保留原数据，最终版本48 | `_analysis/verify/p4-baseline/final-version.txt` | 基线通过 |
| TXT 入口/翻页/书签/强停 | 独立合成 plain.txt 经自身 files_share content URI，读授权1；翻两页、添加书签、force-stop 后重开 | 0%→1%；重开1%；查看书签仍列1% | `_ui-out/p4-baseline-txt-*` | 通过；未覆盖书签改名/删除 |
| PDF | normal.pdf 下一页；后续强停/其他格式后再开 | 1/3→2/3；重开2/3，100% | `_ui-out/p4-baseline-normal.pdf-*`、`_ui-out/p4-baseline-pdf-reopen/` | 通过 |
| Markdown | sample.md 打开/下一页 | 就绪后2/30，合成正文可读 | `_ui-out/p4-baseline-sample.md-*` | 通过；未测该格式书签/重启 |
| EPUB | sample.epub 打开/下一页 | 第1/3章1/13→2/13 | `_ui-out/p4-baseline-sample.epub-*` | 通过；未测跨章/搜索/重启 |
| ZIP | sample.zip→目录·目录→正文.txt | 根3项、子目录3项，正文0% UTF-8 | `_ui-out/p4-baseline-sample.zip-*`、`_ui-out/p4-baseline-zip-*` | 通过；未测该格式书签/重启 |
| DOCX | reader-sample.docx 正文翻页→原版式预览→force-stop 后重开 | 正文1/17→2/17；原版式2/2；重开仍原版式2/2 | `_ui-out/p4-baseline-docx-ready/`、`_ui-out/p4-baseline-docx-fixed-layout/`、`_ui-out/p4-baseline-docx-restart/` | 通过；合成表格/图片与段落样本 |
| 552×768实体适配 | DOCX原版式标注截图及阅读壳节点 | 顶栏、正文、原版式说明、上一页/阅读设置/下一页可见，底栏位于671–726 | `_ui-out/p4-baseline-docx-annotate/screen-annotated.png` | 该样本通过；无尺寸覆写 |
| 翻页全刷设置 | 原每6页CHECKED→关闭→每12页→强停→恢复每6页 | 0可选并继续翻页；12重启后CHECKED；最终6 CHECKED | `_ui-out/p4-baseline-refresh-layout/`、`_ui-out/p4-baseline-refresh-persist-layout/`、`_ui-out/p4-baseline-final-txt-layout/` | 设置/持久化通过；物理波形次数未测 |
| 损坏PDF恢复 | corrupt.pdf 35字节，返回、HOME | 显示“文档无法解析”，HOME后桌面MainActivity可用 | `_ui-out/p4-baseline-corrupt-layout/`、`_ui-out/p4-baseline-home-layout/`、`_analysis/verify/p4-baseline/final-front.txt` | 通过 |

## 资源技术采样

仅在上述小合成样本稳定界面取单次快照，不是峰值/长时压力结论。

- DOCX主进程83,252 KiB PSS，绑定 renderer PID2271 为40,105 KiB，合计123,357 KiB。关联由 `docx-services.txt` 的 Client AppBindRecord 指向主PID1824、isolatedProc PID2271确认。证据为 `_analysis/verify/p4-baseline/docx-{host-memory,renderer-memory,services,processes}.txt`。
- PDF主进程88,978 KiB PSS，isolated worker PID2563为11,116 KiB，合计100,094 KiB。`pdf-services.txt`确认独立UID/服务。证据为 `_analysis/verify/p4-baseline/pdf-{host-memory,worker-memory,services,processes}.txt`。

## 恢复、边界与下一步

- 仅改变阅读器刷新单字段，原值6、最终6已回读；未改字体、编码、系统网络、锁屏或尺寸。记录见 `_analysis/verify/p4-baseline/restore.json`。未读取PIN、用户正文、全偏好或用户数据库。
- 新建 `/sdcard/Download/p4-reader-baseline-20261023/`，仅含7个明确命名合成文件；相关合成阅读记录/一条TXT书签保留供最终新包回归。用户原文件未覆盖。新增目录初检不存在。
- P4已回HOME并释放给集成者，最终安装仍为上述基线release；未提交/推送/升版/发布，未碰S4和共享构建产物。
- 未覆盖：新表格/PPTX/统一搜索、复杂Word、全格式书签/强停、真实SAF撤销重选、网络失败/离线验证、锁屏恢复、物理EPD波形、长时压力/峰值、桌面全部FIX闭环。后续须在集成者最终同一APK双机复验，不关闭017。
- 工具边界：DOCX一次翻页后即时layout仅1节点，稍后再次layout正常2/17，不据瞬态空树判崩溃。PDF设置对话框项目较长，`click text=翻页全刷`虽返回成功但未滚到目标；该次不算设置通过，改在可见TXT菜单复核最终6。
