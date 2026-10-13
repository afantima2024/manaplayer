[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^emulator-\d+$')]
    [string] $Serial,
    [Parameter(Mandatory)]
    [switch] $DisposableDevice,
    [string] $SdkPath = $env:ANDROID_HOME,
    [ValidatePattern('^[a-z0-9-]+$')]
    [string] $Label = 'debug',
    [ValidateRange(1, 10)]
    [int] $Runs = 5,
    [ValidateRange(1, 5)]
    [int] $FeatureRuns = 2,
    [ValidateRange(10, 60)]
    [int] $SampleSeconds = 20,
    [ValidateRange(1, 10000)]
    [int] $FeatureTrackCount = 1,
    [int[]] $TrackCounts = @(0, 2000, 10000),
    [ValidateSet('minimal', 'lyrics', 'static', 'slideshow', 'blurred-slideshow',
        'spectrum15', 'spectrum30', 'spectrum60', 'combined')]
    [string[]] $Features = @('minimal', 'lyrics', 'static', 'slideshow', 'blurred-slideshow',
        'spectrum15', 'spectrum30', 'spectrum60', 'combined'),
    [switch] $SkipStartup,
    [switch] $SkipFeatures
)

$ErrorActionPreference = 'Stop'
if (-not $DisposableDevice) { throw 'This benchmark replaces app data. Use an owned disposable emulator.' }
if (-not $SdkPath) { throw 'Set ANDROID_HOME or pass -SdkPath.' }
$adb = Join-Path $SdkPath 'platform-tools\adb.exe'
function Adb {
    $output = & $adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args`n$output" }
    return $output
}
if (((Adb shell getprop ro.kernel.qemu) -join '').Trim() -ne '1') { throw 'Physical devices are not supported.' }
if (((Adb shell id) -join '') -notmatch 'uid=0') {
    throw 'Use an owned Google APIs emulator and explicitly run adb -s SERIAL root first.'
}
$api = [int](((Adb shell getprop ro.build.version.sdk) -join '').Trim())
$project = Split-Path -Parent $PSScriptRoot
$report = Join-Path $project "app\build\reports\performance\api$api\$Label"
New-Item -ItemType Directory -Path $report -Force | Out-Null
$package = 'com.macarmusic.app'
$data = "/data/user/0/$package"
$packageInfo = (Adb shell dumpsys package $package) -join "`n"
if ($packageInfo -notmatch 'userId=(\d+)') { throw 'Install the app first.' }
$appUid = $Matches[1]
$ticksPerSecond = [int](((Adb shell getconf CLK_TCK) -join '').Trim())
if ($ticksPerSecond -lt 1) { throw 'Could not determine the CPU accounting frequency.' }
$tree = 'content://com.macarmusic.app.test.documents/tree/music'
$audio = "$tree/document/music%2Fone"
Adb shell content call --uri $tree --method setLyricsMode --arg scrollable | Out-Null
$packageInfo | Set-Content -LiteralPath "$report\package.txt"
$installed = ((Adb shell pm path $package) -join "`n")
if ($installed -notmatch '(?m)^package:(.+/base\.apk)\s*$') { throw 'Cannot identify the installed base APK.' }
Adb shell sha256sum ($Matches[1].Trim()) | Set-Content -LiteralPath "$report\installed-apk-sha256.txt"
Adb shell settings list global | Select-String 'animation_scale|animator_duration_scale' |
    ForEach-Object { $_.Line } | Set-Content -LiteralPath "$report\animation-settings.txt"
Adb shell getprop | Set-Content -LiteralPath "$report\device-properties.txt"
Adb shell wm size | Set-Content -LiteralPath "$report\display.txt"
Adb shell wm density | Add-Content -LiteralPath "$report\display.txt"

function Install-DataFile([string] $Local, [string] $Destination) {
    $temporary = '/data/local/tmp/manaplayer-performance-' + [IO.Path]::GetFileName($Local)
    Adb push $Local $temporary | Out-Null
    Adb shell cp $temporary $Destination | Out-Null
    Adb shell chown "${appUid}:${appUid}" $Destination | Out-Null
    Adb shell rm $temporary | Out-Null
}

$imageNames = @(1..3 | ForEach-Object { '00000000-0000-0000-0000-{0:d12}.png' -f $_ })
if (-not $SkipFeatures) {
    Add-Type -AssemblyName System.Drawing
    Adb shell mkdir -p "$data/files/backgrounds" | Out-Null
    Adb shell chown "${appUid}:${appUid}" "$data/files" "$data/files/backgrounds" | Out-Null
    for ($index = 0; $index -lt $imageNames.Count; $index++) {
        $image = [Drawing.Bitmap]::new(1280, 720)
        $graphics = [Drawing.Graphics]::FromImage($image)
        try {
            $random = [Random]::new(42 + $index)
            for ($row = 0; $row -lt 720; $row += 24) {
                for ($column = 0; $column -lt 1280; $column += 24) {
                    $start = [Drawing.Color]::FromArgb($random.Next(256), $random.Next(256), $random.Next(256))
                    $end = [Drawing.Color]::FromArgb($random.Next(256), $random.Next(256), $random.Next(256))
                    $rectangle = [Drawing.Rectangle]::new($column, $row, 24, 24)
                    $brush = [Drawing.Drawing2D.LinearGradientBrush]::new($rectangle, $start, $end, 45.0)
                    try { $graphics.FillRectangle($brush, $rectangle) } finally { $brush.Dispose() }
                }
            }
            $path = Join-Path $report $imageNames[$index]
            $image.Save($path, [Drawing.Imaging.ImageFormat]::Png)
            Install-DataFile $path "$data/files/backgrounds/$($imageNames[$index])"
        } finally {
            $graphics.Dispose()
            $image.Dispose()
        }
    }
}

function Seed([int] $Count, [string] $Feature) {
    if ($Count -lt 0 -or $Count -gt 10000) { throw 'Fixture track count must be 0-10000.' }
    Adb shell am force-stop $package | Out-Null
    $tracks = @(for ($index = 0; $index -lt $Count; $index++) {
        @{
            id = "performance-$index"; uri = $audio; fileName = '01 - Synthetic.wav'
            title = "Performance fixture $index"; artist = 'Synthetic audio'; album = 'Performance evaluation'
            durationMs = 60000; mimeType = 'audio/wav'
            sourceUris = @{ $tree = $audio }; sourceParents = @{ $tree = 'music' }
            sourcePaths = @{ $tree = 'Performance fixtures/01 - Synthetic.wav' }
        }
    })
    $library = @{
        version = 1; tracks = $tracks; favorites = @(); playlists = @()
        sources = @(@{ uri = $tree; name = 'Performance fixtures'; persisted = $true })
    }
    $playback = @{ version = 1; queue = $tracks; index = 0; positionMs = 0; shuffle = $false; repeat = 0 }
    $library | ConvertTo-Json -Depth 8 -Compress | Set-Content -LiteralPath "$report\library.json" -Encoding utf8NoBOM
    $playback | ConvertTo-Json -Depth 8 -Compress | Set-Content -LiteralPath "$report\playback.json" -Encoding utf8NoBOM
    $pictures = $Feature -in @('static', 'slideshow', 'blurred-slideshow', 'combined')
    $lyrics = $Feature -in @('lyrics', 'combined')
    $spectrum = $Feature.StartsWith('spectrum') -or $Feature -eq 'combined'
    $rate = if ($Feature -eq 'spectrum15') { 15 } elseif ($Feature -eq 'spectrum60') { 60 } else { 30 }
    $ids = @()
    if ($Feature -eq 'static') { $ids = @($imageNames[0]) }
    elseif ($pictures) { $ids = $imageNames }
    $escapedIds = [Security.SecurityElement]::Escape((ConvertTo-Json -InputObject @($ids) -Compress))
    $blur = if ($Feature -in @('blurred-slideshow', 'combined')) { 60 } else { 0 }
    @"
<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<map>
<boolean name="parkedNoticeSeen" value="true" />
<int name="theme" value="2" />
<boolean name="lyricsEnabled" value="$($lyrics.ToString().ToLowerInvariant())" />
<boolean name="onlineLyrics" value="false" />
<boolean name="spectrumEnabled" value="$($spectrum.ToString().ToLowerInvariant())" />
<int name="spectrumRefreshRate" value="$rate" />
<string name="spectrumPalette">FIRE</string>
<string name="spectrumFireStyle">LED</string>
<boolean name="customTheme" value="$($pictures.ToString().ToLowerInvariant())" />
<long name="backgroundVersion" value="$(if ($pictures) { 1 } else { 0 })" />
<string name="backgroundImages">$escapedIds</string>
<int name="backgroundBlur" value="$blur" />
<int name="backgroundIntervalSeconds" value="5" />
<int name="backgroundDurationMs" value="2000" />
<string name="backgroundTransition">FADE</string>
<string name="loopMode">OFF</string>
<string name="playOrder">SEQUENTIAL</string>
</map>
"@ | Set-Content -LiteralPath "$report\settings.xml" -Encoding utf8NoBOM
    Adb shell mkdir -p "$data/files" "$data/shared_prefs" | Out-Null
    Adb shell chown "${appUid}:${appUid}" "$data/files" "$data/shared_prefs" | Out-Null
    Install-DataFile "$report\library.json" "$data/files/library.json"
    Install-DataFile "$report\playback.json" "$data/files/playback.json"
    Install-DataFile "$report\settings.xml" "$data/shared_prefs/settings.xml"
    Adb shell restorecon -RF $data | Out-Null
}

function Launch([string] $Name) {
    Adb shell am force-stop $package | Out-Null
    # A force-stop removes these test-only temporary grants; renew them outside the timed launch.
    $grant = (Adb shell am start -W -n com.macarmusic.app.test/com.macarmusic.app.FixtureGrantActivity) -join "`n"
    if ($grant -notmatch 'Status: ok') { throw "Fixture permission setup failed: $grant" }
    Start-Sleep -Milliseconds 750
    Adb logcat -c | Out-Null
    $output = (Adb shell am start -W -n "$package/.ui.MainActivity") -join "`n"
    $output | Set-Content -LiteralPath "$report\$Name-launch.txt"
    if ($output -notmatch 'Status: ok' -or $output -notmatch 'TotalTime: (\d+)') { throw "Launch failed: $output" }
    $displayMs = [int]$Matches[1]
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 300
        $log = (Adb logcat -d -v brief -s ActivityTaskManager AndroidRuntime MusicRepository PlaybackRepository) -join "`n"
        if ($log -match 'FATAL EXCEPTION|Library was preserved after a load failure|Unreadable playback state') {
            throw "App startup failed: $log"
        }
        if ($log -match 'Fully drawn com\.macarmusic\.app/\.ui\.MainActivity: \+(?:(\d+)s)?(\d+)ms') {
            $log | Set-Content -LiteralPath "$report\$Name-log.txt"
            return [pscustomobject]@{ DisplayMs = $displayMs; ControlsMs = [int]$Matches[1] * 1000 + [int]$Matches[2] }
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Usable controls were not reported: $log"
}

function Sample([string] $ProcessId) {
    $stat = ((Adb shell cat "/proc/$ProcessId/stat") -join '').Split(')')[-1].Trim() -split '\s+'
    $uptime = [double]::Parse((((Adb shell cat /proc/uptime) -join '') -split '\s+')[0],
        [Globalization.CultureInfo]::InvariantCulture)
    $io = (Adb shell cat "/proc/$ProcessId/io") -join "`n"
    if ($io -notmatch '(?m)^wchar:\s*(\d+)') { throw 'Process write accounting unavailable.' }
    $written = [long]$Matches[1]
    if ($io -notmatch '(?m)^write_bytes:\s*(\d+)') { throw 'Kernel write accounting unavailable.' }
    [pscustomobject]@{
        Ticks = [long]$stat[11] + [long]$stat[12]; Uptime = $uptime
        Written = $written; KernelWritten = [long]$Matches[1]
    }
}

function Memory([string] $Name) {
    $memory = (Adb shell dumpsys meminfo $package) -join "`n"
    $memory | Set-Content -LiteralPath "$report\$Name-memory.txt"
    if ($memory -match 'TOTAL PSS:\s*(\d+)') { return [int]$Matches[1] }
    if ($memory -match '(?m)^\s*TOTAL\s+(\d+)') { return [int]$Matches[1] }
    throw "Could not read memory: $memory"
}

try {
    if (-not $SkipStartup) {
        $results = @()
        foreach ($count in $TrackCounts) {
            Write-Host "Measuring $Label startup with $count saved tracks."
            Seed $count 'minimal'
            for ($run = 0; $run -le $Runs; $run++) {
                $name = "startup-$count-$run"
                $timing = Launch $name
                Start-Sleep -Seconds 2
                if ($run -gt 0) {
                    $results += [pscustomobject]@{
                        Label = $Label; Api = $api; Tracks = $count; Run = $run
                        DisplayMs = $timing.DisplayMs; ControlsMs = $timing.ControlsMs
                        PssKb = Memory $name
                    }
                    $results | Export-Csv -LiteralPath "$report\startup.csv" -NoTypeInformation
                }
            }
        }
        $results | Format-Table
    }
    if (-not $SkipFeatures) {
        $results = @()
        for ($run = 1; $run -le $FeatureRuns; $run++) {
            $ordered = if ($run % 2 -eq 1) { @($Features) } else { @($Features | Sort-Object -Descending) }
            foreach ($feature in $ordered) {
                Write-Host "Measuring $Label playback: $feature, repetition $run."
                Seed $FeatureTrackCount $feature
                $name = "feature-$feature-$run"
                $timing = Launch $name
                Start-Sleep -Seconds 3
                Adb shell cmd media_session dispatch play | Out-Null
                Start-Sleep -Seconds 3
                $session = (Adb shell dumpsys media_session) -join "`n"
                if ($session -notmatch 'state=3,') { throw "Playback did not start: $session" }
                $processId = ((Adb shell pidof $package) -join '').Trim()
                if ($processId -notmatch '^\d+$') { throw 'Expected one application process.' }
                Adb shell dumpsys gfxinfo $package reset | Out-Null
                $before = Sample $processId
                Start-Sleep -Seconds $SampleSeconds
                $after = Sample $processId
                $session = (Adb shell dumpsys media_session) -join "`n"
                if ($session -notmatch 'state=3,') { throw "Playback stopped during measurement: $session" }
                $pss = Memory $name
                $frames = (Adb shell dumpsys gfxinfo $package) -join "`n"
                $frames | Set-Content -LiteralPath "$report\$name-frames.txt"
                $snapshotPath = "$report\$name-playback.json"
                Adb pull "$data/files/playback.json" $snapshotPath | Out-Null
                $snapshot = Get-Content -Raw -LiteralPath $snapshotPath | ConvertFrom-Json
                if ($snapshot.queue.Count -ne $FeatureTrackCount -or $snapshot.positionMs -lt 1000) {
                    throw 'The persisted queue/position did not reflect the requested playing fixture.'
                }
                $elapsed = $after.Uptime - $before.Uptime
                $results += [pscustomobject]@{
                    Label = $Label; Api = $api; Feature = $feature; Run = $run; Tracks = $FeatureTrackCount
                    DisplayMs = $timing.DisplayMs; ControlsMs = $timing.ControlsMs
                    CpuPercentOneCore = [Math]::Round(($after.Ticks - $before.Ticks) / $ticksPerSecond / $elapsed * 100, 2)
                    PssKb = $pss; SampleSeconds = [Math]::Round($elapsed, 2)
                    WriteBytes = $after.Written - $before.Written
                    KernelWriteBytes = $after.KernelWritten - $before.KernelWritten
                    SnapshotBytes = (Get-Item -LiteralPath $snapshotPath).Length
                }
                $results | Export-Csv -LiteralPath "$report\features.csv" -NoTypeInformation
                if ($run -eq 1) {
                    Adb shell screencap -p /data/local/tmp/manaplayer-performance.png | Out-Null
                    Adb pull /data/local/tmp/manaplayer-performance.png "$report\$name.png" | Out-Null
                }
                Adb shell cmd media_session dispatch pause | Out-Null
            }
        }
        $results | Format-Table
    }
} finally {
    Adb shell am force-stop $package | Out-Null
}
