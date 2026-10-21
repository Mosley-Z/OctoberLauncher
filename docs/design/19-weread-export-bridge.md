# 微读墨记导出桥接口说明

适用版本：微读墨记 V1.3.2-beta（versionCode 48）起
接收端：十雨桌面 V1.0.1 起的 B5 导入端
更新日期：2026-10-20

本文件描述微读墨记向十雨桌面导出数据的跨应用接口，包含调用方式、参数、返回字段、错误码、文件格式与条目清单。两侧实现以本文件为唯一约定，字段增删与语义变化需同步修订本文件并提升清单 schemaVersion。

## 一、调用方式

微读墨记声明组件 `com.inkread.weekread/.bridge.ExportActivity`，exported 为 true，主题为半透明、不进入最近任务。十雨桌面通过显式 Intent 调用并接收结果：

- 动作 EXPORT：`com.inkread.weekread.action.EXPORT`，执行导出。
- 动作 PROBE：`com.inkread.weekread.action.PROBE`，只返回版本与可导出条目概况，不写文件。
- 动作 WRITE_PREFS：`com.inkread.weekread.action.WRITE_PREFS`，把十雨侧配置回写为微读侧偏好，详见第十一节。

调用方式为 `startActivityForResult`，结果经 `onActivityResult` 读取。调用方包名固定为 `com.shuiyu.octoberrain`，安装包为调试签名时兼容 `com.android.shell`；其他来源一律以错误码 1 拒绝。

## 二、请求参数

| 键 | 类型 | 说明 |
| --- | --- | --- |
| `target_uri` | string | 接收端创建并授予写权限的 content:// Uri，导出件完整写入该 Uri |
| `bridge_key` | string | 32 字节对称密钥的 Base64 编码，长度 44 字符，补位符为 `=` |
| `bridge_token` | string | 一次性令牌，长度不小于 16 字符 |
| `write_payload` | string | 写入配置动作的请求体 JSON；与 `target_uri` 二选一，前者优先 |

校验规则：`target_uri` 必须为 content 协议；`bridge_key` 解码后必须恰为 32 字节；`bridge_token` 去除首尾空白后长度不小于 16；任一项缺失或不合规以错误码 4 拒绝。令牌在微读进程内按值记忆，同一令牌在进程存活期内第二次使用以错误码 4 拒绝。

写入配置动作的参数约定：请求体优先取 `write_payload` 字符串；未提供时读取 `target_uri` 指向的 content:// 文本文件，上限 64 KiB。该动作不要求 `bridge_key`。

## 三、返回字段

成功时回传以下字段：

- `manifest_digest`：清单文本行的十六进制小写 sha256，计算对象为 UTF-8 字节，不含行尾换行符。
- `pack_sha256`：写入 Uri 的完整文件字节的十六进制小写 sha256。
- `entry_count`：条目数量。
- `counts_books`、`counts_notes`、`counts_bills`：书目、摘录、账单条目数，供接收端核对。
- `source_version_name`、`source_version_code`：导出方版本名与版本号，当前为 `1.3.2-beta` 与 48。
- `write_count`：写入配置动作实际写入的配置项数量。
- `desk_prefs_updated_at`：桌面偏好最近写入时间，毫秒；本次未写桌面偏好时为既有值，从未写过时为 0。

失败时回传 `error_code` 与 `error_message`。接收端以 error_code 是否存在判定成败，不使用结果码以外的约定。

## 四、错误码

| 码 | 含义 |
| --- | --- |
| 1 | 调用方身份不合法 |
| 2 | 写入空间不足 |
| 3 | 没有可导出的数据 |
| 4 | 请求参数不合法，含令牌重复使用 |
| 5 | 读写或加密过程失败 |

写入配置动作适用错误码 1、4、5：调用方身份不合法、请求参数不合法、读写失败。写入动作不产生 2、3。

## 五、文件布局

导出件为单文件，三段顺序拼接，无额外头部与尾部：

```
清单字节（UTF-8 JSON，单行）  '\n'  初始向量（12 字节）  密文（含 16 字节认证标签）
```

清单保持明文，接收端可先读清单取得条目名、明文字节数与摘要，再解密容器；密文解密失败时仍可依据清单报告条目与版本。加密算法为 AES-256-GCM，无填充，初始向量 12 字节，认证标签 128 位，每次导出重新生成初始向量。

## 六、容器结构

容器明文为大端序二进制，结构如下：

| 偏移 | 类型 | 内容 |
| --- | --- | --- |
| 0 | 4 字节 | 魔数 `WRMK` |
| 4 | int32 | 容器版本，当前为 1 |
| 8 | int32 | 条目数量 |
| 12 | 变长 | 逐条目记录 |

逐条目记录：名称长度（int16，无符号解释）、名称（UTF-8）、数据长度（int32）、数据。所有整数为大端序有符号表示，长度字段为负数时按结构不合法拒绝。接收端校验魔数与版本后逐条解析，条目数量与实际记录数量不一致时整体拒绝。

