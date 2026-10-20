# feature:home

桌面本体。负责宫格与列表两种形态、分页、图标整理、组件宿主、负一屏与布局度量。

## 关键类

`HomeView.kt`：桌面主视图，绘制图标与组件，分派点击、长按、滑动与翻页；整分刷新只处理分钟策略组件。

`DesktopMetrics.kt`：按内容区计算图标尺寸、行列容量、组件槽与每页容量，三档形态共用同一套度量。

`DesktopEntries.kt` 与 `IconStore.kt`：维护入口列表、固定项与图标位图。

`NegativeScreen.kt`：负一屏，按配置顺序装配 `NegativeModuleRegistry` 中的模块，模块之间插入间距。

`HomeDesktopLayoutProvider.kt` 与 `HomeDesktopPreviewProvider.kt`：向设置页提供列数、首屏行数、页数与缩略预览。

`AppInfoScreen.kt` 展示应用详情，`HomeModule.kt` 注册桌面与负一屏目的地。

## 依赖

`implementation` 依赖 `core:common`、`core:device`、`core:design`、`core:render`、`core:data`、`core:widget-api`。

## 测试

`src/test` 一个文件、18 例：桌面度量在三种宽度下的图标、间距与容量换算。

## 已知限制

`WidgetRefreshPolicy` 的事件档尚无调度方，依赖组件内部定时器或数据通知；能力过滤未接入。552×768 与 824×1648 的布局只有单元测试覆盖，未在真机验证。
