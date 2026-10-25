# 当前工作状态

[REL001 V1.0.0本地准备](../task/TASK-REL-001-v1-stable-release.md)完成：版本1.0.0/52、长期正式签名与dist/1.0.0五件套已生成；完整门禁各1027项0失败/错误1既有跳过，APK2211143字节、SHA2568aaf0ddfbb9c33723cadc6bd59288ff188638791054333ba020286c9212940cc。默认GitHub清单/国内镜像接口就绪。用户最新明确旧历史自行处理，本轮仅本地准备，未提交推送/建tag/发布Release，未操作设备或改变远端可见性。[验收及发布前条件](../docs/acceptance/2026-10-26-rel001-v1-release-preparation.md)。

[UX152 P4通知返回跳动](../task/TASK-UX-152-p4-statusbar-transition.md)已交付：隐藏栏路径固定全屏View布局，避免79px inset驱动重排。最终release横竖过渡采样实际View始终完整，各3次开关闭环通过；服务未启用回退通过。check/debug/release各1018项0失败/错误1既有跳过。P4已装1.0.31/51、2205403字节、摘要6185500aae8afa494dfed7afd72aa922c3cae5490dd08e866e66c17e9f67fefa匹配；原配置/方向/选书选记与服务状态恢复，资源释放。S4/Tango未操作。[验收与逐帧边界](../docs/acceptance/2026-10-26-ux152-p4-statusbar-transition.md)。

[UX151 P4通知下滑](../task/TASK-UX-151-p4-notification-gesture.md)已交付：现有无障碍服务连接后隐藏真实栏、恢复自绘指示，下滑展开系统面板；未连接/失败回退真实栏。最终release横竖各3次闭环通过；check/debug/release各1018项0失败/错误1既有跳过。P4已装1.0.31/51、2205411字节，摘要55d1081249c2ff7ca1a8573e69f45488b1a24299a5471843a6e803c9697f6c21匹配。原配置/方向/选书选记恢复、新缓存保留、服务保留开启；P4与构建释放，S4未操作。[验收与边界](../docs/acceptance/2026-10-26-ux151-p4-notification-gesture.md)。CLI的UiAutomation会抑制服务，无障碍验收必须先释放工具进程。

[UX150 P4隐藏栏诊断](../task/TASK-UX-150-p4-hidden-status-bar.md)完成：30组有效独立Activity实验，28组隐藏栏未展开面板，2组可见栏正对照成功；现代/旧式及fits切换均未解决。正式应用未改，79px回退保留；诊断包卸载，原APK摘要一致、P4恢复横屏并释放，S4未操作。[验收与下一步候选](../docs/acceptance/2026-10-25-ux150-p4-statusbar-diagnosis.md)。无障碍路线未接入，真实手指与厂商根因未验证。

当前[UX149](../task/TASK-UX-149-immersive-device-pages-plan.md)已完成B版七项及最后左列20px调整。最终check/debug/release通过，各1014项0失败/错误1既有跳过；双机release1.0.31/51、2204679字节，摘要匹配并实见渲染。P4真实系统栏横竖下拉通过；S4多页独立选择/关闭回填通过。原配置、选书与选记恢复，新缓存保留；无活动构建/设备占用，不提交、升版或新建休眠。[最终验收](../docs/acceptance/2026-10-25-ux149-implementation.md)、[交接](sessions/2026-10-25-ux149-implementation.md)。真实盖板手指触达仍为物理验收边界。

本轮[UX148](../task/TASK-UX-148-safe-area-note-expansion.md)已交付：P4物理15px安全区、横屏12dp左留白、本记整卡展开/短条收起、元信息与按钮一致、局部换条保留数据更新时间。最终check/debug/release通过，各1004项0失败/错误1既有跳过。P4已安装release1.0.31/51、2193763字节、SHA256 fc5f1ac0b3c917995e9abbfe6667122006fc516fe13f169e9e67b3ba42dd6a79；原90°普通横屏本记已恢复，全局配置/选记一致，kernel偏好仅同步状态变化保留。Widget+内核+Overlay合成探针横竖PASS；S4离线未补装，盖板手指与系统下拉需用户实触确认。[验收](../docs/acceptance/2026-10-25-ux148-safe-area-note-expansion.md)。无活动构建/设备占用，不升版、提交或新建休眠任务。

