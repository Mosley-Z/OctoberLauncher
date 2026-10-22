# 第三方 URI 与本地文件同源关联

- 日期/执行者：2026-10-23，Codex URI 子任务；集成、构建和设备由根执行者负责。
- 源码：master / 84a035e 后本轮差异；保留原有 UX、网站及工具链差异。未独立提交、升版或运行 Gradle/ADB。
- 范围：SourceResolver、DescriptorDocumentIdentity、LocalIdentityRules 及其专属 JVM 测试；复用既有 metadata.aliases 和 ReadingRepository.mergeAliases，不改 schema。

## 实现与判断依据

外部 content URI 除既有自有 files_share 映射外，打开一次只读 PFD，`fstat` 确认 regular file；读取 `/proc/self/fd/<fd>` 真实目标，仅接受绝对、非 deleted、无 NUL 路径。规范化后要求文件可读、是 regular file，并且候选路径的 `stat` 与该 PFD 的 device、inode、size 完全相同。成功才将 metadata 主身份设为 `path:<canonical>`、将原 URI 加入 aliases，交由原事务合并较新进度及全部书签。

不按正文摘要、名称或大小单独判断，不扫描存储。副本 inode 不同不会因此合并；移动后的新路径产生新 path 身份，不以 inode 作为数据库主键。未证实的 emulated/runtime 路径映射不猜测。管道、代理/云提供方、已删除目标、不可读路径、stat 不一致或平台检查失败时继续保持 URI 身份，正常来源读取仍决定最终错误提示。

同源识别全部在 SourceResolver 已有 IO 上下文中执行，PFD 在 use 内及时关闭。第三方来源的 stream、seekable 和 archiveFile 仍通过原 URI 与原缓存路径，不因身份映射改为直接读本地文件；persistentAccess 仍取真实 persistedUriPermissions，不把临时授权变成永久授权。自有 files_share 既有行为保留。

## 验证与边界

- 新增 LocalIdentityRulesTest 四例：管道/匿名/相对/deleted/NUL 目标拒绝；相同大小副本与不同设备拒绝；并发尺寸变化拒绝；未知 inode/尺寸拒绝。
- 本子任务未运行 Gradle，测试结果由集成者完整门禁填写；不能把测试源码存在写成测试通过。
- Android `/proc`、regular PFD 与实际 provider 的匹配、URI→文件→重启进度/书签、复制/移动独立及临时授权失效由最终设备验收填写。纯规则测试不替代这些设备项。
- 无新的依赖、Manifest 权限、持久授权写入或用户正文日志。

## 集成点与下一步

根执行者运行 reader 单元测试与静态检查、整包门禁，持有设备者验证真实第三方 regular PFD 和管道回退。若平台暴露运行时路径而规范路径不可读，保留 URI 独立，只有取得该平台对应 dev/ino 证据后才能补映射。

## 根集成者实际补验

实现已进入源码提交 `b4ee37f`，版本1.0.29/49；最终4e98包的check、debug与release构建退出0，reader每变体136项测试零失败（含新增4项规则测试），日志 `_analysis/verify/final-reader/gate-4.log`。151项源码摘要复核零差异。

[P4实机记录](2026-10-23-reader-final-p4.md)明确802b包的第三方regular PFD来源由0%翻至3%并保存书签，随后自有FileProvider本地canonical来源恢复相同3%及书签；同内容副本、新移动路径均0%且独立，移回旧路径恢复3%。本轮也完成真实OS持久授权、撤销与重选闭环。后续215b/4e98只修改生命周期及隐藏HOME冷启动流程，身份实现未变，保留实测APK归属，不冒称整个链在最终包重跑。未知云URI和管道不能证实同源时继续独立，不宣称所有Provider均可识别。
