# Changelog

All notable changes to Locker are recorded here. Versions follow the release tags on GitHub.

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
