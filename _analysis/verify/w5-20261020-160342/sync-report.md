# W5 同步验收记录（2026-10-20 16:30–16:47）

## 一、设备与版本

| 项 | 值 |
| --- | --- |
| 设备 | `SA4B03CN2608081096X`，480×800，密度 219 |
| 包名 | `com.shuiyu.octoberrain` |
| 版本 | versionCode 23，versionName 1.0.3（含 k4 内核） |
| 触发方式 | 配置 `integrations.wereadSource` 置为 `synced`，重启应用后由 `syncWereadIfNeeded` 触发 |
| 目录约定 | 同步写 `files/weread`，导入数据留 `files/weread-import`，备份留 `files/weread-backup` |

## 二、操作顺序

1. 记录前置状态：`files/weread` 286 个文件、`files/weread-backup` 284 个。
2. `files/weread` 改名为 `files/weread-import`，保留导入数据。
3. 备份配置到 `cfg-before-sync2.xml`，改写 `wereadSource` 为 `synced`（`cfg-sync.xml`），`run-as` 覆盖 `shared_prefs/octoberrain_config.xml`。
4. `am force-stop` 后重启 `MainActivity`，同步在后台执行。
5. 轮询到 `files/weread` 出现文件，再取文件与截图。
6. 首轮在 APK 覆盖安装过程中启动，旧进程写出一版错误键名；重装完成后清空目录重跑，得到有效结果（`r3-*` 文件）。

## 三、最终取证结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 必需文件出现 | 通过：28 个文件，含 `shelf.json`、`shelf_ids.json`、`book.json`、`kernel_prefs.json`、`bills.json`、`assets.json`、`stats.json`、`nt_index.json` 与 21 个 `nt_<bookId>.json` | `r3-*` |
| `kernel_prefs` 周期键 | 通过：string 组 8 键，含 `d_weekly:1792339200`、`d_monthly:1790784000`、`stats_annual_2026`、`overall`；long 组 4 键，含 `t_weekly:`、`t_monthly:`、`stats_annual_t_2026`、`stats_overall_t` | `r3-kernel-prefs.json` |
| 账单写入 | 本期周账正确（1 条、`totalSec` 1169）；月账正确（10 条、80106）；多出 `bill_weekly_1767196800`（9 条、1665649），为年度周期被归一成周档 | `r3-bills.json` |
| 负一屏渲染 | 墨单正常（2026-W43、1 本、0h19m、合计 0.3 小时）；阅读资产空态；阅读指纹待导入 | `r3-neg-bill.png`、`r3-neg-asset.png` |
| 导入数据未删除 | 通过：`files/weread-import` 286 个文件（220 个 `nt_`），与 `files/weread-backup` 284 个文件比对为超集，无缺失 | `import-files.txt`、`backup-files.txt` |

## 四、三项数值（有效轮次）

| 文件 | 字节数 | 条目 | 备注 |
| --- | --- | --- | --- |
| `kernel_prefs.json` | 109449 | string 8 键、long 4 键 | 键名见上表 |
| `bills.json` | 3552 | 3 个键 | 周账 1 条/1169 秒、月账 10 条/80106 秒、年度渗入 9 条/1665649 秒 |
| `assets.json` | 139980 | `{"books":[…]}` 1177 条 | `sec`、`pct`、`fin` 全为 0；书架响应 `readingTime` 与 `progress` 全为 0，`finished` 113 本 |

## 五、遗留缺陷与修法

一、年度周期渗入账单。`WereadSyncService.writeBills:146` 仅排除 `overall`，`WereadStore.writeBill:197` 把非 `monthly` 一律归一为 `weekly`，年度响应写成 `bill_weekly_<年初>`。修法：`writeBills` 同时排除 `annually`。

二、资产时长与进度全零。网关书架响应不提供逐书时长与进度，1177 本 `readingTime`、`progress` 均为 0；统计 `readLongest` 的 `overall` 与 `stats_annual_2026` 各有 10 本带 `readTime`。修法：`shelfAssets` 先按 `bookId` 用 `stats readLongest` 合并 `readingSeconds`，再用书架 `finished/finishTime` 补 `fin`，`pct` 暂按 0 或由笔记索引进度补。

## 六、当前设备状态

- 配置保持 `synced`，`files/weread` 为同步结果（28 个文件），`files/weread-import` 保留导入数据（286 个文件），`files/weread-backup` 为更早备份（284 个文件）。
- 未执行任何删除操作；`rm -rf files/weread` 只作用于同步输出目录。

## 七、脚本与辅助文件

`sync-verify.ps1` 封装同一流程：记录前置状态、备份配置、改名保留导入数据、改写来源、重启、等待同步、校验文件与键、截取负一屏、还原配置。`check_round2b.py`、`check_round3.py` 统计键名与资产字段分布。
