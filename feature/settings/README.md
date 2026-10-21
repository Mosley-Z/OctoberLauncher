# feature:settings

设置中心与美化模式。设置项由声明表生成，页面只负责动作与自定义行。

## 关键类

`SettingsSpec.kt`：设置项声明表，20 个分组、72 条 `SettingSpec`，八类控件与七种生效方式；`SettingsSpecBuilders.kt` 构造声明，`SettingsSpecRenderer.kt` 渲染，`SettingEffects.kt` 把生效方式映射到刷新与重建。

声明按页面拆分：`AppearanceSpecs.kt`、`TraySpecs.kt`、`LockSpecs.kt`、`SystemSpecs.kt`、`BookshelfSpecs.kt`。

页面：`SettingsHomeScreen.kt` 首页卡片，`DesktopPrefsScreen.kt` 桌面偏好，`LauncherPrefsScreen.kt` 启动器设置，`AppearanceScreen.kt` 外观，`LockSettingsScreen.kt` 锁屏，`PermissionsScreen.kt` 权限，`SettingsSystemScreen.kt` 系统，`BookshelfSettingsScreen.kt` 书架，`AboutScreen.kt`、`UpdateScreen.kt`、`BackupScreen.kt` 与各类选择器。

美化模式：`BeautifyScreen.kt` 与四个面板 `BeautifyThemePanel.kt`、`BeautifyImagePanel.kt`、`BeautifyIconPanel.kt`、`BeautifyPreviewPanel.kt`，分页状态在 `BeautifyPreviewState.kt`，分页换算在 `DesktopPagination.kt`。

`ImageDirectoryStore.kt` 保存图片目录选择，`ThirdPartyLicenses.kt` 汇总许可文本，`SettingsModule.kt` 注册全部页面。微信读书密钥的校验与二维码录入位于 `feature:weread-app` 的 `ui/qr` 包，本模块保留书架数据相关的设置项。

## 依赖

`implementation` 依赖 `core:common`、`core:device`、`core:design`、`core:render`、`core:data`、`core:widget-api`。

## 测试

`src/test` 五个文件、60 例：声明表完整性与分组、分页换算、美化预览状态、控件取值、负一屏顺序与开关。

## 已知限制

页面基于自绘控件，长列表未做虚拟化；新增设置项需同时补声明与效果映射，缺失时按 `NONE` 处理。部分项依赖系统权限，未授权时只显示状态。
