# 2026-10-23 阅读器最终收口（已完成）

用户要求继续上轮剩余1–4，第5只最终任务卡关闭；小版本迭代、不正式签名/发布，后置格式搁置。基线master/84a035e。未覆盖UX001/网站/Wrapper与gradle.properties等别人的dirty。根唯一Gradle；两个设备分别单agent独占，不能抢ADB serial。

## 当前实现与产物

1.0.29/49。SourceResolver新增DescriptorDocumentIdentity/LocalIdentityRules：Provider regular PFD readlink绝对live路径、canonical可读file且fstat/path stat dev/ino/size相同才metadata.id=path并aliases=URI；读数据仍原URI，不能绕过授权，管道/私有未知保持URI。4个规则用例已过，不按正文/同名合并副本、移动新路径独立。

ReaderTiming可用setprop log.tag.ReaderPerf DEBUG启用，默认空；动作接收elapsedRealtimeNanos→成功publish/Web ready后下一dispatchDraw记录sample/event/format/elapsedUs，不含来源/正文。边界点击event=boundary不算翻页。sourceClosed实际cache count/disk/reserved，pdfCache实际LRU字节/entries，避免release run-as缺口。

802b候选包136测试/variant门禁过后，S4发现冷启成功都sample2；Main入栈与Activity.onResume重复调用ReaderScreen.onResume，首open被取消。修visible幂等后215b，sample1但完整入口P95 TXT2288.832/PDF2630.445ms未达2s；内部计时并不等于完整冷启动。Main分段约1s，隐藏Home先onResume取组件/目录；最终4e98在合法文档冷启时仍建Home View/栈但跳过其首次onResume，失败补resume，后退Home正常首次resume，锁层/引导不变。代码只读审查无新漏调用，实际回归待设备补。

最终候选_analysis/verify/final-reader/reader-1.0.29-home-defer-release.apk，2171928B，SHA256 4e98a2438409bade2078783a2eab04b90b8b48bebe639c691b56e35ee6a3bac5；gate-4完整check/debug/release退出0，25s/1564tasks；reader debug/release各136例0失败。151摘要source-manifest.json，CRC及reader全部assets与215b相同。代码此后冻结，除已证bug不重构。第一次gate-1只有perform复杂度16/15失败，已抽事件分类修，不扩基线；gate2/3/4均实际通过。各中间包及baseline分开留证。

## 并行与已完成

- p4_remaining独占P4L202610130006；新测试com.shuiyu.readerfinalprobe（原先不存在）真实DocumentsProvider+合成OPEN_DOCUMENT picker。系统DocumentsUI确无，不是禁用；Android OS真实persistable/revoke链已实测：VIEW65保存1%书签→强停→负一屏本地最近重开无新VIEW仍1%→provider revoke后最近明确重新授权→产品重新选择→合成picker再授65恢复1%和书签。不能称厂商DocumentsUI界面通过。
- P4第三方同文件3%/书签与本地入口共享；相同字节copy0%、新移动path0%，移动回原path仍3%。RD004独立versioned.txt size79200→79222及mtime变更后进度0%、字号保留、旧49%书签仍可跳；字号已减回。上游XLSX/XLS19×2缓存180、ODS6×12合并及实际横纵滚动通过。忙碌32MiB搜索真实横屏且离开HOME不旧回填、重开0%，方向按auto0/user0等display0再auto1恢复。后续正做锁/后台/真实离线（原wifi1/airplane0）及FIX002/003必要P4闭环、资源quota；不要读PIN/全prefs/用户正文。
- s4_performance独占SA4B03CN2608081096X；原readerprobe本来存在，不卸。原480×800/219、auto1/user0/display0、wifi1/mobile1、ReaderPerf prop空。真实logcat monotonic支持，取自己合成Entry START到同次ReaderPerf draw，Main Displayed不是正文终点。4e98 TXT20全链路P951747.823ms达2s、内部502.648ms；PDF20中。之后Office各20、缓存TXT/PDF各20及0/6/12条件、上游DOCX/PPTX、50MiB长行emoji末段搜索/恢复、状态回归。50MiB文件根生成final-reader/large-text-50m.txt精确52428800B，唯一末段FINAL_LARGE_TEXT_TARGET。
- uri_identity已完成源码/4测试、8官方上游复杂回归样本与24卡只读审计，无活跃设备/构建。上游固定commit+许可+hash在upstream/manifest.json，不进产品/二进制Git；S4测DOCX/PPTX，P4测表格。

## 物理录像与下一步

用户已答可60fps录像、两机都能拍。自动测试目前仍占serial，明确告知先不碰设备；之后需各20partial翻页（刷新0）、20手动full（顶部时间单击，不是长按），原始视频不压缩。physical-pattern.pdf 8290B、SHA e75528b0b8e3755febaf39d14392e71624043612a347598e74addb583625012f，两页A/B反相8×8棋盘。摄像头无数据前不能关闭物理耗时项；60fps报告约16.7ms帧分辨率/两端误差，不声称厂商波形API。S4有ebc节点但无公开安全ioctl契约，未写设备/系统，不以黑overlay虚称波形控制。可用本机ffmpeg/ffprobe(D:/Program Files/ffmpeg-8.1.1-full_build/bin)及python cv2/numpy分析视频；只拍合成屏幕。

等双机实际结果与恢复；补卡000、002–005、011–017及FIX依赖最终状态，018–020已DEFERRED。审计允许复用历史S4已通过且未变的部分，不重跑所有旧矩阵；FIX004自动30分钟被FIX006取消，不能恢复。RD016/017所有必要项有证据才关闭。视频/未达标仍需报告和修复，不能只写本轮DONE。

