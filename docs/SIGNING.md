# APK updates and signing

Android accepts an in-place update only when its package ID and signing certificate match the installed app. The catalog and photos stay in app-private storage during updates. Uninstalling removes that storage.

Earlier CI builds used newly generated debug keys. An APK contains only the public certificate, so its original private key cannot be recovered from the download. A new permanent key cannot directly update an installation signed by a lost debug key. **Do not uninstall your existing copy before backing up its data.** Its debug build permits a USB/ADB backup; a migration or export must be completed before switching certificates.

## Permanent signing certificate

Configured on 19 October 2026. The public certificate is [signing-certificate.pem](signing-certificate.pem); no private key is stored in this repository. CI checks permanently signed APKs against this certificate before publishing the install artifact.

SHA-256: `2C7401C1C05586912A38E3162EBA7BB5B4946DD3808C04A41A070D0966C7EE3A`.

The private key and credentials have a separate private backup. Do not create a new key when building on another computer; restore that backup.

## One-time setup

Install JDK 17, Python 3 and GitHub CLI. Authenticate with `gh auth login` using an account allowed to manage this repository's Actions secrets, then run from the repository:

```sh
python3 scripts/setup-signing.py
```

An optional argument selects a private backup folder outside the repository. The script creates a 4096-bit RSA signing key valid for 100 years and a random password, stores both privately on your computer, and sets these encrypted repository secrets:

- `HODD_KEYSTORE_BASE64`
- `HODD_KEYSTORE_PASSWORD`
- `HODD_KEY_ALIAS`
- `HODD_KEY_PASSWORD`

Keep a secure backup of the entire signing folder, including `credentials.json`. A retry reuses the same key; never replace it just to fix a build. No private signing material belongs in git or build artifacts. GitHub's connector currently cannot set these secrets; the setup script uses GitHub CLI instead.

## Builds to install

Once configured, the Android workflow publishes **hodd-mobile-install**, containing `app-release.apk` signed with the permanent key. Install it over the previous permanently signed version without uninstalling. CI sets `versionCode` from the Android workflow run number; keep that counter increasing when changing workflows. Reruns retain the same version code and key.

**hodd-mobile-test-apks** is for CI only. Without signing secrets, CI uses a temporary key to exercise release signing and upgrade preservation, and deliberately does not publish the install artifact. Partial signing configuration fails the build. Release tasks require a signing configuration and do not fall back to debug signing.

CI checks the APK signature with `apksigner`, then replaces a debug build with the release build and back on an emulator using `adb install -r`. It verifies that catalog and photo checksums survive. Both variants use the supplied key; no key is uploaded as an artifact.

Local signed builds accept `HODD_KEYSTORE_PATH`, `HODD_KEYSTORE_PASSWORD`, `HODD_KEY_ALIAS`, `HODD_KEY_PASSWORD`, and `HODD_VERSION_CODE` as environment variables.
