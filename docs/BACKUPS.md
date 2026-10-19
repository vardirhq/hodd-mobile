# Backups and recovery

## Everyday use

Open **About Hodd → Backups & recovery → Export backup**. Choose a location through Android’s document picker. Hodd writes a ZIP and reads the selected file back to verify its SHA-256 checksum. A verified date appears only after this succeeds. If a provider cannot read the file back, try another location; do not assume the export succeeded.

Keep a copy outside this phone (for example in your own cloud storage or on a computer), and export again after important changes. Verification checks the bytes returned by the document provider; it cannot verify that a cloud provider has completed its remote upload. Exports contain your item details, notes, borrowers and photos without encryption, so choose an appropriate private location.

To check a backup without replacing anything, select **Import backup**, review the counts and titles, then **Cancel restore**. A confirmed import replaces the whole catalog; it does not merge collections. Referenced photos receive new filenames so existing photos cannot be overwritten. A recovery copy of the current readable catalog is kept before replacement.

## Local protection

- Catalog writes use Android AtomicFile, explicit file synchronization and byte-for-byte readback. The visible catalog updates after verification.
- Before replacing a catalog, Hodd saves the previous catalog and the candidate revision in app-private recovery storage. A failure to create required recovery copies blocks the save.
- The latest 20 distinct catalog revisions are retained after successful saves. These share immutable photo files; deleting an item does not delete its photo.
- On load, Hodd validates the catalog. Unreadable data, or a missing catalog with recovery copies/photos, blocks editing and offers recovery instead of silently creating an empty library.
- Recovery is explicit: choose a readable revision, review its counts and confirm replacement. When the existing primary file is unreadable, its original bytes are preserved before recovery/import.
- Interrupted candidate saves may leave an additional recovery revision. Review dates and counts when selecting one.

Recovery copies are on the same device, inside the app. Uninstall, clear-data, device loss, storage failure, or damage to the entire app directory can remove them too. Android cloud backup is disabled. There is no automatic remote backup or sync. Export independently for those cases. No storage system can promise zero loss; these protections reduce specific failure modes and give you recovery options.

Photos are currently retained rather than garbage-collected, including photos no longer referenced by the current catalog. Exports contain only photos referenced by the exported catalog. Monitor device storage and keep independent backups.

## Archive format and validation

Backup format 1 is a ZIP containing `catalog.json`, optional `photos/<safe filename>` entries, and `manifest.json`. The manifest contains the format version, export timestamp, app version and SHA-256 checksums for the catalog and each photo. It is an integrity check, not an authenticated signature.

Import uses a fresh staging directory and validates all entries before displaying the confirmation. Paths must match the allowlist; duplicate entries, directory entries, unexpected files, unsupported formats, checksum mismatches, invalid catalog links and missing or unreferenced photos are rejected. Limits: 16 MiB catalog, 20 MiB per photo, 2 MiB manifest, 10,000 photos and 2 GiB total decompressed data. Exports apply matching catalog/photo limits. Staging and restoring need additional free storage.

App version, Android build number, catalog schema version and backup format version are separate. Existing catalogs retain schema version 1; this backup feature uses manifest format 1. Future schema changes must retain compatibility or explicitly migrate older data.

## Validation

Core tests exercise archive round trips, required photos, tampering, duplicate entries, unsafe paths, decompression limits and unsupported versions. Emulator tests exercise catalog/photo export and restore, failed saves, unreadable/missing primary catalogs, retained history, canceled imports and confirmed replacement. APK replacement checks compare catalog and photo hashes across debug → release → debug installs using the same signing certificate.

Automated fixtures require a disposable emulator and intentionally replace fixture data. Do not run them against an app installation you use for your own collection.