## 七、清单字段

清单为单行 JSON，字段如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `schemaVersion` | int | 清单结构版本，当前为 1 |
| `sourceVersionName` | string | 导出方版本名 |
| `sourceVersionCode` | int | 导出方版本号，当前为 48 |
| `exportedAt` | int64 | 导出时刻的毫秒时间戳 |
| `encrypted` | object | 加密描述对象，见下 |
| `entries` | array | 条目数组，每项含 `name`、`bytes`、`sha256` |
| `counts` | object | 含 `books`、`notes`、`bills` 三项整数 |

`encrypted` 为对象而非布尔：`cipher` 为 `AES/GCM/NoPadding`，`keyBits` 为 256，`tagBits` 为 128，`container` 为 `WRMK`。接收端逐项校验，取值不符时按结构不合法拒绝，不尝试降级解密。

`entries` 中的 `name` 即落位后的相对文件名，`bytes` 为明文数据字节数，`sha256` 为明文数据的十六进制小写摘要。接收端解密后逐条复算摘要并与清单比对，不一致时整包拒绝。

## 八、条目清单

条目按下列顺序生成，顺序固定以便复算：

原盘数据文件（取自应用 filesDir 根目录，文件名保持原样）：`achievement.json`、`nt_index.json`、`nt_batch.json`、`nt_batch_i.json`、`nt_batch_p.json`、`shelf.json`、`shelf_ids.json`、`book.json`，以及所有以 `nt_`、`ni_` 开头且以 `.json` 结尾的文件，分别对应书目档案与摘录档案。

内核偏好聚合：`kernel_prefs.json`，JSON 对象，含 `string`、`long`、`int`、`bool` 四个对象分组，键名沿用微读原版 SharedPreferences 键名，值按原类型写入。优先读取内核写出的同名文件，缺失时回退为偏好聚合。

墨单三份：`menu_notes.json`、`menu_prices.json`、`menu_progress.json`，均为 JSON 对象，键为图书 id，值约定如下：

- `menu_notes.json`：值为字符串，备注正文单行。
- `menu_prices.json`：值为整数，单位分，负一表示未录入。
- `menu_progress.json`：值为字符串，新格式为 `periodStart:pct:g1` 三段，旧格式为 `periodStart:pct` 两段，两种格式原样保留。

三份数据分别取自同名偏好文件，按 key 全量转出，键序按字典序固定。偏好文件缺失时输出空对象 `{}` 条目，不省略条目；值类型与约定不符时按字符串输出并在导出结果中记录说明，优先保证数据不丢失。

桌面偏好快照：`desk_prefs.json`，JSON 对象，字段约定如下：

- `order`：三分区标识数组，取值 `bill`、`rank`、`fp`，缺失或非法时回退默认顺序 `["bill","rank","fp"]`。
- `onMask`：整数开关掩码，缺失时为 3。
- `updatedAt`：毫秒时间戳，取桌面最近写入时间，从未写入为 0。

该条目取自 cfg 偏好中的 `desk_order`、`desk_on_mask`、`desk_prefs_updated_at`，文件缺失时输出带默认值的对象，不省略条目。

不导出的内容：任何 `.db` 文件、SharedPreferences 的 xml 原始文件、缓存目录与日志。

限制：条目数量上限 4096，单条明文上限 1 MiB，全部条目明文总量上限 16 MiB，超出以错误码 5 拒绝。

## 九、接收端落位与校验建议

接收端按以下顺序处理：校验清单 JSON 与 `encrypted` 取值，校验密钥长度，解密容器，校验魔数与容器版本，校验条目数量与清单一致，逐条复算 `sha256`，最后把条目按原名写入 `filesDir/weread/` 目录。落位前清空该目录下与本包同名的旧文件，目录内其他文件保留。

`manifest_digest` 与 `pack_sha256` 用于传输校验与问题定位，不参与解密。

## 十、安全边界

导出过程只读应用私有数据，不写数据库、不修改偏好、不联网。写入只发生在调用方授予的 `content://` Uri。密钥与令牌只存在于本次调用期间，不落盘、不写日志。导出件包含用户笔记与账单数据，接收端应存放于应用私有目录，不导出到公共存储。

## 十一、写入配置动作

调用方以显式组件 `com.inkread.weekread/.bridge.ExportActivity` 启动，动作 `com.inkread.weekread.action.WRITE_PREFS`，一次性令牌规则与其他动作相同。请求体取自 `write_payload` 字符串，缺失时读取 `target_uri` 指向的 content:// 文本文件，上限 64 KiB。请求体为 JSON 对象，首层键即字段名，形如 `{"desk_on_mask":5,"desk_order":["rank","bill","fp"]}`。

未列出的字段忽略；列出字段的类型与约定不符时整次请求以错误码 4 拒绝；请求体不是对象或没有任何可写字段时以错误码 4 拒绝。

字段分五组，字段名与 cfg 偏好键名一致。

