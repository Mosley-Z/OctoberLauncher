# TASK-UX-113 本记原位连续展开

- 状态：READY_FOR_REVIEW
- 执行者/集成者：note_visual_fix / Codex
- 文件范围：NoteOverlayView、widgets NOTE bridge/kernel、WidgetNoteOverlay；Home NOTE接线协调后由集成者修改。
- 依赖：UX110；共享契约本文件由集成者明确授权，Gradle/格式化/ADB仅集成者运行。
- 入口：现有本记行内展开入口，不另建窗口或新抬头。
- 控件位置：原抬头、已见正文与操作保留；隐藏正文向下延伸，底部固定收起。
- 显示归属：Home临时覆盖层只承担末排应用名称下缘内的增量阅读面积。
- 字段与消费者：内核已准备NOTE缓存与原生绘制排版→widget-api临时呈现协议→Home；不得落盘正文。
- 迁移：无配置/数据迁移；关闭释放临时排版。
- 验收触发：合成长划线/长想法/混合/短文、换条、滚动收起、返回、旋转、应用遮挡与上部像素连续性，由集成者最终release验证。

## 执行与交接

- 已改文件/接口决定：WidgetNotePresentation携带内核首行x/基线/行高/划线与想法完整行及tagBlock；新增同包HostNoteLayout调用实际CardLayout，不复制换行算法。Kernel通过无位图Canvas捕获真实正文基线并按身份/尺寸缓存。Home保留原抬头，仅从原正文顶沿覆盖向下增长，SERIF正文沿原坐标绘制，元数据/动作移到底部。没有HomeView接线改动。
- 实际命令及结果/证据：已读PROJECT/STATUS/MEMORY/UX110/35/37与设计README；git status保留既有脏工作区。只读javap证据 `_analysis/verify/note-renderer-javap.txt`。
- 未覆盖/阻塞/解除条件：尚无构建/设备结果；必须以本次真实上部像素与触点验收收口。
- 后台进程/设备占用/恢复情况：本Agent无Gradle/格式化/ADB占用。
- 下一步/接收方：交Codex统一格式/门禁与合成长划线、纯想法、混合验证；尤其比较首行x/y/字体和原抬头像素、检查长文收起与元数据位置。新增Java同包适配需确认release打包。
- 提交/合并/发布：未执行。

## 集成者最终交接

- 完整check/debug/release通过；双机本记/沉浸及入口、图片迁移实测范围见[统一验收](../docs/acceptance/2026-10-23-reading-desktop-visual-alignment.md)。
- 本批后台进程/设备占用/恢复情况：Gradle已结束、两机原测试字段/缓存恢复，最终release摘要均核对一致；无设备占用。
- 未覆盖/阻塞/解除条件：纯想法/混合与短文实体矩阵、长标题/空态全矩阵未穷举，微信读书真实LAN边界保留；不冒充全覆盖。
- 下一步/接收方：用户实际体验反馈与局部矩阵；无需重跑当前有效门禁。未提交/合并/发布。
