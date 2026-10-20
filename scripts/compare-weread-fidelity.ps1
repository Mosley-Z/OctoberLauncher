<#
.SYNOPSIS
    比对微读墨记原版与十雨桌面内核渲染的截图差异。

.DESCRIPTION
    按文件名主体配对两张截图，逐像素比较灰度通道，输出差异像素比例、最大通道偏差
    与差异掩膜图。用于验证桌面整体卡片五种形态与负一屏三个墨台分区的保真度。

    配对规则：去掉前缀 base- 与 new- 后主体相同、扩展名同为 png 的文件视为一对。
    尺寸不同时先把候选图缩放到基线尺寸再比较，尺寸差异单独记录。

.PARAMETER Baseline
    基线截图目录，存放微读墨记原版渲染结果。

.PARAMETER Candidate
    候选截图目录，存放十雨桌面内核渲染结果。

.PARAMETER OutputDirectory
    差异掩膜与结果文件的输出目录，默认为 _analysis\fidelity。

.PARAMETER ChannelThreshold
    判定单个像素发生变化所需的通道偏差，默认 8，用于滤掉字体抗锯齿的细微差异。

.PARAMETER PythonPath
    Python 解释器路径，需具备 Pillow。默认取 D:\Enviroment\Python\python.exe。
#>
param(
    [Parameter(Mandatory = $true)][string]$Baseline,
    [Parameter(Mandatory = $true)][string]$Candidate,
    [string]$OutputDirectory = '_analysis\fidelity',
    [int]$ChannelThreshold = 8,
    [string]$PythonPath = 'D:\Enviroment\Python\python.exe'
)

$ErrorActionPreference = 'Stop'

# 解析相对路径，保证脚本在任意当前目录下执行时结果一致
$root = Split-Path -Parent $PSScriptRoot
$baseDir = if ([System.IO.Path]::IsPathRooted($Baseline)) { $Baseline } else { Join-Path $root $Baseline }
$candidateDir = if ([System.IO.Path]::IsPathRooted($Candidate)) { $Candidate } else { Join-Path $root $Candidate }
$outputDir = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory } else { Join-Path $root $OutputDirectory }

if (-not (Test-Path -LiteralPath $baseDir)) {
    throw "基线目录不存在：$baseDir"
}
if (-not (Test-Path -LiteralPath $candidateDir)) {
    throw "候选目录不存在：$candidateDir"
}
if (-not (Test-Path -LiteralPath $PythonPath)) {
    throw "Python 解释器不存在：$PythonPath"
}
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

# 收集待配对文件，键为去掉前缀后的文件名主体
$pattern = '^(base|new)-(.+)$'
function Get-Pairs {
    param([string]$Directory)

    $table = @{}
    foreach ($file in Get-ChildItem -LiteralPath $Directory -Filter '*.png') {
        $stem = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
        if ($stem -match $pattern) {
            $stem = $Matches[2]
        }
        $table[$stem] = $file.FullName
    }

    return $table
}

$baseFiles = Get-Pairs -Directory $baseDir
$candidateFiles = Get-Pairs -Directory $candidateDir
$common = @($baseFiles.Keys | Where-Object { $candidateFiles.ContainsKey($_) } | Sort-Object)
$onlyBase = @($baseFiles.Keys | Where-Object { -not $candidateFiles.ContainsKey($_) } | Sort-Object)
$onlyCandidate = @($candidateFiles.Keys | Where-Object { -not $baseFiles.ContainsKey($_) } | Sort-Object)

if ($common.Count -eq 0) {
    throw "两个目录没有可配对的截图，请检查命名是否为 <主体>.png 或 base-<主体>.png 与 new-<主体>.png"
}

# 生成 Python 比较脚本，避免在命令行拼接长代码
$python = Join-Path $outputDir 'compare.py'
$pythonBody = @'
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops


def main() -> int:
    request = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    threshold = int(request["threshold"])
    output_dir = Path(request["output"])
    results = []

    for item in request["pairs"]:
        name = item["name"]
        base = Image.open(item["baseline"]).convert("RGB")
        candidate = Image.open(item["candidate"]).convert("RGB")
        scaled = candidate.size != base.size
        if scaled:
            candidate = candidate.resize(base.size, Image.LANCZOS)

        diff = ImageChops.difference(base, candidate)
        gray = diff.convert("L")
        histogram = gray.histogram()
        total = base.size[0] * base.size[1]
        changed = sum(histogram[threshold + 1:])
        ratio = changed / total if total else 0.0

        mask = gray.point(lambda value: 255 if value > threshold else 0)
        mask_path = output_dir / f"diff-{name}.png"
        mask.save(mask_path)

        results.append(
            {
                "name": name,
                "baseline": str(item["baseline"]),
                "candidate": str(item["candidate"]),
                "size": [base.size[0], base.size[1]],
                "scaled": scaled,
                "changed": changed,
                "total": total,
                "ratio": round(ratio, 6),
                "maxDelta": max((i for i, count in enumerate(histogram) if count), default=0),
                "mask": str(mask_path),
            }
        )

    Path(request["report"]).write_text(
        json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
'@
Set-Content -LiteralPath $python -Value $pythonBody -Encoding utf8

# 组装比较请求
$pairs = @(
    foreach ($name in $common) {
        [ordered]@{
            name = $name
            baseline = $baseFiles[$name]
            candidate = $candidateFiles[$name]
        }
    }
)
$requestPath = Join-Path $outputDir 'request.json'
$reportPath = Join-Path $outputDir 'result.json'
$request = [ordered]@{
    threshold = $ChannelThreshold
    output = $outputDir
    report = $reportPath
    pairs = $pairs
}
$request | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $requestPath -Encoding utf8

& $PythonPath $python $requestPath
if ($LASTEXITCODE -ne 0) {
    throw "像素比较失败，退出码 $LASTEXITCODE"
}

# 汇总为可读文本
$rows = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
$lines = @()
$lines += "微读墨记保真度比对"
$lines += "基线目录：$baseDir"
$lines += "候选目录：$candidateDir"
$lines += "通道阈值：$ChannelThreshold"
$lines += ''
$lines += ('{0,-12} {1,-12} {2,10} {3,10} {4,10}' -f '主体', '尺寸', '差异像素', '总像素', '差异比例')
$failed = 0
foreach ($row in $rows) {
    $size = "$($row.size[0])x$($row.size[1])"
    $lines += ('{0,-12} {1,-12} {2,10} {3,10} {4,10}' -f $row.name, $size, $row.changed, $row.total, $row.ratio)
    if ($row.ratio -gt 0.02) {
        $failed++
    }
}
$lines += ''
$lines += "配对成功 $($common.Count) 组，仅有基线 $($onlyBase.Count) 组，仅有候选 $($onlyCandidate.Count) 组"
if ($onlyBase.Count -gt 0) {
    $lines += "仅有基线：$($onlyBase -join '、')"
}
if ($onlyCandidate.Count -gt 0) {
    $lines += "仅有候选：$($onlyCandidate -join '、')"
}
$lines += "差异比例超过 2% 的组：$failed"
$lines += '结果判定：' + $(if ($failed -eq 0) { '通过' } else { '需核对' })

$textPath = Join-Path $outputDir 'result.txt'
$lines | Set-Content -LiteralPath $textPath -Encoding utf8
$lines | ForEach-Object { Write-Output $_ }

if ($failed -gt 0) {
    exit 1
}
