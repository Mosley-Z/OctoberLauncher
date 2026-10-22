# 2026-10-22 FIX001–003 接管审查、纠正与验收

- 范围/验收者：Codex 接管 DSH 三 FIX 增量，修复需求偏差与遗漏，补 S4 验证；本记录不冒充整个阅读器计划完成。
- 源码：master HEAD cfeae53 + 既有未提交/未跟踪差异；保留现场，本轮未提交、未推送、未升版。实际版本仍 1.0.28（48）。
- 工具链：toolchain.path，JDK17、Gradle8.11.1；S4 SA4B03CN2608081096X，480×800。P4 本轮未连接。
- 任务：[FIX001](../../task/TASK-FIX-001-reader-review.md)、[FIX002](../../task/TASK-FIX-002-single-bookshelf.md)、[FIX003](../../task/TASK-FIX-003-special-icons.md)。

## 审查发现与实际纠正

| 问题 | 源码依据/影响 | 本轮处理 |
| --- | --- | --- |
| 书源单独占一行 | DeskSettingsView.buildSourceChoices；违背行数旁下拉要求 | 合为同一行，复用触点和单选对话框 |
| 最近阅读混入微信读书 | ShelfDeskSection 两侧均查 recent | 仅本地书源查/显示，独立于目录、权限与旧开关 |
| 返回保留旧列表 | renderedSource 非空便跳过 onResume；未完成读取取消后也可能停在加载态 | 返回重新加载有效缓存/数据库；取消继续传播，切源不清两侧缓存 |
| 搜索/书签旁路重排 | findNext/addBookmark 直接赋 action；ReaderBookmarks 登记没有完成回调 | 各动作接入 finishAction，旧 generation 丢弃；debug 只记录尺寸、busy、文本锚点 |
| 默认值掩盖字段存在性 | v11 迁移不区分原始 JSON 已有选择 | 正常加载和备份导入共用 hasShelfSource；已选默认值也保留 |
| v9 被再次执行 v8 模块迁移 | DSH 将两个历史迁移合并为 migrateV8ToV10 | 恢复独立 v8→v9、v9→v10，新增顺序保护回归 |
| 可见入口仍可能被旧配置恢复 | DesktopEntries 与主题 SPECIAL_ENTRIES 保留 special:bookshelf | 两处都移除可见定义，旧目的地继续兼容负一屏，补旧 hidden/order 键单测 |
| 目录更换和手动刷新失效 | 本地缓存只按时间；reload 不传 force | 缓存按目录匹配；手动刷新仅强制当前书源；缺权限不显示目录缓存 |
| FIX 未进入机器检查 | check-project-docs 仅扫描 RD | 同时校验 24 张 RD/FIX 卡，ID 带系列，跨系列依赖无环 |

## 最终门禁与产物

`check :app:assembleDebug :app:assembleRelease` 最终通过，39s，1564 tasks，退出 0；日志 `_analysis/verify/fix-gate-final.log`。每次发现实际漏项后追加必要修正与门禁，最终目录缓存/可见入口修正也已通过完整门禁。曾出现迁移函数长度/复杂度、行长、ReaderScreen.open 长度检查失败，已拆分职责/调整诊断位置解决，没有增加基线豁免。

- debug：6581875 字节，SHA-256 `9b043528ab7971b8c23e89e655a9514e2577f0bedd7316470a16a818141941a7`。
- release：1440841 字节，SHA-256 `85c875ada171183ed186550b789ca5553559d6d5b32970369bb52aba56fc63fc`。

最终 debug 单测报告（release 同轮 check 执行，下面不重复计数）：

| 模块 | 测试数 | 失败/错误 | 跳过 |
| --- | --- | --- | --- |
| core/common | 82 | 0 | 1 |
| core/data | 121 | 0 | 0 |
| feature/content | 58 | 0 | 0 |
| feature/reader | 78 | 0 | 0 |
| feature/home | 21 | 0 | 0 |
| feature/settings | 61 | 0 | 0 |

新增/调整回归覆盖原始字段存在性、四种旧开关组合、显式选择/未知值、幂等、负一屏行数保留、v9 模块顺序和特殊图标主题身份匹配。辅助规则测试不能证明 Android 调用点已接入，因此另做下列设备用例。

## S4 实际证据

