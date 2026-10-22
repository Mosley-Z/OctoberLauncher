# ZIP首轮验收（2026-10-22）

RD008：本轮S4闭环通过，P4完整交互与压力由017验收。源码：archive、ArchiveEntryHandle、SourceCache、ReaderRegistry/Screen。ZIP先检查EOCD中央目录预算（最多4MiB/2万条），再建立虚拟索引；不整包解压。ZIP64、多卷、加密和不支持压缩算法暂不支持；嵌套ZIP不自动递归打开。

- 中文名称、目录优先排序、UTF8/GB18030文件名选择；路径规范化不进行URL解码，拒绝根逃逸/绝对路径/盘符/NUL和规范化重名。
- ArchiveEntry共享来源契约带charset，旧记录默认UTF8。条目流按真实输出字节、声明大小和EOF CRC校验，部分预览关闭不强行读完整条目。
- TXT首窗按需解压64KiB，回退使用缓存前缀；PDF选中后预算落盘；图片只解码采样静态首帧并适配固定视口，EXIF、缩放与动画不在本卡范围。
- 统一128MiB临时缓存预约，未知长度边读边限；进程重启后仅清理自身source-*.cache遗留文件。关闭删除自己临时文件，不删除用户原件。

全量check/debug/release通过，1564 tasks退出0。新增ZIP/按需通道/缓存共6个JVM用例通过（reader当前25个）。S4私有provider合成sample.zip根目录3项，进入中文目录后TXT、PNG、3页PDF均可打开并返回原目录；TXT阶段仅65536字节条目缓存加5001字节ZIP本体，HOME后cache total0。截图核对图片比例正常；runtime无AndroidRuntime异常。证据 `_analysis/verify/zip-008/s4-zip-flow.json` 与合成图、runtime；样本由generate_zip_fixtures.py重建。

限制：URI ZIP本体为了ZipFile随机访问可能整体复制压缩包，受128MiB总预算；不宣称所有条目零落盘。GB18030选择路径已接入，旧中文编码真实包矩阵和ZIP64/密码支持留后续；坏归档/路径/CRC/目录预算由JVM验证，不冒充设备矩阵。未升版本、提交或发布。
