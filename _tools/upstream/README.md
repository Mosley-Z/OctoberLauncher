# 上游对照材料

本目录存放上游 APK 与逆向对照材料，仅用于行为、界面与交互形态的比对，不参与构建、不进入安装包、不作为运行依赖。

## tomo-launcher-0.5.0.apk

来源：上游 tomo-launcher 0.5.0，包名 `com.astraabove.tomo`，2.4 MB。

用途：对照桌面布局、组件形态、设置分组、托盘与阅读页的交互细节；逆向产物与对照结论记录在 `_analysis/` 与 `docs/07-upstream-code-map.md`、`docs/13-inkOS对照评估.md`。文件不进入 Gradle 构建，也不随应用分发。

上游 APK 的解析工具为 `_tools/jadx-headless-mcp-0.7.1-all.jar` 与 Android SDK 自带工具，分析过程不修改 APK 本身。若需重新核对某个界面，使用 `jadx` 导出对应类或使用 `aapt2 dump badging` 读取包信息，输出写入 `_analysis/` 对应目录。

本工程代码为自有实现，未复制上游源码；上游材料的使用边界见 `docs/04-identity-removal.md` 与根 `README.md` 的许可章节。
