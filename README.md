# Locker

A local-first, strongly encrypted Android vault for secrets, notes, documents, things, and photos. Built with Kotlin, Jetpack Compose, and Material 3 (Material You).

Locker is inspired by the calm, card-based layout of Ente Locker, but it is deliberately **offline-only**: the app has **no INTERNET permission**, no account, no sync, and no telemetry.

[Download the latest release](https://github.com/Avithegod2010/locker/releases/latest) · [Security policy](SECURITY.md) · [MIT license](LICENSE)

## Quick start

1. Install `Locker-1.2.0-release.apk` from the latest release.
2. Create a vault password (10+ characters) and save the numeric recovery code.
3. Optionally enable biometrics and Autofill in **Security**.
4. Add a secret, note, photo, video, or file. Everything is encrypted on-device before it is written to disk.

## Features

- **Five vault types** — Secret (passwords), Note, Thing (real-world item), File, and Photo.
- **Strong encryption at rest** — every payload and file is authenticated and encrypted. Plaintext is never written to the vault database or vault file directory.
- **Numeric recovery code** — 24 random digits plus a check digit that catches typos, independently wrapping the vault key. Older Base32 recovery keys still unlock the same vault, and the code can be rotated from Security.
- **Backup and restore** — create one encrypted `.locker` archive, preview it, then merge or replace. Whole-archive integrity is verified before anything is changed.
- **Biometric unlock** — hardware-backed Android Keystore key that requires a fresh authentication, running in a dedicated secure activity.
- **Vault password changes** — re-wraps the vault key without touching any items; Argon2id parameters auto-upgrade on unlock.
- **Passwords** — TOTP codes, custom fields, password health, offline audit, a configurable generator (length, digits, symbols, look-alike filter), tags, and website fields.
- **Password-manager import** — offline CSV import for Bitwarden, 1Password, LastPass, KeePassXC, Chrome/Edge, Firefox, Samsung Internet, and other exports. Folders that match an existing collection are filed there automatically; TOTP secrets and notes come along.
- **Launcher disguise** — show the app as Calculator, Calendar, or Notes on the home screen. The icon and name change, but every launch still lands on Locker's own lock screen, and your data is untouched.
- **In-app video recording** — the secure camera has Photo and Video modes, records MP4 with optional audio (microphone permission is requested on first video use), and saves the clip straight into the encrypted vault with no gallery copy.
- **Media** — multi-select photo/video import, encrypted video playback, secure in-app camera, PDF viewer, OCR search, rotation, redaction, and a slideshow.
- **Collections, tags, Important, and Archive** — group items, cut across collections with tags, bulk-select, and hide things without deleting them.
- **Search and filters** — search across titles, usernames, notes, locations, filenames, OCR text, tags, custom fields, types, and collections; supports `type:`, `tag:`, `before:`, and `after:`.
- **Share target and open-with** — send files, photos, videos, PDFs, or text to Locker from any app, or open supported files directly.
- **Autofill** — fill saved logins in other apps while the vault is unlocked.
- **Quick Settings tile, shortcuts, and a home-screen widget** — lock instantly, jump to a new note or secret, or open/lock from the launcher.
- **Haptics** — subtle tactile feedback on cards, chips, and primary actions, with a toggle in Appearance.
- **Files** — import any document, open it after decryption, share a temporary decrypted copy (with warning), or export it through the system file picker.
- **Legacy** — encrypted emergency contacts and notes for people you trust.
- **Trash** — soft delete, restore, delete forever, and optional automatic purge after 30 days.
- **Security controls** — auto-lock, screen-off lock, screenshot protection, screen-recording and external-display locks, secure sensitive screens, clipboard timeout, optional erase-after-failed-attempts, and a typed-confirmation vault wipe. A short hint under **Prevent screenshots** explains the Samsung One UI keyboard fade that affects protected apps while typing.
- **Appearance** — dark, light, or system theme, eight accent palettes, an optional pure-black OLED background, and an optional wallpaper-based Material You colour scheme.
- **Encrypted local profile** — name/label are stored inside the vault, not in system preferences.

## Security design

| Layer | Implementation |
| --- | --- |
| Vault master key | 256-bit random value generated with `SecureRandom`; only in memory while unlocked; defensive copies are used for in-flight writes and wiped afterwards |
| Password KDF | Argon2id, 96 MiB memory, 4 iterations, 4 lanes, 16-byte random salt; minimum 10-character password; older v1 vaults upgrade automatically |
| Backup format | `.locker` archive with Argon2id-derived backup key, wrapped master key, authenticated manifest, chunked AES-256-GCM records, and a whole-archive hash |
| Master key wrapping | AES-256-GCM, authenticated AAD, fresh nonce |
| Recovery key | 24 random digits + SHA-256 check digit (legacy 32-byte Crockford Base32 keys remain valid), stretched with Argon2id; an encrypted display copy is kept for retrieval while unlocked |
| Item payloads | JSON encrypted with AES-256-GCM; AAD binds item id and type |
| File bodies | 128 KiB AES-256-GCM chunks, random per-file key, random 8-byte nonce prefix + 32-bit chunk counter, AAD binds file id / chunk index / file-vs-thumbnail; whole-file size and SHA-256 are verified after decryption |
| File keys | Wrapped by the vault master key with AES-256-GCM and store-bound AAD |
| Thumbnails | Encrypted with the file key |
| Profile | Encrypted in the vault database |
| Biometrics | AES-256-GCM key in Android Keystore, `setUserAuthenticationRequired(true)`, confirmation required, invalidated by new biometric enrollment |
| Database | SQLite stores only ids, item types, timestamps, and encrypted payload blobs |
| Backups | Android backup and device transfer are disabled |
| Network | No `INTERNET` permission in the manifest |

The 32-byte master key is zeroed when the vault locks. Long-running operations hold private copies, so a lock during a large import cannot corrupt the file key. Decrypted metadata is cleared from the in-memory repository, short-lived decrypted share/camera caches are cleared, and the thumbnail cache is evicted. Attempts are committed synchronously for the optional erase-after-failed-attempts feature.

### Losing access

There is no server and no password reset. If you lose both your password and your recovery key, the vault cannot be recovered. That is the trade-off for local-only, zero-knowledge storage.

## Build

Requirements:

- JDK 17
- Android SDK 35 (compileSdk)
- Android Gradle Plugin 8.7.3 (downloaded by Gradle)
- Android 8.0 (API 26) or newer device

```powershell
# Windows
.\gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

The project includes `local.properties` pointing at the local SDK for this machine. Change it if your SDK lives elsewhere.

Run the unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Install the debug build:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Release signing

A local release keystore was generated for this project:

- `keystore/locker-release.jks`
- `keystore.properties` (contains the store/key password)

Both are intentionally ignored by `.gitignore`. **Back them up somewhere safe**; every future update of the installed release app must be signed with this key. The signed APK is written to:

```
app/build/outputs/apk/release/app-release.apk
```

Build it with:

```powershell
.\gradlew.bat :app:assembleRelease
```

The release variant is minified with R8, has `DEV_BUILD=false`, and defaults screenshot protection to **on**.

`BuildConfig.DEV_BUILD` is true only for debug builds. The screenshot-protection preference defaults to **on** in both debug and release; turn it off from Security when you need to capture screenshots during development.

## Project structure

```
app/src/main/java/app/locker/vault/
  core/                 Argon2/AES toolbox, recovery key, preferences, lock session
  data/                 SQLite wrapper, encrypted file store, repository, models
  ui/theme/             Material 3 colour schemes and Plus Jakarta Sans typography
  ui/components/        shared cards, fields, icons, search, biometric helper, thumbnail cache
  ui/navigation/        NavHost and routes
  ui/screens/           onboarding, lock, home, editor, detail, viewer, collections,
                        trash, legacy, settings
```

## Verified on device

The signed, minified release build (`DEV_BUILD=false`, R8, version 1.2.0) was installed in place over 1.1.0 on an Android 17 Samsung phone (1080 × 2340):

- in-place update (`adb install -r`) with the real vault intact — no wipe and no re-onboarding
- the updated app launches straight to its locked screen ("Welcome back", password, biometrics, recovery key); no crash appeared in logcat
- APK metadata verified: versionName 1.2.0 (versionCode 3), no INTERNET permission, three launcher disguises registered, and the same release signing certificate
- unit tests pass for AES-256-GCM round-trip/tamper rejection, Argon2id determinism, SHA-256, RFC 6238 TOTP vectors, numeric recovery codes (including legacy Base32 keys), CSV import edge cases (quoted commas, embedded newlines, semicolons, BOM), and password-generator guarantees

Version 1.1.0 had previously been exercised on the same device for vault creation, release-mode unlocking, recovery-key retrieval/rotation, encrypted notes and secrets with live TOTP, encrypted photo/video import and playback, `.locker` backup/merge/replace, and raw `vault.db` inspection proving that no plaintext item fields are stored.

The camera screens (including in-app video recording), launcher disguises, and CSV import screens compile in both debug and release and their static surfaces were verified on device, but their interactive flows should still be exercised by the vault owner because they depend on OEM camera, document-picker, and launcher behaviour.

## Known limitations / next steps

- Intentionally **local-only**; there is no cloud sync, account, or cross-device sharing.
- No scheduled reminders and no Wear OS companion yet.
- Collections are flat (no nesting or smart rules); search supports `type:`, `tag:`, `before:`, and `after:` but has no saved searches or command palette.
- No duress/decoy vault, multiple vaults, per-collection lock, or full master-key rotation yet.
- Backup and CSV import are full/manual operations, not two-way sync; backups must be stored in a folder you control.
- Document scanning, face/plate blur, ZIP import, and Live Photo/GIF preservation are not implemented.
- Autofill is designed for the unlocked vault and does not yet require a per-fill biometric prompt.
- English UI only.
- Legacy is an encrypted emergency-contact/notes page; it does not automatically transmit anything to contacts.

## Fonts

Plus Jakarta Sans is bundled under the SIL Open Font License. The license text is in `app/src/main/assets/licenses/OFL-PlusJakartaSans.txt`.
