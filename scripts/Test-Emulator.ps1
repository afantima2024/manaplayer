[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^emulator-\d+$')]
    [string] $Serial,
    [string] $SdkPath = $env:ANDROID_HOME,
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_]+$')]
    [string[]] $Tests = @()
)

$ErrorActionPreference = 'Stop'
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath) { throw 'Set ANDROID_HOME or pass -SdkPath.' }
$adb = Join-Path $SdkPath 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw "ADB not found: $adb" }
$qemu = & $adb -s $Serial shell getprop ro.kernel.qemu
if ($LASTEXITCODE -ne 0 -or ($qemu -join '').Trim() -ne '1') {
    throw 'This helper only runs against a booted Android emulator, never physical hardware.'
}

Push-Location (Split-Path -Parent $PSScriptRoot)
$windowAnimationSettings = @{}
try {
    & .\gradlew.bat --no-daemon --console=plain --quiet :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed.' }
    & $adb -s $Serial install -r 'dist\debug\manaplayer.apk'
    if ($LASTEXITCODE -ne 0) { throw 'App APK installation failed.' }
    & $adb -s $Serial install -r 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
    # Synchronize system-window transitions without disabling the app's animator-driven effects.
    foreach ($name in @('window_animation_scale', 'transition_animation_scale')) {
        $original = & $adb -s $Serial shell settings get global $name
        if ($LASTEXITCODE -ne 0) { throw "Could not read emulator setting: $name" }
        $windowAnimationSettings[$name] = ($original -join '').Trim()
        & $adb -s $Serial shell settings put global $name 0
        if ($LASTEXITCODE -ne 0) { throw "Could not configure emulator setting: $name" }
    }
    $testClass = if ($Tests.Count) {
        ($Tests | ForEach-Object { "com.macarmusic.app.MusicIntegrationTest#$_" }) -join ','
    } else { 'com.macarmusic.app.MusicIntegrationTest' }
    $result = & $adb -s $Serial shell am instrument -w -r -e class $testClass com.macarmusic.app.test/androidx.test.runner.AndroidJUnitRunner
    New-Item -ItemType Directory -Path 'app\build\reports' -Force | Out-Null
    $result | Set-Content -LiteralPath 'app\build\reports\emulator-integration.txt'
    $text = $result -join "`n"
    if ($text -notmatch 'OK \(\d+ tests?\)' -or $text -match 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED') {
        throw 'Integration tests failed. See app\build\reports\emulator-integration.txt.'
    }
    Write-Host ([regex]::Match($text, 'OK \(\d+ tests?\)').Value)
    & $adb -s $Serial pull '/sdcard/Android/data/com.macarmusic.app/files/test-screenshots' 'app\build\reports'
    if ($LASTEXITCODE -ne 0) { throw 'Tests passed, but screenshot retrieval failed.' }
} finally {
    foreach ($name in $windowAnimationSettings.Keys) {
        if ($windowAnimationSettings[$name] -eq 'null') {
            & $adb -s $Serial shell settings delete global $name | Out-Null
        } else {
            & $adb -s $Serial shell settings put global $name $windowAnimationSettings[$name]
        }
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not restore emulator setting: $name" }
    }
    Pop-Location
}
