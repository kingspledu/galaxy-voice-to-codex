$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\invoke-gradle.ps1" -GradleArguments @('assembleDebug', 'lintDebug', 'testDebugUnitTest', '--console', 'plain')
