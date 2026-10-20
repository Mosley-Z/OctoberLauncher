# 微信读书 API KEY 手机端录入方案评估

评估对象：为墨水屏设备提供「手机输入、设备接收」的接口密钥录入入口。本文只做评估，不涉及源码改动。

## 一、评估判断

主方案采用「设备临时 HTTP 服务 + 同一局域网 URL 二维码」：设备在设置页启动一次性的本地 HTTP 服务，绑定随机端口并生成一次性令牌，二维码内容为 `http://<设备IPv4>:<port>/k?t=<令牌>`；手机扫码打开页面粘贴密钥提交，设备收到后按现有设置页的写入与刷新流程保存密钥，随后关闭服务。该流程不新增权限，手机与设备处于同一 WiFi 即可，当前设备已连接手机热点（`wlan0` 地址 10.50.139.176/24），具备直接试用条件。

设备自建热点（`WifiManager#startLocalOnlyHotspot`）作为无局域网或路由器开启 AP 隔离时的升级选项，只在主流程失败时提供，不作为默认入口。备选方案为「短地址 + 6 位配对码」，用于二维码被否决或手机扫码失败时。

## 二、现状核实

### 2.1 网络与明文策略

`app/src/main/AndroidManifest.xml` 已声明 `INTERNET`（:5）、`ACCESS_NETWORK_STATE`（:6）、`ACCESS_WIFI_STATE`（:10）、`CHANGE_WIFI_STATE`（:11）；未声明 `ACCESS_FINE_LOCATION`、`NEARBY_WIFI_DEVICES`、`CAMERA`。`<application>`（:34-42）未设置 `android:usesCleartextTraffic`，`app/src/main/res/xml/` 只有 `accessibility_service.xml` 与 `file_paths.xml`，不存在 `network_security_config`。`gradle/libs.versions.toml:5-6` 为 compileSdk 35、minSdk 26；`targetSdk` 为 35（:6）。

Android 明文流量策略约束的是应用自身发起的 HTTP 客户端（`NetworkSecurityPolicy` 与 `HttpURLConnection`/OkHttp 等），入站 `ServerSocket` 不在约束范围内。手机浏览器访问设备地址属于手机侧浏览器行为，与本应用的明文策略无关。因此本方案不需要修改 `usesCleartextTraffic`，也不需要 `network_security_config`；服务端只需 `INTERNET` 权限，该权限已存在。

设备实机为 Android 11（`ro.build.version.sdk=30`），具备 `android.hardware.wifi` 与前置摄像头特性，当前连接手机热点 SSID「Xiaomi 17 Pro Max」，`wlan0` 地址 10.50.139.176/24；`/data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml` 存在，`dumpsys wifi` 含 `SoftApManagerReturnCodeCounts`，说明系统具备 SoftAP 组件，但是否允许第三方启动仍需实机验证。

### 2.2 密钥存储、校验与刷新

`core/common/.../Models.kt:299-301` 定义 `IntegrationsConfig.wereadApiKey`（默认空串）与 `wereadEnabled`；`IntegrationsConfig` 位于 :294 起的 KDoc 与 :299 的声明。

`feature/settings/.../WereadKeyValidator.kt:9-35`：前缀 `wrk-`，后缀长度 8 至 128，字符集 `^[A-Za-z0-9_-]+$`，逐项返回中文错误文案。

录入入口在 `feature/settings/.../BookshelfSettingsScreen.kt:79-99`：`InkDialogs.input` 弹窗输入，`password = true`；校验通过后 `updateIntegrations { it.copy(wereadApiKey = key, wereadEnabled = true) }`，随后提示「密钥已保存」。写入封装为同文件 :149-152 的 `updateIntegrations`，内部调用 `SettingsUi.edit`（`SettingsUi.kt:45-47`，最终 `AppServices.config.edit`）与 `SettingsUi.refresh`（:50-52，`AppServices.refresh.requestPartial`）。

清除入口在同文件 :101-115：置空并关闭 `wereadEnabled`，随后协程调用 `AppServices.content.refreshAll()`。

