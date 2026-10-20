# 0.9.6 微信读书数据层扩展实施报告

任务：task-26（revision 2，owner dev-config），0.9.6 第一阶段数据层。

## 一、文件与行数

新增：

| 文件 | 行数 | 内容 |
| --- | --- | --- |
| `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/data/WereadTransport.kt` | 69 | `RawResponse`、`WereadTransport` 接口、`HttpWereadTransport` |
| `.../data/WereadStats.kt` | 75 | `WereadPeriod`、`WereadBucket`、`WereadStats`、`WereadNote`、`WereadNotePage` |
| `.../data/WereadParsing.kt` | 183 | 周期起点、统计与笔记解析、时间戳归一 |
| `.../data/WereadBucketParsing.kt` | 118 | 分桶解析、序号与时间戳键换算、时长单位归一 |
| `.../data/WereadCache.kt` | 107 | 内存与磁盘两级缓存、六期淘汰、按模式失效 |
| `feature/content/src/test/.../FakeTransport.kt` | 92 | 固定响应传输与测试装配 |
| `.../test/.../WereadStatsTest.kt` | 228 | 统计解析 15 例 |
| `.../test/.../WereadCacheTest.kt` | 84 | 缓存 7 例 |
| `.../test/.../WereadNotesTest.kt` | 193 | 笔记 8 例 |

修改：

- `.../data/WereadClient.kt` 由 591 行增至 719 行：主构造改为注入 `WereadTransport` 与 `WereadCache`，保留 `constructor(context: Context)`；新增 `weekly`（:127）、`annually`（:130）、`overall`（:133）、`notes`（:141）；`month()`、`shelf()`、`highlights()` 签名与行为不变。
- `feature/content/build.gradle.kts`：新增 `testOptions.unitTests.isReturnDefaultValues = true`，使 `InkLog` 与 `android.util.Log` 在 JVM 单测中返回默认值。该文件不在任务写作用域内，属测试可运行的必要改动，请 Lead 确认。

## 二、接口与字段依据

统一请求：`POST https://i.weread.qq.com/api/agent/gateway`，Header `Authorization: Bearer <apiKey>`，body 顶层平铺 `api_name`、`skill_version`（1.0.5）与业务参数。依据 `readdata.md`「通用约定」与 `notes.md`「通用约定」。

统计：`/readdata/detail`，参数 `mode` 与 `baseTime`。字段依据 `readdata.md`「返回字段」：

- `totalReadTime` 单位秒；`readDays` 为天数；`dayAverageReadTime` 单位秒。
- `readTimes` 为以时间戳为键的字典或数组；键兼容秒级时间戳、毫秒级时间戳、`yyyy-MM-dd` 日期与周期内序号（1 起）。
- `dailyReadTimes` 作为 `readTimes` 缺失时的回退来源。
- 分桶时长按周期粒度归一：周与月超过 `100000` 视为毫秒，年超过 `2700000` 视为毫秒，总计超过 `31536000` 视为毫秒；总时长超过 `100000000` 视为毫秒。阈值常量集中在 `WereadBucketParsing.kt` 头部。
- 周期起点由 `WereadParsing.periodStartSeconds` 计算：周取周一、月取月初、年取年初、总计为 0。

笔记分页：`/user/notebooks`，参数 `count` 与可选 `lastSort`。字段依据 `notes.md`「笔记本概览」：`books[].bookId`、`books[].book.title`（缺失回退 `bookTitle`）、`books[].sort`、`hasMore`、`totalNoteCount`。游标取当前页最后一本书的 `sort`。

单本内容：`/book/bookmarklist`（`bookId`）返回 `updated[]` 与 `chapters[]`，依据 `notes.md`「划线」一节；`/review/list/mine`（`bookid`、`count`）返回 `reviews[].review`，依据 `notes.md`「个人想法」与 `review.md` 的字段表：`content`、`abstract`、`chapterName`、`createTime`。划线与想法合并后按 `createTime` 降序排列。

## 三、降级路径

- 密钥为空或集成关闭：直接返回空结果，不发起请求。
- HTTP 状态非 2xx 或网络异常：传输层返回 null，解析层不报错。
- `errcode` 非 0（含 `-2012` 密钥失效）：记录日志并返回 null 或空页。
- 笔记字段缺失：书名、章节、原文、想法、时间、深链降级为空字符串或 0；书目标识为空的书跳过。
- `hasMore` 为 0：不返回游标。

## 四、缓存设计

`WereadCache(directory, maxPeriods = 6)`：

- 键为 `mode + 周期起点`，内存 `LinkedHashMap` 与磁盘 JSON 文件两级；磁盘目录由 `WereadClient` 生产构造指向 `cacheDir/weread`。
- 写入后按文件名起点升序保留最新六期，超出删除最旧文件并同步清理内存；目录不可读时跳过淘汰，保留内存副本。
- `invalidate(mode)` 删除该模式的内存与磁盘条目，`invalidate()` 清空全部；`WereadClient.invalidate()` 一并调用整体失效。
- 已知取舍：六期上限按目录共享计数，周与月混合使用时某一模式可能被另一模式挤占。

## 五、测试

`feature/content` 共 35 例，全部通过：

- `WereadStatsTest` 15 例：三种统计模式解析、时间戳键与序号键、秒与毫秒归一、`dailyReadTimes` 回退、请求体平铺、HTTP 错误、`errcode` 非零、缺密钥与集成关闭、缓存命中与磁盘复用。
- `WereadCacheTest` 7 例：内存写入、模式分键、未命中、磁盘跨实例、六期淘汰、按模式失效、整体失效。
- `WereadNotesTest` 8 例：划线与想法合并排序、游标、空游标、请求携带游标、字段缺失降级、缺密钥、空书目标识、错误码。
- 既有 `CoverResolverTest` 5 例继续通过。

## 六、门禁结果

`gradle --offline :feature:content:testDebugUnitTest :feature:content:compileDebugKotlin :feature:content:detekt :feature:content:ktlintCheck`：BUILD SUCCESSFUL，detekt 与 ktlint 均为 0 告警。

全仓 `ktlintCheck detekt`：全部 ktlint 任务通过，全部 detekt 模块通过，唯 `:core:render:detekt` 失败：

```
core/render/src/main/java/com/shuiyu/octoberrain/core/render/ThumbCacheRegistry.kt:63:9:
Function parameter `context` is unused. [UnusedParameter]
```

该文件属 dev-core 的 0.9.3 收尾改动，不在本任务写作用域内，未修改。

## 七、未决问题

1. 更换密钥不会自动清理已有统计缓存，建议后续在设置页保存密钥后调用 `WereadClient.invalidate()`。
2. `/book/bookmarklist` 作为划线来源，与任务描述中「`/review/list/mine`（划线、想法）」存在接口分工差异，实际同时使用两者；如后续确认划线应走 `/review/list/mine`，可在 `fetchNotes` 内替换。
3. 每页最多拉取五本书的明细（`MAX_NOTE_BOOKS = 5`），单页笔记可能少于 `limit`；如需严格条数，需在后续版本调整为按游标持续拉取。
4. `feature/content/build.gradle.kts` 的 `testOptions` 改动待 Lead 确认归属。
