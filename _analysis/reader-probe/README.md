# 独立阅读器兼容探针

不属于产品模块，不依赖桌面用户数据；测试只使用程序生成的中文文本/文档和固定MIT上游演示幻灯片。

1. `python _analysis/reader-probe/fetch_assets.py` 下载锁定commit的候选库并逐项校验SHA256。仅探针assets目录持有候选资源，产品APK未加入任何JS库。
2. `python _analysis/reader-probe/build.py` 使用仓库toolchain.path、JDK/Android35打包并以本地标准debug密钥签名。
3. 安装out/reader-probe.apk；`am start -n com.shuiyu.octoberrain.readerprobe/.ProbeActivity --es office office`测试DOCX/SheetJS，`--es office slides`测试PPTXjs；不带office测试平台能力。
4. `adb logcat -d -s ReaderProbe:I AndroidRuntime:E chromium:E`取技术结果。内存host和WebView renderer必须分开解释；退出后force-stop/uninstall探针。

来源/版本/许可见vendor-manifest.json、upstream-VENDORED.md和assets/licences.md；Gander MIT声明见upstream-LICENSE。候选库仅在本地HTTPS域的WebResourceResponse中加载，网络请求阻断，不做产品WebHost的安全或性能验收。

FixtureProvider仅白名单utf8.txt、gb.txt、large.txt、pipe.txt，均只读合成正文；产品阅读器真机测试content URI使用它。探针不是产品对外Provider。
