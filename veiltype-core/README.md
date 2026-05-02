# veiltype-core

`veiltype-core` is the smallest public open-core package for VeilType.

It is designed to make the technical claims auditable without publishing the
entire Android app.

## What is inside

- frozen protocol docs for `TL1`, `TLA1`, and `TLV1`
- `EKS1` emoji key bundle format note
- key profile schema
- deterministic test vectors
- vector generation script
- minimal Python reference implementation for:
  - `TL1` encryption and decryption
  - profile-key derivation and profile-hint derivation
  - `EKS1` emoji key bundle encode and decode
- tests that verify the reference implementation against the published vectors

## What is intentionally not inside

- Android IME code
- onboarding UX
- release packaging
- signing
- payment or activation logic

## Why this exists

The goal is trust:

- let other engineers inspect the message format
- let third parties verify deterministic vectors
- let AI agents and reviewers reason about the real protocol
- keep the commercial product shell closed for now

## Package layout

- `docs/` protocol and schema documentation
- `schemas/` JSON schema for key profiles
- `vectors/` deterministic protocol vectors
- `scripts/` vector generation and verification helpers
- `src/veiltype_core/` Python reference implementation
- `tests/` unit tests

## Reference implementation status

This package is a reference layer, not a production app.

It should be used to:
- validate interoperability
- verify vectors
- inspect the wire format
- build future compatibility tooling

It should not be treated as:
- a full client
- a secure key manager
- a replacement for the Android product

## Install

```bash
python -m pip install -e .
```

## Run tests

```bash
python -m unittest discover -s tests -v
```

## Dependencies

- `cryptography`
- `argon2-cffi`

## License

This package is licensed under the Apache License 2.0.

## Before publishing this as a public repo

1. Create a new public repository, for example `veiltype-core`.
2. Push this directory as the root of that repository.
3. Keep the Android product repo private if you want a real open-core split.