字符串组写入同名偏好，枚举值由 core 的写入方法钳制到默认值：`card_period`（weekly/monthly/book/note）、`menu_title`、`menu_unit`、`menu_note_src`、`menu_excerpt_mode`、`menu_footer`、`menu_footer_note`、`menu_paid_algo`、`menu_period_mode`（week/month/all）。

数组组：`desk_order`，元素限 bill/rank/fp，未知元素丢弃、重复去重、缺项补全为默认顺序，写入 `desk_order`。

整数组：`desk_on_mask`（写入 `desk_on_mask` 并刷新 `desk_prefs_updated_at`）、`menu_top_n`、`menu_min_sec`、`menu_blocks_mask`、`menu_view`、`menu_fs_title`、`menu_fs_body`、`menu_fs_serial`。

`menu_min_sec` 的接收单位与落盘单位不同，调用方按秒填写，微读侧四舍五入换算为分钟后写入 `menu_min_sec`。例如请求值 90 落盘为 2，请求值 60 落盘为 1。其余整数字段按原值写入并由 core 钳制到各自取值范围。

布尔组：`menu_drop_off_shelf`、`menu_show_author`、`menu_show_duration`、`menu_show_progress`、`menu_font_title_serif`、`menu_font_body_mono`。

长整数组：`menu_period_start`，单位秒，小于等于零时清除周期选择。`menu_period_mode` 与 `menu_period_start` 成对处理，只出现其中一项时另一项取当前偏好值，不拒绝请求。

写入统一落于 cfg 偏好文件，不触碰用户数据文件、数据库与 `api_key`。写入完成后返回 `write_count`（实际写入字段数）与 `desk_prefs_updated_at`。写入后的界面刷新依赖原版墨台页与桌面卡片的下一次读取，导出桥不触发主动重算。

## 十二、静默态与还原条件

十雨桌面（`com.shuiyu.octoberrain`）已安装且 versionCode 不低于 21 时进入静默态，执行三项动作：停用 `.a11y.CardA11yService` 并停止蓝牙保活前台服务、清空本应用通知；写入静默标记，该标记只作状态记录，不阻断数据写入，统计数据在静默态继续刷新；写入静默起点与触发版本，并拉起 `:silent` 进程承载内核初始化与本地数据盘点。

桌面图标在共存态保留：`.silent.LauncherAlias` 保持启用。旧版本曾把该别名置为停用，升级覆盖场景下停用状态会留在系统中，因此进入共存态时先以 `COMPONENT_ENABLED_STATE_DEFAULT` 复位该别名，恢复动作同样复位，确保升级后图标可见。

十雨桌面卸载或 versionCode 低于 21 时还原：清除静默标记、复位 `.silent.LauncherAlias`、重新启用卡片无障碍服务、停止 `:silent` 进程。所有动作幂等，重复判定不产生额外写入；恢复以组件实际状态为准，不依赖静默标记，偏好丢失时同样复位。切换由 `BOOT_COMPLETED`、`PACKAGE_ADDED`、`PACKAGE_REPLACED`、`PACKAGE_REMOVED`、`MY_PACKAGE_REPLACED` 触发：卸载走 `PACKAGE_REMOVED`，升级与降级走 `PACKAGE_REPLACED` 重新评估，微读墨记自身更新走 `MY_PACKAGE_REPLACED`。

## 十三、版本记录

V1.3.2-beta（versionCode 48）：首次提供导出桥，清单 schemaVersion 1，容器版本 1，条目含墨单三份、桌面偏好快照与写入配置动作；静默态保留桌面图标，卡片服务停用，写入标记仅作状态记录。

候选包记录：当前候选包为 k3 内核、15:35 由 areal 侧构建的版本，649968 字节，sha256 `9d4fbe655fdf86f71f43f54d55ead4bf6dd00173d6c3891735598776ff142a29`，versionCode 48、versionName 1.3.2-beta，DEX 含 AssetPoster、PricePad、WallpaperRenderer、DeskPage、monthScrollY，签名证书与 1.3.1 一致。`dist/update.json` 保持 1.3.1-beta 稳定版内容（555760 字节、sha256 `11056e744774ba5115d8a34bdc17895ab716fd554bce5daf4a2dddafaf0bf5d9`），候选包未进入更新通道，`dist/.last_version_code` 为 48。此前的 641776 字节、sha256 `63084c009fd7d32a848f9c07f887b002de131f5a87c5406ea3588e0d72f1500b`（k2 内核）已被当前包覆盖；641776 字节、sha256 `3a5144c2ae4c98984810dcdacea6c4e2d094726fd92d413e58c97c65eddc3b72`、633584 字节、sha256 `5a0c0998dde0ec74bdd16e84de14e6a38121b838f1120b21abe138e1a1eac643` 与 637680 字节、sha256 `c43d8141a0206a826c5896248209c289c3b20317e37163dc5a7622d201b35319` 为中间构建，已废弃。
