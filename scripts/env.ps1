$projectRoot = Split-Path $PSScriptRoot -Parent
$localJdk = Get-ChildItem "$projectRoot\.tools\jdk" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
if (Test-Path "$projectRoot\.tools\android-sdk") {
    $env:ANDROID_HOME = "$projectRoot\.tools\android-sdk"
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
}
$env:GRADLE_USER_HOME = "$projectRoot\.gradle-user-home"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
