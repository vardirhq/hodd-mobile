# Hodd Mobile

A native Android home for the things you collect. Built with Kotlin and Jetpack Compose, inspired by [Hodd for desktop](https://github.com/vardirhq/hodd).

**Early development · 0.1.0** — the initial offline collection flows are implemented. This is not a production release.

## What you can do

- Create named collections for books, games, movies, records, comics, coins, or anything else.
- Browse a home shelf and recent additions, then search across titles, creators, notes, formats, and barcodes.
- Filter by owned, wanted, and lent out; see wanted items together in the wishlist.
- Add, edit, and delete items, including edition, condition notes, borrower, and barcode.
- Capture barcodes with the bundled scanner, enter them manually, take a cover photo, or choose one from your device.
- Use every collection feature offline, without an account or server.

Scanning captures a code. It does **not** automatically look up a title or artwork yet. Covers without photos are generated locally from the title and type. Example data is optional and uses fictional titles; your library starts empty.

## Build

Requires JDK 17 and an Android SDK with API 35 and Build Tools 35.0.0. The pinned toolchain follows the established Skald Mobile setup rather than using preview dependencies.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

Set `ANDROID_HOME` or put `sdk.dir=/path/to/android-sdk` in an untracked `local.properties`. Open the root directory in Android Studio to run on a device. Minimum Android version: 8.0 (API 26).

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Device/emulator checks:

```sh
./gradlew :app:connectedDebugAndroidTest
```

## Structure

| Module | Responsibility |
| --- | --- |
| `core` | Pure Kotlin models, validation, search, status counts, JSON codec, and unit tests |
| `app` | Compose screens, Android capture integrations, ViewModel, atomic file persistence |

One immutable catalog is observed by every screen. Writes run on the IO dispatcher behind a mutex; the visible catalog updates only after the atomic file save succeeds. Invalid or unreadable data is preserved and blocks writes instead of silently resetting the collection.

Photos are copied into app-private storage. Camera access is used only for capture/scanning. No account, analytics, desktop connection, or sync is required. Android cloud backup is disabled for this initial version; uninstalling the app removes its local data. Import/export and photo cleanup are still pending, so use this version for evaluation.

## Next steps

- Metadata lookup and duplicate suggestions after scanning.
- Portable backup/export including photos; collection rename/delete and sorting.
- Device testing of camera permission denial, large font sizes, rotation, and long collections.
- Optional desktop interoperability and GESH sync after the standalone app is solid.
- Norwegian strings, dark theme, and refinement against the design references.

No desktop catalog or GESH sync compatibility is claimed by this version.
