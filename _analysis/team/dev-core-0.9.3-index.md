# 0.9.3 缩略图索引隔离与缓存回归

## 一、问题与修正

`ThumbCacheRegistry` 按「目标长边 + 抖动开关」创建多个 `ThumbCache` 实例，实例共用 `cacheDir/thumbs`，但索引文件固定为 `index.txt`。`ThumbCache.saveIndex` 每次重写整个文件且只包含当前实例的条目，因此实例 A 保存索引会抹掉实例 B 的条目；随后 B 的 `invalidate` 找不到键，磁盘 PNG 成为孤立文件，只能等 `trimDisk` 达到 32 MB 上限时淘汰。0.9.4 文件页会引入更多目标尺寸，孤立文件的积累会放大。

修正方式为索引按实例隔离：索引文件名包含目标长边与抖动标记，实例只读写自身文件；PNG 文件名本身含内容摘要，不需要改动；`trimDisk` 仍按目录内全部 PNG 累计计算上限。

## 二、改动文件

### 新增 `core/render/src/main/java/com/shuiyu/octoberrain/core/render/ThumbIndex.kt`

单个缓存实例的磁盘索引，内部持有映射与懒加载状态。

```kotlin
internal class ThumbIndex(
    private val directory: File,
    targetEdge: Int,
    ditherEnabled: Boolean,
) {
    fun file(): File
    fun add(digest: String, key: String)
    fun remove(digest: String): Set<String>
    fun keys(digest: String): Set<String>
    fun load()
    fun save()

    companion object {
        fun fileName(targetEdge: Int, ditherEnabled: Boolean): String =
            "index-$targetEdge-dither${if (ditherEnabled) 1 else 0}.txt"
    }
}
```

索引文件示例：目标长边 256、抖动开启为 `index-256-dither1.txt`；目标长边 128、抖动开启为 `index-128-dither1.txt`；目标长边 256、抖动关闭为 `index-256-dither0.txt`。

`load` 只读本实例文件，首次访问时载入一次；`save` 只写本实例文件；`keys` 在读取前执行懒加载，保证新建实例从磁盘重建后可直接查询。

### 修改 `core/render/src/main/java/com/shuiyu/octoberrain/core/render/ThumbCache.kt`

- 删除 `indexByPath`、`indexLoaded`、`loadIndex()`、`saveIndex()` 与常量 `INDEX_NAME`；
- 构造体内新增 `private val index = ThumbIndex(diskDir, targetEdge, ditherEnabled)`；
- `put` 写入磁盘成功后调用 `index.add(ThumbKey.pathDigest(path.absolutePath), key)` 与 `index.save()`；
- `invalidate` 改为 `index.remove(digest)`，键集合为空时直接返回，否则移除内存条目与 PNG 后 `index.save()`；
- `get`、`warmUp`、`stats`、`trimDisk`、`diskFile` 未改动；公开接口与 4 处接入点的调用方式保持不变。

`trimDisk` 仍遍历目录内全部 `.png` 并按修改时间从旧到新删除，与索引归属无关，因此多实例共存时的容量上限仍是 32 MB。

## 三、新增测试

`core/render/src/test/java/com/shuiyu/octoberrain/core/render/ThumbIndexTest.kt`，使用 JUnit4 `TemporaryFolder`，共 4 例：

1. `索引文件名按目标长边与抖动开关区分`：断言三种组合的文件名，确认实例指向不同文件。
2. `两个实例的索引互不覆盖`：目标长边 256 与 128 的实例先后写入同一来源路径，各自保存后重建实例，分别只能读到自身的键；磁盘上两份 PNG 同时存在。
3. `单实例失效保留其他实例条目`：256 实例执行失效并删除自身 PNG，重建后其键集合为空；128 实例重建后仍能读到自身的键，且对应 PNG 存在。
4. `旧版共享索引文件不再读取`：目录内保留 `index.txt`，新实例查询不到旧条目，确认兼容策略为「旧索引不参与读取」。

说明：`ThumbCache.get` 依赖 `Bitmap` 与 `LruCache`，在纯 JVM 单元测试中无法构造位图，因此测试在索引与磁盘文件层面验证隔离、失效与重建行为；`get` 的查找顺序（内存、磁盘 PNG）未改动，行为由既有实现保证。真机上一轮已验证两级缓存的命中路径。

## 四、旧缓存兼容

升级后旧 `index.txt` 不再读取，旧 PNG 也不会被新实例索引；这些 PNG 会在 `trimDisk` 触发容量上限时按修改时间自然淘汰。首次启动后新索引文件为空，缩略图按需重新解码写入，不产生错误状态。

## 五、门禁结果

命令与结果（工作目录 `D:\DSHStation\OctoberLauncher`）：

```text
gradle --offline :core:render:testDebugUnitTest :app:assembleDebug check
> BUILD SUCCESSFUL in 47s
1005 actionable tasks: 170 executed, 835 up-to-date
```

- `core:render` 单元测试 4 套件 32 例 0 失败（新增 4 例，原 28 例保持通过）。
- `check` 覆盖 ktlint、detekt、Android Lint 基线，全部通过；detekt 无新增告警。
- 源码行宽：`ThumbCache.kt`、`ThumbIndex.kt`、`ThumbIndexTest.kt` 超 120 列 0 行。
- 调试包 `app-debug.apk` 3733864 字节，与任务前一致。

## 六、遗留

- 旧 `index.txt` 未做迁移删除，随 `trimDisk` 自然清理；若后续需要立即回收磁盘空间，可增加一次启动清理。
- 索引文件本身不计入 32 MB 上限（每实例一行一条，量级为 KB）。
