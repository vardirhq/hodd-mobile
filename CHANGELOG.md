# Changelog

## [Unreleased]

### Added
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

### APK signing and updates
- Added private-key signing configuration, increasing CI version codes, a one-time secret setup helper, and a dedicated install artifact.
- Added emulator replacement checks for catalog and photo preservation; test-only builds are labeled separately when signing is not configured.
