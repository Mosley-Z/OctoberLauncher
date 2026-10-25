# 2026-10-24 UX137 APK分流与P4入口调查 Sol交接

- 用户目标与最新范围：APK先选择安装/查看结构，常见压缩格式默认查看；随后要求先确认P4文件入口是否被资源管理器占用。只读入口调查已完成，不擅自加恢复开关。
- 任务卡与状态：TASK-UX-137-apk-open-choice.md，READY_FOR_REVIEW；docs/acceptance/2026-10-24-ux137-apk-open-choice.md为限定证据。
- 基线与修改：共享master/f025b18及原差异；相对UX136新增ApkOpenActions/FileItemApkTest，改FileItem/BrowseScreen及reader分类和两份回归，共7文件；710源，source-delta见_analysis/verify/ux137。
- 已完成：文件页APK优先双选项，取消不动作，系统安装器走FileProvider只读授权；结构沿旧reader，普通ZIP兜底按常见压缩身份，不仅PK头；专用EPUB/Office保持，无新RAR引擎。最终check/debug/release38秒，各946例0失败0错误1跳过。
- 最终包：1.0.31/51，2210311字节，SHA ffd320a3132c49e7ab4fb33aa0e9ecf2584241202bff4d06677d5ecfba6f79a8；P4覆盖安装摘要相同，S4离线未安装（仍上轮9b4）。中间a440不再作为最终包。
- 未完成与解除条件：APK弹窗/取消/系统安装/查看结构/ZIP实机均未测，等待本项目文件入口可达；不能把P4安装成功或JVM当业务设备PASS。本轮未向设备传测试样本或实际安装测试APK、未改未知来源授权。
- 入口调查：P4“资源管理器”应用信息com.android.rk、系统应用，_ui-out/ux137-p4-resource-info/layout.json；旧默认hiddenSpecials含special:files（HEAD原有），DesktopEntries按特殊键显隐，安装应用和图标键独立，无覆盖占用。现有隐藏管理只管hiddenPackages，无法恢复内置入口，属于旧策略未同步的入口缺口。
- 接口与消费者：共用Reader契约/Manifest/依赖未改；APK仅文件入口提前选择，DocumentKinds保留显式APK结构能力，其它普通ZIP要求常见后缀/MIME；真实解码能力不因扩展名白名单变广。
- 恢复与资源：无构建/设备占用。P4回原桌面NOTE/2×4/90°/壁纸，进程存活，未改偏好；_ui-out/ux137-p4-final-restored.png。S4离线，先重新核实再操作。
- 下一步：按用户决定恢复入口的范围推进；不要为了凑测试新增导航或全量导出/修改偏好。入口可达后用既有最终包补限定实机检查，不重复无关门禁。
- 可复用经验：要求用户进入页面前先核查入口可达性；按源码默认策略与实机应用身份解释缺入口，不凭相似图标认作同一应用。来源本轮用户追问与只读调查。
- 提交/推送/发布：均无，未改版本。
