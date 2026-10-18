# Versions and releases

The first named alpha is `0.1.0-alpha.1`. The app remains early development software.

## Two version values

- `VERSION` holds the human-facing semantic version and is read by Gradle and the packaging script. Use `0.1.0-alpha.2` for another alpha of the same milestone, `0.2.0-alpha.1` for the next feature milestone, and `0.1.1` for a compatible patch after a stable `0.1.0`. Do not imply production readiness by removing the prerelease label prematurely.
- Android `versionCode` is the positive, increasing Android workflow run number. It controls upgrade/downgrade ordering and is independent of the semantic version. Re-running a workflow keeps its build number. Keep the workflow identity/counter when changing CI; if replacing it, arrange an explicit counter offset above every distributed build before publishing. Local development defaults to build 1; set `HODD_VERSION_CODE` above the installed build when making a local update.

The package ID and permanent signing key must stay the same. A version bump does not allow replacing an APK signed with a different key. See [signing and migration notes](SIGNING.md).

## Changelog discipline

Record notable user-facing work under `Unreleased` as it lands, grouped under Added, Changed, Fixed, Removed, Deprecated or Security. Mention data-format changes and any required migration explicitly. Keep implementation-only housekeeping out of user-facing notes unless it changes reliability, privacy or compatibility.

Named entries use `## [version] - YYYY-MM-DD`, newest first. Before a release, move the applicable Unreleased notes to the new dated version and update VERSION in the same commit. Keep released history intact; correct factual errors without rewriting what shipped. CI rejects invalid versions, missing/duplicate entries, invalid dates and empty current release notes.

About Hodd embeds this file at build time and displays version/build without a network request. CI snapshots can include Unreleased work after the named baseline; their full changelog and extracted notes identify it. A CI build is not automatically a published GitHub Release.

## Cutting a release

1. Prepare the version and dated changelog together. Review data compatibility; the catalog's JSON schema version is separate from the app version and requires migrations/tests when it changes.
2. Commit and push. Require core/UI tests, lint, certificate verification and APK replacement preservation checks to pass.
3. From that successful build, use `hodd-mobile-install`. It contains `hodd-mobile-<version>-build<code>.apk`, `SHA256SUMS.txt`, `build-info.json`, `release-notes.md` and the full changelog. The metadata identifies the exact checked-out commit; PR builds may be built from GitHub's merge commit.
4. To publish a GitHub Release, tag the exact validated source commit as `v<version>`, mark alpha/beta/rc versions as prereleases, use the curated notes and attach the verified APK/checksum. Do not rebuild or change keys just to upload the asset. Tagging/publishing is a separate deliberate action; this workflow does not do it automatically.
5. Open a new Unreleased section for subsequent work. Each independently delivered build must have a higher versionCode; never lower or reset the counter.

All installs from the permanent signing key can be replaced with newer builds using that key without uninstalling. Earlier temporary-debug-key installations need backup/migration before their first switch.
