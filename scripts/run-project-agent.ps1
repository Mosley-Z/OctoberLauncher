param(
    [Parameter(Mandatory)][ValidateSet('code', 'docs')][string]$Role,
    [Parameter(Mandatory)][string]$PromptFile
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$promptPath = (Resolve-Path -LiteralPath $PromptFile).Path
$promptText = Get-Content -LiteralPath $promptPath -Raw -Encoding UTF8
if ([string]::IsNullOrWhiteSpace($promptText)) { throw 'Empty task prompt.' }
$permissionProfile = if ($Role -eq 'code') { 'dsh-code' } else { 'dsh-docs' }
$configArguments = @(& (Join-Path $PSScriptRoot 'project-agent-permission-arguments.ps1'))
# A separate CLI session selects its own filesystem boundary. Native spawn cannot do this.
# No escalation is allowed inside the worker. Do not add full-access or extra write roots.
$promptText | & codex -C $projectRoot -a never @configArguments -c ('default_permissions="' + $permissionProfile + '"') `
    -m gpt-6.1-sol -c 'model_reasoning_effort="medium"' exec -
if ($LASTEXITCODE -ne 0) { throw "Role worker failed: $LASTEXITCODE" }
