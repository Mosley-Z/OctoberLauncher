# 0.9.5 相册索引与时间轴页面：交付说明

## 一、任务范围

写作用域为 `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/data/**`、
`.../ui/album/**` 与 `feature/files/src/test`。`ui/viewer/**`、`ui/edit/**`、`ui/Browse*.kt`、
`ui/GridPane.kt`、`ui/ListPane.kt`、`core/common/Contracts.kt`、`FilesModule.kt`、清单与
`file_paths.xml` 未作修改。

## 二、交付文件

| 文件 | 行数 | 说明 |
| --- | --- | --- |
| `data/MediaIndex.kt` | 630 | 索引条目、日与月分桶、版本化 JSON 编解码、合并、持久化、查询与扫描 |
| `ui/album/AlbumScreen.kt` | 436 | 页面骨架、状态行、页码、权限提示、长按面板、查看器与分享入口 |
| `ui/album/AlbumPane.kt` | 418 | 时间轴绘制、三段分页、缩略图预取、命中测试 |
| `ui/album/AlbumPaging.kt` | 116 | 分段与分页纯函数，支持单日超过九张的续段 |
| `test/.../MediaIndexPureTest.kt` | 188 | 分桶、时间换算、编解码、合并用例 |
| `test/.../AlbumPagingTest.kt` | 188 | 分段、分页、页码夹取、顺序守恒用例 |

`ui/FilesScreen.kt` 在验收阶段临时增加过相册入口按钮，验收完成后已还原为 57 行原状。
相册入口由 `Destination.Album` 提供，`FilesModule.kt:37` 的 `is Destination.Album -> AlbumScreen()`
分支与 `Contracts.kt:270` 的 `data object Album` 由 dev-core 添加，本任务未改动这两处。

## 三、索引设计

数据来源为 `MediaStore.Images` 与目录扫描两路。首版只在媒体库结果为空时扫描目录，设备实测
发现推送进 `Pictures/thumbs-bench` 的 1001 张图片因媒体库尚未入库而被整体忽略，索引停留在
57 条。现改为两路始终合并：`MediaIndex.rebuild()` 以扫描结果为基底、媒体库条目按路径覆盖，
既保留宽高与拍摄时间，又覆盖未入库文件；`refresh(sinceMillis)` 同样合并增量扫描结果，
扫描条按修改时间过滤。

分桶由 `MediaBuckets.days` 与 `months` 完成，时间键与标题经 `MediaTime` 换算，时区作为参数
传入，便于用例覆盖跨月与跨年边界。条目时间取 `takenAt`，缺失时回退 `addedAt`。

持久化写入 `filesDir/media-index.json`，内容为版本号、更新时间与条目数组；先写临时文件再
替换。读取失败或版本不符时删除文件并转入重建。查询、扫描与磁盘读写均在 `Dispatchers.IO`
执行，对外返回 `MediaIndexData` 不可变快照，内部不持有 `Context` 以外的对象，也不保留
`Cursor`（查询在 `use` 内完成投影）。

## 四、页面结构

页面沿用 `FileMetrics` 与 `PaneDraw` 的几何与绘制约定，三列缩略图，缩略图来自
`ThumbPrefetcher` 两级缓存。顶部状态行显示总张数与索引更新时间；底部为上一页、页码、
下一页；音量键翻页与文件浏览页一致。

分页按段推进：一天最多九张为一段，每页最多三段，因此单页最多二十七张。超过九张的当天在
后续页显示「N月N日 · 续」，段头右侧标注「第 a–b 张 / 共 n 张」。设备上 1058 张图片对应
41 页；首版按三天一页且每天最多九张绘制，单日超过九张的部分无法翻到，本次已修正。

长按面板为「打开 / 详情 / 分享」三项：打开进入 `Destination.PhotoViewer`，详情列出路径、
尺寸、类型与时间，分享经 `FileProvider` 授予读取权限后调用系统选择器。空相册与建立索引
期间各有占位文案；重建期间先展示磁盘中的旧索引并标注更新时间。

## 五、性能数据

设备为 S4（SA4B03CN2608081096X，480×800），数据为 1001 张基准图（48×48 JPEG）加原有
57 张图片，共 1058 条。

| 指标 | 数值 |
| --- | --- |
| 首次全量索引耗时 | 1454 ms（含目录扫描与媒体库查询） |
| 索引文件大小 | 211,678 字节 |
| 日分组数 | 9 |
| 二次进入读取磁盘索引 | 494 ms（含一次增量扫描） |
| 页数 | 41 |

二次进入的 494 ms 主要来自增量刷新中的目录扫描。当前每次进入页面都会扫描一次
`Pictures` 与书籍目录，1058 条规模下耗时约 0.5 s，页面先展示旧索引因此不影响首屏。
后续可考虑按目录修改时间跳过未变更子目录，本次未实施。

## 六、验证结果

命令与结果：

```
gradle --offline :feature:files:testDebugUnitTest :feature:files:ktlintCheck \
  :feature:files:detekt :app:assembleDebug check
BUILD SUCCESSFUL
```

feature/files 单元测试 19 个测试类共 173 例全部通过，其中 `AlbumPagingTest` 14 例、
`MediaIndexPureTest` 15 例。`check` 覆盖各模块单元测试与 lint，未出现失败。detekt 与
ktlint 在 `feature/files` 无告警。构建过程中曾出现 `buildCells` 圈复杂度 16 超过阈值 15
与循环内多处跳转，已拆分为 `cellGeometry`、`appendBlock`、`blockCountText`、
`appendEntries` 几个方法后消除。

真机核对内容：时间轴首页与第 2 页（续段）、长按面板三项、点击进入查看器、分享入口可用。
截图位于 `_analysis/team/shots/`：`d-album1.png`、`d-album2.png`、`e-longpress.png`、
`e-viewer.png`、`b-album.png`（多日分组）、`z-final-home.png`（设备还原后的桌面）。

## 七、待确认事项

1. 设备上名为 `exif-orient-6-...` 的测试图片在相册与既有查看器中均显示为侧向。核对
   `core/render/ImageDecoder.decode` 已按 EXIF 归一，`PhotoScreen` 亦使用同一解码入口，
   两者表现一致，说明成因在图片自身的方向标记与像素排列，与相册绘制无关。未修改
   `core` 模块，建议后续在解码层统一核对。
2. 媒体库与目录扫描合并后，同一条目在媒体库入库前后会被扫描条目短暂覆盖字段（宽高为 0），
   下次刷新由媒体库条目覆盖回来。字段缺失不影响缩略图绘制，但详情中的尺寸可能先显示未知。
3. 索引未对已删除文件做即时剔除：`refresh` 只做合并，删除文件在下次 `rebuild` 后消失。
   当前页面重建条件包含索引缺失与首次进入，实际影响有限。

## 八、设备清理

基准图集 `/sdcard/Pictures/thumbs-bench` 已删除；应用配置从
`_analysis/team/shots/prefs-backup.xml` 还原，桌面顺序恢复为原状；`cache/thumbs` 与
`files/media-index.json` 已清理；最终安装包为不含临时入口的构建产物，启动与桌面显示正常。
