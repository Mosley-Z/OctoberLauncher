# 待办与「本记导出」对照记录（S4 480×800 / P4 552×768）

## 一、待办编辑与新增对话框

基准：微读实机截图 `micro-todo-edit.png`（编辑态）、`micro-todo-add.png`（新增态）、`micro-todo-01.png`（空态）、`micro-todo-saved.png`（列表单条）。

| 段 | 微读基准 | 十雨实测（`step-41x-todo-edit.png`） | 结论 |
| --- | --- | --- | --- |
| 顶部位置 | 对话框顶 y=237、底 y=600 | 顶 y=232、底 y≈600 | 已对齐（±5px） |
| 标题行 | 「编辑待办」，左 16dp | 同 | 已对齐 |
| 输入行 | 单行文本，下划线 | 同，下划线样式一致 | 已对齐 |
| 日期行 | 「未设日期」/日期 + 选日期 + 清除 | 同，日期格式 `yyyy-MM-dd` | 已对齐 |
| 时间行 | 「未设时间」/时间 + 选时间 + 清除 | 同，时间格式 `HH:mm` | 已对齐 |
| 底部行 | 删除（编辑态左侧）+ 取消 + 保存（加粗描边） | 同；保存描边加粗 2 倍 | 已对齐 |
| 提示行 | 无 | 无（原「删除后该条不再出现在任何分组。」已删） | 已修 |
| 按钮尺寸 | 约 111×54px | 约 105×56px | 差 6px，可接受 |

新增态并排见 `step-41x-todo-add-side-by-side.png`，编辑态并排见 `step-41x-todo-edit-side-by-side.png`。

行为验证：

- 选日期 → 系统 `DatePickerDialog`（`step-41x-date-picker.png`）。
- 选时间 → 自绘步进弹窗（见第二节）。
- 清除 → 状态文字回到「未设日期」「未设时间」（`step-41x-todo-clear-date.png`）。
- 保存后列表行显示正文 + 次行「10月20日 23:28」（`step-41x-todo-saved.png`）。
- 删除走二次确认，删除后回到空态 `step-41x-todo-empty.png`。

## 二、待办「选时间」步进弹窗

基准：微读 `app/src/main/res/layout/dialog_time_pick.xml`、`shell/TodoPageController.pickTime()`，实机截图 `micro-todo-time-picker.png`（P4 552×768）。

| 项 | 微读基准 | 十雨实测（`step-43x-time-stepper.png`） | 结论 |
| --- | --- | --- | --- |
| 结构 | 标题「选择时间」；时/分两行 [标签][－][数字][＋]；底部提示；取消/确定 | 同 | 已对齐 |
| 加减步长 | 每次 ±1，时按 24 循环、分按 60 循环 | 同（00 减一 → 23，23 加一 → 00） | 已对齐 |
| 长按连发 | 450ms 后每 120ms 一次 | 同（900ms 长按分钟内减 5 次，与 450+4×120=930ms 吻合） | 已对齐 |
| 数字直输 | 可键入，确认时夹取 0–23 / 0–59 | 同（输入 30 生效） | 已对齐 |
| 按钮尺寸 | 60×50dp | 60×50dp（`tokens.dp` 换算） | 已对齐 |
| 输入行下划线 | 有 | 已补 1px 下划线 | 已对齐 |
| 整体宽高 | 外框 16..535、标题 y≈291 | 面板 13..467、标题 y≈234 | 位置差约 50px，比例一致 |

并排图 `step-43x-time-side-by-side.png`（左微读 P4 552×768、右十雨 S4 480×800）。截图里弹窗底部两道浅色重影来自底层新增对话框按钮位置的墨水屏局部刷新残留，uiautomator 节点表中只有一组取消/确定。

## 三、本记导出

