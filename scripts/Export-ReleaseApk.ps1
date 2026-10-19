[CmdletBinding()]
param([string] $SdkPath = $env:ANDROID_HOME)

$ErrorActionPreference = 'Stop'
if (-not $SdkPath) { throw 'Set ANDROID_HOME or pass -SdkPath.' }
$signer = Join-Path $SdkPath 'build-tools\35.0.0\apksigner.bat'
$key = Join-Path $env:USERPROFILE '.android\debug.keystore'
$project = Split-Path -Parent $PSScriptRoot
Push-Location $project
$staged = 'dist\optimized\manaplayer.next.apk'
try {
    function Certificate([string] $Apk) {
        $result = & $signer verify --min-sdk-version 23 --print-certs $Apk
        if ($LASTEXITCODE -ne 0) { throw "Signature verification failed: $Apk" }
        $digest = $result | Select-String '^Signer #1 certificate SHA-256 digest: (.+)$'
        if (-not $digest) { throw "No signing certificate found: $Apk" }
        $digest.Matches[0].Groups[1].Value
    }
    $previous = @('dist\manaplayer.apk', 'dist\optimized\manaplayer.apk') |
        Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object { Certificate $_ }
    & .\gradlew.bat --no-daemon --console=plain --quiet :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed.' }
    if (-not (Test-Path -LiteralPath $key)) { throw 'The development signing key is missing.' }
    New-Item -ItemType Directory -Path 'dist\optimized' -Force | Out-Null
    # The standard Android debug-key password is public, not a production credential.
    & $signer sign --v4-signing-enabled false --ks $key --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android `
        --out $staged 'app\build\outputs\apk\release\app-release-unsigned.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Release signing failed.' }
    $certificate = Certificate $staged
    if (@($previous | Where-Object { $_ -ne $certificate }).Count -gt 0) {
        throw 'Signer mismatch: this release cannot update an existing exported APK.'
    }
    if ((Get-Item -LiteralPath $staged).Length -gt 6MB) { throw 'Release exceeds the 6 MiB APK size budget.' }
    $manifest = & (Join-Path $SdkPath 'build-tools\35.0.0\aapt.exe') dump badging $staged
    if ($LASTEXITCODE -ne 0 -or ($manifest -join "`n") -match 'application-debuggable' -or
        ($manifest -join "`n") -notmatch "sdkVersion:'23'") {
        throw 'Expected a non-debuggable Android 6.0+ release APK.'
    }
    Copy-Item -LiteralPath $staged -Destination 'dist\optimized\manaplayer.apk' -Force
    Copy-Item -LiteralPath $staged -Destination 'dist\manaplayer.apk' -Force
    Copy-Item -LiteralPath 'manaplayer.png', 'manaplayer.ico' -Destination 'dist' -Force
    foreach ($sidecar in @('dist\manaplayer.apk.idsig', 'dist\optimized\manaplayer.apk.idsig')) {
        if (Test-Path -LiteralPath $sidecar) { Remove-Item -LiteralPath $sidecar }
    }
    $hash = (Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  manaplayer.apk" | Set-Content -LiteralPath 'dist\manaplayer.apk.sha256' -Encoding ascii
    Get-Item -LiteralPath 'dist\manaplayer.apk' | Select-Object FullName, Length
    Write-Host 'Optimized sideload release exported; existing development signing identity retained for updates.'
} finally {
    if (Test-Path -LiteralPath $staged) { Remove-Item -LiteralPath $staged }
    if (Test-Path -LiteralPath "$staged.idsig") { Remove-Item -LiteralPath "$staged.idsig" }
    Pop-Location
}
