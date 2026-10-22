# 2026-10-22 Android CLI 界面观测工具验收记录

- 范围/验收者：Android CLI `layout`、`screen capture`、`screen resolve` 封装为 `scripts/android-ui.ps1`，Lead 执行，用户指令 m00345。
- 源码提交或基线+差异范围：master 未提交工作区；本轮新增 `scripts/android-ui.ps1`，修改 `.gitignore`、`docs/design/33-dsh-development-playbook.md`、`docs/design/35-development-workflow.md`、`AGENTS.md`、`memory/MEMORY.md`、`memory/STATUS.md`，新增本记录与 `memory/sessions/2026-10-22-android-cli-tooling.md`。业务源码未改。
- APK路径/版本/摘要：本轮未构建，未安装 APK；设备使用既有安装。
- 环境/工具链/WebView/设备serial：Windows PowerShell 5.1；JDK 17.0.20_12 与 Android SDK 取自 `D:\DSHStation\.toolchain`（`toolchain.path`）；Android CLI 1.0.16625370 位于 `_ui-out\android-cli\android.exe`；设备 S4，serial SA4B03CN2608081096X，Android 11，API 30，480×800，219dpi。
- 关联任务/设计：`docs/design/33-dsh-development-playbook.md` §10；`docs/design/35-development-workflow.md` §5。

| 用例/命令 | 前置条件与操作 | 预期 | 实际/退出码 | 证据路径 | 结论 |
| --- | --- | --- | --- | --- | --- |
| layout | 桌面，`-Action layout -Serial SA4B03CN2608081096X` | 输出节点 JSON 并打印文本节点 | 节点 30、文本 15、可点击 14；文本含 14:42、100%、十雨、微读墨记、书架设置等 | `_ui-out\20261022-144220\layout.json` | 通过 |
| layout 设置页 | 设置主页 | 输出页面节点 | 节点 30、文本 15、可点击 14 | `_ui-out\20261022-144526\layout.json` | 通过 |
| layout 书架设置 | 书架设置页 | 输出页面节点 | 节点 60、文本 38、可点击 22；含 书籍来源、本地目录、刷新间隔等 | `_ui-out\20261022-144409\layout.json` | 通过 |
| layout 桌面偏好 | 桌面偏好页 | 输出页面节点 | 节点 60、文本 35、可点击 23；含 外观与主题、素白·100%、宫格·自动等 | `_ui-out\20261022-144548\layout.json` | 通过 |
| annotate | 桌面，`-Action annotate` | 生成带编号截图 | 480×800、109642 字节，编号 #1–#28 | `_ui-out\20261022-144444\screen-annotated.png` | 通过 |
| resolve | 已生成标注图，`-Action resolve -Labels "#1 #3 #5 #7"` | 输出坐标串 | `413 23 46 84 239 439 280 169` | 命令 stdout | 通过 |
| tap 标号 | `-Action tap -Label "#26" -Screenshot <标注图>` | 点击 #26 进入设置页 | 输出 `242,681`，页面变为设置主页 | `_ui-out\20261022-144526\layout.json` | 通过 |
| tap 文本 | `-Action tap -FindText "书架设置"` | 点击进入书架设置 | 输出 `354,210`，页面变为书架设置 | `_ui-out\20261022-144409\layout.json` | 通过 |
| click 选择器 | `-Action click -Selector "text=桌面偏好"` | 点击进入桌面偏好 | 页面变为桌面偏好 | `_ui-out\20261022-144548\layout.json` | 通过 |
| find | `-Action find -FindText "书架设置"` | 输出文本、bounds、center、可点击 | 文本 书架设置、bounds [266,198][442,223]、center [354,210]、可点击 False | 命令 stdout | 通过 |
| key | `-Action key -Key back` | 逐层返回 | 两次 back 回到桌面 | 设备界面 | 通过 |

说明：

1. 十雨桌面图标、时钟、阅读统计条为 Canvas 自绘，文字不进 accessibility 树；`android input tap --match=first "text=设置"` 返回 `No matching elements`，此类元素必须用 annotate 与 resolve 取坐标后点击。
2. 书架设置覆盖在设置主页之上时，被遮挡的“桌面偏好”仍在节点树中，选择器点击不生效；需先返回上一层。
3. P4 未连接，本轮只在 S4 验证；`text` 与 `raw` 动作未验证。
4. 设备状态已恢复至桌面，未安装或卸载 APK，未修改应用数据。
