# 待办页对照微读墨记

- 设备：SA4B03CN2608081096X（S4），480×800。
- 微读参考：源码与资源（`weread-widget/app/src/main/res/layout/activity_todo.xml`、`item_todo.xml`、`shell/TodoPageController.java`、`strings.xml`）。S4 上微读为共存静默态，`am start -n com.inkread.weekread/.shell.TodoActivity` 后立即被十雨抢回前台；P4 由其他成员用于实验室与待办对照，故本轮无微读待办实机截图。
- 十雨实现：`feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadTodoPage.kt`（本轮重写）。

## 对照表

| 项目 | 微读基准（源码） | 十雨实测（S4 480×800） | 结论 |
| --- | --- | --- | --- |
| 页面结构 | `SegTabView` 两段「待办列表 / 已完成」，页面顶部无其它导航；高 44dp | 全局主导航（本周▽/设置/实验室/待办，77px）下方再接 `SegTabView` 两段「待办列表 / 已完成」 | 多一层全局导航，属内置 App 架构差异，保留 |
| 底部按钮 | 横向两等宽 `Button`：「＋ 添加待办」「排序」（排序态「完成排序」），行 padding 16/16/8/12、margin 6dp | 同位置、同文案、同比例、同间距 | 一致（`step-400-todo-tenrain.png`、`step-403-todo-sort.png`） |
| 条目行 | `CheckBox` + 内容列：正文 17sp `#000`、时间 13sp `#888`（无时间隐藏）；上下 padding 8dp | `CheckBox` + 正文 17sp、时间走 caption（`whenLabel` 非空才显示）；上下 padding 8dp、左右 16dp | 一致（`step-402-todo-list.png`） |
| 完成态 | 勾选后正文划线灰色 | 勾选后 `STRIKE_THRU_TEXT_FLAG` + `InkColors.INK_3` | 一致（`step-404-todo-done.png`） |
| 排序态 | 显示 ↑↓（48dp、22sp），首条 ↑ 与末条 ↓ 灰显，点条目不编辑 | 同：48dp ↑↓、首末灰显禁用、点内容列不编辑 | 一致（`step-403-todo-sort.png`） |
| 空态 | 居中 #888 15sp：「还没有待办，点下面「添加待办」记一条」「还没有已完成的事项」 | 同文案、`tokens.textHeading`、`InkColors.INK_3`、居中 | 一致（`step-407-todo-empty.png`） |
| 点击行为 | 普通态点条目弹编辑对话框；勾选框切换完成 | 同；编辑对话框含标题、内容、删除（二次确认）、取消、保存 | 一致，例外见下（`step-405-todo-edit.png`） |
| 长按行为 | 条目无长按；时间选择器内长按 450ms 连续步进 | 条目无长按 | 一致 |

## 差异与偏差

1. 编辑对话框暂未实现微读的「选择日期 / 选择时间 / 清除日期时间」：新增与编辑只改内容，原日期时间原样保留。微读对应 `DatePickerDialog` 与自绘时间步进对话框，本轮不做，列为后续项。
2. 子页签高度沿用 `tokens.topBarHeight`（77px），微读为 44dp；按 S4 密度 1.75 换算 44dp=77px，实测一致。
3. 微读列表底部有 `CardA11yService.sync()` 同步桌面卡；十雨待办数据走 `AppServices.wereadTodos`，桌面卡由内容刷新链路重绘，本轮未额外触发。

## 证据

| 截图 | 内容 |
| --- | --- |
| `step-400-todo-tenrain.png` | 待办页空态：主导航、待办列表/已完成、空态文案、底部两按钮 |
| `step-402-todo-list.png` | 新增两条待办后的清单 |
| `step-403-todo-sort.png` | 排序态：↑↓ 与「完成排序」 |
| `step-404-todo-done.png` | 已完成页：勾选与划线 |
| `step-405-todo-edit.png` | 编辑对话框 |
| `step-407-todo-empty.png` | 清理测试数据后的空态 |
