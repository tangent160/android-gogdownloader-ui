# android-gogdownloader-ui

An Android front-end for [RikudouSage/GogDownloader](https://github.com/RikudouSage/GogDownloader).
The app bundles a self-executing static PHP build of gog-downloader and drives
it as a native process — browsing your library visually, and delegating login,
database updates, and downloads (with hash verification and resume) to the CLI.

- **minSdk**: 26 (Android 8.0) · **targetSdk**: 36 (Android 16)
- **ABIs**: arm64-v8a (devices), x86_64 (emulator)

## How it works

- `vendor/GogDownloader/` is the upstream source (MIT), included as a
  git submodule pinned to v1.15.1. Update with
  `git -C vendor/GogDownloader fetch --tags && git -C vendor/GogDownloader checkout <tag>`,
  then commit the new submodule revision.
- `php-build/build.sh` downloads the official prebuilt static PHP 8.4 runtime
  from [static-php-cli](https://github.com/crazywhalecc/static-php-cli) (MIT),
  builds the gog-downloader phar with composer + phar-composer, strips the
  phar's shebang, re-signs it, and concatenates it with `micro.sfx` into
  `app/src/main/jniLibs/<abi>/libgogdownloader.so`.
- Packaging the binary as a "native library" is what makes it executable on
  modern Android: binaries shipped inside the APK may be exec'd, while
  user-supplied binaries in app storage may not (API 29+).
- The app reads gog-downloader's own SQLite database directly for the game
  list and download options, and fetches cover art from the public GOG
  products API.
- The UI is edge-to-edge with proper inset handling, so content stays clear
  of the status bar / camera cutout and the navigation bar in both portrait
  and landscape.

## Building

```sh
git submodule update --init   # after cloning
./php-build/build.sh      # build the bundled gog-downloader binaries (once)
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Usage flow

1. **Login** — the app opens GOG's code-login page in your browser; after
   logging in you land on a blank page. Copy its address, paste it into the
   app, and it runs `code-login` for you. Alternatively, import a database
   backup exported from another install to skip logging in.
2. **Sync** — the app runs `update-database` to fetch your library. A
   Settings toggle ("Include hidden games") adds `--include-hidden` to every
   update, so games hidden in your GOG library are fetched too.
3. **Library** — a grid of game covers, searchable from the app bar and
   sortable by title, recently added, or total size (the sort choice is
   remembered). Tap a game to see its installers and
   extras, select what you want, and hit download. Installers are grouped by
   name (GOG reuses one name across platform/language variants); filter chips
   narrow a multi-platform or multi-language game to just the variants you want.
4. Downloads run in a foreground service via the CLI's `download` command
   (`--only=<game>`, `--skip-download` for unselected installers, and
   `--os`/`--language` for the active filter chips), saved to the
   folder chosen in Settings. Saving outside the app folder requires the
   all-files-access permission (the downloader is a native process and cannot
   use SAF).
5. **Backup** — Settings can export/import gog-downloader's database
   (a single SQLite file holding your login tokens and synced library), so you
   can move to a new device or restore after a reinstall without re-syncing.

## License

GPL-3.0 (this app). The vendored GogDownloader and static-php-cli are MIT.
