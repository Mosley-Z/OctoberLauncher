# 0.9.4 P3 查看器移植：实现记录

记录 task-23 的 P3 阶段实现。第一阶段准备结论见 [dev-core-0.9.4-p3-plan.md](dev-core-0.9.4-p3-plan.md)，本文件记录代码落地与验证数据。

## 一、已交付范围

按 lead 的分阶段安排，先完成不依赖 P2 界面宿主的纯逻辑与查看器骨架，目录限定为 `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/ui/viewer`。

### 1. 数据与纯逻辑

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `data/Edits.kt` | 199 | 旋转另存：解码 1600 长边、按累计次数取模 4 计算角度、PNG 输出、写后失效缩略图与内容刷新 |
| `ui/viewer/ViewerState.kt` | 214 | 三档缩放、灰度开关、换页与下标夹取、按档位计算解码目标像素、预取下标、结果代际校验 |
| `ui/viewer/ViewerText.kt` | 90 | 省略号截断、大小与时间格式化、格式标签 |
| `ui/viewer/ViewerMetrics.kt` | 165 | 顶栏、内容区、返回、信息、标题、底栏与面板行的几何计算 |

`Edits.kt` 约束：计数键为 `edited_rotations:<绝对路径>` 存于 `files_prefs`；输出名为 `原名-编-yyyyMMdd-HHmmss.png`；来源位于缓存目录时写入 `Pictures/十雨桌面`，其余写入源目录；写盘前检查线程中断，失败删除残缺文件。

### 2. 查看器骨架

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `ui/viewer/ViewerChrome.kt` | 229 | 动作枚举、命中判定、顶栏与底栏绘制 |
| `ui/viewer/ViewerDecoder.kt` | 75 | 按请求解码，输出灰度或四档量化位图 |
| `ui/viewer/PhotoScreen.kt` | 约 240 | 页面契约实现、后台解码、等比绘制、返回关闭 |

- 命中顺序：返回、信息、底栏四项。顶栏标题区与内容区不返回动作。
- 底栏动作固定为旋转、裁剪、滤镜、另存，几何按四等分。
- 解码在后台守护线程执行，`AtomicInteger` 代际用于丢弃过期结果；页面销毁回收位图。
- 当前阶段左右切换、预取、动作面板与详情面板尚未接入。

### 3. 测试

| 文件 | 例数 | 覆盖 |
| --- | --- | --- |
| `ui/ViewerStateTest.kt` | 12 | 下标夹取、翻页边界、档位循环、三档目标像素、预取剔除、结果校验、灰度开关、文本格式化 |
| `data/EditsTest.kt` | 7 | 角度循环、输出名、目标目录判定、计数取模 |
| `ui/viewer/ViewerChromeTest.kt` | 8 | 返回与信息命中、标题区与内容区不命中、底栏四等分顺序、动作文案、窄视口 |
| `ui/viewer/ViewerDecodePlanTest.kt` | 5 | 适配取短边、填充取长边、原尺寸取两倍、上限约束、灰度关闭不量化 |

## 二、验证记录

命令与工作目录：

```text
cd D:\DSHStation\OctoberLauncher
gradle --offline :feature:files:compileDebugKotlin :feature:files:testDebugUnitTest ^
                :feature:files:ktlintCheck :feature:files:detekt
```

结果：

| 检查 | 结果 |
| --- | --- |
| 编译 | 通过 |
| 单元测试 | 16 套件 130 例，0 失败 0 跳过 |
| ktlint（主源集与测试源集） | 0 条 |
| detekt | 0 条 |
| 行宽 | viewer 目录 5 个文件超 120 列 0 行 |

处理记录：

- 测试函数名使用反引号中文，避免 ktlint 命名规则把中文标识符判为违规。
- `ViewerHitTest.hit` 改为单个 `when` 表达式，满足 detekt `ReturnCount` 阈值 2。
- `bottomActions` 赋值换行后接 `listOf(`，满足 `multiline-expression-wrapping`。
- `PhotoSurface` 底色矩形参数顺序修正为左、上、右、下。

## 三、待接入

等待 P2 交付后处理：

1. `Contracts.kt` 追加 `Destination.PhotoViewer(paths, index)` 与 `displayLabel` 分支。
2. `FilesModule.kt` 增加 `ScreenProvider` 分支。
3. `PhotoEntryActivity` 与 `PickEntryActivity` 实现外部入口与选图回传。
4. `feature/files/src/main/AndroidManifest.xml` 的入口声明核对。
5. `app/src/main/res/xml/file_paths.xml` 增加 `external-media-path` 条目，二次存储卷分享优先走该条。
6. `GridPane` 与 `ListPane` 的图片点击改为路由跳转。
7. 左右切换、前后预取、动作面板与详情面板。
8. 真机验证：EXIF 旋转、三档缩放、分享与旋转另存。
