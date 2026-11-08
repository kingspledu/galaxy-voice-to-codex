$ErrorActionPreference = 'Stop'
$ruleName = 'Galaxy Voice to Codex (Private LAN)'
$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if (-not $existing) {
  New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -Profile Private | Out-Null
  Write-Host 'Windows 방화벽에 개인 네트워크용 8765 포트를 허용했습니다.'
} else {
  Write-Host 'Windows 방화벽 규칙이 이미 있습니다.'
}
