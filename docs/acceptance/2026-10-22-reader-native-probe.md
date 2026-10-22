# RD-000 原生通路兼容性探针

- 执行者：Codex；基线1379e8b + PERF-001；2026-10-22。
- 探针源码/构建：`_analysis/reader-probe/src/ProbeActivity.java`、`build.py`，独立包 `com.shuiyu.octoberrain.readerprobe`；仅生成合成PDF，不访问十雨私有数据。
- S4：API30、armeabi-v7a、memoryClass 128MiB、isLowRamDevice=true；实际WebView `com.android.webview/83.0.4103.120`。
- PDF：合成两页，PdfRenderer读取页数2，渲染非白像素3524；SharedMemory像素往返成功；生成PDF+渲染+共享内存整体1379ms，非纯渲染或物理显示延迟。
- WebView83：CSS columns、TextDecoder、WebAssembly探测通过；CSS.highlights、Array.prototype.at不可用。不把DOM能力探测当成Office或EPUB样本验收。
- 原始证据：`_analysis/reader-probe/s4-native-webview.txt`。未测：100MiB PDF、隔离服务死亡、第三方文档资源、长时PSS。

## 本轮依赖决定

原生PDF、文本、ZIP来源和宿主先使用已安装Android API/JDK，不引入新依赖。001可据此冻结原生通路契约，000的Office兼容矩阵保持IN_PROGRESS，R4资源验证不阻塞34方案的基础阅读。

研究上游固定为 Gander `efa4ea9fa615dd9f523b43dcd282cbd51b6d8797`（GitHub API只读查询）。[来源清单](https://github.com/mokshablr/gander/blob/efa4ea9fa615dd9f523b43dcd282cbd51b6d8797/docs/VENDORED.md)中的docx-preview 0.4.0、JSZip3.10.1、SheetJS CE0.20.3与PPTXjs1.21.1仅为候选，尚未纳入产品、下载摘要或真机验收，不宣称支持。PDF使用平台[PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)，API30能力不含全文搜索/选字。


## 04:16 Office基础探针增量

候选资源已按固定Gander提交下载到独立探针assets，SHA256/字节数/来源见`_analysis/reader-probe/vendor-manifest.json`，版本及完整许可见同目录upstream-VENDORED.md、assets/licences.md。`fetch_assets.py`校验摘要，`build.py`可重建；未加入产品依赖或产品APK。

| 样本 | S4 WebView83 | P4 WebView83 | 验证边界 |
| --- | --- | --- | --- |
| SheetJS0.20.3，1001行中文合成xlsx写入再读取 | 中文true，1632–1740ms | 中文true，2385–2561ms | 读/写+HTML生成总耗时，不是纯首屏；未验大表/公式/图表 |
| 表格输出窗口 | range参数仍1001行；显式!ref后21行 | 同样21行 | 产品必须在HTML生成前裁剪范围；解析原包仍需单独限制 |
| DOCX0.4.0，80段中文合成docx | 80段中文存在，171–178ms，1section | 80段中文存在，166–259ms，1section | 不含图片/表格；breakPages不等于长文自动分页 |
| PPTXjs1.21.1，上游deck.pptx | 3页/363字符，1233ms | 3页/363字符，1835ms | 基础DOM输出，尚未截图核对图形/中文/复杂样式 |

PPTX初次失败由探针把jsZipV2误传布尔true导致：该参数期望脚本URL，触发额外脚本请求与localStorage访问；修正为预载JSZip2+jsZipV2=false，并提供本地XHR读取后通过。这是探针宿主接法修正，不能作为Chromium不兼容结论。无额外库patch。

内存仅记录host进程：S4约69116KiB、P4约38178KiB；WebView renderer另进程未计入，不能用这些数当完整内存预算。Chromium有Crashpad缓存目录提示，样本脚本完成；favicon不存在属于探针未提供图标。没有AndroidRuntime异常。

000仍IN_PROGRESS：commonmark候选、Office图片/图表/大样本、EPUB特性和renderer完整PSS待补。已验证基础库可执行，不宣称Office产品支持。探针结束后应force-stop并卸载释放资源。
