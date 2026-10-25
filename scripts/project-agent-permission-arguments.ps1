# Return one TOML override: CLI dotted-key parsing must not split the literal "." rule.
$policyText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'agent-permissions.toml') -Raw
$profiles = foreach ($profileName in @('dsh-code', 'dsh-docs')) {
    $escaped = [regex]::Escape($profileName)
    $header = [regex]::Match($policyText, "(?ms)^\[permissions\.$escaped\]\s*(.*?)(?=^\[|\z)").Groups[1].Value
    $rules = [regex]::Match($policyText, "(?ms)^\[permissions\.$escaped\.filesystem\."":workspace_roots""\]\s*(.*?)(?=^\[|\z)").Groups[1].Value
    $headerLines = @($header -split '\r?\n' | Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('#') })
    $ruleLines = @($rules -split '\r?\n' | Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('#') })
    if (!$headerLines.Count -or !$ruleLines.Count) { throw "Missing role policy: $profileName" }
    '"' + $profileName + '"={' + ($headerLines -join ',') + ',filesystem={":workspace_roots"={' + ($ruleLines -join ',') + '}}}'
}
@('-c', ('permissions={' + ($profiles -join ',') + '}'))
