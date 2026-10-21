# Weread k6 host1: 页面级笔记缓存

修复 `DeskData.excerptOf`、`markTexts`、`notesKey` 重复调用 `KernelDataLoader.notes(0)`，每个可见书目反复读取整库笔记的性能缺陷。补丁缓存页面内的完整笔记列表；`notes(limit)` 保持原有限制规则并返回列表副本，`invalidateAll()` 清除缓存。另修复 `hiddenIds()` 在宿主列表为空时提前返回、漏掉本页 `hiddenExtra` 的问题，确保首次移出也能生成正确持久化快照。价格、金额、排序、书目与其他规则保持原实现。

源码来自只读原仓库 `WereadMoji/weread-widget/app/src/main/java/com/inkread/weekread/kernel/desk/DeskData.java`。原源码 SHA-256：`dfbd9a477bcfcde734e221c0b18212de0cd2c501276411e7987859ff817e86d8`。源副本保留原包名，改动可以直接与该文件比较。原仓库未修改。

输入为保留的 `libs/weread-kernel-1.3.2-k6.jar`，SHA-256：`0e8d30a95aa06039152d297879253ae1872ae9ac88beb1cc24553fba48c4ec3a`。输出是独立的 `libs/weread-kernel-1.3.2-k6-host1.jar` 及 `.sha256`。宿主继续用每个页面所属的串行队列访问 `DeskData`；本补丁不引入其他线程或网络请求。

## 可重复构建

仓库根目录执行，Python 仅用标准库，JDK 17、Android SDK 35：

```powershell
python tools/weread-kernel-host/build.py --jdk D:/DSHStation/.toolchain/jdk/jdk17.0.20_12 --android-jar D:/DSHStation/.toolchain/android-sdk/platforms/android-35/android.jar
```

脚本以 `javac --release 8 -g:none` 只编译 `DeskData.java`，将生成的单个 class 替换到原 jar，保留原 zip entry 属性。随后验证 entry 名称、顺序与内容：只有 `com/inkread/weekread/kernel/desk/DeskData.class` 的内容允许不同；`javap -public` 的公开 API 必须完全一致。输入摘要不符直接失败，防止对其他内核版本误打补丁。

## 真实 Android 行为探针

本地 Android SDK 的 `android.jar` 中 `org.json` 为桩，不能用于行为验证。下列探针使用真实内核及 Android 设备上的真实 `org.json`，只读写新建的独立合成数据目录，不读取应用私有数据。

```powershell
python tools/weread-kernel-host/build_probe.py --jdk D:/DSHStation/.toolchain/jdk/jdk17.0.20_12 --sdk D:/DSHStation/.toolchain/android-sdk
adb -s SERIAL push _analysis/weread-host1-probe.jar /data/local/tmp/weread-host1-probe.jar
adb -s SERIAL shell "CLASSPATH=/data/local/tmp/weread-host1-probe.jar app_process /system/bin KernelNotesCacheProbe /data/local/tmp/weread-notes-cache-UNIQUE"
```

用实际设备序列号替换 `SERIAL`，用新的唯一后缀替换 `UNIQUE`。探针拒绝已有目录，避免覆盖其他内容。22 项断言覆盖 limit、调用者列表修改、共享缓存、一页内不提前读取新文件、统一失效后的摘录/划线/版本键更新、新页面读取新数据，以及空宿主列表首次移出、快照重建、已有隐藏项合并。失败时抛出 `AssertionError`，成功时打印 `PASS`。构建脚本不连接设备；需要在两台设备上分别执行探针并记录结果。
