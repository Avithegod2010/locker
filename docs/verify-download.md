# Install with Obtainium and verify your download

Locker is distributed as a signed APK through GitHub Releases. There is no
Play Store listing, no account, and no download server of our own: GitHub
serves the same file that CI built and signed.

## Get the APK

### From GitHub Releases

1. Open <https://github.com/Avithegod2010/locker/releases/latest>.
2. Download the asset named `Locker-<version>-release.apk`.
3. Every release note lists the APK's SHA-256. The signature fingerprint is
   below and stays the same for every release.

The release asset is built by the GitHub Actions release workflow on a
GitHub runner and signed with the project's permanent release key.
Pull-request builds and debug builds are **not** release artifacts.

### With Obtainium (automatic updates)

[Obtainium](https://github.com/ImranR98/Obtainium) is an open-source Android
installer that checks a source URL for new releases. It is a good fit for
Locker because it does not need an account and it keeps the APK signature
check that Android itself performs.

1. Install Obtainium and open **Add App**.
2. Paste the repository URL: `https://github.com/Avithegod2010/locker`
3. Choose **GitHub** as the source and let Obtainium read the releases.
4. Optional filters:
   - **APK filter:** `Locker-.*-release\.apk`
   - **Version filter:** `^v?\d+\.\d+\.\d+$`
   - Leave **Include prereleases** off unless you deliberately want beta
     builds.
5. Add the app. Obtainium shows the installed version and the available
   release; an update is downloaded from the same GitHub release asset.

Obtainium cannot make a malicious APK install over Locker: Android requires
every update to be signed by the same certificate. A package signed with a
different key fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, even if its
version number is higher.

## Verify your download

### Signing certificate

Locker uses one permanent signing certificate. Check it before installing an
APK you did not build yourself.

| Field | Value |
| --- | --- |
| Application ID | `app.locker.vault` |
| Certificate SHA-256 | `96fb04402dbea1109402e6755e1b1187f393d632f0835d4a790b0ad083420867` |
| Certificate SHA-256 (colon form) | `96:FB:04:40:2D:BE:A1:10:94:02:E6:75:5E:1B:11:87:F3:93:D6:32:F0:83:5D:4A:79:0B:0A:D0:83:42:08:67` |
| Distinguished name | `CN=Locker, OU=Locker, O=Local, L=Local, ST=Local, C=IN` |
| Valid until | 2054-03-23 |

The app is **never** signed with a debug key, and the certificate is never
rotated. If a download shows a different fingerprint, do not install it.

### Check the certificate with `apksigner`

`apksigner` ships with the Android SDK build-tools. The exact build-tools
version does not matter.

Windows (PowerShell):

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs .\Locker-1.11.0-release.apk
```

macOS / Linux:

```bash
apksigner verify --print-certs Locker-1.11.0-release.apk
```

Look for this line in the output:

```text
Signer #1 certificate SHA-256 digest: 96fb04402dbea1109402e6755e1b1187f393d632f0835d4a790b0ad083420867
```

A different digest means the APK was signed by someone else. Delete it and
download again from the official release page.

### Check the APK hash

Release notes include the SHA-256 of the APK itself. This catches a truncated
or modified download even when the signature is valid.

Windows (PowerShell):

```powershell
Get-FileHash .\Locker-1.11.0-release.apk -Algorithm SHA256
```

macOS / Linux:

```bash
shasum -a 256 Locker-1.11.0-release.apk
```

Compare the 64-character value with the one in the release notes. The APK
hash changes with every release; the signing certificate does not.

### If you already have Locker installed

Android performs the same signature check on every update. Installing an
official update over an older official build works. Installing an APK signed
by a different key fails before any app data is touched, so a failed update
does not delete your vault.

## What to do if a check fails

1. Do not open the APK and do not enter your vault password into it.
2. Delete the downloaded file.
3. Download again from
   <https://github.com/Avithegod2010/locker/releases/latest>.
4. If the official asset still has the wrong fingerprint, report it through
   the repository's private security advisory instead of a public issue.

The signing key and the vault encryption key are separate. Publishing the
certificate fingerprint above does not expose the private key or weaken the
vault.