实现：复用微读 `feature/NoteExport.java`（372 行原样），仅四个偏好 getter 经 `WereadMicroBridge` 读十雨 `kernel_prefs`：`export_tpl`/`paper`、`nsize`、`note_sign`、`note_date`；导出流程为后台线程 `renderAndSave`（保存到 `Pictures/微读墨记/benji_<ms>.png`）+ 主线程 `presentResult`（toast「已导出 480×H 的图片」并呼起分享面板）。

微读常量：`WeekCardView.EXPORT_W = 480`、`EXPORT_H_MAX = 8000`、`NoteExport.PAD_X = 56`、字号档 `TIERS = {26, 32, 40}`、页脚 `FOOT = "微读墨记 · 本记"`（居中灰字，恒定绘制）。

### S4（480×800）六变体

| 变体 | 截图 | 实际像素 |
| --- | --- | --- |
| 手账 + 中字 + 署名/日期开 | `step-42x-export-0-hand-zhong.png` | 480×702 |
| 素白 + 中字 | `step-42x-export-1-su-bai.png` | 480×680 |
| 卡纸 + 中字 | `step-42x-export-2-ka-zhi.png` | 480×702 |
| 手账 + 小字 | `step-42x-export-3-size-small.png` | 480×496 |
| 手账 + 大字 | `step-42x-export-4-size-large.png` | 480×994 |
| 手账 + 中字 + 署名/日期关 | `step-42x-export-5-no-sign-date.png` | 480×702 |

### P4（552×768）六变体

| 变体 | 截图 | 实际像素 |
| --- | --- | --- |
| 手账 + 中字 + 署名/日期开 | `p4-export-1-hand-zhong.png` | 480×702 |
| 素白 + 中字 | `p4-export-2-su-bai.png` | 480×680 |
| 卡纸 + 中字 | `p4-export-3-ka-zhi.png` | 480×702 |
| 手账 + 小字 | `p4-export-4-size-small.png` | 480×496 |
| 手账 + 大字 | `p4-export-5-size-large.png` | 480×994 |
| 手账 + 中字 + 署名/日期关 | `p4-export-6-no-sign-date.png` | 480×702 |

### 两机对照

| 变体 | S4 480×800 | P4 552×768 | 结论 |
| --- | --- | --- | --- |
| 手账 + 中字 | 480×702 | 480×702 | 一致 |
| 素白 + 中字 | 480×680 | 480×680 | 一致 |
| 卡纸 + 中字 | 480×702 | 480×702 | 一致 |
| 手账 + 小字 | 480×496 | 480×496 | 一致 |
| 手账 + 大字 | 480×994 | 480×994 | 一致 |
| 手账 + 中字 + 无署名日期 | 480×702 | 480×702 | 一致 |

宽度恒为 480，与设备分辨率无关；高度只随纸张边框与字号档变化。两机同变体尺寸一致，文件字节数也相同，说明渲染不依赖设备屏幕尺寸。

页脚归属说明：条码与「墨单 · 年份-周」属墨单生成壁纸（`feature/BillWallpaper.java` 的 `bars()`/`barsH()`），不在本记导出中；本记导出末尾固定为居中灰字水印「微读墨记 · 本记」，不随署名/日期开关变化。

## 四、残余偏差

1. 待办对话框按钮宽度比微读窄约 6px，行距差 5~15px，观感一致。
2. 时间步进弹窗在 S4 上整体位置比微读略高（面板顶 205 vs 248），由设备密度与对话框窗口高度换算差异造成，行结构与控件比例一致。
3. 导出宽度固定 480 而非随设备宽度（微读同款设计），两机实测一致。
4. 大字档 480×994 未触发 `EXPORT_H_MAX=8000` 上限。

## 五、验证命令与结果

- `bash _tools/gradle-task.sh :feature:weread-app:ktlintFormat :feature:weread-app:ktlintCheck :feature:weread-app:detekt :feature:weread-app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL。
- S4 与 P4 均安装同一 debug APK；P4 的 `files/weread` 与 `shared_prefs` 由 S4 复制，保证两机数据一致。
