# core:widget-api

桌面组件协议。组件实现只依赖本层，宿主通过注册表发现组件。

## 关键类

`WidgetApi.kt`：`WidgetView` 约定尺寸变更、配置变更、刷新、绘制、点击、长按与销毁；`WidgetHost` 提供宿主回调与配置入口；`BaseWidgetView` 提供尺寸缓存、画笔与重绘入口。

`WidgetCategory` 分时间、本地、微信读书与其他四类；`WidgetRefreshPolicy` 分分钟、事件与手动三档，`triggersOnMinute` 供宿主判断整分刷新。

`WidgetProvider` 声明标识、标题、分类、刷新策略与能力依赖，并创建视图；`WidgetRegistry` 提供运行时注册、查询与创建；`WidgetPlaceholder` 用于缺失组件的占位。

## 依赖

`api` 依赖 `core:common`、`core:device`、`core:design`、`core:render`、`core:data`，组件实现可使用其能力。

## 测试

`src/test` 一个文件、7 例：注册表登记、重复覆盖、按标识创建与缺失占位。

## 已知限制

`requiredCapabilities` 尚无宿主侧过滤，声明只由组件声明表测试校验；注册表为进程内单例，创建时机依赖模块安装顺序。
