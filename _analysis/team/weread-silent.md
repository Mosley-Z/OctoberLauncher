# weread-silent 交付报告（微读墨记 V1.3.2-beta · A3/A4/A5）

时间：2026-10-20
任务板：task-3（T3 A3 静默进程 / A4 导出桥 / A5 版本号）
仓库：D:\WorkBuddyStation\WereadMoji\weread-widget（HEAD 5c67da6，tag v1.3.1-beta）

## 一、完成范围

A3 静默进程：新增独立进程 `:silent`，承载内核初始化与本地数据刷新，不加载界面。四项静默动作按交接文档第四节实现：桌面入口别名置 DISABLED、卡片无障碍服务停用、通知清空、定时任务停止，另对数据写入加冻结开关。静默判定阈值为十雨桌面 versionCode ≥ 21，判定入口统一在 `SilentMode.evaluate`，动作幂等。

A4 导出桥：新增 `bridge/` 包，把内核数据导出为十雨桌面可读格式。导出过程只读 filesDir 与 SharedPreferences，不写数据库、不改偏好、不联网，写入只落在调用方授予的 content:// Uri。导出件为「明文清单首行 + 12 字节 IV + AES-256-GCM 密文」，容器明文以 WRMK 魔数开头。

A5 版本号：VERSION_NAME 1.3.2-beta、VERSION_CODE 48（原 47），候选包已产出，未替换正式签名。

## 二、改动文件

版本号
- `version.properties`：VERSION_NAME、VERSION_CODE、NOTES 四条与来由注释块。

清单
- `app/src/main/AndroidManifest.xml`：权限 RECEIVE_BOOT_COMPLETED；queries 增 com.shuiyu.octoberrain；application 的 android:name 指向 .silent.SilentApp；MainActivity 的 MAIN/LAUNCHER 过滤器迁至 activity-alias .silent.LauncherAlias；service .silent.SilentService（process=:silent）；receiver .silent.SilentBootReceiver（BOOT_COMPLETED、PACKAGE_ADDED/REPLACED/REMOVED + data path、MY_PACKAGE_REPLACED）；activity .bridge.ExportActivity。

静默包 `app/src/main/java/com/inkread/weekread/silent/`
- SilentApp、SilentService、SilentBootReceiver、SilentClient、SilentMode、SilentStatus、SilentPrefs、SilentLog、KernelProbe、ProcessIdentity、LocalDataRefresher、WriteFreeze。

导出桥 `app/src/main/java/com/inkread/weekread/bridge/`
- BridgeContract、ExportActivity、ExportPack、ExportFormat、KernelPrefsBuilder、MenuPrefsJson、Json、ExportSelfTest。

自检：ExportSelfTest 为纯 Java 类，不依赖 Android 运行时，覆盖容器编解码往返、AES-256-GCM 往还与错误密钥拒绝、清单单行与字段完整性、文件封装拆分与首行摘要口径、墨单三份 JSON 的键保留与值类型共 35 项检查，桌面 JVM 运行输出 SELFTEST PASS，退出码 0。运行方式见类注释：单独编译 BridgeContract、ExportFormat、Json、MenuPrefsJson、ExportSelfTest 五个文件后执行 main。

## 三、构建与产物

构建命令：`bash tools/build.sh`，环境为仓库自带 JDK17 与 android-sdk（build-tools 33.0.2、platform android-33）。

产物：`dist/weread-stats-1.3.2-beta.apk`，604912 字节，sha256 `64e090a7e1d50d20d7b7a0b2c80da28c001504a46c7757c63824ca3b4595673d`（追加墨单三份条目后的构建，11:36）。`dist/update.json` 由脚本第 7 步同步为 versionCode 48、size 604912、同一 sha256。

badging：versionCode 48、versionName 1.3.2-beta、minSdk 23、targetSdk 30。

签名：apksigner 验证 v1/v2/v3 通过，签名者证书 SHA-256 `3550054cf65ae5d48687a4d883d165dd6222dba3691690604a609b192336f2aa`，与 dist 中 1.3.1-beta 包的签名证书一致，未替换正式签名。

