# feature:tray

状态栏、快捷托盘与托盘偏好。下拉托盘承载亮度、无线开关、媒体与通知。

## 关键类

`StatusBarView.kt`：自绘时间、电量、充电状态与通知计数，点击空白区打开托盘。

`TrayPanelView.kt`：托盘面板，绘制日期电量、快捷开关、冷光与暖光档位、媒体控制与通知列表。

`TrayControllerImpl.kt`：实现 `TrayController`，维护展开状态并与刷新调度协作。

`TrayPrefsScreen.kt` 与 `NotificationPrefsScreen.kt`：托盘与通知显示设置。

`TrayModule.kt`：注册状态栏与托盘目的地，完成模块装配。

## 依赖

`implementation` 依赖 `core:common`、`core:device`、`core:design`、`core:render`、`core:data`。

## 测试

`src/test` 尚未创建。托盘交互以真机截图与手动走查核对。

## 已知限制

托盘开关依赖系统权限与设备支持，部分机型无蓝牙或前光时按不可用处理；通知列表受通知读取权限约束。缺少单元测试，纯逻辑部分后续可抽出。
