# 阅读器依赖冻结与用途（2026-10-22）

本轮为007离线WebHost/009 Markdown增加必要的JVM/AndroidX依赖，不捆绑第二套浏览器。版本在gradle/libs.versions.toml固定，实得产物SHA256/字节数见`_analysis/reader-probe/dependency-manifest.json`；首次在线解析使用本机代理，后续离线构建已通过。

| 坐标 | 用途 | 采用依据/验证 |
| --- | --- | --- |
| androidx.webkit:webkit:1.12.1 | WebViewAssetLoader、本地HTTPS路由、带可信来源/主帧信息的WebMessageListener | [官方版本记录](https://developer.android.com/jetpack/androidx/releases/webkit)；S4实际WebView83.0.4103.120的WEB_MESSAGE_LISTENER=true，图文/定位消息成功 |
| org.commonmark:commonmark:0.21.0 | JVM解析Markdown，避免新版marked对Chromium92+的要求 | [固定tag说明](https://raw.githubusercontent.com/commonmark/commonmark-java/commonmark-parent-0.21.0/README.md)说明Java8/Android19+的尽力支持；实际JVM与API30图文解析通过，不将文档声明代替真机 |
| org.commonmark:commonmark-ext-gfm-tables:0.21.0 | 表格语法 | 同版本核心扩展，中文/代码/表格测试通过 |
| org.jsoup:jsoup:1.23.2 | 允许列表清洗HTML、移除脚本/事件/框架/原样CSS | [官方发布记录](https://jsoup.org/news/release-1.23.2)；仅调用本地解析/清洗，不调用Jsoup网络功能；恶意标记测试与S4样本通过 |
| com.android.tools:desugar_jdk_libs:2.1.3 | 按库Android支持要求提供Java库API兼容 | [Android官方配置](https://developer.android.com/studio/write/java8-support)；app与reader启用coreLibraryDesugaring，保持JDK17/compile35/min26，不改其他模块的源码语言级别；R8/设备门禁检验实际调用 |

完整许可文本随产品放入`feature/reader/src/main/assets/reader/licenses/`，关于页可点开阅读；来源及摘要见license-manifest.json。AndroidX为Apache-2.0，commonmark为BSD-2-Clause，jsoup为MIT，desugar_jdk_libs为GPL-2.0加Classpath Exception。该清单是上游许可登记，不代替上游原文。

Commonmark默认不会完整清洗HTML，因此使用escapeHtml/sanitizeUrls后还经jsoup允许列表。资源仅限当前文档根；单文件content URI不能虚构目录授权，图片只有已显式授予的前缀/URI可读。外部入口及MainActivity均要求READ_URI_PERMISSION，转交已提供的ClipData/前缀授权；没有任意路径接口。

预算：Markdown原始UTF-8输入1MiB，单行8192字符、2万行、5万AST节点/HTML元素、深度128，生成HTML2MiB；超预算可明确选纯文本阅读。图片每文件8MiB、每张8M像素、累计32MiB压缩字节/16M像素；这些是首轮约束，不能当全部复杂文档性能承诺。GIF/WebP/APNG动态图片、超大图及压力矩阵留016/017确认，未声明所有图片组合已验收。

探针中的Gander/Office脚本仍未加入产品资源；本轮产品新增JS为自写的Chrome83兼容小脚本，不使用.at、CustomHighlight或全局polyfill。R8后包体以对应阶段验收报告的实际APK摘要/字节数为准，不用依赖jar原始大小推断APK增量。
