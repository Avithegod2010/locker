# Build + unit tests for this host.
# Usage: powershell -NoProfile -File scripts/build-tests.ps1 [-Task <gradle task>] [-Log <file>]
#
# The host toolchain moved to C:\Dev; E:\Dev is kept as a fallback so older notes still work.
# Gradle metadata still lives in E:\Dev\.gradle, which avoids a cold dependency resolve.

param(
    [string]$Task = ":app:testDebugUnitTest",
    [string]$Log = "build.log"
)

$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

function Resolve-Jdk {
    foreach ($candidate in @(
        'C:\Dev\jdk\jdk-17.0.20+8',
        'E:\Dev\jdk\jdk-17.0.20+8'
    )) {
        if (Test-Path (Join-Path $candidate 'bin\java.exe')) { return $candidate }
    }
    throw 'No JDK 17 found in C:\Dev or E:\Dev'
}

function Resolve-Sdk {
    foreach ($candidate in @('C:\Dev\android-sdk', 'E:\Dev\android-sdk')) {
        if (Test-Path (Join-Path $candidate 'platform-tools')) { return $candidate }
    }
    throw 'No Android SDK found in C:\Dev or E:\Dev'
}

$env:JAVA_HOME = Resolve-Jdk
$env:ANDROID_HOME = Resolve-Sdk
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = if (Test-Path 'C:\Dev\.gradle\caches') { 'C:\Dev\.gradle' }
    elseif (Test-Path 'E:\Dev\.gradle\caches') { 'E:\Dev\.gradle' }
    else { Join-Path $env:USERPROFILE '.gradle' }

Write-Output "JAVA_HOME=$env:JAVA_HOME"
Write-Output "ANDROID_HOME=$env:ANDROID_HOME"
Write-Output "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"

if (Test-Path $Log) { Remove-Item -Force $Log -ErrorAction SilentlyContinue }

& "$root\gradlew.bat" $Task --no-daemon --max-workers=1 --console=plain *> $Log
$code = $LASTEXITCODE
"EXIT=$code" | Out-File -Append -FilePath $Log
Write-Output "EXIT=$code"