同版本重复构建会被脚本的版本门禁拒绝，重跑用 `SKIP_VERSION_CHECK=1`。`dist/update.json` 与 `dist/.last_version_code` 由脚本第 7 步生成，内容为 vc=48，非人手改动。

## 四、导出桥契约要点

调用：显式组件 `com.inkread.weekread/.bridge.ExportActivity`，startActivityForResult；动作 EXPORT 与 PROBE；调用方包名固定 com.shuiyu.octoberrain（调试例外 com.android.shell）。

参数：target_uri（content:// 且授写权限）、bridge_key（32 字节密钥的 Base64 字符串）、bridge_token（不小于 16 字符，一次性）。

返回：成功回传 manifest_digest、pack_sha256、entry_count、counts_books、counts_notes、counts_bills、source_version_name、source_version_code；失败回传 error_code 与 error_message，错误码 1 身份、2 空间、3 无数据、4 参数、5 读写或加密。

清单 digest 口径：清单文本行 UTF-8 字节的 sha256，不含换行符。

条目：固定名 achievement.json、nt_index.json、nt_batch.json、nt_batch_i.json、nt_batch_p.json、shelf.json、shelf_ids.json、book.json，加 nt_/ni_ 前缀的 json，加 kernel_prefs.json（string/long/int/bool 四组，优先取内核 filesDir/kernel_prefs.json，缺失回退偏好聚合），加墨单三份 menu_notes.json、menu_prices.json、menu_progress.json。墨单三份取自同名偏好文件并按 key 全量转出，键为图书 id：备注值为字符串，标价值为整数分（负一表示未录入），进度值为字符串（三段或旧两段格式原样保留）；偏好文件缺失时输出空对象条目，不省略。全部条目计入 items 数与 16 MiB 总量校验。不含 .db 与偏好 xml。

## 五、遗留项

1. 真机验证未执行：静默态四项动作、解除静默后的恢复、导出桥跨应用调用与解密，需在装有十雨桌面的设备上验证。编码层已由 ExportSelfTest 覆盖。
2. 令牌一次性只在进程内记忆，进程重启后同一令牌可再用，跨应用重放风险由十雨侧一次性使用策略承担。
3. 静默态停用 a11y.CardA11yService 后，桌面卡片在十雨共存的设备上不再刷新，属预期行为；删除十雨桌面后需重启微读恢复。
4. 正式签名替换、发布渠道与打 tag 时点由用户决定；本轮只产候选包，未 git add、未提交、未打 tag。
5. `dist/weread-stats-1.3.1-beta.apk` 在 11:13 被一次外部构建改写，非本成员操作，未回滚，交 lead 统一核对 diff。

## 六、P4a 配置写入与桌面偏好导出（V1.3.2-beta / vc48）

动作：`com.inkread.weekread.action.WRITE_PREFS`，与 EXPORT、PROBE 同一组件。请求体取 `write_payload` 字符串，缺失时读 `target_uri` 的 content:// 文本，上限 64 KiB。写入动作不要求 `bridge_key`，`bridge_token` 一次性规则不变。

字段白名单二十五项，按五组写入，字段名与 cfg 偏好键名一致：字符串组 card_period（weekly/monthly/book/note）、menu_title、menu_unit、menu_note_src、menu_excerpt_mode、menu_footer、menu_footer_note、menu_paid_algo、menu_period_mode（week/month/all）；数组组 desk_order（元素限 bill/rank/fp，去重补全）；整数组 desk_on_mask（刷新 desk_prefs_updated_at）、menu_top_n、menu_min_sec、menu_blocks_mask、menu_view、menu_fs_title、menu_fs_body、menu_fs_serial；布尔组 menu_drop_off_shelf、menu_show_author、menu_show_duration、menu_show_progress、menu_font_title_serif、menu_font_body_mono；长整数组 menu_period_start。请求体首层即字段名，无 fields 包装；未列出字段忽略，类型不符以错误码 4 拒绝；menu_min_sec 按秒接收、四舍五入换算分钟后落盘；menu_period_mode 与 menu_period_start 只出现一项时另一项取现值，不拒绝。字符串枚举按 core 既有 setter 钳制。写入统一落于 cfg 偏好，不触碰用户数据、数据库与 api_key。

