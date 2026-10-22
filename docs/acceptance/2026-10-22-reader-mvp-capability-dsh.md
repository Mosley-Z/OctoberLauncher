# RD015 MVP 格式能力表（2026-10-22）

范围：RD015 的格式能力盘点。能力值取自源码只读核对——[ReaderRegistry.kt](../../feature/reader/src/main/java/com/shuiyu/octoberrain/feature/reader/ReaderRegistry.kt#L32-L56) 的格式分派、各 Engine 的 `ReaderCapabilities`、[ReaderScreen.kt](../../feature/reader/src/main/java/com/shuiyu/octoberrain/feature/reader/ReaderScreen.kt#L337-L632) 的提示文案；设备侧行为引用已有验收记录。本次未运行 Gradle 与 adb，未修改源码。版本停在 1.0.28，Office 未集成。

## 能力矩阵

`capabilities` 列取 `ReaderCapabilities` 的 search、reflow、pageJump、zoom、bookmark 五项。全部引擎的 selectText 保持默认 false，矩阵不再单列。

| 格式 | 引擎 | capabilities | 入口 | 支持边界 |
| --- | --- | --- | --- | --- |
| 图片 | `ImageEngine` | 无 | 文件浏览图片项交 `PhotoViewer`；外部 VIEW/SEND 或魔数判为 IMAGE 时进阅读页 | 采样解码、固定视口居中、单帧、位图上限 12MiB；识别 PNG/JPEG/GIF/WEBP 魔数；无缩放、无动图、无 EXIF 方向 |
| PDF | `PdfEngine`，隔离进程与 SharedMemory | pageJump zoom bookmark | 文件浏览与外部入口，`%PDF-` 头判定；SDK<27 抛 UNSUPPORTED | API 27 起可用；缩放 1–4 倍、平移、裁边 5%、整页适配、页与视口书签、宿主位图 LRU 12MiB；加密 PDF 报 PASSWORD，损坏报失败；无搜索、选字、批注 |
| TXT | `TextEngine` | search reflow bookmark | 扩展名命中 TEXT_EXTENSIONS 或 mime `text/*`，编码在阅读页切换 | 64KiB 窗口、字节锚点、稀疏索引、UTF-8/UTF-16/GB18030 切换、分块搜索、书签；无跳页、无缩放 |
| Markdown | `MarkdownEngine`，OfflineWebHost 渲染；回落 `TextEngine` | search reflow bookmark | 扩展名 md/markdown；无 charset 且定位器非 Text 时进入排版渲染 | 输入不超过 1MiB 且为 UTF-8；单行 8192 字符、2 万行、AST 5 万节点；相对图片限本地目录或已授权 URI；宿主不可用或超限时改纯文本 |
| EPUB | `EpubEngine` | search reflow bookmark | 扩展名 epub 或 ZIP 容器内部标记 | 仅 UTF-8、无加密资源的重排版 EPUB；spine 上限 5000、单章上限 2MiB 与 5 万标记、CSS 上限 16 份；固定版式与 encryption.xml 拒绝并给出专用文案 |
| ZIP | `ArchiveEngine` | 无 | 扩展名 zip 或容器判定为普通 ZIP | 只列目录，每页 100 项，不整包解压；文件名编码 UTF-8/GB18030 可切换；ZIP64、多卷、加密、其他压缩算法不支持；嵌套 ZIP 不展开 |
| DOCX | 无，仅容器识别 | — | 文件浏览与外部入口均进阅读页，`ReaderRegistry` 抛 UNSUPPORTED | 提示“暂不支持此文档类型，可使用其他应用打开”，无渲染、无附加操作 |
| SHEET | 无，仅容器识别 | — | 同上 | 同上 |
| SLIDES | 无，仅容器识别 | — | 同上 | 同上 |

Office 三种格式由 [ZipContainerProbe.kt](../../feature/reader/src/main/java/com/shuiyu/octoberrain/feature/reader/source/ZipContainerProbe.kt#L66-L77) 依据中央目录条目名区分：DOCX 看 `word/document.xml` 或 `[Content_Types].xml` 加 `word/` 目录，SHEET 看 `xl/workbook.xml`，SLIDES 看 `ppt/presentation.xml`；EPUB 由 mimetype 内容或 `META-INF/container.xml` 认定，未命中则归为普通 ZIP。预检失败或读取异常时回退到扩展名判定。Office 三种格式只到容器识别一层。

## 入口与提示现状

两个入口共用 `ReaderGateway.open`：[ReaderModule.kt](../../feature/reader/src/main/java/com/shuiyu/octoberrain/feature/reader/ReaderModule.kt#L17-L47) 的网关恒返回 true，打开动作不预判格式。文件浏览由 [BrowseScreen.kt](../../feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/ui/BrowseScreen.kt#L149-L159) 的 `onOpenItem` 分流：图片项交 `PhotoViewer`，其余全部交阅读器；外部 VIEW/SEND 经 `DocumentEntryActivity` 进入同一阅读页。格式判定集中在 `ReaderRegistry`，Office 三种格式在 `when` 末尾抛 `ReaderSourceException(UNSUPPORTED)`，由 `ReaderScreen.unsupportedMessage()` 显示“暂不支持此文档类型，可使用其他应用打开”；EPUB 走专用一句，说明仅支持无加密资源的重排版版本。工具栏项由 `options()` 按 capability 与格式硬编码共同决定。

## 未覆盖项

- EPUB 固定版式、字体混淆资源与 encryption.xml 加密包按设计拒绝。
- PDF 搜索、选字、批注缺失；API 26 及以下关闭 PDF 引擎；加密 PDF 在 API 30 无解密路径。
- ZIP64、多卷、密码与非 deflate/stored 压缩算法未支持；嵌套 ZIP 需解出后单独打开。
- 图片无缩放、平移与动图播放，EXIF 方向未处理。
- 全格式选字与复制缺失：`ReaderCapabilities.selectText` 无引擎置 true。
- Markdown 跨本地文档相对链接未接线，排版渲染依赖 WebView 可用。
- Office 三种格式停在容器识别，无文档模型、无布局、无渲染。

## 待集成点

- Office 渲染引擎接入时替换 `ReaderRegistry` 的 else 分支，按 `ReaderFormat` 已有枚举补 `ReaderCapabilities` 与工具栏项。
- 入口预判：在网关或浏览层按检测结果决定是否进入阅读页，避免先渲染一屏 chrome 再显示提示。
- 图片补齐缩放后，`ImageEngine` 需实现 zoom，`options()` 的工具栏项来源改由 capability 驱动。

## 接入建议

以下差异交 Lead 决策，本次未改源码。

不支持提示当前落在阅读页状态栏，入口已先完成跳转与 chrome 渲染；Office 三种格式在入口处拦截，或在网关返回前给出提示，交互成本更低。UNKNOWN 与 Office 共用同一句提示，建议对无法识别的类型改为“无法识别文件类型”，与“不支持此文档类型”区分。图片在阅读器内的能力为无缩放无动图，动图只取首帧，文件浏览与阅读页两处均未说明该限制。Markdown 的“纯文本阅读（从头）”选项会让图片与表格消失，选项文案未提示这层损失。`options()` 中 ZIP 的文件名编码项与 Markdown 双模式项由格式判断产生，与 capability 驱动的其余项混在一起，后续接入新格式时易于遗漏，建议统一到能力与格式元数据。

## 参考

- [2026-10-22-reader-native-first.md](2026-10-22-reader-native-first.md)：文本阅读首轮
- [2026-10-22-reader-pdf.md](2026-10-22-reader-pdf.md)：PDF 隔离渲染
- [2026-10-22-reader-epub.md](2026-10-22-reader-epub.md)：EPUB 重排版
- [2026-10-22-reader-web-markdown.md](2026-10-22-reader-web-markdown.md)：离线宿主与 Markdown
- [2026-10-22-reader-zip.md](2026-10-22-reader-zip.md)：ZIP 归档
- [2026-10-22-reader-sources-002.md](2026-10-22-reader-sources-002.md)：类型识别与外部授权
- [2026-10-22-reader-shell-state-text-dsh.md](2026-10-22-reader-shell-state-text-dsh.md)：重排、书签与文本索引
