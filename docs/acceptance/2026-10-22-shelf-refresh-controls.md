# 2026-10-22 FIX004 书架标题操作与刷新验收

- 范围/验收者：Codex 直接开发；用户最新确认行为，S4 本轮交付，P4 延后。
- 源码：master cfeae53 + 原有工作区差异 + FIX004 增量；保留原修改，未提交、推送、升版、发布。
- 任务：[FIX004](../../task/TASK-FIX-004-shelf-controls-refresh.md)；契约：[36](../design/36-demand-handoff-contract.md)。
- 工具链：toolchain.path 指向本地工具链，JDK17/Gradle8.11.1/offline；S4 SA4B03CN2608081096X，480×800，font_scale=1.0。
- APK：app/build/outputs/apk/{debug,release}/app-*.apk，沿用 1.0.28/code48；最终 S4 安装 release。
- release SHA256：`255869850f933febcabb286b825e7600813f050cea6b360431adf5718a3bd607`。
- debug SHA256：`f36afbf8d0a15c4bfea20b29d6aea5416c1eec4d1edaf93d8f25ff2d86f60c64`。

## 实现与证据

| 用例 | 操作与实际 | 证据 | 结论 |
| --- | --- | --- | --- |
| 唯一标题行 | 书架、−、2、＋、书源下拉、刷新同排，中心 y=138；可点击控件高55px；两来源切换正常 | `_ui-out/20261022-204618/layout.json`、`205826/layout.json` | S4 debug/release 通过 |
| 设置迁移 | 负一屏设置有书架设置、刷新方式、本地目录同排；手动隐藏间隔，自动显示30/60/180/360分钟选项；旧源/行数不再重复 | `_ui-out/20261022-204132/layout.json`、`204319/layout.json`、`205252/layout.json` | S4 通过 |
| 设置应用移除旧卡 | 设置应用为现有微读、桌面偏好、美化等卡，不再有阅读与数据；LauncherPrefs旧入口也移除 | `_ui-out/20261022-205639/layout.json`；SettingsCatalog回归 | release UI + 源码通过 |
| 默认与升级 | fresh默认manual；v11升级manual，保留旧合法间隔与其他集成配置，迁移幂等 | ShelfMigrationTest、NegativePageConfig | JVM通过；本机升级后手动/原6小时选项已读回 |
| 改目录待刷新 | 切至隔离目录不展示旧Books书目，提示点击刷新；最近阅读继续显示 | `_ui-out/20261022-204512/layout.json` | S4通过 |
| 手动快照不扫描 | 隔离目录首次点击得到1本；添加第2本、调行数、重启/覆盖安装，仍为1本且updatedAt不变；再次点击才变2本 | `_ui-out/20261022-204535/layout.json`、`204618/layout.json`；技术计数/时间戳读回 | S4通过 |
| 本地失败保留 | 将本轮隔离目录暂换为同名普通文件，刷新报错，2本原书仍在；随后恢复目录 | `_ui-out/20261022-204658/layout.json` | S4通过 |
| 自动仅列表前台 | 将测试快照时间设为到期：桌面保持时间戳1不变；进入负一屏才更新；不是等待30分钟的墙钟测试 | `_ui-out/20261022-204741/layout.json`、`204746/layout.json`；时间戳读回 | S4到期补刷通过；计时规则JVM通过 |
| 微信读书显式刷新 | 手动点击后成功获取并保存10条；技术日志trigger=manual/source=weread/success=true；普通读仅本地封面 | `_ui-out/20261022-204850/layout.json`、ShelfRefresh技术日志；仓库读取/封面调用点复核 | S4成功路径通过 |
| release快照一致性 | 最终混淆包从已存本地快照显示原Books书目，未点击刷新；切回微信读书正常 | `_ui-out/20261022-205545/layout.json`、`205826/layout.json` | 最终release通过 |
| 兼容旧路由 | HomeModule把BookshelfSettings映射至NegativeScreen(showShelfSettings=true)，新设置定位函数有精确R8规则；旧页面删除 | HomeModule、NegativeScreen、DeskContainerView、app/proguard-rules.pro | 源码/R8复核；无可见旧入口，未伪记设备触发 |
| 完整门禁 | ktlintFormat后check :app:assembleDebug :app:assembleRelease退出0 | `_analysis/verify/fix004-format.log`、`fix004-check.log`，最终BUILD SUCCESSFUL in24s | 通过 |

相关模块每个构建变体的单测：common82/data122/content61/settings58，debug/release均0失败；完整check还覆盖其他模块。新增测试覆盖快照跨实例读写、目录隔离、手动永不调度、到期/失败重试间隔/时钟回拨、升级保留与严格刷新配置失败。旧org.json调用依赖Android运行时，未把空实现JVM假传输当成成功网络覆盖；成功联网使用S4真实结果。

## 边界与恢复

- 自动模式设置页暂停、离开取消/返回重新检查、失败不紧密重试，由生命周期调用点复核与纯计时测试支持；未等待30分钟持续计时、未做运行中断网的微信读书失败矩阵，留最终设备门禁补充。其他微信读书分区策略保持原字段，不由新mode驱动。
- 首帧快照尚未读完可短暂出现空提示；CLI必须等页面就绪，不把瞬时占位当最终结果。收口时改为显式ShelfSnapshot.serializer()，消除新增持久类型的反射依赖；未将首帧现象确诊为R8数据损坏。
- 最近阅读存储、阅读进度入口未改；本地独立记录继续显示，微信读书只叠加已有条目的本机进度，不借机改变快照成员。
- 测试仅创建__fix004_fixture__及alpha/beta两个样本，已逐一删除并rmdir；用户Books目录未改动，恢复后显式建立9本快照；最终source=weread、rows=2、mode=manual、原间隔360分钟、本地目录Books、480×800/font1.0、release。
- P4按用户指令延后；本轮无双机DONE声明。快照/偏好原始备份只在OS私有临时目录，不进Git或文档。
