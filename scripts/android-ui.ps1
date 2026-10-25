# Android CLI 界面观测脚本：封装 layout 与 screen capture/resolve 两个子命令
# 用法示例：
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-ui.ps1 -Action layout
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-ui.ps1 -Action annotate
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-ui.ps1 -Action resolve -Labels "#1 #26"
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-ui.ps1 -Action tap -Label "#26"
#   长按：-Action tap -Label "#26" -HoldMs 650（须使用当前页面标注截图）
#   自绘区滑动：-Action swipe -Label "#26" -EndLabel "#30" -Screenshot <当前标注图>
#   系统顶部边缘诊断：-Action edge-swipe -EdgeStartY 1（按实时逻辑屏幕坐标，不用于元素导航）
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-ui.ps1 -Action find -FindText "设置"
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-ui.ps1 -Action click -FindText "设置"
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-ui.ps1 -Action key -Key back
# 说明：
#   layout 输出 JSON 节点树，含 text、resource-id、interactions、bounds 与 center；
#   annotate 截图并给元素编号，resolve 把编号换算为坐标；
#   十雨桌面自绘文字不进 accessibility 树，自绘元素必须走 annotate 与 resolve。
#   多设备时必须用 -Serial 指定；同一 ADB serial 同一时间只允许一个操作者。

param(
    [ValidateSet('layout', 'annotate', 'resolve', 'tap', 'swipe', 'edge-swipe', 'find', 'click', 'key', 'text', 'raw')]
    [string]$Action = 'layout',
    [string]$Serial = '',
    [string]$Labels = '',
    [string]$Label = '',
    [string]$EndLabel = '',
    [ValidateRange(100, 5000)]
    [int]$SwipeMs = 350,
    [ValidateRange(0, 100)]
    [int]$EdgeStartY = 1,
    [ValidateRange(0, 5000)]
    [int]$HoldMs = 0,
    [string]$FindText = '',
    [string]$Selector = '',
    [string]$Value = '',
    [string]$Key = '',
    [string]$OutDir = '',
    [string]$Screenshot = '',
    [switch]$Full,
    [switch]$NoIdle,
    [int]$TimeoutSec = 60,
    [string]$RawArgs = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# 读取工程根 toolchain.path，与 build.ps1 使用同一套工具链
$tcFile = Join-Path $root 'toolchain.path'
if (-not (Test-Path -LiteralPath $tcFile)) { throw "缺少 toolchain.path：$tcFile" }
$tc = (Get-Content -LiteralPath $tcFile -TotalCount 1).Trim()
if (-not (Test-Path -LiteralPath $tc)) { throw "toolchain.path 指向的目录不存在：$tc" }

# 解析 JDK：工具链下 jdk 目录中取含 bin\javac 的版本目录
$jdkRoot = Join-Path $tc 'jdk'
$jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk) { throw "未在 $jdkRoot 找到 JDK" }

$sdk = Join-Path $tc 'android-sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw "未找到 adb：$adb" }

