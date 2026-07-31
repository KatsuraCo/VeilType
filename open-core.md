# VeilType Open Core

VeilType uses an open-core model where the auditable technical layer is public and the complete personal Android application is distributed free forever.

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

## Product shell

- Android IME shell
- Android UX and onboarding
- release automation and signing
- store publishing and device-specific integration

## Why this boundary

This creates a reviewable trust boundary while allowing the complete personal product to spread without payment, registration, or activation friction. Enterprise integration, SDK work, support, and strategic licensing remain separate from access to the free application.
