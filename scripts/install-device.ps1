$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $projectRoot '.tools\android-sdk\platform-tools\adb.exe'
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path $adb)) { throw 'ADB is missing. Run scripts/check-env.ps1.' }
if (!(Test-Path $apk)) { throw 'Debug APK is missing. Run scripts/build-debug.ps1.' }
$devices = @(& $adb devices | Select-String "\t(device|unauthorized|offline)$")
if (!$devices) { throw 'No Android device detected. Enable USB debugging and approve the device prompt.' }
$authorized = @($devices | Where-Object { $_.Line -match "\tdevice$" })
if (!$authorized) { throw "ADB device is not authorized or is offline: $($devices -join '; ')" }
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "APK installation failed: $LASTEXITCODE" }
& $adb shell am force-stop com.kuiro.youtubemarker
& $adb shell monkey -p com.kuiro.youtubemarker 1
Write-Host 'Installed and launched com.kuiro.youtubemarker.'
Write-Host 'Next: follow docs/FREE-WHISPER.md and docs/FLOATING-DEVICE-TEST.md.'
