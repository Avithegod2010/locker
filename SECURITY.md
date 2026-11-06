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

## Personal data

The repository contains source code and documentation only. Signing keys, local SDK paths, user vaults, screenshots, and build artifacts are ignored and must never be committed.
