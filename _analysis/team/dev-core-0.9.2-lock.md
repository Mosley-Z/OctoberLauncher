# task-10 锁屏安全与状态恢复

## 一 环境

- 工程目录 `D:\DSHStation\OctoberLauncher`
- JDK `D:\DSHStation\.toolchain\jdk`（取含 `bin\javac.exe` 的最高版本）
- `ANDROID_HOME=D:\DSHStation\.toolchain\android-sdk`，`GRADLE_USER_HOME=D:\DSHStation\.toolchain\gradle-home`
- Gradle 8.11.1，Kotlin 2.0.21，AGP 8.7.3

## 二 改动清单

### 1 新增 `core/common/src/main/java/com/shuiyu/octoberrain/core/common/PinHasher.kt`（170 行）

纯 JVM 工具对象，不引用 Android 类型，供 `core/common` 单元测试直接覆盖；只负责摘要与校验，失败计数与退避移入 `PinLockPolicy`，十六进制转换移入 `HexCodec`。

- 常量：`DEFAULT_ITERATIONS = 120_000`、`SALT_BYTES = 16`；私有 `ALGORITHM = "PBKDF2WithHmacSHA256"`、`KEY_BITS = 256`、`PREFIX = "pbkdf2"`、`SEPARATOR = '$'`。
- 摘要格式 `pbkdf2$<轮数>$<小写十六进制>`，轮数写入摘要串，校验以串内轮数为准，轮数提升后既有摘要仍可校验。
- `newSalt()`：`SecureRandom` 生成 16 字节盐并转十六进制。
- `effectiveIterations(configured)`：`coerceAtLeast(DEFAULT_ITERATIONS)`，旧配置的 `iterations = 1` 在计算前被提升。
- `hash(pin, saltHex, iterations)`：按传入轮数计算（低于 1 取 1），调用方先用 `effectiveIterations` 提升，便于测试构造低轮数摘要。
- `verify(pin, saltHex, stored)`：旧格式（无前缀的 64 位十六进制）按单次 SHA-256(盐串 UTF-8 + PIN UTF-8) 校验；新格式由私有 `verifyPbkdf2` 解析轮数后重算；比较使用 `MessageDigest.isEqual`。
- `isLegacy`、`iterationsOf`、`needsUpgrade`：旧格式或登记轮数低于目标值时判定需要升级。
- 私有 `derive`、`legacyDigest`，`PBEKeySpec` 用后调用 `clearPassword`。

### 2 新增 `core/common/src/main/java/com/shuiyu/octoberrain/core/common/PinLockPolicy.kt`

- `MAX_ATTEMPTS = 5`，私有 `BASE_BACKOFF_MILLIS = 30_000L`、`MAX_BACKOFF_MILLIS = 900_000L`。
- `remainingAttempts(failedAttempts)`：`MAX_ATTEMPTS - 失败次数`，下限 0。
- `backoffMillis(failedAttempts)`：前四次返回 0，第五次 30 秒，随后按 2 的幂递增，上限 900 秒。
- `remainingLockoutSeconds(lockoutUntilMillis, nowMillis)`：剩余毫秒向上取整到秒。

### 3 新增 `core/common/src/main/java/com/shuiyu/octoberrain/core/common/HexCodec.kt`

包内可见的 `encode` 与 `decode`，供 `PinHasher` 转换盐与摘要；拆出后 `PinHasher` 与方法数均满足 detekt 默认阈值，静态检查无新增告警。

### 4 `core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt`（:246-262）

`LockConfig` 调整：

```kotlin
val iterations: Int = PinHasher.DEFAULT_ITERATIONS,
...
val failedAttempts: Int = 0,
val lockoutUntilMillis: Long = 0L,
```

`CURRENT_VERSION` 保持 7。新增字段均有默认值，旧配置文件缺少字段时按默认值解码，无须迁移步骤。

### 5 重写 `app/src/main/java/com/shuiyu/octoberrain/LockGateImpl.kt`（170 行）

