# Changelog

All notable changes to Locker are recorded here. Versions follow the release tags on GitHub.

## Unreleased

- **Restore review** - after opening a `.locker` archive, the preview now lists the items that will be added, replaced, or removed instead of showing counts only. The full list is shown in a review dialog before Merge or Replace is committed; merge never removes current items.
- **Themed icon** - Android 13+ monochrome adaptive icon (`ic_launcher_monochrome`) so Locker matches the system themed-icon treatment.
- **Download verification** - Obtainium setup and a plain "verify your download" guide with the permanent signing certificate SHA-256, plus the same fingerprint in `SECURITY.md`.

## 1.11.0

- **PDF tools** — from the PDF viewer: extract a page range into a new document, merge two vault PDFs, re-compress, or redact a page. Redaction is destructive by design: the page is re-rendered with black boxes burned in and the document is rebuilt, so the covered text is gone rather than hidden. Results are saved as new encrypted items; originals are never modified.
- **PDF text search** — imported PDFs are indexed on-device, and any PDF that was not (older imports, image-only scans) now has a **Make searchable** action in the viewer. Recognised text lives inside the encrypted item payload, so global search finds documents by their contents.
- **Skip silence in voice memos** — an optional toggle pauses the recorder during sustained quiet and resumes when you speak again, using separate pause/resume thresholds so breaths do not cause chattering.
- **Save a video's audio track** — videos gain an **Audio** action that lifts the audio stream into an M4A and saves it as a new vault item, without re-encoding.
- PDF merge/split/compress output is rasterised, so the result is larger and not selectable; this is the trade-off for redaction actually removing text.

## 1.10.0

- **Trial vault starter content** — a trial vault now opens with six clearly tagged `Sample` items (a welcome note, an example login, a Wi-Fi entry, two real-world things, and a note about collections) so the first screen is not empty.
- **First-run tour** — three short coach marks explain local-only encryption, the sample tag, and how to keep or delete the trial. Dismissing it is remembered.
- **Trial countdown and explicit delete** — the trial banner and lock screen show the days left in a 7-day window. When it ends nothing is deleted: the vault keeps every item and asks you to set a password or delete it. **Delete trial** removes only that trial vault, behind a typed confirmation.
- **Per-collection locks** — mark any collection as *Require unlock*. Its contents are withheld from the home list, search, smart folders, autofill, the widget, archive, trash, password health, and the tag manager until you unlock it with biometrics or your vault password. Unlocking lasts for the session only and is cleared on lock; locked collections show a lock badge and can be locked again from the collection screen.
- **Autofill no longer suggests unrelated logins** — when nothing matched the current app or website, the service used to offer five arbitrary secrets. It now returns nothing instead, and never offers items from a locked collection.

## 1.9.0

- **Temporary trial vault** — a first-run shortcut creates a vault with the public default password `Qwerty1234567`, skipping password and recovery setup for a quick look.
- **Persistent trial reminders** — a home banner, lock-screen badge, drawer notice, and Security warning stay visible until a real password is set.
- **One-tap conversion** — changing the vault password clears the trial state and keeps every item.
- **Generated vault password** — normal onboarding can generate, reveal, and copy a strong password before creating the vault.

## 1.8.0

- **Recovery shares** — split the numeric recovery code into three paper shares with a 2-of-3 threshold; any two reconstruct it.
- **Tamper detection** — a fingerprint of the encrypted database is recorded on lock and checked on unlock. A replaced or rolled-back `vault.db` raises an in-app warning (best-effort detection, not cryptographic prevention).
- **Hidden vault (travel mode)** — a separate encrypted space with its own keys, database, files, recovery key, and password, opened by entering its password on the lock screen.
- **Decoy realism** — the decoy is seeded with a more lived-in set of backdated notes, logins, and things, and its password can be changed independently inside the decoy.
- **Encrypted QR transfer** — send a text item to another device as a passphrase-protected QR image, decoded fully offline.

## 1.6.0

- **Item templates** for logins, Wi-Fi, bank accounts, insurance policies, ID/passport, and notes.
- **Previous versions** for secrets and notes: the last 10 encrypted snapshots can be restored; the current version is saved first.
- **Per-item biometric gate**: require a fresh fingerprint before a protected item's contents, copies, or attachments are shown. Protected items are excluded from autofill.
- **Import expansion**: Bitwarden JSON, Dashlane/ZIP CSV exports, and TOTP QR images (single `otpauth://` links and Google Authenticator migration QR codes).
- **Autofill polish**: better field and domain/package matching, and saving an autofilled password updates the matching login instead of creating a duplicate.
- **Photo redaction**: draw black boxes over a photo and save the result as a new encrypted item; the original is untouched.
- **Local diagnostics**: app-private crash log with build details and a text export through the system picker.
- **Release script** and this changelog.

## 1.5.0

- **Password history** for the previous 10 passwords per login.
- **Expiry dates** with Active / Expires soon / Expired badges and a password-health dashboard.
- **Tag manager** to rename, merge, or delete tags without deleting items.
- **Printable recovery-kit PDF** generated fully on-device.
- Shared five-group recovery-code formatting and Group 2 + Group 4 verification.
- **Unit-test CI** on GitHub Actions.

## 1.4.1

- Fixed the 1.4.0 onboarding blocker: numeric recovery keys now use five 5-digit groups and verify Group 2 + Group 4.
- Verified the 1.4.0 feature set end-to-end: duress/decoy vault, Notes and Calendar disguises, voice memo, document scanner, video trim, smart folders, and the new shield-and-keypad icon.

## 1.4.0

- Voice memos, document scanning to PDF, video as a first-class type with trim-in-place, smart folders, a share target, Quick Settings lock tile, and screen-off auto-lock.
- Onboarding recovery-code review, duress/decoy vault, launcher disguises, and the blue shield-and-keypad icon.

## 1.3.1

- Hotfix release for the 1.3.0 scanner and recorder crash paths.