# 解析 Android CLI：环境变量优先，其次工作区内的工具目录，最后工具链目录
$cliCandidates = @(
    $env:ANDROID_CLI,
    (Join-Path $root '_ui-out\android-cli\android.exe'),
    (Join-Path $root '_tools\android-cli.exe'),
    (Join-Path $root '_tools\android-cli\android.exe'),
    (Join-Path $tc 'android-cli\android.exe')
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
$cli = $cliCandidates | Select-Object -First 1
if (-not $cli) {
    throw @'
未找到 android CLI。安装方式（需要联网，node 24 已在环境内）：
  node -e "const fs=require('fs');fetch('https://dl.google.com/android/cli/latest/windows_x86_64/android.exe').then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.arrayBuffer()}).then(b=>fs.writeFileSync(process.argv[1],Buffer.from(b))).catch(e=>{console.error(e.message);process.exit(1)})" "<工程根>\_ui-out\android-cli\android.exe"
'@
}

# 环境变量是 CLI 定位 JDK 与 SDK 的唯一入口，进程内设置，不写入全局环境
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_USER_HOME = $env:ANDROID_USER_HOME
if (-not $env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME = Join-Path $root '_ui-out\android-home' }
$env:PATH = (Join-Path $jdk.FullName 'bin') + ';' + $env:PATH
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
# annotate 的临时缓存写系统 TEMP 时可能被拒绝；统一切到工作区临时目录
$tmpRoot = Join-Path $root '_ui-out\tmp'
New-Item -ItemType Directory -Force -Path $tmpRoot | Out-Null
$env:TMP = $tmpRoot
$env:TEMP = $tmpRoot

# 输出目录：默认按时间戳在工作区 _ui-out 下新建，避免覆盖上一轮证据
if (-not $OutDir) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    if ($Serial) { $stamp += '-' + $Serial }
    $OutDir = Join-Path $root "_ui-out\$stamp"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# 为命令行参数补引号，参数本身含空格时保持整体传递
function Quote-Arg {
    param([string]$Text)
    if ($Text -match '[\s"]') { return '"' + ($Text -replace '"', '\"') + '"' }
    return $Text
}

# 调用 android CLI 一次：单字符串传参，等待退出，超时强制结束
# 直接 & 调用在写出文件后可能长期驻留，因此统一用 Start-Process 并设置超时
function Invoke-Cli {
    param([string]$Arguments)
    $outFile = Join-Path $env:TEMP ('android-cli-' + [guid]::NewGuid().ToString('N') + '.out')
    $errFile = Join-Path $env:TEMP ('android-cli-' + [guid]::NewGuid().ToString('N') + '.err')
    $proc = Start-Process -FilePath $cli -ArgumentList ('--no-metrics ' + $Arguments) -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile
    $exited = $proc.WaitForExit($TimeoutSec * 1000)
    if (-not $exited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        Remove-Item $outFile, $errFile -Force -ErrorAction SilentlyContinue
        throw "android CLI 超时 ${TimeoutSec}s：$Arguments"
    }
    $stdout = Get-Content -LiteralPath $outFile -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    $stderr = Get-Content -LiteralPath $errFile -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    Remove-Item $outFile, $errFile -Force -ErrorAction SilentlyContinue
    # CLI 以 native 进程运行，部分版本无法取到退出码，仅在取到非零退出码时判失败
    if ($null -ne $proc.ExitCode -and $proc.ExitCode -ne 0) {
        throw "android CLI 失败（退出码 $($proc.ExitCode)）：$Arguments`n$stderr"
    }
    return $stdout
}

# 执行一次 adb 命令并返回标准输出，失败时抛出
# adb 的 daemon 启动提示写到 stderr，PowerShell 5.1 会把它当作错误记录，这里统一过滤
function Invoke-Adb {
    param([string[]]$CommandArgs)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $adb -s $script:DeviceSerial @CommandArgs 2>&1
        $code = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $previous }
    $lines = @($output | ForEach-Object { "$_" } | Where-Object { $_ -notmatch 'daemon not running|starting now at tcp' })
    if ($code -ne 0) { throw ("adb 执行失败：{0}`n{1}" -f ($CommandArgs -join ' '), ($lines -join "`n")) }
    return $lines
}

# 解析设备序列号：显式指定优先，否则取唯一在线设备，多台时报错
function Resolve-Device {
    if ($Serial) { return $Serial }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $lines = & $adb devices 2>&1 | ForEach-Object { "$_" } | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' } }
    finally { $ErrorActionPreference = $previous }
    $serials = @($lines | ForEach-Object { ($_ -split '\s+')[0] })
    if ($serials.Count -eq 0) { throw '未检测到已连接设备，先确认 adb 与设备状态' }
    if ($serials.Count -gt 1) { throw "检测到多台设备（$($serials -join ', ')），请用 -Serial 指定" }
    return $serials[0]
}

# 读取 layout 输出的 JSON，返回顶层窗口数组
function Get-Layout {
    $jsonFile = Join-Path $OutDir 'layout.json'
    $arguments = 'layout --device=' + (Quote-Arg $script:DeviceSerial)
    if ($Full) { $arguments += ' --full' }
    if ($NoIdle) { $arguments += ' --no-idle' }
    $arguments += ' -o ' + (Quote-Arg $jsonFile)
    Invoke-Cli $arguments | Out-Null
    if (-not (Test-Path -LiteralPath $jsonFile)) { throw "layout 未写出 JSON：$jsonFile" }
    $raw = Get-Content -LiteralPath $jsonFile -Raw -Encoding UTF8
    return $raw | ConvertFrom-Json
}

# 递归展开节点：默认模式子节点键为 content，--full 模式为 children
function Get-AllNodes {
    param($Nodes)
    $collected = New-Object System.Collections.ArrayList
    foreach ($node in @($Nodes)) {
        if ($null -eq $node) { continue }
        [void]$collected.Add($node)
        foreach ($childKey in @('content', 'children')) {
            $childNodes = $node.$childKey
            if ($childNodes) {
                foreach ($child in (Get-AllNodes $childNodes)) { [void]$collected.Add($child) }
            }
        }
    }
    return , $collected
}

