[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^emulator-\d+$')]
    [string] $Serial,
    [Parameter(Mandatory)]
    [switch] $DisposableDevice,
    [string] $SdkPath = $env:ANDROID_HOME,
    [string] $BaselineApk
)

$ErrorActionPreference = 'Stop'
if (-not $DisposableDevice) { throw 'Use a disposable emulator. These tests replace its ManaMusicPlayer library.' }
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath) { throw 'Set ANDROID_HOME or pass -SdkPath.' }
$adb = Join-Path $SdkPath 'platform-tools\adb.exe'
function Adb {
    $result = & $adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args`n$result" }
    return $result
}
if (((Adb shell getprop ro.kernel.qemu) -join '').Trim() -ne '1') {
    throw 'This helper refuses physical devices.'
}
$api = [int](((Adb shell getprop ro.build.version.sdk) -join '').Trim())
if ($api -lt 23) {
    throw 'ManaMusicPlayer requires an Android 6.0/API 23 or newer emulator.'
}
$audioPermission = if ($api -ge 33) { 'android.permission.READ_MEDIA_AUDIO' } else { 'android.permission.READ_EXTERNAL_STORAGE' }
$installed = @(Adb shell pm list packages)
$disabled = @(Adb shell pm list packages -d)
$pickers = @('com.google.android.documentsui', 'com.android.documentsui') |
    Where-Object { "package:$_" -in $installed }
if (-not $pickers) { throw 'No standard DocumentsUI package was found to simulate this firmware condition.' }

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & .\gradlew.bat --no-daemon --console=plain --quiet :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed.' }
    Adb install -r 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if ($api -lt 29) { Adb shell pm grant com.macarmusic.app.test android.permission.WRITE_EXTERNAL_STORAGE }
    foreach ($picker in $pickers) { Adb shell pm disable-user --user 0 $picker }
    if ($api -ge 24 -and
        ((Adb shell cmd package resolve-activity --brief -a android.intent.action.OPEN_DOCUMENT_TREE) -join "`n") -notmatch 'No activity found') {
        throw 'Another folder-picker handler is installed; this is not a pickerless test environment.'
    }
    function Run-Check([string] $Report, [string] $Class, [int] $Count, [string] $Flag) {
        $result = Adb shell am instrument -w -r -e $Flag true -e class $Class `
            com.macarmusic.app.test/androidx.test.runner.AndroidJUnitRunner
        $text = $result -join "`n"
        $result | Set-Content -LiteralPath "app\build\reports\$Report.txt"
        $result | Select-Object -Last 8
        if ($text -notmatch "OK \($Count tests?\)" -or $text -match 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED') {
            throw "Storage regression failed: $Report"
        }
    }
    if ($BaselineApk) {
        Adb install -r -d $BaselineApk
        Run-Check 'picker-baseline-reproduction' 'com.macarmusic.app.LegacyPickerSmokeTest' 1 'baselinePicker'
    }
    Adb install -r 'dist\debug\manaplayer.apk'
    Adb shell pm revoke com.macarmusic.app $audioPermission
    if ($api -ge 33) {
        Adb shell pm clear-permission-flags --user 0 com.macarmusic.app $audioPermission user-set user-fixed
    } else {
        Write-Host 'Older Android has no targeted permission-flag reset here; use a fresh emulator for denial checks.'
    }
    $methods = @(
        'pickerFallbacksKeepContentGrantsAndDoNotPretendToPersistTemporaryAccess',
        'exhaustedPickersAndSecurityRestrictionsRemainExplicit',
        'indexedFolderTokensAreDistinctFromFilesProvidersAndUnnormalizedPaths',
        'pickerlessDeviceRequestsAudioOnlyOnChoiceThenBrowsesScansAndPlaysSavedFolders',
        'ordinaryFileManagerFallbackAlsoImportsPrivateBackgroundPictures',
        'ordinaryFileManagerFallbackOpensAudioWithARealTemporaryContentGrant'
    )
    $classes = ($methods | ForEach-Object { "com.macarmusic.app.StorageCompatibilityTest#$_" }) -join ','
    Run-Check 'picker-compatibility-integration' $classes 6 'pickerless'
    Run-Check 'picker-chinese-integration' `
        'com.macarmusic.app.StorageCompatibilityTest#chineseCompatibilityCopyAndSavedFolderLabelsFollowTheSelectedLanguage' 1 'pickerless'
    Adb shell pm revoke com.macarmusic.app $audioPermission
    Run-Check 'picker-permission-revocation' `
        'com.macarmusic.app.StorageCompatibilityTest#savedSourcesFavoritesAndPlaylistsSurviveRealAudioPermissionRevocation' 1 'afterAudioRevoke'
    Run-Check 'picker-empty-catalog' `
        'com.macarmusic.app.StorageCompatibilityTest#emptyIndexedCatalogShowsAnExplicitLimitationAndCleansUpOwnedFixtures' 1 'cleanupIndexed'
    Adb shell pm revoke com.macarmusic.app $audioPermission
    New-Item -ItemType Directory -Path 'app\build\reports\picker-compatibility-screenshots' -Force | Out-Null
    Adb pull '/sdcard/Android/data/com.macarmusic.app/files/test-screenshots' 'app\build\reports\picker-compatibility-screenshots'
} finally {
    try {
        foreach ($picker in $pickers) {
            if ("package:$picker" -notin $disabled) { Adb shell pm enable --user 0 $picker }
        }
    } finally {
        Pop-Location
    }
}
