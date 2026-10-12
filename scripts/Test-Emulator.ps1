[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^emulator-\d+$')]
    [string] $Serial,
    [string] $SdkPath = $env:ANDROID_HOME
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
try {
    & .\gradlew.bat --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed.' }
    & $adb -s $Serial install -r 'dist\manaplayer.apk'
    if ($LASTEXITCODE -ne 0) { throw 'App APK installation failed.' }
    & $adb -s $Serial install -r 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
    $result = & $adb -s $Serial shell am instrument -w -r -e class com.macarmusic.app.MusicIntegrationTest com.macarmusic.app.test/androidx.test.runner.AndroidJUnitRunner
    New-Item -ItemType Directory -Path 'app\build\reports' -Force | Out-Null
    $result | Tee-Object -FilePath 'app\build\reports\emulator-integration.txt'
    $text = $result -join "`n"
    if ($text -notmatch 'OK \(\d+ tests?\)' -or $text -match 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED') {
        throw 'Integration tests failed. See app\build\reports\emulator-integration.txt.'
    }
    & $adb -s $Serial pull '/sdcard/Android/data/com.macarmusic.app/files/test-screenshots' 'app\build\reports'
    if ($LASTEXITCODE -ne 0) { throw 'Tests passed, but screenshot retrieval failed.' }
} finally {
    Pop-Location
}