返回：write_count、desk_prefs_updated_at；失败仍为 error_code 与 error_message。

导出条目新增 `desk_prefs.json`：JSON 对象，order（数组，默认 ["bill","rank","fp"]）、onMask（整数，默认 3）、updatedAt（毫秒，从未写入为 0），取自 cfg 的 desk_order、desk_on_mask、desk_prefs_updated_at。

实现位置：`bridge/BridgeContract.java`（动作与返回键常量）、`bridge/WritePrefsHandler.java`（纯 JVM 解析校验，写入经 core 公开 setter）、`bridge/DeskPrefsJson.java`（快照序列化）、`bridge/ExportPack.java`（采集与上限校验）、`bridge/ExportActivity.java`（动作分派）、`bridge/ExportSelfTest.java`（自检扩至 55 项，含写入字段校验 20 项与 desk_prefs 6 项）。

构建（13:39，最终）：dist/weread-stats-1.3.2-beta.apk，633584 字节，sha256 5a0c0998dde0ec74bdd16e84de14e6a38121b838f1120b21abe138e1a1eac643，versionCode 48、versionName 1.3.2-beta；签名证书 SHA-256 3550054cf65ae5d48687a4d883d165dd6222dba3691690604a609b192336f2aa 与 1.3.1 一致。build.sh 第 7 步改写 dist/update.json（vc48、size 633584、同 sha256）与 dist/.last_version_code。APK 内可检索到 desk_prefs.json、WRITE_PREFS、write_payload、write_count、desk_prefs_updated_at、desk_order、menu_period_start。自检以 `java -classpath build/classes;android.jar com.inkread.weekread.bridge.ExportSelfTest` 运行，输出 SELFTEST PASS，共 55 项。

## 七、P4a 补齐与静默策略调整

字段扩展：WritePrefsHandler 白名单从十项扩到二十五项，desk_order 由字符串改为 JSON 数组（requireOrder 校验元素类型），menu_min_sec 改为经 MenuPrefs.setMinMinutes 写分钟，删除 splitCsv 与 KEY_MENU_MIN_SEC 私有键写入。ExportSelfTest 追加数组解析、首项、元素类型拒绝、字符串形态拒绝、全字段计数五项用例。

静默策略（按 lead m00996 确认）：SilentMode.enterSilent 不再对 .silent.LauncherAlias 写 DISABLED，桌面图标在静默态保留；restore 改用新增的 resetComponentDefault，以 COMPONENT_ENABLED_STATE_DEFAULT 清理旧版本遗留的停用状态。WriteFreeze 改为静默标记，只记录状态、不阻断写入，全仓检索确认除 SilentMode 与状态上报外无写入路径查询该标记。.a11y.CardA11yService 停用逻辑保留。

版本说明：version.properties 的 NOTES 第 1 条原写「桌面图标隐藏、定时任务停止、本应用数据冻结」，改为「卡片服务停用、通知清空、静默标记记录，桌面图标保留」，dist/update.json 随之刷新。

文档：docs/19 新增第十二节「静默态与还原条件」，第十一节字段表按实现重写，menu_min_sec 的接收单位（秒）与落盘单位（分钟）分列说明。

## 八、待决与遗留

1. task-14 描述与 lead m00996 第三条的表述差异已由 lead 澄清（m01013）：以消息为准，task-14 描述已同步修改；共存态只停用 `.a11y.CardA11yService`，桌面图标保留、数据刷新按原周期运行、通知清空照旧。
2. 策略判定自检已实现为纯 Java 类 `silent/SilentPolicySelfTest`，覆盖未安装、低于阈值、等于阈值、高于阈值四组输入，输出 SELFTEST PASS。
3. menu_bill_mode 与 menu_pin_mode 经核对在源码中不存在，lead 已确认误记并撤回，未新增偏好键。
4. 真机未验证：共存模式下图标与悬浮窗行为、WRITE_PREFS 跨应用调用、静默切换后的组件恢复。
5. 正式签名替换、打 tag 与发布时点由用户决定；本轮未 git add、未提交、未打 tag。

## 九、P4c 收尾

