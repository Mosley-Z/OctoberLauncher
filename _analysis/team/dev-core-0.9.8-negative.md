# 0.9.8 负一屏文件区与最近图片卡片

## 一、目标与结果

负一屏新增「最近图片」卡片：取媒体索引中最近六张图片，以两行三列缩略图展示，点击进入查看器，卡片底部保留「全部图片」入口；没有图片或索引尚未就绪时展示「暂无图片」，卡片始终返回有效视图。缩略图统一经 `ThumbCacheRegistry` 取用，卡片自身不解码原图、不扫描目录。任务要求全部达成，测试与门禁通过，真机三张截图留档。

## 二、代码改动

| 文件 | 行数 | 改动 |
| --- | --- | --- |
| `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/data/MediaIndex.kt` | 643 | 新增只读查询 `recent(limit)`：取内存索引，过滤非图片条目，按时间倒序、同时间按路径升序，条数上限非正值返回空列表；不读磁盘、不触发重建。 |
| `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/ui/negative/FilesNegativeModule.kt` | 360 | 新增。`RecentImages` 纯逻辑对象与 `FilesNegativeModule` 卡片实现。 |
| `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/FilesModule.kt` | 61 | `install` 中注册负一屏卡片，按模块标识幂等判断。 |
| `feature/files/src/test/java/com/shuiyu/octoberrain/feature/files/ui/negative/RecentImagesTest.kt` | 148 | 新增 13 例纯 JVM 用例。 |

`RecentImages` 提供 `recentPaths(entries, limit)`、`viewerTarget(entries, index)`、`filesTarget()`、`emptyText`、`gridRows(count)`、`tileSize(contentWidth, gap)`，排序与截取复用 `MediaBuckets.sorted`，与相册页保持一致。`FilesNegativeModule` 实现 `NegativeModule`：`id = "negative_files"`、`label = "最近图片"`，`createView` 恒返回卡片视图；`onResume` 刷新最近条目，`onPause` 作废进行中的读取与缩略图任务。

注册判断说明：`ScreenRegistry` 只有 `register`，没有按目的地查询接口，无法在运行期直接判断 `Destination.Files` 是否已有工厂。文件页工厂与负一屏卡片在同一次 `install` 中先后注册，卡片内的「全部图片」入口因此总能解析；重复安装时以 `NegativeModuleRegistry.get("negative_files") == null` 幂等跳过，避免覆盖既有卡片。

## 三、卡片几何

卡片宽度取宿主内容区宽度，宿主容器左右各留一个 `pageGutter`；卡片内再留一个 `gap` 的内边距。设屏幕宽度为 W，则内容宽度 C = W − 2 × pageGutter − 2 × gap，单张缩略图边长 T = (C − 2 × gap) / 3，余数留空，保证三列等宽。设备实测参数（480 × 800，219 dpi，density 1.37）：pageGutter 27 px、gap 11 px、卡片宽度 426 px、内容宽度 404 px、缩略图 127 × 127 px；两行之间与列之间均为 11 px。标题使用 `InkWidgets.heading`，空态文案与底部入口分别使用 `caption` 与 `row`，卡片背景统一为 `InkWidgets.background(radius, PAPER, stroke, INK)`。

## 四、最近条目查询与取图

数据来源为 `MediaIndex`：`createView` 时先按内存索引渲染，随后在后台执行 `loadCached()`；磁盘索引缺失或损坏时执行一次 `rebuild()`，读取结果经令牌校验后回填。条目时间取 `takenAt`，为零时取 `addedAt`。缩略图按目标长边调用 `ThumbCacheRegistry.get(context, T).loadOrDecode(File(path)) { ImageDecoder.decode(path, T) }`，命中缓存直接绘制，未命中由缓存解码并持有位图；卡片不自行回收位图。

## 五、验证记录

| 命令 | 结果 |
| --- | --- |
| `gradle --offline :feature:files:compileDebugKotlin` | BUILD SUCCESSFUL |
| `gradle --offline :feature:files:testDebugUnitTest` | 20 套件 186 例 0 失败 0 跳过（本任务新增 13 例） |
| `gradle --offline :feature:files:detekt :feature:files:ktlintCheck` | BUILD SUCCESSFUL，本任务三个文件 0 条 |
| `gradle --offline :feature:files:testDebugUnitTest :app:assembleDebug check` | BUILD SUCCESSFUL，54 秒，1005 个任务，`app-debug.apk` 4206330 字节 |
| 行宽检查 | 本任务三个文件超 120 列 0 行 |

## 六、真机验证

设备 SA4B03CN2608081096X（480 × 800，Android 11）。验证前用 `run-as` 备份 `shared_prefs/octoberrain_config.xml`（4296 字节），并把同一份内容拉到主机留底。设备原配置开启了锁屏且负一屏模块列表只含书架，为进入桌面与加载卡片，临时把配置中 `lock.enabled` 置为 false、`negativePage.modules` 追加 `negative_files`，经标准输入写入应用目录后重启；验证结束把主机留底的原始配置写回并重启，锁屏界面恢复，写入后文件为 4292 字节（换行在传输中归一，语义与备份一致）。

| 截图 | 内容 |
| --- | --- |
| `_analysis/team/shots/t33-card.png` | 负一屏书架卡片下方为「最近图片」卡片，两行三列共六张缩略图，底部「全部图片」行带箭头；`t33-card3.png` 为重新进入页面后的同一状态。 |
| `_analysis/team/shots/t33-viewer.png` | 点击首张缩略图进入查看器，顶栏显示「返回 文件名 1/6」，底栏为旋转、裁剪、滤镜、另存；logcat `page=图片查看 create=18ms`、`解码 ... 下标 0 耗时 217ms`，路径列表取自卡片顺序。 |
| `_analysis/team/shots/t33-empty.png` | 写入空索引后重启，卡片显示「暂无图片」与「全部图片」入口。 |
| `_analysis/team/shots/t33-files.png` | 点击「全部图片」进入文件页，停留在上次目录 `Pictures`，底栏为上一页、页码、下一页、菜单。 |

另留 `t33-negative.png`（首次进入负一屏）、`t33-home.png`（桌面）、`t33-restored.png`（恢复配置后的锁屏界面）作为过程记录。空态截图通过临时写入内容为 `{"version":1,"updatedAt":0,"entries":[]}` 的 `files/media-index.json` 构造，重启后卡片不再重建索引，因而稳定显示空态；验证结束已删除该文件，下次进入相册或卡片时按正常流程重建。

## 七、判断记录

1. 写作用域内的 `feature/content/src/main/java` 未作改动：注册发生在 `FilesModule.install`，`ContentModule` 保持原样，避免两个模块重复注册。
2. 未修改 `MediaIndex` 的写入路径与 `Contracts.kt` 的 `Destination` 表；`recent` 仅读取内存索引。
3. 设备配置修改属于可回溯操作（先备份、后恢复），仅用于截图取证，不属于产品行为。
4. detekt 首轮报 `TooManyFunctions`（12 个函数）与 `NestedBlockDepth`，通过拆分行内视图构造与内联宽度计算把函数数降到 10，未把告警写入基线。

## 八、遗留事项

1. 卡片只在 `onResume` 与创建时刷新；应用停留在负一屏期间新增图片需重新进入页面才会更新。后续可在索引刷新完成后通过 `NegativeHost.requestRedraw` 主动推送。
2. `NegativeModule` 接口没有销毁回调，模块持有的协程作用域随进程存活，未做显式释放。
3. 真机未测试首次重建索引（数百张图片）时的卡片显示时序，仅以空索引与既有索引两种状态取证。
