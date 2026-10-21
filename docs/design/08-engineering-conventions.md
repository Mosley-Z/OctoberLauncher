# 工程规范

> 2026-10-22 整理说明：本文保留早期设计/交接语境。当前协作、Git、构建和验收流程以 [35开发流程](35-development-workflow.md) 为准；版本、模块数、测试数和设备状态须从源码/当轮证据复查。

规范的作用是让后续每一次扩展都有固定位置可放。本文按 0.9.7 的实现状态记录，原设计中的四层目录、Hilt 模块、调度器封装等约定在实现阶段调整，差异写在对应小节。

## 一、模块与包

模块划分见 [03-target-architecture.md](03-target-architecture.md) 第二节。`feature` 模块内部按实际使用平铺，不强制四层目录：

```
feature/files/src/main/java/<namespace>/feature/files/
  *.kt              入口与模块装配（FilesModule）
  data/            文件、媒体索引、编辑会话与导出
  ui/              列表、浏览、查看器与编辑页
  ui/album/        相册分页与视图
  ui/edit/         编辑状态、裁剪、滤镜、保存
  ui/viewer/       图片查看器与输入处理
```

`feature/home`、`feature/settings`、`feature/widgets`、`feature/tray` 的 Kotlin 文件平铺在模块包下，不设 `domain/` 与 `di/`；`feature/content` 保留 `data/` 与 `ui/` 两级。`core` 模块按能力主题平铺，`service` 只有服务与视图三个文件。约定为：页面级代码放 `ui/`，纯逻辑放 `data/` 或与页面并列的独立文件，跨模块共享逻辑上移 `core`，禁止在 `feature` 内放置 `di/` 目录（未使用依赖注入框架）。

`core` 模块只提供能力，不承载业务语义；`service` 模块只对接系统，不含页面。模块清单见 `settings.gradle.kts`，共 13 个库模块加壳工程。

## 二、命名

包名与 `applicationId` 为 `com.shuiyu.octoberrain`，不使用 `tomo`、`astraabove` 及任何上游片段。

上游类名的替换结果按实现记录：`TomoChrome` 改为 `feature/tray/StatusBarView`，`TomoNotificationService` 改为 `service/NotificationRelayService`，`TomoAccessibilityService` 改为 `service/OverlayAccessibilityService`，`EinkRefresh` 改为 `app/InkRefreshController`，`EinkRoundRect` 改为 `core/render/InkCanvas`，`LockGate` 改为 `app/LockGateImpl`，`TomoPermission` 改为 `core/device/PermissionCenter`。原方案中的 `HttpGateway`、`IoDispatcher`、`WidgetSlotHost` 未创建同名类：微信读书请求集中在 `feature/content/data/WereadTransport.kt`，调度沿用 `Dispatchers.IO`（见第三节待办），组件宿主职责由 `feature/home/HomeView` 承担。

资源文件数量很少，界面文案以 Kotlin 常量声明，颜色与尺寸在 `core/design` 的令牌中定义，XML 仅保留清单与 `res/xml/file_paths.xml`。新增资源时前缀按模块划分：通用 `core_`，桌面 `home_`，组件 `widget_`，托盘 `tray_`，设置 `settings_`；资源名中不出现 `eink`，统一写 `ink`。

配置以单键 JSON 保存（`ConfigPersistence` 的 `PREFS_NAME` 与单键），功能模块的局部偏好使用各自的 `SharedPreferences` 文件，文件名与键名声明在对应类的常量中（`feature/files/data/FilePrefs.kt`、`feature/settings/ImageDirectoryStore.kt`、`app/MainActivity` 的 `UI_PREFS`）。禁止在业务代码中散写字符串键。

## 三、代码风格

Kotlin 使用官方风格加 ktlint 校验（ktlint 1.4.1、插件 12.1.2），缩进 4 空格，行宽上限 120，由仓库根 `.editorconfig` 的 `max_line_length` 与 detekt 的 `MaxLineLength` 规则共同约束；禁止通配符导入。公开 API 显式声明返回类型。全仓库源码已完成 ktlintFormat，排版改动不夹带逻辑与注释内容的变更。