刷新链：`ContentSourceImpl.refreshAll`（`feature/content/.../ContentSourceImpl.kt:470-474`）先 `invalidate()`，再 `AppServices.refresh.requestPartial()`；`invalidate()`（:192-199）清空书架缓存并调用 `wereadClient.invalidate()`（`WereadClient.kt:72-77`，清统计与缓存）。设置声明表侧的内容刷新走 `SettingsBaseScreen.kt:104-131` 的 `SettingEffect.CONTENT_REFRESH`，经 `RefreshSchedulerBridge.notifyContentChanged()`（`SettingsSpec.kt:240-256`）与 `AppServices.content.refreshAll()`。

新入口只需要复用同一写入函数：`SettingsUi.edit` 写入 `wereadApiKey` 与 `wereadEnabled`，页面/服务回调再执行 `SettingsUi.refresh()` 与 `AppServices.content.refreshAll()`，即与设置页行为一致。

### 2.3 现有能力检索

对 `app/`、`core/`、`feature/`、`service/` 检索 `Zxing|ZXing|QRCode|ServerSocket|NanoHTTPD|HttpServer|SocketServer`，无命中。二维码生成、二维码识别、HTTP 服务端均需新增。

`WifiManager` 仅用于状态监听与开关（`core/data/.../SystemControlsImpl.kt:59-60`、`:87`、`:231`），没有热点启动代码。`feature/files` 只提供 `FileProvider`，与 HTTP 服务无关。项目无 Google 服务，二维码识别不能依赖 Play 服务组件。

### 2.4 现有输入体验的两个事实

`InkDialogs.kt:60-83` 的 `input` 只在 `numeric && password` 同时为真时设置密码输入类型（:82）；当前密钥弹窗只传 `password = true`，实际仍以明文显示，墨水屏上数十位字符的输入与校对成本都很高。这是本次评估的出发点。

设置页每行声明在 `BookshelfSpecs.kt`：接口密钥行为 :66-76（`bookshelf.wereadKey`，Action 摘要「未配置/已配置」），清除行为 :77-84，刷新为 :85-91。新增「手机录入」入口应在该分组内增加一行 Action，动作路由在 `BookshelfSettingsScreen` 的动作表或 `SettingsBaseScreen` 的动作分发处接入。

## 三、候选方案逐项评估

### 3.1 设备侧临时 HTTP 服务（局域网，主方案）

协议：设备在 IO 线程监听 `ServerSocket(port=0, backlog=8, InetAddress 0.0.0.0)`，随机端口与 128 位随机令牌；手机 HTTP/1.1 请求，`GET /` 返回输入页，`POST /k?t=...` 提交密钥；响应 `Connection: close` 后关闭连接；会话超时 90 秒或成功提交后关闭服务。

交互步数：设备「设置 → 微信读书 → 接口密钥 → 手机录入」4 次点击（含打开设置），页面自动启动服务并显示二维码；手机扫码 1 步、粘贴密钥 1 步、提交 1 步；设备自动显示成功并返回。设备侧无需任何文字输入。

权限：`INTERNET` 已有；地址读取需要 `ACCESS_NETWORK_STATE`（已有）。无需定位权限。若要在页面内展示当前 WiFi 名称可再加 `ACCESS_WIFI_STATE`（已有）。

依赖：无外部依赖；二维码生成需 ZXing core 或自写编码器，见第四节。

失败分支：无活动网络或只有 IPv6 时无法给出可访问地址；家庭路由器开启 AP 隔离时手机无法访问设备；端口被占用（改用随机端口可规避）；令牌过期、错误、重复提交；手机浏览器不支持 HTTP（极少数强制 HTTPS 的浏览器）；设备切换前后台导致服务销毁。

改动量：服务端 200 至 260 行、会话状态 80 至 120 行、地址解析 70 至 100 行、二维码渲染 60 至 80 行、录入页 220 至 280 行、入口与路由 40 至 60 行，合计约 670 至 900 行，另加测试 200 至 300 行。

