# core:device

设备画像与系统能力探测。布局与权限判断依赖本层，页面不读取 `DisplayMetrics`。

## 关键类

`DeviceProfile.kt`：画像字段包括像素与 dp 尺寸、密度、宽高分级、显示类型、前光、系统栏插入区与长短边。

`DeviceProfileDetector.kt`：启动时探测显示类型，依据厂商、品牌、型号关键字判定墨水屏；前光改为探测暖光设置键，缺失时降级为不可用。

`DisplayClass.kt` 区分墨水屏与液晶屏，`SizeClass.kt` 提供 `compact`、`medium`、`expanded` 分级。

`HomeRole.kt` 查询并申请默认主屏角色。

`PermissionCenter.kt` 汇总通知读取、无障碍、悬浮窗、精确闹钟、使用情况访问、存储与通知显示七项权限，并提供跳转入口。`UsageAccessChecker.kt` 按 SDK 版本选择 `unsafeCheckOpNoThrow` 或废弃接口。

## 依赖

`api(project(":core:common"))`。

## 测试

`src/test` 一个文件、4 例：前光判定与探测失败降级。

## 已知限制

API 26 至 28 的 `checkOpNoThrow` 分支只有单元测试覆盖，未在真机验证；墨水屏关键字表需随新机型补充，未知机型按液晶屏处理。
