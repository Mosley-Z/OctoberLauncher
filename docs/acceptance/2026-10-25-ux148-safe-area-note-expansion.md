# UX148 P4安全区、本记展开与局部换条验收

- 任务：[UX148](../../task/TASK-UX-148-safe-area-note-expansion.md)，来源为用户确认UX147方案1/2/4及追加要求，换条策略参考WereadMoji。
- 实施/验收：Codex主代理；接手442项已有变更，保留全部差异。未升版、提交、推送或发布。
- 源码基线：当前工作树，改前范围快照 `_analysis/verify/ux148-20261025/before`；本轮改动位于app宿主、core/device、core/widget-api、home及widgets。
- 最终release：1.0.31/51，`app/build/outputs/apk/release/app-release.apk`，2193763字节，SHA256 `fc5f1ac0b3c917995e9abbfe6667122006fc516fe13f169e9e67b3ba42dd6a79`。
- debug：7333181字节，SHA256 `c269b118b56f819ae50f3510184c5401ad1bdd4a834f1bcf7ff17376a4b361fe`。
- 环境：toolchain.path配置的JDK17、Gradle8.11.1、Android35；P4 `P4L202610130006` 在线，型号P4/厂商Eink。S4本轮离线。

## 最终行为

| 需求 | 实现及实际消费者 |
| --- | --- |
| P4盖板补偿 | DisplaySafeArea仅匹配Eink/P4，物理左右各15px；横屏映射上下15px。DeviceProfileDetector输出能力，MainActivity根布局按当前宽高同步padding，壁纸/状态栏/内容/锁屏使用同一安全区域。不是15dp，不按分辨率误判其他设备 |
| 系统下拉 | P4使用Android标准临时系统栏滑出行为；没有恢复十雨旧下拉面板或引入悬浮窗权限。应用内安全区不能移动Android系统边缘手势区域，盖板与手指效果仍需实触确认；尚未追加依赖无障碍的内侧代理 |
| 横屏留白 | DesktopMetrics普通组件与ImmersiveHome轮播左边距在既有基础上加12dp，应用区位置与分栏比例不变，绘制和触摸共用矩形 |
| 横屏本记展开 | 整张卡片从原顶部到应用区右边界铺纸；使用独立卡片状态绘制完整抬头与元信息，保留原组件排版单位，Widget再按同一比例映射到实际像素。没有改变正文偏好或放大整张卡片 |
| 元信息 | 删除“划线与想法”，释放上方空间；横屏书名15→18、章节13→15逻辑字号。展开只留一份书籍信息；章节等于书名时不再重复显示 |
| 竖屏本记 | 既有阅读层保留，元信息仅一处并增大，纸面延伸至完整应用区，避免半截图标。没有新增独立页面 |
| 换条后的展开状态 | 原卡自然排版产生的溢出标记随Widget快照传至桌面；短条自动收起，长条保持展开，条目改变时滚动归零；以原卡容量判断，不用展开后的宽高判断 |
| 操作按钮 | 横竖屏上一条/换一条/收起均有分界和描边，绘制矩形与触摸一致；横屏沿用原字号/圆角，竖屏从原渲染捕获按钮字号，使用原方形描边，不再采用无边框小字 |
| 换条策略 | 对照WereadMoji NoteStore、CardContentController.showNoteItem/nextNote。保留每批40条、按书加权、选书范围和历史回退；prepareNote仅更新note/slot/progress，不重读书架、周月统计、封面或待办，不改变数据更新时间。同步继续使用现有刷新入口 |

原版参考是本地 `D:/WorkBuddyStation/WereadMoji` 源码，未修改或整份复制；其“本地有内容即显示、章节补全不阻塞显示”用于审查触发链。本次没有扩展云端接口或引入依赖。

## 验证证据