隐患：同一局域网的其他设备可达该端口，凭一次性令牌与短时限降低暴露面；设备需要保持亮屏，否则协程与 socket 可能被系统挂起；墨水屏显示二维码的刷新与残影需要专门处理。

### 3.2 设备自建热点（LocalOnlyHotspot，升级方案）

协议：`WifiManager.startLocalOnlyHotspot`（API 26 起）创建无外网热点，回调返回 `WifiConfiguration`，其中含 SSID 与预共享密钥；设备在热点接口上提供与 3.1 相同的 HTTP 服务；手机手动或扫码入网后访问设备地址。

交互步数：设备侧同上，另加一次「开启热点」确认；手机侧多一步「加入热点」（系统 WiFi 列表选择 SSID、输入密码），若使用 `WIFI:` 二维码则可省去手动选择。总计手机侧 4 至 6 步。

权限：`CHANGE_WIFI_STATE` 已声明（`AndroidManifest.xml:11`）；Android 11（API 30）上 `startLocalOnlyHotspot` 还需要 `ACCESS_FINE_LOCATION` 运行时授权，当前清单未声明；设备为 API 30，`NEARBY_WIFI_DEVICES` 不适用（该权限从 API 33 生效，targetSdk 35 的声明只影响 Android 13 及以上设备，需同时提供 `usesPermissionFlags="neverForLocation"`）。用户拒绝定位时热点不可用。

依赖：系统 API，无第三方库；地址无法由 API 直接取得，需要枚举 `NetworkInterface`。

失败分支：厂商 ROM 禁用 SoftAP（当前设备 SoftAP 存储存在，但未验证第三方调用）；启动热点会断开现有 WiFi，密钥保存后需要重新连接原网络；定位权限被拒；部分机型同时开蓝牙或使用某些省电策略时启动失败。

改动量：在 3.1 基础上增加热点控制器 120 至 160 行、地址枚举与候选 60 至 80 行、权限申请与降级 40 至 60 行，合计增加 220 至 300 行。

隐患：启动热点会中断设备当前网络，成功后需要显式恢复；定位授权弹窗对启动器类应用显得突兀，用户拒绝概率不低；热点密码与 URL 同屏展示会增加墨水屏信息密度。

### 3.3 二维码直接编码密钥

协议设想一：设备显示密钥二维码，手机扫描后仍需回传设备，回传通道即 3.1 的服务，二维码本身没有减少步骤，反而多一次扫描。
协议设想二：手机侧页面生成密钥二维码，设备前置摄像头扫描识别。设备具备 `android.hardware.camera.front`，但需要 `CAMERA` 权限、相机预览界面、ZXing 解码与对焦控制；墨水屏预览帧率低，扫码成功率不可控；手机侧生成二维码需要浏览器内编码库或第三方应用，前者增加页面复杂度，后者依赖用户安装。

容量：密钥最长为 4+128=132 字符，按字节模式、纠错级别 M，QR 版本 8 可容纳 152 字节，版本 8 为 49×49 模块，在 480 宽屏幕上单模块约 8 像素，可显示；容量不是限制因素。

交互步数：设想二至少需要「手机打开生成页 → 设备点扫描 → 对准 → 识别 → 保存」5 步以上，且设备端需要相机界面。

权限：`CAMERA`（危险权限，需运行时申请）。

依赖：ZXing core 的解码部分仍需引入；或使用 ML Kit，但设备无 Google 服务，不可行。

失败分支：光线、对焦、屏幕反光、曲面屏、二维码显示尺寸。

改动量：相机预览与解码 300 行以上，且设备端输入界面复杂度高于 3.1。

隐患：密钥以图像形式出现在手机屏幕上，可能被截屏或旁观拍摄；设备相机对准手机屏幕的交互在墨水屏上体验差。

判断：不作为候选主方案。二维码更适合承载「短 URL + 令牌」，不适合承载密钥本体。

### 3.4 手机开热点 + 设备连入

协议：手机开启个人热点；设备在系统 WiFi 设置中连接该热点；设备取得 `wlan0` 地址；手机浏览器访问该地址上的临时服务。

