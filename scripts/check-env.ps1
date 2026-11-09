$ErrorActionPreference = 'Continue'
. "$PSScriptRoot\env.ps1"
Write-Host 'YouTube Voice Marker Android environment check'
if (Get-Command java -ErrorAction SilentlyContinue) { java -version } else { Write-Host 'JDK: MISSING' -ForegroundColor Yellow }
if (Get-Command adb -ErrorAction SilentlyContinue) { adb version } else { Write-Host 'ADB: MISSING' -ForegroundColor Yellow }
if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { Write-Host "Android SDK: $env:ANDROID_SDK_ROOT" } else { Write-Host 'ANDROID_SDK_ROOT: MISSING' -ForegroundColor Yellow }
if ($env:ANDROID_SDK_ROOT -and (Test-Path "$env:ANDROID_SDK_ROOT\ndk\27.2.12479018\source.properties")) { Write-Host 'Android NDK 27.2.12479018: FOUND' } else { Write-Host 'Android NDK 27.2.12479018: MISSING' -ForegroundColor Yellow }
if ($env:ANDROID_SDK_ROOT -and (Test-Path "$env:ANDROID_SDK_ROOT\cmake\3.22.1\bin\cmake.exe")) { Write-Host 'CMake 3.22.1: FOUND' } else { Write-Host 'CMake 3.22.1: MISSING' -ForegroundColor Yellow }
if (Test-Path "$PSScriptRoot\..\gradlew.bat") { Write-Host 'Gradle Wrapper: present' } else { Write-Host 'Gradle Wrapper: not generated yet' -ForegroundColor Yellow }
