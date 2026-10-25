# UX145 最近十书、设置首页与应用/文件操作验收

本轮用户四项要求由Codex主代理直接实现、审查和验证，范围见[任务卡](../../task/TASK-UX-145-desktop-settings-file-actions.md)。沿用既有工作树，保留原修改；未升版、提交或推送。没有委托DSH继续修改其他任务。

## 最终行为

| 要求 | 实现与消费者 | 实际证据 |
| --- | --- | --- |
| 最近10本循环 | KernelDataSourceImpl.bookCards经recentCarouselBooks按现有最近阅读时间排序后取前10；少于10用实际数量，不改完整共享书架。WereadBoardWidget普通与沉浸均启用同一循环状态；初始index0最近首本居中，重载保留仍在候选中的选书。 | JVM覆盖15取10、源书架不截断、0/1/2本与十本首尾；两机生产Android探针通过。P4普通本书、S4沉浸均实际首本右滑到末尾、左滑返回首本，封面/标题同步。 |
| 设置主页重排 | 桌面偏好、美化与图标、微读墨记、检查更新，按该顺序组成现有两列圆角卡片；其他7个系统模块以全宽单列行呈现。About的更新分区移除，主页第四项复用Destination.Update。 | 双机debug与最终release布局节点确认前四项顺序、两列边界及系统全宽行；debug实际点击进入更新页。两台设备未配置更新来源，因此未发起网络请求，不冒称验证更新下载/安装。 |
| 卸载可用 | Manifest增加正常请求权限REQUEST_DELETE_PACKAGES，桌面菜单与应用信息共用AppUninstall系统确认调用；不使用静默卸载。 | 两机桌面菜单进入系统确认后取消，测试包仍在；P4从桌面菜单确认实际卸载，S4从应用信息确认实际卸载；PM核对自建包消失，桌面补位。仅卸载本轮自建无权限测试应用。 |
| 文件详情/删除 | 列表与宫格长按共用打开/详情/删除文件；后台读取实时名称、路径、类型、字节数、修改时间及读写属性。普通文件删除前确认，后台删除后调用既有缩略图失效并重载当前目录、夹回页码；失败提示。目录支持详情，本轮不递归删除目录。 | 双机实际详情显示合成文件31字节、完整路径与时间；取消后两份文件逐字节未变。P4宫格、S4列表确认删除后，所选文件不存在、另一文件内容不变且补到第一项。JVM覆盖实时大小变化、缺失文件、只删目标和目录不递归。 |

卸载权限依据：[Android权限参考](https://developer.android.com/reference/android/Manifest.permission#REQUEST_DELETE_PACKAGES)。本轮补齐权限后以设备系统确认及实际包移除作为成功证据，不只凭startActivity返回。

## 工程与设备

证据目录：`_analysis/verify/ux145-20261025/`。工具链按toolchain.path：JDK17、Gradle8.11.1、项目Android SDK。格式通过；完整check与debug/release构建通过（gate.log，退出0）。每变体1000项，0失败、0错误、1项既有跳过。最后只同步两处轮播说明注释，再打包成功（final-package.log），产物摘要与此前相同。

- 版本：1.0.31 / 51，未升版。
- debug：7246719字节，SHA256 `dffca10ac98e1d3b76ce1482628c4966fa2c88c6ff877dea1726af993ac9ea8f`。
- release：2223507字节，SHA256 `0d6f278118fdd0893c8c362bbd2b4ddd90a972de1f532cb94be9389dcb7774f6`。
- P4：P4L202610130006，552×768；S4：SA4B03CN2608081096X，480×800。两机覆盖安装上述release，installed-release.json均摘要匹配。

生产RecentBooksProbe使用合成书架15本，通过最终APK真实recentCarouselBooks函数及普通组件实际carousel实例确认10本、原书架保持15本、默认循环开启与0↔9首尾；两机均exit0。实体UI操作用scripts/android-ui.ps1；自绘面板细粒度GREEDY标注后按标签操作，标准弹窗走layout/click。

原始界面证据为`_ui-out/ux145-*`，包含settings、update、uninstall、file-details、file-delete、book-cycle和release-smoke。功能操作主要在同源码debug中完成，最终release做安装摘要、原桌面与设置入口烟测；不把debug手势结果伪称release逐项触屏。设置版式实测为两台竖屏，本轮未额外完整点验横屏、管理策略限制的应用或外置只读卷。

## 恢复与边界

测试前原APK及数据备份位于仓库外私有临时目录，不在Git或日志保存全量偏好/正文。恢复按实际触及字段合并：全局配置差异数0，原book_pick恢复，文件页last_path/view_grid/sort_by/sort_asc四字段恢复，其他最新数据保留；审计见restoration-audit.json。自建测试包、两份合成文件和唯一测试目录均已清理；未删除用户文件或用户应用。

最终回P4原普通桌面、S4原沉浸桌面。测试前两机均为唤醒状态，本轮没有另设息屏/休眠流程；前轮遗留一次性休眠任务已停用，避免干扰新工作。无活动构建、后台探针或设备操作占用。文件删除为永久删除并明确确认，不含文件夹递归删除、回收站或云端URI管理。
