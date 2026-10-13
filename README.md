# ManaMusicPlayer / 小悦音乐播放器

A native Kotlin music player for **standalone / aftermarket Android head units running Android 6.0 (API 23) or newer**. Install the APK directly on Android. This is **not Android Auto** and **not an Android Automotive OS app**.

**Set up storage, browse, search, and manage playlists only while parked.** Standard Android/Bluetooth media keys are supported; proprietary CAN-bus and steering-wheel protocols are outside the app's scope.

## Features

- Multiple saved internal-storage or USB music folders, normally using Android's Storage Access Framework (SAF), with a persistent multi-folder playback selection. **Device music / 设备音乐** provides a picker-independent alternative for folders indexed by Android's audio library.
- Folder/file navigation, recursive scanning and folder playback, rescan, and single-file selection. Selecting a file in the folder browser plays that file only. Library playback uses the selected loop scope.
- Local title, artist, album, duration, track number, year, genre, and embedded-art extraction, where available. Filenames supply missing titles. Metadata and document operations run off the UI thread. Artwork is decoded on demand at a bounded resolution and cached separately, not embedded in the library database.
- Play/pause, previous (restart after the usual threshold), next, **10-second rewind/forward**, seek, queue reordering/removal, sequential or random order, single-file/folder/selected-folders looping, and Android media-stream volume. Opening Queue scrolls to the current playing occurrence, including repeated songs.
- Library search, sorting, multi-song selection, favorites, and named local playlists: create, add/edit songs and folders, rename, refresh, play, delete, and save the current queue or selected folders. Pin saved folders to protect their library and playlist references until unpinned.
- A single service-owned Media3 ExoPlayer, a MediaLibrarySession, and MediaController-driven native UI. Playback can continue in the background and with the screen off, with a media notification, Android music-widget controls, and standard headset/Bluetooth media controls.
- Android audio focus handling (pause/duck according to the other app's request), becoming-noisy handling, and explicit playback errors. An inaccessible file **stops playback; the app never enters an automatic skip/retry loop**. Reconnect storage and press Play to retry, or choose another track.
- Atomic, app-private persistence for library references, folder selection, favorites, playlists, and the last queue/position. Language, theme, playback mode, and sort preferences also persist. Launch restores the queue **paused, without preparing media or requesting audio focus**. There is no boot autoplay.
- Staged startup: a small branded launch window replaces the blank white screen, the player draws before service connection, and optional work waits for usable playback controls. Background loading additionally waits for library restoration and a one-second grace period. Hidden Settings, library, folder, queue, and playlist pages are built only when opened.
- High-contrast night/day/car-system themes and a **Customize** picture/slideshow theme with selectable transitions, timing, independent blur, black/white tint, and tint-strength controls. Large controls, a seekable mini-player outside Now Playing, and portrait/landscape layouts.
- Transparent block outlines with solid, dashed, rounded-arc, **dashed arc**, or customizable repeated-character borders across the player, lyrics, mini-player, and navigation. Rounded styles have an adjustable radius; an optional raised-brick effect adds solid lower/right edges without filling the interior. A General switch returns to unframed controls.
- An artwork-first navigation toolbar that can dock at the bottom, top, left, or right. Responsive song information and spectrum use the remaining player space, including the height released by side docking. The header and screen top stay fixed when switching destinations. Elapsed/remaining time sits left of the main seek bar; total duration sits right.
- Configurable Settings page transitions. An optional, real audio spectrum shares the song-information area on either side, with peaks and falloff controls. Classic, Fire, and Theme support smooth bars and LED segments. Classic uses one chart-height color scale; Fire stretches all three colors over each individual bar. Tap the spectrum to cycle through Line, Classic smooth, Classic LED, Fire smooth, and Fire LED.
- Optional local-first lyrics replace the artwork in a configurable **left/right landscape** or **top/bottom portrait** panel. Current/other lines have independent font sizes and color-block selectors, initially 24sp/20sp with the existing theme colors. Only the playing line keeps extra spacing. Dragging shows a time beside the center guide; opt-in drag-to-play seeks to the released line. Online matching and bounded caching remain available.
- **Settings > About and help** contains usage instructions, device information, and the supplied upper-right photo. Tap the photo for an original-resolution viewer with pinch/double-tap zoom, panning, and zoom/Fit buttons.
- A native two-pane Settings page with **English**, **Simplified Chinese**, or system language. General includes appearance controls; centered category labels have border-style-matched dividers. Color selectors keep their palette blocks and also accept custom `#RRGGBB` values with preview and validation, including theme accents. Settings uses a single-row mini-player and leaves the main navigation dock unchanged and accessible. Operation notices dismiss automatically after ten seconds or immediately using their close button.

## Build

The complete project is in `C:\Users\afma\CopilotProjects\MaCarMusic`.

The app and Gradle project are named **ManaMusicPlayer**, with the Chinese app name **小悦音乐播放器**. Version **1.9.3** (version code **17**) retains the application ID `com.macarmusic.app`, existing source folder, and signing identity so an in-place update preserves settings, saved folders, playlists, and the playback queue. Android 6.0/API 23 compatibility is restored to match the user-confirmed installable 1.3.1 APK, without reverting current features or release optimization. The `manaplayer1010` archive, Git tag, and first release preserve the earlier 1.2.0 baseline.

Pinned versions:

| Component | Version |
| --- | --- |
| Gradle wrapper | 8.10.2, with distribution SHA-256 verification |
| Android Gradle Plugin | 8.8.1 |
| Kotlin | 2.1.21 |
| AndroidX Media3 | 1.6.1 |
| Compile / target SDK | 35 |
| Minimum SDK | 23 |
| Android SDK Build Tools | 35.0.0 |
| Java compilation target | 17 |

Use **JDK 17 or 21**. A newer IDE-bundled JDK 25 is not compatible with this pinned Gradle release. An Android Studio installation is optional; the Android SDK and a compatible JDK suffice.

1. Install/locate Android SDK Platform 35, Build Tools 35.0.0, and Platform Tools. Accept the SDK licenses through your normal SDK tooling.
2. Set `JAVA_HOME` to JDK 17/21 and `ANDROID_HOME` to the SDK. Alternatively, put an escaped SDK path in untracked `local.properties`. The existing `local.properties` is specific to this development machine; do not copy it to another machine unchanged.
3. In PowerShell, from the project folder:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\scripts\Export-ReleaseApk.ps1
```

On the development machine the existing compatible JDK is `C:\Users\afma\.jdks\jbr-21.0.11`, and the SDK is `C:\Users\afma\AppData\Local\Android\Sdk`.

**Standard optimized release APK output:**

```text
C:\Users\afma\CopilotProjects\MaCarMusic\dist\manaplayer.apk
```

The export helper builds R8-optimized, resource-shrunk, non-debuggable release code, verifies Android 6.0+ packaging and the signing certificate, and enforces a **6 MiB APK size budget**. It writes identical APKs to `dist\manaplayer.apk` and `dist\optimized\manaplayer.apk`, plus `dist\manaplayer.apk.sha256`. The top-level APK is no longer the approximately 22 MiB debug build.

The current **1.9.3** APK is **5,766,496 bytes (5.50 MiB / 5.77 MB)**, **173 KiB (3.17%)** larger than 1.9.2. Its SHA-256 is `5003E9ED66BB2F5D28836DE7922F27FE181CACC46AAB46206459E9F0D40F7655`. Both export locations contain that identical APK. Each later export updates `dist\manaplayer.apk.sha256` and must still meet the size budget.

Restoring Android 6 support retains R8/resource shrinking and explicitly keeps DEX uncompressed, as in the preceding optimized release. Lowering minSdk would otherwise change Gradle's default to compressed DEX; the smaller ZIP is not used as a substitute for preserving direct DEX mapping on modern Android. The APK uses Android 6-compatible DEX 035, not DEX 039. No dependency or optional feature was added for this compatibility update.

The first build downloads the pinned Gradle distribution and dependencies.

**Signing:** the sideload release retains this project's existing development signing identity for compatibility with previously installed APKs. This is not a new production/store signing key. The helper requires the existing `%USERPROFILE%\.android\debug.keystore` and rejects a signer mismatch with either previously exported APK. Keep the original key backed up privately; a different key cannot update existing installs. No keystore or machine-specific configuration is committed. For a different distribution/signing identity, configure a private key outside source control and build `:app:assembleRelease` (unsigned by default); do not silently change the signer of updates.

### Separate developer build

For debugging and the regular AndroidX instrumentation suite:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
```

Debug output: `dist\debug\manaplayer.apk`, also under `app\build\outputs\apk\debug\app-debug.apk`. It never overwrites the standard release APK. The first debug build creates a development key if none exists on that machine; this newly generated identity is not the original published release's key.

Debug builds are intentionally not shrunk and are much larger. R8 code optimization and resource shrinking, not removing features or older Android support alone, produce the small release APK. Build outputs, signing keys, SDKs, and machine-specific files are ignored by Git; only the optimized APK and checksum belong in a published release.

## Install and use

Copy `manaplayer.apk` to the head unit and open it with its local package installer, or, after **you** enable developer options/USB debugging and authorize your own device:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s YOUR_DEVICE_SERIAL install -r .\dist\manaplayer.apk
```

Always choose the intended device explicitly. Development validation does not authorize actions on connected physical devices.

1. Open **ManaMusicPlayer / 小悦音乐播放器** while parked.
2. Go to **Folders > Add folder**. Choose a music subfolder in internal storage or an exposed USB volume, and grant read access. If the head unit has no Android folder picker, choose **Device music**, allow music/audio access (called storage access on Android 6-12), and check one or more indexed folders in the built-in chooser. This option is also under **Settings > Folders**.
3. Add further folders as needed. Tap a folder to browse it, **Play folder** to scan/play it recursively, or a file to play just that file. **Open file** tries Android's document picker, then compatible ordinary file managers, and validates the returned content URI and audio type afterward.
4. Use a song's three-dot menu for favorite, Play next, Add to queue, Add to playlist, details, or removal of its library reference.
5. Use **Playlists > New playlist**, then **Add songs**, or **Queue > Save as playlist**. **Save folders as playlist** includes selected saved roots or the currently browsed subfolder. Inside a playlist, **Update playlist** edits its songs/folders, renames it, refreshes saved folders, or deletes it.
6. In Library, long-press a song or tap **Select songs**, select multiple rows, then **Add to playlist**. **Select all shown** respects the current search/filter. Selection persists while filtering or rotating; choosing songs in this mode does not start playback. Unfinished selections are held in the retained ViewModel, not Android's size-limited saved-state Bundle, and clear when that browsing session ends.
7. Open **Settings** using the only top-right button, the gear. **About and help** is a Settings category. The navigation dock remains usable; tap any page to leave Settings directly.
8. **Settings > Folders > Pin folders** protects saved sources and their songs from removal/deselection, including deleting a playlist containing them. Unpin first when you want to remove those references.

Removing a source, song reference, or playlist **never deletes, edits, moves, or renames a music file**. Library reference removal prunes associated favorites and playlist entries. Source rescans can rediscover deliberately removed song references.

Folder playlists retain their folder associations separately from manually added songs. Refresh scans only when requested, adds discoveries, preserves manually added songs and existing saved references, and does not replace playlist membership after a failed scan. Explicitly editing/removing songs is separate from refreshing; a future refresh can rediscover an excluded song still in a linked folder. Overlapping folders deduplicate songs. Pins protect library/playlists, not the temporary playback queue.

**Song details > File location** shows a storage-volume path or the saved-folder-relative path, rather than a cryptic `content://` identifier. Android uses content URIs to grant access through storage providers; they are not ordinary filesystem paths. If a provider cannot reveal a complete path, Details shows the known folder/filename and explains the limitation instead of inventing a path. Location lookup is asynchronous and runs only when Details is opened.

Operation notices, including scan results and errors, close after **10 seconds**. Each replacement notice gets a fresh ten-second lifetime, and its close button dismisses it immediately. Confirmation dialogs remain open until you choose an action.

On short windows, the horizontal dock uses labeled-for-accessibility icons, and the mini-player keeps Play, Next, seeking, and time in one row. List headings move into the main header; search and actions share a row, preserving at least 48dp control targets. Settings stays scrollable above the mini-player and dock. Long compact notices can be read by long-pressing their text. Exceptionally short landscape player panels can scroll rather than shrinking controls below their usable size.

### Head units without a system file picker

The old "no compatible Android file picker" message was emitted after Android rejected `ACTION_OPEN_DOCUMENT_TREE`/`ACTION_OPEN_DOCUMENT` with `ActivityNotFoundException`; there was no package-visibility precheck incorrectly hiding a handler. The same error was reproduced with the preceding APK on an emulator whose DocumentsUI package was disabled. A car's exact cause can be a missing, disabled, or unsupported picker implementation, not necessarily a refused storage permission.

Normal SAF selection grants access to the chosen file/folder through the picker, so **no storage-permission prompt at app startup is needed**. The optional Device music path instead requests `READ_MEDIA_AUDIO` on Android 13+, or `READ_EXTERNAL_STORAGE` on Android 6-12 (declared with `maxSdkVersion="32"`). The prompt appears only after choosing Device music. Denial keeps existing references; a blocked prompt links to the app's permission settings. No all-files access, write permission, photo/video permission, package-inventory permission, or boot-time media scan is added.

On Android 6-9, the legacy audio index supplies folder/file locations through its `_data` metadata column; those strings are used only for grouping and display, never to open raw files or walk a disk. Audio still opens through `content://` URIs. Because these providers cannot reliably identify individual USB mount states, an empty rescan of a saved indexed folder reports unavailable media and retains its references rather than silently erasing them. An initially empty Device music catalog still displays its explicit limitation.

Device music queries MediaStore on a cancellable worker, presents multiple selectable folders, and uses the same saved-source, browsing, recursive scan, rescan, loop-selection, favorites, playlist, and playback paths. Permission checks include app-ops so denied access is not mistaken for an empty library. Music remains addressed by `content://` URIs. Android 10+ uses MediaStore's relative directory and volume columns; Android 6-9 uses the guarded metadata-only grouping described above. Missing volumes and failed rescans preserve saved references.

**Limitations:** this view contains only audio indexed by Android. Granting permission cannot make an unindexed USB volume appear. Such files need a compatible file manager returning a readable content URI, or restored system SAF support from the head-unit vendor. MediaStore permission does not grant adjacent LRC/TXT files; a SAF folder grant is still needed for sidecars. Embedded lyrics, cached/online lyrics, metadata, and covers continue to work. Index or volume-identity changes can require reselection. Different providers may expose separate references to the same recording.

Single-file and background-picture selection fall back to `ACTION_GET_CONTENT`, including managers that omit the `OPENABLE` category. These returns are treated as potentially temporary; private background copies survive loss of the original grant. A raw `file://` return is rejected with an actionable message. Each fallback has its own activity-result channel: Android can deliver a late cancelled result for the failed primary launch, which must not consume the fallback's successful result.

About/help now displays the framework Android version/API level and manufacturer/model locally to make firmware-specific diagnosis easier.

## Startup behavior

The earlier launch path inflated every page, including the large Settings hierarchy, before drawing the player. Initial state collectors also rendered hidden Settings repeatedly, sorted the hidden library, and formatted every hidden queue row. Service/player initialization competed with that first frame. The ordinary DayNight launch theme could show its light starting window before the saved night/custom theme was applied. Local and online lyric I/O was already on background dispatchers; it was **not** a synchronous network wait on the UI thread.

ManaMusicPlayer uses Android's native starting window with a small music icon on a dark background, including the Android 12+ system splash attributes. There is no splash activity, timer, or minimum splash duration. The saved day/night/custom theme takes over as the actual player is created. The first player frame includes navigation and settings access; the transport shows Connecting until the service restores the saved queue. That queue is still paused and unprepared. Session binding starts after the first draw, and saved queue-to-MediaItem conversion runs off the main thread.

The activity starts lyrics, embedded artwork, and visualizer capture after the controller and paused queue are ready and a usable-controls frame has drawn. Background decoding waits for that milestone, library restoration, and a further **one second**; slideshow cycling starts only after the first picture has appeared for the requested interval. This grace period never delays Play, navigation, or the initial screen. Putting the activity in the background cancels deferred work; a changed song, disabled lyric switch, or cancelled lookup cannot publish an old lyric result. There is no automatic rescan or storage enumeration during queue restoration. External music widgets still initialize the service directly without requiring the activity or its staged UI.

Secondary pages are created on first use and retained for that activity. Hidden list rows are not rebuilt, and library sorting is subscribed only while the library is open (with a short retention window). Rotation and theme/language recreation restore the selected page and its settings category rather than constructing every page.

### 1.9.0 performance baseline

The optimized 1.9.0 APK (SHA-256 `A151AD5F7E9BEF34E6BF1AD189C2B14D0C820E6408DCB5F357AEBEA4B4EE6D90`) was compared with the published 1.8.0 APK on one disposable Android 13/API 33 x86_64 emulator: two virtual CPUs, 2 GiB RAM, 1024 x 600 at density 160, software graphics, and normal animation scales. The Android 10 emulator and builds were stopped during measurement. The sequence was **1.8.0, 1.9.0, then 1.8.0 again** to expose batch/runtime variation rather than attribute it to the new code. These performance measurements were not repeated for 1.9.1 or 1.9.2.

Each library size used one excluded warm-up and three force-stopped launches, with identical synthetic library/queue JSON and settings: night theme, lyrics/spectrum off, no slideshow. Values are median **first display / usable controls, in milliseconds**:

| Saved library and queue | 1.8.0 initial batch | 1.9.0 | 1.8.0 repeat |
| --- | --- | --- | --- |
| Empty | 1,427 / 1,739 | 684 / 791 | 815 / 1,016 |
| 2,000 tracks | 1,589 / 2,167 | 767 / 1,029 | 705 / 990 |
| 10,000 tracks | 1,527 / 2,777 | 752 / 1,224 | 786 / 1,286 |

**This does not establish a general startup speedup.** The baseline repeat was substantially faster than its first batch, and its 2,000-track usable-controls median was 39 ms lower than 1.9.0. Host scheduling and Android runtime/profile/cache state were not frozen. First display is `am start -W`; usable controls follows restored paused playback and a rendered frame. These are emulator measurements, not car-startup guarantees.

With actual synthetic PCM playback, each feature used two 20-second samples and a different mode order in the second pass. Spectrum was Fire/LED at 30 fps. Slideshow used three generated 1280 x 720 pictures, a five-second interval and two-second fade. CPU is the app process's percentage of **one** virtual CPU; PSS is a sampled proportional memory figure, not peak use:

| Optional feature | 1.8.0 initial CPU | 1.9.0 CPU | 1.8.0 repeat CPU | 1.9.0 PSS |
| --- | --- | --- | --- | --- |
| None | 3.5% | 2.4% | 3.4% | 38.2 MiB |
| 30-fps spectrum | 9.1% | 7.0% | 11.4% | 40.0 MiB |
| Sharp-picture slideshow | 10.6% | 7.9% | 14.0% | 49.0 MiB |

The varying baseline samples prevent a reliable percentage-improvement claim. A static/ordinary background and disabled spectrum remain the lightest choices; neither feature is required for playback. Optional image/lyric/visualizer work remains deferred, Settings remains lazy, and no dependency was added for these refinements.

Large-library memory still matters: median startup PSS with 10,000 references was 79.8 MiB in 1.9.0, versus 86.9 MiB in the initial baseline and 73.7 MiB in its repeat. The new build retains per-source readable-path data that the old model ignored, but these variable point samples do not isolate its exact allocation cost. Whole-library/queue restoration and periodic full-queue checkpoints remain the existing scaling limits; this update does not claim to redesign them.

Raw startup/feature CSVs, installed APK hashes, launch logs, frame/memory reports, and playback screenshots are retained under `app\build\reports\performance\api33\fine-baseline-180\`, `fine-updated-190\`, and `fine-baseline-repeat-180\`. Reproduce with `scripts\Measure-Performance.ps1`, using `-Runs 3 -FeatureRuns 2 -SampleSeconds 20 -TrackCounts @(0,2000,10000) -Features @('minimal','spectrum30','slideshow')` and a distinct label for each installed APK.

### Historical 1.7.0 / 1.7.1 cold starts

The previously delivered 1.7.0 debug APK and the 1.7.1 debug APK were compared on the same isolated Android 15/API 35 x86_64 emulator, at 1024 x 600 and density 160 with software graphics. Each APK was reinstalled before its batch. Each scenario used one excluded warm-up followed by five force-stopped, process-cold launches, with no method profiler attached. Settings were identical: night theme, lyrics enabled, online lookup disabled, and spectrum enabled. The larger case used 2,000 synthetic saved library/queue references, not a physical music drive.

| Saved library and queue | 1.7.0 first display, median | 1.7.1 first display, median | Reduction | 1.7.1 usable controls, median |
| --- | --- | --- | --- | --- |
| Empty | 1,922 ms | 1,377 ms | 28.4% | 1,884 ms |
| 2,000 tracks | 2,038 ms | 1,407 ms | 31.0% | 2,236 ms |

First display is Android's `am start -W` total launch time. The usable-controls measurement uses `reportFullyDrawn()` after both controller connection and paused queue restoration, then another player draw; it does not mean audio is already buffered or every optional picture/lyric is loaded. The older APK did not report this second metric, so there is no before/after usable-controls comparison. The branded OS starting window appears before these app-frame milestones without an added timer.

Raw launch output, system timing logs, and `final-baseline-0.csv`, `final-baseline-2000.csv`, `final-updated-0.csv`, and `final-updated-2000.csv` are in `app\build\reports\startup\`. Exploratory profiling runs are separate and are not included in these figures. These are process-cold emulator measurements with normal filesystem caches, not reboot-cold or physical-car guarantees. The user's five-second car observation was not directly reproduced on that hardware; its CPU, storage, firmware, and Android process-start overhead still affect real launch time.

### Optimized 1.8.0 release measurements

The final signed release was compared with the preceding optimized 1.7.3 APK on the same owned Android 13 emulator: 2 virtual CPUs, 2 GiB RAM, 1024 x 600, density 160, software graphics. No builds or other emulators ran during measurements. Playback used one synthetic PCM track, local lyrics, three generated pictures, 5-second slideshow intervals, and 2-second fades. Each feature has two 20-second samples, with a changed order in the second pass.

| Enabled features | Optimized 1.7.3 app CPU | Final 1.8.0 app CPU | Final 1.8.0 PSS |
| --- | --- | --- | --- |
| Basic playback | 2.73% | 2.24% | 37.1 MiB |
| Sharp slideshow | 13.95% | 7.47% | 47.8 MiB |
| Blurred slideshow | 13.52% | 7.76% | 38.9 MiB |
| Fire/LED spectrum, 30-fps setting | 9.24% | 8.70% | 39.7 MiB |
| Lyrics + blurred slideshow + spectrum | 19.35% | 15.53% | 41.3 MiB |

CPU is the app process's percentage of **one virtual CPU**, not the whole device. PSS is a point-in-time proportional-memory sample, not peak RAM. The slideshow rows used about **43-46% less app CPU** and the combined row about **20% less** in these batches. Small differences, including the spectrum-only comparison, should be treated as noise. A later single-sample baseline repeat reached 12.19% for spectrum and 28.05% combined, illustrating host/runtime variability. These are not physical-car power or thermal guarantees.

Performance investigation also reproduced an animation bug: extra view invalidations could create parallel spectrum redraw loops, exceeding the selected rate. A 15-fps regression produced 44 draws in 1.1 seconds before the fix. The renderer now keeps one cancellable callback, respects the selected cap despite intervening redraws, and stops scheduling when paused/hidden/detached. Fire geometry is batched rather than repeatedly changing per-cell GPU state. No features or saved preferences were removed.

Startup values below are **median first display / usable controls, in milliseconds**. Every batch excluded one warm-up. The original baseline used five measured launches per scenario; its repeat and the final release used three.

| Saved library and queue | Optimized 1.7.3 | 1.7.3 repeat | Final 1.8.0 |
| --- | --- | --- | --- |
| Empty | 636 / 731 | 662 / 748 | 785 / 870 |
| 2,000 references | 720 / 900 | 741 / 946 | 795 / 1,092 |
| 10,000 references | 745 / 1,197 | 740 / 1,137 | 899 / 1,377 |

The final startup medians were **not faster** than the prior optimized build; deferring slideshows is not presented as a measured startup speedup. Startup still includes full saved-library/queue restoration, and process/runtime/cache conditions vary. The branded starting window precedes these app-frame milestones, and picture decoding waits until controls are usable. The car's previously reported five-second launch has not been measured on that hardware.

Evidence, including installed APK hashes, is under `app\build\reports\performance\api33\release-baseline-173\`, `release-baseline-repeat\`, and `release-180-verified\`. Other `release-180-*` folders are intermediate experiments, not the published APK's results.

### Historical Android 13 performance evaluation, version 1.7.3

The debug and optimized APKs were measured on one Android 13/API 33 x86_64 emulator: 2 virtual CPUs, 2 GiB RAM, 1024 x 600, density 160, software graphics, and normal system/app animation scales. The Android 10 emulator was stopped first; no builds ran during timing batches. Each startup scenario excluded one warm-up and measured five force-stopped launches. Libraries and queues used synthetic references, with no scan of a real music disk. There was no method profiler attached.

Values below are **first display / usable controls, in milliseconds**, using the median within each batch:

| Saved library and queue | Initial debug batch | Final debug APK, reinstalled | Optimized APK | Optimized repeat immediately after final debug |
| --- | --- | --- | --- | --- |
| Empty | 868 / 1,031 | 1,566 / 1,988 | 663 / 763 | 775 / 883 |
| 2,000 tracks | 955 / 1,341 | 1,229 / 1,832 | 694 / 869 | 773 / 969 |
| 10,000 tracks | 931 / 1,589 | 1,274 / 2,439 | 691 / 1,137 | 926 / 1,423 |

The initial debug batch followed the integration suite; the final debug batch followed repackaging/reinstallation. Host load and Android runtime/profile/cache state were not frozen, so the differing batches are shown rather than claiming a precise percentage speedup. Both optimized batches had lower medians. Individual launches can be slower: the optimized repeat included a 2,478 ms usable-controls sample with 10,000 tracks. These are emulator results, not a promise of instant startup on the car. The native branded starting window appears before the first app-frame metric.

**Foreground playback costs** were measured on the optimized APK with one saved track: two 20-second samples per mode, with a changed mode order in the second pass. Audio was a real 60-second, 22.05 kHz mono synthetic PCM WAV from the separate test provider. Lyrics were local, timed lines; online lookup was disabled to avoid provider variability. Pictures were three private 1280 x 720 generated images; slideshow settings were 5-second intervals and 2-second fades. Spectrum used Fire/LED. Screenshots and persisted advancing positions confirmed actual playback and enabled effects.

| Enabled optional features | App CPU, mean, % of one virtual CPU | App PSS, mean MiB |
| --- | --- | --- |
| None: ordinary theme, no lyrics/spectrum | 2.1% | 39.9 |
| One static picture | 2.3% | 43.6 |
| Local scrolling lyrics | 4.9% | 40.8 |
| Spectrum, 15 fps | 4.9% | 42.3 |
| Spectrum, 30 fps | 6.7% | 42.9 |
| Spectrum, 60 fps | 9.5% | 43.9 |
| Sharp-picture slideshow | 13.2% | 61.4 |
| Slideshow with 60% blur | 12.4% | 44.7 |
| Local lyrics + blurred slideshow + 30-fps spectrum | 17.9% | 41.3 |

CPU uses the app process's user/system CPU ticks; 100% means one core, not the entire device. PSS is a point-in-time proportional-memory sample, not peak allocation. Garbage collection and renderer caches make the rows non-additive, especially the combined case. Guest/host graphics and other system-process costs are not included. A single tone also paints fewer spectrum bands than complex music. Treat small differences as noise and do not translate these numbers into car battery/thermal guarantees. Blur renders smaller working bitmaps, so it did **not** simply cost more memory than a sharp slideshow.

**A concrete non-visual improvement:** with 2,000 queued tracks, the saved playback JSON was 1,227,859 bytes. Basic playback produced 7,372,800 kernel-accounted write bytes in each 20-second sample, versus 16,384 bytes for a one-track queue. The service checkpoints about every five seconds and on player events; position changes currently rewrite the full queue. These Linux process I/O counters are not a direct measurement of NAND wear. Separating small position/index checkpoints from rarely changed queue metadata would reduce this work without removing any player feature.

Recommended priorities, without changing existing preferences or deleting features:

1. Use optimized release code. This became the standard exported APK in 1.8.0, without giving up features.
2. Improve saved-state handling: restore the essential current-song state first, defer safe bulk work, and stop rewriting the full queue for position-only changes. The current restore still waits for the entire library and queue, so large collections remain a startup-scaling concern.
3. For a lighter visual setup, choose a static picture instead of a slideshow, and turn spectrum off or select 15 fps. Keeping the features available is different from keeping them active.
4. Keep local lyrics unless the user prefers otherwise. They are already deferred; online lookup can take up to 25 seconds after controls are ready, but is not a synchronous launch wait. Removing lyrics is not the first startup recommendation.
5. Do not remove Settings categories, transparent borders, or the About photo just to speed launch. Those pages are lazy. The photo contributes about 0.88 MiB to the optimized APK, but is loaded only when About is opened.

Code inspection also found unquantified opportunities: FFT capture remains around 30 Hz even with a 15-fps display; Media3's tee processor still copies PCM when visualization is disabled; progress/volume polling can avoid unchanged or hidden-view updates. No such behavioral rewrites, feature removals, or preference changes were made in that evaluation. Version 1.8.0 now avoids assigning unchanged time/volume label text; the broader capture/checkpoint opportunities remain unimplemented.

Reproduce measurements with **PowerShell 7**, the installed app and test APK, and an owned, disposable Google APIs emulator. The helper refuses physical devices, requires explicit root/disposable-device acknowledgement, and replaces only the test app's data:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 root
.\scripts\Measure-Performance.ps1 -Serial emulator-5554 -DisposableDevice -Label optimized
.\scripts\Measure-Performance.ps1 -Serial emulator-5554 -DisposableDevice -Label optimized-queue2000 `
    -SkipStartup -Features minimal -FeatureTrackCount 2000
```

Keep other emulators/builds stopped while comparing. Synthetic temporary grants are renewed before, not inside, each timed launch. The helper validates launch completion, actual playing state, and persisted queue/position, records installed APK hashes, and distinguishes process `wchar` writes from kernel `write_bytes`. Raw CSVs, logs, memory/frame reports, generated fixtures, and screenshots are under `app\build\reports\performance\api33\{debug-initial,debug,optimized,optimized-repeat,optimized-queue2000}\`.

## System music controls and launcher widgets

The player advertises Android's standard music-app category, Media3 service, and legacy `android.media.browse.MediaBrowserService` endpoint. Ordinary launcher/music-control plugins can connect without a privileged media-control permission, obtain the session token and song metadata, and issue Play, Pause, Stop, previous/next, and seek commands. Media3 controllers can also change shuffle and repeat. The browser exposes the current/recent song, including a paused restored queue; selecting it resumes that queue rather than replacing it with a single song. No UI launch is required for an explicit widget Play request.

External requests are limited to standard playback commands and already-saved music references. Plugins cannot introduce arbitrary file URIs or edit the app's source folders and playlists. Select music in the app first, then select **ManaMusicPlayer** (or **小悦音乐播放器**) in the plugin's player settings if offered. Plugins based on notification-listener access need their own Android access setting; the player cannot grant that permission on their behalf.

Compatibility is with standard Android media APIs, not an OEM's private broadcasts, package-name allowlists, or CAN-bus protocol. A vendor plugin that only accepts its factory player may still require configuration or vendor support.

For plugin authors: use `MediaController.Callback` metadata/state updates for a live widget. The browser root is an entry point, not a full music catalogue; framework `MediaBrowser` clients may need to reload its children rather than relying on Media3's compatibility-layer subscription notifications. On Android 6-8, framework child-list callbacks lack caller identity required by Media3, so use the session's metadata/queue and transport controls rather than relying on that browser list. Modern framework browsing is covered on Android 10 and 13.

## Settings and playback modes

Choose a centered category in the **left-hand column**, then configure it in the **right-hand pane**. Dividers follow the selected solid, dashed, rounded, dashed-rounded, or character border style. Both columns scroll independently when necessary; the selected category and each category's scroll position survive rotation and language/theme recreation. Older saved Appearance selections reopen in General. The detail pane has a fixed category heading; the toolbar Back arrow, one-row mini-player, and unchanged navigation dock stay outside the scrolling content.

| Category | Controls |
| --- | --- |
| General | Language, Settings animation, theme/accent colors, block controls, border style/character, arc radius, brick shadow, navigation toolbar position |
| Backgrounds | Customize-theme shortcut, pictures, slideshow timing/transition, blur and tint |
| Playback | Loop mode, playback order, independent main/mini-player elapsed/remaining displays |
| Spectrum | Enable, position, bars/line, width, color-block palettes, LED/smooth bars, peaks, falloff and redraw rate |
| Lyrics | Display, orientation-specific position, current/other font sizes and colors, online search/order, cache path, smooth following and opt-in drag-to-play |
| Folders | Multiple-folder selection, pin/unpin, add, play, and browse/manage |
| About and help | Version/device information, usage instructions, and photo viewer |

- **Language:** English, Simplified Chinese, or Follow system. Changes apply immediately and persist across relaunches. Changing language or theme does not stop the service-owned player. Song titles, filenames, playlist names, and storage-provider messages are not translated.
- **Theme:** Night/dark, Day/light, Follow car/system, or Customize. Follow car uses Android's system night-mode signal; it does not read a proprietary vehicle bus.
- **Theme accent:** Keep the preset day/night color blocks, or enter a custom six-digit HEX color in General. Custom colors remain exact in both themes; native controls, Theme spectrum, and default current-line lyrics follow them. Filled controls use a contrasting black/white foreground. Apply saves, Cancel discards, and Use default removes customization. Invalid/incomplete input shows an inline error without saving or closing. The selected custom color becomes the first block when reopening. Changing accent recreates only the activity, not the service-owned player.
- **Block-style player controls:** Enabled by default. Under General, select Solid line (default), Dashed line, Arc / rounded corners, Repeated character, or Dashed arc. Arc styles reveal a **0–48dp radius** slider (default 18dp). Optional raised-brick shadow is off by default: solid lower/right faces are cached as vector paths, while the interior remains transparent. It uses no bitmap blur or extra animation. The character editor accepts one visible Unicode code point, such as `*`, `+`, or `#`; Apply saves, Cancel discards, and invalid input shows an error. Font/device support determines decorative glyphs. Controls and panels use consistent **8dp gaps**, with **12dp interior insets** for character borders. Transport buttons have individual outlines, not an overlapping second row outline. They are 64dp high normally and at least 48dp in compact layouts.
- **Navigation toolbar position:** Bottom (default), Left, Right, or Top, configured under General. Artwork is the first item, before Now playing; tapping it opens Now playing without changing playback. Side toolbars scroll on short displays so all destinations remain reachable. Settings keeps the dock and mini-player visible, and a dock tap exits Settings directly. Custom backgrounds remain continuous behind the toolbar.
- **Responsive player layout:** Landscape gives lyrics/artwork, spectrum, and song information equal column widths, separated by the same 8dp gutters as the side dock. Portrait measures controls first and gives artwork/lyrics up to 40% of the available height rather than reserving space the controls need. Compact layouts combine accessible Favorite/Details icon buttons and volume in one row, use single-line metadata with full details available on demand, and retain 48dp touch targets. Surplus height goes to information/spectrum. Side docking releases the bottom toolbar and its gap. Exceptionally small windows or large accessibility fonts may still need scrolling rather than clipped content or undersized targets. All destinations share the 48dp header and content top.
- **Main player time display:** Playing time is immediately left of the main seek bar, with total duration immediately right. Tap the left time to switch between elapsed and negative remaining time; total duration is unchanged. This preference also appears under Playback and is independent of the mini-player's time mode.
- **Rewind / forward:** The main row orders Previous, Rewind 10 seconds, Play/Pause, Forward 10 seconds, and Next. Relative seeks clamp within the current track and preserve paused/playing state; they never cross into another song. Unknown or unseekable media disables these actions. Shuffle/Repeat move to the secondary row when needed to preserve 48dp targets.
- **Settings opening / closing animation:** Fade (default), Slide, Zoom and fade, or None. Page transitions last 300 ms and respect Android's animator scaling. Rapid navigation cancels the previous animation and restores normal view transforms. The toolbar back arrow is wired on every activity creation, so language/theme changes and rotation do not lose its click handler; it returns to the previously opened player screen.
- **Mini-player time display:** Elapsed time (default) or Time remaining. Settings puts artwork/song information, Play/Pause, Next, seek, and `elapsed / total` or `-remaining / total` in one 48dp-high row. Narrow widths hide artwork/metadata before reducing the seek area; exceptionally large fonts or tiny windows allow horizontal scrolling rather than clipped controls. Other pages retain their existing mini-player layout. The total always stays the track duration. Unknown duration disables seeking; keyboard/accessibility actions use the same player seek command.
- **Loop mode:** Off finishes the queue; Single file repeats the current song; Folder repeats the current song's saved source folder, including subfolders; All selected folders repeats music from the checked sources. With no current song, Folder uses the first selected source.
- **Playback order:** Sequential/continuous follows queue order. Random/shuffle chooses a random starting song for a new queue and shuffles subsequent tracks. Tapping a specific song always starts that song. Turning shuffle off restores the queue's displayed order.
- **Select music folders:** Add any number of folders with **Add folder**, one picker visit per folder, then check several sources together and press **Apply**. New sources are selected by default. Unchecking a source keeps its music, favorites, and playlists. Overlapping sources do not duplicate tracks.

**Play selected folders** scans the selected sources, builds their combined queue, and enables All selected folders looping. **Browse and manage folders** opens the existing file browser. A failed scan reports the error without starting a partial selection.

Library playback respects folder loop scopes. With looping Off or Single file, it uses the current filtered/sorted library list. Explicit single-file, folder, and playlist actions retain the requested queue until loop mode is changed; this preserves **Play only this file** and playlist behavior. A standalone file has no inferred parent folder: select its folder through the picker to use folder looping.

Changing loop scope preserves the current song and position when it remains eligible. If it is outside the new selection, the replacement queue stays paused until Play is pressed. Selecting no folders clears scoped playback rather than silently using all folders. Queue, language, theme, and playback choices restore on the next launch without autoplay.

## Lyrics

**Settings > Lyrics > Show lyrics in the artwork area** enables/disables lyric display and lookup. Both lyric display and online fallback are enabled initially; each can be switched off separately. Choose **Left/Right for landscape** and **Top/Bottom for portrait**, independently; Left/Top remain the defaults. These choices move the existing artwork/lyrics panel, not a duplicated renderer. Disabling lyrics cancels lookup and returns to artwork. Mini-player and navigation artwork are unchanged. Loading, missing lyrics, and lookup/storage failures have explicit status text; unavailable lyrics leave the cover visible, with **Retry lyrics** for a deliberate refresh. Automatic lyric lookup/display never pauses or skips playback; the optional drag-to-play gesture is an explicit seek/play action.

The lookup order is:

1. A same-basename `.lrc`, then `.txt`, in the song's actual containing folder, ignoring extension/name case. For `Song.mp3`, use `Song.lrc` or `Song.txt`. The browser continues to show only audio and folders, never lyric documents as playable songs.
2. Embedded ID3v2.2/v2.3/v2.4 ULT/USLT text, including LRC timestamps within that text. SYLT, compressed/encrypted frames, and other containers' embedded lyric tags are not currently supported; use a sidecar file for those formats.
3. If enabled, a saved online result or an HTTPS lookup using **Kugou / 酷狗**, followed by **NetEase / 网易云音乐** when needed. Settings shows this actual order and whether online fallback is enabled. Kuwo, Baidu, and QQ are not advertised as implemented providers. These independently implemented adapters follow the title/artist/duration search and lyric-download flow inspected earlier in the user-provided Smali reference. No Smali files or provider credentials are bundled.

Search derives bounded title/artist queries from tags and, when useful, the local filename. Recognized track/disc numbers, audio extensions, and technical quality labels are removed from search names; sharing-site/URL artist tags are not mistaken for performers. Both `title - artist` and `artist - title` filenames are recognized. For example, the reported title `1562.如果你也听说-张惠妹` with artist `小高网分享` now searches for `如果你也听说` by `张惠妹`. This changes only the lookup query, not displayed metadata or music files. Legitimate numeric titles and Live/Remix recording qualifiers are retained.

The duet filename `1542. 当爱已成往事-林忆莲_李宗盛.MP3` is recognized as `当爱已成往事` by both singers. Artist separators are normalized and singer order is ignored, so a catalog entry credited to `李宗盛、林忆莲` matches. Every singer supplied by the query must be present; missing/wrong singers, different recording qualifiers, and duration mismatches are still rejected.

Only derived song names are sent; audio, artwork, document URIs, and directory paths are never uploaded. Kugou resolves a matching catalog song to its file hash before searching for lyrics, with a title-only catalog fallback and up to two matching hashes. The title-only fallback still verifies the artist. Exact normalized titles, artist matching when available, and duration checks reject likely wrong recordings; without an artist, a close known duration is required. Requests use HTTPS with normal certificate validation and bounded response decoding. Incomplete/error responses are reported as lookup failures rather than silently treated as an empty catalog. Provider availability, region restrictions, rate limits, catalog rights, and API changes can prevent a result; coverage is not guaranteed and no sign-in/authentication barriers are bypassed.

Live checks found that closely spaced Kugou catalog searches returned successful but empty lists; the same queries worked after a quiet interval, independent of the extra request headers. Catalog requests are now spaced at least **10 seconds** apart within the app process, including across track changes. The first request after an idle interval is immediate; subsequent searches can wait without blocking the UI. This is conservative pacing, not a retry loop. Waiting is cancellable, and each provider gets at most 15 seconds within the 25-second overall online-lookup deadline so a slow first provider does not consume the entire fallback budget.

SAF grants matter: select the **music folder**, not only an individual file, to read neighboring lyric files. Existing libraries discover the containing folder through provider queries on first lyric lookup; new scans/browser selections save its opaque document ID. Overlapping grants and removal of source references preserve only the remaining authorized associations. Newly added sidecars take precedence over online cache on the next lookup; **Rescan**, **Retry lyrics**, or toggle lyric display to refresh.

LRC supports multiple timestamps, hundredths/milliseconds, global offset (positive values advance display), duplicate-time translations, and line-level highlighting. UTF-8, BOM-marked UTF-16, and legacy GB18030 text are accepted. **Smooth lyric scrolling** is on by default: following uses an eased, approximately 400 ms movement instead of jumping. Turn it off for immediate following.

**Current-line and other-line appearance** have independent **12–48sp** sliders and color-block selectors. Defaults are **24sp / 20sp**, using the existing theme colors and inactive-line opacity. A custom inactive color is drawn at full opacity; Reset restores the original colors and sizes. The former percentage-enlargement option has been replaced, and existing percentage preferences migrate to the equivalent current-line font size. Only the active row keeps 10dp top/bottom padding; inactive rows use 2dp padding without reserving active-line height. Following waits for changed rows to lay out before smoothly centering the playing line. Returning from Settings recenters it, including after changing its size or recreating the screen.

**Drag lyrics to seek and play** is off by default. Dragging timed lyrics shows a horizontal guide at the actual center of the lyric area, with the selected timestamp immediately to its left, even with drag-to-play off. When enabled, releasing seeks to that line and starts playback, including from a paused state. The guide and timestamp clear on release/cancellation. Track changes, cancellation, extra pointers, hidden/stopped screens, non-seekable media, and plain text do not trigger stale seeks. With drag-to-play off, manual browsing pauses following for five seconds without changing playback. Plain text has no fabricated timing.

Reads are bounded to 256 KiB of lyric text and 4,000 lines; embedded ID3 inspection is capped at 2 MiB. Lookup uses at most six distinct title/artist variants, bounded provider candidates and response sizes, connection/read timeouts, and a 25-second overall deadline. A track/settings change cancels pending results. Successful results are cached privately for up to 30 days, at most 100 entries / 4 MiB; unsuccessful queries are held for ten minutes in memory to avoid repeated requests. Local files are always checked before that cache. **Retry lyrics** bypasses remembered misses; clearing application data is not necessary.

Lyrics Settings shows the **actual cache directory on the device**, usually `/data/user/0/com.macarmusic.app/cache/lyrics`. Cached online results are app-private JSON records, not new LRC files beside the music. Android can evict this cache; original lyric/music files are unchanged.

## Custom backgrounds and slideshows

Choose **Settings > Backgrounds**, enable Customize with the shortcut if needed, then **Choose background pictures**. The same theme can also be selected under Appearance. Android's image document picker allows selecting up to **20 pictures** together; use its multi-select gesture/control, typically a long press followed by additional selections. Some vendor pickers only return one picture. The picker supplies read access; ManaMusicPlayer copies sampled images into private storage rather than depending on permanent photo/USB grants. Choosing again replaces the gallery, never the original files. A failed or cancelled batch keeps the previous selection; partially imported private copies are cleaned up. Existing single-picture installations continue using their saved picture until it is replaced.

One picture stays still. More than one automatically enables the slideshow in the order returned by the picker, and reveals these Settings controls:

| Control | Default | Range / behavior |
| --- | --- | --- |
| Picture change interval | **5 seconds** | 1–120 seconds between transition starts |
| Transition duration (immediately below the interval) | **2 seconds** | 0–10 seconds in 0.1-second steps, capped to the interval |
| Picture transition | **Fade** | Fade, Slide, Zoom and fade, or None |

Transition time is **included** in the interval, not added to it. A 5-second interval with a 2-second fade starts a change every 5 seconds, with about 3 seconds at full visibility between transitions. Reducing the interval also caps an overlong transition. Fade crossfades over the previous picture rather than exposing an empty/black frame. None and a zero duration change immediately. Animation speed also follows Android's system animator scale.

The slideshow runs while the app is visible, including Settings and paused music; it stops its timer/animation when the activity is stopped. Initial decoding is deferred until one second after usable controls and library restoration, and transitions are capped at approximately **30 updates/second**, not every display refresh. Rotation keeps the current image index, and returning to the app starts a fresh interval. Changing unrelated settings does not restart the slideshow. A failed image read reports an error and retains the displayed picture instead of repeatedly retrying.

- **Blur 0** keeps the sampled picture sharp; **1–100** applies increasing, three-pass box blur on a small working bitmap. It is software-rendered off the UI thread on every supported Android version, without requiring Android 12's blur APIs. The preview updates on slider release.
- **Original / no tint** preserves picture colors; **Dark / black tint** or **Light / white tint** adds the selected color. The separate **Tint strength 0–100%** slider controls the overlay, not blur. Light tint uses dark text/day controls; the other modes use light text/night controls.
- The picture is one continuous background behind the toolbar, artwork/lyrics area, playback controls, lists, mini-player, Settings, and bottom navigation. Customize mode removes separate panel fills, bar shadows, and the filled navigation-selection pill. The chosen tint is applied once across the whole picture, not separately to each section; use its strength and blur to improve text contrast. Album artwork, text, and controls remain foreground content.
- The image fills the window with centered, aspect-preserving cropping; some edges may be outside the screen. Custom system bars are transparent, including after changing between black and white tint. Switching back to Day/Night restores the ordinary theme surfaces.

JPEG, PNG, and WebP are the expected image formats. Each import is capped at 20 MiB, orientation is corrected using EXIF, and each private copy is sampled to at most approximately 1,280 pixels per dimension. Images are imported/decoded off the UI thread, one at a time. Blurred rendering uses at most 480 pixels per dimension. A least-recently-used render cache holds at most **three images** and at most **12 MiB or one eighth of the app heap, whichever is smaller**. This avoids repeatedly decoding a typical three-picture slideshow without retaining a whole gallery. Pictures and settings persist; private copies remain usable after the original USB volume is removed.

## Audio spectrum

Enable **Settings > Spectrum > Show audio spectrum beside song information**. It is off initially to preserve the previous layout. When enabled, the title/artist/album/status area is split between song information and the spectrum; artwork/lyrics and playback controls remain available.

Controls are inspired by Winamp's classic visualization preferences, not by a bundled Winamp skin or plug-in:

- **Position:** Left or right of song information (default right).
- **Display and width:** Bars or Line; thick 16-band or thin 32-band layout (default 32).
- **Colors:** Classic green/yellow/red, Fire gold/orange/red (bottom to top), or the current theme accent. Classic and Fire each retain an independent set of three RGB colors.
- **Color editors:** Select Classic or Fire, then edit Bottom, Middle, or Top using color blocks or a six-digit HEX value such as `#3DCF74`. The preview updates for valid input; Apply saves and Cancel discards. Invalid input stays in the dialog with a clear error. Reset affects only the selected palette. Saved custom colors remain selectable as blocks. The same editor serves lyrics and theme accents, without RGB sliders.
- **Bar rendering:** Smooth bars or LED segments for **Classic, Theme, and Fire**. Classic/Theme share a saved rendering preference, initially Smooth; Fire retains its independent preference, initially LED. Line mode stays continuous.
- **Tap cycle:** Tapping the visible spectrum repeats **Line → Classic smooth → Classic LED → Fire smooth → Fire LED → Line**. Starting from Theme returns to Classic Line. Colors, bands, peaks, falloff, and FPS settings are retained.
- **Peaks:** Optional peak markers, with separate Slow/Medium/Fast falloff for bars and peaks.
- **Maximum redraw rate:** 15, 30 (default), or 60 frames/second. This caps scheduled interpolation/peak updates; actual cadence also depends on display refresh and load. One cancellable callback prevents unrelated UI redraws from multiplying animation loops. Audio analysis is capped at 30 updates/second.

Classic and Fire use **three smoothly blended color anchors**, with the middle anchor halfway between bottom and top. **Classic maps them to the full chart height**, so equal absolute heights have equal colors across all bars. **Fire + Bars maps them to each bar's own lit height**: a 1% bar and a 100% bar both use the entire selected bottom/middle/top gradient. Each RGB channel interpolates continuously; there are no abrupt zone boundaries. Fire defaults to gold `#FFDF69`, orange `#FF9828`, and red `#ED4529`; Classic defaults to green `#3DCF74`, yellow `#FCD34D`, and red `#F04C44`. Existing custom colors are retained.

**LED segments** uses 20 height steps, transparent gaps, a subtle dashed zero line, and optional contrasting peak caps. Classic keeps its shared chart-height gradient; Fire spans the complete gradient across each bar's lit pixels, including within a single short segment. **Smooth bars** removes the gaps. On Android 10+ canvases, all bar palettes render bars and peaks in **one colored-vertex batch**, reusing approximately 43 KiB of bounded arrays allocated only when bars are used. Android 6-9 hardware canvases do not support that batch operation, and their software vertex rendering loses precision. Those versions instead reuse a path, gradient and transform, without allocating a software bitmap layer or rebuilding shaders per bar. Line modes use a cached shared chart-height gradient. The zero line is only a reference, not fabricated audio. Theme rendering remains a single accent color. Very small raster heights are naturally limited by the number of physical pixels.

Media3's PCM tap forwards the original audio bytes unchanged. A bounded, conflated worker performs a 2,048-sample Hann-windowed FFT on decoded audio, combining channel powers without cancelling opposite-phase stereo. Bands cover approximately 20 Hz to 20 kHz, limited by the recording's sample rate. This is a visualization, not an equalizer or calibrated measurement. The tap is before system volume, so muted playback can still show a spectrum.

No microphone, audio-recording permission, global audio capture, or synthetic/random animation is used. Analysis runs only while playback is active and an enabled Now Playing spectrum is visible. Pausing, seeking/changing tracks, opening Settings, or backgrounding clears stale data. Outputs that bypass the PCM processor may show **No PCM data** while playback continues. Real device/output behavior still needs head-unit testing.

## About photo

Open **Settings > About and help**. The supplied `Screenshot 2026-10-12 174201.png` appears at the upper right. Tap it to open the original **878 × 932** image in a full-window viewer. Pinch or double-tap to zoom, drag an enlarged image to pan, or use Zoom in, Zoom out, and Fit. Zoom is limited to 1–8 times the fit-to-window scale, and panning stops at the image edges. Back/Close returns to About without changing playback. About content and its photo are loaded only when that category opens.

The original PNG is bundled locally as `app\src\main\res\drawable-nodpi\about_photo.png`, without downsampling or density scaling. Zoom cannot add detail beyond the supplied resolution. No photo-library permission is needed. The image is part of the APK and source, so distributing either also distributes this photo.

## Audio formats and metadata

Container recognition and audio decoding are separate. The app uses Media3's progressive local extractors and the device's available audio decoders, not an added FFmpeg decoder bundle.

| Local format | Expected support and limitations |
| --- | --- |
| MP3 (`.mp3`) | Standard MP3, with ID3 metadata where the platform retriever can read it. |
| AAC (`.aac`) | ADTS AAC; profile, channel-count, and sample-rate support depend on the device. |
| AAC in MP4 (`.m4a`, `.m4b`) | Unencrypted AAC audio. An M4A extension does not imply that arbitrary contents, such as ALAC, are decodable. |
| FLAC (`.flac`) | Standard FLAC through Media3/platform decoding; unusual bit depths, rates, and broken vendor decoders may be limiting. |
| PCM WAV (`.wav`, `.wave`) | Common PCM WAV. WAV can contain non-PCM codecs; those are not universally supported. |
| Ogg Vorbis (`.ogg`, `.oga`) | Vorbis in Ogg when the platform decoder is available. |
| Ogg Opus (`.opus`, `.ogg`, `.oga`) | Opus in Ogg when the platform decoder supports it. Other Opus containers are not promised. |

The browser accepts known audio MIME types plus these extension fallbacks, including uppercase extensions and providers that report `application/octet-stream`. Unsupported files, virtual documents, DRM-protected content, WMA, DSD, playlists-as-media, and arbitrary codecs/containers are not claimed to work.

Metadata uses Android's `MediaMetadataRetriever`, including ID3 and equivalent supported container tags. Missing or malformed tags fall back to useful filenames. A file may play successfully even when the head unit cannot extract some tags or artwork. Embedded cover images are decoded on demand, downsampled to at most approximately 512 pixels per dimension, limited to 8 MiB compressed, and cached in a 4 MiB bitmap cache.

References: [Media3 supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats), [Android supported media formats](https://developer.android.com/media/platform/supported-formats).

## USB, storage, and permission behavior

- **Content-URI playback:** SAF remains the default; optional Device music reads Android's indexed audio using the version-appropriate permission described above. No raw-path playback, filesystem-wide traversal, `MANAGE_EXTERNAL_STORAGE`, or storage write permission is used.
- `INTERNET` is used for the requested HTTPS lyric lookup. Audio playback remains content-URI based; image selection uses a document grant, not `READ_MEDIA_IMAGES`. No network-state or additional runtime permission is required for lyrics or backgrounds.
- USB must be mounted by the head unit and exposed by an Android documents provider. USB power, hubs, cable quality, mass-storage support, partitioning, filesystems (including exFAT/NTFS), and vendor picker behavior are device-dependent. ManaMusicPlayer cannot mount a drive, supply a filesystem driver, or force a vendor to expose a volume.
- On Android 11+, Android often disallows selecting a whole storage root, the Download root, and `Android/data` or `Android/obb`. Select a **Music subfolder** instead. If folder selection is unavailable, try **Open file**.
- Choose music files on the device or USB through Android's picker. A third-party provider's own behavior is outside this application's control.
- SAF read grants are persisted only when the provider offers them. A provider may issue a temporary grant or refuse persistence. That is reported in the UI, and folder access is labeled temporary. Saved references may then require reselection after restart. Device music sources are instead labeled as using the music-library permission; removing their references does not revoke the shared permission used by other saved audio.
- Revocation, removal, or remounting under a different document ID requires reselection. Reconnect the same drive and **Rescan**, or choose the folder again. Renamed files get new provider IDs on some devices.
- A failed scan keeps the prior library unchanged. Even a successful empty scan does **not** erase references: unmatched tracks are marked missing. This prevents a temporarily empty/disconnected provider from silently wiping favorites or playlists.
- Scans are cancellable and committed only after traversal/metadata work completes. Safety limits are 100,000 traversed items and 64 levels; choose smaller music subfolders for larger collections. Scans are tied to the app's retained ViewModel, not a boot worker or foreground scanning service. Finishing the app cancels an incomplete scan without replacing the previous library.
- System media notifications are [exempt from Android 13's notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions). No `POST_NOTIFICATIONS` prompt is needed because the app posts only its media-session notification. FGS/media-playback and wake-lock permissions support background audio. The OS or vendor can still restrict background services or notification display.
- If Android reports fixed media volume, the slider is disabled and hardware volume controls remain the supported path.

## App data

Library and playback JSON, the imported background gallery, and settings are stored privately in the app's internal data directory. New gallery files are decoded and saved before their ordered IDs are synchronously committed; only then are superseded private copies removed. Artwork is an in-memory cache; online lyrics use bounded private cache files. Android app backup is disabled. Uninstalling or clearing app data removes ManaMusicPlayer's preferences and references, not external music or picture files.

## Validation

### 1.9.3 restored Android 6 compatibility

The exact optimized APK installed and ran on **Android 6/API 23 and Android 13/API 33**. Both passed all nine color-dialog regressions, including custom HEX entry, resizing, Cancel/Apply, saved colors and activity recreation. Real synthetic-file playback, paused launch, pause/seek, rewind/forward, navigation and all seven Settings categories also passed. This checks the released, non-debuggable R8 output, not just a developer build.

On API 23, **23 distinct focused core cases** passed across the legacy regression batches. On API 33, **11 focused core cases** passed. Both versions separately passed **nine pickerless-storage cases**, including permission denial/revocation, indexed-folder browsing and scanning, real playback, temporary single-file/background grants, Chinese labels and preservation of saved references. Legacy graphics checks cover hardware rendering and software rasterization; Android 6's older shader lookup allows a maximum difference of four levels per RGB channel, while modern software checks retain their tighter tolerance.

Lowering the manifest minimum alone was insufficient: actual Android 6 runs exposed a Material font string-pool inflation failure and a lost pre-attachment draw observer that prevented deferred startup from completing. The theme now references the system-font name through a resource, and first-draw observation is attachment-aware. Legacy MediaStore columns, cursor tinting, system-bar options, tooltips/autosizing and animator queries are version-guarded. Android 6-9 spectrum bars use reusable paths/gradients rather than unsupported hardware vertices or a full software layer.

All **158 JVM tests** passed; Android lint reported **zero errors / 25 advisory warnings**. Package checks confirmed minSdk 23, targetSdk 35, DEX 035, uncompressed/aligned DEX, valid v1/v2/v3 signatures in their applicable API ranges, identical export/checksum files and the existing signing certificate. The app has no native-library ABI restriction and remains below its 6 MiB limit.

An Android 13 upgrade from the user-confirmed installable **1.3.1** to this exact **1.9.3** retained the same UID and byte-identical library, playback and settings files at installation. Saved folders, favorites, playlists, repeated queue entries and the paused 18-second position survived. A separate unprivileged controller then played, paused, sought and changed tracks successfully. Synthetic temporary grants were explicitly reissued after installation; this fixture does not claim that temporary document grants become persistent.

Startup was compared in **1.9.2 / 1.9.3 / 1.9.2** order on the same API 33 emulator: two virtual CPUs, 2 GiB RAM, 1024 x 600, software GPU, optional visual effects off. Each cell is the median **first display / usable controls in milliseconds**, from three force-stopped launches after one warm-up for that library size. No other test emulator or build was running.

| Saved tracks | 1.9.2 before | 1.9.3 | 1.9.2 repeated |
|---|---:|---:|---:|
| 0 | 1128 / 1377 | 704 / 834 | 746 / 850 |
| 2,000 | 1051 / 1482 | 750 / 1039 | 734 / 1024 |

The repeated baseline exposes substantial host/emulator warm-up variation; the faster first comparison must not be presented as an app speedup. Against the warmed baseline, usable controls differed by -16 ms and +15 ms, and median PSS differed by +74 KiB and +223 KiB. **No material startup regression was observed in this bounded comparison.** These are process-cold, cache-warm emulator measurements, not fresh-boot car timings or a complete performance sweep of every optional effect.

Evidence: `app\build\reports\compat-api23-integration.txt` plus `compat-api23-final-corrections.txt`, `compat-api33-integration.txt`, the `compat-api23-picker-*` / `compat-api33-picker-*` and `compat-api23-optimized-*` / `compat-api33-optimized-*` reports, `compat-package-verification.txt`, `compat-upgrade\result.json`, and `compat-startup-summary.json` with raw samples in `performance\api33\compat-*`. Exact-release reports record the APK SHA-256. No physical-car installation is claimed; the original generic package-parsing error still lacks a confirmed framework API level or installer error code.

### Historical 1.9.2 custom colors and Settings polish

The final optimized APK passed the framework-only color regression on both **Android 10/API 29 and Android 13/API 33**, at **1024 x 600** and compact **360 x 640**. All nine selectors retain their blocks through repeated width changes and support custom HEX preview, invalid-input rejection, Cancel, Apply, reopening saved colors, and custom-accent activity recreation. The same APK passed actual synthetic-file playback, pause/seek, transport controls, seven Settings categories, About, and navigation on both versions.

The developer APK passed **14 focused integration cases on each Android version**, plus **six compact-window cases on each** at 640 x 360 / 360 x 640. Coverage includes native custom accents in both themes, typed colors that match a preset, legacy Appearance-state restoration into General, centered labels and every divider style, solid shadow edges with transparent interiors, alpha handling, narrow/large-font mini-player measurements, all four dock positions, seeking, and playback continuity. The framework runner uses the activity's localized strings, including AppCompat languages on Android 10.

All **157 JVM tests** passed. Android lint reported **zero errors / 25 existing advisory warnings**. Export checks confirmed non-debuggable R8/resource-shrunk packaging, minSdk 29, the established signing certificate, identical APK copies, and the 6 MiB budget. No dependency was added. Settings and color dialogs remain lazy; brick edges use cached vector paths rather than blur bitmaps, and custom control tinting is not part of per-frame rendering. No physical car/USB or new startup benchmark is claimed.

Evidence: `app\build\reports\polish-api29-final.txt`, `polish-api33-final.txt`, `polish-api29-compact.txt`, `polish-api33-compact.txt`, and the `polish-api29-optimized-*` / `polish-api33-optimized-*` reports. Each optimized report records the exact APK SHA-256.

### 1.9.1 color-dialog hotfix

The 1.9.0 optimized APK reproduced `IllegalArgumentException: columnCount must be greater than or equal to the maximum of all grid indices` when the color grid was measured wide and then narrow. Android's GridLayout had retained resolved cell spans from its earlier measurement. The shared picker now clears those spans before changing its column count, preserving the palette and current selection. This covers spectrum, lyric, and theme-accent selectors without adding dependencies or startup work.

The exact 1.9.1 optimized APK passed the new regression on Android 13 at 1024 x 600 and compact 360 x 640: all three Classic colors, all three Fire colors, both lyric colors, and theme accent, with repeated 8/3/6/1/5/8-column measurements, cell-bound checks, selection, Cancel, and Apply. Its regular optimized playback/navigation smoke also passed. All 154 JVM tests passed and lint reported zero errors / 25 advisory warnings. The signer and APK size remain unchanged; the hotfix installed directly over 1.9.0. No physical car test or new full Android 10/13 suite is claimed for this narrowly scoped patch.

Evidence: `app\build\reports\color-crash-before.txt`, `color-crash-before-log.txt`, `color-crash-after-api33.txt`, `color-hotfix-api33-portrait.txt`, and `color-hotfix-api33-smoke.txt`. With the framework-only runner built and installed as described below, run the color regression using:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 shell am instrument -w -r `
    -e colorPickerOnly true com.macarmusic.app.test/com.macarmusic.app.OptimizedSmokeInstrumentation
```

The regression uses an owned disposable emulator, with system-window/task animations temporarily disabled and restored afterward, just like the regular UI helper; in-app animator effects remain enabled.

### 1.9.0 baseline

| Check | Result |
| --- | --- |
| Optimized export and packaging | Successful; non-debuggable, R8/resource-shrunk, minSdk 29, targetSdk 35; 5,576,930 bytes with the existing signing identity |
| JVM tests | 154 passed, no failures/errors |
| Full developer-APK integration | All 59 tests passed on Android 10/API 29 and all 59 on Android 13/API 33 |
| Compact native layouts | Seven targeted cases passed on each version at 640 x 360 / 360 x 640, including dock placement, portrait controls, selection, Settings, and color blocks |
| Final mini-player spacing | Two additional Android 13 cases passed after the final normal-layout gap/character-padding adjustment |
| Pickerless compatibility | All nine checks passed separately on Android 10 and Android 13 |
| Android lint | Zero errors; 25 advisory warnings |
| Exact optimized APK | Framework-only smoke passed on both versions against the final `A151AD5F...` artifact: paused launch, actual PCM playback, seek, rewind/forward, five pages, eight Settings categories, About, and exiting Settings through navigation |
| 1.8.0 to 1.9.0 in-place update | Preserved app UID and byte-identical library, queue, and settings files on installation; restored the legacy source, favorite, playlist, and three-entry queue paused at index 1 / 18 seconds |
| Independent system controls | Exact final APK accepted discovery and Play/Pause/seek/next/previous/Stop from a separate-UID framework client with `trusted=false`; real audio position advanced and duplicate queue occurrences stayed intact |

The full suites preceded the last small mini-player spacing adjustment; that adjustment received the additional targeted coverage and both exact-final-artifact smoke runs. Performance measurements above also used the exact final APK, not the larger debug build.

Current evidence is in `app\build\reports\fine-api29-integration-final.txt`, `fine-api33-integration-final.txt`, `fine-api29-compact-final.txt`, `fine-api33-compact-final.txt`, `fine-mini-spacing-final.txt`, `fine-compatibility-api29\`, `fine-compatibility-api33\`, `fine-api29-optimized-smoke-final.txt`, `fine-api33-optimized-smoke-final.txt`, and `fine-upgrade\result.json`. Packaging evidence is in `fine-release-apk-manifest.txt` and `fine-release-apk-signature.txt`; current JVM/lint reports use the standard paths listed below.

New regression coverage includes legacy lyric-size and playlist migration, independent lyric colors/sizes and orientation placement, palette/shape cycling, theme accent, dashed arcs/shadow, readable locations, playlist folder refresh and failed-scan retention, pin guards, library multi-selection, timed notice replacement/dismissal, current-occurrence queue scrolling, and transport seek limits. A 2,000-ID selection/recreation test keeps activity saved state below 16,384 bytes by retaining selection outside the saved-state Bundle.

### Historical 1.8.0 baseline

The following results belong to the published **1.8.0** release, not to later source changes:

| Check | Result |
| --- | --- |
| Optimized release and separate debug builds | Successful; subsequent developer/test builds do not change the standard release APK hash |
| Focused JVM tests | 144 passed |
| Android 13 / API 33 integration | 49-test full-suite run passed at 1024 x 600; three final startup/layout cases and three final spectrum cases also passed, including the new GPU-window/rate-cap regression |
| Compact Android 13 layout | Six targeted cases passed at 640 x 360 / 360 x 640, covering all docks, on-screen portrait controls, lyric centering/enlargement, drag guides, and time controls |
| Android 10 / API 29 behavior | 15 targeted tests passed: startup, documents, playback/background, paused restoration, language/theme, independent controllers, responsive layout, Fire colors, lyrics, slideshow caching and animation rate; all three final spectrum cases also passed |
| Pickerless compatibility | All 9 checks passed separately on Android 10 and Android 13 with DocumentsUI disabled: fallback contracts, real permission denial/approval, indexed-folder playback, images, Chinese labels, revoked access, and an empty catalog |
| Startup and existing features | First-draw staging, no launch autoplay, external controllers, responsive layout, Settings, lyrics, spectrum, slideshows, and the photo viewer passed in the full suite |
| Android lint, including API compatibility | No errors; 22 warnings |
| Packaged manifest | minSdk 29; `READ_MEDIA_AUDIO` on Android 13+, with `READ_EXTERNAL_STORAGE` capped at API 32; no all-files, write, image/video, package-inventory, or microphone permission |
| APK signature and output verification | Valid for Android 10+; existing development signer retained; standard/optimized APKs and checksum match |
| Actual optimized APK on Android 10 and 13 | Framework-only smoke passed on both: non-debuggable/min29, paused launch, actual PCM playback, pause/seek, all five pages and seven Settings categories |
| Independent controls against the final optimized APK | Separate-UID client with `trusted=false`: discovery, cold paused three-track queue, advancing playback, pause, seek, next, previous and stop |
| In-place update | Installing final 1.8.0 over 1.7.3 preserved the app UID and exact library, queue and settings file hashes |
| Native UI regression coverage | English/Chinese, portrait/landscape, every dock, equal gutters, 48dp touch targets, per-bar Fire/fixed-height Classic gradients, all four transparent borders, compact inactive lyrics, guide-adjacent time, photo viewer and saved-theme launch handoff |

Run the focused JVM suite, APK build, and API/manifest lint:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Reports:

- `app\build\reports\tests\testDebugUnitTest\index.html`
- `app\build\reports\lint-results-debug.html`
- `app\build\outputs\logs\manifest-merger-debug-report.txt`
- `app\build\reports\release-api33-integration.txt`
- `app\build\reports\release-api33-compact.txt`
- `app\build\reports\release-api33-final-layout.txt`
- `app\build\reports\release-api29-integration.txt`
- `app\build\reports\release-api33-spectrum-final.txt`
- `app\build\reports\release-api29-spectrum-final.txt`
- `app\build\reports\release-compatibility-api33\`
- `app\build\reports\release-compatibility-api29\`
- `app\build\reports\performance\`
- `app\build\reports\release-api33-optimized-smoke.txt`
- `app\build\reports\release-api29-optimized-smoke.txt`
- `app\build\reports\release-external-controller\result.txt`
- `app\build\reports\release-upgrade-preservation.txt`
- `app\build\reports\startup\`
- `app\build\reports\release-apk-manifest.txt`
- `app\build\reports\release-apk-signature.txt`
- `app\build\reports\test-screenshots\`
- `app\build\reports\picker-compatibility-screenshots\test-screenshots\`

Instrumented integration tests include a **test-APK-only** read-only document provider that generates synthetic PCM WAV audio, local LRC/TXT lyrics, and two distinct pictures; the production APK contains no fixture provider or sample library. The tests exercise real SAF document queries, recursion/filtering, metadata, disk persistence, temporary/persisted grants, disconnected-storage retention, playback/seek, UI play/pause, media-stream volume, queue operations, media keys, foreground media notification, paused restoration, screen-off/background playback, and native navigation/layout. Settings tests switch English/Chinese and day/night during playback, verify single-song looping and random/sequential order, apply multiple folder selections, and confirm scoped queues and preserved library references. JVM tests also enforce complete translations and matching format arguments. A missing-file test verifies that playback stops instead of automatically skipping to an available track.

Startup regressions observe the actual first draw: only the player exists, lyric work has not started, and a cold playback service has not yet been initialized. They then verify enabled controls, a restored paused queue/position, on-demand page creation, hidden-library sorting avoidance, and selected-page recreation. A five-second synthetic lyric-provider delay cannot block Play or Settings navigation; changing tracks, disabling lyrics, and backgrounding reject cancelled results without stopping audio. Other checks cover latest-track activation, cancelled draw callbacks, the pre-Android-12 branded drawable, Android 12+ splash attributes, and the saved day-theme handoff.

Pickerless tests disable the standard DocumentsUI package only on the disposable emulator, then exercise real Android permission-controller Allow/Don't allow buttons. Synthetic audio is inserted into MediaStore by the separate test-APK UID, so the player needs an actual audio grant rather than implicitly owning the files. Checks cover multiple/nested folders, similarly named siblings, escaped query prefixes, metadata, playback, selected-folder queues, disk persistence, recreation, and an unmounted-volume error. Real runtime-permission revocation restarts the app without erasing its saved sources, favorites, or playlists. An empty index has an explicit explanation. Ordinary file-manager results are intercepted in tests but use real readable content URIs; audio and private-image imports run through the real activity-result callbacks. Failed primary launches reproduced the late-cancelled-result race before separate fallback channels fixed it.

Compatibility fixtures write and later delete only their own generated MediaStore audio. On Android 6-9 the separate test APK has a bounded legacy write permission for these synthetic fixtures; the production app has no write permission or fixture code and never modifies music. JVM tests cover the Android 6 minimum, permission selection across API 23-36, the legacy manifest cap, modern and legacy folder grouping, directory boundaries, and literal SQL-prefix escaping. Chinese UI checks also verify that stored indexed-folder labels follow the selected language without rewriting the saved library.

The earlier API 35 results and 1.7.1/1.7.3/1.8.0 timings remain historical results, not runtime testing of every Android version for 1.9.0.

Lyric regressions cover legacy-library parent discovery, opaque SAF folder IDs, local-before-online precedence, synthetic online responses and cache restoration, unreadable sidecars, bounded failed lookups, seeking, track changes, and the display switch. Query regressions reproduce both reported filenames, including reversed/separator-varied duet credits, preserve numeric titles and recording versions, test filename fallback without sending paths, and reject malformed provider responses. Pacing tests use a controlled monotonic clock; cancellation does not reserve another request slot. Provider-budget tests verify timeout fallback and propagation of overall cancellation. Background tests import through a temporary grant, exercise blur and tint controls during playback, recreate the activity, reopen saved settings, remove provider access, and reject invalid replacements without losing the previous picture. They also assert transparent panels across every navigation screen, full-window image bounds after a light-tint change, and restored ordinary-theme surfaces. JVM tests cover LRC timing/encoding/size limits, ID3 lyrics, conservative provider matching and duration units, Base64 decoding, background effects, and backward-compatible persistence.

New UI regressions reproduce the missing Settings back handler before its fix, then verify navigation after recreation, language, and theme changes. They exercise every Settings/slideshow animation with Android animations enabled, measure the default 5-second interval and 2-second fade, verify gallery persistence and legacy-picture replacement, and check pause/resume behavior. An intercepted document-picker result exercises the real activity-result callback for multiple selections, duplicate/non-URI entries, single-image results, and cancellation; it does not qualify every vendor's picker UI. Partial import failures retain the previous gallery.

Canvas-pixel checks verify interpolated custom colors, continuous gradients inside Fire LED segments, segment gaps versus smooth rendering, and transparent unlit space. Mixed-height checks compare 1%, 50%, and 100% bars: Fire remaps the complete palette to each lit extent, whereas Classic retains one chart-height scale. Color-block editor tests exercise Apply, Cancel, palette-specific Reset, and independent Classic/Fire persistence. Theme smooth/LED checks verify the selected accent and one drawing batch. Each border style is rasterized to verify distinct geometry, the requested color, and zero-alpha interiors; dashed-arc checks also compare radii and the offset shadow. Settings regressions tap every category, verify left/right geometry, independent scroll restoration, selected-category persistence, bilingual theme changes, and the original return screen.

Navigation tests exercise every edge with the spectrum visible, first-item artwork, scrolling to the last sidebar destination on short displays, recreation, and unchanged queue/position/play state. Geometry checks measure the full height released by side docking, equal landscape columns, consistent 8dp gaps, all seven transport controls and 48dp touch targets, and identical toolbar/content-top coordinates across destinations. Settings remains separate from the dock and mini-player. Relative-seek tests check ten-second steps, endpoint clamping, and paused/playing preservation; Queue checks the current occurrence in a long repeated-song list. Timed lyric tests cover smooth following, independent sizes/colors, legacy preference migration, both placement axes, disablement, cache-location text, opt-in release-to-seek/play, and stale-gesture protection. The centered drag guide keeps its timestamp immediately to the left and clears on release/cancellation. Photo tests verify upper-right placement, original bitmap dimensions, zoom/fit/pan/back actions, and unchanged playback.

Mini-player tests drag the seek bar, perform accessibility seeking, switch elapsed/remaining time without changing the total, and confirm paused playback stays paused. A track-change-during-drag regression first reproduced an unintended seek into the new song; the fixed behavior ignores that stale drag and allows the next valid seek. Spectrum tests verify a real 440 Hz PCM signal, unchanged audio bytes, both panel positions, configurable display controls, and capture clearing on pause/hide/background. Pure JVM tests cover FFT frequency/amplitude behavior, DC/silence, opposite-phase stereo, non-finite samples, peak decay, time formatting, and option limits.

System-control regressions use framework `MediaBrowser` and `MediaController` in a separate test-APK process and UID with no privileged media access. They cover discoverability, Play/Pause/Stop, previous/next, seeking, live metadata callbacks, media-ID and saved-URI playback, rejection of unselected URIs, and recent-song queue/position resumption without opening the player's UI. The original connection rejection was reproduced before the fix. Branding is checked in both language resources and the packaged APK.

Boot a disposable test emulator, then use its actual serial:

```powershell
.\scripts\Test-Emulator.ps1 -Serial emulator-5554
```

The helper uses `ANDROID_HOME` (or `-SdkPath`), builds and installs both APKs, checks the actual JUnit result, and retrieves screenshots. It temporarily disables only emulator system-window/task transitions, restoring their original values in `finally`; in-app animator effects remain enabled. This prevents Android 10/13 input-window races after rotation or forced lifecycle transitions. Screenshot capture waits for the app UI queue rather than requiring a playing media session to stop publishing accessibility updates. Border checks allow antialiased small glyphs while still checking visible outlines and transparent interiors. The helper refuses physical devices. The standard Gradle `:app:connectedDebugAndroidTest` task is also available for explicitly authorized test environments.

The optimized APK needs the separate framework-only smoke runner: the ordinary debug AndroidX runner expects Kotlin classes under names that R8 legitimately changes. Do not weaken production shrinking rules just to satisfy that runner. On the disposable emulator:

```powershell
.\gradlew.bat --no-daemon -PoptimizedSmoke=true :app:assembleDebugAndroidTest
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 install -r .\dist\optimized\manaplayer.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 shell am instrument -w -r `
    com.macarmusic.app.test/com.macarmusic.app.OptimizedSmokeInstrumentation
```

Require `INSTRUMENTATION_RESULT: result=PASS`, not just an ADB exit code. Omitting `-PoptimizedSmoke=true` when rebuilding restores the normal JUnit runner. Both runners and the synthetic provider exist only in the test APK.

To reproduce pickerless firmware on a **fresh, disposable API 29+ emulator**, including system-package restoration and owned-fixture cleanup:

```powershell
.\scripts\Test-StorageCompatibility.ps1 -Serial emulator-5554 -DisposableDevice
```

This helper replaces the test app's library, temporarily disables the emulator's standard DocumentsUI, grants/revokes its audio permission, and refuses physical devices. It restores initially enabled picker packages in `finally`. Optional `-BaselineApk C:\path\to\preceding-manaplayer.apk` also reproduces the old banner before installing the current candidate. Do not use an emulator containing music references you want to retain. Compatibility tests are opt-in when using the generic Gradle instrumentation task.

The live-provider checks are separate from the hermetic tests and are skipped unless explicitly enabled. After installing both APKs on the disposable emulator, run:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 shell am instrument -w -r `
    -e liveLyrics true -e class com.macarmusic.app.LiveLyricsSmokeTest `
    com.macarmusic.app.test/androidx.test.runner.AndroidJUnitRunner
```

These use three fixed public-song queries, including both reported filenames, not the device library. They never log lyric text or provider access keys. The previous **1.6.0** Android run, after adding conservative catalog pacing, retrieved **52 timed lines** for the numbered `如果你也听说` example, **63** for `晴天`, and **77** for `当爱已成往事`, all from Kugou. The duet check uses the matching catalog recording's 279-second duration; the user's actual MP3 was not accessed. Closely spaced requests had reproduced successful-empty catalogs before the pacing fix. The shared limiter is exercised by running all three tests together, not by adding test-only sleeps.

Those 1.6.0 results establish successful lookup for those recordings in that environment, not universal catalog/region availability. Public-provider requests were not repeated for this release; provider protocols and matching were not changed. The successful run did not need NetEase, whose live availability is not established here; its fallback protocol is covered by deterministic tests. `G:\smali` was unavailable during the 1.6.0 lyric recheck, which used the earlier inspection and provider responses rather than claiming a fresh comparison of that folder. No unrelated recording is downloaded to make a failed search appear successful. Local lyrics, cached results, artwork, and audio playback remain available independently of public services.

Device tests modify ManaMusicPlayer's **test-device** library. Do not run them against a personal library you want to retain. They never access a physically attached USB drive; the provider fixtures are synthetic and isolated.

API 23 / Android 6.0 is the restored compatibility floor. Version-specific runtime results are recorded above; not every Android release or vendor firmware has been tested separately. The car's generic package-parsing message did not supply a framework API level or native installer error, so its exact cause remains unconfirmed. The system-information screen alone does not establish that framework API. No physical car, USB disk, Bluetooth accessory, speaker output, specific head-unit control plugin, OEM navigation/audio-focus behavior, CAN-bus, or every-codec qualification was performed. Installation, USB/provider availability and codec support must still be checked on the intended head unit.

## Persistence and recovery

`library.json` and `playback.json` use atomic replacement, and unknown/corrupt saved schemas are reported instead of silently overwritten. If the library cannot be decoded, it stays read-only to preserve evidence; restart after resolving a transient internal-storage problem. In a debug build, an experienced developer can use `adb -s SERIAL shell run-as com.macarmusic.app` to inspect/back up these files. Preserve the originals before any manual recovery. Do not clear application data as the first troubleshooting step.

Queue positions are checkpointed on player events and approximately every five seconds during the service lifetime. Sudden power removal or forced process termination can lose the most recent checkpoint interval; no mobile application can guarantee a disk write after power is cut.

## Code layout

- `model`: serializable records and pure filtering, sorting, library/playlist, and queue operations.
- `data`: SAF and permission-gated MediaStore queries, background scanning/metadata/artwork, atomic JSON storage, and repositories.
- `lyrics`: bounded LRC/ID3 parsing, local-first lookup, HTTPS provider adapters, matching, and caching.
- `playback`: content-only media data source, local cover loader, MediaItem mapping, service-owned player/session, and bounded PCM/FFT spectrum analysis.
- `ui`: lifecycle-aware MediaController activity, retained ViewModel, background slideshow/transitions, spectrum rendering, native lists/dialogs, and responsive layouts.
- `src\test`: focused JVM behavior/persistence tests.
- `src\androidTest`: synthetic documents provider and instrumented integration tests.

Source repository: [afantima2024/manaplayer](https://github.com/afantima2024/manaplayer). The `manaplayer1010` tag preserves the version 1.2.0 baseline. Generated APKs, local archives, build caches, and machine-specific SDK settings are not tracked.
