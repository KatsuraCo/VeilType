# VeilType Trust Model

VeilType is a local-only Android keyboard for encrypted text in normal chat apps.

## Core claims

- Encryption happens locally on the Android device.
- 8-emoji shared keys stay on-device.
- The main launch story is text encryption in existing chat apps.
- Audio and video capsules exist, but they are secondary features.

## Explicit non-claims

- No server-side encryption or decryption.
- No account system.
- No cloud backup requirement.
- No recovery flow.
- No master key.
- No protection for a phone that is already compromised.

## User requirements

- Both people need VeilType.
- Both people need the same 8-emoji shared key.
- If the key is lost, access is lost.

## Open-core direction

The most trustworthy open-core boundary for VeilType is not the whole Android app.

The first public surface should be:
- protocol specifications,
- key profile schema,
- test vectors,
- minimal reference codec behavior,
- human-readable trust model and non-claims.

That lets third parties and AI agents verify the cryptographic story without requiring the full product shell to be open.
