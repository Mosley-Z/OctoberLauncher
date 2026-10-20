# feature:files

文件浏览、相册、图片查看、图片编辑与外部入口。文件页支持宫格与列表两种视图，图片可进入查看器或编辑页。

## 关键类

数据层在 `data/`：`FileListing.kt` 枚举目录与排序，`FileItem.kt` 描述条目，`FileOps.kt` 与 `FileStreams.kt` 完成复制、移动与比较，`FilePrefs.kt` 保存视图与排序偏好，`StorageAccess.kt` 解析存储卷与输出目录，`MediaIndex.kt` 维护媒体索引并提供 `recent` 查询，`CropMath.kt`、`EditSession.kt`、`EditExport.kt`、`Edits.kt` 负责编辑状态与导出。

界面层在 `ui/`：`BrowseScreen.kt` 浏览页，`GridPane.kt`、`ListPane.kt`、`BrowsePane.kt`、`PaneDraw.kt` 两种视图，`FilePager.kt` 与 `FilePagerChrome.kt`、`FilePagerTouch.kt`、`FilePaging.kt` 提供翻页容器，`FileMetrics.kt`、`FileTokens.kt`、`FileText.kt` 负责几何与文案，`ThumbPrefetcher.kt` 预取缩略图。

`PickPane.kt`、`PickEntryActivity.kt` 为选图入口，`PhotoEntryActivity.kt` 为外部查看入口。`ui/album/` 下 `AlbumScreen.kt`、`AlbumPane.kt`、`AlbumPaging.kt` 实现相册。`ui/viewer/` 下 `PhotoScreen.kt` 组合查看器，`ViewerChrome.kt`、`ViewerDecoder.kt`、`ViewerState.kt`、`ViewerMetrics.kt`、`ViewerText.kt` 分工绘制、解码与状态。`ui/edit/` 下 `EditScreen.kt` 组合编辑页，`EditSurface.kt`、`EditState.kt`、`CropView.kt`、`CropPanel.kt`、`RotatePanel.kt`、`FilterPanel.kt`、`SavePanel.kt` 分工交互与导出。`ui/negative/FilesNegativeModule.kt` 为负一屏卡片。`FilesModule.kt` 注册全部页面与卡片。

## 依赖

`api(project(":core:common"))`，`implementation` 依赖 `core:device`、`core:design`、`core:data`、`core:render`。

## 测试

`src/test` 二十个文件、186 例：条目与排序、分页与几何、查看器状态与命中、编辑会话与导出计划、媒体索引与负一屏卡片。

## 已知限制

公共目录读写依赖 `MANAGE_EXTERNAL_STORAGE`，未授权时只能浏览应用私有目录与媒体库。相册以 MediaStore 为主，索引重建期间可能短暂为空。编辑输出固定 PNG，长边按 1600 或 2400 导出。
