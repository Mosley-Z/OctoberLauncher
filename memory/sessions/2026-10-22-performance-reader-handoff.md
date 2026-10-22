# 2026-10-22 性能与阅读器 Codex接力

- 用户目标与最新范围变化：DSH开发结束，用户授权本聊天全面接管，实施此前8项性能优化；完成后用剩余5小时额度启动引用聊天的阅读器设计34，额度接近上限收尾，04:30继续。无需新确认；没有授权自动发布。
- 任务卡/设计/当前状态：PERF-001 DONE；RD000–005 IN_PROGRESS，其余TODO。阅读器不是全功能完成。先读PROJECT/STATUS/MEMORY、task卡与设计34/35。
- 源码基线、真实工作树、原有修改与本轮修改：D:/DSHStation/OctoberLauncher，master1379e8b；开工干净。当前全部性能+阅读器差异未提交，不reset/clean。VERSION仍1.0.28。现有DSH删除统计/月历/实验室必须保留。
- 已完成及实际证据：性能8项与7个新JVM用例，详见2026-10-22-s4-performance验收。新增reader模块9个测试及2个公共序列化测试；全量check/debug/release最终1556tasks、21秒退出0，日志_analysis/reader-probe/native-gate.log。reader Lint无告警，空错误baseline没有吞错。最终APK哈希见reader-native-first验收。
- 原生功能：LocalPath/ContentUri、受限外部入口、seek/有界临时缓存、TEXT窗口分页、编码/字号、字节位置持久化、书签新增列表回跳、分块搜索下一个/取消、音量键及墨水刷新。UI主线程发布、后台IO，generation与close释放。只记录已显示帧。全文不进内存，页历史最多64。
- 设备证据：S4八次翻页后强停恢复3536；搜索4001、书签回跳4001；GB18030显示；50MiB文本首屏；管道171000字节缓存退出归零。AndroidRuntime无异常。完整流程在最后Lint修正前执行，最终包安装/启动成功，不把最后冒烟扩大成所有场景重测。P4最终release安装启动，原生产品阅读流程尚未验。
- Office兼容探针：两机均API30/armeabi-v7a/128MiB memoryClass/lowRam/WebView83。固定Gander efa4ea9fa615dd9f523b43dcd282cbd51b6d8797。docx-preview0.4.0、JSZip3.10.1、SheetJS0.20.3、PPTXjs1.21.1仅独立探针；vendor-manifest有摘要/来源，assets/licences有完整许可。fetch_assets.py/build.py可重建；assets/lib和out忽略入Git。未采用产品JS依赖。
- 兼容发现：SheetJS sheet_to_html的range参数仍输出1001行，显式!ref裁剪后21行；产品必须先裁剪，解析仍需预算。docx80段只1section，breakPages不代表自动长文分页。PPTX3页/363字符DOM通过；第一次参数jsZipV2=true错误已修正为预载JSZip2+false及本地XHR，非WebView版本失败。Office图片/图表/大样本、commonmark、EPUB和renderer PSS待补。
- 未完成/失败/待核实：产品PDF、Web、EPUB、ZIP、Markdown、Office全未实现；当前明确提示引擎未启用。搜索高亮/结果列表、稀疏索引、书签编辑删除/最近阅读、旋转重排、异常退出缓存回收待后续卡。不要把空Registry/OpenPlan草案当实际类。
- 接口决定及下游消费者：task/CONTRACTS.md冻结原生v1；core/common/ReaderContracts.kt只放描述/位置/能力；ReaderEngine内部page/turn/suspend findNext/close，ReaderSession暂仅创建TextEngine；SQLite版本1。后续增加引擎增量冻结兼容，不另建同名不兼容契约。
- 共享文件、构建进程、设备占用/恢复：没有运行中的Gradle。S4 SA4B03CN2608081096X装debug，P4 P4L202610130006装release；两台readerprobe均已卸载释放WebView，S4回桌面。合成阅读记录在新reader.db，不包含用户正文。截图只在系统TEMP；不要拷贝设备凭据/偏好/历史PIN。
- 下一步：04:30醒来先查limits、时间、git status和设备，再停用automation id04-30（一次续工，不重复每天运行），确认无本聊天工作并发后读TASK-RD-006与设计PDF部分，落地隔离service/PFD/SharedMemory串行渲染和预算/取消；不要把独立probePdf当产品服务。若先补002–005，按卡剩余验收实施而非重跑已通过门禁。
- 工具链：toolchain.path -> D:/DSHStation/.toolchain，JDK该目录jdk下含javac的子目录，SDK android-sdk，Gradle8.11.1，GRADLE_USER_HOME gradle-home；离线构建。默认sandbox native-platform.dll曾不可写，require_escalated已获自动审查允许。一次审批超时，按允许重试一次成功，没有未解除审批阻塞。格式化先独立ktlintFormat结束再check，不能同一Gradle批次同时格式/检查。
- 可复用经验及来源：git diff --check用 -c core.whitespace=cr-at-eol兼容原有混合换行；Python显式encoding=utf-8。独立探针自带只读合成Provider，不需要访问用户文件来验编码。WebView host PSS不是renderer总PSS。
- 提交/推送/发布情况：均未执行；文档检查仅2个已登记历史删除源码WARN，无新错误。