# 判断节点是否声明可点击
function Test-Clickable {
    param($Node)
    return (@($Node.interactions) -contains 'CLICKABLE')
}

# 将节点坐标转成可读文本
function Format-Center {
    param($Node)
    if ($null -eq $Node.center) { return '' }
    return ("$($Node.center)")
}

# 取节点中心坐标：center 字段是 "[x,y]" 字符串，缺失时按 bounds 计算
function Get-CenterPair {
    param($Node)
    if ($Node.center) {
        $text = "$($Node.center)"
        if ($text -match '(-?\d+)\D+(-?\d+)') { return @([int]$matches[1], [int]$matches[2]) }
    }
    if ($Node.bounds -match '\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]') {
        $x1 = [int]$matches[1]; $y1 = [int]$matches[2]; $x2 = [int]$matches[3]; $y2 = [int]$matches[4]
        return @([int](($x1 + $x2) / 2), [int](($y1 + $y2) / 2))
    }
    return $null
}

# 动作：layout，输出节点统计并打印 JSON 文件位置
function Invoke-LayoutAction {
    $windows = Get-Layout
    $nodes = Get-AllNodes $windows
    $textNodes = @($nodes | Where-Object { $_.text })
    $clickableNodes = @($nodes | Where-Object { Test-Clickable $_ })
    Write-Host "布局文件   $(Join-Path $OutDir 'layout.json')"
    Write-Host "窗口数     $(@($windows).Count)"
    Write-Host "节点数     $($nodes.Count)"
    Write-Host "文本节点   $($textNodes.Count)"
    Write-Host "可点击节点 $($clickableNodes.Count)"
    foreach ($node in $textNodes) {
        Write-Host ("  文本 {0}  bounds {1}  center {2}" -f $node.text, ($node.bounds -join ','), (Format-Center $node))
    }
}

# 动作：annotate，截图并标注元素编号
function Invoke-AnnotateAction {
    $png = Join-Path $OutDir 'screen-annotated.png'
    $arguments = 'screen capture --device=' + (Quote-Arg $script:DeviceSerial) + ' --annotate --output=' + (Quote-Arg $png)
    Invoke-Cli $arguments | Out-Null
    if (-not (Test-Path -LiteralPath $png)) { throw "截图未写出：$png" }
    Add-Type -AssemblyName System.Drawing
    $image = [System.Drawing.Image]::FromFile($png)
    try {
        Write-Host "标注截图   $png"
        Write-Host "尺寸       $($image.Width)x$($image.Height)"
    }
    finally { $image.Dispose() }
    return $png
}

# 取用于 resolve 的标注截图：显式路径优先，其次本目录最新标注图
function Get-AnnotatedScreenshot {
    if ($Screenshot) {
        if (-not (Test-Path -LiteralPath $Screenshot)) { throw "标注截图不存在：$Screenshot" }
        return (Resolve-Path -LiteralPath $Screenshot).Path
    }
    $latest = Get-ChildItem -LiteralPath $OutDir -Filter '*-annotated.png' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) { return (Invoke-AnnotateAction) }
    return $latest.FullName
}
function Invoke-ResolveAction {
    if (-not $Labels) { throw 'resolve 需要 -Labels，例如 -Labels "#1 #26"' }
    $png = Get-AnnotatedScreenshot
    $output = Invoke-Cli ('screen resolve --screenshot=' + (Quote-Arg $png) + ' --string=' + (Quote-Arg $Labels))
    Write-Host "标注截图   $png"
    Write-Host "解析结果   $($output.Trim())"
    return $output.Trim()
}

# 从 resolve 输出中取出第一组点击坐标
function Get-TapCoordinate {
    param([string]$Resolved)
    if ($Resolved -match 'tap\s+(\d+)\s+(\d+)') { return @([int]$matches[1], [int]$matches[2]) }
    if ($Resolved -match '^\s*(\d+)\s+(\d+)') { return @([int]$matches[1], [int]$matches[2]) }
    throw "无法从 resolve 结果解析坐标：$Resolved"
}

