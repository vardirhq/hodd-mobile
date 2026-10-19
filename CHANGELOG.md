# Changelog

Notable changes are grouped by version. Work in progress goes under Unreleased; dated entries describe named alpha builds.

## [Unreleased]

## [0.1.0-alpha.5] - 2026-10-19

### Added
- Keyless Wikidata search for games and movies from a reviewed cover identity or typed title.
- Canonical catalog titles, publisher, series, earliest catalog release year, developer/director and supported game platform details, with source links and explicit review before applying.
- Type, sequel-number and platform checks; ambiguous multiplatform works do not guess a physical edition.
- Rate-limited, bounded requests and a 24-hour results cache. Photos and notes stay local.

## [0.1.0-alpha.4] - 2026-10-19

### Changed
- Cover identification first tries image plus OCR input through the local model, with text/OCR fallback when unsupported or unsuccessful.
- Made cover prompts category-aware, using image input from the existing pinned ML Kit Prompt integration.
- Cover results retain raw text alternatives; recognized text, suggested title, creator and type can be reviewed/corrected before applying.
- Cover-to-catalog lookup now requires reviewing the search identity before sending it online.

### Fixed
- Platform branding is rejected as a work title, including some damaged header text.
- Game title lines are no longer accepted as creators without supporting credit/company evidence.
- Unsupported facts from text-only cover suggestions are removed; cover recognition cannot infer ownership or condition.
- Raw model responses are no longer included in recognition diagnostics.

## [0.1.0-alpha.3] - 2026-10-19

### Added
- Offline searchable choices for formats, publishers, languages, release years, condition and category-specific details.
- Game platform selection by brand, including current and legacy systems, search aliases and confirmation before clearing an existing platform.
- Movie region choices by format and coin denomination suggestions by country.
- Custom-value dialogs and reusable values from saved items, without rewriting existing custom or fetched text.
- Bundled brand/platform logos with source and license notices, plus fallback icons.

### Fixed
- Category-specific draft attributes now survive activity recreation.

## [0.1.0-alpha.2] - 2026-10-19

### Added
- Backups & recovery in About Hodd, with portable ZIP exports containing the catalog and referenced cover photos.
- Verified export readback, checksums, bounded archive validation, import preview and explicit replacement confirmation.
- Up to 20 local catalog recovery copies, with retained photos and recovery access when the primary catalog cannot be opened.
- Preservation of the previous catalog before replacement, including unreadable original bytes during explicit recovery.
- Backup round-trip, corrupt archive, missing file, failed save, cancel/confirm and recovery tests.

### Changed
- Saves sync and verify their contents before updating the visible collection.
- Data fixtures and APK replacement checks run only on disposable emulators.

### Fixed
- A missing primary catalog with recovery copies or stored photos no longer silently opens as a new empty collection.

## [0.1.0-alpha.1] - 2026-10-19

### Added
- Version and build information plus an offline changelog in About Hodd.
- A shared VERSION file, release metadata, checksums, and APK filenames identifying their version/build.
- One-time signing setup and emulator checks that app replacement preserves catalog and photo data.
- Keyless Open Library ISBN/title and MusicBrainz barcode/title searches, result caching and optional cover downloads.
- Offline cover text recognition, with preferred on-device Gemini Nano structuring where supported.
- Local AI description-to-fields, explicit model setup and review-before-apply without overwriting existing edits by default.
- Metadata provenance and compatibility with older saved catalogs.
- Provider parsing/identifier/merge tests and emulator coverage for caching, OCR, and local AI preference/fallback.
- Native Android home, collections, wishlist, item details, and add/edit screens.
- Offline collections with atomic local persistence and recoverable save errors.
- Search, status filters, ownership counts, and lending details.
- Barcode capture, manual code entry, camera photos, and photo selection.
- Optional example collection, local typographic covers, and empty states.

### Changed
- Item details now use a cover-led header, dedicated lending/wishlist cards, grouped metadata, and persistent edit/delete controls.
- Light app screens use dark system-bar icons; the camera temporarily switches to light icons for contrast.
- Kotlin and Compose compiler pinned to 2.1.20 for the local assistance integration.

### Fixed
- Field scanning uses the full app window so Capture text stays above Android navigation controls, including when opened from a focused input.
- Guided field OCR requests camera access and inserts confirmed text into the selected field.
- The visible scan frame and OCR use the same crop, including EXIF-rotated photos.
- Camera binding waits for preview layout and releases only its own use cases when closed.
- The barcode scanner follows the phone’s orientation instead of forcing landscape.

### Security
- APKs use a permanent private signing key, with CI verification against the pinned public certificate.
- Test-only APKs are labeled separately from installable builds.
- The first transition from earlier temporary debug keys requires a data backup before switching certificates.
