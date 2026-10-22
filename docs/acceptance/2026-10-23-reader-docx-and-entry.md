# DOCX双模式与基础入口增量验收（2026-10-23）

负责人/集成者 Codex；用户在本聊天批准按建议接手剩余开发。源码为master/cfeae53 + 保留的工作区差异，版本仍1.0.28，未提交/推送/升版/发布。本报告只覆盖本批，不关闭RD017或整份34方案。

## 实现与契约

- ReaderRegistry新增DOCX引擎。正文模式提取段落、表格、内部图片，使用既有离线分页宿主；原版式采用固定docx-preview0.4.0/JSZip3.10.1，按页显示、按宽适配、页内滚动，支持跳页及搜索。长页不再整页压缩到不可读尺寸。
- 新增ReaderLocator.Docx(preview/textOffset/fraction/page)，保存模式与文本锚点；重启、跨模式、页内搜索/滚动均通过已有会话代次/已显示帧规则落盘。数据库表结构和旧定位器序列化名称不变。
- DOCX压缩输入8MiB、容器总展开32MiB、正文及关系XML各2MiB、标记数量和图片像素预算先检查。正文不会执行域/嵌入对象；预览禁用altChunk、修订与批注执行路径。脚本固定白名单，仅预览文档允许同源资源读取，未命中仍阻断；其他Web文档保持connect-src 'none'。
- 两份脚本打包前与既有vendor-manifest摘要核对，产品内记录见 `reader/licenses/docx-assets.json`；完整Apache/MIT许可与版权保留在 `reader/licenses/docx.txt`，关于页已登记。未复制Gander业务Kotlin代码。
- 阅读设置增加content URI重新选择文档；HostFilePicker仅在系统真实返回persistable+read时持久保存读取授权。取消或无返回URI不导航。UNKNOWN和已识别不支持格式文案分开，Markdown纯文本提示注明排版损失。
- 本应用files_share URI与规范路径确认同源时共用身份；事务保留较新进度、迁移全部书签、删除旧别名记录。归档条目继承父来源别名并保留编码/路径/CRC。其他Provider不猜测映射，不按名称/相似内容合并副本。

## 工程门禁与最终产物

- 最终 `check :app:assembleDebug :app:assembleRelease --offline --console=plain` 退出0，1564 tasks，21s。日志 `_analysis/verify/reader-takeover-final-gate.log`。reader单测85例、0失败/错误，Lint无新增问题，ktlint/detekt通过。
- debug 6106043字节，SHA256 `a88386b42b27abe083df6845c5e02ec50b080444b033dfeac76eaa49218699fd`。
- release 1507783字节，SHA256 `c2d9efcd09348c51663a2068a7b78ac1031130d18bc79681fd9951927a6d55ab`。
- 新增有行为意义的单测：正文表格/文字/字段排除、内部/外部图片关系、实体拒绝、已知Provider根与越界拒绝、归档父别名继承；刷新规则另见[RD016第一批](2026-10-22-reader-resource-first.md)。JVM结果不替代WebView/SQLite设备结论。

## S4结果

S4 SA4B03CN2608081096X，API30/WebView83，480×800。合成合法DOCX含中文80段、表格、图片和显式分页，文件3283字节，摘要在 `_analysis/verify/docx-011/fixture.json`；没有用户正文。

| 触发 | 实际结果与证据 |
| --- | --- |
| 从阅读设置重选DOCX | 经过设备实际可用的十雨PickEntryActivity选择Download合成文件，正文显示1/17，中文/表格/图片可见；`_ui-out/docx-body-first/` |
| 原版式及长页 | 首轮2页，翻页可用；发现长页整屏压缩后已修为按宽显示与页内滚动；`_ui-out/docx-correct-preview-next/` |
| 搜索未显示的末段079 | 定位到预览第二页底部，末段可见；`_ui-out/docx-search-found/` |
| 强停后重开 | 保留原版式2/2与末段位置；`_ui-out/docx-restored-ready/` |
| release重开/切正文/低内存后翻页 | 模式与位置恢复，切正文后RUNNING_LOW仍能翻到16/17；`_ui-out/docx-release-ready.json`、`docx-release-trim-ready.json` |
| 迁移前URI添加书签，更新后打开 | 保留16/17与原书签；`_ui-out/rd015-before-alias.json`、`rd015-alias-bookmarks/` |
| 桌面文件页打开同一本地DOCX | 仍是16/17且看到同一书签；`_ui-out/rd015-file-canonical/`、`rd015-local-ready.json`、`rd015-local-bookmarks/` |
| 合成副本及移动后新路径 | 副本初始1/17；移动后的新路径在release仍1/17，原文件仍16/17；`rd015-copy-ready.json`、`rd015-moved-release-ready.json`、`rd015-final-uri-release.json` |

最终带归档别名兼容的release另做打开烟测；上表较早细项来自本批对应APK，业务未变部分复用有效证据。最后APK与源码摘要以上节为准，不把早期压力APK冒充最终包。

DOCX迭代期间观测prepareMs约1.6–4.7s，属于不同打开/模式样本，未计算首屏P95。一次配对采样宿主108839KiB、所属WebView renderer41357KiB，合计150196KiB；归属由activity processes中绑定关系确认，证据 `_analysis/verify/docx-011/renderer-association.txt`、`host-paired-memory.txt`、`renderer-memory.txt`。这是身份迁移前的小样本观测，不是最终全格式持续内存保证。一次模式销毁切换时有Chromium renderer终止日志，新宿主恢复并显示；未据此宣称renderer从未退出，完整异常/压力矩阵仍待。

## 边界、恢复与后续

- 正文为简化语义阅读，不保留完整Word样式、自动编号、页眉页脚与复杂域；原版式分页依赖文档标记/库实现，长内容允许页内滚动，不能保证等同Word自动分页。字体、浮动图形、复杂图表、非UTF-8 XML等复杂包仍需真实样本矩阵。
- DOCX首轮为READY_FOR_REVIEW，不是全格式验收。超限/坏包/renderer故障完整矩阵、复杂DOCX、P4与Office压力由000/016/017继续收口；搜索结果高亮/统一计数等留014。
- S4未安装DocumentsUI；实际选择器为十雨自己的文件页。因此真实SAF持久授权、系统撤销后重选本次未验，不能将自有Provider成功算作SAF通过。任意第三方URI的内容摘要关联仍未实现，RD004/015不因本次已知别名合并关闭。
- 文件页入口与同源进度/书签验证已补；所有格式的列表位置/异常返回等完整矩阵仍待017。
- 文件页最后目录通过界面恢复到原先 `/storage/emulated/0`，网格/其他设置未改，全刷恢复6。S4最终release与HOME；合成DOCX及移动副本保留在Download供后续Office矩阵，不含用户数据。没有导出配置全量或设备备份。
- 下一批按既定授权推进RD012表格/CSV、RD013 PPTX、RD014统一搜索，再补RD016全格式与RD017最终双机门禁。018–020仍后置。
