# Locker — agent context

This file is the working brief for any AI agent continuing the Locker project.

## What Locker is

Locker is a **fully offline, local-first Android vault** for secrets, notes, real-world things, files, photos, and videos. It is written in Kotlin with Jetpack Compose and Material 3.

Non-negotiable product constraints:

- **No INTERNET permission**, no account, no cloud, no sync, no telemetry.
- Everything is encrypted on-device before it is written to disk.
- The app must remain usable with the system back gesture and one-handed phone use.
- Same release signing key forever. Never change the keystore or applicationId.
- Do not wipe user data during updates. Test installs must use `adb install -r`.
- Screenshot protection defaults to **on**.
- Launcher disguise icons stay flat coloured squircles; the real icon is a blue shield with a keypad.

## Current state

- **Latest release: 1.10.0** (versionCode 12).
- Previous: 1.9.0 (versionCode 11), release https://github.com/Avithegod2010/locker/releases/tag/v1.9.0
- Release 1.9.0 APK asset: `Locker-1.9.0-release.apk`, SHA-256 `100fb9cff69f8518a0f0d33df9b079883bdde201816ddc239baa5b772bfeb429`.
- Signing certificate SHA-256: `96fb04402dbea1109402e6755e1b1187f393d632f0835d4a790b0ad083420867`.
- Main line: `46d61d7` was the last docs commit before the 1.10.0 work.
- The app is left at the welcome/onboarding screen after the last verification run.

## Tech stack

- Kotlin 2.1.x, Jetpack Compose, Material 3, Navigation Compose.
- compileSdk 35, minSdk 26, targetSdk 35.
- Argon2id via Bouncy Castle, AES-256-GCM, Android Keystore.
- SQLite via `SQLiteOpenHelper`; encrypted payload blobs only.
- CameraX, Media3, ML Kit text recognition (OCR), ZXing core (QR).
- No network libraries. Do not add any dependency that needs network at runtime.

## Build environment (Windows host)

Use these environment variables for every build:

```powershell
$env:JAVA_HOME='E:\Dev\jdk\jdk-17.0.20+8'
$env:GRADLE_USER_HOME='E:\Dev\.gradle'
$env:ANDROID_HOME='E:\Dev\android-sdk'
```

Release signing uses the git-ignored `keystore/` and `keystore.properties`.

### Build commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1
.\gradlew.bat :app:assembleRelease --no-daemon --max-workers=1
```

The host has ~8 GB RAM. Other Gradle builds (other projects, Android Studio auto-builds, aura-camera) can starve it and cause `OutOfMemoryError`, `Metaspace`, or a daemon crash. If that happens:

1. Stop unrelated `java.exe` / Gradle processes.
2. Retry with a bounded heap:
   `"-Dorg.gradle.jvmargs=-Xmx1280m -XX:MaxMetaspaceSize=320m"`.
3. If it still OOMs, free more memory and retry `assembleRelease` with `1536m`.
4. Never enable R8 for release on this host. R8 stays disabled; the APK is large and unobfuscated.

APK output: `app/build/outputs/apk/release/app-release.apk`.

## Release checklist

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Update `README.md` (APK name, features, verification, limitations) and `CHANGELOG.md`.
3. Run the full unit tests; expect all green.
4. Build the signed release APK.
5. Record size, SHA-256, versionCode/versionName, and apksigner certificate SHA-256.
6. Commit with the noreply identity:
   `Avithegod2010 <258314859+Avithegod2010@users.noreply.github.com>`.
7. Tag `vX.Y.Z`, push `main` and the tag.
8. Rename a copy of the APK to `Locker-X.Y.Z-release.apk` and create the GitHub release with notes that include `APK SHA-256: ...`.
9. Confirm the GitHub Actions unit-test run is green.
10. Verify on-device when a phone is available, then erase only the temporary test vault.

`scripts/release.ps1` automates bump/test/build/hash/commit/tag/push/publish. It requires a clean tree and a notes file.

## On-device verification

Phone used: Samsung SM-S942B, Android 17. Connect over wireless ADB; the mDNS name changes, so always run `adb devices -l` and use the current id.

Important automation notes:

- The phone has been running **DeX on an external display**. `uiautomator dump` follows the external display. To move Locker back to the external display for automation, get its task id and run:
  `adb shell am display move-stack <taskId> <displayId>`
  (display ids come from `dumpsys SurfaceFlinger --display-id`).
- The phone display itself often shows black in `screencap` because screenshot protection is on.
- Other apps have repeatedly stolen foreground. Re-check `dumpsys activity activities | Select-String topResumedActivity` before trusting taps, and relaunch Locker with `am start --display <id> -n app.locker.vault/.MainActivity`.
- `adb shell pm clear app.locker.vault` is acceptable only for disposable test data, never for a user vault.
- Erase test data through Security → Danger zone when possible, or `pm clear` after verification.

Device checks used so far: onboarding/recovery code, trial vault, hidden vault, decoy, recovery shares, imports, QR transfer, redaction, diagnostics, biometric gate, lock/auto-lock, PDF kit.

## Architecture map

```
app/src/main/java/app/locker/vault/
  LockerApp.kt              AppContainer, VaultSpace (main/decoy/hidden), lifecycle, seed data
  MainActivity.kt           single activity + screenshot protection
  BiometricAuthActivity.kt  transparent FragmentActivity for BiometricPrompt
  core/
    Crypto.kt               Argon2id, AES-256-GCM, hashing, base64
    SessionManager.kt       lock/unlock, recovery, duress, biometric, password change
    KeyMaterialStore.kt     wrapped master key, recovery copy, KDF params
    AppPreferences.kt       non-secret settings, trial/tamper flags
    RecoveryCodeFormat.kt   shared 5-group display/verification
    RecoveryShares.kt       Shamir 2-of-3 paper shares
    Shamir.kt               GF(256) secret sharing
    TamperGuard.kt          database fingerprint on clean lock
    TrialVault.kt           public default password constant
    TrialWindow.kt          7-day trial countdown (never destructive)
    CollectionLocks.kt      per-collection lock inheritance and filtering
    TransferCodec.kt        encrypted text-item QR payload
    RecoveryKitPdf.kt       one-page recovery kit
    CrashReporter.kt        app-private crash log
  data/
    VaultModels.kt          VaultItem, PasswordHistoryEntry, ItemHistoryEntry
    VaultRepository.kt      encrypted CRUD, history, tags, bulk actions
    VaultDb.kt              minimal SQLite schema (ids/types/timestamps + ciphertext)
    EncryptedFileStore.kt   chunked file encryption, EXIF stripping, thumbnails
    PasswordImport.kt       CSV, Bitwarden JSON, KeePass XML, 1PUX, ZIP CSV
    TotpImport.kt           otpauth + Google Authenticator migration QR
    PasswordAudit.kt        weak/reused/old/expiry audit
    PasswordHistory.kt      newest 10 passwords
    ItemHistory.kt          newest 10 item snapshots
  ui/
    LockerRoot.kt           setup/recovery/biometric/lock/nav switching
    navigation/             routes and NavHost
    screens/                onboarding, home, editor, detail, viewers, settings
    components/             shared cards, fields, QR helpers, biometrics