交互步数：手机开热点 1 至 2 步；设备「设置 → WiFi → 选择热点 → 输入密码（墨水屏软键盘）」4 至 6 步；再进入桌面设置启动服务并扫码 3 步。设备侧文字输入量大，恰是本次要消除的环节。

权限：无新增权限；设备侧读取地址用 `ACCESS_NETWORK_STATE`。

依赖：无。

失败分支：手机热点频段为 5GHz 而设备仅 2.4GHz；热点密码含特殊字符；设备已连接其他 WiFi 需要切换；热点无互联网不影响本方案。

改动量：仅需 3.1 的局域网分支与地址展示，约 40 至 60 行提示文案，不需要热点控制器。

隐患：设备端输入热点密码的成本高于直接输入密钥；手机热点名称与密码可能变化，需要每次重连。

判断：作为 3.1 失败后的备选网络形态，不单独作为入口方案。当前实机已处于手机热点下（`wlan0` 10.50.139.176），说明该形态可用。

### 3.5 短地址与 6 位配对码

协议：设备显示 `http://<IPv4>:<port>` 与 6 位数字配对码；手机手动输入地址打开页面，再输入配对码；令牌由配对码承担（6 位数字仅 20 bit，需要限制尝试次数并绑定设备会话）。

交互步数：手机输入地址（约 14 字符）1 步、输入配对码 1 步、粘贴密钥 1 步、提交 1 步；设备侧少一次二维码渲染。

权限：无新增。

依赖：无；不引入二维码库。

失败分支：地址输入错误、配对码过期、同一网段多台设备时地址混淆。

改动量：服务端与会话同 3.1，减少二维码渲染与依赖，增加配对码校验与限速，约 40 行。

隐患：手机键盘输入 IP 与端口仍易出错；6 位码抗穷举能力弱，必须限制 5 次尝试并每分钟重置；没有二维码时用户需要先知道设备地址。

判断：作为无二维码依赖的备选，适合在引入 ZXing 被否决或扫码失败时启用。

### 3.6 复用现成能力

检索结果：无二维码生成、无二维码识别、无 HTTP 服务、无 NanoHTTPD。`feature/files` 的 `FileProvider` 只处理文件共享。`WereadClient` 的 `HttpURLConnection` 是出站客户端，不能复用为入站服务。`SystemControlsImpl` 的 `WifiManager` 只做状态读取与开关。

判断：不存在可直接复用的入站服务或二维码能力；热点能力可复用系统 API，但需要新增权限与控制器。

### 3.7 候选对比

| 候选 | 设备侧输入 | 手机侧步数 | 新增权限 | 新增依赖 | 改动量 | 主要隐患 |
| --- | --- | --- | --- | --- | --- | --- |
| 3.1 局域网 HTTP | 无 | 3 | 无 | ZXing core（或自写编码） | 约 670-900 行 | 路由器 AP 隔离、需亮屏 |
| 3.2 设备热点 | 无 | 4-6 | FINE_LOCATION | 同 3.1 | 约 890-1200 行 | 厂商限制、断网、授权弹窗 |
| 3.3 密钥二维码 | 对准扫描 | 5+ | CAMERA | ZXing core | 300 行以上 | 扫码成功率、密钥曝光 |
| 3.4 手机热点 | 热点密码 | 4-6 | 无 | 无 | 40-60 行 | 设备端输入成本高 |
| 3.5 短码 | 无 | 4+ | 无 | 无 | 约 710-940 行 | 手动输入易错、穷举 |
| 3.6 复用 | 不可行 | - | - | - | - | 无现成能力 |

## 四、推荐方案

### 4.1 交互流程

第一屏（设备，`KeyEntryScreen` 初始态）：标题「手机录入密钥」，说明「手机与本机连接同一 WiFi 后扫码」，按钮「显示二维码」与「取消」。

第二屏（二维码态）：请求局域网 IPv4 与随机端口，启动服务，整屏刷新一次以消除残影，绘制二维码，二维码下方显示 `http://<ip>:<port>` 与倒计时（90 秒），提示「扫码后粘贴密钥提交，本页自动完成」。

