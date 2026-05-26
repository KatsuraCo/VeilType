# VeilType Open Core

VeilType should use an open-core model where the auditable technical layer is public and the product shell stays closed.

## Public first

- protocol specifications
- message and capsule formats
- key profile schema
- test vectors
- minimal reference codec behavior
- trust model and explicit non-claims

## Published package

The repository contains the published public module at:

- `veiltype-core/`

That module includes:
- the Kotlin/JVM `TL1:` message codec extracted from the Android application
- AES-256-GCM encryption and authenticated decryption
- Argon2id profile-key derivation
- unit tests and a stable message vector
- Apache-2.0 license text

## Private for now

- Android IME shell
- premium UX and onboarding
- release automation and signing
- payment and activation flows

## Why this boundary

This creates real trust because engineers can verify how the format and crypto work, while the commercial product can still stay differentiated.