# 部分墨水屏的截图固定为面板原生方向，input 使用逻辑方向；只在两者宽高互换时换算。
function Convert-ScreenshotPoint {
    param([int[]]$Point, [string]$Png)
    $display = (Invoke-Adb -CommandArgs @('shell', 'dumpsys', 'input')) -join "`n"
    if ($display -notmatch 'Viewport INTERNAL: displayId=0,[^\r\n]*orientation=(\d), logicalFrame=\[0, 0, (\d+), (\d+)\]') {
        throw '无法核实截图与输入坐标的显示方向'
    }
    $rotation = [int]$matches[1]
    $logicalWidth = [int]$matches[2]
    $logicalHeight = [int]$matches[3]
    Add-Type -AssemblyName System.Drawing
    $picture = [System.Drawing.Image]::FromFile($Png)
    try { $pw = $picture.Width; $ph = $picture.Height } finally { $picture.Dispose() }
    if ($pw -eq $logicalWidth -and $ph -eq $logicalHeight) { return $Point }
    if ($pw -ne $logicalHeight -or $ph -ne $logicalWidth) { throw '截图尺寸与当前显示不一致，请重新 annotate' }
    switch ($rotation) {
        1 { return @($Point[1], ($pw - 1 - $Point[0])) }
        3 { return @(($ph - 1 - $Point[1]), $Point[0]) }
        default { throw '截图方向无法匹配当前显示，请重新 annotate' }
    }
}

# 动作：tap，按标号或选择器点击
function Invoke-TapAction {
    if ($Label) {
        $png = Get-AnnotatedScreenshot
        $resolved = Invoke-Cli ('screen resolve --screenshot=' + (Quote-Arg $png) + ' --string=' + (Quote-Arg $Label))
        $coordinate = Convert-ScreenshotPoint -Point (Get-TapCoordinate $resolved.Trim()) -Png $png
        if ($HoldMs -gt 0) {
            Invoke-Adb -CommandArgs @('shell', 'input', 'swipe', "$($coordinate[0])", "$($coordinate[1])", "$($coordinate[0])", "$($coordinate[1])", "$HoldMs") | Out-Null
        } else {
            Invoke-Adb -CommandArgs @('shell', 'input', 'tap', "$($coordinate[0])", "$($coordinate[1])") | Out-Null
        }
        Write-Host "点击标号   $Label -> $($coordinate -join ',')（截图 $png）"
        return
    }
    if ($Selector) {
        if ($HoldMs -gt 0) { throw "长按请使用当前截图的 -Label" }
        Invoke-Cli ('input tap --device=' + (Quote-Arg $script:DeviceSerial) + ' --match=first ' + (Quote-Arg $Selector)) | Out-Null
        Write-Host "点击选择器 $Selector"
        return
    }
    if ($FindText) {
        if ($HoldMs -gt 0) { throw "长按请使用当前截图的 -Label" }
        $node = Find-NodeByText $FindText
        if (-not $node) { throw ('未找到文本 ' + $FindText + '；自绘元素不在 accessibility 树内，改用 -Action annotate 与 -Action tap -Label') }
        $center = Get-CenterPair $node
        if (-not $center) { throw ('节点缺少可用坐标：' + $FindText) }
        Invoke-Adb -CommandArgs @('shell', 'input', 'tap', "$($center[0])", "$($center[1])") | Out-Null
        Write-Host "点击文本   $FindText -> $($center -join ',')"
        return
    }
    throw 'tap 需要 -Label、-Selector 或 -FindText'
}

# 按文本查找节点：优先精确匹配，其次忽略大小写包含
function Find-NodeByText {
    param([string]$Text)
    $windows = Get-Layout
    $nodes = Get-AllNodes $windows
    $exact = @($nodes | Where-Object { $_.text -eq $Text })
    if ($exact.Count -gt 0) { return $exact[0] }
    $contains = @($nodes | Where-Object { $_.text -and $_.text.ToLower().Contains($Text.ToLower()) })
    if ($contains.Count -gt 0) { return $contains[0] }
    return $null
}

# 动作：find，按文本输出节点位置，不执行点击
function Invoke-FindAction {
    if (-not $FindText) { throw 'find 需要 -FindText' }
    $node = Find-NodeByText $FindText
    if (-not $node) {
        Write-Host ('未找到文本 ' + $FindText + '。自绘元素不在 accessibility 树内，改用 -Action annotate 查看编号。')
        return
    }
    Write-Host ("文本      {0}" -f $node.text)
    Write-Host ("bounds    {0}" -f ($node.bounds -join ','))
    Write-Host ("center    {0}" -f (Format-Center $node))
    Write-Host ("可点击    {0}" -f (Test-Clickable $node))
}