本轮[UX147](../task/TASK-UX-147-settings-cleanup-landscape-assessment.md)已交付已选清理：A1–A9/B1–B3及使用情况访问/最近应用、悬浮窗/主动通知/精确闹钟入口删除；现有外观数据与完整备份恢复保留。check/debug/release通过（各1000项0失败/错误1既有跳过），最终release2191827字节、SHA256 4f2c49eabae41a2aa79f3e2fa28b6f96819e70e8bd07a2f53096cff9e2a40c95，P4安装摘要匹配并验证三项权限页；S4当前离线未装。P4当前普通横屏/本记为用户状态，本轮未调整。横屏安全区/留白/本记性能与展开只读评估，等用户确认；[完整验收与方案](../docs/acceptance/2026-10-25-ux147-cleanup-landscape-assessment.md)。无活动构建/设备占用，不自动开休眠或实施第二部分。

本轮[UX146](../task/TASK-UX-146-settings-consolidation.md)已交付：统一锁屏密码、关于归检查更新、权限备份扁平与组件库直达，其他说明/重复项/权限仅列待选。完整 check/debug/release 通过（各1000项、0失败/错误、1既有跳过）；两机已安装最终 release 1.0.31/51、2217971字节、SHA256 364f855e0cc4e9bb6dce69e94c77af07e36945c0b40f4faf84bd7b6f646824ed。全局配置恢复差异0；密码交互/最终release边界与待选编号见[验收](../docs/acceptance/2026-10-25-ux146-settings-consolidation.md)。下一步等用户选择，无活动构建/设备占用，不自动清理权限或启动休眠。

本轮[UX145](../task/TASK-UX-145-desktop-settings-file-actions.md)已交付：普通/沉浸最近10本循环、设置首页四卡+系统单列、系统卸载确认与文件详情/普通文件删除。check和debug/release通过，每变体1000项0失败0错误1既有跳过；两机系统卸载/详情删除/轮播验证，最终release1.0.31/51、2223507字节、SHA256 0d6f278118fdd0893c8c362bbd2b4ddd90a972de1f532cb94be9389dcb7774f6已安装匹配。按字段恢复原桌面、选书与文件页偏好，测试包/目录清理；无活动构建和设备占用。[验收及测试边界](../docs/acceptance/2026-10-25-ux145-desktop-settings-file-actions.md)。前轮遗留休眠任务已停用，本轮不新建休眠调度。

最新授权已交付：UX143/144由DSH Tauri完成，主代理审查直接修正并统一验证；自动首页竖2行3列/横4行2列，显式设置保留。最终check/debug/release通过，各995项0失败0错误1既有跳过。两机release1.0.31/51、2221639字节、SHA256 d45aa1abece1628e8a158a72fec2787b8b50b3a8238c22b1bb3a3acedce413f8已安装且摘要匹配；原测试字段/选书/图片恢复，合成数据删除。S4最终布局探针横屏复跑exit137，采用此前未变布局源码完整结果和实体横屏证据，其他最终探针通过。heartbeat已暂停，DSH与构建无活动任务。旧卡非S4遗留不扩做；双机已确认Asleep/OFF；电脑休眠已交Windows一次性计划任务调度（最终回执见hibernate-dispatch.json），尚未观测实际休眠。详见[集成验收](../docs/acceptance/2026-10-25-ux143-144-integration.md)与[本轮接力](sessions/2026-10-25-ux143-144-monitor.md)。以下旧记录保持各自边界。

当前批次UX139整批仍IN_PROGRESS；本轮UX142代码审查、微读刷新入口/绑定与E小时规则已完成。修复方向fallback/关闭整页与设置回读，横竖刷新热区、本记绑定、待办刷新清理；E固定零时至九时/一零至二四，独立日期.0575，并纠正前次字号误改共用路径（恢复.041）。最终check及debug/release通过，各976例0失败0错误1既有跳过；P4周/月实际点击缓存推进、独立8形态/横竖12格探针与E20图通过，最终release2224155字节、1.0.31/51摘要匹配，测试字段曾恢复原E；用户中途调整微信读书竖屏后交还设备，最终临时E验收完成并切回用户新的微信读书本周竖屏，新读缓存保留。设备探针/构建/P4释放。S4不操作，143/144仍TODO；备份导入端到端、美化逐屏、设置逐项步进和本记全库云端绑定未完整实机重测。[最新审查验收](../docs/acceptance/2026-10-25-ux142-review-refresh.md)、[接力](sessions/2026-10-25-ux142-review-refresh.md)。保留master/f025b18及原差异，无升版、提交、推送或发布。以下旧记录保留各自边界。

