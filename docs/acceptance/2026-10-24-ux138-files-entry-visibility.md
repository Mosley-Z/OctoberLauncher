# 2026-10-24 UX138 文件入口恢复与显隐

实现、最终统一工程门禁及P4同包显隐/APK/ZIP限定业务验证已交付，样本清理与Pictures偏好恢复已确认。关联[UX138](../../task/TASK-UX-138-files-entry-visibility.md)、[UX137](../../task/TASK-UX-137-apk-open-choice.md)及[文件模块职责](../design/16-files-module.md)；新的业务证据归当前包，不改写UX137历史未测结论。

- 集成者：Sol root；保留已有工作区修改。712份源与资源，相对UX137新增FilesEntryMigration及其测试，修改Models、ConfigMigration、DesktopPrefsScreen，共5文件。
- 最终APK `1.0.31 / 51`，2210547字节，SHA-256 `d8b8dab4067812adce46d34cc0198ae7223edf36cd61b2597f01e5c38f29164f`；配置结构版本16，不代表APK升版。未提交、推送或发布。

## 行为与工程证据

新装默认显示文件；配置≤15向16迁移仅一次移除`hiddenSpecials`的`special:files`，其他入口、安装应用与布局保持。v16以后重新隐藏的选择保留。既有布局设置→管理隐藏应用顶部只增加“内置入口/文件”，开显示、关隐藏，父级数量包含文件；安装应用仍独立管理，开关语义统一。

统一`check :app:assembleDebug :app:assembleRelease`成功，耗时1分21秒；1566任务中372执行、1194 up-to-date。Debug、release各948例、0失败、0错误、1跳过；同例不相加为1896个独立用例，部分输出复用有效缓存。证据位于`_analysis/verify/ux138/`，门禁见gate.log（本地证据：`_analysis/verify/ux138/gate.log`）。工程结果不替代实际显隐、系统安装或阅读器行为验证。

## P4当前包实际结果

P4覆盖安装后已观察文件默认恢复，加载后原资源管理器、NOTE等原首页内容仍在；原资源管理器未被文件特殊入口替换。既有隐藏页“内置入口/文件”初始为开，安装应用开关也为开显示，见`_ui-out/ux138-p4-visibility-on/layout.json`。

以下证据均来自当前`d8b8…`包，原生页面及自绘观察按项目工具执行；路径前缀均为`_ui-out/ux138-p4-`。

| 用例 | 实际结果与证据后缀 |
| --- | --- |
| 用户重新隐藏保留 | 关闭文件开关后force-stop/重启，仍为关：`visibility-off-after-cold/layout.json`；证明v16后不被迁移反复恢复 |
| 恢复显示及桌面 | 开关重新开：`restored-on/layout.json`；冷启后`final-home`图保持原NOTE、壁纸及首页应用（含系统资源管理器），`page2`图可见独立内置文件 |
| 内置文件实际入口 | 成功打开原Pictures列表，系统资源管理器与内置文件身份独立 |
| APK选择及取消 | `apk-choice/layout.json`显示安装/查看结构；`apk-cancel`图回原列表，取消无动作 |
| APK查看结构 | `apk-structure`实际显示META-INF、assets、com、kotlin、org、res及AndroidManifest等APK条目 |
| ZIP默认查看 | `zip`图直接显示structure.txt，无APK选择框 |
| JAR分类 | `jar/layout.json`显示未知类型，不默认当普通归档 |
| 系统安装交接 | `installer/layout.json`实际进入系统“十雨桌面”更新提示，点击取消；没有真正安装测试APK，也未修改未知来源授权 |

本轮自建`/sdcard/Download/00-UX138-Probe`三个文件已逐一精确删除，再移除该空目录；复查返回`No such file`，未扩大删除范围。`_ui-out/ux138-p4-restored-pictures/screen-annotated.png`确认原Pictures路径与列表模式恢复。最终`_ui-out/ux138-p4-restored-home/screen-annotated.png`停桌面第二页，恢复显示的文件入口可见；桌面记住当前页，不能称已回原首页。原首页NOTE、应用及壁纸保持由`final-home`图另证。

应用进程10756存活；已安装base.apk摘要现场核对仍为本记录`d8b8…`最终包。无遗留测试样本，清理及恢复不再待补。S4离线，未安装本包，不给S4通过结论。

当前结果是有限业务矩阵，不以一次默认恢复推定新装或所有旧配置组合均已设备覆盖；AAB/未知bin等其余分类边界仍按工程测试，不由JAR一例外推全部格式。系统安装只验证交接与取消，不包含真正安装、授权变更或发布。UX137历史包的未测结论保持，新业务通过只归当前UX138包。