| 用例 | 前提/操作 | 实际结果 | 证据/边界 |
| --- | --- | --- | --- |
| 同行控件 F1 | 负一屏设置，行数 2、当前微信读书 | −/2/＋与书源下拉中心 y=692，同一行；下拉选本地成功 | `_ui-out/fix-codex/controls/`、`source-choice/`、`local/` |
| 空目录/旧开关全关 F2 | 仅测试字段改成空目录、两个旧开关 false、来源 local | 本地空提示下仍显示最近阅读，从已有本地 EPUB 记录可进入阅读器 | `_ui-out/fix-codex/empty-local/`、`recent-open/`；未写入书籍内容，扫描器产生的空测试目录已以 rmdir 删除 |
| 打开中尺寸 F3 | 合成 plain 打开时改为 552×768 | resize busy=true，目标 552×519；最终 frame 尺寸匹配，锚点 3348 | `_analysis/verify/fix-codex/resize-open.log` 为打开期间更新目标；未证明实体旋转 |
| 搜索忙碌尺寸 F3 | 合成大文本查不存在 ASCII 词；搜索中改尺寸 | 19:07:48 resize busy=true，opened=480×551、target=552×519、anchor=0；19:07:55 最终 opened=552×519、anchor=0；恢复分辨率阅读页可用 | `_analysis/verify/fix-codex/resize-search.log`、`_ui-out/fix-codex/search-final/`；这是本轮关键补验，不用书签回归替代 |
| 旧设置收敛 | 从设置入口打开 | 显示“阅读与数据”，桌面不再列独立书架；旧目的地适配源码核对到 NegativeScreen | `_ui-out/fix-codex/settings-final/`；系统原生 Launcher 自带“书架”不属于十雨入口，观测必须核对包名 |
| 长按换图 F5 | 分别长按设置/文件 | 菜单含更换；未出现卸载/应用信息；选图自动返回，桌面实际回显 | `_ui-out/fix-codex/settings-menu/`、`settings-home-final/`、`files-picker/` |
| debug 持久化 F5 | 两目标换图，force-stop 后重启 | 两图保留；两个持久化目标独立；恢复设置后文件仍保留 | `_ui-out/fix-codex/icons-persisted/`、`settings-reset/`；技术字段检查，不输出偏好全量 |
| 美化/主题 F5 | 美化页分别选两目标换图；保存临时主题并应用 | 两项“已换”，临时主题包含 6 项图标，应用后两特殊键均保留且路径独立；只删除本轮临时主题 | `_ui-out/fix-codex/beautify/`、`theme-library/`、`theme-applied/`、`theme-delete/` |
| release 持久化 F5 | 同轮 release 覆盖安装，两目标换图并分别重启 | 两个自定义图标均保留、入口正常；随后恢复默认 | `_ui-out/fix-codex/release-persisted/`、`release-both/`；最终恢复见交接 |

## 恢复与未覆盖

- 测试目录和旧开关按基线逐字段合并恢复；最终来源 weread、行数 2。没有回滚数据库/阅读记录，保留期间新记录；凭据和正文未进入报告/Git。
- 两个特殊入口恢复默认；用户原有普通应用图标与主题不主动删除。临时 FIX-20261022 主题通过 UI 删除。窗口尺寸复位为物理 480×800，无 override。
- P4 未连接，已向用户请求接入；不能写双机通过。实体旋转与 P4 矩阵仍待补；S4 系统字体 1.3、连续变尺寸及搜索中离开已补验，见下方增量。真实 SAF 重选、Office、RD016 压力及 RD017 全量门禁不在本次结论内。
- 三卡交付状态为 READY_FOR_REVIEW（代码与 S4 证据就绪、设备全矩阵待补），不伪写 DONE；下一步按 [交接](../../memory/sessions/2026-10-22-fix-takeover-codex.md) 继续。

## S4 收尾补验

- 连续变尺寸：搜索 busy=true 时依次出现 target=552×519、480×551、552×519；最终只有最后目标的 frame，opened=552×519、anchor=0。证据 `_analysis/verify/fix-codex/resize-continuous.log`。
- 登记尺寸后离开：搜索 busy=true 改尺寸后 HOME 返回十雨桌面，日志只出现 resize、没有旧阅读器 frame；CLI 桌面只有 6 节点，无 reader_status。证据 `_analysis/verify/fix-codex/resize-exit.log` 与 `_ui-out/fix-codex/exit-home/`。
- 字体：系统 font_scale 从基线 1.0 临时改 1.3，实际负一屏同行控件边界仍完整；已恢复 1.0。证据 `_ui-out/fix-codex/font13/`，不把它当 P4 实测或实体旋转。
- 恢复检查：测试涉及的 integrations 字段与基线一致、source=weread、rows=2、两特殊覆盖均清除、普通图标键和桌面顺序保留，记录 `_analysis/verify/fix-codex/restoration.json`。没有打印偏好全量。

## 最后目录与入口收口

- 不重启进程，从实际“阅读与数据”把原目录改成本轮空目录，再回负一屏；原书目消失，空提示与最近阅读保留，证明未命中旧目录缓存。证据 `_ui-out/fix-codex/directory-local/`、`directory-edit/`、`directory-changed-live/`；随后逐字段恢复原目录/source，空目录仅在为空时删除。
- `DesktopEntriesTest.legacyVisibleShelfKeyDoesNotCreateShelfEntry` 覆盖旧配置取消隐藏且 order 保留旧书架键，书架不再生成而设置/文件仍生成。
- 设备验证分阶段进行：忙碌/图标持久化/主题用例在本轮相应修正包执行；之后修改限定为目录缓存、旧可见入口及注释，ReaderScreen 与图标实现未再变化。最终包重跑完整门禁、目录实时切换和 release 启动；未将所有先前用例写为最终同包重新执行。最终同包双机门禁仍由待补矩阵与 RD017 收口。
- 本轮执行也有改进：应在首轮构建前列全入口、缓存、迁移与直接赋 action 的调用点；本次边补验边发现漏项导致多次重建。后续按 36 约定先形成清单，再批量集成，减少反复。

## 用户范围确认（2026-10-22）

用户已回复“本轮先完成 S4，P4 留待补验”。S4 本轮收尾有效，P4 延后，不再阻塞本轮交付或后续独立工作；设备结论仍仅限已记录的 S4 场景，P4 不记通过，最终同包双机门禁由 RD017 补齐。此前“优先等待 P4”的建议由本条取代，后续先按依赖进入 RD016。
