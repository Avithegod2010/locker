# Security Policy

Locker is a local-only vault. It has no account system, no cloud service, no analytics, and no `INTERNET` permission. Security issues are handled through GitHub's private vulnerability reporting.

## Reporting

Please use **Security → Report a vulnerability** on this repository. Do not open a public issue for an exploitable flaw. Include the app version, Android version, reproduction steps, and impact.

## Design summary

- Argon2id password KDF (96 MiB, 4 passes, 4 lanes, random salt)
- AES-256-GCM for records, with AAD binding item IDs/types
- Per-file random keys and 128 KiB authenticated file chunks
- Recovery keys independently wrap the master key
- Backups are separately encrypted `.locker` archives with a whole-archive hash
- Android Keystore-backed biometric key with authentication required
- No network permission; Android backup and device transfer disabled
- Screenshot protection, screen-recording detection, external-display locking, secure clipboard, and auto-lock

## What is out of scope

- Physical access to an unlocked device
- Malicious system software, root, or a compromised OS
- Forgotten password **and** recovery key (the vault is intentionally unrecoverable)
- Denial of service against the local app

## Release signing

Every published APK is signed with the same long-lived certificate. Verify it
before installing a build you did not make:

- Application ID: `app.locker.vault`
- Certificate SHA-256: `96fb04402dbea1109402e6755e1b1187f393d632f0835d4a790b0ad083420867`
- Colon form: `96:FB:04:40:2D:BE:A1:10:94:02:E6:75:5E:1B:11:87:F3:93:D6:32:F0:83:5D:4A:79:0B:0A:D0:83:42:08:67`

See [docs/verify-download.md](docs/verify-download.md) for the `apksigner`
command and the Obtainium setup. A download with a different fingerprint
should not be installed.

## Personal data

The repository contains source code and documentation only. Signing keys, local SDK paths, user vaults, screenshots, and build artifacts are ignored and must never be committed.
