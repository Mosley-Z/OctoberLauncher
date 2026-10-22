# 离线宿主与 Markdown 首轮验收（2026-10-22）

范围：RD007、RD009，S4 Android30 / WebView83 / 2GB RAM。P4完整阅读交互留给RD017；没有把历史独立探针算作本轮产品验收。

## 实现

- 单活动WebView；退出销毁。AssetLoader固定HTTPS域、会话目录、CSP与文档HTML白名单；未命中资源直接403。禁止file/content访问、网络加载、下载、文件选择和新窗口。消息校验来源、主frame、会话、请求编号与长度，没有通用路径读写接口。
- commonmark0.21.0加GFM表格，jsoup清洗；原始HTML作为文字。依赖、许可证和兼容依据见同日reader-dependencies验收。
- 字符锚点分页、字号重排、搜索、书签与进度恢复；宽表格横向查看并将搜索命中单元格移入视口。相对图片仅限本地目录或实际获得读取授权的URI范围；不把单文件URI当作整目录授权。
- Markdown输入UTF8最多1MiB，单行8192字符、2万行、AST5万节点/深度128；清洗HTML最多2MiB。图片单件8MiB/合计32MiB，解码像素单件8M/合计16M。超限给出可操作失败，可切原生纯文本。
- 外部入口必须带READ授权；保留实际授予的prefix与ClipData。debug专用DUMP权限测试receiver可触发自身WebView崩溃，release清单不含该receiver。

## 当前验证

- 全量check/debug/release退出0（1564 tasks）；最后HTML预算及表格尾页修复后reader:check和双APK再次退出0（1065 tasks），Lint无问题。reader19个JVM用例通过；不能替代设备测试。
- S4飞行模式：合成中文、图片、长代码块、表格与原始HTML可读；3次翻页后4/41，字号20→22后4/47，字符锚点302保持。搜索唯一尾部标记到42/47、锚点5003，书签回跳和强停重开均恢复该锚点。见 `_analysis/verify/web-007/s4-markdown-flow.json`。
- 正确私有provider前缀授权可读取图片；入口与直接MainActivity缺少READ标志时均无provider打开记录。标题fragment跳转通过。见权限及故障JSON与对应runtime。
- 实际chrome://crash触发native renderer崩溃，onRenderProcessGone(crashed=true)回调、桌面PID保持，界面报告显示中断；返回并重新打开成功。不能用早先仅发送am crash的短观察代替此证据。
- R8 release合成30列表格可横移到末列；搜索R7C29显示对应单元格。截图只含合成文档，存于 `_analysis/verify/web-007/`。
- S4无线状态恢复为测试前值；HOME后独立Web renderer不再占用。内存是单次采样且HOME与阅读场景不同，不宣称稳定内存节省；尚未完成低内存持续压力和动画图片压力验收。

## 限制与后续

P4完整产品交互、不同WebView/API矩阵、持续压力由017验收。首屏debug测量约4秒，不声称达到2秒目标。跨本地文档链接尚未接线；GIF/WebP/APNG动画压力未完成。EPUB、ZIP、Office没有因本次公共宿主完成而自动成为已支持格式。未升级版本、提交、推送或发布。
