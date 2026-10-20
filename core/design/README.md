# core:design

设计令牌、配色与通用控件。页面不写死尺寸与颜色，统一从本层取值。

## 关键类

`InkTokens.kt`：以 `DeviceProfile` 换算间距、字号、描边与圆角，提供 `dp`、`dpF`、`textSize`。

`InkColors.kt`：纸白、墨色、次级墨色、分隔线与反色，墨水屏下保持黑白二值观感。

`InkWidgets.kt`：`InkPage` 页面骨架、标题、按钮、说明文字、卡片背景、分隔线与空白占位。

`InkDialogs.kt` 提供确认与输入对话框，`InkGlyphs.kt` 绘制矢量图标。

`ThemePalette.kt` 定义调色板，`ThemeTokensCodec.kt` 负责主题令牌的序列化与校验，`DesignRuntime.kt` 保存当前令牌供全应用读取。

## 依赖

`api(project(":core:device"))`，用于读取设备画像。

## 测试

`src/test` 两个文件、14 例：调色板取值与主题令牌编解码往返。

## 已知限制

控件以自绘 `View` 为主，无法复用系统 Material 组件的无障碍特性；新增控件须同时处理按下态与反色场景。令牌单位由密度换算，极端密度设备需复核字号下限。