判定逻辑抽到 `silent/CoexistPolicy.java`：`SHIYU_MIN_VERSION_CODE = 21` 为唯一来源，提供 isCoexist、isOriginal、describe，不依赖 Android 运行时。SilentMode 的阈值常量改为引用该值，shouldBeSilent 委托 isCoexist，行为保持不变。

restore 由「静默偏好为真才调用」改为 evaluate 非共存分支无条件调用。组件复位本身幂等，偏好丢失或旧版本遗留的停用状态同样被复位；markRestored 仅在本次确实处于共存态时写入，避免反复刷新恢复时间。卸载走 PACKAGE_REMOVED，降级走 PACKAGE_REPLACED 重新评估，两条路径都到达 evaluate，恢复分支均被执行。

新增 `silent/SilentPolicySelfTest.java`：未安装、低于阈值、等于阈值、高于阈值四组输入加版本号 0 与阈值常量一致性，6 项 OK，输出 SELFTEST PASS。

最终构建（13:39）：633584 字节，sha256 5a0c0998dde0ec74bdd16e84de14e6a38121b838f1120b21abe138e1a1eac643，versionCode 48、versionName 1.3.2-beta；dist/update.json 与 dist/.last_version_code 已由 build.sh 同步。ExportSelfTest 55 项 PASS，SilentPolicySelfTest 6 项 PASS。

docs/19 第十二节补充切换触发来源，写明卸载与降级各自经过的广播路径。

## 十、真机验证

设备 SA4B03CN2608081096X，Android 版本与十雨 1.0.1（versionCode 21）同装。十雨为 release 包，`run-as com.shuiyu.octoberrain` 返回 package not debuggable，配置备份按预定方案跳过，失败输出记录在 `_analysis/verify/20261020-134103/cfg-backup.err`。

验证对象经历一次变化：13:41 安装 637680 字节、sha256 c43d8141 的包；核对发现 `disabledComponents` 同时包含 `LauncherAlias` 与 `CardA11yService`，`cmd package resolve-activity` 返回 No activity found，桌面图标不可见。原因是旧版本在设备上留下的别名停用状态在升级覆盖后仍然保留，而当时的 enterSilent 只移除了写入 DISABLED 的分支、没有复位遗留状态。修复为 enterSilent 先以 COMPONENT_ENABLED_STATE_DEFAULT 复位别名，重新构建得到 641776 字节、sha256 3a5144c2 的包并重装复验。

安装与版本：`adb install -r` 两次均返回 Success，`dumpsys package com.inkread.weekread` 显示 versionCode 48、versionName 1.3.2-beta。

三项状态核对（修复包，13:44 至 13:45）：

1. 图标：`cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.inkread.weekread` 返回 `com.inkread.weekread/.silent.LauncherAlias`；`disabledComponents` 仅剩 `com.inkread.weekread.a11y.CardA11yService`。
2. 卡片服务：`settings get secure enabled_accessibility_services` 返回 null，服务处于停用。
3. 打开应用：统计页正常显示，本周 19 分钟、阅读 1/7 天、刷新按钮与打开墨台入口在位，未见崩溃。日志中 SilentBootReceiver 收到 PACKAGE_REPLACED 后执行评估，`Mode: silent=true shiyuInstalled=true shiyuVc=21 frozen=false kernel=com.inkread.weekread.kernel.CardKernel nt=219 ni=56 bills=19`，判定与内核探测符合预期。

悬浮卡片：按 HOME 回到十雨桌面后，十雨自身渲染的微读数据卡片可见（本周、19 分钟、打开桌面），原版悬浮卡片未出现，符合卡片服务停用后的预期。该条按指示只记录观察结果。

