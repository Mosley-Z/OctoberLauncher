# W5 同步验收脚本：保留导入数据 → 触发独立同步 → 校验盘文件与负一屏渲染 → 输出对比。
# 所有设备操作按 lead 指定顺序执行；脚本只读导入数据，不删除任何已有文件。
param(
    [string]$Adb = 'D:\DSHStation\.toolchain\android-sdk\platform-tools\adb.exe',
    [string]$Device = 'SA4B03CN2608081096X',
    [string]$Package = 'com.shuiyu.octoberrain',
    [string]$OutDir = 'D:\DSHStation\OctoberLauncher\_analysis\verify\w5-20261020-160342',
    [int]$SyncWaitSeconds = 180
)

$ErrorActionPreference = 'Stop'
function Sh([string]$cmd) { & $Adb -s $Device shell $cmd 2>&1 }
function Evidence([string]$name) { Join-Path $OutDir $name }

New-Item -ItemType Directory -Force $OutDir | Out-Null

# 一、前置状态
"== 1. 前置状态 ==" | Tee-Object -FilePath (Evidence 'sync-log.txt')
$ver = Sh "dumpsys package $Package" | Select-String 'versionCode|versionName' | Select-Object -First 2
$ver | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append
$before = Sh "run-as $Package sh -c 'ls files 2>/dev/null; echo ---; ls files/weread 2>/dev/null | wc -l; echo ---; ls files/weread-import 2>/dev/null | wc -l'"
$before | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append

# 二、备份配置
& $Adb -s $Device shell "run-as $Package cat shared_prefs/octoberrain_config.xml" > (Evidence 'cfg-before-sync.xml')

# 三、保留导入数据：weread 改名为 weread-import
$exists = Sh "run-as $Package sh -c 'if [ -d files/weread ]; then echo yes; else echo no; fi'"
if ($exists -match 'yes') {
    Sh "run-as $Package mv files/weread files/weread-import" | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append
    "moved files/weread -> files/weread-import" | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append
}

# 四、配置改为 synced 并重启应用，触发一次同步
$raw = Get-Content -Raw (Evidence 'cfg-before-sync.xml')
if ($raw -notmatch 'wereadSource') { throw '配置中没有 wereadSource 键' }
$patched = $raw -replace '&quot;wereadSource&quot;:&quot;(imported|local|synced)&quot;', '&quot;wereadSource&quot;:&quot;synced&quot;'
$patchedPath = Evidence 'cfg-sync.xml'
Set-Content -LiteralPath $patchedPath -Value $patched -Encoding UTF8 -NoNewline
& $Adb -s $Device push $patchedPath /data/local/tmp/cfg-sync.xml | Out-Null
Sh "run-as $Package cp /data/local/tmp/cfg-sync.xml shared_prefs/octoberrain_config.xml"
Sh "am force-stop $Package"
Sh "am start -n $Package/.MainActivity" | Out-Null
Start-Sleep -Seconds 3

# 五、等待同步写出 shelf.json
$deadline = (Get-Date).AddSeconds($SyncWaitSeconds)
$synced = $false
while ((Get-Date) -lt $deadline) {
    $hit = Sh "run-as $Package sh -c 'if [ -f files/weread/shelf.json ]; then echo yes; else echo no; fi'"
    if ($hit -match 'yes') { $synced = $true; break }
    Start-Sleep -Seconds 5
}
"sync shelf.json present: $synced" | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append

# 六、盘文件清单与关键键校验
$after = Sh "run-as $Package sh -c 'ls files/weread 2>/dev/null; echo ---COUNT---; ls files/weread 2>/dev/null | wc -l; echo ---IMPORT---; ls files/weread-import 2>/dev/null | wc -l'"
$after | Tee-Object -FilePath (Evidence 'sync-files.txt')
Sh "run-as $Package cat files/weread/kernel_prefs.json" > (Evidence 'sync-kernel-prefs.json')
$kp = Get-Content -Raw (Evidence 'sync-kernel-prefs.json')
foreach ($needle in @('"string"', '"d_week:', '"d_month:', 'stats_annual_', 'stats_overall', 'bill_week_', 'bill_month_')) {
    $ok = $kp -match [regex]::Escape($needle)
    "kernel_prefs 包含 $needle : $ok" | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append
}
foreach ($f in @('shelf.json', 'shelf_ids.json', 'book.json', 'kernel_prefs.json', 'bills.json', 'assets.json', 'nt_index.json')) {
    $ok = $after -match [regex]::Escape($f)
    "同步目录含 $f : $ok" | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append
}
$ntFiles = ($after -split "`n" | Where-Object { $_ -match '^nt_' }).Count
"同步目录 nt_<id>.json 条数: $ntFiles" | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append

# 七、负一屏渲染取证
Sh "input keyevent KEYCODE_HOME"
Start-Sleep -Milliseconds 700
Sh "input swipe 20 620 460 620 300"
Start-Sleep -Seconds 2
& $Adb -s $Device exec-out screencap -p > (Evidence 'sync-neg-top.png')
for ($i = 1; $i -le 4; $i++) {
    Sh "input swipe 240 700 240 200 350"
    Start-Sleep -Seconds 1
    & $Adb -s $Device exec-out screencap -p > (Evidence ("sync-neg-{0}.png" -f $i))
}
"截图完成" | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append

# 八、恢复配置来源
Sh "run-as $Package cp shared_prefs/octoberrain_config.xml shared_prefs/cfg-during-sync.xml"
& $Adb -s $Device push (Evidence 'cfg-before-sync.xml') /data/local/tmp/cfg-restore.xml | Out-Null
Sh "run-as $Package cp /data/local/tmp/cfg-restore.xml shared_prefs/octoberrain_config.xml"
"配置已还原为同步前版本" | Tee-Object -FilePath (Evidence 'sync-log.txt') -Append