| 用例 | 结果及证据 |
| --- | --- |
| 统一门禁 | verify.ps1执行受影响模块ktlintFormat、全工程check、assembleDebug/Release，最终退出0、BUILD SUCCESSFUL。各变体1004项JVM测试、0失败/错误、1既有跳过；gate.log、summary.json |
| 设备识别/方向映射 | 新增DisplaySafeAreaTest验证P4/其他厂商/其他型号及横竖边距，ExpandedNoteGeometryTest验证扩宽不改变排版单位和抬头锚点，NoteMetadataTest验证同名去重 |
| P4真实View边界 | 竖屏Home为`[15,45][537,768]`，270°横屏Home为`[0,60][768,537]`，刷新FrameLayout横屏为`[18,21][50,53]`。原始界面仅私有目录，技术数字见safe-area-ui-bounds.json |
| P4实际界面 | debug核对90°/270°与竖屏壁纸边界/留白；竖屏实际点击展开、检查元信息及按钮，未点击用户笔记换条；270°通过标准View点击刷新控件。最终release回到原90°横屏本记，布局与APK摘要已核对 |
| 真实生产渲染探针 | NoteExpansionProbe在P4的app_process中加载最终debug的KernelWereadBoard、WereadBoardWidget适配层与横竖Overlay，使用隔离Context及合成笔记，不接触应用私有选记文件。portrait-probe.log、landscape-probe.log均PASS |
| 探针断言 | 整张横屏纸面包含原顶部；书名一份、无旧副标题；按钮字号与原卡一致；通过真实按钮矩形触发换条及收起；长→短自动收起、长→长保留展开、滚动归零、上一条回退；局部换条不调用其他ContentSource读取且数据更新时间不变 |
| 耗时边界 | 最终合成环境单次换条为竖34ms/横57ms，属于当前设备/合成数据的内核切换样本，不包含真实墨水屏刷屏时延，不宣称为真实全库延迟或性能倍数 |
| 合成画面 | landscape-normal/expanded.png、portrait-normal/expanded.png均为原生Canvas生成，已查看。不是实体S4截图，也不是用户正文 |
| 最终包 | P4覆盖安装release，installed.txt记录摘要匹配；实际运行版本1.0.31/51。未把debug探针结果冒称release完整笔记操作回归 |

## 修正与验证边界

- 探针发现竖屏收起分支遗漏，已补齐；组件层审查发现需要同时传递原卡溢出标记和绘制缩放，已修正并用真实Widget适配层重跑。
- 首个竖屏探针使用缺密钥占位态，Android未捕获断言表现为进程被终止；补齐全套离线模拟服务及合成密钥后，确认是探针前置条件，不归因设备内存。最终每次运行使用独立临时目录。
- 探针重置步骤曾被自动审批拒绝，理由是隔离范围不明。只读核实shell UID与应用700目录权限、ContextWrapper路径和生产文件派生路径后，移除删除步骤，增加canonical路径约束；在全新的`/data/local/tmp/october-ux148-probe/run-*`目录完成验证，未删除真实用户选记状态。合成临时目录保留复核。
- P4盖板遮挡宽度与手指触摸可达性不能由ADB证明。当前交付15px补偿及软件事件链；系统通知栏下拉是否已可达，需要用户实触反馈。
- S4当前离线，未安装或实测本轮APK；型号不补偿由单测验证。未伪造S4当前版本验收。
- 本轮主要合成探针为纯划线；原文与想法的既有排版代码保留，未重跑全部全库内容/跨日/云端同步矩阵。

## 用户状态与交接

- 仅临时修改P4方向用于竖屏和270°验证，已按字段恢复原90°普通横屏/本记。
- restoration.json：全局配置一致，note_selection.json一致；kernel_prefs.json唯一差异为`string.weread_sync_state`，其余偏好一致。保留最新同步状态，没有全量回滚阅读数据。
- 含正文截图与原始备份放私有临时目录；工作区中转路径被Git忽略，原始截图已移出。任务/记忆/验收只留技术数字和合成内容。
- 最终P4运行release；无活动构建或设备操作，未创建休眠任务。下一步为用户实体盖板触摸反馈，S4连接后补装/实测。
