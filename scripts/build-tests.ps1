# Build + unit tests for this host. Run from the repo root or anywhere; it locates itself.
# Usage: powershell -NoProfile -File scripts/build-tests.ps1 [-Task <gradle task>]

param(
    [string]$Task = ":app:testDebugUnitTest",
    [string]$Log = "build.log"
)

$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$env:JAVA_HOME = 'E:\Dev\jdk\jdk-17.0.20+8'
$env:GRADLE_USER_HOME = 'E:\Dev\.gradle'
$env:ANDROID_HOME = 'E:\Dev\android-sdk'

if (Test-Path $Log) { Remove-Item -Force $Log -ErrorAction SilentlyContinue }

& "$root\gradlew.bat" $Task --no-daemon --max-workers=1 --console=plain *> $Log
$code = $LASTEXITCODE
"EXIT=$code" | Out-File -Append -FilePath $Log
Write-Output "EXIT=$code"
