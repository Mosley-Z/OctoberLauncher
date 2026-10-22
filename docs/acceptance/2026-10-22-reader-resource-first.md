# 阅读器资源与基础恢复第一批（2026-10-22）

负责人 Codex。用户批准按“基础余项/RD016 → Office → 统一搜索 → 最终验收”推进；master/cfeae53 + 保留全部既有差异，未提交/升版/发布。

## 实现

- 阅读设置新增翻页全刷关闭/每6页/每12页，默认6，独立 SharedPreferences 持久化。仅已显示且定位改变的翻页累计，打开/搜索/字号重排不计；全局全刷完成版本统一重置阅读计数，经 RefreshScheduler 请求刷新。
- 可见宿主注册 ComponentCallbacks2，内存压力串行排到当前动作之后；PDF逐出可重建位图缓存，不 recycle 视图仍引用的当前帧；退出注销回调并沿用取消/关闭流程。
- content URI 阅读设置新增重新选择文档；HostFilePicker 仅在系统返回可持久化且可读时保留读取授权。UNKNOWN提示与已识别但不支持格式分开，Markdown纯文本提示说明图片与表格排版损失。

## 当前证据

- `:feature:reader:testDebugUnitTest`、reader/render 的 ktlintCheck/detekt 及 debug 构建通过；新增刷新计数2例。`check :app:assembleDebug :app:assembleRelease`退出0，1564 tasks，2m16s，日志 `_analysis/verify/rd016-full-gate.log`。这是接入DOCX前的基础批次，不代表后续Office最终门禁。
- S4 API30/WebView83；三个刷新选项实际可选，“每12页”强停重启后仍CHECKED，随后恢复6页。证据 `_ui-out/rd016-persisted-choice/layout.json`。
- `_analysis/verify/rd016-pressure.py` 通过项目Android CLI执行合成3页PDF的200次前后翻页、RUNNING_LOW后继续翻页、30次关闭/重开。每20次检查页码，201个action样本P95=28ms、最大182ms（输入处理到内容发布，不含CLI调用及物理屏幕刷新）；准备耗时日志本次没有有效样本，不作首屏结论。
- 压力APK保存在 `_analysis/verify/rd016/pressure-debug.apk`，6337495字节，SHA256 `7fcc8ae426eac094823183a0bd5730971ced60910fd6f2d5bee6ba05fd1e3796`。该APK早于授权重选和“打开中收到低内存后延迟释放”的最终增量，后者另验，不伪写同APK全覆盖。

| 时点 | 宿主PSS KiB | PDF进程PSS KiB |
| --- | ---: | ---: |
| 开始 | 71511 | 30172 |
| 200次翻页后 | 76661 | 30603 |
| 低内存后 | 72191 | 30609 |
| 30次开关后 | 70915 | 29999 |

临时来源目录始终4KiB（目录元数据），之后find未见文件。AndroidRuntime仅日志头，无异常。以上是一次合成样本趋势，不是全格式稳定性保证。

原脚本最后一次返回后露出了更早的PDF阅读页，因此pressure.json的afterExit仍有PDF服务，不能算退出释放。随后用项目CLI发送HOME，界面确认桌面，进程列表无PDF，activity services为空；证据 `after-home-services.txt` 与 `_ui-out/rd016-home/layout.json`。桌面加载后的宿主PSS121495KiB，内容与阅读阶段不同，不拿它证明退出后内存下降。

## 剩余与边界

- 当前刷新控制器用黑色覆盖层与invalidate重绘，无厂商EPD波形接口；物理刷新耗时与残影效果待实际观测。
- 待补文本/Markdown/EPUB及Office同矩阵、多轮首屏P95、最终APK复验、P4和真实断网。RD016保持IN_PROGRESS。
- 系统真实授权撤销重选、文件页列表返回、复制移动的记录区分继续由RD015/017收口；不能因存在重选按钮勾为设备通过。
- 设备合成PDF位于探针私有目录，推送时的/data/local/tmp文件已删除；全刷设置恢复6。无用户正文、配置全量或设备备份写入证据。
