# 2026-10-24 UX135 横屏应用位置与时间分行

用户确认：2026-10-24本轮明确反馈“已确认，非常好”。该人类反馈确认本轮交付，不计作新增自动设备测试，也不补写既有未覆盖场景为通过。

**当前实现、统一工程门禁及P4限定验收已交付，冷启恢复通过；新包实际展开右界仍未设备覆盖。** 用户确认旧包已经展开后认为范围过大，明确要求普通横屏应用再左移15物理px、累计30px，组件大小及位置不变，展开右界随首页应用区收回；更新时间按小时两位、横置冒号、分钟两位分行，“更新于”仍竖排。

- 关联：[UX135](../../task/TASK-UX-135-landscape-spacing-time.md)、[横屏本记设计](../design/39-landscape-note-expansion-proposal.md)。
- 集成者：Sol root；保留现有工作区差异。704份源码与资源见清单（本地证据：`_analysis/verify/ux135/final-source-manifest.json`）。
- Release `1.0.31 / 51`，2208815字节，SHA-256 `a851ee149ab07e38c5820470e1806f5e92eb996f38c8edb3316075f3bb18fe55`，已装P4；见APK清单（本地证据：`_analysis/verify/ux135/apk-manifest.json`）。
- 旧包实际展开图`_ui-out/ux134-expanded-user-raw.png`仅为用户反馈的现场依据，不算新包通过。
- 未升版、提交、推送或发布；旧验收记录保持原包归属。

| 已集成项 | 范围与预期 |
| --- | --- |
| 普通横屏应用位置 | 再左移15物理px，累计左移30px；绘制和触点共同使用对应位置。组件几何计算未改，大小和位置保持 |
| 本记展开右界 | 跟随首页应用区右界收回，不再使用整页最大右界；保留对两列应用的纸面覆盖与独立正文留白 |
| 更新时间分行 | “更新于”仍逐字竖排；小时HH同一行，冒号横置独立一行，分钟mm同一行；只改时间分行辅助与绘制，标题原路径保留 |

## 当前工程与设备证据

最终统一门禁`check :app:assembleDebug :app:assembleRelease`成功，耗时43秒；1566任务中103执行、1463 up-to-date。Debug、release各938例、0失败、0错误、1跳过，同一用例不重复相加，部分输出使用有效缓存。证据：gate.log（本地证据：`_analysis/verify/ux135/gate.log`）、unit-summary.json（本地证据：`_analysis/verify/ux135/unit-summary.json`）。

position-pixels.json（本地证据：`_analysis/verify/ux135/position-pixels.json`）显示7个应用图标相对上一包最佳偏移均为-15物理px，匹配90.433–100%；正文、按钮、分隔线抽样在原坐标100%一致。该结果支持追加位移及抽样组件位置保持，不等于全部触点已逐一验证。新包`_ui-out/ux135-after-raw.png`实际显示小时两位、单独冒号及分钟两位分行；冒号经90度旋转实现横置，源时间逻辑未改。

首页右界的getter与工程测试已按首页应用区right计算，但新包未再次实际展开，不能写右界设备PASS。P4正式报告（本地证据：`_analysis/verify/ux135/p4-result.md`）确认force-stop重启后首帧加载中不计通过；最终`_ui-out/ux135-final-cold-raw.png`已加载，原NOTE长文、普通横屏90°、2×4及原壁纸保持，应用新位置、按钮及时间分行保留。进程存活、crash buffer本应用Process计数0，见process-check（本地证据：`_analysis/verify/ux135/process-check.json`），不外推全部路径无崩溃。

未编辑正文、导入fixture或修改字号、容量、方向、壁纸；安装及冷启按临时层设计关闭展开，最终停原本记收起态，P4已释放，无后台自动操作，S4未操作。旧APK存于OS私有临时目录，不等于用户私有数据备份；未换debug或清数据。新包展开覆盖、滚动、收起、Back及270°、竖屏、沉浸未本轮重验；三个组件区域抽样100%一致不扩大为所有主题/尺寸的完整量测。

最终一致性（本地证据：`_analysis/verify/ux135/final-consistency.json`）复核704份源/资源、本批5项差异、摘要0差异；文档检查（本地证据：`_analysis/verify/ux135/documents.log`）为205份文档、40迁移、27张RD/FIX、2项历史WARN、0ERROR，不声称该检查包含全部UX卡。

用户旧包手点成功说明此前实际展开已发生，但不能替代本轮收回右界后的设备验证。若需用户协助当前入口，只记录真实协助与后续画面，不用历史截图补新包结论。未实测矩阵和冷启范围须明确保留。
