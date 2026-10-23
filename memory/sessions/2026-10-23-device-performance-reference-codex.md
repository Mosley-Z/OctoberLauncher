# 双机硬件与性能参考整理交接

- 日期/负责人：2026-10-23，Codex根；用户要求重试推送并整理设备硬件及优化经验。
- 工作范围：新38参考文档、PROJECT/设计索引/33手册入口、稳定记忆和STATUS本轮段；不修改业务、版本、签名或旧测试结论。
- 基线：master/e198d2e；.gitignore、设计索引、STATUS、看板、gradle.properties及其他UX/网站/Wrapper等已有差异保留。共享索引/STATUS只精确暂存本轮差异。
- 推送：本轮首次git push仍443超时，用户随后要求自行执行，已提供cd仓库后git push origin master。整理期间本地origin/master到e198d2e；本轮新增文档提交后需用户再推送，不继续自动重试。
- 硬件：两机已释放/在线，只读getprop白名单、CPU/meminfo/sysfs/wm/数据分区块数；S4约1.928GiB、P4约2.910GiB内核总RAM，不再混称都2GB。rk3326/4逻辑CPU/最高频率字段1.512GHz/32位ABI相同，lowRam与128MiB应用档相同。未写sysfs或设置。
- 证据：本地_analysis/verify/device-performance-reference/hardware-2026-10-23.json；既有native-webview探针、final-s4/p4与optical报告及冻结源代码。正文/PIN/全配置未采集；原始技术文件不入Git。
- 交付：[38参考](../../docs/design/38-device-performance-reference.md)，区分硬件实测、历史性能与设计建议；性能阈值未达、光学阈值敏感性、跨会话/版本、PSS非峰值等限制保留。
- 验证：文档检查与白名单diff由根执行；不为纯文档重跑Android构建/设备压力。无新依赖/后台进程/设备租约。后续只需按需阅读和手动推送，不自动开启性能重构。