```

### Vault spaces

`AppContainer` owns three independent `VaultSpace`s:

- `main` — real vault (`vault.db`, `vault/`).
- `decoy` — duress password opens it; harmless backdated example content; independent keys/db/files.
- `hidden` — optional travel-mode vault with its own password, recovery key, db/files.

Each space has its own `KeyMaterialStore`, `SessionManager`, `VaultRepository`, and `EncryptedFileStore`. `activeSpace` decides which one the UI reads. Switching locks the other spaces. Full wipe removes all three.

### Data/security invariants

- `VaultItem` is JSON encrypted with AES-256-GCM; AAD binds item id and type.
- File bodies use 128 KiB AES-GCM chunks and a random per-file key wrapped by the master key.
- Only ids, item types, timestamps, and ciphertext live in SQLite.
- The vault master key exists in memory only while unlocked and is zeroed on lock.
- Password/trial/duress/hidden verifier values are salted hashes, never plaintext passwords.
- Recovery code: 25 digits (24 + check digit), Argon2id-wrapped master key; legacy Base32 keys accepted.
- The trial vault password is public by design (`Qwerty1234567`); reminders must stay until a real password is set.

## Recently shipped

- 1.10.0 — trial sample content and first-run tour, trial countdown plus explicit trial delete, per-collection locks enforced across every listing surface, autofill no longer offers unmatched secrets.
- 1.9.0 — trial vault with default password, persistent trial reminders, one-tap conversion, generated onboarding password.
- 1.8.0 — hidden vault, Shamir 2-of-3 recovery shares, tamper warning, richer decoy, static encrypted QR transfer for text items.
- 1.6.0 — item templates, previous versions, per-item biometric gate, Bitwarden JSON/Dashlane/TOTP QR import, autofill matching, photo redaction, diagnostics.
- 1.5.0 — password history, expiry dashboard, tag manager, recovery-kit PDF, unit-test CI.

## Known limitations / deferred work

- No documents/media batch yet (scanner upgrades, PDF merge/split/compress/page redaction, OCR search inside PDFs, video export, voice-memo skip-silence). The old "1.7.0" label is retired; the batch is planned as 1.11.0.
- Per-collection locks are enforced in the UI layer, not cryptographically: items in a locked collection are filtered from lists, search, autofill, archive, trash, health, tags, and the widget until unlocked for the session. Anyone who can read the SQLite file still needs the master key, but the lock adds no second key.
- Autofill lacks inline suggestions and per-fill biometric; protected items and items in locked collections are excluded from autofill, and no dataset is offered when nothing matches.
- Hidden vault is a separate space, not a per-collection flag; items cannot be moved between spaces.
- QR transfer is static and text-item-only; animated/whole-vault transfer is not implemented.
- Tamper detection is best-effort and does not resist an attacker who can rewrite app-private preferences.
- Passkeys, SQLCipher full-DB encryption, Shamir shares for the raw vault key, tablet two-pane layout, and i18n are not implemented.
- Release APKs are unminified because of host memory limits.

## Working conventions

- Read existing code before adding abstractions; keep the current Material 3 card/chip visual language.
- Prefer small pure helpers plus unit tests for crypto, parsing, audit, history, and sharing logic.
- Do not log secrets, generated passwords, recovery keys, or file contents.
- Never add analytics, crash upload, cloud sync, or network permissions.
- Preserve backward compatibility of encrypted payloads; absent JSON fields must decode to safe defaults.
- Before pushing, grep the diff for personal data and secrets and confirm the noreply Git identity.
- After a release, leave the repo clean and the app at onboarding after erasing only disposable test data.

## Next work (needs user approval)

The user has a roadmap of larger items and expects an agent to **ask permission before starting**:

- Quick wins: README screenshots/GIFs, monochrome icon, Obtainium/signing docs, backup restore preview/diff.
- Product depth: autofill polish (inline suggestions, per-fill biometric), documents/media batch (1.11.0), WorkManager backup scheduling, decoy editor, tablet/accessibility.
- Big bets: passkeys, animated/whole-vault QR, hidden collection refinement, SQLCipher, vault-key Shamir shares.
