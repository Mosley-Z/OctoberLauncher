# 原生PDF隔离阅读验收（2026-10-22）

基线1379e8b + 本轮PERF/原生阅读器未提交差异，版本仍1.0.28。负责人Codex。对应TASK-RD-006；本报告仅原生PDF，不包含Web/EPUB/Office产品实现。

## 结果

- 非导出isolatedProcess服务、只读来源FD、oneway AIDL异步回调、小型Bundle元数据、SharedMemory固定视口像素；PdfRenderer/Page由单线程串行操作。绑定5秒/请求15秒超时，服务死亡能返回失败；离开取消等待并解除绑定。
- 宿主位图LRU最多12MiB，只留当前和邻页；缩放1–4倍只改矩阵，不创建整张放大页。支持上一页/下一页、跳页、平移、裁边5%、整页适配、页/视口书签和恢复。API30明确不显示搜索、选字、批注工具。
- API26禁用该服务，v27资源开启且Registry有SDK检查；未改全局minSdk。来源临时配额收敛到设计中的128MiB。ReaderScreen隐藏时仅保留Locator，清除帧引用，避免返回栈保留页位图。
- Android实际隔离进程名会在`:reader_pdf`后追加服务名；最初按完整进程名相等判断导致子进程误初始化桌面配置并崩溃。改为受限前缀判断，S4隔离UID990xx只运行PDF服务，不初始化桌面文件/网络服务。原始失败证据`s4-before-application-guard.txt`，主进程未因此退出。

## 复现与设备验证

`generate_pdf_fixtures.py`生成中文文字、AES加密、损坏、14400×14400点大页面和107977904字节/114页的合成扫描PDF；样本摘要见`_analysis/reader-probe/pdf-fixture-manifest.json`。样本不含用户内容，产物不入Git。

S4 SA4B03CN2608081096X（API30、armeabi-v7a、128MiB memoryClass、2GB设备）：

- 飞行模式并关闭Wi-Fi后，约103MiB扫描PDF首/尾页1/114、114/114可读，含跳页；退出后隔离进程列表为空。无线配置已恢复到测试前飞行模式0、Wi-Fi1、蓝牙0。见`s4-flow.json`。
- 普通中文PDF翻页、150%缩放、20次前后翻页可用。返回重开、强制结束主进程重开，恢复2/3与150%视口。
- 注入`am crash <隔离PID>`后主进程PID保持不变；下一次未缓存渲染显示服务失败，返回后重新打开恢复。注入崩溃日志单独记录，不能把它误写为正常路径无异常。
- 加密PDF明确说明API30不支持解密，损坏PDF明确失败；两项失败后隔离进程为0。超大页面按视口可读。见`s4-failure-recovery.json`；恢复后的正常路径AndroidRuntime日志为空。
- 临时调试计时为请求到主线程发布帧，不包含物理墨水刷新。该轮扫描/普通首屏2239/2852ms，首屏≤2秒目标未证明达成；21个动作样本中跳尾页290ms、首次渲染223ms，其余缓存翻页5–27ms。整组P95=223ms；缓存命中部分均低于200ms。后续017需分段测绑定/解析/渲染及多轮P95，不能宣称已满足所有性能目标。
- 同轮103MiB尾页宿主PSS79019KiB、隔离服务30621KiB，合计109640KiB；回桌面样本宿主80048KiB且无隔离进程。一次采样的差额29592KiB，含控件/缓存状态变化，不是稳定内存保证；已包含隔离进程，未把Java heap当总PSS。

P4：此轮debug安装与启动通过；屏幕截图仍全黑、层级无阅读控件，未绕过锁屏或改显示配置。不能把此前独立探针P4通过写成产品PDF完整交互通过；P4补测留017。

## 门禁与交付

全项目`check :app:assembleDebug :app:assembleRelease --offline --console=plain`退出0，1560tasks/21秒；日志`_analysis/reader-probe/pdf-gate.log`。reader Lint无问题，新增3个纯计算预算/定位测试和1个旧PDF定位兼容测试通过；框架返回默认值的JVM测试不替代设备PDF证据。未提交、未推送、未升版本或发布。

- debug：5321448字节；SHA256 `fcdc32871676063a3531754661bd5426159959bd0849f06fbe0807f43932fbcc`。
- release：1252893字节；SHA256 `b7066b8fdeaca48fe4301054411c8e1fb7d64e6faa4005bf13b18a27c8b0167a`。

最终PDF release补验：S4/P4覆盖安装成功；S4原生PDF显示2/3、150%，R8后模块反射、Service/AIDL及渲染闭环可用，AndroidRuntime无异常。证据release-smoke.json/s4-release-runtime.txt。P4仍只计安装启动。S4回桌面、probe已force-stop，/data/local/tmp的5个本轮合成PDF已清理；probe私有合成文件保留供后续017复测。
