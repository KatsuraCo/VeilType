# VeilType Open Core

This directory publishes the privacy-critical message codec extracted from the
VeilType Android keyboard.

## Included

- `TL1:` encrypted message encoding and parsing.
- AES-256-GCM message encryption and authenticated decryption.
- Argon2id profile-key derivation used for local visual/emoji key profiles.
- Existing unit tests and a stable encoded-message test vector.

The Kotlin classes under `src/main` are taken from the Android application
crypto package. The tests under `src/test` are the matching application tests.

## Boundary

This open-core module does not contain the Android keyboard UI, capsule UX,
license or payment handling, release signing material, distribution logic, or
brand assets. VeilType message keys and plaintext remain local to the Android
application at runtime.

## Verify

Install Gradle, or use a Gradle wrapper from a local Android checkout:

```powershell
gradlew.bat -p .\veiltype-core test
```

## License

This directory is licensed under the Apache License, Version 2.0. Other files
in the VeilType repository are not relicensed unless their own notice says so.
