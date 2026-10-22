# 2026-10-22 Android CLI 界面观测工具接入

## 用户指令

用户于 2026-10-22 14:36 指令（m00345）："将Android CLI 的两个子命令正式融入本项目开发工作流并沉淀记忆，让其他AGENT也知道如何使用"。

范围限定为 Android CLI 的 `layout` 与 `screen capture`、`screen resolve` 三个子命令，`install`、`run`、`device remote`、`skills`、`sdk`、`studio` 与 `create`、`describe` 等未接入。

## 前期实验事实

- Android CLI 版本 1.0.16625370，Windows x86_64 单文件 `android.exe`，3,177,112 字节。
- 运行需要 `JAVA_HOME` 指向工具链 JDK 与 `ANDROID_HOME` 指向项目 SDK；用户目录用 `ANDROID_USER_HOME` 指定，默认在 `%TEMP%\android-home`。
- 首次运行会解包内嵌资源，冷启动约 11 秒；热态 `layout` 约 1.1 秒，`uiautomator dump` 在 S4 约 2.8 秒。
- `layout` 输出 JSON：顶层为窗口数组，节点字段 `class`、`text`、`resource-id`、`interactions`、`bounds`、`center`；默认只保留有文本或可交互节点，子节点键为 `content`；`--full` 输出全树，子节点键为 `children`。`center` 为 "[x,y]" 字符串。
- `screen capture --annotate --output=<png>` 在截图上叠加编号；`screen resolve --screenshot=<png> --string "tap #1 #26"` 输出 `tap x y` 坐标串。
- `input tap <target>` 的 target 为逗号分隔键值：`text`、`id`、`class`、`desc` 与布尔 `clickable`、`scrollable` 等；`--match=single|first`，`--wait=ms`。
- 十雨桌面图标、时钟、阅读统计条由 Canvas 自绘，文字不进 accessibility 树；`android input tap --match=first "text=设置"` 返回 `No matching elements`，按 resolve 坐标点击才有效。

## 本轮改动

- 新增 `scripts/android-ui.ps1`（约 350 行，UTF-8 带 BOM）：动作 `layout`、`annotate`、`resolve`、`tap`、`find`、`click`、`key`、`text`、`raw`；参数 Serial、Labels、Label、FindText、Selector、Value、Key、OutDir、Screenshot、Full、NoIdle、TimeoutSec、RawArgs。
- 脚本自行从 `toolchain.path` 读取 JDK 与 Android SDK，不依赖全局环境；CLI 候选顺序为 `ANDROID_CLI`、`_ui-out\android-cli\android.exe`、`_tools\android-cli.exe`、`_tools\android-cli\android.exe`、`<toolchain>\android-cli\android.exe`。
- 调用 CLI 统一用 `Start-Process` 单字符串参数、`WaitForExit` 与超时 `Stop-Process`，避免 CLI 写文件后进程驻留；退出码为空时以输出文件与 stdout 判断。
- adb 调用统一过滤 daemon 启动提示，避免 PowerShell 5.1 把 stderr 当终止错误。
- `.gitignore` 增加 `_ui-out/`；CLI 二进制与截图、布局 JSON 均不入库。
- `docs/design/33-dsh-development-playbook.md` §10 新增“Android CLI 界面观测”小节。
- `docs/design/35-development-workflow.md` §5 设备段增加脚本引用。
- `AGENTS.md` 接手顺序新增第 5 条。
- `memory/MEMORY.md` 新增界面观测一行。

## 设备验证（S4，SA4B03CN2608081096X，480×800）

| 动作 | 命令 | 结果 |
| --- | --- | --- |
| layout | `-Action layout` | 桌面 30 节点、文本 15；设置主页 30 节点；书架设置 60 节点、文本 38；桌面偏好 60 节点、文本 35 |
| annotate | `-Action annotate` | 480×800，109642 字节，编号 #1–#28 |
| resolve | `-Action resolve -Labels "#1 #3 #5 #7"` | `413 23 46 84 239 439 280 169` |
| tap -Label | `-Action tap -Label "#26"` | 输出 `242,681`，从桌面进入设置页 |
| tap -FindText | `-Action tap -FindText "书架设置"` | 输出 `354,210`，进入书架设置页 |
| click -Selector | `-Action click -Selector "text=桌面偏好"` | 进入桌面偏好页 |
| find | `-Action find -FindText "书架设置"` | 文本、bounds、center、可点击字段正确 |
| key | `-Action key -Key back` | 逐层返回，最终回到桌面 |

## 未覆盖与限制

- 选择器点击对自绘元素无效，必须用 annotate 与 resolve 获取坐标。
- 覆盖层场景：书架设置覆盖在设置主页之上时，被遮挡的“桌面偏好”仍在树中，选择器点击不生效；需先返回上一层再操作。
- P4（552×768）未连接，本轮只在 S4 验证。
- CLI 未参与 `install`、`run`、构建与云真机；构建仍走 `scripts/build.ps1`，安装仍用 `adb install -r`。
- 脚本的 `text`、`raw` 动作未在设备上验证。

## 下一步

- P4 接入后按相同动作用例复测，记录编号数量与坐标。
- 导航脚本可逐步改用 `layout` 与 annotate 定位，替换固定坐标。
- 若后续引入 `input scroll` 与 `--wait`，补充滚动容器用例。
