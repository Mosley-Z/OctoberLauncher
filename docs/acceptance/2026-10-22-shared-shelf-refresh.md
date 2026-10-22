# 2026-10-22 FIX005/006 最近在读与共享书架刷新

- 范围/验收者：Codex；用户先要求检查最近在读选书和去除底部进度/沙漏，随后要求取消独立自动刷新，与桌面本书共用缓存及刷新。
- 源码：master cfeae53 + 既有未提交差异 + 本轮content/common配置/data迁移增量；保留现场，未提交/推送/升版/发布。
- 关联：[FIX005](../../task/TASK-FIX-005-shelf-recency.md)、[FIX006](../../task/TASK-FIX-006-shared-shelf-refresh.md)。FIX006取代FIX004的独立自动刷新和微信读书独立展示快照，不影响本地目录手动快照。
- 设备：S4 SA4B03CN2608081096X；本轮仍按用户授权以S4收尾，P4延后。
- 构建：本机toolchain.path/Gradle8.11.1/offline；产品版本沿用1.0.28/code48，配置结构升级v13。
- 最终release：app/build/outputs/apk/release/app-release.apk，SHA256 `c22daf66ec739a165d675ab5358c3df1d6166c036aa03f2193a88fd0546a4207`。
- 最终debug：app/build/outputs/apk/debug/app-debug.apk，SHA256 `b2f08a2235f1860e99ba771cf536fab3b363648667a5ecedd27bc7e0f77d5c65`。

## 问题与最终路径

上一轮由我引入的独立快照沿用了WereadClient先取接口前40项再排序的路径，又只保存10本；小组件使用共享本机资产最近阅读时间。S4技术证据显示目标书资产排名1，但旧10本快照没有它；不是用户没有刷新就能解释的正常差别。

FIX005先修正完整解析候选、按阅读时间排序再限量、只为选中候选下载封面，并去掉书封底部覆盖层及30dp预留空间。中间版本刷新后候选30本、目标排名1。用户随后要求共用缓存，因此最终FIX006直接让负一屏读取小组件同一filesDir/weread书架和资产；不再消费shelf-display/weread.json，不再复制写入微信读书快照。旧文件留存但被忽略，避免不必要的数据删除。

负一屏刷新复用AppServices.wereadSync.refresh(WereadRefreshRequest("book"))，与本书使用同一个HostWereadSync/WereadCardRefreshService，沿用本书已有绑定与选书偏好，不调用全量同步。共享落盘后已有content.dataVersion通知；书架前台订阅后仅重读本地。进入、返回、切源、改行数不新增网络请求，独立计时器已删除。本地书源仍需标题手动扫描且按目录匹配。右上角百分比保留。

## 验证

| 用例 | 实际结果 | 证据 | 结论 |
| --- | --- | --- | --- |
| 修复前排名 | 目标资产最近排名1，旧快照10本且缺失目标 | `_analysis/verify/fix005-before.json` | 确认差异 |
| 原截取回归 | 第100项候选凭最近时间进入第一；相同时间稳定、远端新时间可用，不加入未知成员 | WereadShelfOrderTest 3例 | JVM通过 |
| FIX005中间包 | 显式刷新后30候选、目标第一；退出重进updatedAt不变 | `fix005-after-refresh.json`、`fix005-after-return.json`；`_ui-out/20261022-211952/layout.json` | S4通过，最终被共享源取代 |
| 封面底部清理 | 无进度条/沙漏/底部遮罩，腰封下移，右上角百分比保留 | `_ui-out/20261022-212016/screen-annotated.png`、最终`213815/screen-annotated.png` | debug/release真实视觉通过 |
| 旧自动模式清退 | 旧auto/360配置解码升级到13，旧字段不再编码，书源/行数及其他集成设置保留 | ShelfMigrationTest；设备`fix006-before-ready.json` | JVM+S4通过 |
| 共用入口 | 请求mode=book且bookId留空由共享偏好决定，不调用syncNow；无host/忙碌拒绝能结束且不报成功 | SharedBookRefreshTest 2例；现有WereadCardRefreshServiceTest | JVM通过 |
| 本书→负一屏 | 标注精确点击本书刷新；共享1178本书架savedAt从1792674234791变为1792676119866，进入负一屏未再点击即显示目标第一 | `_ui-out/fix006-widget-annotated.png` #33；`fix006-before-ready.json`/`fix006-after-widget.json`；`213540/layout.json` | S4通过 |
| 负一屏→本书 | 标题刷新后同一共享savedAt变为1792676167992；目标仍是最近第一及当前书；返回本书读取同一数据 | `fix006-after-negative.json`/`fix006-after-return.json`；`213710/screen-annotated.png` | S4通过 |
| 不再写独立远程快照 | 两入口刷新后旧snapshot.updatedAt均停留1792675175213；返回桌面不额外改变共享savedAt | 上述技术JSON | S4通过 |
| 取消独立设置 | 书架设置只有Books/修改一行，不再有刷新方式/间隔 | `_ui-out/20261022-213629/layout.json`；最终release复验 | S4通过 |
| 最终混淆包 | 冷启动读共享目标第一；标题刷新完成、设置只留目录、本地缓存与最近阅读正常、恢复weread/2行 | `_ui-out/20261022-213815/screen-annotated.png`、`214111/layout.json`、`214117/layout.json`、`223628/layout.json`、`223717/layout.json` | 最终release通过 |
| 完整门禁 | ktlintFormat、check :app:assembleDebug :app:assembleRelease退出0，BUILD SUCCESSFUL in4m33s | `_analysis/verify/fix006-format-final.log`、`fix006-check.log` | 通过 |

最终相关模块每个debug/release变体单测：common82、data122、weread-sync54、content65、settings58，均0失败；完整check还覆盖其他模块。FIX005中间门禁也退出0，最终结论以FIX006 APK为准。

## 验证边界与交接

- 本轮真机覆盖正常双向刷新、旧快照停写、配置升级、最近第一、视觉清理及退出重进。网络异常保留沿用共享服务既有单测和机制，本轮未做真实断网矩阵；P4未验。独立自动刷新已取消，旧30分钟墙钟验证项不再适用。
- 没有改本书的手动选书偏好、绑定掩码或其他模块定时策略，没有自动全量同步、拉整库笔记或预热所有封面。普通读封面只查本地缓存，标题刷新可补可见封面。
- 原始偏好/正文不进入报告；技术证据只记录计数、排名、时间戳和相关开关存在性。CLI瞬时占位不作读取失败，等快照读取完成后检查。
- 最终release本地回归：切本地后直接显示Books已存书目和最近阅读，见`_ui-out/20261022-223628/layout.json`；随后恢复weread/2行，目标第一，见`223717/layout.json`。最终480×800/font1.0；本轮未改目录、图标、选书偏好或绑定项。
- 额度耗尽曾使自动审批复核失败，最后一项本地回归未执行；用户“继续”后已完成。续工时设备休眠，先确认Asleep再用Android键码224唤醒；未绕过审批，不把超时节点查询算作产品失败。
- 最终S4安装上述release，构建/ADB操作已释放，不再有本轮构建。下一步RD016基于共享缓存审计，不重建独立书架轮询；RD017补P4与网络失败矩阵。

本轮反思：由我在FIX004实现手动展示时引入了独立快照，却没有把“最近在读与小组件一致”纳入最终对照，导致先截取再排序及成员缺失漏验。后续先确认权威数据源和所有消费者，再决定是否新增缓存/计时器；排序必须先于限量，跨入口用同一目标书做成员与排名对照。
