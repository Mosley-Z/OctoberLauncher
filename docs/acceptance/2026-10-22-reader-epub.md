# EPUB首轮验收（2026-10-22）

源码master / 1379e8b加本轮未提交差异，仍1.0.28 / 48。RD010首轮完成，不代表全部阅读器任务或最终双机矩阵完成。

实现container/OPF/spine、EPUB3 nav及NCX，UTF8无加密资源重排版EPUB；当前章加载、逐章搜索、字符/章节定位、目录、包内跨章链接、字号、图片章、书签与恢复。章节最多2MiB/5万标记，spine最多5000；沿用ZIP中央目录4MiB/2万项及128MiB缓存总预约。包内CSS最多16份、过滤输入单份256KiB、生成样式128KiB，只接受有限排版属性与作用域选择器；不加载导入、字体、定位或网络URL。固定版式及encryption.xml保守拒绝（含字体混淆资源）。不宣称完整作者CSS/编码/排版兼容。

## 验证

- 最终check/debug/release退出0，1564 tasks，38秒；reader Lint无问题。reader30测试全部通过，common81通过+1历史MonthGrid跳过。
- S4 Android30/WebView83离线分阶段观测：字号26→28保持锚点57；第一章搜索到第三章锚点2153；图片章前后翻页、目录、书签和重启恢复。s4-epub-flow.json旧驱动尾段曾停止，不宣称旧驱动退出0。
- 最终check_epub_finish.py退出0：工具栏中心可用，书签与强停恢复均保留spine2/锚点2153，HOME缓存total0，见s4-epub-finish.json。
- 最终check_epub_release.py退出0：第一章CSS、跨章链接到第三章、图片章、加密资源/固定版式明确失败、EPUB MIME注册通过；release不含debug测试receiver。HOME后Web sandbox消失，见s4-epub-release-final.json及runtime。
- S4底部手势区756–800px会拦截原743–800px按钮中心。ReaderChrome按根系统insets避让，并提供手势模式下系统尺寸回退；最终按钮699–756px。未改设备导航设置。驱动已修正为删旧XML、确认新dump成功并等待按钮启用，避免冷启动陈旧证据。
- 证据均在_analysis/verify/epub-010；APK大小/SHA256见final-apk-manifest.json。S4/P4覆盖安装最终release保留数据；P4只计安装启动，不计阅读交互。S4无线恢复，合成probe卸载。

## 限制与交接

大书持续压力、复杂CSS/字体、RTL/垂直/SVG/其他编码、API26/27及P4完整交互由017及后续验证；没有首屏2秒或稳定内存节省承诺。Office未产品接入；书签编辑删除、最近阅读、旋转、稀疏索引等仍见002–005卡。按用户最新指令交DSH后停止开新卡，未提交/推送/升版/发布。
