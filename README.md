# Locker

A local-first, strongly encrypted Android vault for secrets, notes, documents, things, and photos. Built with Kotlin, Jetpack Compose, and Material 3 (Material You).

Locker is inspired by the calm, card-based layout of Ente Locker, but it is deliberately **offline-only**: the app has **no INTERNET permission**, no account, no sync, and no telemetry.

[Download the latest release](https://github.com/Avithegod2010/locker/releases/latest) · [Security policy](SECURITY.md) · [MIT license](LICENSE)

## Quick start

1. Install `Locker-1.6.0-release.apk` from the latest release.
2. Create a vault password (10+ characters) and save the numeric recovery code.
3. Optionally enable biometrics and Autofill in **Security**.
4. Add a secret, note, photo, video, voice memo, scan, or file. Everything is encrypted on-device before it is written to disk.

## Features

- **Five vault types** — Secret (passwords), Note, Thing (real-world item), File, and Photo.
- **Strong encryption at rest** — every payload and file is authenticated and encrypted. Plaintext is never written to the vault database or vault file directory.
- **Numeric recovery code** — 24 random digits plus a check digit that catches typos, independently wrapping the vault key. Older Base32 recovery keys still unlock the same vault, and the code can be rotated from Security. Onboarding and the Security dialog share the same five-group formatting, and you can save a printable one-page **recovery kit PDF** with the key and step-by-step instructions.
- **Backup and restore** — create one encrypted `.locker` archive, preview it, then merge or replace. Whole-archive integrity is verified before anything is changed.
- **Biometric unlock** — hardware-backed Android Keystore key that requires a fresh authentication, running in a dedicated secure activity.
- **Vault password changes** — re-wraps the vault key without touching any items; Argon2id parameters auto-upgrade on unlock.
- **Passwords** — TOTP codes, custom fields, password health, offline audit, a configurable generator (length, digits, symbols, look-alike filter), tags, and website fields.
- **Password history** — the previous 10 passwords for each login are kept encrypted with the item. You can reveal, copy, or restore an old password from the item screen; restoring pushes the current password into history.
- **Previous versions** — secrets and notes keep their last 10 encrypted title/notes/login snapshots. Restore an earlier version from the item screen; the current version is saved first so you can switch back.
- **Item templates** — start a login, Wi-Fi network, bank account, insurance policy, ID/passport, or note from a structured template. Custom fields stay editable, and you can attach photos to any item.
- **Per-item biometric gate** — require a fresh fingerprint before a protected secret, note, or file shows its contents, allows copies, or opens attachments. Protected items are deliberately excluded from autofill.
- **Expiry dates and dashboard** — give a secret or thing an optional expiry date (passports, insurance, cards, renewals). The item shows an Active / Expires soon / Expired card, and **Password health** lists everything expired or expiring within 30 days.
- **Tag manager** — rename a tag everywhere from the drawer, merge it into an existing tag by renaming, or delete it from all items without touching the items themselves.
- **Password-manager import** — offline import from Bitwarden (CSV and JSON), 1Password (CSV and .1pux), KeePass XML, LastPass, KeePassXC, Dashlane/ZIP CSV, Chrome/Edge, Firefox, Samsung Internet, and other CSV exports. You preview every row (with automatic duplicate detection) and choose what to import; folders that match an existing collection are filed there automatically, and TOTP secrets and notes come along. **Import TOTP QR** reads a single `otpauth://` link or a Google Authenticator migration QR from a screenshot or photo.
- **Launcher disguise** — show the app as Calculator, Calendar, or Notes on the home screen. All three are usable: Calculator is a working calculator (enter the passcode and press `=`), Notes is a working notepad, and Calendar is a real month view — long-press the title in Notes or Calendar to enter the passcode. Your vault password is still required afterwards. Only one icon is shown at a time; if your launcher caches the old icon, use **Refresh launcher icon** in Security. Shortcuts and the home-screen widget follow the active alias.
- **Duress password (non-destructive)** — an optional second password that opens a separate decoy vault with its own keys, database, and files. Your real vault stays encrypted and untouched, and your normal password still opens it. Resetting the duress password rebuilds the decoy with fresh example entries.
- **In-app video recording** — the secure camera has Photo and Video modes, records MP4 with optional audio (microphone permission is requested on first video use), and saves the clip straight into the encrypted vault with no gallery copy. Videos get encrypted thumbnails, resume where you left off, remember playback speed, and can be trimmed in place with an automatic “(original)” copy kept in the vault.
- **Voice memos** — record encrypted audio notes with a live waveform, pause and resume mid-recording, then play them back with a waveform scrubber and 0.5×–2× speed.
- **Document scanner** — capture several pages, auto-trim the borders, reorder pages, and save one encrypted PDF straight into the vault.
- **Media** — multi-select photo/video import, encrypted video playback, secure in-app camera, PDF viewer, OCR search, rotation, draw-and-save photo redaction, and a slideshow. Image imports strip GPS coordinates and camera/device EXIF tags before anything is encrypted.
- **Local diagnostics** — an app-private crash log with build details, visible under **About → Diagnostics** and exportable as a plain text file through the system picker. No network path exists.
- **Nested collections and smart folders** — collections can contain subcollections; any search (`type:secret tag:bank`) can be saved as a smart folder, renamed, given an icon, and pinned to Home.
- **Daily automatic backups** — optional encrypted `.locker` snapshots into a folder you choose, at most once a day after a password unlock, rotating the last N archives. Every automatic snapshot is read back and verified before it replaces the previous one, and **Verify latest snapshot** runs a restore drill without touching the vault. Fully offline.
- **Quick-capture widget actions** — the home-screen widget can open a new note or the camera, and lock the vault.
- **Collections, tags, Important, and Archive** — group items, cut across collections with tags, bulk-select, and hide things without deleting them.
- **Search and filters** — search across titles, usernames, notes, locations, filenames, OCR text, tags, custom fields, types, and collections; supports `type:`, `tag:`, `before:`, and `after:`.
- **Share target and open-with** — send files, photos, videos, PDFs, or text to Locker from any app, or open supported files directly.
- **Autofill** — fill saved logins in other apps while the vault is unlocked. Matching understands full URLs, subdomains, app packages, and common field hints; saving an autofilled password updates the matching login instead of creating a duplicate.
- **Quick Settings tile, shortcuts, and a home-screen widget** — lock instantly, jump to a new note or secret, or open/lock from the launcher.
- **Haptics** — subtle tactile feedback on cards, chips, and primary actions, with a toggle in Appearance.
- **Files** — import any document, open it after decryption, share a temporary decrypted copy (with warning), or export it through the system file picker.
- **Legacy** — encrypted emergency contacts and notes for people you trust.
- **Trash** — soft delete, restore, delete forever, and optional automatic purge after 30 days.
- **Security controls** — auto-lock, screen-off lock, screenshot protection, screen-recording and external-display locks, secure sensitive screens, clipboard timeout, optional erase-after-failed-attempts, and a typed-confirmation vault wipe. A short hint under **Prevent screenshots** explains the Samsung One UI keyboard fade that affects protected apps while typing. Rooted or test-build devices show a warning because screenshot and Keystore guarantees are weaker there.
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
| Duress password | A separate vault space with its own wrapped keys, database, and encrypted files; the dispatcher unlocks the decoy while the real vault remains encrypted and reachable with the normal password. Failed decoy attempts use a decoy-only counter and cannot trigger the real vault's erase-after-N rule |
| Automatic backups | Encrypted `.locker` archives written to a user-picked SAF folder; the vault password copy used for the snapshot is held only for the length of the backup and wiped afterwards |
| Launcher disguise | Activity aliases and `PackageManager` component states; the vault password is always required |
| Item payloads | JSON encrypted with AES-256-GCM; AAD binds item id and type |
| File bodies | 128 KiB AES-256-GCM chunks, random per-file key, random 8-byte nonce prefix + 32-bit chunk counter, AAD binds file id / chunk index / file-vs-thumbnail; whole-file size and SHA-256 are verified after decryption |
| File keys | Wrapped by the vault master key with AES-256-GCM and store-bound AAD |
| Thumbnails | Encrypted with the file key |
| Profile | Encrypted in the vault database |
| Biometrics | AES-256-GCM key in Android Keystore, `setUserAuthenticationRequired(true)`, confirmation required, invalidated by new biometric enrollment |
| Database | SQLite stores only ids, item types, timestamps, and encrypted payload blobs |
| Diagnostics | Crash reports stay in an app-private file, capped in size, removable from **About → Diagnostics** |
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

The same command runs in GitHub Actions on every push and pull request (`.github/workflows/android.yml`), and the HTML report is uploaded as a build artifact.

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

The release variant has `DEV_BUILD=false` and defaults screenshot protection to **on**.

The **1.3.1 hotfix, 1.4.0, 1.4.1, 1.5.0, and 1.6.0** are packaged with R8 disabled (`isMinifyEnabled = false`) because the build host repeatedly ran out of native memory under concurrent Gradle builds. Signing, behavior, and permissions are identical — the APK is simply larger and not obfuscated. Later releases will return to the minified configuration.

### One-command release

`scripts/release.ps1` bumps `versionCode`/`versionName` when needed, runs the unit tests, builds the signed APK, computes its SHA-256, commits, tags, pushes, and publishes the GitHub release:

```powershell
.\scripts\release.ps1 -Version 1.6.0 -NotesFile "$env:TEMP\locker-notes-1.6.0.md"
```

Add `-DryRun` to stop after the build and hash, or `-SkipTests` when the tests already ran. The working tree must be clean before the script starts.

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

The signed release build (version 1.6.0; R8 disabled because the build host hit native memory limits under concurrent builds) was installed with `adb install -r` on an Android 17 Samsung phone (SM-S942B) and exercised end-to-end with a temporary vault:

- fresh install → create vault → numeric recovery key shown in five 5-digit groups → Group 2 and Group 4 verified → onboarding completed; data survived a `-r` reinstall
- **Bank template** filled the title, notes, and structured bank/account fields
- editing a note created a **Previous versions** snapshot; restoring it returned the earlier text and kept the newer version in the list
- **per-item biometric gate** hid a protected secret behind a lock card; the Android prompt appeared, and cancelling returned to the locked card with the content still hidden
- **Bitwarden JSON** imported 2 logins and skipped the 1 non-login item; folder, TOTP, notes, and favourite state mapped correctly
- **Google Authenticator migration QR** imported the account and produced a live refreshing 6-digit code
- **photo redaction** saved a new item; the exported copy had a solid black box over the redacted line and the rest of the image unchanged, and the new item kept its PNG type
- **Diagnostics** showed the build/device details and exported a text file with no crash reports
- the temporary vault was erased afterwards; the app was left at onboarding

Unit tests pass for AES-256-GCM round-trip/tamper rejection, Argon2id determinism, SHA-256, RFC 6238 TOTP vectors, numeric recovery codes (including legacy Base32 keys), CSV/Bitwarden/ZIP import parsing, Google Authenticator migration QR parsing, item-history retention, password-generator guarantees, the calculator engine, and the predictable-password detector.

Version 1.3.x had previously been exercised on the same device for the calculator disguise, in-app video recording, voice memos, document scanning, password import, smart folders, and the icon set. Version 1.2.0 was exercised for the numeric recovery code, launcher disguises, accent themes, and true-black mode. Version 1.1.0 was exercised for vault creation, release-mode unlocking, recovery-key retrieval/rotation, encrypted notes and secrets with live TOTP, encrypted photo/video import and playback, `.locker` backup/merge/replace, and raw `vault.db` inspection proving that no plaintext item fields are stored.

## Known limitations / next steps

- Intentionally **local-only**; there is no cloud sync, account, or cross-device sharing.
- No scheduled reminders and no Wear OS companion yet.
- Collections support nesting, saved searches, icons, and Home pins; per-collection locks and smart rules beyond saved queries are not implemented.
- Passkeys, encrypted QR transfer, import from Bitwarden/1Password JSON or Dashlane ZIP, manual column remapping, and tablet two-pane layouts are not implemented yet.
- Automatic backups are triggered on unlock (no WorkManager scheduling), run at most once a day, and only after a password unlock (not after a biometric-only unlock).
- Password history and expiry dates are stored per item; there are no push notifications or reminders for upcoming expiries — they appear in **Password health** when you open the app.
- The duress password opens a decoy vault; it does not erase the real vault. Deleting the duress password deletes the decoy only.
- Autofill is designed for the unlocked vault and does not yet require a per-fill biometric prompt.
- English UI only.
- Legacy is an encrypted emergency-contact/notes page; it does not automatically transmit anything to contacts.

## Fonts

Plus Jakarta Sans is bundled under the SIL Open Font License. The license text is in `app/src/main/assets/licenses/OFL-PlusJakartaSans.txt`.