手机侧：相机扫码 → 浏览器打开输入页 → 粘贴密钥 → 提交 → 页面显示「已提交，请查看设备」；设备轮询或回调收到成功后，写配置并整屏刷新为成功页「密钥已保存，正在刷新数据」，随后自动返回设置页并触发 `AppServices.content.refreshAll()`。

失败态：设备显示失败原因（地址不可用、热点启动失败、等待超时、密钥格式错误）与「重试」「改用配对码」按钮；手机页面显示格式错误说明，允许重新提交，最多 5 次。

断网升级：第二屏提供「本机开启热点」按钮，仅当检测到没有可用局域网地址或提交超时时出现；启动热点后展示 SSID 与密码（文本）以及新的 URL 二维码；提交成功后关闭热点并提示「已恢复原网络」，恢复动作由 `WifiManager` 保存的旧网络信息执行，失败时提示用户手动重连。

### 4.2 二维码内容格式

主二维码：`http://<ipv4>:<port>/k?t=<token>`。`token` 为 128 位随机数的 32 位十六进制串；URL 总长约 45 至 60 字节，纠错级别 M，QR 版本 3 至 4（29×29 至 33×33 模块），在 480 像素宽屏幕以 320 像素绘制、四周留 4 模块静区，模块尺寸约 8 像素，可被常见手机相机识别。

热点模式下提供两种展示，推荐 B：
A：第一张二维码使用 `WIFI:T:WPA;S:<ssid>;P:<password>;;`，第二张为 URL。优点是手机可自动入网；缺点是 iOS 与部分 Android 相机对 `WIFI:` 的支持不一致，部分厂商相机需依赖 Google Lens（设备与手机均可能缺失），失败时用户不知道原因。
B：只放 URL 二维码，SSID 与密码以文字两行展示，由用户手动加入。优点是二维码单一、页面信息少；缺点是手机侧多两步输入，但热点密码通常为 8 至 10 位，输入成本可接受。

选择 B，避免依赖手机相机对 `WIFI:` 的非标准扩展。若后续实测主流手机均支持，可在同一屏增加较小的 WIFI 二维码作为可选入口。

### 4.3 页面清单

1. 设置页入口行：`BookshelfSpecs.kt` 微信读书分组新增 `bookshelf.wereadKeyMobile`，标题「手机录入密钥」，Action 摘要「扫码或配对码」。
2. 录入页：新增 `Destination.WereadKeyEntry`（`Contracts.kt:318-397` 的 sealed interface 与 :477 的 `destinationLabels` 映射各加一项，`displayLabel` 的 when 可落到默认标签或显式返回「手机录入密钥」），由 `SettingsModule` 的 `SettingsProvider`（`SettingsModule.kt:33-54`）注册。
3. 录入页状态：准备、二维码、等待提交、成功、失败、热点凭证、配对码。所有状态在同一 `InkPage` 内切换，避免多次路由跳转。
4. 手机页面（服务端渲染）：输入页（表单 + 密钥输入框 + 提交按钮）、成功页、错误页（格式错误、令牌无效、已提交、已过期）。页面使用 `viewport`、无 JavaScript、UTF-8 编码，单页 20 至 40 行 HTML。
5. 权限说明弹窗：定位权限申请前展示用途；热点失败时引导手动方式。

### 4.4 新增文件与行数估算

