param(
    [Parameter(Mandatory = $true)][string]$Tag,
    [string]$Root = (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)),
    [string]$DestBase = 'D:\DSHStation\_snapshots',
    [int]$Keep = 4
)

<#
生成十月桌面的版本快照。

参数含义：
  Tag      快照标签，形如 0.9.1，决定目标目录名 OctoberLauncher-0.9.1。
  Root     工作副本根目录，默认为脚本所在工程目录。
  DestBase 快照存放的父目录，默认为 D:\DSHStation\_snapshots。
  Keep     保留的快照份数，超出的旧快照移入 archive 子目录。

行为说明：
  复制工作副本到目标目录，排除 build、.gradle、.kotlin、.idea 四类构建与缓存目录；
  逐文件计算 SHA-256 写入 MANIFEST.sha256；
  按目录修改时间排序，把超出保留份数的旧快照移入 archive。
  退出码 0 表示快照生成并通过清单自检，非 0 表示失败。
#>

$ErrorActionPreference = 'Stop'

if ($Tag -notmatch '^\d+\.\d+(\.\d+)?(-[A-Za-z0-9.]+)?$') {
    Write-Error "标签格式不合法: $Tag"
    exit 2
}

$rootFull = [System.IO.Path]::GetFullPath($Root)
if (-not (Test-Path (Join-Path $rootFull 'settings.gradle.kts'))) {
    Write-Error "根目录缺少 settings.gradle.kts，确认是否为工程目录: $rootFull"
    exit 2
}

New-Item -ItemType Directory -Path $DestBase -Force | Out-Null
$dest = Join-Path $DestBase "OctoberLauncher-$Tag"
if (Test-Path $dest) {
    Write-Error "目标快照已存在: $dest"
    exit 2
}
New-Item -ItemType Directory -Path $dest -Force | Out-Null

# 复制工作副本，排除构建输出与缓存
$null = robocopy $rootFull $dest /E /XD build .gradle .kotlin '.idea' /XF '*.hprof' /R:1 /W:1 /NFL /NDL /NP /NJH /NJS
if ($LASTEXITCODE -ge 8) {
    Write-Error "复制失败，robocopy 退出码 $LASTEXITCODE"
    exit 3
}

# 生成清单，记录每个文件的摘要与相对位置
$manifest = Join-Path $dest 'MANIFEST.sha256'
$lines = Get-ChildItem -Recurse -File -Force -Path $dest |
    Where-Object { $_.Name -ne 'MANIFEST.sha256' } |
    ForEach-Object {
        $rel = $_.FullName.Substring($dest.Length).TrimStart('\')
        $hash = (Get-FileHash -Algorithm SHA256 -Path $_.FullName).Hash.ToLower()
        "$hash  $rel"
    }
Set-Content -Path $manifest -Value $lines -Encoding UTF8

# 清单自检：重新计算清单摘要并与文件规模比对
$fileCount = (Get-ChildItem -Recurse -File -Force -Path $dest | Where-Object { $_.Name -ne 'MANIFEST.sha256' }).Count
if ($fileCount -ne $lines.Count) {
    Write-Error "清单行数 $($lines.Count) 与文件数 $fileCount 不一致"
    exit 4
}
$manifestHash = (Get-FileHash -Algorithm SHA256 -Path $manifest).Hash.ToLower()

# 保留最近 Keep 份，其余移入 archive
$archive = Join-Path $DestBase 'archive'
New-Item -ItemType Directory -Path $archive -Force | Out-Null
$old = Get-ChildItem -Directory -Path $DestBase | Where-Object { $_.Name -like 'OctoberLauncher-*' } |
    Sort-Object LastWriteTime -Descending | Select-Object -Skip $Keep
foreach ($item in $old) {
    Move-Item -Path $item.FullName -Destination (Join-Path $archive $item.Name) -Force
    Write-Host "已归档: $($item.Name)"
}

Write-Host "快照完成: $dest"
Write-Host "文件数: $fileCount"
Write-Host "清单摘要: $manifestHash"
exit 0