- `_state` 初始化时直接计算 `lock.enabled && lock.autoLock && pinHash` 非空（:26-30），进程重启后按配置恢复锁定；`MainActivity.kt:103` 已有 `if (AppServices.lock.isLocked) showLock()`，无需改动入口。
- `unlock(pin)`：先按 `PinLockPolicy.remainingLockoutSeconds` 判断是否处于退避锁定，锁定中直接返回假；校验通过走 `applySuccess`，失败走 `applyFailure`。
- `applySuccess`：清空失败计数与锁定截止时间；`PinHasher.needsUpgrade` 为真时用目标轮数重算摘要写回，完成旧格式与低轮数摘要升级。
- `applyFailure`：失败次数加一，`PinLockPolicy.backoffMillis > 0` 时写入 `lockoutUntilMillis = now + backoff`；计数与截止时间写入 `ConfigStore`，进程重启后继续生效。
- `setPin` / `clearPin`：使用 `newSalt()` 与 `effectiveIterations`，同步清零失败计数。
- `createOverlay` 的两个回调在调用时读取 `config.state.value.lock` 的实时值，界面提示始终反映最新失败计数；`LockGate` 接口保持不变。
- `createOverlay` 在锁屏未启用或无 PIN 时返回 null，`MainActivity.showLock` 已按空值提前返回。

### 6 `app/src/main/java/com/shuiyu/octoberrain/LockOverlayView.kt`（19-25、126-160）

- 构造参数新增 `remainingAttempts: () -> Int = { 0 }` 与 `lockoutSeconds: () -> Long = { 0L }`，默认值保证既有调用方兼容。
- `submit()` 失败分支改调 `failureMessage()`：退避中提示「尝试次数过多，N 秒后重试」；否则为「密码错误，剩余 N 次」，剩余为零时提示稍后重试。

### 7 新增测试 `core/common/src/test/java/com/shuiyu/octoberrain/core/common/PinHasherTest.kt`（10 例）

覆盖：PBKDF2 摘要带前缀且校验通过、错误 PIN 返回假、轮数提升后旧 PBKDF2 摘要仍可校验、旧版单次摘要兼容并判定升级、配置轮数低于默认值时提升、盐长度与小写十六进制、剩余次数、退避时长分段与封顶、锁定剩余秒数向上取整、空盐与空摘要返回假；其中退避相关的三例针对 `PinLockPolicy`。

## 三 验证命令与结果

```powershell
# 环境变量设置后执行
gradle :core:common:testDebugUnitTest --offline
# 34 tests completed, 0 failed（原有 24 例 + 新增 10 例）

gradle :app:compileDebugKotlin --offline
# BUILD SUCCESSFUL

gradle :core:common:detekt :app:detekt --offline
# 本任务新增与修改的文件无告警
```

首次运行 `core:common` 测试时 `轮数提升后旧 PBKDF2 摘要仍可校验` 失败（`expected:<1000> but was:<120000>`），原因是 `hash` 内部再次提升轮数，与「构造低轮数摘要用于兼容测试」的用途冲突；改为 `hash` 按传入轮数计算、由调用方使用 `effectiveIterations` 提升后通过。

静态检查首轮对本任务文件报出 6 条告警（`TooManyFunctions`、`ReturnCount`）。处理方式：退避计算拆到 `PinLockPolicy`，十六进制转换拆到 `HexCodec`，`verify` 与 `needsUpgrade` 改为表达式分支，`LockGateImpl` 的初始锁定态并入属性初始化并移除两个只被界面回调使用的公开方法。复跑后本任务文件在 detekt 报告中无条目。

## 四 遗留问题

1. `LockGateImpl` 的失败计数持久化与冷启动锁定态恢复依赖 `ConfigStore` 与 `Context`，`app` 模块按任务要求不新增 `app/src/test`，这两项未做 JVM 单测，改由 `PinHasherTest` 覆盖退避计算、由构建与真机冒烟覆盖装配路径。
2. `onScreenOff` 与 `showLock` 的联动沿用既有实现，本次未改动 `MainActivity.kt`。
3. 轮数提升后首次成功校验会重算摘要并写回配置，写入本身走 `ConfigStore.edit`，配置存储的原子性由另一任务的 `JsonConfigStore` 负责。