[UX142](../task/TASK-UX-142-grid-and-widget-reflow.md)已完成：其他页新增横竖方向容量（`landscapeColumns/Rows`、`portraitColumns/Rows`，0 沿用原行列），关闭组件后首页与其他页共用整页容量、横屏不再保留空分栏，页数随条目重算并把当前页夹回有效范围。统一 check+debug/release 通过，1946 例 0 失败 0 错误 2 跳过（每变体 973 例），debug 7213098 字节、release 2223935 字节，1.0.31/51。P4 实测竖屏 3 列 4 行、横屏 4 列 3 行各 12 容量，关闭组件后横屏整页排布、第二页关闭后回到第一页；配置已恢复原状。[验收](../docs/acceptance/2026-10-25-ux142-direction-grid-and-widget-reflow.md)、[交接](sessions/2026-10-25-ux142-direction-grid-and-widget-reflow.md)。S4、备份导入端到端与美化逐屏点验未覆盖；143–144 仍 TODO，不自动执行；保留 master/f025b18 及既有差异，不升版/提交/推送/发布。

当前批次：[UX139](../task/TASK-UX-139-widget-upgrade-integration.md)整批IN_PROGRESS；[UX141](../task/TASK-UX-141-todo-week-calendar.md)已完成本轮代码审查整改：箭头放大并扩展热区、日移IO串行保存、重复可见回调不复位窗口、时间限列、完成白勾、分钟刷新及视图状态回主线程；E日期字号约增22%。最终check+debug/release通过，各968例0失败0错误1既有跳过；P4实际左日移/周移及E显示、独立三尺寸12箭头方向/生命周期探针通过。[最新审查验收](../docs/acceptance/2026-10-25-ux141-review.md)、[接力](sessions/2026-10-25-ux141-review.md)。真实待办未改，测试字段已合并恢复，最终release2221839字节、1.0.31/51，安装摘要匹配并回原todo_week桌面；探针/构建/P4释放。S4未操作，真实午夜、触摸抖动/连点压力及全量CRUD未重测，宿主2px取消点击门限保持。143–144仍TODO，不自动执行；保留master/f025b18及既有差异，不升版/提交/推送/发布。以下旧验收保留各自版本边界。

[UX138文件入口恢复](../task/TASK-UX-138-files-entry-visibility.md)及[UX137 APK打开选择](../task/TASK-UX-137-apk-open-choice.md)已完成集成和P4验证；无后台构建或设备占用。

- 文件默认显示，配置v16一次恢复；设置→桌面偏好→桌面与布局→管理隐藏应用可再次隐藏。关状态冷启保留，最终恢复开。系统资源管理器com.android.rk保留；P4原首页顺序不变，文件位于第二页。
- 当前1.0.31/51、2210547字节，SHA256 d8b8dab4067812adce46d34cc0198ae7223edf36cd61b2597f01e5c38f29164f。统一check及两种构建通过，各948例0失败0错误1既有跳过；P4安装摘要相同。
- 当前包P4实测APK菜单、取消、结构、系统安装提示后取消，ZIP直接查看、JAR未知分支。未实际安装APK或更改授权；三份自建样本和空目录已删除，文件恢复原Pictures列表，桌面停第二页可见文件。NOTE/2×4/90°/壁纸及系统入口保持。
- S4离线未安装当前包；其UX136横竖宫格/EPUB结果仍属于旧包，不冒充当前验收。未扩大RAR等解码能力。
- [当前验收](../docs/acceptance/2026-10-24-ux138-files-entry-visibility.md)、[本轮交接](sessions/2026-10-24-ux138-files-entry-visibility.md)。保留master/f025b18及既有差异；未升应用版本、提交、推送或发布。下一步等待反馈。
