# 2026-10-24 UX137 APK打开选择

本轮APK打开选择及常见归档分类边界已完成，最终统一工程门禁通过，P4最终包安装并核对摘要。内置文件入口不可达原因已只读确认，业务设备分支均未测试。关联[UX137](../../task/TASK-UX-137-apk-open-choice.md)、[文件模块职责](../design/16-files-module.md)。

- 集成者：Sol root；保留既有工作区修改，共享Reader契约、Manifest、依赖及版本保持，Reader内部分类和对应probe测试纳入本轮修改。未提交、推送或发布。
- 最终710份源与资源；相对UX136新增2份、删除0份、修改5份，共7文件，见source-delta（本地证据：`_analysis/verify/ux137/source-delta.json`）。
- 最终release `1.0.31 / 51`，2210311字节，SHA-256 `ffd320a3132c49e7ab4fb33aa0e9ecf2584241202bff4d06677d5ecfba6f79a8`，见APK摘要（本地证据：`_analysis/verify/ux137/apk-manifest.json`）。此前`a440…`仅中间包，不计最终业务设备验证。

## 已交付行为

文件页任意目录的APK以文件后缀或APK MIME识别，在图片和普通阅读分流之前提供“安装/查看结构”选择。取消不执行动作；安装经既有FileProvider和ACTION_VIEW进入系统安装界面，不自动确认安装，不改未知来源授权。查看结构交原阅读器同一ZIP引擎。

Reader普通ZIP兜底收紧为常见压缩后缀或ZIP MIME；JAR、AAB、未知bin不再仅凭PK头默认归档，EPUB/Office内部专用标记仍优先。分类及既有probe集成测试同步调整，不新增RAR等解码引擎。

## 工程证据

最终统一`check :app:assembleDebug :app:assembleRelease`成功，耗时38秒；1566任务中42执行、1524 up-to-date。Debug、release各946例，0失败、0错误、1跳过；同例不重复计为1892个独立用例，部分输出使用有效缓存。中间包的40秒/945例不作最终证据。

证据：gate.log（本地证据：`_analysis/verify/ux137/gate.log`）、test-summary.json（本地证据：`_analysis/verify/ux137/test-summary.json`）。工程测试不替代实际系统安装界面、权限状态或阅读器打开设备验证。

## 设备与未覆盖

P4最终包安装成功且摘要匹配，见设备包核对（本地证据：`_analysis/verify/ux137/device-apk-summary.json`）。旧`_ui-out/ux137-p4-installed/screen-annotated.png`属于中间阶段，不作为最终包业务通过。最终包未执行APK选择、取消、系统安装或结构查看，安装成功本身不代表这些分支已测。

主代理已核对最终恢复图`_ui-out/ux137-p4-final-restored.png`：原NOTE、2×4、横屏90°及壁纸保持，进程10085存活，无偏好改变或遗留设备占用。文档检查211份、40项映射、27张卡，2项历史WARN、0错误；CRLF兼容差异检查通过。源码最终摘要一致性待集成者单独核准，不由上述设备恢复结论推定。

用户询问P4资源管理器是否占用入口后，主代理只读确认其应用信息为`com.android.rk`系统应用，证据`_ui-out/ux137-p4-resource-info/layout.json`。内置`special:files`在`DEFAULT_HIDDEN_SPECIALS`中，HEAD原始版本已包含；桌面依`hiddenSpecials`过滤内置入口，已安装`app:包名`与其独立，不按同名或图标替换。现有隐藏应用管理只处理`hiddenPackages`，缺少内置入口恢复口。该调查解释入口不可达，不是资源管理器占用，也不表示本轮新增了隐藏行为；本轮未恢复入口、修改配置或新增导航。

S4当前离线，未安装本包。未向设备上传样本、未改配置、未实际安装测试APK。APK选择、取消、系统安装界面、查看结构及ZIP原分支均为实机未测；不能以P4安装成功或JVM测试推定通过。后续若用户恢复入口，仅按本包真实操作补充证据，不重写当前未测为历史通过。

实现及最终统一工程验证已交付，P4最终包已装；内置入口恢复不属于本轮已实施改动。业务实机矩阵仍未覆盖，不声称全部场景验收或发布已经完成。