截图与文件路径（目录 `D:\DSHStation\OctoberLauncher\_analysis\verify\20261020-134103\`）：`screen-1-app.png` 为 637680 包启动过程截图，画面为桌面；`screen-2-app.png` 为修复包统计页；`screen-3-home.png` 为修复包回到十雨桌面的画面；`screen-4-drawer.png` 为一次上滑尝试，画面仍为桌面，未取得应用抽屉视图；`screen-5-home.png` 为 13:57 设备释放后的桌面；`screen-6-page2.png` 为第二页图标；`screen-7-page3.png` 为第三页，含「微读墨记」图标；`screen-8-click.png` 为点击图标后进入的书架页；`screen-9-home-after-click.png` 为返回桌面后的画面，十雨卡片更新于 13:59。

补测（13:57 设备释放后）：左右滑动切到第三页，`screen-7-page3.png` 显示「文件」与「微读墨记」图标，图标可见性取得直接截图证据；执行 `input tap 240 168` 后前台进入微读书架页（`screen-8-click.png`），入口可用；按 HOME 返回桌面（`screen-9-home-after-click.png`），十雨卡片更新于 13:59，原版悬浮卡片未出现。抽屉截图项由此完成。

## 十一、内核 k2 复核（14:11）

areal 通知内核 k2 定稿后重新构建，`bash tools/build.sh` 返回 exit=0，产物 641776 字节、sha256 `63084c009fd7d32a848f9c07f887b002de131f5a87c5406ea3588e0d72f1500b`，versionCode 48、versionName 1.3.2-beta；DEX 内可检索到 DeskPage、KernelDataLoader、KernelDataSource、monthScrollY 四个新增符号，`dist/update.json` 与 `dist/.last_version_code` 同步。ExportSelfTest 55 项、SilentPolicySelfTest 6 项均输出 SELFTEST PASS。

真机安装该包后复验：`disabledComponents` 仍仅含 `CardA11yService`，`resolve-activity` 返回 `com.inkread.weekread/.silent.LauncherAlias`，日志 `Mode: silent=true shiyuInstalled=true shiyuVc=22`，十雨已升至 versionCode 22，仍高于阈值 21。`settings get secure enabled_accessibility_services` 返回服务名而组件处于停用，`dumpsys accessibility` 的 Bound services 为空，说明系统保留了旧的启用记录，组件级停用阻止绑定，原版悬浮卡片未运行。

日志同时记录十雨对导出桥的一次调用：14:11:39 打开 `.bridge.ExportActivity`，14:11:40 报 `Permission Denial: opening provider androidx.core.content.FileProvider ... not exported from UID 10125`，导出以错误码 1 失败。说明十雨调用端把 target_uri 指向未导出的 FileProvider，需要十雨侧为微读授予读取权限或改用可共享落点。

内核侧归档：`dist/kernel/weread-kernel-1.3.2-k2.jar`，163494 字节，sha256 `a9855a4fb493a1b3c86f3c941aa46cacc7715557a5267b8b2155cf3a3b260241`，57 class，`Kernel-Api-Version: 2`；探针三图与脚本归档于 `docs/snapshots/kernel-1.3.2-k2/`，内容总高 1104，分区起点 0/699/914，供 P5 门禁比对。`kernel/` 与 `kernel/desk/` 本轮不再改动。

当前候选包状态（15:39 核对）：`dist/weread-stats-1.3.2-beta.apk` 已被内核侧 15:35 构建覆盖为 649968 字节、sha256 `9d4fbe655fdf86f71f43f54d55ead4bf6dd00173d6c3891735598776ff142a29`，DEX 含 AssetPoster、PricePad、WallpaperRenderer 等 k3 内核符号，签名证书仍为 1.3.1 正式证书。`dist/update.json` 保持 1.3.1-beta 稳定版内容，`dist/.last_version_code` 为 48，候选包未进入更新通道。真机当前安装的仍是 14:11 的 k2 包。

包内组件核对（`aapt2 dump xmltree`）：`RECEIVE_BOOT_COMPLETED` 权限、`queries` 内 `com.shuiyu.octoberrain`、`application android:name=".silent.SilentApp"`、`activity-alias .silent.LauncherAlias`、`service .silent.SilentService` 且 `android:process=":silent"`、`receiver .silent.SilentBootReceiver`、`activity .bridge.ExportActivity` 均在位。自检 ExportSelfTest 55 项、SilentPolicySelfTest 6 项均 PASS，对应源码在 15:26 至 15:34 的内核改动中未变更。

仍待补：WRITE_PREFS 跨应用联调待十雨调用端修正导出落点后进行；真机未覆盖十雨卸载或降级后的组件恢复、`MY_PACKAGE_REPLACED` 路径的独立观察。
