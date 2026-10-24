# <img src="manaplayer.png" width="40" height="40" alt=""> ManaMusicPlayer / 小悦音乐播放器

A native Kotlin music player for **standalone / aftermarket Android head units running Android 6.0 (API 23) or newer**. Install the APK directly on Android. This is **not Android Auto** and **not an Android Automotive OS app**.

**Set up storage, browse, search, and manage playlists only while parked.** Standard Android/Bluetooth media keys are supported; proprietary CAN-bus and steering-wheel protocols are outside the app's scope.

## Features

- A lightweight **built-in file explorer** for music folders/files, background pictures, lyric-cache folders, local backup import/export and reconnecting restored songs. It uses the player's theme, accent and button borders. On Android 6-9, **Device storage / USB** can browse readable shared-storage mounts, including unindexed music, without another file-manager app. Newer Android uses granted SAF folders and indexed audio; the Android picker remains available.
- Multiple saved internal-storage or USB folders, recursive scanning/playback, incremental quick rescans and explicit full metadata rescans. **Open file** browses without importing a folder; selecting one file reads its tags and plays only that file. **Device music / 设备音乐** also retains the separate multi-folder indexed import with **Select all / Deselect all**. Library playback uses the selected loop scope.
- Local title, artist, album, duration, track number, year, genre, and embedded-art extraction, where available. Filenames supply missing titles. Metadata and document operations run off the UI thread. Artwork is decoded on demand at a bounded resolution and cached separately, not embedded in the library database.
- Play/pause, previous (restart after the usual threshold), next, **10-second rewind/forward**, seek, queue reordering/removal, sequential or random order, single-file/folder/selected-folders looping, and Android media-stream volume. Opening Queue locates the current playing occurrence, including repeated songs; selecting a song or changing tracks while browsing does not move the list. Queue and Playlist song rows share a compact, single-line title/artist/album layout, preserving existing font sizes and colors, with 8dp between the duration and More button. Normal-font rows and More targets are 48dp; larger fonts can increase row height instead of clipping.
- Library search supports literal text and Chinese pinyin initials, including **消防.mp3 → xf**. Matching covers filenames, title, artist, album and genre; multiple query words, favorites, sorting and artist/album browsing still combine normally. Local playlists support create, add/edit songs and folders, rename, refresh, play, delete, and saving the current queue or selected folders. Pin saved folders to protect their library and playlist references until unpinned.
- **Remove all folders** is available in both the Folders browser and Folder settings. After confirmation it removes all unpinned folder references in one library update, retaining pinned folders, shared songs still owned by a kept folder, and separately opened files. It never deletes storage folders or music files.
- **Library > Browse** switches between Songs, Artists and Albums using saved metadata, without a new scan. Artist/album groups are built only while that page is open; search, favorites and multi-song selection remain available within a group.
- **Restore position** recalls each individual folder or playlist's last song/time and loads it **paused**. Normal Play still starts a new selection. Collection history is kept separately from the small, five-second current-playback checkpoints.
- **General > Save local backup / Restore local backup** exports and imports a validated ZIP containing settings, custom colors, private backgrounds, music references, favorites, playlists and collection positions. Restore merges references and playlists, replaces settings and backgrounds, and never treats another installation's stored URIs as access grants.
- A single service-owned Media3 ExoPlayer, a MediaLibrarySession, and MediaController-driven native UI. Playback can continue in the background and with the screen off, with notification-shade Previous/Play-Pause/Next and expanded 10-second seek controls, Android music-widget controls, and standard headset/Bluetooth media controls.
- Standard music-app discovery, including legacy music-player choosers, plus **Open with** for local audio `content://` documents. An explicit file-open request selects and plays that file; ordinary launches, recreation and reopening the app from Recents do not replay the request.
- Four compact native home-screen widgets: controls/progress (3x1), artwork/controls (3x1), lyrics/controls (3x2), and Play/Pause (1x1), with configurable background transparency. Lyrics-widget controls are top-aligned; the current/next rows share the remaining space and use the configured lyric colors and font sizes. All share the existing playback queue. Player-only mute leaves navigation and other apps' audio unchanged.
- Android audio focus handling (pause/duck according to the other app's request), becoming-noisy handling, and explicit playback errors. An inaccessible file **stops playback; the app never enters an automatic skip/retry loop**. Reconnect storage and press Play to retry, or choose another track.
- Atomic, app-private persistence for library references, folder selection, favorites, playlists, and the last queue/position. Small position checkpoints avoid rewriting unchanged queue metadata. Language, theme, playback mode, and sort preferences also persist. Launch restores the queue **paused, without preparing media or requesting audio focus**. There is no boot autoplay.
- Staged startup: a small branded launch window replaces the blank white screen, the player draws before service connection, and optional work waits for usable playback controls. Background loading additionally waits for library restoration and a one-second grace period. Hidden Settings, library, folder, queue, and playlist pages are built only when opened.
- High-contrast night/day/car-system themes and a **Customize** picture/slideshow theme with selectable transitions, timing, independent blur, black/white tint, and tint-strength controls. Large controls, the same compact single-row mini-player on every page outside Playing, and portrait/landscape layouts.
- Transparent block outlines with solid, dashed, rounded-arc, **dashed arc**, or customizable repeated-character borders across the player, lyrics, compact progress/time group, navigation, Settings buttons and About contents. Disable **Block-style player controls** for standard controls without these decorative frames, shadows or category dividers. Rounded styles have an adjustable radius; an optional raised-brick effect adds solid lower/right edges without filling the interior.
- An artwork-first navigation toolbar that can dock at the bottom, top, left, or right. **Left/right docks always put text below the icons. Top/bottom docks put text beside icons in landscape and below in portrait.** Tap the top-left app logo to return to Playing from any page, including Settings, without changing playback. Responsive song information and spectrum use the remaining player space, including the height released by side docking. The header and screen top stay fixed when switching destinations. Elapsed/remaining time sits left of the main seek bar; total duration sits right.
- Configurable Settings page transitions. An optional, real audio spectrum shares the song-information area on either side, with **3, 8, 16 or 32 bands** and bars growing from the **bottom, left, right or top inside that same window**. Peaks, falloff and 15/30/60 Hz controls remain available. Classic and Fire share one color selection, retaining the previously saved Fire colors. Classic, Fire, and Theme accent support smooth bars and LED segments. Classic uses one chart-height color scale; Fire stretches all three colors over each individual bar. Tap to cycle through Line, Classic smooth, Classic LED, Fire smooth, Fire LED, **Off**, then back to Line. Off shows the static app icon and stops analysis/animation while remaining tappable; the master switch can hide it completely.
- Optional local-first lyrics replace the artwork in a configurable **left/right landscape** or **top/bottom portrait** panel. Current/other lines have independent font sizes and color-block selectors, initially 24sp/20sp with the existing theme colors. Only the playing line keeps extra spacing. Dragging shows a time beside the center guide; opt-in drag-to-play seeks to the released line. Online results can use bounded internal caching or a portable cache in a writable USB/document folder. Lyrics settings can inspect, clear or relocate this cache, with independent Never/1/7/30/90-day expiry choices, without deleting music or original lyrics.
- **Lyrics > Karaoke lyric highlight** optionally sweeps a separately chosen color over the current timed lyric. It is off by default and preserves the existing base font/color. Wrapped lines fill in reading order; translations sharing a timestamp progress together. The lyric widget uses the same color at its existing one-second update rate. The main mute button aligns with the volume container's leading edge without changing its 48dp touch target.
- **Available lyrics** is inline to the left of the source label. Its offline list highlights the current version, keeps version/status text on one heading, and puts the source and last timestamp inside each lyric cell. More precedes the cell; the trailing heart marks **Best** for future plays. Both controls stay aligned with the cell. Song duration above the list helps compare versions. Tap a row to use it now; tap More or long-press for Activate, Best and **Delete saved copy**, with confirmation before removal. Deleting the current copy switches to another saved/local version without an online search or interrupting music. **Search online lyrics** opens a progressive all-provider list with provider-reported durations and downloads only the tapped version. Both dialogs' Close buttons follow the selected block style. Search and **Lyric timing** remain on the source row; timing saves whole-song corrections without modifying original files.
- **Download lyrics** is available in song, folder and playlist menus and above Queue. It saves one suitable match per song for later offline playback, with cancellable progress, duplicate removal and sequential processing. Folder downloads include subfolders. Good saved lyrics and manual/Best choices are retained; clearly truncated automatic results are replaced using provider-duration and lyric-timestamp checks. Explicit internal downloads are stored outside Android's reclaimable automatic cache; the chosen expiry policy still applies.
- App-owned menus, confirmations, editors, lyric windows and all Settings selectors use native player panels with themed surfaces, shared borders, large action buttons and scrolling content. Choice and explorer rows are recycled rather than inflated for the entire library. Footer buttons wrap when needed for large fonts. Permission prompts, granting new SAF access, optional external pickers, sharing and system settings remain managed by Android.
- **Settings > About and help** shows the app logo and installed version/build, followed by feature introductions in English and Simplified Chinese. It covers playback, folders/USB, library/playlists, saved positions, backups, lyrics, spectrum, appearance and supported formats, without request history or device diagnostics. The logo is not a button and does not open a picture viewer.
- A native two-pane Settings page with **English**, **Simplified Chinese**, or system language. General includes appearance controls and a read-only **System information** dialog showing reported Android/API, patch, model, build and kernel values on demand. Centered category labels have border-style-matched dividers. Shared color selectors offer **seven rainbow blocks, five initially empty custom slots, and a "+" block**. Custom slots use RGB sliders or optional `#RRGGBB` entry, can be edited, and persist across color settings after Apply. Settings uses a single-row mini-player and leaves the main navigation dock unchanged and accessible. Operation notices dismiss automatically after ten seconds or immediately using their close button.

## Build

The complete project is in `C:\Users\afma\CopilotProjects\MaCarMusic`.

The app and Gradle project are named **ManaMusicPlayer**, with the Chinese app name **小悦音乐播放器**. Version **1.29.2** (version code **45**) retains the application ID `com.macarmusic.app`, existing source folder, and signing identity so an in-place update preserves settings, saved folders, playlists, and the playback queue. Android 6.0/API 23 compatibility remains enabled without reverting current features or release optimization. Tags `v1.27.0` and `manaplayer1023` preserve the published pre-explorer baseline; its local source-and-APK archive is `archives\manaplayer1023.zip`, with checksum `archives\manaplayer1023.sha256`. Release tag `v1.21.0` preserves the earlier published baseline; `manaplayer1019` preserves 1.20.0. Earlier baselines remain available as `manaplayer1010` (1.2.0) and `manaplayer1013` (1.9.3).

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

The current **1.29.2** APK is **5,092,187 bytes (4.86 MiB / 5.09 MB)**, unchanged in total size from 1.29.1 and still **593,920 bytes / 580 KiB / 10.45%** smaller than 1.29.0. Its SHA-256 is `E4B2BFF5691EC44B7E04A1B17D882A317ECD5C8041F69225C5C4BB173E92AF3F`. Both export locations contain that identical APK. This patch shortens the Chinese main tabs, reduces selected button contents to 120%, and improves remote focus for playback controls and the native explorer. The `en`/`zh` resource filter and both advertised app-language choices remain. Spectrum frequency, FFT processing, karaoke and artwork handling are unchanged. Focus handling adds no polling or file/metadata reads. The explorer's optional legacy write access remains capped at API 28 for backup/cache destinations. The supplied PNG and ICO are also exported to `dist\manaplayer.png` and `dist\manaplayer.ico`. Each later export updates `dist\manaplayer.apk.sha256` and must still meet the size budget.

Restoring Android 6 support retains R8/resource shrinking and explicitly keeps DEX uncompressed, as in the preceding optimized release. Lowering minSdk would otherwise change Gradle's default to compressed DEX; the smaller ZIP is not used as a substitute for preserving direct DEX mapping on modern Android. The APK uses Android 6-compatible DEX 035, not DEX 039. No dependency or optional feature was added for this compatibility update.

DEX is the compiled application code. Leaving it uncompressed permits Android to map/read it directly from the APK rather than requiring extraction of a compressed copy. The translation filter reduces resource data instead. Its size reduction is measured; it does not establish a startup-speed improvement.

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
2. Go to **Folders > Add folder** to open the built-in explorer. On Android 6-9 choose **Device storage / USB**, allow storage access, open a visible volume and music subfolder, then tap **Use this folder**. On any supported version you can open an already authorized folder or choose **Grant folder access** to authorize a new one with Android. **Device music** accesses indexed audio; the separate Folders/Settings **Device music** button retains multi-folder import.
3. Add further folders as needed. Tap a saved folder to browse it, **Play folder** to scan/play it recursively, or a file to play just that file. **Open file** uses the same explorer without scanning or saving a folder while you browse. **Locations**, **Up** and **Refresh** control navigation; Cancel leaves the library unchanged. **Android file selector** is an optional fallback, including compatible ordinary file managers for individual files.
4. Use a song's three-dot menu for favorite, Play next, Add to queue, Add to playlist, **Download lyrics**, details, or removal of its library reference. **Download lyrics** also appears in folder and playlist menus and above Queue; connect to the network for this step, then use the saved lyrics during offline playback. Choose **Never** in the relevant lyric-cache expiry setting for long-term retention.
5. Use **Playlists > New playlist**, then **Add songs**, or **Queue > Save as playlist**. **Save folders as playlist** includes selected saved roots or the currently browsed subfolder. Inside a playlist, **Update playlist** edits its songs/folders, renames it, refreshes saved folders, or deletes it.
6. In Library, long-press a song or tap **Select songs**, select multiple rows, then **Add to playlist**. **Select all shown** respects the current search/filter. Selection persists while filtering or rotating; choosing songs in this mode does not start playback. Unfinished selections are held in the retained ViewModel, not Android's size-limited saved-state Bundle, and clear when that browsing session ends.
7. Open **Settings** using the only top-right button, the gear. **About and help** is a Settings category. The navigation dock remains usable; tap any page to leave Settings directly.
8. **Settings > Folders > Pin folders** protects saved sources and their songs from removal/deselection, including deleting a playlist containing them. Unpin first when you want to remove those references.

Removing a source, song reference, or playlist **never deletes, edits, moves, or renames a music file**. Library reference removal prunes associated favorites and playlist entries. Source rescans can rediscover deliberately removed song references.

Folder playlists retain their folder associations separately from manually added songs. Refresh scans only when requested, adds discoveries, preserves manually added songs and existing saved references, and does not replace playlist membership after a failed scan. Explicitly editing/removing songs is separate from refreshing; a future refresh can rediscover an excluded song still in a linked folder. Overlapping folders deduplicate songs. Pins protect library/playlists, not the temporary playback queue.

**Song details > File location** shows a storage-volume path or the saved-folder-relative path, rather than a cryptic `content://` identifier. Android uses content URIs to grant access through storage providers; they are not ordinary filesystem paths. If a provider cannot reveal a complete path, Details shows the known folder/filename and explains the limitation instead of inventing a path. Location lookup is asynchronous and runs only when Details is opened. Songs imported from Android's index also read their complete file tags on demand here; loading failures preserve the indexed information.

**Color settings:** tap a rainbow block, then **Apply** to confirm. Tap an empty numbered slot to define it using the red, green and blue sliders; HEX entry remains optional. A defined custom block selects that color and exposes **Edit custom color**. The **+** block adds another slot. **Use color** returns a preview to the parent panel; only its **Apply** saves the selected setting and custom slots. Cancelling the editor discards that edit, and cancelling the parent discards all its draft changes. Custom slots are shared by spectrum, lyrics and theme-accent settings and loaded only when a color panel opens. **Use default** resets that setting, not the saved custom palette.

Operation notices, including scan results and errors, close after **10 seconds**. Each replacement notice gets a fresh ten-second lifetime, and its close button dismisses it immediately. Confirmation dialogs remain open until you choose an action.

Left/right navigation docks always place labels below their icons, keeping a compact 64/80dp sidebar. Top/bottom docks show labels beside icons in landscape and below icons in portrait, including short windows. Top/bottom docks normally use a 48dp-high landscape row instead of 80dp; portrait retains the 80dp stacked layout. Larger fonts can increase the row height, and narrow landscape rows scroll horizontally rather than hiding labels. Side docks scroll vertically when needed. The mini-player keeps Play, Next, seeking, and time in one row. On short windows, list headings move into the main header; search and actions share a row, preserving at least 48dp control targets. Settings stays scrollable above the mini-player and dock. Long compact notices can be read by long-pressing their text. Exceptionally short landscape player panels can scroll rather than shrinking controls below their usable size.

### Built-in explorer and head units without a system picker

All local file/folder selection starts in one native explorer. Music selection shows audio, background selection shows images, and backup restore shows ZIP files. Folder selection hides files. Image checkboxes support up to 20 selections across folders; navigation and selections survive activity recreation. Backup export accepts a filename, adds `.zip` when necessary and refuses an existing name instead of overwriting it. There are no general-purpose rename, move or delete controls.

| Location | Availability and access |
| --- | --- |
| **Device storage / USB** | Android 6-9/API 23-28 only. Reads OS-accessible shared-storage mount roots after a storage permission request. Can include music not indexed by Android, pictures and local lyric sidecars. |
| **Device music** | Android 6+. Uses MediaStore and requests `READ_EXTERNAL_STORAGE` through API 32 or `READ_MEDIA_AUDIO` on API 33+. Audio only; not a writable cache/backup destination. |
| **Authorized folders** | Previously granted SAF trees, including their subfolders. Selecting a child keeps the original parent grant rather than inventing a new grant. Removing one saved source does not revoke a grant still needed by another. |
| **Grant folder access / Android file selector** | Uses Android's folder/file picker when present. Grant folder access returns to the explorer for further browsing; Android file selector selects directly for the current operation. |

**No permission prompt or storage enumeration is added to ordinary startup.** Choosing a legacy storage location requests read access. Choosing it as a lyric-cache or backup destination also requests `WRITE_EXTERNAL_STORAGE`, declared only through **API 28**. Older Android versions may group read/write approval into one storage prompt. The app writes only the requested new backup and its own lyric-cache records, never music or original lyrics. Denial keeps existing library data and offers permission-settings recovery. No all-files access, photo/video permission, root, USB driver or package-inventory permission is added.

The legacy bridge exposes only app-owned `content://` documents and checks the caller UID. It confines documents to readable shared-storage roots, rejects traversal and symbolic links, excludes `Android/data`, `Android/obb` and `Android/sandbox`, and checks opened descriptors before writes. Its provider advertises no roots to external file managers. Playback, tags, local lyrics, scanning and caching continue to use the existing content-URI pipeline; no filesystem paths are passed to ExoPlayer.

**Performance:** the explorer lists only the current directory on a cancellable worker, recycles rows and does not decode artwork, read song tags or recursively scan just to browse. It adds no library dependency, playback service or periodic/background job. Closing it cancels pending work. Enumeration is capped at 100,000 entries and navigation at 64 levels. A slow provider may take time to finish an in-flight filesystem call, but stale results cannot replace a newer location. Select the intended music subfolder before importing rather than the entire disk.

**Limits:** the app cannot mount an unsupported USB filesystem, expose a vendor-hidden mount, bypass scoped storage or read private system partitions. On Android 10+, a missing system picker still prevents granting access to a new unindexed folder; existing grants and indexed audio remain usable. On Android 6-9, the legacy path can avoid that picker only for mounts that Android already makes readable/writable. A read-only USB disk cannot become a writable cache destination. Disconnecting storage or revoking access reports an error and preserves saved references; reconnect and retry.

Even on Android 9, granting both storage permissions does not necessarily allow direct writes to a secondary removable volume. Reading and playing its music can work while writing a backup or lyric cache at its root is denied. Use an authorized writable SAF folder or an internal destination in that case.

The separate **Device music** multi-folder importer remains available from Folders and Folder settings. It groups selected roots by volume, reuses indexed tags for Quick rescan and performs one library commit after a successful batch. **Select all / Deselect all** changes only that import selection. Full rescan reads actual tags; Details enriches index-only songs on demand. The index can be stale or incomplete, and genre is queried directly only on Android 11+. On legacy Android an empty rescan of a previously saved indexed folder reports unavailable media rather than erasing its references.

For adjacent LRC/TXT lyrics, add the music folder through SAF or the legacy explorer; opening a standalone file or using MediaStore alone does not add a sidecar-folder association. Embedded lyrics and saved/downloaded lyrics remain available. Different storage backends can expose separate references to the same recording.

Optional single-file/image/backup-import fallbacks try `ACTION_OPEN_DOCUMENT`, then `ACTION_GET_CONTENT`, including managers that omit `OPENABLE`. Temporary grants remain temporary; imported private backgrounds outlive them. A raw `file://` return is rejected. Separate result channels prevent a late cancellation from a failed picker launch consuming a successful fallback result. Backup export also retains the existing share fallback when no create-document handler exists.

**Settings > General > System information** reports Android/API, patch, model, build and kernel on demand without identifiers or transmission. Android 13 normally means API 33; API 28 is Android 9, so the reported head unit uses the legacy explorer path. Vendor branding can disagree with API values; this is not a definitive authenticity check.

Storage rules: [Android documents and SAF](https://developer.android.com/training/data-storage/shared/documents-files) and [shared media access](https://developer.android.com/training/data-storage/shared/media).

### Chinese-initial search

Type **xf** to find **消防.mp3**, or **zhj** for **周华健** in artist metadata. Initials are case-insensitive and combine with existing literal query words and filters. Simplified/traditional characters and alternative pronunciations are supported; this is character-initial matching, not a word-context dictionary or a new full-pinyin search mode. Polyphonic alternatives can produce additional matches. Unknown characters remain searchable literally.

The compact bundled table covers **44,359 characters**, derived from Unicode 17.0.0 Unihan `kMandarin`, `kHanyuPinyin` and `kXHC1983` readings. It is approximately **82 KiB compressed / 246 KiB in memory**, loaded once only when a Chinese-initial lookup needs it. Filtering stays on the existing background dispatcher; there is no transliteration dependency, network request, database rewrite or startup scan. The generation input is SHA-256-pinned; regeneration uses `node scripts\Generate-PinyinInitials.cjs C:\path\to\Unihan_Readings.txt`. The generated binary and Unicode License V3 notice are included in the repository and APK.

### 1.11.0 indexed-import measurements

On a disposable Android 13/API 33 emulator, the instrumented debug build imported **120 generated WAV files in 12 folders** into a fresh library for each run. Three alternating Quick/Full pairs took **110, 79, 57 ms** for Quick and **2,693, 2,022, 3,437 ms** for Full metadata reads: medians **79 ms versus 2,693 ms**. This isolates the benefit of trusting indexed tags instead of opening every file; it compares two paths in the current version, **not the previous APK or a physical car**. Incomplete indexes fall back to file reads and will not achieve the same reduction. Both paths include the library commit, but fixture creation and the initial folder chooser are outside the timer. No other emulator or build ran during these measurements.

The indexed regression also observes the actual library-file close-write events: overlapping selected roots produce **one library write**, retain both source associations, and leave saved references unchanged when cancelled before the commit. Full rescan and on-demand Details replace index-only tags with real file metadata; subsequent Quick rescans retain that richer metadata.

Evidence: `app\build\reports\indexed-benchmark-api33.txt` and `indexed-enhancements-api33.txt`. To reproduce the timing on a **disposable emulator only**, install the normal debug/test APKs and run `StorageCompatibilityTest#indexedImportBenchmarkMeasuresFreshQuickAndFullMetadataScans` with instrumentation argument `indexedBenchmark=true`. This opt-in fixture replaces that emulator's app library and creates/removes only its own indexed audio files.

## Startup behavior

The earlier launch path inflated every page, including the large Settings hierarchy, before drawing the player. Initial state collectors also rendered hidden Settings repeatedly, sorted the hidden library, and formatted every hidden queue row. Service/player initialization competed with that first frame. The ordinary DayNight launch theme could show its light starting window before the saved night/custom theme was applied. Local and online lyric I/O was already on background dispatchers; it was **not** a synchronous network wait on the UI thread.

ManaMusicPlayer uses Android's native starting window with a small music icon on a dark background, including the Android 12+ system splash attributes. There is no splash activity, timer, or minimum splash duration. The saved day/night/custom theme takes over as the actual player is created. The first player frame includes navigation and settings access; the transport shows Connecting until the service restores the saved queue. That queue is still paused and unprepared. Session binding starts after the first draw, and saved queue-to-MediaItem conversion runs off the main thread.

The activity starts lyrics, embedded artwork, and visualizer capture after the controller and paused queue are ready and a usable-controls frame has drawn. Background decoding waits for that milestone, library restoration, and a further **one second**; slideshow cycling starts only after the first picture has appeared for the requested interval. This grace period never delays Play, navigation, or the initial screen. Putting the activity in the background cancels deferred work; a changed song, disabled lyric switch, or cancelled lookup cannot publish an old lyric result. There is no automatic rescan or storage enumeration during queue restoration. External music widgets still initialize the service directly without requiring the activity or its staged UI.

Secondary pages are created on first use and retained for that activity. Hidden list rows are not rebuilt, and library sorting is subscribed only while the library is open (with a short retention window). Rotation and theme/language recreation restore the selected page and its settings category rather than constructing every page.

Saved playback restoration no longer waits for the full library: the queue and its small position checkpoint restore independently, then library references reconcile against the live queue when ready. Explicit library operations and folder-loop rebuilding still wait for readable library data. The full saved queue is still decoded; this is not a paged database or a promise that collection size has no startup cost.

Progress polling stops when paused and does not update hidden main-player controls while another page uses the mini-player. Player events still refresh controls immediately. Hardware-volume changes are checked every two seconds only while the main volume slider is visible; no volume polling runs on hidden pages or while the activity is stopped.

### 1.10.0 lightweight measurements

These are historical measurements. The Light mode preset was removed in 1.25.0; current builds use the individually configured effect and lyric settings without that override.

The delivered **1.10.0** APK (SHA-256 `883E6CA327F23176BC91794B7EA21E787EB63DBCBE08165DA5573240DEC74015`) was compared with the exact archived 1.9.3 APK in **1.9.3 / 1.10.0 / 1.9.3** order on one disposable Android 13/API 33 emulator: two virtual CPUs, 2 GiB RAM, 1024 x 600 at density 160, and software graphics. No other emulator or build ran during measurement. Each library size used one excluded warm-up and three force-stopped launches with identical synthetic library/queue data, night theme, and optional effects off.

Values are median **first display / usable controls, in milliseconds**:

| Saved library and queue | 1.9.3 before | 1.10.0 | 1.9.3 repeated |
| --- | ---: | ---: | ---: |
| Empty | 702 / 791 | 700 / 802 | 702 / 793 |
| 2,000 tracks | 786 / 1,002 | 734 / 977 | 743 / 1,003 |
| 10,000 tracks | 753 / 1,203 | 800 / 1,253 | 776 / 1,279 |

**There is no established general startup speedup.** Controls no longer depend on library completion, but decoding the full queue and Android's startup/runtime work remain. The 10,000-track result sits between the two baseline control times; small differences are not a reliable performance gain. These are process-cold emulator launches, not reboot-cold or physical-car measurements.

The strongest measured improvement is **checkpoint disk I/O**. During two approximately 20-second samples of real synthetic PCM playback with 2,000 queued tracks, 1.9.3 produced **7,290,880–8,749,056 kernel-accounted write bytes** per sample. Version 1.10.0 produced **20,480–24,576 bytes**, about **99.7% less**. Its position file was **121 bytes**; the 1,499,917-byte queue remained separate. The old queue file was 1,457,859 bytes; the new metadata-fingerprint field adds some queue storage, but no longer gets rewritten for every position update. Linux process I/O counters are not a direct measurement of NAND wear.

Optional effects were measured separately with one synthetic 22.05 kHz mono PCM track, two 20-second samples per mode, and a changed mode order in the second pass. Spectrum was Fire/LED. Combined mode used local scrolling lyrics, three generated 1280 x 720 pictures, 60% blur, five-second slideshow intervals, two-second fades and a **30 Hz spectrum**. Light mode kept that same configured spectrum rate and lyric content, using its documented temporary overrides.

| Mode | Mean app CPU, % of one virtual CPU | Mean sampled PSS |
| --- | ---: | ---: |
| Spectrum at 15 Hz | 5.1% | 40.0 MiB |
| Spectrum at 30 Hz | 7.4% | 39.8 MiB |
| Spectrum at 60 Hz | 9.9% | 40.4 MiB |
| Combined effects, 30 Hz spectrum | 14.0% | 41.8 MiB |
| Same configuration with Light mode | 7.85% | 44.4 MiB |

Light mode used about **44% less app CPU** in this workload while keeping the 30 Hz spectrum. It did **not** reduce sampled RAM here: a sharp static picture uses a larger bitmap than the reduced-resolution blurred renderer. PSS is not peak memory, and CPU excludes other guest/host graphics processes. A single tone also lights fewer bands than complex music. These results are not device-wide power, temperature or car-startup guarantees.

Raw CSVs, installed APK hashes, launch logs, memory/frame reports and synthetic screenshots are under `app\build\reports\performance\api33\lite-final-{193-before,110,193-after,visuals}\`. Earlier `lite-*` batches describe intermediate builds, not the final artifact. Run the checkpoint/startup workload with `scripts\Measure-Performance.ps1 -Runs 3 -TrackCounts @(0,2000,10000) -FeatureRuns 2 -Features minimal -FeatureTrackCount 2000`; for current effect modes use `-SkipStartup -FeatureTrackCount 1 -Features @('spectrum15','spectrum30','spectrum60','combined')`. Both require the explicit disposable-emulator parameters shown below. The retired Light mode comparison requires the historical implementation.

### 1.9.0 performance baseline

This and the older measurement sections below are historical. The checkpoint, restore, analysis-rate, and idle-work changes described for 1.10.0 supersede their then-unimplemented proposals.

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

**A concrete non-visual improvement identified in 1.7.3:** with 2,000 queued tracks, the saved playback JSON was 1,227,859 bytes. Basic playback produced 7,372,800 kernel-accounted write bytes in each 20-second sample, versus 16,384 bytes for a one-track queue. The service checkpointed about every five seconds and on player events; position changes then rewrote the full queue. These Linux process I/O counters are not a direct measurement of NAND wear. Separating small position/index checkpoints from rarely changed queue metadata was proposed to reduce this work without removing any player feature.

Priorities proposed in that historical evaluation, without deleting features:

1. Use optimized release code. This became the standard exported APK in 1.8.0, without giving up features.
2. Improve saved-state handling: restore the essential current-song state first, defer safe bulk work, and stop rewriting the full queue for position-only changes. The then-current restore waited for the entire library and queue.
3. For a lighter visual setup, choose a static picture instead of a slideshow, and turn spectrum off or select 15 fps. Keeping the features available is different from keeping them active.
4. Keep local lyrics unless the user prefers otherwise. They are already deferred; online requests are asynchronous and cancellable, not a synchronous launch wait. Removing lyrics is not the first startup recommendation.
5. Do not remove Settings categories, transparent borders, or the About photo just to speed launch. Those pages are lazy. The photo contributes about 0.88 MiB to the optimized APK, but is loaded only when About is opened.

That inspection also found unquantified opportunities: FFT capture stayed around 30 Hz even with a 15-fps display, and progress/volume polling could avoid unchanged or hidden-view updates. Version 1.8.0 avoided assigning unchanged time/volume labels. Version 1.10.0 implements selected-rate analysis, smaller checkpoints and reduced idle polling. Media3's PCM tee processor remains installed, including its buffer handling when visualization is disabled; it was deliberately not removed or bypassed.

Reproduce measurements with **PowerShell 7**, the installed app and test APK, and an owned, disposable Google APIs emulator. The helper refuses physical devices, requires explicit root/disposable-device acknowledgement, and replaces only the test app's data:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 root
.\scripts\Measure-Performance.ps1 -Serial emulator-5554 -DisposableDevice -Label optimized
.\scripts\Measure-Performance.ps1 -Serial emulator-5554 -DisposableDevice -Label optimized-queue2000 `
    -SkipStartup -Features minimal -FeatureTrackCount 2000
```

Keep other emulators/builds stopped while comparing. Synthetic temporary grants are renewed before, not inside, each timed launch. The helper validates launch completion, actual playing state, and persisted queue/position, records installed APK hashes, and distinguishes process `wchar` writes from kernel `write_bytes`. Raw CSVs, logs, memory/frame reports, generated fixtures, and screenshots are under `app\build\reports\performance\api33\{debug-initial,debug,optimized,optimized-repeat,optimized-queue2000}\`.

## Collection browsing, saved positions and local backups

**Library > Browse** selects Songs, Artists or Albums. Names are grouped case-insensitively with outer whitespace removed; missing tags appear under Unknown artist/album. Albums use artist plus album title, so identically named albums by different artists stay separate. Compilation tracks with different artist tags therefore appear in separate album groups. Open a group to search, sort, play or select its songs; Back returns to the group list. Grouping uses the existing catalogue and stops when Library is hidden. It does not fetch artwork or reread tags.

Folder playback (including a single file opened in its browser) and playlist playback record a collection identity. **Restore position** uses the current catalogue to find the remembered song, preserving playlist order or the current folder sort. It loads paused, and it reports a missing/unmatched song rather than automatically choosing another. An artist/album selection is not recorded as a folder/playlist. Appending music outside the active collection ends that collection's tracking instead of replacing its remembered position with an unrelated song. Existing periodic current-playback checkpoints still run every five seconds; collection-history writes happen on collection changes, paused seeks and pause/stop-related events, not on every UI progress tick.

**Settings > General > Save local backup** opens the built-in explorer. Choose a writable folder and a new ZIP filename; an existing file is never overwritten. **Android file selector** retains the system Save document option and, if it is absent, the existing share-panel fallback. Save the copy **outside app storage** before uninstalling; a shared preparation file in app cache is not a permanent backup. The sharing provider exposes only its dedicated temporary-export folder, not the private library or settings files.

**Restore local backup** selects a ZIP in the explorer, validates the format and shows a confirmation. It merges library references, favorites and playlist contents without removing existing entries; an existing playlist keeps its name, and a separate playlist with a colliding name gets a numeric suffix. App display/playback settings, language, custom color slots and private background images are replaced. Music files, lyric files/cache, Android permission decisions and the operating system's media volume are not included. Copy your music separately when moving devices.

On a new installation, restored tracks initially remain unavailable references rather than reusing possibly unrelated `content://` IDs. Add and scan the matching music folder through the explorer or Device music, tap the restored folder, and choose **Reconnect restored folder**. Matching uses its source-relative paths, falling back to a unique filename only when no path was saved; ambiguous/unmatched entries remain available for another attempt. **Locate music file** in a restored song's menu uses the explorer to reconnect a single file. Favorites, playlist membership, pins and saved-position aliases follow reconnection. Playlist folder paths are resolved again through the selected provider instead of replaying foreign document IDs.

The versioned ZIP accepts at most 16 MiB of metadata, 20 private PNG backgrounds of at most 8 MiB each, and 1,024 saved color slots. It rejects malformed/unsupported data, non-local music references, duplicate/unexpected archive entries, missing images and unsafe archive paths before changing preferences or the library. Extraction and validation run on demand off the UI thread. Library writes are atomic; settings are rolled back if the library commit fails. A failure to save collection positions after the library/settings commit is reported explicitly as a partial restore. No music files are deleted during backup, restore or reconnection.

## System music controls and launcher widgets

### Native home-screen widgets

Long-press an empty area of your Android home screen, open **Widgets**, find **ManaMusicPlayer / 小悦音乐播放器**, and drag a widget onto the desktop. **Settings > General > Home screen widgets** also offers a pin request on Android 8+ launchers that support it; older or unsupported launchers show the manual instructions.

| Widget | Grid | Contents |
| --- | --- | --- |
| 1. Playback controls | 3x1 | Previous/restart, Play/Pause, Next, player mute and a bottom progress bar |
| 2. Artwork and controls | 3x1 | The same controls/progress, with an album thumbnail behind Play |
| 3. Lyrics and controls | 3x2 | Top-aligned controls/progress, with current and next lyric rows below |
| 4. Play / Pause | 1x1 | Only a Play/Pause button |

Select music in the app first. Adding widgets does **not** start playback; an explicit Play can restore the saved queue without opening the player. Multiple widgets stay synchronized. Previous follows the same restart/previous-track behavior as the main controls. **Mute affects this player only**, is remembered, and can also be toggled with the main player's speaker button. Moving the main media-volume slider above zero unmutes the player.

Tap the background, lyric or progress to return to Now Playing without autoplay. The artwork Play tile toggles playback. The progress bar is a **display, not a draggable seek control**; seek in the app. Compact widgets omit visible title/time rows, retaining song identity and progress in accessibility descriptions. An unavailable cover uses the app logo. The lyric widget works independently of the full-screen lyric switch. Timed lyrics show the current and following line, or the current line and its simultaneous translation; plain lyrics show their first two lines. Separate single-line text views prevent a long first line from consuming both rows; long text is ellipsized, and a missing second line is hidden. It reads **local or already-cached lyrics only** and never starts an online lookup itself. Passive reads do not wait behind an online request or replace a newer successful result with a miss. Cached online results require the existing online-lyrics option; missing lyrics or folder access are indicated in the widget.

**General > Widget background transparency** controls all widget backgrounds from **0% (opaque) to 100% (transparent)**, with **50% transparency initially**; controls, lyrics and the album thumbnail keep their own opacity. The setting is saved, included in local backups, and applied on slider release rather than starting an animation or new timer. Android 6-compatible image-alpha actions are used.

The lyric widget also follows **Lyrics > Current-line / Other-line appearance**, including exact custom colors, the selected font sizes, and theme-dependent default colors. The defaults remain **24sp / 20sp**. Both rows use the space below the controls; a missing next line releases its space to the current line. Changing appearance does not resize the widget or its controls. Long lines are ellipsized rather than shrinking the chosen font; larger fonts or system font scaling can require a taller host allocation to fit both rows.

Android 12+ receives explicit target cells; older launchers use the compact minimum dimensions. Launcher cell sizes differ, so these grid requests cannot override a vendor's layout policy. **Previously placed widgets may need resizing or re-adding** after an update. Widgets resize horizontally and vertically where permitted; use more space for larger system fonts or bigger touch targets. Even the small legacy bounds (180x40dp controls/artwork, 180x110dp lyrics and 40x40dp Play/Pause) keep the controls inside the widget.

The widgets use native `RemoteViews` and the existing service-owned player, with no new permissions or dependencies. Progress is updated at most once per second while playback advances and the screen is on. It stops when paused, the screen is off, only Play/Pause widgets remain, or the last widget is removed. Artwork is bounded to a 128-pixel edge and is not resent in progress-only updates. There are no widget alarms, periodic background jobs, extra wake locks or boot autoplay.

Some car launchers do not offer Android widgets, restrict resizing, or replace them with proprietary music panels. This app cannot add widget support to such a launcher; the system-media/plugin integration below remains available.

### System and third-party music controls

**Default music app:** the player advertises `MAIN` + `APP_MUSIC`, the legacy `android.intent.action.MUSIC_PLAYER` action, and `VIEW` for local `content://` audio/OGG documents. **General > Default music player** explains the choices and opens Android's default-app settings (Android 7+) or the app-details page when that page is unavailable or on Android 6. Select ManaMusicPlayer in the device's music-app list if offered, or open an audio file in a compatible file manager and choose **ManaMusicPlayer > Always** when Android offers that choice. Android has no universal default-music role; some head units have a separate vendor picker or an allowlist that cannot be changed by an ordinary app. The button does not silently change system defaults.

**Opening audio:** external file requests use the same metadata/format validation and URI-grant handling as Open file. New requests reuse the player activity and the single playback service. Temporary grants are labeled; they are not assumed to persist. Network URLs and raw `file://` paths are not advertised or accepted. For an older file manager that only sends raw paths, use **Open file** in the built-in explorer instead: authorized folders, **Device music**, or **Device storage / USB** on Android 6-9.

**Pull-down controls:** start a song, then pull down Android's status bar. The media panel exposes Previous, Play/Pause and Next when those commands are available; expanded panels also offer Back 10 seconds and Forward 10 seconds when the track is seekable. Seeking is clamped inside the current song, including while paused. Tapping the notification returns to Now Playing without replacing the queue or starting paused playback. Android 13+ builds its system media controls from the same session commands; older Android uses the MediaStyle notification actions. Layout and expansion behavior are controlled by the OS. If missing, check the app's notification settings, its **Music playback** channel and vendor background-playback restrictions. There is no extra player, polling worker or boot-started service.

The player also advertises the Media3 service and legacy `android.media.browse.MediaBrowserService` endpoint. Ordinary launcher/music-control plugins can connect without a privileged media-control permission, obtain the session token and song metadata, and issue Play, Pause, Stop, previous/next, and seek commands. Media3 controllers can also change shuffle and repeat. The browser exposes the current/recent song, including a paused restored queue; selecting it resumes that queue rather than replacing it with a single song. No UI launch is required for an explicit widget Play request.

Media-session requests are limited to standard playback commands and already-saved music references. Plugins cannot introduce arbitrary file URIs or edit the app's source folders and playlists. Select music in the app first, then select **ManaMusicPlayer** (or **小悦音乐播放器**) in the plugin's player settings if offered. Plugins based on notification-listener access need their own Android access setting; the player cannot grant that permission on their behalf.

Compatibility is with standard Android media APIs, not an OEM's private broadcasts, package-name allowlists, or CAN-bus protocol. A vendor plugin that only accepts its factory player may still require configuration or vendor support.

For plugin authors: use `MediaController.Callback` metadata/state updates for a live widget. The browser root is an entry point, not a full music catalogue; framework `MediaBrowser` clients may need to reload its children rather than relying on Media3's compatibility-layer subscription notifications. On Android 6-8, framework child-list callbacks lack caller identity required by Media3, so use the session's metadata/queue and transport controls rather than relying on that browser list. Modern framework browsing is covered on Android 10 and 13.

## Settings and playback modes

Choose a centered category in the **left-hand column**, then configure it in the **right-hand pane**. Dividers follow the selected solid, dashed, rounded, dashed-rounded, or character border style. Both columns scroll independently when necessary; the selected category and each category's scroll position survive rotation and language/theme recreation. Older saved Appearance or Backgrounds selections reopen in General. The detail pane has a fixed category heading, except About, which omits a duplicate heading. The toolbar Back arrow, one-row mini-player, and unchanged navigation dock stay outside the scrolling content.

| Category | Controls |
| --- | --- |
| General | Language, Settings animation, appearance/accent colors, pictures/slideshow/blur/tint when Customize is selected, block controls, border style/character, arc radius, brick shadow, navigation toolbar position, default-player settings, home-screen widgets/transparency, local backup/restore |
| Playback | Loop mode, playback order, independent main/mini-player elapsed/remaining displays |
| Spectrum | Enable, window position, in-window bar origin, bars/line, 3/8/16/32 bands, color-block palettes, LED/smooth bars, peaks, falloff and redraw/analysis rate |
| Lyrics | Display, orientation-specific position, current/other font sizes and colors, optional karaoke highlight/color, online search/order, cache path, smooth following and opt-in drag-to-play |
| Folders | Multiple-folder selection, pin/unpin, add, play, browse/manage and remove all folder references |
| About and help | App logo, installed version/build and current feature introductions |

- **Language:** English, Simplified Chinese, or Follow system. Changes apply immediately and persist across relaunches. Changing language or theme does not stop the service-owned player. Song titles, filenames, playlist names, and storage-provider messages are not translated.
- **Retired Light mode:** The performance preset and its overrides were removed in 1.25.0. Old preferences/backups no longer disable configured backgrounds, blur, animations or online lookup. Individual settings and all 15/30/60 Hz choices remain. The unrelated Day/light theme is unchanged.
- **Theme:** Night/dark, Day/light, Follow car/system, or Customize. Follow car uses Android's system night-mode signal; it does not read a proprietary vehicle bus. Selecting **Customize** reveals picture, slideshow, blur and tint controls immediately below Appearance in **General**. There is no separate Backgrounds category. Other themes hide those controls without erasing saved background choices.
- **Theme accent:** Select one of seven rainbow blocks, define or edit a custom slot with RGB sliders/HEX, or use **+** to add a slot. Custom colors remain exact in both themes; native controls, Theme accent spectrum, and default current-line lyrics follow them. Filled controls use a contrasting black/white foreground. Apply saves, Cancel discards, and Use default restores the standard accent. Invalid/incomplete input shows an inline error without saving or closing. Custom slots are shared across color settings. Changing accent recreates only the activity, not the service-owned player. Accents remain single-color; directional two-color blending is not enabled.
- **Block-style player controls:** Enabled by default. General lists **Solid line / Arc solid line / Dashed line / Arc dashed line / Repeatable character**, in that order. Solid line remains the default. Settings actions/categories/dropdowns, Library/Folders/Queue/Playlists actions, recycled row More buttons and the mini-player follow the same border renderer, character, radius and shadow. Touch feedback and dropdown indicators remain available. Disable the block-controls switch to restore standard controls and hide decorative panel/navigation frames and Settings category dividers; customization remains saved. The former **None** option is no longer shown. Old preferences and backups with None migrate to disabled blocks and Solid line, preserving character/radius/shadow values. Arc styles reveal a **0–48dp radius** slider (default 18dp). Optional raised-brick shadow is off by default: solid lower/right faces are cached as vector paths with transparent interiors, without bitmap blur or extra animation. The character editor accepts one visible Unicode code point, such as `*`, `+`, or `#`; Apply saves, Cancel discards, and invalid input shows an error. Font/device support determines decorative glyphs. Controls and panels use consistent **8dp gaps**, with **12dp interior insets** for character borders. Transport buttons have individual outlines, not an overlapping second row outline. Main-app controls are 64dp high normally and at least 48dp in compact layouts. Styles are applied only when configuration changes or a recycled row needs them, not on playback-progress ticks.
- **Navigation toolbar position:** Bottom (default), Left, Right, or Top, configured under General. Artwork is the first item, before Playing; tapping it opens Playing without changing playback. Chinese tabs use **播放 / 乐库 / 队列**. Left/right docks always show centered text below the icons. Top/bottom docks use text to the right in landscape for a thinner row, and text below in portrait. Docks scroll when necessary so labels and 48dp touch targets remain available. Settings keeps the dock and mini-player visible, and a dock tap exits Settings directly. Custom backgrounds remain continuous behind the toolbar.
- **Main-window buttons:** Horizontal buttons keep their icon at the leading edge and center the text independently within the unchanged button bounds. Narrow portrait and side navigation remain stacked. Selected navigation items, Favorite, Shuffle, Repeat and player mute enlarge only their text/icon content to **120%**; unselected controls keep normal sizing. Momentary actions do not stay selected, and Settings detail controls retain their existing alignment. Long labels can be ellipsized inside fixed bounds; their full accessible labels/tooltips remain available.
- **Projector remote / D-pad:** Directional arrows move keyboard focus; OK/Enter activates the focused control or explorer row, and Back returns to the parent folder. The accent outline is keyboard focus, not the selected page/favorite/playback mode, and does not enlarge button contents. All five transport actions use the same native image-button type; rewind/forward additionally require a prepared, seekable track with a known duration and an available seek command. Disabled actions are intentionally skipped. Text buttons use Material buttons, whereas navigation and explorer entries are custom icon/text rows: being clickable is not enough to make such rows keyboard-focusable on older Android. Explorer rows explicitly accept focus on API 23+, retain it through asynchronous folder loads, and let Android handle scrolling/recycling. Completing a load does not take focus back after a user moves to the toolbar or switches to touch. Horizontal transport focus follows the actual wrapped row and checks current enabled state when a direction is pressed, without stale links after compact layouts move buttons elsewhere. These changes use native focus events, states and layout callbacks, not polling, extra file/metadata reads, FFT work, or changes to playback. Dedicated media keys remain separate from directional focus keys.
- **Remove all folders:** In either Folders entry point, confirmation shows how many unpinned folders will be removed and how many pinned folders will remain. Only the confirmed folder set is removed; a folder added afterward is not included, and a newly pinned target cancels the batch. Removed-only song references are pruned from favorites and playlist membership, while the playlists themselves remain. Separately opened files and songs shared with retained folders are kept. The operation uses one atomic library write and one pass over persisted grants, without rescanning music or invoking document deletion. It is disabled during scans/backups or when there are no saved folders. Unpin protected folders before removing them.
- **Responsive player layout:** Landscape gives lyrics/artwork, spectrum, and song information equal column widths, separated by the same 8dp gutters as the side dock. Portrait measures controls first and gives artwork/lyrics up to 40% of the available height rather than reserving space the controls need. Compact layouts combine accessible Favorite/Details icon buttons and volume in one row, use single-line metadata with full details available on demand, and retain 48dp touch targets. Surplus height goes to information/spectrum. Side docking releases the bottom toolbar and its gap. Exceptionally small windows or large accessibility fonts may still need scrolling rather than clipped content or undersized targets. All destinations share the 48dp header and content top.
- **Main player time display:** Playing time is immediately left of the main seek bar, with total duration immediately right. Tap the left time to switch between elapsed and negative remaining time; total duration is unchanged. This preference also appears under Playback and is independent of the mini-player's time mode.
- **Rewind / forward:** The main row orders Previous, Rewind 10 seconds, Play/Pause, Forward 10 seconds, and Next. Relative seeks clamp within the current track and preserve paused/playing state; they never cross into another song. Unknown or unseekable media disables these actions. Shuffle/Repeat move to the secondary row when needed to preserve 48dp targets.
- **Settings opening / closing animation:** Fade (default), Slide, Zoom and fade, or None. Page transitions last 300 ms and respect Android's animator scaling. Rapid navigation cancels the previous animation and restores normal view transforms. The toolbar back arrow is wired on every activity creation, so language/theme changes and rotation do not lose its click handler; it returns to the previously opened player screen.
- **Mini-player time display:** Time remaining (initial default) or Elapsed time. Both main and mini-player displays initially use remaining time, while existing saved choices are retained. Library (including Favorites), Folders, Queue, Playlists, and Settings all put artwork/song information, Play/Pause, Next, seek, and `elapsed / total` or `-remaining / total` in the same compact 48dp-high row, rather than a full-width second progress row. Narrow widths hide artwork/metadata before reducing the seek area; exceptionally large fonts or tiny windows allow horizontal scrolling rather than clipped controls. The main Now Playing layout and navigation dock are unchanged. The total always stays the track duration. Unknown duration disables seeking; keyboard/accessibility actions use the same player seek command.
- **Loop mode:** Off finishes the queue; Single file repeats the current song; Folder repeats the current song's saved source folder, including subfolders; All selected folders repeats music from the checked sources. With no current song, Folder uses the first selected source.
- **Playback order:** Sequential/continuous follows queue order. Random/shuffle chooses a random starting song for a new queue and shuffles subsequent tracks. Tapping a specific song always starts that song. Turning shuffle off restores the queue's displayed order.
- **Select music folders:** Add any number of folders with **Add folder**, one picker visit per folder, then check several sources together and press **Apply**. New sources are selected by default. Unchecking a source keeps its music, favorites, and playlists. Overlapping sources do not duplicate tracks.

**Play selected folders** scans the selected sources, builds their combined queue, and enables All selected folders looping. **Browse and manage folders** opens the existing file browser. A failed scan reports the error without starting a partial selection.

Fresh preferences default to **Folder loop**, **Random/shuffle order** and **Time remaining**. Explicit saved preferences, nonempty legacy playback queues and older backups keep their original choices; an empty first-launch queue does not overwrite the new defaults.

Library playback respects folder loop scopes. With looping Off or Single file, it uses the current filtered/sorted library list. Explicit single-file, folder, and playlist actions retain the requested queue until loop mode is changed; this preserves **Play only this file** and playlist behavior. A standalone file has no inferred parent folder. With Folder loop selected, tapping such a Library song still plays that file, and Play all uses the requested Library selection, with an explanatory notice instead of blocking playback. Select its folder through the picker for an actual folder scope. An empty **All selected folders** selection still never falls back to unrelated files.

Changing loop scope preserves the current song and position when it remains eligible. If it is outside the new selection, the replacement queue stays paused until Play is pressed. Selecting no folders clears scoped playback rather than silently using all folders. Queue, language, theme, and playback choices restore on the next launch without autoplay.

## Lyrics

**Settings > Lyrics > Show lyrics in the artwork area** enables/disables lyric display and lookup. Both lyric display and online fallback are enabled initially; each can be switched off separately. Choose **Left/Right for landscape** and **Top/Bottom for portrait**, independently; Left/Top remain the defaults. These choices move the existing artwork/lyrics panel, not a duplicated renderer. Disabling lyrics cancels lookup and returns to artwork. Mini-player and navigation artwork are unchanged. Loading, missing lyrics, and lookup/storage failures have explicit status text; unavailable lyrics leave the cover visible, with **Retry lyrics** for a deliberate refresh. Automatic lyric lookup/display never pauses or skips playback; the optional drag-to-play gesture is an explicit seek/play action.

**Karaoke lyric highlight / 卡拉 OK 歌词效果** is an independent, initially disabled appearance option. **Karaoke highlight color / 卡拉 OK 覆盖颜色** uses the shared color picker, initially gold; it does not replace the normal current/other-line colors. Both options persist and are included in local backups. Reset lyric appearance restores all lyric appearance defaults, including disabling karaoke.

The overlay estimates progress from the current line's timestamp to the next timestamp, using the known song duration for the final line. This is **line-timed visual interpolation, not exact word/syllable timing**; existing inline word timestamps are not retained. Saved lyric offsets, seeks and playback speed are respected. Untimed lyrics, prelude previews, blank gaps and a final line with no usable end time are not animated. Wrapped text advances in reading order within its paragraph, including right-to-left text; multiple text paragraphs at one timestamp progress together.

Only the attached, visible current lyric row requests animation redraws, at most once per 34ms, using cached text-layout geometry and clipped text drawing rather than bitmaps or per-frame list updates. Pausing/buffering freezes coverage; hiding the player, stopping the activity, recycling a row or disabling the effect stops its animation. The existing 500ms whole-player polling rate is unchanged. Widgets color complete Unicode code-point prefixes only on current text, never the future preview, using their existing one-second ticker and existing paused/screen-off gates; there is no additional background task, dependency or permission.

The normal lookup order is below. A saved **Best** version is checked first, followed by a retained timing or legacy Next selection. Best, explicitly chosen versions and songs retained with **Download lyrics** work with online lookup disabled after download, including QQ/Kuwo versions; ordinary automatic online caches still follow the online-lyrics setting. A temporary list selection does not replace the future default.

1. A same-basename `.lrc`, then `.txt`, in the song's actual containing folder, ignoring extension/name case. For `Song.mp3`, use `Song.lrc` or `Song.txt`. The browser continues to show only audio and folders, never lyric documents as playable songs.
2. Embedded ID3v2.2/v2.3/v2.4 ULT/USLT text, including LRC timestamps within that text. SYLT, compressed/encrypted frames, and other containers' embedded lyric tags are not currently supported; use a sidecar file for those formats.
3. If enabled, a saved online result or sequential HTTPS fallbacks: **Kugou direct → Kugou mobile → QQ Music → NetEase → Kuwo**. Settings lists these five routes. The adapters use public metadata search and lyric-download endpoints without accounts or bundled credentials. No Smali files, companion-app monitoring or external-player session access are included.

Search derives a bounded primary title/artist query from usable tags or a cleaned filename. Track/disc prefixes, audio extensions, technical quality labels and Windows `- Copy (n)` / `- 副本 (n)` suffixes are removed; sharing-site/URL artist tags are not mistaken for performers. Both `title - artist` and `artist - title` filenames are recognized, with a known artist used to disambiguate them. For example, `1586.演员-薛之谦 - Copy (2).mp3` now searches for `演员` by `薛之谦`, rather than treating `Copy (2)` as the singer. A usable filename can recover an unknown title even with an unrecognized uploader tag. This changes only the query, not displayed metadata or music files.

Automatic online lookup retains the supplied reference's **local-file `OnlineLyricsFetcher` query/protocol path**, not its companion/session-ID path, with the requested duration-safety correction. Title and artist equality contribute 10 points each; normalized substring matches contribute 5 each. The first search result gets one extra point, with stable first-result tie handling. Duration now contributes 8 points below three seconds difference, 5 below five, and 2 below ten on **all five routes**, after converting each provider's units. Direct Kugou retains its minimum score of 10. QQ/NetEase use the first returned singer. Each route tries at most **three ranked automatic candidates**, stopping at its first usable match rather than downloading every catalog result.

For known song durations, a provider-reported duration must differ by no more than **10% of the song, clamped to 10–30 seconds**. The downloaded LRC is checked too: its final parsed timestamp must be no earlier than the song length minus **25% of the song, capped at 60 seconds**, and no later than the song plus that 10–30 second tolerance. Boundaries are inclusive and LRC offsets/end markers count. Thus a **3:00 song rejects a 0:40 preview** and accepts final timestamps from **2:15 through 3:18**, leaving room for an instrumental outro. An absent provider duration does not bypass the LRC check. Known-duration songs do not automatically accept untimed text; unknown song durations are not fabricated. These checks also discard old, clearly mismatched automatic cache entries. Manual selections and Best versions are preserved, and the online picker still permits an explicit choice outside these automatic limits. Duration is a useful safeguard, not proof of the correct singer/recording.

Kugou/Kuwo keywords use the reference's parenthetical-title cleanup and artist-tail cleanup; QQ/NetEase use the original prepared title/artist pair. Kugou direct searches by keyword and then downloads the selected ID/access key; the separate mobile route searches its ten-result catalog and uses the selected hash download. QQ requests Base64 lyrics, while NetEase uses its GET search/lyric endpoints. QQ/NetEase can use translated lyrics when the original result is empty. Kugou and Kuwo require **at least eight timed lines**. Kuwo uses its `SONGNAME`/`ARTIST` fields, OpenAPI lyric list and centisecond formatting.

Compatibility/safety adaptations remain explicit: current direct-Kugou candidate durations are already milliseconds, so they are not multiplied as in the older reference; all requests use HTTPS, including Kuwo; no hard-coded client/account identifier from the reference is sent. Bounded JSON/Base64/text decoding, normal certificate checks and explicit transport/parse errors are retained. Only song metadata is sent, never audio, artwork, document URIs or directory paths. Provider outages, region restrictions, rate limits, catalog rights and API changes can prevent a result; coverage is not guaranteed and no authentication barrier is bypassed.

The old web-catalog cooldown and repeated filename-variant loops remain removed. For automatic lookup, each route makes at most one search and three candidate downloads. Reference-compatible **eight-second connect/read timeouts** are bounded by a 33-second route deadline and a 166-second overall ceiling, allowing all five sources a turn without blocking playback or startup. Explicit bulk downloads use shorter **eight-second route / 45-second active-search budgets** per song. Waiting for another lyric request's network lock is outside this active-search budget. There is no background retry worker; changing tracks or settings cancels ordinary playback lookup.

Folder access matters: add the **music folder** through SAF or the legacy explorer, not only an individual file, to read neighboring lyric files. Existing libraries discover the containing folder through provider queries on first lyric lookup; new scans/browser selections save its opaque document ID. Overlapping grants and removal of source references preserve only the remaining authorized associations. Newly added sidecars take precedence over ordinary automatic online cache on the next lookup; **Rescan**, **Retry lyrics**, or toggle lyric display to refresh. Best and explicit saved selections are the exception. A suitable explicit offline download can also replace a clearly timing-mismatched local result without changing the original file; clearing the active lyric cache restores normal local-first lookup.

LRC supports multiple timestamps, hundredths/milliseconds, global offset (positive values advance display), duplicate-time translations, and line-level highlighting. UTF-8, BOM-marked UTF-16, and legacy GB18030 text are accepted. **Smooth lyric scrolling** is on by default: following uses an eased, approximately 400 ms movement instead of jumping. Turn it off for immediate following.

**Current-line and other-line appearance** have independent **12–48sp** sliders and color-block selectors. Defaults are **24sp / 20sp**, using the existing theme colors and inactive-line opacity. A custom inactive color is drawn at full opacity; Reset restores the original colors and sizes. The former percentage-enlargement option has been replaced, and existing percentage preferences migrate to the equivalent current-line font size. Only the active row keeps 10dp top/bottom padding; inactive rows use 2dp padding without reserving active-line height. Following waits for changed rows to lay out before smoothly centering the playing line. Returning from Settings recenters it, including after changing its size or recreating the screen.

**Drag lyrics to seek and play** is off by default. Dragging timed lyrics shows a horizontal guide at the actual center of the lyric area, with the selected timestamp immediately to its left, even with drag-to-play off. When enabled, releasing seeks to that line and starts playback, including from a paused state. The guide and timestamp clear on release/cancellation. Track changes, cancellation, extra pointers, hidden/stopped screens, non-seekable media, and plain text do not trigger stale seeks. With drag-to-play off, manual browsing pauses following for five seconds without changing playback. Plain text has no fabricated timing.

Reads are bounded to 256 KiB of lyric text and 4,000 lines; embedded ID3 inspection is capped at 2 MiB and provider responses at 1 MiB. Each song's cache record is limited to 512 KiB. The **ordinary automatic internal cache** retains up to 100 songs / 4 MiB. Explicit offline downloads and portable caching have no collection-wide count/byte eviction; their existing expiry policies still apply. Genuine no-match results are held for ten minutes; temporary transport failures for only **30 seconds**, without background retries. Local files are checked before remembered misses and ordinary online cache; discovering an explicit override needs only a keyed cache read, not a collection scan. Cache-only widget reads do not hold or wait for the network mutex. **Retry lyrics** bypasses remembered misses; clearing application data is not necessary.

### Downloading lyrics for offline use

Choose **Download lyrics** in a song's More menu in the Library, folder browser, Queue or Playlist, in a saved folder/subfolder's More menu, or in a playlist's More menu. The Queue header also downloads lyrics for the whole current queue. Folder actions include nested folders and reuse unchanged scanned metadata. Playlist actions use its current stored membership, not an implicit linked-folder refresh. Repeated queue entries are downloaded only once.

The progress window opens immediately, shows folder preparation and then completed/total songs, the current song, and saved/already-available/no-match/failed counts. **Cancel** stops further songs and cancels the current request; completed saves remain. Rotation retains the job and window without restarting it. Music, queue order, playback position and play/pause state are not changed. There is one batch at a time; conflicting scans, cache moves/clear, backup operations and manual online selection are blocked until it finishes. Closing the app/process does not schedule a worker to restart it; running the command again reuses completed downloads.

This explicit command uses the network to obtain missing lyrics, even if automatic online lookup is disabled; it does not change that setting. Subsequent playback uses the saved lyrics offline. It keeps good cached/local matches and preserves manual choices, Best marks, timing corrections and existing retrieval ages. Clearly truncated/mismatched automatic lyrics are replaced with one suitable match, not every provider's version. A normal no-match advances to the next song. Partial provider failures remain visible in the failed count but do not abandon the collection while other routes respond. Three searches with no usable provider response stop the batch rather than retrying an offline collection indefinitely; cached songs do not reset that failure limit. A storage failure stops with an explicit error and does not count an unsaved result as downloaded.

Performance remains bounded: songs and candidate downloads are sequential, metadata/lyric work stays off the UI thread, and a batch retains only the last folder's lyric-file index (at most 4,096 entries, not lyric bodies or a whole-library index). Oversized folders use the normal uncached lookup instead of truncating results. No new dependency, service, permission, polling task or periodic download worker is added.

Internal explicit downloads are retained in private **`files/downloaded-lyrics`**, outside Android's reclaimable cache and the automatic 100-song / 4 MiB eviction limit. Existing good cached records are moved there without fetching them again. A selected USB cache is used directly instead. **Configured expiry and Clear cached lyrics still apply**, including the default 30-day internal policy; choose Never for long-term offline retention. These app-managed JSON records are not LRC sidecars for another player. They are not included in the local settings ZIP backup; uninstalling/clearing app data removes internal downloads.

### Local and downloaded versions

Tap **Available lyrics**, the list button immediately **left of the lyric source**, to see matching local `.lrc`/`.txt` files, supported embedded lyrics, retained downloads and the currently displayed result. The active version has a filled highlight and bold heading. Current / Best / Saved copy labels follow the filename/version in that heading, with the preview underneath. More is before the cell, with Best after it; all three borders stay aligned as text wraps. The 12sp source label is right-aligned **inside** the cell rather than reserving a separate column. The footer also shows **Last line**, the final parsed timestamp after LRC offsets and saved corrections, including a blank end marker. Plain text shows **No timing**. Narrow windows/large fonts stack the footer details instead of squeezing the time into a thin column. Dialog content fits the space above its buttons so the last row remains reachable by scrolling. The music duration above the list is the comparison reference; an unknown music duration is labeled Unknown. A last timestamp is not the full song length; manual selection is not blocked by the automatic timing limits. Usage guidance is omitted from this dialog. Close and nested action/confirmation buttons follow the configured block style, with their full outlines kept inside the native button panel. This list does **not** query providers or show undownloaded online candidates.

For local alternatives, keep files beside the song and use its basename or cleaned title with a punctuation/bracket suffix, such as `Song.lrc`, `Song (Alternate).lrc`, `Song - QQ.txt` or `Song.2.lrc`. Unrelated longer song names are not included. Automatic lookup still uses the exact basename first; expanded matching runs only when the list is opened. Unreadable alternatives show an issue without hiding other readable versions. Identical content from the same source is deduplicated.

Tap a version to use it for the current song visit, including through Settings, backgrounding and rotation. Tap its **heart** separately to mark it **Best**; this saves the future preference without changing the version currently displayed. Best loads when returning to the song or reopening the app. Tap the filled heart to remove that preference. These actions also work with online lookup disabled, and widgets follow the selected offline version. Original music and sidecar files are never changed.

**Tap a row's three-dot button or long-press the row** for Activate, Mark/Remove Best and **Delete saved copy**. These actions use a standard dialog instead of an anchored popup, and do not require working long-press gestures. Holding the lyric, heart, source or three-dot button opens the same actions. Deletion requires confirmation and removes that version, its saved timing and its Best preference from the active internal or USB cache. Original music, embedded lyrics and sidecar files are never deleted; a local original can remain in the list without the Saved copy label. Delete is disabled for rows without a cached copy. Deleting another version leaves the current lyrics unchanged. Deleting the active version uses the remaining Best, otherwise another saved/local version; if only its local original remains, that original is used without the deleted timing correction. No provider request is made. If nothing remains, both player and widget lyrics clear while music continues. This empty choice is retained for the current song visit; a later visit or explicit Retry can use normal lookup again.

Marking Best no longer moves or renumbers existing rows, and explicit activation retains the current song identity through UI restarts. Removal preserves surviving versions' timing, retrieval ages and order. A storage or partial-cleanup failure is reported explicitly and the list is reconciled with readable stored data; there is no silent internal-storage fallback. Older portable revisions may remain after a provider refuses cleanup; reconnect storage before retrying a cache operation.

Downloaded alternatives have been retained since 1.20.0, together with each version's timing correction. Previously overwritten versions cannot be recovered offline. A song retains at most 100 versions within its existing 512 KiB record budget; the oldest unprotected alternatives are removed first. The default, the incoming selection and Best are protected during a save. If those protected versions cannot fit, the save reports an error and preserves the previous record rather than silently dropping the selection. Each version keeps its own retrieval age: selecting, marking Best or adjusting timing does not renew it. Clear, expiry, internal eviction and Android cache reclamation also remove the corresponding saved preferences/corrections; original local files remain available. Cache size counts **songs**, not individual versions.

### Choosing another lyric match

Tap **Search online lyrics**, the search/arrow icon immediately to the right of the lyric source. The native dialog opens **before results arrive**, then appends matches from every enabled provider: Kugou direct/mobile, QQ, NetEase and Kuwo. Online lookup must be enabled. This replaces the previous one-click Next behavior; automatic local-first lookup and its sequential provider order are unchanged.

The dialog issues at most **two catalog requests concurrently** and shows up to **ten ranked matches per route** from its bounded search response, not every page of a provider's archive. Music duration stays visible above the list. Rows show title/artist, **Provider duration** when supplied (otherwise **Unknown**), and the trailing provider/route. This is the provider's reported recording duration, not a downloaded lyric endpoint. Existing rows do not jump as other sources finish. Progress updates when a route completes, without a continuous loading animation. Close uses the selected block style. A failed or timed-out source is reported without hiding successful results. Manual choices may include lower-ranked matches rejected by automatic matching.

Listing downloads **metadata only**, not lyric bodies or previews, and does not write the lyric cache. Tap any arrived row to download just that version without waiting for all providers. The current lyrics and playback stay unchanged until the chosen version is successfully downloaded and saved in the active cache. An empty result or failed download/save leaves the dialog available to try another match. Successful selection also appears in Available lyrics; it does not replace Best or the existing future default. Re-selecting an already saved version retains its original retrieval age and timing correction; a genuinely new recording starts at zero additional adjustment.

Back/Close, changing songs or disabling lyrics/online lookup cancels pending work. Rotation retains the dialog and search instead of restarting requests. Cache changes reject a stale save. The selected candidate/content history remains bounded to 100 IDs/hashes in saved records; URLs and access keys are not persisted. No eager downloads, new timer, automatic prefetch or background provider polling is added.

### Adjusting lyric timing

Tap the **clock beside the source** for synchronized lyrics. **Earlier / Later** adjust the whole song by **0.1 seconds** or **1 second** per tap, with live player preview and a limit of ten minutes in either direction. Earlier is useful when the words appear after they are sung; Later is useful when they appear too soon. **Reset timing** previews the original source timing. **Cancel** discards the preview; **Save** commits it. Changing songs or closing the editor does not apply a draft to a different song.

This follows the [LRC offset convention](https://en.wikipedia.org/wiki/LRC_(file_format)): positive milliseconds advance the lyrics; negative milliseconds delay them. The effective time is `source timestamp - (original LRC offset + saved adjustment)`, clamped to the supported timeline. Every preview is derived from the original source text, so repeated edits and Reset do not accumulate rounding or clamping losses. The player highlight, lyric seek guide and saved widget current/next lines use the adjusted timestamps. Widgets receive the committed result, not draft previews.

The adjustment is stored **with the selected lyrics in the active internal or portable JSON cache**, including for a local/embedded source. Original LRC/TXT and music files are never edited. Moving the cache preserves selections, timing and history. Clearing, expiry, internal eviction or Android cache reclamation can remove these corrections along with the lyrics; saving an adjustment does not renew an existing record's retrieval age. Plain text has no timing editor, and a whole-song offset does not correct line-by-line errors or tempo drift.

### Provider configuration for future builds

Edit **`app\src\main\java\com\macarmusic\app\lyrics\LyricProviderConfig.kt`**. The ordered `providers` list defines each provider's routes, exact HTTPS hosts and `enabled` flag. Reorder entries to change automatic lookup order, keep a provider's routes together, or set `enabled = false` to disable it. The Settings search-order display and online picker use those configured routes. This is project configuration, not an in-app URL editor.

To add a new provider protocol, add its `LyricOrigin` and `LyricSearchRoute`, implement its search/download adapter in `OnlineLyricsClient`, register the definition and hosts, and add its English/Chinese source and route labels in `MainActivity`/string resources. Preserve stable existing enum names for old cache records. Add deterministic adapter tests for matching, alternate IDs/content, timeouts and malformed responses, then rebuild/export the APK. A URL alone cannot define a different provider's JSON protocol. HTTPS certificate checks, response limits and the absence of account credentials must remain intact.

### Lyric cache storage

**Settings > Lyrics** shows the active location, cache size and independent expiry selectors. The selectors grow to fit their text and shared border padding, including larger system fonts. Internal storage shows both private locations: usually `/data/user/0/com.macarmusic.app/cache/lyrics` for ordinary automatic caching, and `/data/user/0/com.macarmusic.app/files/downloaded-lyrics` for explicitly retained offline downloads. Only the former is reclaimable by Android as cache. The page keeps only concise controls/status and the basic search explanation.

| Control | Behavior |
| --- | --- |
| Internal cache expiry | Never, 1, 7, 30 or 90 days; defaults to **30 days** for both internal locations. Never disables age expiry; the 100-entry / 4 MiB cap still applies only to ordinary automatic caching. |
| USB / folder cache expiry | The same choices; defaults to **Never**, preserving existing USB collections. |
| Check cache size | Removes expired recognized records, then counts the active cache and shows a fresh count/size notice above the scrolling page, including zero or unchanged results. With finite expiry selected, entering Lyrics settings or changing the active policy also performs this check off the UI thread, quietly on success. Unavailable storage reports an error, not a successful empty result. |
| Clear cached lyrics | After confirmation, deletes only this app's recognized cache records in the active location. Music, embedded lyrics, original `.lrc`/`.txt` files and unrelated documents are untouched. Playback continues, and already displayed lyrics can remain in memory. |
| Choose cache folder / Move internal cache | Uses the explorer to select a writable folder. Creates or reuses `ManaMusicPlayer-Lyrics`, verifies copies of committed internal records, saves the new location, then removes internal originals. A failed copy keeps the originals and previous location; verified destination copies may remain for a retry. |
| Use internal cache | Returns to internal automatic caching and private offline downloads without copying back or deleting the USB collection. Select that portable folder again to reuse it. |

Portable caches default to **Never**, without internal collection-wide count/byte limits; the per-song 512 KiB limit still applies. Finite policies expire versions independently at their exact age boundary, using saved retrieval time or a legacy file's modification time, not last access. Younger versions survive an older version's expiry. Unknown or future timestamps are retained rather than risking premature deletion. Expiry is checked when a record is read and during cache inspection; there are **no startup-wide cache scans, alarms or background USB polling**. Expired revisions cannot resurrect a stale result; deletion/pruning failures are reported. Expiry choices are included in local settings backups.

Portable files are app-specific JSON records, not LRC sidecars for other music players. A format marker identifies the dedicated folder; the app refuses to take over a nonempty unmarked folder. Hashed two-character subfolders keep normal song lookups from enumerating the whole collection. Writes use a new, read-back-verified revision before replacing older records and do not require provider rename support. Matching metadata, duration and filename reuse the cache independently of the music URI/device mount path.

Changing from one portable folder to another leaves the previous USB collection intact. Cache clearing/location changes prevent an already-running lookup from repopulating the old cache. Music-folder removal preserves a shared cache grant, and returning to internal storage retains any read grant still needed by saved music. Cache operations use background I/O, not a new service, polling job or database. Cancelling an online lookup also closes its HTTPS connection off the UI thread, including during screen rotation.

**USB writing requires either a writable legacy shared-storage mount on Android 6-9 with storage permission, or a compatible SAF folder with persistent read/write access.** The built-in explorer handles both and disables selection for read-only destinations. Device music indexing or a single-file picker cannot grant folder writes. Read-only, disconnected, full or revoked storage produces an explicit error without interrupting playback or silently redirecting writes internally. Reconnect/reselect the folder, or choose internal caching. Portable files survive an app uninstall, but access grants are not included in local ZIP backups; select the folder again after reinstalling or moving devices.

## Custom backgrounds and slideshows

Choose **Settings > General > Appearance > Customize**, then use **Choose background pictures** in the controls revealed below Appearance. Tap pictures in the built-in explorer to select up to **20**, including selections across browsed folders, then confirm the selection. **Android file selector** remains available for system-managed image selection; some vendor pickers only return one picture. ManaMusicPlayer copies sampled images into private storage rather than depending on permanent photo/USB grants. Choosing again replaces the gallery, never the original files. A failed or cancelled batch keeps the previous selection; partially imported private copies are cleaned up. Existing single-picture installations continue using their saved picture until it is replaced.

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

- **Spectrum window position:** Left or right of song information (default right); this existing setting moves the whole window.
- **Bars start from:** Bottom (the original upward-growing display), Left (rightward), Right (leftward), or Top (downward). Only the plot changes inside the existing spectrum window; its bounds, artwork and song information do not move. The title and frequency labels stay upright.
- **Display and width:** Bars or Line; **3 bands, 8 bands, 16 bands, or 32 bands** (default 32), using the same count-based labels. All 32 underlying FFT bins contribute, including the highest bins when grouped into three bands.
- **Colors:** Classic and Fire share one set of three RGB colors; Theme accent still follows the app accent. Upgrades and old backups retain the former **Fire** colors for both modes. The retired Classic colors are no longer used.
- **Color editors:** In Classic or Fire, edit the same Bottom, Middle, or Top values using color blocks, RGB sliders or a six-digit HEX value such as `#3DCF74`. Apply saves and Cancel discards. Invalid input stays in the dialog with a clear error. Reset restores the shared Fire defaults for both modes. Saved custom color slots remain selectable as blocks. When the bar origin changes, the palette follows the bar from base to tip.
- **Bar rendering:** Smooth bars or LED segments for **Classic, Theme, and Fire**. Classic/Theme share a saved rendering preference, initially Smooth; Fire retains its independent preference, initially LED. Line mode stays continuous.
- **Tap cycle:** A single tap on the visible spectrum repeats **Line → Classic smooth → Classic LED → Fire smooth → Fire LED → Off → Line**. Off draws the existing app icon without analysis, an animation callback or an extra image asset. Starting from Theme returns to Classic Line. Colors, bands, bar origin, peaks, falloff, and FPS settings are retained.
- **Double tap:** Rotate **Bars start from** clockwise: **Bottom → Left → Top → Right → Bottom**. This changes only the plot's origin, not its window position or display mode, and saves the selection. A double tap does not also trigger a single-tap mode change. Rotating while Off keeps analysis/animation stopped.
- **Peaks:** Optional peak markers, with separate Slow/Medium/Fast falloff for bars and peaks.
- **Maximum redraw/analysis rate:** **15 by default**, with **30 and 60** still selectable. Existing valid saved choices are retained. The selected value caps both scheduled interpolation/peak updates and PCM-to-FFT submissions; actual cadence also depends on audio buffers, display refresh and device load. One cancellable callback prevents unrelated UI redraws from multiplying animation loops.

Classic and Fire use **three smoothly blended color anchors**, with the middle anchor halfway between bottom and top. **Classic maps them to the full chart height**, so equal absolute heights have equal colors across all bars. **Fire + Bars maps them to each bar's own lit height**: a 1% bar and a 100% bar both use the entire selected bottom/middle/top gradient. Each RGB channel interpolates continuously; there are no abrupt zone boundaries. The shared defaults are gold `#FFDF69`, orange `#FF9828`, and red `#ED4529`. Existing Fire custom values are retained. Version-1 backups remain accepted: the two retired fields are validated and discarded while all other unknown fields still fail validation.

**LED segments** uses 20 height steps, transparent gaps, a subtle dashed zero line, and optional contrasting peak caps. Classic keeps its shared chart-height gradient; Fire spans the complete gradient across each bar's lit pixels, including within a single short segment. **Smooth bars** removes the gaps. On Android 10+ canvases, all bar palettes render bars and peaks in **one colored-vertex batch**, reusing approximately 43 KiB of bounded arrays allocated only when bars are used. Android 6-9 hardware canvases do not support that batch operation, and their software vertex rendering loses precision. Those versions instead reuse a path, gradient and transform, without allocating a software bitmap layer or rebuilding shaders per bar. Line modes use a cached shared chart-height gradient. The zero line is only a reference, not fabricated audio. Theme rendering remains a single accent color. Very small raster heights are naturally limited by the number of physical pixels.

Media3's PCM tap forwards the original audio bytes unchanged. A bounded, conflated worker performs a 2,048-sample Hann-windowed FFT on decoded audio, combining channel powers without cancelling opposite-phase stereo. Bands cover approximately 20 Hz to 20 kHz, limited by the recording's sample rate. This is a visualization, not an equalizer or calibrated measurement. The tap is before system volume, so muted playback can still show a spectrum.

No microphone, audio-recording permission, global audio capture, or synthetic/random animation is used. Analysis runs only while playback is active and an enabled Now Playing spectrum is visible. Pausing, seeking/changing tracks, opening Settings, or backgrounding clears stale data. Outputs that bypass the PCM processor may show **No PCM data** while playback continues. Real device/output behavior still needs head-unit testing.

## App logo and About

The transparent, multicolor music logo identifies the launcher, starting window, main-window title and About branding. It also replaces the generic artwork placeholder; a song's actual embedded artwork is unchanged. The title-bar artwork remains 32dp inside an accessible 48dp touch target. Tapping it opens Now Playing, including from Settings, without changing the queue or playback position. The Settings Back arrow still returns to the previously opened page.

Open **Settings > About and help** to see the logo and installed version/build above the localized feature guide. The version comes from Android's installed package metadata, not duplicated help text. The About logo has no click action, zoom controls or full-screen viewer. About content remains lazy; removing the old bundled photograph also reduces the APK.

The supplied originals are preserved as `manaplayer.png` (1024 x 1024, transparent) and `manaplayer.ico` (multiple Windows icon sizes) at the project root. Release export copies both beside the APK in `dist`. Android uses right-sized PNG variants in `app\src\main\res\drawable-mdpi` through `drawable-xxxhdpi`, plus a bounded 512px placeholder for the larger artwork area, not the full-resolution original or the ICO. These share the same artwork and transparent margins without adding a runtime image library. Earlier photo-viewer results below describe historical releases only.

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

**Quick rescan** still enumerates folders but reuses successful metadata only when the same document's filename, MIME type, positive size and modification time are unchanged. Unknown/zero timestamps or sizes, changed files, previous metadata failures, missing tracks and unavailable saved sources trigger normal extraction. The completion notice reports how many metadata reads were reused.

**Full rescan** reads all tags again, even when a provider reports unchanged size/time. Choose it after tag edits that preserve timestamps, or when a vendor's index is stale. Both choices are available from Folders > Rescan; a source's menu also offers Full rescan. The first quick rescan of a legacy library must establish fingerprints, so it may not yet save metadata work. Fingerprints describe provider metadata, not a guarantee that a USB file remains physically reachable; playback still handles storage failures explicitly.

References: [Media3 supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats), [Android supported media formats](https://developer.android.com/media/platform/supported-formats).

## USB, storage, and permission behavior

- **Content-URI playback:** the explorer uses SAF, permission-gated MediaStore or the app-only legacy shared-storage bridge. No raw-path playback, filesystem-wide scan, `MANAGE_EXTERNAL_STORAGE`, root or USB driver is used. Legacy write permission is capped at API 28 and is requested for backup/cache destinations, not ordinary music selection.
- `INTERNET` is used for the requested HTTPS lyric lookup. Audio playback remains content-URI based. Image selection uses document access or the legacy read permission, not `READ_MEDIA_IMAGES`. No network-state permission is required.
- USB must be mounted by the head unit and exposed through a readable shared-storage mount on Android 6-9 or an Android documents provider. USB power, hubs, cable quality, mass-storage support, partitioning, filesystems (including exFAT/NTFS), and vendor picker behavior are device-dependent. ManaMusicPlayer cannot mount a drive, supply a filesystem driver, or force a vendor to expose a volume.
- On Android 11+, Android often disallows selecting a whole storage root, the Download root, and `Android/data` or `Android/obb`. Select a **Music subfolder** instead. If folder selection is unavailable, try **Open file**.
- Choose music files on the device or USB through the built-in explorer or its optional Android picker. A third-party provider's own behavior is outside this application's control.
- SAF read grants are persisted only when the provider offers them. A provider may issue a temporary grant or refuse persistence. That is reported in the UI, and folder access is labeled temporary. Saved references may then require reselection after restart. Device music sources are instead labeled as using the music-library permission; removing their references does not revoke the shared permission used by other saved audio.
- Revocation, removal, or remounting under a different document ID requires reselection. Reconnect the same drive and **Rescan**, or choose the folder again. Renamed files get new provider IDs on some devices.
- A failed scan keeps the prior library unchanged. Even a successful empty scan does **not** erase references: unmatched tracks are marked missing. This prevents a temporarily empty/disconnected provider from silently wiping favorites or playlists.
- Scans are cancellable and committed only after traversal/metadata work completes. Safety limits are 100,000 traversed items and 64 levels; choose smaller music subfolders for larger collections. Scans are tied to the app's retained ViewModel, not a boot worker or foreground scanning service. Finishing the app cancels an incomplete scan without replacing the previous library.
- System media notifications are [exempt from Android 13's notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions). No `POST_NOTIFICATIONS` prompt is needed because the app posts only its media-session notification. FGS/media-playback and wake-lock permissions support background audio. The OS or vendor can still restrict background services or notification display.
- If Android reports fixed media volume, the slider is disabled and hardware volume controls remain the supported path.

## App data

Library and playback JSON, the imported background gallery, and settings are stored privately in the app's internal data directory. New gallery files are decoded and saved before their ordered IDs are synchronously committed; only then are superseded private copies removed. Artwork is an in-memory cache. Automatic lyric lookups use bounded private cache files or the selected portable cache; explicit internal downloads use durable private files outside the automatic cache's collection limit. Both destinations retain their configured expiry policies. Android app backup is disabled. Uninstalling or clearing app data removes ManaMusicPlayer's private data, preferences and references, not external music, pictures or portable lyric records. A portable cache must be selected again to restore access.

## Validation

### 1.29.2 short labels, selected sizing and remote focus

The unchanged 1.29.1 APK reproduced the explorer problem on **Android 6/API 23**: its custom rows were clickable but not keyboard-focusable. **Android 9/API 28** automatically made those rows focusable. Prepared, seekable tracks already allowed rewind/forward through D-pad navigation on both stock emulators; the projector-specific skip was not independently reproduced. The update explicitly enables legacy row focus and checks live enabled state during horizontal transport focus search, without removing the existing seekability guards or preparing/autoplaying media on launch.

The **exact signed 1.29.2 APK** passed English landscape and Chinese portrait D-pad flows on both API 23 and API 28, including disabled-seek skipping, actual paused +/-10-second seeking, folder/file focus, nested navigation and Back without resetting focus to the toolbar. Chinese labels and selected 120% icon/text measurements are asserted in these flows. Both APIs also passed the full optimized touch/settings/playback/collections/backup smoke flow, including all navigation docks and switching decorative borders off.

Focused native regressions cover fixed button bounds, centered words and leading icons, all dock positions, wrapped transport rows and disabled controls, focus outlines independent of selection, nested file activation, touch image selection, large fonts and theme borders. A 2,050-entry directory remained recycled after 61 D-pad steps, with fewer than 40 attached rows after scrolling settled, no extra folder queries, and zero audio reads or library imports. Delayed folder completion did not steal focus after moving to the toolbar or switching to touch. All seven localization tests and release lint passed. Android 6's 2x-font case was run after applying the system font scale at guest boot; real display rotation was used for reliable portrait screenshots instead of resizing the emulated display.

Packaging retains the original signer, v1/v2/v3 signatures, minSdk 23, targetSdk 35, English/Chinese resources, ZIP alignment and uncompressed DEX035. Permissions/components are unchanged; the manifest differs only in version metadata. The APK has no total-size increase from 1.29.1, but executable code does change for these UI fixes. No new startup-speed claim is made. Focus drawing/search is event-driven, with no new polling, scans, decoding, FFT work or audio-pipeline processing.

Evidence is in `app\build\reports\update1292`, including the preserved baseline APK, native results, exact-release traces/screenshots, final lint/localization output and `delivery.json`. Both task-owned emulator guests were stopped and removed; SDK images were retained. These are disposable-emulator results, not verification on the user's physical projector, remote or USB hardware.

### 1.29.1 English/Chinese-only resource packaging

The normal build now retains only default English and Chinese resource configurations. APK inspection reports the default locale and `zh`; `locales_config.xml` still advertises `en` and `zh-CN`. Both localized app names are intact. The 5,092,187-byte APK retains the original signer, valid v1/v2/v3 signatures, ZIP alignment, minSdk 23 and targetSdk 35.

All **six localization tests** passed, including complete English/Chinese key and format-argument parity. The **exact exported APK** passed an English landscape player/settings/backup flow and a Chinese portrait lyric-menu/progress/offline-fixture flow on Android 6/API 23. No live lyric provider or physical device was used.

The executable DEX is byte-identical to 1.29.0 and remains uncompressed. UI resources and application assets match the previously audited language-filtered candidate, except for regenerated ART optimization-profile metadata. Manifest changes are limited to the version name/code; permissions and components are unchanged. No spectrum, FFT, karaoke or artwork cleanup was included. No new startup-speed improvement is claimed.

Evidence is in `app\build\reports\update1291`, including package comparisons, locale/signature/alignment reports, localization results, exact-APK native results and screenshots. The owned test guest was stopped and removed.

### Historical 1.29.0 compatibility and optimization audit

The delivered 1.29.0 APK was left unchanged. Additional exact-APK checks passed on **Android 15/API 35 and Android 16/API 36**, including playback, navigation, collections, backup/restore, external file-open/recreation, actual notification-shade transport/seek controls and notification return without autoplay. API 36 also passed native explorer, karaoke and cross-UID widget checks. The earlier 1.29.0 API 23/API 28 results below remain applicable; those suites were not rerun. Initial modern-SystemUI automation failures were resolved by inspecting all interactive windows, collapsing a leftover shade and explicitly selecting the first queue item before testing Next.

A separately signed **English/Chinese resource-filtered candidate** removed unused dependency translations: **5,686,107 to 5,092,187 bytes**, saving **593,920 bytes / 580 KiB / 10.45%**. Its executable DEX was byte-identical and remained uncompressed; all assets/layouts were also byte-identical. Only `resources.arsc` and the required v1-signature entries changed. No feature, dependency, Android 6 support or signing identity was removed. Six candidate flows passed on API 36, including actual Chinese UI, offline-fixture lyric downloads, widgets, explorer and system controls. At the time of this audit the candidate had not replaced `dist` or changed normal build configuration; the filter was subsequently adopted in 1.29.1 above.

Process-cold, cache-warm startup was measured in **original / candidate / original again** order on one isolated API 36 x86_64 emulator: two CPUs, 1536 MiB RAM, software graphics, 1920x1200 at 240 DPI. Each cell is the median of three runs after a discarded warm-up; the track count also equals the restored queue size.

| Saved tracks/queue | Original controls ready | Candidate controls ready | Repeated original |
|---|---:|---:|---:|
| 0 | 1,519 ms | 931 ms | 868 ms |
| 2,000 | 1,755 ms | 1,178 ms | 1,110 ms |
| 10,000 | 1,963 ms | 1,460 ms | 1,995 ms |

**No consistent general startup speedup is established.** The repeated original exposes substantial runtime/host variation; the candidate's large-queue batch was faster, but this is not a physical-head-unit speed guarantee. APK size savings are directly verified, independently of timing.

Two 15-second samples per effect, using one synthetic local WAV and timed lyrics, measured median whole-app CPU of **2.6%** of one core with effects off, **8.2%** with ordinary lyrics, **13.4%** with karaoke, **10.1%** with 30 Hz spectrum and **14.7%** with 60 Hz spectrum. Observed PSS was about 31-35 MiB during these playback samples. Screenshots confirm that the intended effects were active. Prefer the existing 30 Hz option on slower head units; karaoke is already optional and visible/playing-only. These are short emulator measurements, not GPU, thermal, worst-case music or combined-effect qualification.

The main low-risk packaging opportunity is the measured locale filter. A smaller, **unimplemented and unbenchmarked** code opportunity is caching the 32 FFT band-bin ranges per sample rate. A separate metadata cleanup could avoid exposing an audio-document URI as an image URI: API 35 SystemUI logged recoverable artwork-read permission errors for coverless SAF fixtures, although actual music controls passed. Storage grants should not be broadened to suppress those warnings. Existing lazy startup, bounded artwork caching, off-main-thread browsing, tiny position checkpoints and gated visual/widget updates should be retained.

Evidence and the separate candidate are in `app\build\reports\audit129`; startup/CPU samples are in `app\build\reports\performance\api36\audit129-{original,locales,original-repeat}`. The benchmark script now recognizes modern package/playback-state output and has an explicit `karaoke` feature selector. Both owned guests were stopped and removed. No physical device or USB media was accessed. Both modern guests used 4 KiB pages; the APK has no bundled native libraries, but 16 KiB runtime execution was not tested.

### 1.29.0 internal and removable-storage simulation

The unchanged delivered APK passed the complete explorer scenario in landscape and portrait on an isolated Android 9/API 28 emulator, using 1920x1200 at 240 DPI and its portrait rotation. Both storage permissions were granted. Shared internal storage and a separate 512 MiB virtual removable FAT volume each contained two synthetic, 60-second WAV files, including a Chinese-named file in a nested album folder, plus an unrelated text file.

The native explorer discovered both roots without the system file selector. Browsing did not import music or autoplay. Confirming each folder recursively imported its two songs; both saved sources coexisted, **Play selected folders** created a four-song queue, and **Open file** played only the selected song from either volume. Unmounting the virtual volume while its browser was open and internal music was playing displayed an explicit storage error, retained both sources/all four tracks, and left internal playback running. Remounting the same volume and pressing **Refresh** restored file selection and playback without another grant or duplicate tracks. All four audio files remained byte-identical.

Results, saved library snapshots and screenshots are in `app\build\reports\explorer-simulation`. This tests Android removable-volume/filesystem behavior, not physical USB hardware, OEM mounting, exFAT/NTFS support, or unplugging a drive during playback of a file on that drive. The removable root was readable but not writable by the app despite the storage grants; fixture setup copied music onto it beforehand, as a user would prepare a drive on another device. No production-code or APK change was needed for this simulation.

### 1.29.0 karaoke lyric highlighting and mute alignment

All **94 focused JVM tests** passed, covering lyric intervals, offsets, sampled playback clocks, Unicode prefix boundaries, appearance validation and backward-compatible backup defaults/round-trips. The earlier full 346-test suite was not rerun for this change. Android lint reports **zero errors / 56 advisory warnings**.

Android 6/API 23 and Android 9/API 28 each passed **14 focused native cases**. They verify rendered coverage at 12/24/48sp, wrapped reading order, simultaneous translations, RTL, Android's in-place text-layout reuse, paused/hidden/detached animation gates, unchanged adapter binding counts, settings/color/reset/recreation, span-preserving widgets and their existing ticker, and 48dp mute alignment in both orientations with solid, character and unframed controls.

The **exact delivered optimized APK** passed **six flows**: karaoke in landscape and portrait plus full playback/navigation/collections/backup on both Android versions. These include actual cross-UID widget color spans, paused and advancing coverage, backward seeks, persisted appearance, recreation and functional mute/reset controls. A **1.28.0-to-1.29.0** API 28 upgrade retained the UID and six private files byte-for-byte at installation. Settings, backgrounds, lyrics, library references, favorites, playlists and the repeated queue survived, with a paused 25-second restore followed by working play/pause.

Packaging preserves minSdk 23 / targetSdk 35, unchanged permissions, the original signing identity and one aligned, uncompressed **DEX 035** (3,619,312 bytes). Both owned guests use **1920x1200 at 240 DPI**. Evidence is under `app\build\reports\update129-*`, including native/optimized results, screenshots, package integrity and `update129-upgrade-api28\result.json`. These are bounded-work and functional checks, not an isolated before/after performance benchmark or physical car/projector/USB qualification.

### Historical 1.28.0 native file explorer

All **346 JVM tests** passed. Coverage includes mode-specific filtering, mislabeled LRC/TXT exclusion, backup filename validation, canonical root confinement, traversal/symlink rejection, legacy-storage permission gates and strict indexed-audio URI recognition. Android lint reports **zero errors / 56 advisory warnings**.

The 15-case explorer suite passed every applicable case: **14 passed / one modern-only skip** on API 23 and API 28, and **eight passed / seven legacy-only skips** on API 33. Two additional background/cache picker regressions passed on each version. They cover native folder scanning and playback, local sidecars, image multiselection and recreation, no-overwrite ZIP creation, shared SAF grants and descendant directories, writable-cache checks, protected music files, unavailable/read-only storage, cancellation and retained library data. The real MainActivity round-trip exercises all picker entry points, including native cache migration, ZIP export/import and reconnection of a restored favorite without autoplay. Malformed or oversized external image results leave the previous gallery intact rather than importing a truncated selection. Another **12 pickerless/permission/indexed-audio cases** passed on each of API 23 and API 28, including real permission denial/recovery and removal of the system picker on owned test guests.

Large-font checks use actual **200% system fonts**, including a rebooted API 23 guest, and verify theme/border rendering, reachable actions and usable backup browsing space without an automatically opened keyboard. A **2,050-entry directory** test confirms **zero audio opens**, fewer than **40 attached rows**, no tag/artwork work and no library import merely from browsing. These are behavior and bounded-work checks, not an isolated startup benchmark or a physical-head-unit speed guarantee.

The **exact delivered optimized APK** passed **ten flows**: native explorer selection/recreation in landscape and portrait on APIs 23, 28 and 33, plus cache and full playback/navigation/collections/backup flows on APIs 23 and 28. A **1.27.0-to-1.28.0** API 28 upgrade retained the UID and six private files byte-for-byte at installation. Settings, background, cached lyrics, sources, favorites, playlists and the repeated queue survived; the queue restored paused at 25 seconds and then played and paused normally.

Packaging preserves minSdk 23 / targetSdk 35, the original signing identity and a single aligned, uncompressed **DEX 035** (3,612,500 bytes). The only manifest permission delta is `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`; there is no all-files permission or new network feature. Evidence is under `app\build\reports\update128-*`, including native/optimized reports, screenshots, package integrity and `update128-upgrade-api28\result.json`. All three owned guests use **1920x1200 at 240 DPI**. No physical USB media or car/projector hardware was accessed.

### Historical 1.27.0 owned panels, Chinese initials and spectrum gestures

All **338 JVM tests** passed. Coverage includes Chinese filename/metadata initials, simplified/traditional and polyphonic readings, supplementary characters, overlapping and long queries, preserved literal Latin searches, combined filters/sorting and the explicit clockwise origin order. Android lint reports **zero errors / 50 existing advisory warnings**.

Android 6/API 23 and Android 9/API 28 each passed **22 distinct focused native cases**. Actual gestures verify clockwise origin persistence without moving the spectrum window, changing the display mode or disturbing paused playback. Native library searches find the Chinese filename through `xf`, uppercase/multiword queries and alternative readings. The app-owned choice panel recycles a 500-item list and retains protected checks, reachable wrapping actions, complete borders and readable text across actual day/night and English/Chinese configurations at 1x/2x fonts. Lyric-button glyph bounds remain centered with unchanged touch targets across all five borders.

Related regressions cover Settings selectors, custom accents/RGB input, cache/expiry/About dialogs, lyric timing, current/Best selection and deletion, progressive online choices, bulk download progress/cancellation/recreation, folder/playlist multi-selection and Queue scroll preservation. These checks caught and corrected an inherited filled-button text style on the new outlined footer. Final theme fixtures use actual app language/theme changes and explicitly preserve locale when overriding font scale, rather than assuming an isolated configuration wrapper changes AppCompat's application-wide settings.

The **exact delivered optimized APK** passed **14 flows** across both Android versions: the new owned-panel/initial-search/double-tap flow and spectrum/lyric flow in landscape and portrait, plus cache, large-font portrait downloads and full playback/navigation/collections/backup. These flows also verify that R8 retains and uses the bundled initials table and license. An API 28 **1.26.0-to-1.27.0** update preserved the UID and six private files byte-for-byte at installation; settings, library references, favorites, playlists, cached lyrics and the repeated queue survived, with a paused 25-second restore followed by working play/pause.

A single warm **10,000-track filtering/sorting** sample took **12 ms on API 23** and **61 ms on API 28** on the background dispatcher. These are debug-fixture samples taken during parallel emulator work, not a before/after benchmark or physical-head-unit guarantee. There is no new startup scan or periodic work. The optimized APK retains minSdk 23 / targetSdk 35, unchanged permissions, the original v1/v2/v3 signer and one aligned, uncompressed **DEX 035** (3,567,044 bytes).

Evidence is under `app\build\reports\update127-*`, including native/optimized reports and screenshots, `update127-upgrade-api28\result.json` and `update127-delivered-release.json`. The API 28 guest uses **1920x1200 at 240 DPI**; API 23 uses 1024x600 at 160 DPI. These are disposable-emulator/provider-fixture results, not physical USB/car qualification or live-provider availability checks. The file explorer was still assessment-only in that baseline; it added no browser, storage permission or USB driver.

### Historical 1.26.0 bulk lyric downloads and duration-aware matching

All **126 focused JVM tests** passed, including six localization checks. Coverage includes all five routes' duration units, the exact **180-second song / 40-second lyric** rejection case, inclusive timing boundaries, manual overrides, the three-body automatic candidate limit, sequential/deduplicated downloads, cancellation, partial-provider versus complete-search failures, and downloaded-record/version retention.

Android 6/API 23 and Android 9/API 28 each passed **66 focused native cases**: 63 cache/repository cases and three complete download UI workflows. These cover song/folder/subfolder/queue/playlist scope, immediate progress, recreation, uninterrupted playback, cancellation and resume, offline reuse, retained manual/Best choices and selection generations, old short-cache repair, more than 100 durable downloads, expiry/clear, verified USB migration, unavailable/write-failing portable storage without internal fallback, late-save rejection after clearing, and one shared-folder lyric index per batch.

The **exact delivered optimized APK** passed **eight flows** across both Android versions: downloads in English landscape and Chinese portrait, cache operations, and full playback/navigation/collections/backup. The API 28 portrait run also exercised 2x system fonts. The download fixture uses the real HTTPS transport with deterministic responses, rejects a 40-second preview before accepting a 56-second ending for 60-second music, verifies actual cancellation/disconnection, and checks durable JSON, counters, visible outlined actions and unchanged playback. The API 28 guest uses **1920x1200 at 240 DPI**, matching the reported head-unit API/display geometry; API 23 uses 1024x600 at 160 DPI. These are disposable-emulator/provider-fixture results, not physical head-unit/USB qualification or a claim of current live-provider availability.

An API 28 **1.25.0-to-1.26.0** update preserved the UID and six private files byte-for-byte at installation. Settings, expiry choices, custom colors/background, widget transparency, pinned sources, favorites, playlists, collection positions and the legacy cached lyric survived. The repeated queue restored without autoplay at 25 seconds, then played and paused normally.

Android lint reports **zero errors / 50 advisory warnings**. Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, unchanged permissions, the original signer and one aligned, uncompressed **DEX 035** (3,557,528 bytes). Downloads are explicit, sequential and bounded; no dependency, service, periodic job or startup scan was added. This is not a new runtime-performance benchmark. Evidence is under `app\build\reports\update126-*`, including the unit/native summaries, optimized-flow reports and screenshots, `update126-upgrade-api28\result.json` and `update126-delivered-release.json`. Earlier diagnostic failures remain recorded; final fixtures distinguish intentionally unprepared restoration from a prepared paused player and scope menu controls to the active list.

### Historical 1.25.0 lyric layout, centered controls and shared spectrum colors

All **86 focused JVM tests** passed, covering final lyric timestamps/offsets, strict legacy backup migration, the shared palette, localization and time formatting. Android 6/API 23 and Android 9/API 28 each passed **20 focused native cases**; Android 13/API 33 passed **eight** additional cases, including the modern colored-vertex renderer.

The lyric-dialog case covers **24 combinations**: English/Chinese, 1x/2x fonts, and all five border styles plus unframed controls. It checks aligned leading More/cell/Best controls, source/time placement and contrast, fully reachable scrolling content, actual activation/Best/long-press touches, and complete Close/Cancel outlines. These checks caught and corrected both single-line text scrolling that hid foreground borders and native button-panel padding that clipped their top edges. Main-page icons and labels are independently positioned without changing button bounds; narrow navigation remains stacked. Other coverage includes current/Best persistence, removal, timing edits, progressive metadata-only search, retained Fire colors, ignored legacy Light mode and all 15/30/60 Hz choices.

The **exact delivered optimized APK** passed **eight flows**: spectrum/lyric behavior in landscape and portrait on all three Android versions, plus the main playback/navigation/collections/backup flow on API 23 and API 28. The API 28 guest uses **1920x1200 at 240 DPI**, matching the reported head-unit API/display geometry. The API 23 and API 33 guests use 1024x600 at 160 DPI. These are disposable-emulator/provider-fixture results, not physical head-unit/USB qualification or a claim of live-provider availability.

An API 28 **1.24.0-to-1.25.0** update preserved the UID and six private files byte-for-byte at installation. Its fixture includes legacy Light mode enabled, different Classic/Fire colors, 60 Hz, custom colors/background, expiry choices, widget transparency, pinned sources, favorites, playlists, collection positions and a cached lyric. Saved customization and lyric data remained unchanged after launch; the repeated queue restored paused at 25 seconds, then played and paused normally.

Android lint reports **zero errors / 50 advisory warnings**. Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, unchanged permissions, the original signer and one aligned, uncompressed **DEX 035** (3,537,040 bytes). The static Off icon reuses existing artwork and does not restart spectrum capture or redraw scheduling. No dependency, service, periodic job or eager lyric download was added; this is not a new runtime-performance benchmark. Reports and screenshots are under `app\build\reports\update125-*`, including `update125-native-summary.json`, `update125-upgrade-api28\result.json` and `update125-delivered-release.json`. Earlier diagnostic failures remain recorded; final fixtures use isolated preferences and wait for rendered controls and real-touch callbacks.

### Historical 1.24.0 compatible lyric actions and compact Playlist songs

Android 6/API 23 and Android 9/API 28 each passed **ten distinct focused native cases**. These cover real tap and long-press lyric actions, current/Best state, confirmed cached-copy removal, protected originals, cancellation, rotation and dismissal during song changes or cache operations. Queue scrolling remains stable; Queue and Playlist song rows retain inline typography and 48dp targets with an exact 8dp duration/More gap. Playlist song playback and removal work, and returning to the playlist-name list restores its separate subtitle layout.

The **exact delivered optimized APK** passed **ten flows**: Queue/Playlist behavior and spectrum/lyric actions in landscape and portrait, plus the main playback/navigation/collections/backup flow, on both Android versions. The API 28 guest uses **1920x1200 at 240 DPI** in landscape, matching the reported API and display geometry despite the supplied device's "Android 13" label. The API 23 guest uses 1024x600 at 160 DPI. These are disposable-emulator/provider-fixture checks, not reproduction or qualification of the OEM firmware, physical USB hardware or proprietary gesture handling.

An API 28 **1.23.0-to-1.24.0** update preserved the UID and six private files byte-for-byte at installation. Settings, colors/background, expiry choices, 60 Hz, widget transparency, pinned sources, favorites, playlists, collection positions and the legacy cached lyric survived. The repeated queue restored paused at 25 seconds, then played and paused normally.

Android lint reports **zero errors / 50 advisory warnings**. Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original signer and one aligned, uncompressed **DEX 035** (3,522,372 bytes), with no APK size increase. Reports and screenshots are under `app\build\reports\update124-*`, including `update124-native-summary.json`, `update124-upgrade-api28\result.json` and `update124-delivered-release.json`. No dependency, permission, service, periodic job or spectrum-processing change was added. This is not a new runtime-performance benchmark or a claim of current live-provider availability.

### Historical 1.23.0 compact Queue and stable scroll position

Android 6/API 23 and Android 13/API 33 each passed **five distinct focused native cases**. A 100-occurrence queue verifies the exact first visible row and pixel offset before and after song selection and Next, updated playing highlights, preserved queue order, initial/reopened positioning and working compact More/removal controls. Typography coverage compares title and metadata size, color and style against unchanged ordinary rows across day/night themes, 1x/2x system fonts, English/Chinese text, long or missing metadata, several widths and border styles. Normal-font Queue rows and More targets are 48dp; larger fonts can increase row height instead of clipping text. Existing mini-player/navigation geometry and Library selection/playlist workflows also pass.

The **exact delivered optimized APK** passed **six flows**: Queue selection/removal in landscape and portrait, plus the main playback/navigation/collections/backup flow, on both Android versions. An Android 13 **1.22.0-to-1.23.0** update preserved the UID and six private files byte-for-byte at installation, retained settings/library/cache references, restored the repeated queue paused at 25 seconds, and subsequently played and paused normally.

Android lint reports **zero errors / 50 advisory warnings**. Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original signer and one aligned, uncompressed **DEX 035** (3,521,136 bytes), with no APK size increase. Evidence is under `app\build\reports\update123-*`, including screenshots, `update123-native-summary.json`, `update123-upgrade-api33\result.json` and `update123-delivered-release.json`. Earlier harness failures remain recorded; final cases use main-thread UI queries, allow natural large-font heights, follow the current Settings category count and dismiss the keyboard reopened by Android 6 activity recreation. No dependency, permission, service, periodic job or spectrum-processing change was added. These disposable-emulator/provider-fixture results are not physical USB/car qualification or a new startup benchmark.

### Historical 1.22.0 widget appearance, General settings and progressive online lyrics

All **140 focused JVM tests** passed; Android lint reports **zero errors / 50 advisory warnings**. Provider coverage verifies metadata-only catalog listing, the two-request concurrency bound, progressive results, stable candidate identity, selected-only downloads, failures and cancellation while preserving automatic matching behavior.

Android 6/API 23 and Android 13/API 33 each passed **89 distinct focused native cases**: 53 cache cases, three HTTPS transport cases, nine widget cases and 24 UI cases. Actual cross-UID widgets use configured colors and 24/20, 32/26 and 48/48sp text without resizing controls or adding paused/screen-off polling. Compact rows fit the tested host sizes. UI coverage checks conditional General backgrounds and retained choices, selected-only 150% text/icon content with fixed button dimensions, navigation docks/large fonts, progressive selection, failed downloads, cancellation, recreation and existing offline selection/Best/removal workflows.

The **exact delivered optimized APK** passed **12 flows** across both Android versions: spectrum/lyric controls and cache operations in landscape and portrait, plus full UI/playback/collections/backup and cross-UID widgets in landscape. The online dialog is checked in English landscape and Chinese portrait; deterministic HTTPS fixtures hold one provider pending while other rows appear, verify no cache writes or lyric downloads during listing, then download and activate only the selected version without changing playback or Best. The retained version is highlighted in Available lyrics.

An Android 13 **1.21.0-to-1.22.0** update preserved the UID and six private files byte-for-byte at installation. Settings, expiry choices, 60 Hz, colors/background, widget transparency, pinned folders, favorites, playlists, repeated queue, collection positions and a legacy cached lyric survived. The queue restored paused at 25 seconds and subsequently played and paused normally.

Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original signer and one aligned, uncompressed **DEX 035** (3,520,764 bytes). Evidence is under `app\build\reports\update122-*`, including screenshots, `update122-native-summary.json`, `update122-upgrade-api33\result.json` and `update122-delivered-release.json`. Earlier failed runs are retained; follow-up fixes correct widget row allocation and avoid an indefinitely animated search indicator, while harness corrections distinguish fixed button dimensions from position, respect the hidden About heading and account for Android 6 touch-progress truncation. No dependency, permission, service, periodic job or spectrum-processing change was added; **15/30/60 Hz** remain available. These disposable-emulator/provider-fixture results are not physical USB/car qualification, a new startup benchmark or a claim of current live-provider availability.

### Historical 1.21.0 lyric selection and saved-copy removal

All **138 focused JVM tests** passed; Android lint reports **zero errors / 50 advisory warnings**. The preceding build reproduced the Best-order/numbering regression and loss of song identity during activation across a UI restart. New coverage verifies stable version order, exact-ID removal, Best clearing, retained timing/ages and protected original files.

Android 6/API 23 and Android 13/API 33 each passed **70 focused native cases**: 51 cache/provider cases, eight widget cases and 11 UI cases. These cover active highlighting and compact English/Chinese headings in both orientations and themes, immediate selection and Best updates, long-press actions and confirmation, disabled deletion of originals, current/noncurrent/last-copy removal, offline fallback, empty player/widget lyrics, recreation during removal, stale reads/writes, portable failures and partial-cleanup reconciliation. Earlier failed runs are retained separately; harness corrections target popup roots and wait for completed activity recreation and lyric loading.

The **exact delivered optimized APK** passed **ten flows**: spectrum/lyric controls at **1024x600 landscape and 600x1024 portrait on both Android versions**, plus cache controls, full UI/playback/collection/backup and separate-UID widgets in landscape on each. The picker flow checks actual selected words, a separate Best preference, stable stored order, reopened highlighting, confirmed per-version deletion, offline fallback, unchanged originals and playback, and timing after reactivating an original.

An Android 13 **1.20.0-to-1.21.0** update preserved the UID and six private files byte-for-byte at installation. Settings, expiry choices, 60 Hz, colors/background, widget transparency, pinned folders, favorites, playlists, repeated queue, collection positions and a legacy cached lyric survived. The queue restored paused at 25 seconds and subsequently played and paused normally.

Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original v1/v2/v3 signer and aligned, uncompressed **DEX 035** (3,501,952 bytes). Evidence is under `app\build\reports\update121-*`, including screenshots, `update121-upgrade-api33\result.json` and `update121-delivered-release.json`. No dependency, permission, service, periodic job or spectrum-processing change was added; **15/30/60 Hz** remain available. These are disposable-emulator/provider-fixture results, not physical USB/car qualification, a new startup benchmark or a new claim of live-provider availability.

### Historical 1.20.0 available lyric versions and Best preference

All **132 focused JVM tests** passed; Android lint reports **zero errors / 50 advisory warnings**. Coverage includes version identity/deduplication, local alternative filenames, uniform English/Chinese band labels, independent expiry and protected storage budgets. A temporary selection, automatic default and Best cannot silently evict one another; a record that cannot retain these protected versions fails without replacing the saved data.

Android 6/API 23 and Android 13/API 33 each passed **56 distinct focused native cases**: 40 cache/provider cases, seven widget cases and nine UI cases. These verify local/embedded/retained-download inventory without provider searches, trailing sources, independent Current/Best choices, future Best restoration, temporary selection across Settings and rotation, partial storage failures, stale reads/writes, portable moves, expiry and bounded writes. A reconnecting UI controller no longer clears a same-song temporary selection. Existing spectrum origins, timing controls and cache inspection remain covered.

The **exact delivered optimized APK** passed **ten flows**: spectrum/lyric-version controls at **1024x600 landscape and 600x1024 portrait on both Android versions**, plus cache controls, full UI/playback/collection/backup and separate-UID widgets in landscape on each. The picker flow selects a retained QQ version while a different local version stays Best, preserves the temporary choice through recreation, and restores Best after another song visit. Online lookup is disabled for these fixtures. The harness waits for completed page transitions before comparing spectrum bounds; earlier failed attempts are preserved separately.

An Android 13 **1.19.0-to-1.20.0** update preserved the UID and six private files byte-for-byte at installation. Settings, expiry choices, 60 Hz, custom colors/background, widget transparency, pinned folders, favorites, playlists, repeated queue, collection positions and a legacy cached lyric survived. The queue restored paused at 25 seconds and subsequently played and paused normally.

Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original v1/v2/v3 signer and aligned, uncompressed **DEX 035** (3,491,312 bytes). Evidence is under `app\build\reports\update120-*`, including screenshots, `update120-upgrade-api33\result.json` and `update120-delivered-release.json`. No dependency, permission, service, periodic job or spectrum-processing change was added; **15/30/60 Hz** remain available. These are disposable-emulator/provider-fixture results, not physical USB/car qualification, a new startup benchmark or a new claim of live-provider availability. Previously overwritten lyric versions cannot be recovered; saved versions, Best and timing follow cache clear/expiry/eviction rules.

### Historical 1.19.0 spectrum origins and lyric controls

All **128 focused JVM tests** passed; Android lint reports **zero errors / 49 advisory warnings**. Coverage includes legacy settings/cache decoding, all four band counts and origins, provider order/configuration, current-provider-first alternatives, duplicate history, timing signs and limits, original-source preservation and cache serialization.

Android 6/API 23 and Android 13/API 33 each passed **57 distinct focused native cases**, reconciled across the full selection and targeted reruns. Spectrum bitmap checks cover 128 combinations of band count, origin, bar rendering and sampled frequency, including the highest FFT bin in three-band mode. Native UI cases verify unchanged window bounds, persistent settings, inline source actions, readable timing controls, preview/Cancel/Save, recreation and exhausted alternatives without interrupting playback. Cache/provider fixtures cover selected-match precedence, portable timing copies, stale-load races, cancelled/failed writes, clear-during-search and untimed lyrics. Actual cross-UID widgets receive committed current/next lyric rows, not previews.

The **exact delivered optimized APK** passed **eight flows**: spectrum/lyric controls at **1024x600 landscape and 600x1024 portrait on both Android versions**, plus cache controls and full UI/playback/collection/backup flows in landscape. The timing flow verifies the saved JSON offset, Reset, recreation, migration to a writable document cache, failed portable-save recovery and an unchanged paused 12-second playback position. An Android 13 **1.18.1-to-1.19.0** update preserved the UID and six private files byte-for-byte at installation. Saved folders, favorites, playlists, repeated queue, collection history, expiry choices, custom colors/background, widget transparency and 60 Hz survived; the queue restored paused at 25 seconds and subsequently played normally.

Validation exposed and fixed a fast-save/dialog-dismissal race and low-contrast timing buttons. Test harness corrections distinguish asynchronous one-shot widget refreshes from the actual periodic ticker, use the current activity after task-return rotation, wait for asynchronous library creation, and configure offline lyrics through the live Settings control. Earlier failed attempts remain separate from the reconciled results.

Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original v1/v2/v3 signer and aligned, uncompressed **DEX 035** (3,460,224 bytes). Evidence is under `app\build\reports\update119-*`, including screenshots, `update119-upgrade-api33\result.json` and `update119-delivered-release.json`. No dependency, permission, service, periodic job or extra FFT calculation was added; **15/30/60 Hz** remain available. These are disposable-emulator/provider-fixture results, not physical USB/car qualification, a new startup benchmark or a new claim of live-provider availability. Saved timing and selection history follow the same cache clear/expiry rules as the lyrics.

### Historical 1.18.1 cache controls and reported system information

Both defects were reproduced against the preceding 1.18.0 debug APK before changing production code: the fixed-height expiry selector left **16px for text needing 25px**, and manual cache inspection produced no fresh visible result. The selectors now use the existing wrap-content Settings choice style. Manual inspection uses the existing notice bar; automatic inspection stays quiet on success, and unchanged selector callbacks no longer trigger redundant reads.

All **17 focused JVM tests** passed; Android lint reports **zero errors / 48 advisory warnings**. Android 6/API 23 and Android 13/API 33 each passed **26 focused native cases**. Geometry checks cover English/Chinese, 1.0/1.3/2.0 font scales, all five borders and disabled borders. UI cases cover fresh empty/populated/unchanged cache results, quiet automatic inspection, portable counting and unavailable-storage errors, protected original lyrics, playback and recreation. System information displays the actual runtime fields in both languages with selectable text and a shared-style General button, without adding a toolbar action.

The **exact delivered optimized APK** passed **six flows**: cache controls at **1024x600 landscape and 600x1024 portrait on both versions**, plus full UI/playback/collection/backup and system-information checks in landscape. An Android 13 **1.18.0-to-1.18.1** update preserved the UID and six private files byte-for-byte at installation, including explicit expiry policies. Saved sources, favorites, playlists, repeated queue, collection history, colors/background, widget transparency and 60 Hz settings survived. The queue restored paused at 25 seconds, then supported actual playback and pause.

Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original v1/v2/v3 signer and aligned, uncompressed **DEX 035** (3,434,344 bytes). Evidence is under `app\build\reports\update1181-*`, including before/after reports, bilingual screenshots, `update1181-upgrade-api33\result.json` and `update1181-delivered-release.json`. No dependency, permission, polling job or spectrum-processing change was added. No new folder explorer was introduced. These are disposable-emulator/provider-fixture results, not physical USB/car qualification, a firmware-authenticity verdict or a new performance benchmark. Lyric-provider logic is unchanged; the partial 1.18.0 live results below remain the latest live-provider evidence.

### Historical 1.18.0 widget, expiry, spectrum and lyric refinements

All **125 focused JVM tests** passed; Android lint reports **zero errors / 48 advisory warnings**. Coverage includes the reported copied filename, reference route order and scoring, best-only downloads, provider protocols, expiry boundaries and clock rollback, widget lyric rows, legacy settings and backup round trips.

Android 6/API 23 and Android 13/API 33 each passed **41 distinct focused native cases across the initial and corrected widget batches**. Cache-provider fixtures cover independent internal/portable expiry, legacy modification times, protected original files, retained Never records and stale-revision deletion failures. Other cases verify real two-row widget updates with controls at the top at 110/180/240dp heights, transparency and ticker gating, spectrum Off stopping capture/animation and tapping back on, shared progress/About borders, hidden duplicate About heading, recreation and existing local-first/cache behavior. The new seek test initially used an unprepared player; preparing it and waiting for `STATE_READY` corrected the fixture, and all five widget cases passed on both versions.

The **exact delivered optimized APK** passed **eight flows**: cache controls at **1024x600 landscape and 600x1024 portrait on both versions**, plus full UI/playback/collection/backup and separate-UID widget flows in landscape. The cache UI creates a two-day-old synthetic record, expires it through the one-day selector, retains fresh/original files, and persists independent Never/seven-day choices. Popup test selection excludes the activity's duplicate labels and waits for visible, laid-out popup items; no production popup workaround was needed. Version 1.18.1 separately fixes the selected-row text-height defect.

Live-provider results are **partial, not four-for-four**: on both Android versions, the copied `演员` query retrieved **43 timed lines**, and `晴天` retrieved **33**, each through direct Kugou in two requests. The numbered `如果你也听说` and duet queries did not retrieve lyrics in these runs: direct Kugou returned no candidates; mobile Kugou presented a certificate for a different hostname; QQ returned HTTP 500; NetEase returned no result object; Kuwo returned empty lyric data. Normal certificate validation remains enforced. Protocol fixtures pass for all five routes, but these live results do not establish universal provider availability. The actual MP3 files and their embedded tags were not supplied; the fixtures use explicit public metadata/durations.

An Android 13 **1.17.0-to-1.18.0** update preserved the UID and six private files byte-for-byte at installation. Explicit playback preferences, 60 Hz, custom colors/background, widget transparency, pinned source, favorites, playlist membership, repeated queue, collection history and legacy cached lyrics survived. The queue restored paused at 25 seconds, then supported actual playback and pause.

Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original v1/v2/v3 signer and aligned, uncompressed **DEX 035** (3,433,584 bytes). Reports and screenshots are under `app\build\reports\update118-*`, including `update118-upgrade-api33\result.json` and `update118-delivered-release.json`. No dependency, permission, polling job, widget timer or extra FFT work was added; 15/30/60 Hz remain available. These are disposable-emulator/provider-fixture results, not physical USB/car qualification or a new startup-performance benchmark.

### Historical 1.17.0 clearable and portable lyric cache

All **84 focused JVM tests** passed; Android lint reports **zero errors / 51 advisory warnings**. Coverage includes bounded cache records, strict UTF-8 and filenames, legacy JSON, lyric parsing and matching, provider protocols, widget progress and localized resources.

Android 6/API 23 and Android 13/API 33 each passed **28 focused native cases**. A real, separate-UID writable DocumentsProvider exercises verified moves, partial writes/readback failures, read-only and temporary grants, disconnected storage, corrupt revisions, opaque IDs, reordered cursor columns and direct cache-folder selection. Tests preserve original/unrelated files, legacy atomic backups, shared music grants and previous USB collections; portable records remain beyond 100 entries, 4 MiB and 60 days. In-flight lookups cannot repopulate a cleared cache. Settings confirmations, shared button styling, playback through cache operations, rotation/recreation, local-first lyrics and backup round trips also pass.

The **exact delivered optimized APK** passed cache controls at **1024x600 landscape and 600x1024 portrait on both Android versions**, plus full UI/playback/collection/backup and cross-UID widget flows in landscape. Rotation exposed an Android 6 HTTPS teardown crash: cancellation could close a TLS socket on the main thread. Teardown now uses the existing I/O dispatcher, with deterministic native cancellation, request-configuration and address-allowlist regressions. Cache/UI flows use synthetic data with online lookup disabled rather than depending on live catalogs.

An Android 13 **1.16.0-to-1.17.0** update preserved the UID and six private files byte-for-byte at installation, including a legacy cached lyric. Explicit playback preferences, 60 Hz, colors, background, widget transparency, pinned source, favorites, playlist membership, repeated queue and collection history survived playback after the update. The queue restored paused at 25 seconds.

Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original v1/v2/v3 signer and aligned, uncompressed **DEX 035** (3,426,608 bytes). Reports and screenshots are under `app\build\reports\update117-*`, including `update117-upgrade-api33\result.json` and `update117-delivered-release.json`. No dependency, permission, periodic task, spectrum option or FFT work was added. Cache size scans run only on request. These are disposable-emulator/provider-fixture results, not physical USB/car qualification or a new startup-performance benchmark.

### Historical 1.16.0 compact widgets, lyric lookup and defaults

All **150 focused JVM tests** passed. Android lint reports **zero errors / 40 advisory warnings**. Coverage includes query recovery, harmless title annotations, wrong-singer/recording rejection, all four provider protocols, strict legacy JSON/entity decoding, cancellation, request/time budgets, widget opacity/progress, playback scopes, backup compatibility and localized resources.

Android 6/API 23 and Android 13/API 33 each passed **20 focused native cases**, plus **three opt-in live-provider checks**. Real cross-UID widget hosts check the four compact layouts, including 180x40dp, 180x110dp and 40x40dp legacy bounds, provider target cells, visible controls/progress/lyrics, bounded artwork and true 0/50/100% background transparency without fading controls. Progress stops while paused, screen-off, or when only Play/Pause widgets remain. Settings/default-player navigation, ordered border choices, all Library/Folders/Queue/Playlists action and recycled-row styles, disabling/restoring borders, preference migration and local backup round trips also pass.

Lyric cases verify that cache-only reads are not delayed by a pending online lookup, cannot replace newer results, and do not access the network. Temporary failures expire after 30 seconds without polling. All three fixed public-song lookups succeeded on both Android versions: Android 6 used QQ Music; Android 13 used QQ Music and Kugou. NetEase and Kuwo protocol fixtures pass, but a successful matched live result from those two services is not established here. A Kuwo host probe returned a service-level music-query failure; provider availability remains a runtime limitation, not a reason to bypass HTTPS or authentication.

The **exact delivered optimized APK** passed full UI/playback/collection/backup checks at **1024x600 landscape and 360x640 portrait on both Android versions**. Its widget flow passed at **1024x600 on Android 6**, and **1024x768 / 360x640 on Android 13**. The new Folder default initially exposed an existing assumption that every Library song had a saved folder. Explicit standalone-file selection now remains playable without inventing a folder; an empty selected-folders scope still does not play unrelated music. Both deterministic scope regressions and the exact-release collection flow pass.

Cold-process checks on both versions confirm paused restoration without starting the playback service, explicit widget Play, persistent player-only mute and a 25-second position, and safe empty widgets. An Android 13 **1.15.0-to-1.16.0** update preserved the UID and five private files byte-for-byte at installation. Explicit loop/order/elapsed-time choices, 60 Hz, colors, backgrounds, pinned folders, favorites, playlist membership, repeated queue position, collection history and notification-channel properties survived playback after the update.

Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original v1/v2/v3 signer, and aligned, uncompressed **DEX 035** (3,392,604 bytes). Reports and screenshots are under `app\build\reports\update116-*`, including the cold/upgrade `result.json` files and `update116-delivered-release.json`. Android 6 preference-fixture ownership and slider-hit-coordinate issues were corrected in the harness and passed on rerun. Full smoke runs use clean disposable data rather than inheriting pinned folders from another test. These results do not qualify physical car hardware, USB media, every launcher or all font scales.

### Historical 1.15.0 native home-screen widgets

All **52 focused JVM tests** passed, covering widget restoration/progress/lyrics/tick gating, queue edits, LRC/ID3 lyrics, and English/Chinese resources. Android lint reports **zero errors / 39 advisory warnings**. No dependency or permission was added.

Android 6/API 23 and Android 13/API 33 each passed **three real-widget integration cases** using an actual `AppWidgetHost` in the separate test-APK UID. They exercise all four providers, Play/Pause/Previous/Next, synchronized player-only mute, timed/plain lyrics, live/frozen progress, and 48dp touch targets at the minimum width. Screen-off playback continues without progress updates; updates resume on wake and stop when only Play/Pause widgets remain or the last widget is removed. Artwork fallback and bounded bitmap rendering are covered; these fixtures do not claim additional embedded-image extraction coverage.

The **exact delivered optimized APK** passed the framework-only full UI smoke at **1024 x 600 landscape and 360 x 640 portrait on both Android versions**. Its widget flow passed at **1024 x 600 on Android 6**, and **1024 x 768 landscape and 360 x 800 portrait on Android 13**, including manual/pin setup, actual cross-UID widget actions, main speaker control, and returning to Now Playing without autoplay. Android 6 uses compatible image-alpha actions rather than a `View.setAlpha` RemoteViews action unsupported by that release.

Separate cold-process checks passed on both versions: adding widgets restores paused progress without creating the playback service; an explicit widget Play starts background audio; player mute and a 25-second paused position survive restart; empty widgets do not autoplay and open the player for music selection. An Android 13 **1.14.0-to-1.15.0** in-place update preserved the UID and five private files byte-for-byte at installation, including settings, library, repeated queue entries, backgrounds and collection history. Playback, pinned folders, favorites, playlists and notification-channel settings remained intact.

Packaging retains minSdk 23 / targetSdk 35, R8/resource shrinking, the original v1/v2/v3 signer, and aligned, uncompressed DEX 035. Both exported APKs match the tested candidate exactly. Evidence is under `app\build\reports\widgets-*`, including the two `widgets-cold-api*\result.json` reports, `widgets-upgrade-api33\result.json` and `widgets-delivered-release.json`. These are disposable-emulator results, not qualification of physical car hardware, USB devices, every vendor launcher, or all system font scales.

### Historical 1.14.0 system music discovery and pull-down controls

All **25 focused JVM tests** passed: audio-format filtering, queue edits and overflow-safe within-track seeking, and English/Chinese resource coverage. Android lint reports **zero errors / 25 advisory warnings**, including paragraph-concatenation advisories in the localized About guide. No dependency or permission was added.

Android 6/API 23 and Android 13/API 33 each passed **six focused UI/media integration cases**, plus the independent cold-start widget case in a separate, UI-free test process. Coverage includes modern and legacy music discovery, supported local-audio intents, rejected network/raw-file intents, temporary grants, cold/warm file opening, paused recreation and history launches, background playback and media keys, and the bilingual guide. Tests click the actual system Pause, Play, Previous, Next and 10-second seek controls, check endpoint clamping, and reopen Now Playing without changing the paused queue.

The **exact delivered optimized APK** passed the full framework-only UI smoke at **1024 x 600 landscape and 360 x 640 portrait on both Android versions**. The focused system-controls smoke also passed at **1024 x 600 on Android 6**, and **1024 x 768 landscape and 360 x 640 portrait on Android 13**. Add `-e systemControlsOnly true` when invoking `OptimizedSmokeInstrumentation` for that focused flow. Android 13 is checked through its visible media controls and session state, rather than assuming a fixed raw `Notification.actions` count. Its OS-provided split shade can clip the media card in short landscape configurations; the app does not control that system layout.

An in-place Android 13 update from **1.13.0 to 1.14.0** retained the same application UID and five private files byte-for-byte at installation. Settings, custom colors, 60 Hz spectrum selection, the background image and collection history remained unchanged after playback. Pinned folders, favorites, playlist order, the repeated queue occurrence and a 25-second paused restore were preserved. The existing notification channel kept its ID, importance, sound, vibration, visibility and badge settings; only its display name changed to **Music playback**.

Packaging retains minSdk 23 / targetSdk 35, non-debuggable R8/resource-shrunk code, the original v1/v2/v3 signer, and aligned, uncompressed DEX 035. Both exported APKs match the tested candidate exactly. Reports and screenshots are under `app\build\reports\system-*`, including `system-upgrade-api33\result.json` and `system-delivered-release.json`. Initial harness failures involving accessibility refresh, OS-specific action labels, taskbar-driven activity recreation and notification-array assumptions were corrected without changing the production candidate. Validation used disposable emulators and generated audio, not physical car or USB hardware.

### Historical 1.13.0 folder removal, navigation and Settings

All **68 focused JVM tests** passed, covering batch library edits, visual options and legacy border normalization, localized functional labels, backup validation and Light mode. Lint reports **zero errors / 22 existing advisory warnings**. Android 6/API 23 and Android 13/API 33 each passed **12 focused native integration cases**. These cover pinned/shared/standalone folder references and grants, cancellation and both bulk-removal entry points, preference and backup migration, Settings styling and colors, header navigation, installed About version, compact playback layout and local ZIP round trips. The fixture provider records no document-deletion requests during bulk removal.

The **exact delivered optimized APK** also passed the framework-only smoke in **1024 x 600 landscape and 360 x 640 portrait on both Android versions**. It exercises actual generated-audio playback and seeking, every dock/page, all seven Settings categories, header-logo navigation, border disabling, artist/album browsing, paused collection restoration, ZIP backup/restore and both bulk-removal actions. An in-place Android 13 upgrade from **1.12.1 to 1.13.0** retained the same application UID and five seeded private files byte-for-byte at installation. On launch, legacy `NONE` becomes disabled block controls with `SOLID` saved as the selectable style; radius, character, shadow, colors/custom slots and 60 Hz remain intact. Pinned folders, favorites, playlist order, the repeated queue occurrence, a 25-second paused position, collection history and the background image/reference survive the update and subsequent playback.

**Two-color theme accents are not included.** A software-Canvas assessment compared a solid accent with cached horizontal, vertical and diagonal gradient shaders on 32 rounded controls with text. Shader/path creation was outside measurement; results are median microseconds per draw pass after warmup, using nine alternating samples of 100 passes:

| Emulator | Solid | Left-to-right | Top-to-bottom | Diagonal |
| --- | ---: | ---: | ---: | ---: |
| Android 6 / API 23 | 129.961 | 306.445 | 309.550 | 304.543 |
| Android 13 / API 33 | 381.136 | 756.313 | 954.124 | 946.101 |

This measures added **software drawing work**, not whole-app slowdown, GPU frame rate or physical-head-unit performance; timings should not be compared between the two guests. The single-color accent is retained to avoid that added work on legacy rendering paths. No production gradient feature or dependency was added. Spectrum processing and its 15/30/60 Hz options remain available.

Packaging retains minSdk 23 / targetSdk 35, non-debuggable R8/resource-shrunk code, the original v1/v2/v3 signer and aligned, uncompressed DEX 035. Permissions are unchanged from 1.12.1. Evidence is under `app\build\reports\settings-*`, with successful instrumentation runs named `*-final.txt` and upgrade details in `settings-upgrade-api33\result.json`. Earlier Android 6 test-harness array-compilation and dialog-input failures were isolated and corrected without changing the production candidate. Both owned emulators were stopped and removed. No physical car or USB media was used.

### Historical 1.12.1 app branding and visible version

All **four focused localization JVM tests** passed. Android 6/API 23 and Android 13/API 33 each passed **five focused native integration cases**, covering transparent, bounded logo resources; the branded launch window and saved-theme handoff; title spacing and About version/logo in day/night and portrait/landscape; English/Chinese feature text, visible version and recreation; and staged first-frame startup. Actual taps on the About logo leave the page and paused playback unchanged.

The **exact delivered optimized APK** passed the framework-only smoke in **1024 x 600 landscape and 360 x 640 portrait on both Android versions**. Coverage includes the logo before the title, installed version/build, non-interactive About logo, real synthetic playback, transport/seek controls, every dock and page, compact playback geometry, artist/album browsing, paused collection restoration and local ZIP backup/restore. An Android 13 update from **1.12.0 to 1.12.1** retained the same application UID and all six seeded app-private files byte-for-byte, including library, queue, position, settings and a background image.

Lint reports **zero errors / 22 existing advisory warnings**. The six packaged PNG sizes match their prepared source pixels against black and white backgrounds; the supplied full-size PNG/ICO remain byte-identical outside the APK. The old photo resource is absent. Packaging checks confirm minSdk 23 / targetSdk 35, non-debuggable R8/resource-shrunk code, the original signer with v1/v2/v3 signatures, ZIP alignment and uncompressed DEX 035. No dependency or permission changed, and no startup-speed improvement is claimed.

Reports and screenshots are under `app\build\reports\branding-*`, including `branding-delivered-release.json` and `branding-api33-upgrade.json`. Final optimized runs use the `-final.txt` reports. Earlier title-spacing failures are retained; an Android 13 emulator System UI ANR blocked one attempt and was cleared by rebooting the disposable guest before rerunning the unchanged APK successfully. Both task-owned emulators were removed. No physical car or USB media was used.

### Historical 1.12.0 collection browsing, saved positions and local backups

The complete JVM suite passed **232 tests**, including 46 additions covering grouped browsing, independent collection positions, legacy queue persistence, ZIP bounds/validation, non-destructive restore merging, cross-installation URI isolation, reference reconnection and pinned-folder identity resolution. Lint reported **zero errors / 22 advisory warnings**. Existing Light mode behavior and both languages' resource/format coverage remained intact.

Android 6/API 23 and Android 13/API 33 each passed six focused integration cases: artist/album browsing and selection across rotation/recreation; independent positions for two playlists and a nested folder, always restored paused; backup cancellation and malformed-input isolation; settings/colors/background-image restore with simulated new-installation references and folder reconnection; the existing deferred-startup check; and nested-folder playlist/path behavior. The restored folder's pin remains active when its source-relative path resolves to a new provider document ID.

The **exact optimized APK** also passed the framework-only playback/navigation/settings checks and the new grouping, paused playlist-position and local ZIP export/restore checks in **landscape and portrait on both Android versions**. The initial Android 6 integration run exposed a pinned-folder refresh guard, fixed before the final runs. Native test-dialog gestures also needed stable `performClick()` activation rather than coordinates racing window/keyboard movement; those harness corrections did not change the tested release APK. Earlier failures and the final successful reports are retained separately.

An Android 13 in-place upgrade from the exact **1.11.4** APK preserved the application UID and byte-identical library, queue and settings files at installation. Subsequent reads retained pins, favorites, playlist order, None/radius/shadow preferences, 60 Hz and arbitrary custom colors. A three-item queue with a repeated song resumed paused at its third occurrence and 25 seconds, then played actual synthetic audio and saved a matching compact checkpoint.

Startup was compared on one disposable Android 13 emulator, with two virtual CPUs, 2 GiB RAM, 1024 x 600 at density 160 and software graphics. The sequence was **1.11.4 / 1.12.0 / 1.11.4**; no builds or other emulators ran during these batches. Each case used one excluded warm-up and three force-stopped launches, identical synthetic library/queue files and optional effects off. Values are median **first display / usable controls**, in milliseconds:

| Saved library and queue | 1.11.4 first | 1.12.0 | 1.11.4 repeat |
| --- | --- | --- | --- |
| Empty | 1,245 / 1,391 | 1,226 / 1,550 | 1,203 / 1,394 |
| 10,000 references | 1,362 / 2,517 | 1,345 / 2,309 | 1,348 / 2,697 |

These are mixed results, not a claimed startup speedup: empty-library control readiness was slower in the new-build batch, while the larger-library median was lower. Host/runtime variation remains; this does not measure startup on a physical car or qualify physical USB hardware.

Evidence is under `app\build\reports\collections-*` and `performance\api33\collections-*`: final integration reports, all four optimized orientation reports, screenshots, `collections-upgrade-api33\result.json`, package/signature/DEX checks and `collections-delivered-release.json`. That release was **1.12.0 / code 24**, non-debuggable, R8/resource-shrunk, minSdk 23 / targetSdk 35, with the original signer, valid v1/v2/v3 signatures, ZIP alignment and uncompressed DEX 035. No runtime permission or dependency was added.

### Historical 1.11.4 feature guide and optimized sideload release

About and help now contains concise English and Simplified Chinese introductions to the current functions, without request history, development details or the device/API diagnostic block. The existing photo viewer and lazy loading are unchanged. No playback, startup, storage, spectrum, dependency or permission changes were made.

Android 6/API 23 and Android 13/API 33 each passed **three focused integration cases**: localized feature sections and excluded diagnostic/request text, scrolling in both orientations and recreation, the original-resolution photo viewer without playback changes, and Settings category/scroll restoration. The **exact optimized APK** passed playback, seeking, dock transitions, compact navigation and the feature-only About check in **landscape and portrait on both Android versions**. The previous 1.11.3 APK failed the new feature-only About check as expected. Lint reports **zero errors / 22 advisory warnings**.

The final release is **1.11.4 / code 23**, **5,565,793 bytes**, non-debuggable, R8/resource-shrunk, minSdk 23 and targetSdk 35, with the original signing certificate, valid v1/v2/v3 signatures and ZIP alignment. Reports and screenshots are under `app\build\reports\help-release-*`. Initial emulator-startup failures and an Android 6 test-callback failure are retained separately from the successful final reports; the callback now reuses its unchanged term list outside the UI callback. Validation used disposable emulators and generated audio, not physical car or USB hardware.

### Historical 1.11.3 stacked side-dock labels

The previous 1.11.2 optimized APK reproduced the reported side-dock layout: labels remained beside icons in landscape. Left/right docks now use centered labels below icons and retain the compact 64/80dp sidebar width. Top/bottom landscape rows remain 48dp high at the default font size; portrait keeps stacked labels. No dependencies, background work, playback processing, or settings migration were added.

Android 6/API 23 and Android 13/API 33 each passed **five focused integration cases** covering all four dock positions, both orientations, three border styles, recreation on every edge, narrow/wide measurements, double-size fonts, transitions between horizontal and side docks, unchanged playback and compact mini-player geometry. The **exact optimized 1.11.3 APK** also passed playback and all four dock transitions in **landscape and portrait on both Android versions**. Lint reports **zero errors / 25 existing advisory warnings**. The first-boot Android 6 guest became unresponsive; it was restarted and its test APKs reinstalled before the successful final runs.

Reports and screenshots are under `app\build\reports\side-label-*`; `side-label-before-api33.txt` records the expected old-layout failure. The release retains minSdk 23, targetSdk 35, the original certificate, valid v1/v2/v3 signatures and ZIP alignment. The APK is **5,573,985 bytes**, below the 6 MiB budget. These are emulator checks, not physical car or USB validation.

### Historical 1.11.2 borderless blocks and compact landscape navigation

The previous optimized APK reproduced the landscape-label mismatch: its navigation buttons stacked text below icons. Landscape now uses a 48dp-high top/bottom dock at the default font size, with text beside icons; portrait retains stacked labels. Tests cover all four dock positions, normal/character/None borders, rotation, recreation, repeated narrow/wide measurements and double-size fonts. Navigation labels remain present on short windows; scrolling preserves access instead of reducing touch targets below 48dp.

All **21 focused visual-options JVM tests** passed, and lint reports **zero errors / 25 existing advisory warnings**. Android 6/API 23 and Android 13/API 33 each passed **nine focused integration cases**, covering the six border choices, transparent interiors, zero painted pixels for None even with a saved shadow, hidden irrelevant controls, persisted None selection, restoration of saved radius/character/shadow, compact mini-player geometry, stable content bounds, and unchanged playback while navigating.

The **exact optimized 1.11.2 APK** passed separate **landscape and portrait playback/navigation checks on both Android versions**, including choosing and saving None. Package checks confirm version code 21, minSdk 23, targetSdk 35, the original signing certificate, valid v1/v2/v3 signatures and ZIP alignment. Its size remains **5,569,889 bytes**. No dependency, runtime permission, scan, background worker, or spectrum-processing change was added.

Reports and screenshots are under `app\build\reports\border-toolbar-*`; `border-toolbar-before-api33.txt` records the expected old-layout failure. These checks use disposable emulators and generated audio, not a physical car or USB drive.

### Historical 1.11.1 compact browsing playback bar

The previous 1.11.0 optimized APK reproduced the mismatch at 1024 x 600, density 160: Settings used a 48dp playback row, while Library expanded it to 120dp with a second progress row. Library/Favorites, Folders, Queue, and Playlists now reuse the existing Settings row. Now Playing and the navigation dock are unchanged. The compact layout is initialized when first needed and reused across page changes, without adding startup I/O, background work, or dependencies.

Android 6/API 23 and Android 13/API 33 each passed **five focused integration cases**. They compare actual control and content bounds against Settings across all four dock positions, portrait/landscape, and activity recreation; check narrow widths and double-size fonts; exercise seeking and elapsed/remaining time; reject a stale drag after a track change; and retain paused/hidden polling behavior. Both versions also passed the **exact optimized APK** playback/navigation smoke, including matching mini-player geometry on every browsing page. The Android 13 release installed directly over 1.11.0.

All **185 JVM tests** passed; lint reports **zero errors / 25 existing advisory warnings**. The exported APK remains **5,569,889 bytes**, with minSdk 23, the original signing identity, valid v1/v2/v3 signatures, and verified ZIP alignment. Reports and screenshots are under `app\build\reports\compact-mini-*`. These are emulator results, not physical-car or USB qualification.

### Historical 1.11.0 palette and indexed-storage update

All **185 JVM tests** passed; Android lint reports **zero errors / 25 existing advisory warnings**. Added model cases cover the seven fixed colors, five initial slots, custom edits/appends/duplicates, serialization, indexed-tag normalization and atomic overlapping-folder merges.

Android 13/API 33 passed **eight focused palette/Details cases** and Android 6/API 23 passed **six**. They cover explicit Apply, both cancellation levels, invalid HEX, RGB synchronization, shared saved slots and additions, custom accents in both themes, recreation, independent spectrum colors and readable file details. Closing Details while a slow metadata read is pending cancels without a false error notice. Both versions also passed **nine pickerless-storage cases plus two indexed enhancements**, including Select all / Deselect all, rotation, permission denial/revocation, shortcut-only browsing, real single-file playback, batch-write counting, overlapping sources, cancelled imports, on-demand full tags and preserved favorites/playlists.

The **exact optimized 1.11.0 APK** (`076D71062F2D010317A3342BE24332892B22994DE17CC302CF18E3F49B7FADFD`) installed on both Android versions and passed all **nine color-dialog** checks, including repeated narrow/wide resizing, RGB/HEX synchronization and saved custom slots. Separate playback/navigation checks cover paused launch, real synthetic audio, seeking, rewind/forward, five pages, seven Settings categories and leaving Settings through the dock. Packaging retains minSdk 23, targetSdk 35, uncompressed/aligned DEX 035, the existing signer and valid v1/v2/v3 signatures, with no added runtime permission or dependency.

In-place upgrades from the preserved **1.10.0** APK passed on **both Android 6 and 13**. The application UID and private library/playback/settings files remained byte-identical at installation. Pinned folders, favorites, playlist order, four arbitrary existing colors and a saved **60 Hz** choice survived. The repeated queue `[A, B, A]` restored paused at 25 seconds, then played and saved a generation-matched checkpoint at occurrence 2. Temporary test-provider grants were explicitly renewed; no stronger grant persistence is claimed.

Reports are under `app\build\reports\palette-core-api33.txt`, `palette-storage-api23\`, `palette-optimized-api{23,33}-*.txt`, `indexed-enhancements-api33.txt`, the `picker-*.txt` reports, and `palette-upgrade-api{23,33}\result.json`. The scan timing and its limitations are recorded above. These emulator checks do not qualify physical USB hardware or a particular car's firmware.

### Historical 1.10.0 lightweight update

All **178 JVM tests** passed, with **zero Android lint errors / 25 existing advisory warnings**. New cases cover legacy checkpoint migration, position-only writes, equivalent/reordered/repeated queues, interrupted writes, mismatched generations, corrupt-state preservation, metadata reuse and non-destructive Light mode.

Android 6/API 23 passed **18 focused core cases**; Android 13/API 33 passed the corresponding **18 distinct cases across focused batches**. Coverage includes paused restoration, deferred first draw, full/quick rescans with metadata-read counters, 15/30/60 Hz analysis without changing PCM bytes, idle/hidden progress behavior, settings/mini-player controls, source loops and independent media controllers. Both separately passed **nine pickerless-storage cases**, including permission denial/revocation, real indexed playback, temporary grants, Chinese labels and retained references. Cache-only lyric checks use a synthetic transport: cached lyrics remain available in Light mode and on Retry, uncached requests never contact the transport, and newly available local lyrics still win.

The lossless photo's full-resolution decoded pixel hash matched the original on **both Android 6 and 13**: `39E69AA8D922F14BF7FA29662559166EBE767E377B75C6AFAAB79828CACF639F`. The original-resolution zoom viewer also passed.

The **exact non-debuggable R8 APK**, not only a debug build, installed and passed all nine color-dialog regressions and playback/navigation smoke checks on both versions. Those checks include paused launch, real synthetic playback, seeking, rewind/forward, five main pages, all seven Settings categories, About and leaving Settings through the dock. MinSdk 23, targetSdk 35, DEX 035, uncompressed/aligned DEX, the original signer and the 6 MiB size budget remain intact. Optional lyric networking is retained; no storage-write, all-files, microphone or additional runtime permission was introduced.

Exact-APK upgrades from **1.3.1 and 1.9.3 on Android 13**, plus **1.9.3 on Android 6**, preserved the UID and byte-identical library/playback/settings files at installation. Folders, favorites, playlist order and an explicitly saved **60 Hz** choice survived. Independent controllers restored paused at 18 seconds, played and sought within the queue, then persisted and cold-restored the **second occurrence** in `[A, B, A]` at 25 seconds. Seeking to zero and pressing Previous selected B, independently confirming the restored occurrence index. The generation-matched position file was 121 bytes. Temporary fixture grants were explicitly renewed; they did not become persistable grants.

Reports are retained as `app\build\reports\lite-api23-*`, `lite-api33-*`, and `lite-upgrade-*-final\result.json`. Performance evidence and its limitations are recorded above. No physical-car or USB-hardware qualification is implied.

### Historical 1.9.3 restored Android 6 compatibility

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

Compatibility fixtures write and later delete only their own generated audio and test directories on disposable emulators. The production app contains no fixture code and never modifies music; its optional legacy write permission is capped at API 28 for cache/backup destinations. JVM tests cover the Android 6 minimum, permission selection across API 23-36, manifest caps, modern/legacy folder grouping, directory boundaries and literal SQL-prefix escaping. Chinese UI checks also verify that indexed-folder labels follow the selected language without rewriting the saved library.

The earlier API 35 results and 1.7.1/1.7.3/1.8.0 timings remain historical results, not runtime testing of every Android version for 1.9.0.

Lyric regressions cover legacy-library parent discovery, opaque SAF folder IDs, local-before-online precedence, synthetic online responses and cache restoration, unreadable sidecars, bounded failed lookups, seeking, track changes, and the display switch. Query regressions include the numbered duet and copied `演员` filename, metadata fallback without sending paths, reference route/keyword order, weighted ranking and stable ties, best-candidate-only downloads, short-content rejection, translated fallbacks, malformed responses and cancellation. Background tests import through a temporary grant, exercise blur and tint controls during playback, recreate the activity, reopen saved settings, remove provider access, and reject invalid replacements without losing the previous picture. They also assert transparent panels across every navigation screen, full-window image bounds after a light-tint change, and restored ordinary-theme surfaces. JVM tests cover LRC timing/encoding/size limits, ID3 lyrics, provider duration units, Base64 decoding, cache-expiry boundaries, background effects and backward-compatible persistence.

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

Widget tests additionally use a **test-APK-only** host. After installing both debug APKs on a disposable emulator, grant that test package widget binding and run the focused suite:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 shell appwidget grantbind --package com.macarmusic.app.test --user 0
try {
    & "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 shell am instrument -w -r `
        -e class com.macarmusic.app.HomeWidgetIntegrationTest `
        com.macarmusic.app.test/androidx.test.runner.AndroidJUnitRunner
} finally {
    & "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 shell appwidget revokebind --package com.macarmusic.app.test --user 0
}
```

Require `OK (5 tests)`. The binding grant is also needed when including these cases in `connectedDebugAndroidTest`. To check widgets against the optimized APK instead, install the framework-only harness, keep the same grant/revoke steps, and pass `-e homeWidgetsOnly true` to `OptimizedSmokeInstrumentation`. The fixture replaces the test library and exercises screen sleep/wake; use a disposable guest without personal music references. On Android 6, keep the test display override within its physical capture bounds for screenshots.

To reproduce pickerless firmware on a **fresh, disposable API 23+ emulator**, including system-package restoration and owned-fixture cleanup:

```powershell
.\scripts\Test-StorageCompatibility.ps1 -Serial emulator-5554 -DisposableDevice
```

This helper replaces the test app's library, temporarily disables the emulator's standard DocumentsUI, grants/revokes its audio permission, and refuses physical devices. On API 23-28 it also checks opt-in native cache-folder access with actual storage-permission denial and recovery. Indexed-file browsing verifies that browsing does not import songs or folders, and indexed batch checks cover tag reuse, one commit and cancellation. It restores initially enabled picker packages in `finally`. Optional `-BaselineApk C:\path\to\preceding-manaplayer.apk` also reproduces the old banner before installing the current candidate. Do not use an emulator containing music references you want to retain. Compatibility tests are opt-in when using the generic Gradle instrumentation task.

The live-provider checks are separate from the hermetic tests and are skipped unless explicitly enabled. After installing both APKs on the disposable emulator, run:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 shell am instrument -w -r `
    -e liveLyrics true -e class com.macarmusic.app.LiveLyricsSmokeTest `
    com.macarmusic.app.test/androidx.test.runner.AndroidJUnitRunner
```

These use four fixed public-song queries, including the copied `演员` filename, not the device library. They never log lyric text or provider access keys. The historical **1.6.0** run retrieved **52 timed lines** for the numbered `如果你也听说` example, **63** for `晴天`, and **77** for `当爱已成往事`, all from Kugou. The current copied-filename fixture uses 261 seconds and the duet fixture 279 seconds; the actual MP3 files and their embedded metadata were not accessed.

Historical results do not imply universal catalog/region availability. Version **1.16.0** repeated three queries on Android 6 and 13 with its then-conservative multi-provider implementation; all succeeded through QQ Music or Kugou. Version **1.18.0** replaces that selection procedure with the reference local-player routes and weighted ranking described above. Protocol fixtures cover every route; live success must be recorded separately and does not establish availability for unexercised providers. Local lyrics, cached results, artwork and audio playback remain available independently of public services.

Device tests modify ManaMusicPlayer's **test-device** library. Do not run them against a personal library you want to retain. They never access a physically attached USB drive; the provider fixtures are synthetic and isolated.

API 23 / Android 6.0 is the restored compatibility floor. Version-specific runtime results are recorded above; not every Android release or vendor firmware has been tested separately. The car's generic package-parsing message did not supply a framework API level or native installer error, so its exact cause remains unconfirmed. The system-information screen alone does not establish that framework API. No physical car, USB disk, Bluetooth accessory, speaker output, specific head-unit control plugin, OEM navigation/audio-focus behavior, CAN-bus, or every-codec qualification was performed. Installation, USB/provider availability and codec support must still be checked on the intended head unit.

## Persistence and recovery

`library.json`, `playback.json` and `playback-position.json` use atomic replacement. The queue file changes only when its contents/order/metadata change or a legacy snapshot first needs migration. The small position file records the index, position, shuffle/repeat and a matching queue-generation identifier. Queue commits happen first; a stale checkpoint can never apply another queue's index. Repeated occurrences of a song retain their selected queue index.

Legacy one-file snapshots load without an eager migration write. A missing or mismatched position checkpoint falls back to the queue's embedded recovery position. An unreadable checkpoint reports a problem, preserves both files and prevents automatic overwrite until a deliberate replacement selection. Unknown/corrupt queue or library schemas are reported instead of silently overwritten. If the library cannot be decoded, it stays read-only to preserve evidence; restart after resolving a transient internal-storage problem.

In a debug build, an experienced developer can use `adb -s SERIAL shell run-as com.macarmusic.app` to inspect/back up these files. Preserve the queue and its matching position file together, including any atomic backup, before manual recovery. Do not clear application data as the first troubleshooting step.

Queue positions are checkpointed on player events and approximately every five seconds during the service lifetime. Sudden power removal or forced process termination can lose the most recent checkpoint interval; no mobile application can guarantee a disk write after power is cut.

## Code layout

- `model`: serializable records and pure filtering, sorting, library/playlist, and queue operations.
- `data`: SAF and permission-gated MediaStore queries, background scanning/metadata/artwork, atomic JSON storage, and repositories.
- `lyrics`: bounded LRC/ID3 parsing, local-first lookup, HTTPS provider adapters, matching, and caching.
- `playback`: content-only media data source, local cover loader, MediaItem mapping, service-owned player/session, and bounded PCM/FFT spectrum analysis.
- `ui`: lifecycle-aware MediaController activity, retained ViewModel, background slideshow/transitions, spectrum rendering, native lists/dialogs, and responsive layouts.
- `src\test`: focused JVM behavior/persistence tests.
- `src\androidTest`: synthetic documents provider and instrumented integration tests.

Source repository: [afantima2024/manaplayer](https://github.com/afantima2024/manaplayer). The `manaplayer1019` tag preserves version 1.20.0. Its local archive/checksum are `archives\manaplayer1019.zip` and `archives\manaplayer1019.sha256`; the ZIP contains the source, wrapper/build scripts, tests, documentation and exact signed APK/checksum under one `manaplayer1019` folder.

Earlier baselines remain unchanged: `manaplayer1010` preserves version 1.2.0, and `manaplayer1013` / commit `be396a5f487222b5910fa5bc6820aec949f2296a` preserve version 1.9.3. Their dated local archives/checksums are retained in `archives`. Generated APKs, local archives, build caches, machine-specific SDK settings and signing keys are not tracked.
