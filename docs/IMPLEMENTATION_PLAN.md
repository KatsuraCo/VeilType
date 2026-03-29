# Implementation Plan

## Immediate next steps

1. Freeze protocol decisions.
2. Prepare crypto test vectors.
3. Define Android project structure.
4. Build profile storage and key derivation first.
5. Build keyboard UI only after crypto core is stable.

## Phase 0 outputs

- `shared/protocol/format.md`
- `shared/test_vectors/`
- final algorithm choice
- final profile schema

## Phase 1 outputs

- Android module skeleton
- local storage abstraction
- profile manager
- encrypt/decrypt service
- unit tests for known vectors

## Phase 2 outputs

- working Android keyboard
- `Enigma` mode
- `Decrypt clipboard`
- profile switcher
- expiry warnings

## Phase 3 outputs

- security review checklist
- polish and bug fixing
- user-facing warnings and onboarding

## Phase 4 outputs

- local identity key pair
- contact bundle export format (`EKC1`)
- import from clipboard / QR-ready bundle parser
- shared-secret derivation via public keys
- automatic profile creation from contact handshake
- safety fingerprint confirmation screen

## Phase 5 outputs

- audio capsule recording and playback
- video capsule recording and playback
- file-based capsule container for `TLA1` and `TLV1`
- explicit share/import flow through attachments or exported files
- preview UI that mimics voice note and video note interactions

## Future improvements

- `image capsule` mode: render encrypted payload as an image or sticker-like visual container instead of a raw `TL1:` string
- import/decrypt flow for image capsules via local image recognition or QR-style scanning
- avoid fragile steganography inside ordinary stickers for MVP; prefer explicit machine-readable visual payloads
- `TLA1` / `TLV1` can later be extended with richer previews, transcode options, and sticker-like visual wrappers without changing the encrypted file container
