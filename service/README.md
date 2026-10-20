# service

系统服务对接。通知监听与无障碍悬浮层在进程内运行，数据写入 `core:data` 供页面与组件读取。

## 关键类

`NotificationRelayService.kt`：实现 `NotificationListenerService`，订阅通知发布与移除事件，写入 `NotificationCenter`，供托盘与桌面组件读取。

`OverlayAccessibilityService.kt`：实现 `AccessibilityService`，提供返回键与托盘手势通道，管理悬浮状态栏的显示与收起。

`OverlayStatusBarView.kt`：绘制悬浮状态栏，处理拖动、点击与刷新。

## 依赖

`implementation` 依赖 `core:common`、`core:device`、`core:design`、`core:data`、`core:render`。

## 测试

`src/test` 尚未创建。服务行为依靠真机日志与界面走查核对。

## 已知限制

通知读取需要用户在系统设置中授权，未授权时服务连接但无数据；无障碍服务在部分厂商 ROM 上会被限制或延迟启动，需要用户手动保活。服务生命周期由系统调度，应用退出不保证立即断开。
