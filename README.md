# Hodd Mobile

A native Android home for the things you collect. Built with Kotlin and Jetpack Compose, inspired by [Hodd for desktop](https://github.com/vardirhq/hodd).

**Early development · 0.1.0-alpha.3** — the initial offline collection flows are implemented. This is not a production release.

## What you can do

- Create named collections for books, games, movies, records, comics, coins, or anything else.
- Browse a home shelf and recent additions, then search across titles, creators, notes, formats, and barcodes.
- Filter by owned, wanted, and lent out; see wanted items together in the wishlist.
- Add, edit, and delete items, including edition, condition notes, borrower, and barcode.
- Search offline field choices, select a game brand then platform, or add custom values.
- Capture barcodes with the bundled scanner, enter them manually, take a cover photo, or choose one from your device.
- Find book details by ISBN/title through Open Library, and CD/vinyl releases by barcode/title through MusicBrainz.
- Review fetched details and optionally download artwork from Open Library or Cover Art Archive.
- Read cover text offline using bundled ML Kit recognition; prefer Gemini Nano to structure that text when available.
- Describe an item naturally to propose fields using on-device AI on supported devices.
- Export and restore verified catalog/photo backups; recover recent catalog revisions.
- Use every collection feature offline, without an account or server.

Open **Find and fill details → Find online** to search by title and optionally creator. Title search takes priority even if an EAN/ISBN is present. With the title left empty, lookup can use a scanned or manually entered identifier. Select Book/Comic for Open Library or CD/Vinyl for MusicBrainz. Games and movies currently support local cover recognition and manual entry, not online metadata lookup.

Suggestions are reviewed before they fill the form. Existing values and photos are preserved unless replacement is explicitly selected. Nothing is saved until you tap Save. Open Library returns work-level matches: check your physical edition and cover. Community catalogs have gaps, and music barcodes can identify multiple releases.

Covers without photos are generated locally from the title and type. Example data is optional and uses fictional titles; your library starts empty.

## Build

Requires JDK 17 and an Android SDK with API 35 and Build Tools 35.0.0. Kotlin/Compose compiler 2.1.20, AGP 8.7.3 and Gradle 8.11.1 are pinned. The optional ML Kit Prompt integration is pinned to 1.0.0-alpha1; its availability and behavior depend on the device and AICore.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

Set `ANDROID_HOME` or put `sdk.dir=/path/to/android-sdk` in an untracked `local.properties`. Open the root directory in Android Studio to run on a device. Minimum Android version: 8.0 (API 26).

Installable CI APKs use a permanent private key and increasing version codes once signing is configured. See [APK signing and updates](docs/SIGNING.md). The `hodd-mobile-test-apks` artifact is for CI; install `hodd-mobile-install` for updates. Local debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Automated data tests (disposable emulator only; fixtures replace the test app catalog, and the runner refuses physical devices):

```sh
./gradlew :app:connectedDebugAndroidTest
```

## Versions and changelog

[VERSION](VERSION) is the shared app version. CI assigns an increasing Android build number independently. About Hodd shows both and includes the full changelog offline. Install artifacts contain a named APK, SHA-256 checksum, exact commit/build metadata and release notes. See [CHANGELOG.md](CHANGELOG.md) for changes and [release procedure](docs/RELEASING.md) for version bumps and tagged releases.

## Structure

| Module | Responsibility |
| --- | --- |
| `core` | Pure Kotlin models, validation, search, status counts, JSON codec, and unit tests |
| `app` | Compose screens, Android capture integrations, ViewModel, atomic file persistence |

One immutable catalog is observed by every screen. Writes run on the IO dispatcher behind a mutex; the visible catalog updates only after the atomic file save succeeds. Invalid or unreadable data is preserved and blocks writes instead of silently resetting the collection.

Photos are copied into app-private storage. Camera access is used only for capture/scanning. No Hodd account, paid API key, hosted backend, desktop connection, or sync is required.

## Local assistance and online metadata

**Read cover** runs bundled Latin-script ML Kit OCR on the device, including offline. If Gemini Nano is ready, Hodd prefers it to structure the recognized text; if it fails or is unavailable, you can choose a recognized line as the title. Missing author/edition information is not inferred by the OCR fallback. **Suggest from description** requires an available local model. AI output is untrusted, bounded and validated, and always reviewed; it can still be wrong.

Hodd checks Gemini Nano availability at runtime. AICore/device/model support varies, and Google Play/AICore initialization may be necessary. Where supported but not downloaded, **Set up local AI** offers an explicit model download. There is no cloud AI fallback, API key or AI subscription. Actual Nano generation still needs testing on supported physical hardware; emulator tests use a controlled model adapter to verify preference and failure handling.

**Find online** sends only a barcode, or title and creator, to Open Library or MusicBrainz over HTTPS. Photos, notes and your catalog are not uploaded. Requests identify Hodd, are serialized at no more than one per 1.1 seconds, and successful results are cached for 24 hours (up to 100 queries). Artwork is downloaded only when you select it and then stored locally. Errors/no matches leave the draft intact and manual entry remains available. Metadata source and provider ID/link are stored with the item.

Sources: [Open Library API](https://openlibrary.org/developers/api), [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API), [Cover Art Archive](https://musicbrainz.org/doc/Cover_Art_Archive). Cover artwork remains subject to its respective rights. Bundled OCR and the AICore integration are Google ML Kit components and may perform SDK/service diagnostics independently of Hodd; consult [ML Kit terms and privacy](https://developers.google.com/ml-kit/terms).

## Data storage
Open **About Hodd → Backups & recovery** to export a ZIP containing your collections, item details and referenced photos. The selected file is read back and checked before Hodd reports a verified export. Keep an exported copy on another device or in cloud storage, and make a new backup after important changes.

Saves use atomic writes with synchronization and readback verification. Up to 20 local catalog revisions are retained; old photo files are kept so recovery can restore their references. Import validates checksums, catalog relationships and required photos in temporary storage, shows a preview and requires confirmation. The previous catalog is preserved before replacement. An unreadable or unexpectedly missing catalog blocks editing and offers recovery rather than silently resetting data.

Internal recovery copies are removed by uninstalling or clearing app data. Android cloud backup remains disabled; there is no automatic cloud sync. Local revisions protect against some editing and file failures, but an independently stored export is needed for device loss or uninstall. Photos retained for recovery can increase storage use. See [backup and recovery details](docs/BACKUPS.md).

## Next steps

- Additional metadata sources and duplicate suggestions after scanning.
- Collection rename/delete and sorting; photo storage management that respects recovery history.
- Device testing of camera permission denial, large font sizes, rotation, and long collections.
- Optional desktop interoperability and GESH sync after the standalone app is solid.
- Norwegian strings, dark theme, and refinement against the design references.

No desktop catalog or GESH sync compatibility is claimed by this version.


### Guided field scanning

Tap the scan icon beside a supported text field to open a camera with a visible horizontal frame. Only text inside that frame is read, entirely on-device. Capture, choose a recognized line, and it is inserted into the selected field; other fields are kept. Camera access is requested on first use. The crop uses the same normalized bounds as the overlay and honors photo EXIF orientation. EAN/ISBN scanning remains a secondary button beside the identifier input; title/creator search is preferred.
