# 2026-10-22 FIX 接管纠正与收尾交接

- 用户目标：检查 DSH 的 FIX 实现并直接纠正，接替其剩余验证/回填工作，反思需求对接并固化经验。本轮业务范围为这三 FIX；RD016 与原 Office 等后续计划仍按看板推进，不把本批等同整个计划完成。
- 真实工作树：D:/DSHStation/OctoberLauncher；master HEAD cfeae53 + 大量既有差异；没有 reset/clean/回滚、提交、推送、升版或发布。
- 任务：[FIX001](../../task/TASK-FIX-001-reader-review.md)、[FIX002](../../task/TASK-FIX-002-single-bookshelf.md)、[FIX003](../../task/TASK-FIX-003-special-icons.md)；当前代码与 S4 证据就绪，状态 READY_FOR_REVIEW，P4 未连接不能写 DONE。
- 主要纠正：同行书源下拉；最近阅读只归本地但不受目录/旧开关限制；返回刷新且切源不清缓存；搜索/添加书签/书签管理接入统一完成回调；原始字段存在性覆盖正常加载与导入；恢复独立历史迁移；主题可见列表清掉旧书架。
- 证据：[接管验收](../../docs/acceptance/2026-10-22-fix-takeover-codex.md)，包含最终 APK 摘要、门禁、单测和 S4 debug/release 路径；重点看 resize-search、resize-continuous、resize-exit 技术日志。DSH 的原书签 16/16 是其历史证据，本轮没有把它写成本轮重跑。
- 工具：scripts/android-ui.ps1 新增 tap -Label -HoldMs（0–5000ms），按当前截图坐标长按，不支持 selector 长按时明确报错，保持 UTF-8 BOM。先观察页面，选图成功会自动返回，不能盲目再 back 到系统 Launcher。
- 测试状态：空目录/旧开关、字体、尺寸均已恢复；来源 weread、行数 2；设置/文件默认图标；普通图标键、桌面顺序保留，期间阅读记录保留。本轮临时主题已删除。
- 敏感数据：设备偏好基线仅放操作系统私有临时目录；仓库只保留技术布尔/计数。不要把 private-location 指向的原始文件复制到 Git、任务或记忆。
- 当前设备/构建：本轮唯一操作者 Codex；S4 在线，最终覆盖安装本轮 release，回桌面；尺寸物理 480×800 无 override，font_scale=1.0。P4 未连接；构建完成后释放，无后台测试脚本。开工重新核实，不凭此推断设备仍在线。
- 未覆盖：P4 同包验证、实体旋转、真实 SAF 撤销重选、Office、RD016 资源压力和 RD017 最终整合。S4 窗口模拟不是 P4 或旋转通过。
- 下一步：P4 接入后先覆盖安装验收记录中的 release，核对来源选择/同行触点、旧入口兼容、本地/最近阅读打开、设置/文件换图与重启/恢复默认；完成后回填三卡。不要重复重写已修逻辑。RD016 可做独立资源审计，正式验收按依赖接收本批再推进。
- 沉淀：[36 需求对接与闭环交付约定](../../docs/design/36-demand-handoff-contract.md) 已接入 AGENTS、35 流程、DSH 手册、任务/交接模板与 MEMORY；check-project-docs 同时覆盖 RD/FIX 的状态、字段、索引和依赖。规则不能代替实际证据。

## 最后追加修正

DesktopEntries 同样移除 special:bookshelf，防旧 hidden/order 配置恢复可见入口；本地缓存加入目录匹配，手动刷新强制当前书源，权限不足不复用目录缓存。不重启实际换目录验证通过，测试空目录已 rmdir 删除。最终完整门禁 39s 通过，最终 release 已覆盖安装；分阶段验证与未重复项目在验收记录明确区分。WeRead 相关集成字段仅比较是否与基线相等，结果一致，没有输出密钥。

文档收尾校验：check-project-docs 为 documents=109、tasks=24、errors=0，仅两条登记历史删除 WARN；本轮相关 diff --check 通过。最终 release 已在 S4 实际回桌面（_ui-out/fix-codex/final-release/），未持有构建或设备脚本。

## 用户范围确认（2026-10-22）

用户已回复“本轮先完成 S4，P4 留待补验”。S4 本轮收尾有效，P4 延后，不再阻塞本轮交付或后续独立工作；设备结论仍仅限已记录的 S4 场景，P4 不记通过，最终同包双机门禁由 RD017 补齐。此前“优先等待 P4”的建议由本条取代，后续先按依赖进入 RD016。
