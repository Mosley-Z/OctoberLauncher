# 2026-10-23 RD013 PPTX子任务交付记录

- 范围/验收者：PPTX引擎、单页子包、资源与解析测试 / slides子智能体。
- 源码基线+差异范围：master/0a1534c后专属office/slides、test/office/slides及assets/reader/web/slides；共享入口由Codex根集成。
- APK/设备：本子任务未构建APK、未运行Gradle、未占用S4/P4，不记设备通过。
- 关联：[任务](../../task/TASK-RD-013-slides.md)、[总方案](../design/34-files-reader-upgrade-plan.md)。

| 用例/命令 | 预期 | 实际 | 证据 | 结论 |
| --- | --- | --- | --- | --- |
| 固定资源摘要核对 | 9项原样 | 9项SHA-256一致 | assets/reader/web/slides/vendor-manifest.json | 源码通过 |
| node --check slides-host.js | JS语法有效 | 退出0 | host源码 | 语法通过 |
| generate.py | 中文/图像/表格/不支持图形及200页样本 | mixed.pptx 31546B；deck-200.pptx 291520B | _analysis/verify/slides-013/ | 样本生成通过 |
| SlideIndexTest | 页序、200页子包只保留1页、路径及实体/节点预算 | 5例交根运行 | test/office/slides/ | 待根门禁 |
| 双机打开/跳页/搜索/恢复 | 当前页原版式与提纲可用 | 未操作设备 | 由根补最终证据 | 待验 |

实现压缩8MiB/中央目录展开32MiB预检、XML每part2MiB/50000标记、全文2M字符、最多1000页、图像8M单图/16M总像素。每次只向WebView传当前页及其关系闭包，关闭替换资源时清空ByteArray。图表、嵌入对象、视频/音频、复杂图形不完整支持，界面明确提示。表格保留；外部关系、动画和超链接移除；宏二进制无执行入口。

许可：完整上游许可副本在专属assets/slides/LICENSES.txt；9项来源摘要见vendor-manifest，根统一注册许可入口。原PPTXjs保持冻结版本1.21.1及上游已有补丁，未直接修改vendor脚本。

原版式交互补充：host通过inert template清理第三方渲染HTML后挂载；检索query按纯文本定位并逐span包mark；等待图像与字体后按fraction恢复滚动，用户touch滚动180ms节流上报locator。底部提示由host固定呈现，不依赖office模式忽略的body。上述源码仅通过JS语法检查，交互仍待设备实测。


RD014增量（尚未设备验证）：SlideEngine实现SearchProvider，旧findNext退役；SlideSearch.scan逐页ensureActive+collectMatches，不改阅读位置，SearchCollector只保留请求64条并计完整总数。showHit按canonical text算页内matchOrdinal，host按ordinal选择相同query第n个命中，避免OOXML换行与DOM偏移不一致。host cancel命令立即返回；3项JVM测试覆盖未加载第200页、64条结果窗口及同页重复词，交根门禁。`node -e "eval(require('fs').readFileSync('_analysis/verify/slides-013/host-ordinal-test.js','utf8'))"`退出0，6项视觉ordinal定位测试通过；直接执行_analysis内.js受既有_analysis/package.json格式错误影响，未改该无关文件。node --check slides-host.js退出0。
