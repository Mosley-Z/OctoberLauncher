# core:common

跨模块契约与数据模型的公共层。所有模块通过本层声明的接口交互，不横向引用彼此实现。

## 关键类

`src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt`：`Destination` 页面目的地、`Screen` 与 `ScreenProvider` 页面协议、`Router` 路由、`AppServices` 服务定位器（19 个服务）、`LockGate`、`SystemControls`、`ContentSource`、`NegativeModule` 等。

`Models.kt`：`LauncherConfig` 及其分区模型，版本 7；`withoutLocalCredentials` 在备份与导出时剔除 PIN 摘要与盐。

`AppJson.kt`：全局 `Json` 配置，忽略未知字段、编码默认值、显式空值不输出，保证跨版本容错。

`PinHasher.kt` 与 `PinLockPolicy.kt`：PBKDF2-HMAC-SHA256 摘要、旧格式判定与升级判定；失败次数、剩余次数与指数退避。

`HexCodec.kt` 负责十六进制转换，`InkLog.kt` 统一日志标签，`TimeUtils.kt` 提供日期与时长格式化。

## 依赖

无工程依赖。对外暴露 `androidx.core`、协程与 `kotlinx.serialization`，模块按 `api` 传递。

## 测试

`src/test` 五个文件、37 例：JSON 容错、模型默认值、月份网格、时间格式化、PIN 摘要与退避。

## 已知限制

契约增删会影响全部模块，改动需同步检查 `AppServices` 装配与各模块编译。`PinHasher` 默认 12 万轮，低端设备首次校验约耗时百毫秒，调用方须放到后台线程。
