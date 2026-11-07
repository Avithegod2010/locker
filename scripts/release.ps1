param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$NotesFile,
    [switch]$SkipTests,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Invoke-Step([string]$Label, [scriptblock]$Action) {
    Write-Host "==> $Label" -ForegroundColor Cyan
    & $Action
}

if (git status --porcelain) {
    throw "The working tree is not clean. Commit or stash your changes before releasing."
}

$gradleFile = "app/build.gradle.kts"
$gradle = Get-Content $gradleFile -Raw
$codeMatch = [regex]::Match($gradle, "versionCode\s*=\s*(\d+)")
$nameMatch = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"')
if (-not $codeMatch.Success -or -not $nameMatch.Success) {
    throw "Could not read versionCode/versionName from $gradleFile"
}
$versionCode = [int]$codeMatch.Groups[1].Value
$versionName = $nameMatch.Groups[1].Value

if (-not (Test-Path $NotesFile)) {
    throw "Release notes not found: $NotesFile"
}
if (git tag --list "v$Version") {
    throw "Tag v$Version already exists."
}

if ($versionName -ne $Version) {
    $nextCode = $versionCode + 1
    $gradle = $gradle -replace "versionCode\s*=\s*\d+", "versionCode = $nextCode"
    $gradle = $gradle -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$Version`""
    Set-Content -Path $gradleFile -Value $gradle -NoNewline
    Write-Host "Bumped version to $Version (versionCode $nextCode)." -ForegroundColor Yellow
} else {
    $nextCode = $versionCode
    Write-Host "Version is already $Version (versionCode $versionCode)."
}

if (-not $SkipTests) {
    Invoke-Step "Running unit tests" {
        & .\gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1 --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Unit tests failed." }
    }
}

Invoke-Step "Building signed release APK" {
    & .\gradlew.bat :app:assembleRelease --no-daemon --max-workers=1 --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Release build failed." }
}

$apk = "app/build/outputs/apk/release/app-release.apk"
if (-not (Test-Path $apk)) { throw "APK not found at $apk" }
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $apk).Length

$notes = Get-Content $NotesFile -Raw
if ($notes -match "\{\{SHA256\}\}") {
    $notes = $notes.Replace("{{SHA256}}", $hash)
} elseif ($notes -notmatch "APK SHA-256") {
    $notes = $notes.TrimEnd() + "`n`nAPK SHA-256: ``$hash``" + "`n"
}
Set-Content -Path $NotesFile -Value $notes -NoNewline

$assetName = "Locker-$Version-release.apk"
$assetPath = Join-Path $env:TEMP $assetName
Copy-Item $apk $assetPath -Force

Write-Host ""
Write-Host "APK:    $assetName ($size bytes)" -ForegroundColor Green
Write-Host "SHA256: $hash" -ForegroundColor Green
Write-Host ""

if ($DryRun) {
    Write-Host "Dry run: stopping before commit, tag, and publish." -ForegroundColor Yellow
    exit 0
}

Invoke-Step "Committing release" {
    git add -A
    git commit -m "Release $Version"
    if ($LASTEXITCODE -ne 0) { throw "Git commit failed." }
}
Invoke-Step "Tagging v$Version" {
    git tag -a "v$Version" -m "Locker $Version"
    if ($LASTEXITCODE -ne 0) { throw "Git tag failed." }
}
Invoke-Step "Pushing main and tag" {
    git push origin main
    if ($LASTEXITCODE -ne 0) { throw "git push failed." }
    git push origin "v$Version"
    if ($LASTEXITCODE -ne 0) { throw "git push tag failed." }
}
Invoke-Step "Publishing GitHub release" {
    gh release create "v$Version" "$assetPath" --title "Locker $Version" --notes-file $NotesFile
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed." }
}

Write-Host ""
Write-Host "Released Locker $Version" -ForegroundColor Green
Write-Host "SHA256: $hash"
