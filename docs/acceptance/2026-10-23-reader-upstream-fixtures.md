# 上游复杂 Office 回归样本来源

日期：2026-10-23。执行者：Codex URI 子任务；最终 APK 与设备验收由集成者及双设备执行者记录。本记录证明测试输入来源、预算与预期结构，不证明阅读器已通过这些样本。

样本来自官方项目公开回归目录，固定提交后通过 GitHub API/raw 下载，未 clone、未执行任何上游脚本或文档宏。二进制、原始目录清单、许可副本与完整摘要只存本地 `_analysis/verify/final-reader/upstream/`，不进入产品资源或 Git。机器可读证据为该目录 `manifest.json`，包含源 URL、固定 commit、SHA256、压缩/展开字节与结构指标。正文未写入本记录或日志。

## 固定来源及项目许可

- [docxjs c533f383069008d4a1e05c6171ef8777fd078385](https://github.com/VolodymyrBaydalka/docxjs/tree/c533f383069008d4a1e05c6171ef8777fd078385/tests/render-test)：保存该提交 LICENSE 副本（项目 Apache-2.0）。
- [LibreOffice/core 57b88b46ef65dc0cf3ffdaf27a9c2dabcb8fba6f](https://github.com/LibreOffice/core/tree/57b88b46ef65dc0cf3ffdaf27a9c2dabcb8fba6f)：保存 COPYING、COPYING.MPL、COPYING.LGPL 副本及其 SHA256，具体文件适用说明以原项目为准，不把测试素材加入产品。
- 已查看 PPTXjs 固定提交 1a9260b2062f89ba822aeb54da792236780af8e7；唯一 Sample_12.pptx 为 21,599,261 字节，超过本轮 8MiB 输入预算，未作为产品兼容验收输入，改选 LibreOffice Impress QA 样本。

## 来源与可观察预期

下列均小于 8MiB，ZIP 实际展开均小于 32MiB。XLS 为 OLE/BIFF，不以 ZIP 展开指标描述。

| 本地文件 | 上游路径 | 字节 / ZIP展开字节 | 结构检查得到的预期 |
| --- | --- | --- | --- |
| docx-table-spans.docx | docxjs tests/render-test/table-spans/document.docx | 5,023 / 25,206 | 1表、6行、26个cell，5个gridSpan与14个vMerge元素；核对原版式合并与正文降级 |
| docx-header-footer.docx | docxjs tests/render-test/header-footer/document.docx | 19,086 / 78,736 | 3个header与3个footer部件、1个正文section；核对原版式及正文模式差异 |
| xlsx-shared-formula.xlsx | LibreOffice sc/qa/unit/data/xlsx/shared-formula/basic.xlsx | 8,984 / 17,231 | 3sheet，首sheet A1:B19，其余为空；18个公式缓存值为10、20…180，不要求公式重算 |
| xls-shared-formula.xls | LibreOffice sc/qa/unit/data/xls/shared-formula/basic.xls | 13,824 / 不适用 | xlrd独立读取3sheet，首sheet 19×2、36数字单元格，其余为空；与XLSX对照缓存显示 |
| ods-merged.ods | LibreOffice sc/qa/unit/data/ods/merged.ods | 7,723 / 29,700 | 2table，4个合并矩形：6列×12行、3×11、5×9、3×13；45 covered-cell元素 |
| pptx-table-style.pptx | LibreOffice sd/qa/unit/data/pptx/predefined-table-style.pptx | 36,430 / 161,693 | 1页6×6表格、1master/11layouts；核对实际表格显示和提纲能力 |
| pptx-shape-image.pptx | LibreOffice sd/qa/unit/data/pptx/ShapePlusImage.pptx | 36,088 / 55,464 | 1页、1shape+1picture，嵌入图片521×265；核对图片与形状可见 |
| pptx-master-slides.pptx | LibreOffice sd/qa/unit/data/pptx/master-slides.pptx | 54,445 / 151,785 | 1页3shape，2masters/7layouts；核对关系闭包与母版引用 |

## 样本摘要

| 文件 | SHA256 |
| --- | --- |
| docx-table-spans.docx | 7cb203433e01f90128d50c1c557669dff7181ac005e1563b169cafa2d3a4cf3f |
| docx-header-footer.docx | 8dbb6833241ad7e6261312033d96aeeff1594c24fa65cc0c544fb1007a2ebab1 |
| xlsx-shared-formula.xlsx | 7ec5305479c3ef38868406985f97f9220054ac847892d36f40511f2ceca12105 |
| xls-shared-formula.xls | 6277d4c3ee511868d23d1171275bde6df5dfb2cfa39f9a0db67e39f7353402a4 |
| ods-merged.ods | 9ea4b0215ad4b4df5b4f295039ce5728e0b4b903ac6f58d9a9a748c1f568680d |
| pptx-table-style.pptx | f3711fb2c44295c7d93ae92e5a5458af14a49e049ace24cc6fc64a3e3c0018bc |
| pptx-shape-image.pptx | 5b4016ca717d6ca552e6bdf06f0b756f72c98c2ee3798a04d4de07baf9116d51 |
| pptx-master-slides.pptx | cbc14cf6683c7aa3f5c4b83d70ef405b35c7ae02746e5abcc8bcbedfa235e726 |

## 验证边界与交接

下载/结构解析脚本最终退出0；docx/xlsx/ods/pptx只读取ZIP/XML，XLS用本机已有xlrd读取缓存值，不执行公式。P4执行者已收到表格输入与合并/缓存预期；S4执行者已收到DOCX/PPTX输入与图片/表格/母版预期。设备实际结果归各自最终报告，不能由结构指标推断渲染正确，也不能以这8个上游回归样本宣称所有真实复杂文档兼容。
