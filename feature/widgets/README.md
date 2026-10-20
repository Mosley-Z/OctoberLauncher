# feature:widgets

桌面组件实现与组件选择页。所有组件实现 `WidgetProvider`，在模块安装时注册。

## 关键类

时间类：`ClockWidget.kt`、`CalendarWidget.kt`，表盘样式在 `ClockFaces.kt`，选择页为 `ClockStyleScreen.kt`。

本地类：`ReadingWidget.kt`、`CoversWidget.kt`、`HeatMonthWidget.kt`、`UsageWidget.kt`、`ImageWidget.kt`。

微信读书类：`WereadCardWidget.kt`、`WereadMarkWidget.kt`、`WereadNoteWidget.kt`、`WereadStatsWidget.kt`、`WereadBillWidget.kt`、`WereadRankWidget.kt`、`WereadFingerprintWidget.kt`，数据整理在 `WereadWidgetData.kt` 与 `WereadWidgetLogic.kt`。

其他：`WeatherWidget.kt`、`PlaceholderWidget.kt`。

`WidgetCatalog.kt` 汇总 18 条声明，`InkWidgetView.kt` 提供基类，`WidgetPickerScreen.kt` 为添加入口，`WidgetsModule.kt` 完成注册。

## 依赖

`implementation` 依赖 `core:common`、`core:device`、`core:design`、`core:render`、`core:data`、`core:widget-api`。

## 测试

`src/test` 两个文件、26 例：声明表完整性、分类与刷新策略、能力声明、时间与统计格式。

## 已知限制

天气组件依赖 Open-Meteo 网络请求，离线时显示缓存或占位；微信读书组件依赖内置接口密钥与账号状态。`requiredCapabilities` 未参与宿主过滤，缺权限时组件自行降级。
