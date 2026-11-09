param([string[]]$GradleArguments = @('assembleDebug', '--console', 'plain'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$workingRoot = $projectRoot
$createdDrive = $null
$proofFile = $null
$pushed = $false
try {
    if ($projectRoot -match '[^\x00-\x7F]') {
        $proofName = 'drive-proof-' + [guid]::NewGuid().ToString('N') + '.tmp'
        $proofFile = Join-Path $projectRoot ".tools\$proofName"
        [IO.Directory]::CreateDirectory((Split-Path $proofFile -Parent)) | Out-Null
        [IO.File]::WriteAllText($proofFile, $proofName)
        $workingRoot = $null
        foreach ($letter in @('W', 'X', 'Y', 'Z', 'R')) {
            $drive = "${letter}:\"
            if (Test-Path $drive) {
                $candidateProof = Join-Path $drive ".tools\$proofName"
                if ((Test-Path -LiteralPath $candidateProof) -and [IO.File]::ReadAllText($candidateProof) -eq $proofName) { $workingRoot = $drive; break }
                continue
            }
            & subst.exe "${letter}:" $projectRoot
            if ($LASTEXITCODE -ne 0) { throw 'Cannot create an ASCII drive alias.' }
            $workingRoot = $drive; $createdDrive = $letter; break
        }
        if (!$workingRoot) { throw 'No unused drive letter available for the native build.' }
    }
    Push-Location $workingRoot; $pushed = $true
    . (Join-Path $workingRoot 'scripts\env.ps1')
    & .\gradlew.bat @GradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally {
    if ($createdDrive -and $pushed) { & .\gradlew.bat --stop *> $null }
    if ($pushed) { Pop-Location }
    if ($createdDrive) { & subst.exe "${createdDrive}:" /D }
    if ($proofFile -and (Test-Path -LiteralPath $proofFile)) { Remove-Item -LiteralPath $proofFile }
}
