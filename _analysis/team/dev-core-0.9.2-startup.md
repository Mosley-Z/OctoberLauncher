# task-11 启动期工作迁移与前光能力修正

## 一 环境

- 工程目录 `D:\DSHStation\OctoberLauncher`
- JDK `D:\DSHStation\.toolchain\jdk`，`ANDROID_HOME=D:\DSHStation\.toolchain\android-sdk`，`GRADLE_USER_HOME=D:\DSHStation\.toolchain\gradle-home`
- Gradle 8.11.1，Kotlin 2.0.21，AGP 8.7.3

## 二 改动清单

### 1 `core/data/src/main/java/com/shuiyu/octoberrain/core/data/SystemControlsImpl.kt`（259 → 289 行）

- 删除构造期的 `init { register(); refreshAll() }`，构造只初始化状态流：类注释（:22-27）与新增字段 `started`（:32-33）说明启动时机。
- 前光状态初值改为 `FrontLightState(0, 0, false)`（:41），构造期不再读取系统设置。
- 新增 `start()`（:68-73）：`started` 为真时直接返回，否则置位后调用 `register()` 与 `refreshAll()`，避免重复注册接收器。
- `dispose()`（:172-176）：注销接收器并把 `started` 复位，与 `start()` 构成对称的启用与释放路径。
- `updateWifi()`（:214-233）：整体包入 `runCatching`，系统服务为空或读取受限（`SecurityException` 等）时降级为 `WifiState(false, false, "", -100)`，异常不再沿状态刷新路径向上传播。
- `refreshAll()`（:188-197）：读取粘性电量广播的 `registerReceiver(null, filter)` 包入 `runCatching`，失败时跳过电量刷新。
- `frontLightHardwareAvailable()`（:273-282）：设备画像未就绪时由「按存在处理」改为返回假，防止对无前光机型写入 `screen_brightness` / `screen_brightness1`（写入点仍为 `setFrontLight`，:107）。

### 2 `app/src/main/java/com/shuiyu/octoberrain/OctoberRainApp.kt`（129 → 149 行）

- 新增字段 `systemControls: SystemControlsImpl?`（:37-38）。
- 服务装配完成后调用 `systemControls = system` 与 `system.start()`（:79-81），系统监听在 `AppServices` 全部赋值之后才注册。
- 新增 `onTerminate()`（:117-122）：调用 `systemControls?.dispose()`、清空字段并取消协程作用域；注释说明真机通常不回调该方法，进程被回收时接收器随进程释放。

### 3 `core/device/src/main/java/com/shuiyu/octoberrain/core/device/DeviceProfileDetector.kt`（99 → 132 行）

- 新增常量 `KEY_FRONT_LIGHT = "screen_brightness1"`（:33-34），与 `SystemControlsImpl` 写入的暖光键一致。
- `detect()` 中 `hasFrontLight` 由 `display.isEink` 改为 `resolveFrontLight(display.isEink) { probeFrontLight(context) }`（:55）。
- 新增 `resolveFrontLight(isEink, probe)`（:97-100）：液晶屏直接判定无前光；墨水屏以探测结果为准，探测抛出异常或返回假时降级为无前光。
- 新增私有 `probeFrontLight(context)`（:108-113）：读取暖光设置项，`Settings.System.getInt` 在键不存在时抛出 `SettingNotFoundException`，由 `resolveFrontLight` 统一降级。

### 4 新增测试

- `core/device/src/test/java/com/shuiyu/octoberrain/core/device/DeviceProfileDetectorTest.kt`（4 例）：液晶屏不调用探测且判定不支持、探测成功判定支持、探测抛出异常降级不支持、探测返回假判定不支持。
- `core/data/src/test/java/com/shuiyu/octoberrain/core/data/SystemControlsImplTest.kt`（5 例，`ContextWrapper(null)` 子类记录调用）：构造不注册接收器且不访问系统服务、重复 `start()` 只注册一次、`dispose()` 与 `start()` 对称、无前光设备写入前光被忽略、系统服务缺失时刷新保持未知状态。

## 三 验证命令与结果

```powershell
# 环境变量设置后执行
gradle :core:device:testDebugUnitTest :core:data:testDebugUnitTest --offline
# 32 tests completed, 0 failed（含新增 9 例）

gradle :app:compileDebugKotlin --offline
# BUILD SUCCESSFUL
```

首轮 `SystemControlsImplTest` 两例失败（`expected:<1> but was:<2>`），原因是假上下文把 `refreshAll()` 读取粘性电量的 `registerReceiver(null, filter)` 也计入注册次数；假上下文改为分别计数接收器注册与粘性读取后通过。

## 四 遗留问题

1. 完整门禁（`scripts/build.ps1 -Variant debug`、`gradle --offline testDebugUnitTest detekt ktlintCheck`）暂缓：`feature/settings/src/main/.../SettingsSpec.kt` 处于另一任务的重构中间态，`compileDebugKotlin` 报一组 `GROUP_*` 未解析引用与 `DIM_STEPS` 未初始化，导致整包构建失败；该文件不在本任务写作用域。重构完成后需补跑门禁，并把结果补入本报告。
2. `SystemControls` 接口位于 `core/common` 的 `Contracts.kt`，不在本任务写作用域，因此 `start()` 只作为 `SystemControlsImpl` 的具体方法存在；`OctoberRainApp` 持有具体类型 `SystemControlsImpl` 才能调用。
3. `dispose()` 只在 `Application.onTerminate()` 调用，真机不回调该方法。接收器注册在应用上下文上，进程结束即随之释放，因此未追加服务销毁路径；如需更早释放，需在 `service` 模块的 `onDestroy` 中调用 `(AppServices.system as? SystemControlsImpl)?.dispose()`，而 `service` 模块不在本任务写作用域。
4. 前光探测以暖光设置项存在为准，仅带冷光通道且未登记暖光键的墨水屏会被判定为无前光；任务约定探测失败即降级不支持，因此按此实现。
