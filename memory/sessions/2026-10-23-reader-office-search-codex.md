# 2026-10-23 Office/统一搜索接力（本轮已交付）

## 授权、源码与产物

用户要求“提交然后继续，P4已上线，可以用子智能体并行开发和测试”。已提交0a1534c基线（194文件）、7d8a680 Office/统一搜索（87文件）、2da644b重复外部入口任务复用修复（仅Manifest一行）；本轮验收文档随后单独提交。master，未推送/升版/发布，仍1.0.28/48。网站、未验证Gradle9.6 Wrapper/gradle.properties及_analysis原始证据未纳入。

最终APK `_analysis/verify/office-next/entry-affinity-release.apk`，2,170,000B，SHA256 fbd4bebc2e593fc413ea77b03f1f2f5b2125b748080a35d1fbcab2d8fe2878e9；2da644b。清单entry-affinity-source-manifest.json复核146文件，只有Manifest相对477变化，reader所有JS/CSS逐字节一致、APK CRC通过。完整check/debug/release确认退出0，entry-affinity-confirm-gate.log（5s/1564tasks），reader debug/release各132例0失败。首次补丁门禁SUCCESS1m9s但外层pwsh等待；核对构建子进程已结束后仅停止该外壳7152，再直接调用脚本确认退出0。没有活跃构建。

引擎原冻结包office-search-final-release.apk，477ab628f67c8670428ae6d6e3b471f57236a2bfaafd2fe9c82df246944d50be、7d8a680；final-gate-4.log通过。双机200翻页使用此包，fbd4的30真实隐式开关与小回归单列；不称fbd4重跑200。

## 实现与发现

CSV原生流式/四编码/逻辑记录，SheetJS0.20.3原生20×10窗口，PPTXjs1.21.1完整资源链及单页关系闭包；8MiB/32MiB和结构预算；Sheet/Slide Locator，统一SearchProvider完整计数+64窗口/上下个/实际取消/受控高亮，精确VIEW/SEND MIME和完整许可。无新Gradle依赖，既有数据库不重建，查询不持久化，命中显示后才保存位置。

已修代理尾重复计数、Text旧history、Workbook高亮传递、JS实际取消、旧CSV Text字节锚点/编码迁移、PPTX同段跨样式run完整词漏搜。P4旧基线→fcf3自然覆盖升级UTF8 BOM/GB18030旧进度/书签均142行1列；quoted换行无偏移，GB无需重选。最终fbd4仍保持。

S4在30开关前发现NEW_TASK重复同URI仅前置HOME：task baseIntent是Entry但活动仅Main，Entry/Main均无回调；显式Entry同样失败，MULTIPLE_TASK一次对照成功。2da644b仅Entry taskAffinity为空，分开临时Entry和长期Main任务。两机不清旧task自然覆盖后，两次同URI隐式VIEW均成功，再各完成30完整隐式开关，不用显式Main绕过。sheets只读复查同结论，无额外源码修改。

## 设备结果与资源释放

- P4 P4L202610130006：477工作簿6000×10完成200有效翻页，行1→4001；旧包30次开关均恢复4001。11阶段总PSS116,792–123,766KiB。fbd4再完整30次相同URI/flags1/限定包隐式开关，逐次4001→HOME；打开10/20/30合计148,913/148,323/145,242KiB，关闭92,758/90,449/87,741KiB，无绑定renderer。补两旧CSV与真实Parcelable Uri+ClipData DOCX SEND。实际安装hash吻合；全刷6/原生552×768/HOME，新增probe卸载且pm path空、临时send文件删除，serial已释放。第8次准备延迟补等就绪再关闭后才计数，不是入口失败。
- S4 SA4B03CN2608081096X：477核心TXT/MD/EPUB/DOCX双模式/PPTX完整计数/前后/高亮、64/65、取消换书、字号书签重启、PDF无搜索/ZIP/真实入口均通过。200有效翻页逐次页号+标题断言（1→200→199），阶段总PSS130,234起、40次171,734后回落、200次145,873KiB。fbd4完整30次HOME闭合+隐式恢复199；打开10/20/30总137,814/136,031/134,008KiB，关闭129,566/118,673/104,552KiB，关闭采样无绑定renderer。TXT99%、EPUB3章17/17及真实DOCX SEND小回归；全刷6/原生480×800/219dpi/HOME、无绑定renderer，serial已释放。关闭后31个renderer code-1逐PID配对isolated not needed；smoke后主174,340KiB另记，不混循环末点。脚本只读HOME变量错误未完成循环不计数。

两机保持最终fbd4安装。根唯一Gradle，当前无本批测试/ADB/Gradle任务；其他项目Python进程未动。release私有缓存计数不可读，未编造0；阶段PSS不是全时峰或长期无泄漏证明。

## 证据、状态与后续

[集成交付](../../docs/acceptance/2026-10-23-reader-office-search-integration.md)、[S4](../../docs/acceptance/2026-10-23-reader-search-s4.md)、[P4](../../docs/acceptance/2026-10-23-reader-search-p4.md)逐包列证据。RD012/013/014 READY_FOR_REVIEW；RD016/017保持IN_PROGRESS，未写全方案DONE。固定vendor不改原始空白；精确白名单和摘要在office-next/whitespace-exclusions.json，自有代码/文档diff检查通过。文档检查128篇/40迁移/27卡、2历史WARN、0错误。

后续需真实SAF persistable授权撤销/重选（现机无DocumentsUI）、通用第三方URI身份、真实复杂Office集合、全桌面网络/锁屏与其余全格式压力、首屏/翻页P95和物理EPD波形/耗时。当前控制器黑覆盖/invalidate不等于厂商波形API。不要重复本轮已过验证，不以截图/CLI墙钟或小合成样本推断剩余通过；没有授权发布。