| 文件 | 行数 | 内容 |
| --- | --- | --- |
| `feature/settings/.../keyentry/KeyEntryServer.kt` | 200-260 | ServerSocket 监听、HTTP 解析、路由、响应生成、关闭 |
| `feature/settings/.../keyentry/KeyEntrySession.kt` | 80-120 | 令牌生成、TTL、尝试次数、提交结果状态机 |
| `feature/settings/.../keyentry/KeyEntryNetwork.kt` | 100-140 | 局域网地址解析、热点启动/停止、网络类型判定 |
| `feature/settings/.../keyentry/KeyEntryPage.kt` | 60-80 | 手机侧 HTML 模板 |
| `feature/settings/.../keyentry/QrBitmap.kt` | 60-80 | ZXing `QRCodeWriter` 结果转 `Bitmap` |
| `feature/settings/.../KeyEntryScreen.kt` | 220-280 | 设备侧页面与状态切换、二维码绘制、倒计时 |
| `core/common/.../Contracts.kt` | +6-10 | 新 Destination 与展示标签 |
| `feature/settings/.../BookshelfSpecs.kt` | +15-20 | 入口行声明 |
| `feature/settings/.../BookshelfSettingsScreen.kt` | +20-30 | 动作分发到路由 |
| `feature/settings/.../SettingsModule.kt` | +2-4 | Provider 注册 |
| `feature/settings/build.gradle.kts` | +1-2 | ZXing core 依赖 |
| `app/src/main/AndroidManifest.xml` | +3-6 | 定位权限（仅热点方案） |

生产代码合计约 750 至 1000 行；测试 200 至 300 行。

### 4.5 依赖与体积

ZXing core `com.google.zxing:core:3.5.3`，Apache-2.0，jar 约 590 KB，纯 Java、无 Android 依赖、无 Google 服务要求。经 R8 与资源裁剪后，debug APK 增量估计 250 至 400 KB；方法数约 4000 至 5000，minSdk 26 下无 multidex 问题。若必须零依赖，可移植单文件 QR 编码器（Nayuki QR Code generator，MIT，约 350 至 450 行 Kotlin），但需自担正确性维护与测试，建议只在体积成为硬约束时采用。

### 4.6 权限声明

热点方案需要 `ACCESS_FINE_LOCATION`（`maxSdkVersion="32"`）与 `NEARBY_WIFI_DEVICES`（`usesPermissionFlags="neverForLocation"`，API 33 起生效）。局域网方案不新增权限。定位权限必须在页面内说明用途，拒绝后自动降级到局域网或配对码方式。

### 4.7 安全注意

令牌使用 `SecureRandom` 生成 128 位，比较时用常量时间比较；单次使用，成功或过期即失效；提交尝试上限 5 次，超过即关闭会话；请求体上限 4 KiB，只接受 `Content-Length`，拒绝分块传输；只接受 `POST /k`，其他方法返回 405；响应带 `Cache-Control: no-store`；密钥不写日志、不回显、不出现在成功页；服务在页面暂停、销毁、屏幕熄灭或超时后关闭；设备在会话期间保持亮屏；二维码位图在会话结束后回收。备份导出已有 `LauncherConfig.withoutLocalCredentials()` 剔除密钥（`Models.kt:388` 附近），密钥不会随备份外流。

## 五、备选方案

短地址与配对码：设备显示 `http://<ipv4>:<port>` 与 6 位数字码，手机手动输入地址与配对码后打开页面。该方案不引入任何依赖，适合二维码被否决、相机扫码失败的环境；代价是手机端两次键盘输入，且 6 位码必须限速。实现时与主方案共用服务端、会话与页面，仅替换展示与配对校验，增加约 40 行。

## 六、ServerSocket 自写最小 HTTP 的可行性

判断：足够。手机浏览器提交单一表单只需要：请求行与头部解析、`Content-Length` 读取、`GET /` 与 `POST /k` 两个路由、固定 HTML 响应、`Connection: close`。不需要分块传输、不需要 keep-alive、不需要 multipart、不需要静态文件服务、不需要 HTTPS。实现要点：

1. `ServerSocket(0, 8, InetAddress.getByName("0.0.0.0"))` 绑定全部接口，端口由系统分配，实际端口从 `localPort` 取得并写入二维码。
2. 设置 `soTimeout = 500ms`，在循环中检查取消标志与截止时间；每个连接单独设置 5 秒读超时，避免慢连接占满线程。
3. 读取请求头累计不超过 8 KiB，`Content-Length` 不超过 4 KiB；超限直接 `413`。
4. 令牌从查询参数读取；签发时记录，比较时用 `MessageDigest.isEqual`；提交成功后立即失效。
5. 响应统一 `HTTP/1.1 200 OK`、`Content-Type: text/html; charset=utf-8`、`Content-Length`、`Cache-Control: no-store`、`Connection: close`。
6. 服务端运行在 `Dispatchers.IO` 的协程中，页面销毁时取消协程并 `close()` socket；提交成功后通过回调切到主线程写配置与刷新。

