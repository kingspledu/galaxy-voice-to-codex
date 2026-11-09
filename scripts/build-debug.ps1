$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\invoke-gradle.ps1" -GradleArguments @('assembleDebug', '--console', 'plain')
