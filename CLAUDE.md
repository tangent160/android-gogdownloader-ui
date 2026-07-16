# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An Android front-end (Kotlin + Jetpack Compose, minSdk 26, targetSdk 36) that wraps the upstream
[RikudouSage/GogDownloader](https://github.com/RikudouSage/GogDownloader) PHP CLI. The app does not
reimplement any GOG logic — it bundles a self-executing static PHP build of the CLI and drives it
as a native child process, reading the CLI's own SQLite database for the UI.

## Build commands

```sh
git submodule update --init        # once after cloning (vendor/GogDownloader)
./php-build/build.sh               # builds the bundled CLI binaries into app/src/main/jniLibs/
./gradlew :app:assembleDebug       # APK at app/build/outputs/apk/debug/app-debug.apk
```

`build.sh` must run before the first Gradle build (jniLibs is gitignored). It caches downloads in
`php-build/cache/`; re-runs are fast. There are no tests; verification is manual on a device
(`adb install`). The x86_64 output `php-build/out/gog-downloader-x86_64` runs directly on a Linux
host — the fastest way to test CLI behavior, e.g.
`CONFIG_DIRECTORY=$PWD ./gog-downloader-x86_64 code-login FAKE --no-interaction`.

## The binary pipeline (php-build/)

`build.sh`: downloads static-php-cli's prebuilt PHP 8.4 (host CLI + per-ABI `micro.sfx`), runs
composer + phar-composer on `vendor/GogDownloader` (a git submodule pinned to an upstream tag),
strips the phar's shebang, re-signs it (`fix-phar.php` — the phar SHA256 signature spans the stub;
PHP's internal SHA256 signature constant is 0x0003), then concatenates `micro.sfx + phar` into
`app/src/main/jniLibs/<abi>/libgogdownloader.so`.

Packaging the binary as a fake native library is load-bearing: binaries inside the APK may be
exec'd on modern Android, user-supplied ones may not (API 29+). Requires
`useLegacyPackaging = true` (app/build.gradle.kts) so the file exists on disk at
`applicationInfo.nativeLibraryDir`.

## Android-side architecture

Three environment shims in `core/` make the static musl binary work on Android — all wired up in
`GogCli.run()` (ProcessBuilder wrapper, the only place the binary is invoked):

- **DNS**: musl reads /etc/resolv.conf, which Android lacks. `ProxyServer.kt` is an in-app loopback
  HTTP CONNECT proxy; the CLI gets `https_proxy` pointed at it so the JVM does name resolution.
- **TLS**: OpenSSL can't read Android's trust store. `CaStore.kt` exports AndroidCAStore to a PEM
  file passed via `SSL_CERT_FILE` (the build honors SSL_CERT_FILE/SSL_CERT_DIR, NOT CURL_CA_BUNDLE).
- **Output**: `GogCli` splits process output on `\r` as well as `\n` because Symfony progress bars
  redraw with bare carriage returns; `SyncService.parseProgress` turns ` 12/300 [=>-]  4% - Title`
  lines into determinate progress. `GogCli.Result.errorMessage` extracts the real error from
  Symfony's output (the last line is always a useless usage synopsis).

Data flow: UI never parses CLI tables. `GameDatabase.kt` reads gog-downloader's own SQLite file
(`<filesDir>/gog/gog-downloader.db`; tables `games`, `downloads`, `game_extras`, `auth` — schema in
`vendor/GogDownloader/src/Migration/`). The bundled PHP has PDO+SQLite3, so the CLI always uses its
SQLite backend — auth and library live in that one file (the separate `auth.db`/`games.db` layout is
the CLI's fallback file backend, never active here). Cover art comes from the public GOG products
API (`CoverRepository.kt`); it's not stored locally.

Library search/sort: both are in-memory transforms in `LibraryViewModel` (a `combine` of the full
game list, query, and `LibrarySort`) — never SQL, since `games()` loads the whole library anyway.
`Game.totalSizeBytes` (summed from `downloads` in the `games()` join; counts all variants, sort key
only) backs the size sort; the chosen sort persists via `Settings.librarySort` as the enum name.

Backup: `core/DatabaseBackup.kt` exports/imports that single db file via SAF, used from Settings
(export + import) and the login screen (import instead of logging in). Import refuses while a
sync/download runs (the CLI child process writes the db), validates the file (SQLite magic + `games`
and `auth` tables), and atomically renames over the live file — safe because `GameDatabase` opens
the db read-only per query and never holds a handle.

Long-running CLI work must run in a foreground service with a partial wake lock, or Android
freezes the child process on screen-off: `sync/SyncService` (update-database) and
`download/DownloadService` (download). Each publishes state through a process-wide singleton
(`SyncMonitor`, `DownloadQueue`) that ViewModels observe; screens start the service and re-attach
to that state. `MainActivity` holds the nav graph; singletons live on `GogApp`.

UI insets: the app is edge-to-edge (`enableEdgeToEdge()` in `MainActivity.onCreate`). Screens with
a `Scaffold` (Library, GameDetail, Settings, Queue) get status/nav-bar insets for free — they must
apply the Scaffold content-`padding` parameter to their content, and nothing else. Scaffold-less
screens (Login, Preflight, SyncChoice, Sync) must put
`Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` on their root Column or they draw under
the status bar / camera cutout and navigation bar. New screens follow one of these two patterns.

## CLI semantics that matter

- Game filter for `download` is `--only=<exact title>` (case-insensitive, no regex); unselected
  files are excluded per-file with `--skip-download=<name>`.
- Installer `name` is NOT unique — GOG reuses it across platform/language variants, and
  `--skip-download` exact-matches on name, so it skips all variants of a name or none. The UI
  therefore groups same-named variants into one selectable row (`DownloadGroup` in
  `GameDetailViewModel`) and narrows variants with `--os`/`--language` filter chips instead.
- `--os` takes `windows`/`mac`/`linux` and `--language` takes enum *codes* (`en`, not `English`);
  an invalid value aborts the whole run. `core/GogCliFilters.kt` maps DB values (which may be
  local names) to valid args and drops a filter dimension entirely if any value is unmappable.
- `--os`/`--language` filter each game's installer list, then games with zero remaining
  installers are dropped *including their extras* — never pass these flags on an extras-only
  (`--no-games`) job.
- `update-database` skips games hidden on GOG unless `--include-hidden` is passed. The
  `Settings.includeHidden` toggle appends it to every sync mode (`GogCli.updateDatabase`); it only
  affects what future syncs fetch — turning it off never removes already-synced hidden games.
- `update-database --updated-only` also fetches every owned game *missing* from the local DB. That
  is why `Settings.librarySyncMode` exists: after a `--search` sync, automatic/incremental updates
  are disabled (they would silently fetch the whole library). Nothing may trigger a full update
  except the user's explicit choice (sync-choice screen or Settings).
- GOG login codes are single-use and expire within minutes; login failures are usually stale codes.
- App-start never syncs; all updates go through the shared sync screen (`sync?mode=...` route).

## Updating upstream

`vendor/GogDownloader` is a submodule pinned to a release tag:
`git -C vendor/GogDownloader fetch --tags && git -C vendor/GogDownloader checkout <tag>`, commit the
new revision, re-run `build.sh`. The version stamp is derived from the submodule's git tag.
