# feature:content

内容数据源与阅读页面。书架、微信读书、KOReader、天气与使用统计集中在此。

## 关键类

`ContentModule.kt`：注册内容相关目的地与负一屏书架卡片。`ContentRegistry.kt` 与 `ContentSourceImpl.kt` 实现 `ContentSource`。

数据层在 `data/`：`BookshelfRepository.kt` 扫描本地书籍，`FileLibrary.kt` 提供文件检索，`ContentPaths.kt` 解析目录，`CoverResolver.kt` 与 `EpubCover.kt` 生成封面，`KoreaderRepository.kt` 读取统计，`UsageRepository.kt` 汇总使用时长，`WeatherClient.kt` 请求天气，`WereadTransport.kt`、`WereadClient.kt`、`WereadCache.kt`、`WereadParsing.kt`、`WereadBucketParsing.kt`、`WereadStats.kt` 组成微信读书的请求、缓存与解析流程。

页面层在 `ui/`：`BookshelfScreen.kt`、`WereadScreen.kt`、`WereadCalendarScreen.kt`、`WereadSections.kt`、`WereadSectionViews.kt`、`KoreaderScreen.kt`、`SearchScreen.kt`、`RecentsScreen.kt`、`ContentScreen.kt`，通用绘制在 `ContentUi.kt`。

## 依赖

`implementation` 依赖 `core:common`、`core:device`、`core:design`、`core:render`、`core:data`。

## 测试

`src/test` 六个文件、49 例：封面命名、微信读书解析与分桶、统计聚合、路径与格式化。

## 已知限制

微信读书依赖内置密钥与账号状态，接口变动会影响解析；KOReader 统计依赖本地文件结构，缺失时降级为空。天气依赖网络，离线时显示缓存。
