# ManaPlayer / 小悦播放器

A native Kotlin music player for **standalone / aftermarket Android head units running Android 6.0 (API 23) or newer**. Install the APK directly on Android. This is **not Android Auto** and **not an Android Automotive OS app**.

**Set up storage, browse, search, and manage playlists only while parked.** Standard Android/Bluetooth media keys are supported; proprietary CAN-bus and steering-wheel protocols are outside the app's scope.

## Features

- Multiple saved internal-storage or USB music folders, using Android's Storage Access Framework (SAF), with a persistent multi-folder playback selection.
- Folder/file navigation, recursive scanning and folder playback, rescan, and single-file selection. Selecting a file in the folder browser plays that file only. Library playback uses the selected loop scope.
- Local title, artist, album, duration, track number, year, genre, and embedded-art extraction, where available. Filenames supply missing titles. Metadata and document operations run off the UI thread. Artwork is decoded on demand at a bounded resolution and cached separately, not embedded in the library database.
- Play/pause, previous (restart after the usual threshold), next, seek, queue reordering/removal, sequential or random order, single-file/folder/selected-folders looping, and Android media-stream volume.
- Library search and sorting, favorites, and named local playlists: create, add songs, remove songs, play, remove playlist, and save the current queue as a playlist.
- A single service-owned Media3 ExoPlayer, a MediaLibrarySession, and MediaController-driven native UI. Playback can continue in the background and with the screen off, with a media notification, Android music-widget controls, and standard headset/Bluetooth media controls.
- Android audio focus handling (pause/duck according to the other app's request), becoming-noisy handling, and explicit playback errors. An inaccessible file **stops playback; the app never enters an automatic skip/retry loop**. Reconnect storage and press Play to retry, or choose another track.
- Atomic, app-private persistence for library references, folder selection, favorites, playlists, and the last queue/position. Language, theme, playback mode, and sort preferences also persist. Launch restores the queue **paused, without preparing media or requesting audio focus**. There is no boot autoplay.
- High-contrast night/day/system themes, large controls, a mini-player outside Now Playing, and portrait/landscape layouts.
- A native Settings page with **English**, **Simplified Chinese**, or the system language; theme; loop mode; playback order; and music-folder selection.

## Build

The complete project is in `C:\Users\afma\CopilotProjects\MaCarMusic`.

The app is named **ManaPlayer** in English and **小悦播放器** in Chinese. Version **1.2.0** retains the application ID `com.macarmusic.app` and the existing source folder so an in-place update signed with the same key preserves settings, saved folders, playlists, and the playback queue.

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
.\gradlew.bat --no-daemon :app:assembleDebug
```

On the development machine the existing compatible JDK is `C:\Users\afma\.jdks\jbr-21.0.11`, and the SDK is `C:\Users\afma\AppData\Local\Android\Sdk`.

**Debug APK output:**

```text
C:\Users\afma\CopilotProjects\MaCarMusic\dist\manaplayer.apk
```

`:app:assembleDebug` automatically exports this filename through `:app:exportDebugApk`. Android Studio's standard intermediate APK remains under `app\build\outputs\apk\debug\app-debug.apk`.

The first build downloads the pinned Gradle distribution and dependencies.

For a production release, configure your own private signing key, keep credentials outside source control, and build `:app:assembleRelease`. The default release build is unsigned. Debug builds are for local evaluation and sideload testing, not a store-ready release.

## Install and use

Copy `manaplayer.apk` to the head unit and open it with its local package installer, or, after **you** enable developer options/USB debugging and authorize your own device:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s YOUR_DEVICE_SERIAL install -r .\dist\manaplayer.apk
```

Always choose the intended device explicitly. Development validation does not authorize actions on connected physical devices.

1. Open **ManaPlayer / 小悦播放器** while parked.
2. Go to **Folders > Add folder**. Choose a music subfolder in internal storage or an exposed USB volume, and grant read access. The folder is scanned recursively.
3. Add further folders as needed. Tap a folder to browse it, **Play folder** to scan/play it recursively, or a file to play just that file. **Open file** uses Android's single-document picker and validates the selected audio type afterward.
4. Use a song's three-dot menu for favorite, Play next, Add to queue, Add to playlist, details, or removal of its library reference.
5. Use **Playlists > New playlist**, then **Add songs**, or **Queue > Save as playlist**.
6. Open **Settings** using the top-right gear to change language, appearance, playback modes, and selected folders.

Removing a source, song reference, or playlist **never deletes, edits, moves, or renames a music file**. Library reference removal prunes associated favorites and playlist entries. Source rescans can rediscover deliberately removed song references.

## System music controls and launcher widgets

The player advertises Android's standard music-app category, Media3 service, and legacy `android.media.browse.MediaBrowserService` endpoint. Ordinary launcher/music-control plugins can connect without a privileged media-control permission, obtain the session token and song metadata, and issue Play, Pause, Stop, previous/next, and seek commands. Media3 controllers can also change shuffle and repeat. The browser exposes the current/recent song, including a paused restored queue; selecting it resumes that queue rather than replacing it with a single song. No UI launch is required for an explicit widget Play request.

External requests are limited to standard playback commands and already-saved music references. Plugins cannot introduce arbitrary file URIs or edit the app's source folders and playlists. Select music in the app first, then select **ManaPlayer** (or **小悦播放器**) in the plugin's player settings if offered. Plugins based on notification-listener access need their own Android access setting; the player cannot grant that permission on their behalf.

Compatibility is with standard Android media APIs, not an OEM's private broadcasts, package-name allowlists, or CAN-bus protocol. A vendor plugin that only accepts its factory player may still require configuration or vendor support.

For plugin authors: use `MediaController.Callback` metadata/state updates for a live widget. The browser root is an entry point, not a full music catalogue; framework `MediaBrowser` clients may need to reload its children rather than relying on Media3's compatibility-layer subscription notifications.

## Settings and playback modes

- **Language:** English, Simplified Chinese, or Follow system. Changes apply immediately and persist across relaunches. Changing language or theme does not stop the service-owned player. Song titles, filenames, playlist names, and storage-provider messages are not translated.
- **Theme:** Night/dark, Day/light, or Follow system.
- **Loop mode:** Off finishes the queue; Single file repeats the current song; Folder repeats the current song's saved source folder, including subfolders; All selected folders repeats music from the checked sources. With no current song, Folder uses the first selected source.
- **Playback order:** Sequential/continuous follows queue order. Random/shuffle chooses a random starting song for a new queue and shuffles subsequent tracks. Tapping a specific song always starts that song. Turning shuffle off restores the queue's displayed order.
- **Select music folders:** Add any number of folders with **Add folder**, one picker visit per folder, then check several sources together and press **Apply**. New sources are selected by default. Unchecking a source keeps its music, favorites, and playlists. Overlapping sources do not duplicate tracks.

**Play selected folders** scans the selected sources, builds their combined queue, and enables All selected folders looping. **Browse and manage folders** opens the existing file browser. A failed scan reports the error without starting a partial selection.

Library playback respects folder loop scopes. With looping Off or Single file, it uses the current filtered/sorted library list. Explicit single-file, folder, and playlist actions retain the requested queue until loop mode is changed; this preserves **Play only this file** and playlist behavior. A standalone file has no inferred parent folder: select its folder through the picker to use folder looping.

Changing loop scope preserves the current song and position when it remains eligible. If it is outside the new selection, the replacement queue stays paused until Play is pressed. Selecting no folders clears scoped playback rather than silently using all folders. Queue, language, theme, and playback choices restore on the next launch without autoplay.

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

- **SAF only:** all playback references are `content://` URIs. No raw paths, storage-wide scans, `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, or `READ_MEDIA_AUDIO` permission.
- USB must be mounted by the head unit and exposed by an Android documents provider. USB power, hubs, cable quality, mass-storage support, partitioning, filesystems (including exFAT/NTFS), and vendor picker behavior are device-dependent. ManaPlayer cannot mount a drive, supply a filesystem driver, or force a vendor to expose a volume.
- On Android 11+, Android often disallows selecting a whole storage root, the Download root, and `Android/data` or `Android/obb`. Select a **Music subfolder** instead. If folder selection is unavailable, try **Open file**.
- Choose music files on the device or USB through Android's picker. A third-party provider's own behavior is outside this application's control.
- Read grants are persisted only when the provider offers them. A provider may issue a temporary grant or refuse persistence. That is reported in the UI, and folder access is labeled temporary. Saved references may then require reselection after restart.
- Revocation, removal, or remounting under a different document ID requires reselection. Reconnect the same drive and **Rescan**, or choose the folder again. Renamed files get new provider IDs on some devices.
- A failed scan keeps the prior library unchanged. Even a successful empty scan does **not** erase references: unmatched tracks are marked missing. This prevents a temporarily empty/disconnected provider from silently wiping favorites or playlists.
- Scans are cancellable and committed only after traversal/metadata work completes. Safety limits are 100,000 traversed items and 64 levels; choose smaller music subfolders for larger collections. Scans are tied to the app's retained ViewModel, not a boot worker or foreground scanning service. Finishing the app cancels an incomplete scan without replacing the previous library.
- System media notifications are [exempt from Android 13's notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions). No `POST_NOTIFICATIONS` prompt is needed because the app posts only its media-session notification. FGS/media-playback and wake-lock permissions support background audio. The OS or vendor can still restrict background services or notification display.
- If Android reports fixed media volume, the slider is disabled and hardware volume controls remain the supported path.

## App data

Library and playback JSON are stored privately in the app's internal data directory. Artwork is an in-memory cache. Android app backup is disabled. Uninstalling or clearing app data removes ManaPlayer's preferences and references, not external music files.

## Validation

Verified for the delivered debug APK:

| Check | Result |
| --- | --- |
| Debug APK build | Successful |
| Focused JVM tests | 57 passed |
| Android integration tests | 14 passed on an isolated Android 15 / API 35 x86_64 emulator |
| Android lint, including API compatibility | Passed, no errors |
| Packaged manifest | minSdk 23; targeted media-playback permissions |
| APK signature verification for API 23 | Passed with v1 and v2 signatures |
| Native UI inspection | Portrait/landscape, day/night, English/Chinese, and Settings screenshots reviewed |

Run the focused JVM suite, APK build, and API/manifest lint:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Reports:

- `app\build\reports\tests\testDebugUnitTest\index.html`
- `app\build\reports\lint-results-debug.html`
- `app\build\outputs\logs\manifest-merger-debug-report.txt`
- `app\build\reports\emulator-integration.txt`
- `app\build\reports\external-controller-integration.txt`
- `app\build\reports\apk-manifest.txt`
- `app\build\reports\apk-signature.txt`
- `app\build\reports\test-screenshots\`

Instrumented integration tests include a **test-APK-only** read-only document provider that generates synthetic PCM WAV audio; the production APK contains no fixture provider or sample library. The tests exercise real SAF document queries, recursion/filtering, metadata, disk persistence, temporary/persisted grants, disconnected-storage retention, playback/seek, UI play/pause, media-stream volume, queue operations, media keys, foreground media notification, paused restoration, screen-off/background playback, and native navigation/layout. Settings tests switch English/Chinese and day/night during playback, verify single-song looping and random/sequential order, apply multiple folder selections, and confirm scoped queues and preserved library references. JVM tests also enforce complete translations and matching format arguments. A missing-file test verifies that playback stops instead of automatically skipping to an available track.

System-control regressions use framework `MediaBrowser` and `MediaController` in a separate test-APK process and UID with no privileged media access. They cover discoverability, Play/Pause/Stop, previous/next, seeking, live metadata callbacks, media-ID and saved-URI playback, rejection of unselected URIs, and recent-song queue/position resumption without opening the player's UI. The original connection rejection was reproduced before the fix. Branding is checked in both language resources and the packaged APK.

Boot a disposable test emulator, then use its actual serial:

```powershell
.\scripts\Test-Emulator.ps1 -Serial emulator-5554
```

The helper uses `ANDROID_HOME` (or `-SdkPath`), builds and installs both APKs, checks the actual JUnit result, and retrieves screenshots. It refuses physical devices. The standard Gradle `:app:connectedDebugAndroidTest` task is also available for explicitly authorized test environments.

Device tests modify ManaPlayer's **test-device** library. Do not run them against a personal library you want to retain. They never access a physically attached USB drive; the provider fixtures are synthetic and isolated.

API 23 is the declared floor and is checked by Android lint and APK signature verification; runtime testing here was on API 35, not an Android 6 device. No physical car, USB disk, Bluetooth accessory, speaker output, specific head-unit control plugin, OEM navigation/audio-focus behavior, CAN-bus, or every-codec qualification was performed. USB/provider availability and actual codec support must still be checked on the intended head unit.

## Persistence and recovery

`library.json` and `playback.json` use atomic replacement, and unknown/corrupt saved schemas are reported instead of silently overwritten. If the library cannot be decoded, it stays read-only to preserve evidence; restart after resolving a transient internal-storage problem. In a debug build, an experienced developer can use `adb -s SERIAL shell run-as com.macarmusic.app` to inspect/back up these files. Preserve the originals before any manual recovery. Do not clear application data as the first troubleshooting step.

Queue positions are checkpointed on player events and approximately every five seconds during the service lifetime. Sudden power removal or forced process termination can lose the most recent checkpoint interval; no mobile application can guarantee a disk write after power is cut.

## Code layout

- `model`: serializable records and pure filtering, sorting, library/playlist, and queue operations.
- `data`: SAF queries, background scanning/metadata/artwork, atomic JSON storage, and repositories.
- `playback`: content-only media data source, local cover loader, MediaItem mapping, and service-owned player/session.
- `ui`: lifecycle-aware MediaController activity, retained ViewModel, native lists/dialogs, and responsive layouts.
- `src\test`: focused JVM behavior/persistence tests.
- `src\androidTest`: synthetic documents provider and instrumented integration tests.

Source repository: [afantima2024/manaplayer](https://github.com/afantima2024/manaplayer). The `manaplayer1010` tag preserves the version 1.2.0 baseline. Generated APKs, local archives, build caches, and machine-specific SDK settings are not tracked.
