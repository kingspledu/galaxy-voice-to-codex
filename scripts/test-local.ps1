$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\invoke-gradle.ps1" -GradleArguments @('testDebugUnitTest', '--console', 'plain')