静态检查命令为 `gradle ktlintCheck detekt`，两项任务配置为失败即中止；`gradle check` 一次执行格式、缺陷扫描、单元测试与 Android Lint。detekt 1.23.7 按模块维护基线 `config/detekt/baseline-<模块>.xml`，12 份、累计 237 条存量问题；Android Lint 基线位于 `config/lint/baseline-<模块>.xml`，14 份、累计 72 条；ktlint 存量 0 条。新增或重构代码先修复告警，确需更新基线时在同一提交内说明理由，生成命令为 `gradle detektBaseline` 与 `gradle updateLintBaseline -PlintBaselineUpdate`。

禁止在主线程读写文件与网络。`feature` 层不读取 `DisplayMetrics` 与 `Resources.getDisplayMetrics`，尺寸经 `core/design` 令牌换算；`Context.getSharedPreferences` 只在 `core:data`、`core:device` 与配置相关的少量页面工具中使用。

协程调度现状：全仓 56 处 `Dispatchers.IO`，尚未抽出统一调度入口，`IoDispatcher` 为待办；新增代码沿用现有写法，改动集中在单文件时不经中间层。回调式系统接口尽量封装为 `Flow` 或 `suspend` 函数，协程作用域跟随页面或应用生命周期（`app/OctoberRainApp` 的 `scope`、页面 `onPause` 取消）。

注释使用中文，函数注释说明用途、参数与行为，公共类型补充约束说明。不写无信息量的注释。

## 四、构建变体

构建类型只有 `debug` 与 `release`，没有渠道与显示类型两个维度。`release` 开启 R8、资源压缩与混淆，混淆规则集中在 `app/proguard-rules.pro`，只保留反射入口、序列化生成物与组件协议实现三类；按 D7 沿用调试签名配置，正式发布前替换签名与密钥。显示类型在运行时由 `DeviceProfile.display` 判定，不需要构建变体。

## 五、测试

测试为纯 JVM 单元测试，统一放在各模块 `src/test`，不新增 `androidTest`。当前 52 个测试类、469 例、1 例跳过；覆盖核心数据与纯逻辑：配置迁移、JSON 容错、时间与月份网格、主题令牌、文件与媒体索引、查看器状态与几何、设置声明表、组件声明、渲染尺寸与抖动、索引隔离。

Android 依赖通过 `testOptions.unitTests.isReturnDefaultValues = true` 与接口注入隔离，涉及 `Context` 的类用最小假实现（`ContextWrapper` 子类）替代，不引入 Robolectric。覆盖率门槛尚未接入；截图测试与 UIAutomator 冒烟未接入，界面改动以真机截图记录在 `_analysis/verify/` 或 `_analysis/team/shots/`。

新增纯逻辑时先写可测函数再接入页面，测试命名使用中文反引号描述行为。

## 六、分支与提交

工程当前没有 Git 仓库（根目录无 `.git`），版本以快照方式保存：`D:\DSHStation\_snapshots\OctoberLauncher-<版本>`，快照后生成文件清单摘要记录在 `docs/design/15-V1.0开发计划.md` 的版本段落。

启用版本控制后沿用以下约定：主分支 `main` 保持可发布，功能分支 `feat/<域>-<简述>`，修复分支 `fix/<域>-<简述>`，发布分支 `release/<版本>`；提交信息用约定式，一次提交只做一件事；合并前通过 `gradle check`，关键改动附真机截图。

## 七、版本与发布

版本号写在根 `VERSION`（当前 `0.9.7`）与 `app/build.gradle.kts` 的 `versionName`、`versionCode`（当前 15），两者保持一致且 `versionCode` 单调递增。每个版本在 `CHANGELOG.md` 记录新增、修复与已知问题。

发布流程：升版本号与 `versionCode`、跑门禁、生成 release 包与快照、记录 APK 大小与签名指纹、交付安装包与验收记录。release 签名当前沿用调试密钥（D7），正式发布前替换为发布密钥并重新验证安装与更新。更新清单托管在自有域名，不复用任何上游地址。

## 八、文档

`docs/` 维持本文档集：功能清单、结构剖析、目标架构、身份隔离、适配设计、路线、代码索引、工程规范。新增功能时同步更新功能清单；架构调整时更新目标架构与本文档的目录约定。根目录 `README.md` 说明工程用途、构建方式与目录结构。

模块级 `README.md` 尚未创建，列为待办；创建时说明该模块职责、对外契约与扩展方式，篇幅控制在百行以内。