引入 NanoHTTPD 的收益有限：项目当前无该依赖，引入后增加约 60 KB 与一套请求对象抽象，仍需自行处理令牌、限速与路由；其多线程模型对本场景没有优势。自写服务端预计 200 至 260 行，配合纯函数化的协议解析可在 JVM 单测中覆盖。

## 七、地址获取与展示

局域网：`ConnectivityManager.activeNetwork`（`ACCESS_NETWORK_STATE` 已有）配合 `LinkProperties.linkAddresses` 取第一个 IPv4 非回环地址，接口名一般为 `wlan0`；必要时用 `NetworkInterface.getNetworkInterfaces()` 兜底。注意过滤 `127.0.0.1`、`169.254.*` 与 IPv6。

热点：`startLocalOnlyHotspot` 回调返回的 `WifiConfiguration` 提供 SSID 与预共享密钥，但热点接口地址没有公开 API。取得方式为枚举 `NetworkInterface`，排除回环与当前站点接口，优先匹配 `ap*`、`swlan*`、`wlan1` 等名称，取其中的 IPv4 地址；如果仅有 `192.168.43.1` 一类约定地址的推断，必须先确认本机存在该地址再展示。热点网段一般为 `192.168.43.0/24` 或 `192.168.49.0/24`，提交端校验远端地址落在 `10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16` 之内。

展示：二维码下方显示完整 URL 文本，便于手动输入；地址不可用时显示明确原因（无 WiFi、仅 IPv6、热点启动失败），并提供「改用配对码」入口。

## 八、真机测试步骤

1. 备份 `shared_prefs/octoberrain_config.xml` 与当前壁纸、密钥配置。
2. 手机与设备连接同一 WiFi，记录设备 `ip -4 addr show wlan0` 地址。
3. 设备进入「设置 → 微信读书 → 接口密钥 → 手机录入」，确认二维码出现、地址与设备地址一致。
4. 手机相机扫码，确认浏览器打开输入页；粘贴合法密钥提交。
5. 设备确认成功页出现，返回后检查设置页摘要为「已配置」，阅读数据刷新。
6. 失败用例：错误格式密钥、重复提交、令牌篡改、超过 90 秒、手机离开网络；确认错误页文案与设备超时页。
7. 热点方案：关闭设备 WiFi，点击「本机开启热点」，确认定位权限弹窗与用途说明；手机加入热点后扫码提交；确认成功后热点关闭、原 WiFi 恢复、地址提示正确。
8. 配对码方式：在无二维码依赖的构建中验证地址与配对码输入、限速与过期。
9. 重启设备后检查密钥仍在、阅读数据可刷新；恢复备份配置与原始网络。

## 九、遗留与待定事项

1. 设备厂商 ROM 是否允许第三方调用 `startLocalOnlyHotspot`，需真机验证；若不支持，热点方案整体降级为「手机热点 + 设备连入」。
2. 定位权限在 API 30 设备上的运行时行为与厂商定制弹窗需要实测；若授权体验不可接受，可默认只提供局域网与配对码两种方式。
3. 家庭路由器 AP 隔离、访客网络隔离会影响局域网方案，需要文案引导用户改用手机热点。
4. 墨水屏二维码的识别率与刷新残影需要三档设备实测（480×800、552×768、824×1648），必要时调整二维码像素尺寸与静区。
5. `InkDialogs.input` 的密码输入类型问题（`InkDialogs.kt:80-83`）建议另行修复；本方案的新入口不依赖该弹窗。
6. 二维码库选型需在实施前确认 APK 体积预算；若 release 包体积敏感，改用单文件编码器。
7. 本报告写入 `_analysis/team/apikey-entry-eval.md`，未改动任何工程源码与版本文件。
