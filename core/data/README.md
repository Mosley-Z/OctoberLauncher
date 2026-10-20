# core:data

配置存储与系统数据源实现。`AppServices` 的多数服务在此实现，页面通过接口取用。

## 关键类

`JsonConfigStore.kt`：以单键 JSON 保存 `LauncherConfig`，写盘成功后发布状态，写失败保留旧值并记录错误。

`ConfigPersistence.kt` 抽出持久化接口，默认实现基于 `SharedPreferences`，测试可替换为内存实现。`ConfigMigration.kt` 负责版本补齐与字段保留，迁移结果由存储层回写，高版本配置不清洗版本号。

`AppCatalogImpl.kt` 枚举可启动应用并缓存图标；`IconOverrideStoreImpl.kt` 与 `IconPackResolver.kt` 管理单应用图标与图标包。

`AppearanceRepositoryImpl.kt`、`ThemeRepositoryImpl.kt`、`WallpaperGeometry.kt`、`WallpaperQuantize.kt`、`WallpaperSwap.kt` 负责外观、主题与壁纸。

`NotificationCenter.kt` 维护通知快照，`SystemControlsImpl.kt` 控制亮度、前光与无线状态，`FontsRepository.kt` 枚举字体，`BackupServiceImpl.kt` 与 `UpdateServiceImpl.kt` 提供备份与更新，`MediaBridge.kt` 向组件层暴露媒体摘要。

## 依赖

`api(project(":core:common"))`、`api(project(":core:device"))`、`api(project(":core:design"))`、`api(project(":core:render"))`。

## 测试

`src/test` 七个文件、57 例：配置迁移、写盘失败与回写、JSON 容错、图标包解析、系统控件注册与释放。

## 已知限制

持久化基于 `SharedPreferences`，跨进程并发写不做仲裁；`SystemControlsImpl` 需由宿主显式调用 `start` 与 `dispose`。壁纸替换后的旧文件清理尚未完成。