文档最终检查、源码/APK摘要、签名仍debug、设备字段恢复后提交必要文件。注意memory/STATUS.md含别人UX001未提交段，不能whole-file stage连带UX；根新增section另存final-reader/status-current-section.txt，届时可用HEAD旧STATUS+本section构造精确cached patch（保留working UX dirty）。task/README/docs设计索引/.gitignore也有无关UX dirty，别顺手提交。root新增android-ui.ps1 Action swipe -Label/-EndLabel从当前截图resolve两端后wrapper input swipe，P4已实机导航，脚本语法过；33手册追加用法。无正式签名/发布。

## 中段更新：1.0.30与录像已完成

0d242dd修复官方master-slides扩展区p14:sldId误索引，专属6例/真实文件harness通过；gate6完整成功，reader137/variant。最终APK与SHA见STATUS，151摘要source-manifest更新，旧4e98单独source-4e98-manifest保留。b4ee37f/0d242dd已推送GitHub。原片光学reader-optical已完成且根复核两机接触图，不再等待视频。两机40事件齐全，帧PTS/摄影配准/5%主定义与3%敏感性均有证据。

S4最终4e98五格式冷启100个关闭样本三项实际缓存均零；PDF/PPTX完整首屏未达目标如实保留。P4 TXT200/30、CSV200/30已完整，图标安全主题及书架回归继续。S4 DOCX200/30仍在跑，约55页时仅证据脚本取消改唯一ReaderPerf逐步ack+每20layout/PSS，已发生55补录且不重跑。之后两机覆盖1.0.30仅PPTX官方母版/必要smoke，不重跑未变引擎全批。根无Gradle/推送活跃进程，设备仍各agent独占。最终卡/文档尚未结束，不提前final全部DONE。

## 最终交接（取代前文进行中现场）

- 用户授权剩余1–4与第5任务卡关闭已执行；RD000–017及FIX001–006 DONE，RD018–020 DEFERRED。最终1.0.31/51沿测试签名，不正式签名/Release；其他UX001/网站/Wrapper差异保留。
- 源码1ca51be（前序b4ee37f/0d242dd）；最终reader-1.0.31-icon-release.apk2172584B，SHA29784637149498aa00814b4dcf2af60bc4f09b3a1da5986eb58eeb0427b40005；154源码摘要零差异，两机实际base.apk相同。gate7 check/debug/release成功，reader137/data128每变体零失败，Android Debug签名有效。
- 已补真实OS持久授予/强停最近重开/撤销重选、同源与副本移动、版本变化、官方Office及50MiB、状态网络/renderer恢复、图标主题；真实PPTX扩展节点误索引及坏图破坏旧图两缺陷修复并实机前后对照。
- 性能主体4e98各20冷启P95：TXT1.748s、PDF2.173s、DOCX4.481s、XLSX4.800s、PPTX6.335s。PDF2s/PPTX5s目标未达，分段定位保留，不因卡DONE宣称达标。P4 TXT/CSV与S4 DOCX各200/30通过；历史包与最终专项归属分列。
- 原片实际PTS/摄影配准光学P95：S4翻页116.5/软件manualFull902.6ms，P4 90.7/489.6ms；各20，5%ROI定义/3%敏感性及帧误差明列，不含触点等待/厂商波形控制。
- S4/P4均最终包/HOME、全刷6、原生480×800/552×768、auto1/user0与原网络恢复，ReaderPerf回空。P4新增probe/临时主题PNG已清，S4旧probe保留、仅清14已知合成文件；设备已释放，无活跃测试/Gradle。
- 局部未测保留：厂商DocumentsUI不存在、PIN凭据锁未动、P4桌面BOOK双向未单测、本次S4锁屏未覆盖、实体/模拟宽视口未改变Main；历史S4和P4尺寸证据独立。有限样本非全格式保真或长期无泄漏。
- 权威：[最终收口](../../docs/acceptance/2026-10-23-reader-final-closure.md)、双机/光学报告及[本轮交接](2026-10-23-reader-final-closure-codex.md)。交付目标GitHub origin/master；原始视频/APK/夹具只存_analysis/verify/final-reader。


最终新图标修复1ca51be/1.0.31：独立副本校验及config读回成功才清旧图，6例IO测试，gate7 check/debug/release成功1m22s。P4同样有效图→坏图→强停仍有效，先前1.0.30会丢图反例与缺失默认回退均留证。S4新包PPTX母版/图片/表格、MD/EPUB/HOME负一屏通过，受控renderer故障明确错误并恢复DOCX2/37。两机实际base.apk与产物297846一致，无设备或构建租约遗留。

本轮需要提交的仅代码/卡/报告/记忆/索引，UX001/网站/Wrapper/gradle.properties等原差异和原始_analysis不带入。STATUS和看板含别人UX修改，根按HEAD+本轮独立段生成精确cached patch，不整文件暂存。最终Git推送由根执行并核对；无PR/正式Release。

## 最终推送阻塞

开发与验收提交已完成：1ca51be图标修复，4b0390f文档关闭。origin/master已成功到0d242dd；后续push连续出现github.com:443连接超时，HTTP/1.1最后尝试连接重置，DNS解析正常。未强改远端/用户代理/凭据。最后两个提交及此阻塞记录尚未上传；网络恢复后仅需git push origin master并核对HEAD与origin/master，勿重跑已完成设备验收。所有设备与构建均释放。