# 动作：key，发送按键；名称或 Android keycode 均可
function Invoke-KeyAction {
    if (-not $Key) { throw 'key 需要 -Key，例如 back' }
    # 按键无需UiAutomation；避免其持久instrumentation临时抑制被测无障碍服务。
    $keyCode = if ($Key -match '^\d+$' -or $Key -match '^KEYCODE_') { $Key } else { 'KEYCODE_' + $Key.ToUpperInvariant() }
    Invoke-Adb -CommandArgs @('shell', 'input', 'keyevent', $keyCode) | Out-Null
    Write-Host "按键      $Key"
}

# 动作：text，向标准 View 输入文本
function Invoke-TextAction {
    if (-not $Value) { throw 'text 需要 -Value' }
    if (-not $Selector) { throw 'text 需要 -Selector，例如 "class=android.widget.EditText"' }
    Invoke-Cli ('input text --device=' + (Quote-Arg $script:DeviceSerial) + ' ' + (Quote-Arg $Value) + ' ' + (Quote-Arg $Selector)) | Out-Null
    Write-Host "输入文本  $Value"
}

# 动作：swipe，按当前标注截图解析两端点
function Invoke-SwipeAction {
    if (-not $Label -or -not $EndLabel) { throw 'swipe 需要当前截图的 -Label 和 -EndLabel' }
    $png = Get-AnnotatedScreenshot
    $startResolved = Invoke-Cli ('screen resolve --screenshot=' + (Quote-Arg $png) + ' --string=' + (Quote-Arg $Label))
    $endResolved = Invoke-Cli ('screen resolve --screenshot=' + (Quote-Arg $png) + ' --string=' + (Quote-Arg $EndLabel))
    $startPoint = Convert-ScreenshotPoint -Point (Get-TapCoordinate $startResolved.Trim()) -Png $png
    $endPoint = Convert-ScreenshotPoint -Point (Get-TapCoordinate $endResolved.Trim()) -Png $png
    Invoke-Adb -CommandArgs @('shell', 'input', 'swipe', "$($startPoint[0])", "$($startPoint[1])", "$($endPoint[0])", "$($endPoint[1])", "$SwipeMs") | Out-Null
    Write-Host "滑动标号   $Label -> $EndLabel（截图 $png）"
}

# 动作：raw，原样透传 CLI 参数，便于排查新子命令
function Invoke-RawAction {
    if (-not $RawArgs) { throw 'raw 需要 -RawArgs' }
    Write-Host (Invoke-Cli $RawArgs)
}

# 系统顶部边缘诊断；不是应用元素导航。每次从设备读取逻辑显示尺寸与旋转。
function Invoke-EdgeSwipeAction {
    $sizeText = (Invoke-Adb -CommandArgs @('shell', 'wm', 'size')) -join "`n"
    $sizes = [regex]::Matches($sizeText, '(?:Physical|Override) size:\s*(\d+)x(\d+)')
    if ($sizes.Count -eq 0) { throw '无法确定屏幕尺寸' }
    $size = $sizes[$sizes.Count - 1]
    $width = [int]$size.Groups[1].Value
    $height = [int]$size.Groups[2].Value
    $display = (Invoke-Adb -CommandArgs @('shell', 'dumpsys', 'input')) -join "`n"
    $rotation = [regex]::Match($display, 'SurfaceOrientation:\s*(\d+)')
    if (-not $rotation.Success) { throw '无法确定输入旋转' }
    if ([int]$rotation.Groups[1].Value -in @(1, 3)) {
        $swap = $width; $width = $height; $height = $swap
    }
    $x = [int]($width / 2)
    $endY = [int]($height * 0.8)
    Invoke-Adb -CommandArgs @('shell', 'input', 'swipe', "$x", "$EdgeStartY", "$x", "$endY", "$SwipeMs") | Out-Null
    Write-Host "系统边缘滑动 ($x,$EdgeStartY)->($x,$endY), logical=${width}x${height}, rotation=$($rotation.Groups[1].Value)"
}

$script:DeviceSerial = Resolve-Device

switch ($Action) {
    'layout' { Invoke-LayoutAction }
    'annotate' { Invoke-AnnotateAction | Out-Null }
    'resolve' { Invoke-ResolveAction | Out-Null }
    'tap' { Invoke-TapAction }
    'swipe' { Invoke-SwipeAction }
    'edge-swipe' { Invoke-EdgeSwipeAction }
    'find' { Invoke-FindAction }
    'click' { Invoke-TapAction }
    'key' { Invoke-KeyAction }
    'text' { Invoke-TextAction }
    'raw' { Invoke-RawAction }
}
