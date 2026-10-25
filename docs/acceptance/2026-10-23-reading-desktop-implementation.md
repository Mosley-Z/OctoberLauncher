# 2026-10-23 阅读桌面实现与设备验收

- 范围/验收者：UX100–107、109、110；Codex集成，三个GPT-6.1 Sol / low子智能体分工；UX108按用户要求搁置。
- 源码：master/f025b18 + 本批未提交差异；保留原有网站、阅读器验收与工具链差异。产品1.0.31/51不升版，配置v14。
- 最终APK：`app/build/outputs/apk/debug/app-debug.apk` SHA256 `c2fec3d02d802b373fa53d41e5db90435b1bceebec002d48c96d58a0ec1df9e7`；`app/build/outputs/apk/release/app-release.apk` SHA256 `a256de3309f22a02dfbd368e34d97446dc0b809551f42c286d9ead9d20ccf472`。
- 两机：S4 SA4B03CN2608081096X（480×800/219dpi/API30）；P4 P4L202610130006（552×768/210dpi/API30）。恢复后两机安装SHA256均等于上述最终release，rotation=0。
- 设计：[37方案](../design/37-reading-desktop-proposal.md)、[设计稿](../../design/reading-desktop/README.md)。任务：[UX100](../../task/TASK-UX-100-integration.md)。
- 最新纠正优先：首启只保留本机录入二维码，手机粘贴已复制API Key传给墨水屏保存；官方获取仅普通文字步骤，无官方二维码/打开网页按钮。历史双二维码证据不作为最终交付。

| 用例/命令 | 前置/操作 | 实际/退出码 | 证据 | 结论 |
| --- | --- | --- | --- | --- |
| 全门禁 | 格式后`check :app:assembleDebug :app:assembleRelease --offline --console=plain` | 0；25秒、1564任务 | `_analysis/verify/desktop-ux/gate.log` | 通过 |
| 全项目单测 | debug/release各905例 | 每变体0失败、1既有Ignore（MonthGrid周首日口径待修） | 各模块build/test-results | 通过，保留历史跳过 |
| 受影响模块 | data/home/settings/widgets/widget-api/weread-sync/weread-app | 139/23/60/83/7/54/11，均0失败0跳过 | 单测XML | 通过 |
| 配置迁移 | 旧列表/显式自动/备份一致/名称迁移 | v14幂等，原始字段缺失与0区分 | DesktopLayoutMigrationTest | JVM通过 |
| 主题规则 | HOME容量不改其他页、透明精确目标/图片互斥、旧包缺字段 | 新增用例通过 | ThemeAppearanceTest | JVM通过 |
| 首启旧基础 | 启用/断点冷启/跳过/完成后不重跑 | S4通过；旧双QR被最新需求替代 | `s4-onboarding-report.md` | 相应项通过 |
| 最新手机录入 | 实际解码本机二维码，GET粘贴textarea、POST合成Key | HTTP200、保存=true、强停重启保留=true；测试主机LAN直连超时，改临时ADB forward验证同一服务，已移除forward | `phone-upload-report.md`、123532-770-S4 layout | 传输/保存通过；真实手机同网实扫未测 |
| 独立首页容量 | UI首页3→4列，其他页仍3×4；列表首页条数1 | P4白名单字段读回通过 | `p4-report.md` | 已测项通过 |
| 列表布局 | 靠左→居中、标准→宽边距，回HOME | P4图标+名称组居中且留白变化，设置落盘 | 114158-652-P4截图 | 居中/宽边距通过；三对齐全矩阵未测 |
| 沉浸与抽屉 | 单行四常用入口、应用分页、启动图库、Back、退出 | P4 debug和R8 release均通过，退出恢复普通布局 | `p4-report.md`、`p4-release-report.md` | 通过 |
| 横屏 | UI90°和270°，270°实际启动应用/冷启 | rotation1/3；左本书/clock、右应用竖栏；270°触点和冷启通过 | P4报告；截图物理坐标需按rotation换算 | P4已测项通过；S4产品横屏未重跑 |
| 普通组件 | clock/image序列，RIGHT | P4 clock→image→clock，直接切换 | 115414/115629-P4截图 | RIGHT通过；UP/OFF未设备穷举 |
| 长本记覆盖 | 合成60段，折叠→展开→滚动→Back | 约1.5段→4段，覆盖至末行名称下缘约y760；滚至04–07段操作栏固定；Back恢复应用 | 114633-104/114758-373/114805-121-S4；R8 release122553-651-S4复查 | 通过 |
| 短本记 | 单行合成短文冷启 | 完整显示、无展开按钮 | 115335-007-S4 | 通过 |
| 透明图标 | 设置入口改透明，Back再点击名称 | 无图片/边框，名称保留且仍启动设置 | 120019-415/120300-844-S4 | 通过 |
| 主题资源往返 | 纯合成PNG+透明状态：首页保存→导出→导入→删原主题→应用导入副本 | 导出PNG698字节逐字节匹配；独立wallpaper/theme-*副本匹配、透明保留、其他页3×4 | `s4-theme-report.md` | 通过 |
| 文档与差异 | checker、diff检查 | 0新增错误；2既有历史源码链接WARN | 最终命令输出 | 通过 |

## 边界与恢复

- 本记竖屏覆盖到实际末行名称下缘；横屏左组件已全高，转独立本记页，避免展开变短。横屏后备和覆盖点底层应用的完整设备矩阵未测；触摸实现先拦截覆盖层。
- 无真实账号Key，因此在线验证成功、无书成功、成功后首轮真实书架加载未验证；已实现保存与验证分离、断点、暂停旧请求丢弃。未把合成Key持久化测试当在线账号通过。
- 真实封面内部手势、隐藏名称全视觉矩阵、透明→图片→默认全循环未设备穷举；代码/相关规则门禁通过。任务保留READY_FOR_REVIEW，不伪写全矩阵DONE。
- 两机原APK和私有备份在系统临时目录；备份/密钥/用户正文不进Git。原release不可run-as，先保存原APK，同签名debug覆盖但不启动后备份；缓存隔离，只写合成数据。
- 最终逐字段合并恢复测试触及配置，保留其他当前字段；原微信读书目录恢复，合成下载包/临时主题清理。私有比对确认原Key、壁纸、应用顺序恢复（只输出布尔结果）。两机最终安装本页release，设备/构建租约均释放。
- 尝试复制含实际图片的旧验收主题被自动审批拒绝，未执行；改为0用户图标且PNG字节可核实的纯合成主题完成测试，没有请求放宽审批。
- 并行截图旧脚本秒级目录可能碰撞；现目录加入毫秒和serial。尺寸/内容不符的旧图不作为验收。自绘图标在横屏物理截图下需rotation坐标转换；技术字段和实际启动另记。
- detekt只随compute→computePage改名迁移两条既有基线；新增告警通过拆分修复，未扩大豁免。临时UX_DIAG日志已移除。
- 未提交、推送、升版或发布；休眠/关机壁纸未新增入口。
