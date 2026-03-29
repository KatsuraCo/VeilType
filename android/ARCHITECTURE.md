# Android Architecture

Status: Pre-implementation
Date: 2026-03-27

## Goal

Build an Android-first Enigma Keyboard MVP with:
- local-only crypto
- remembered key profiles
- encrypted paste into any text field
- decrypt from clipboard

## Component map

### `ime/`
Responsibilities:
- `InputMethodService`
- keyboard state machine
- composing buffer
- mode switching
- encrypted paste into host editor

### `crypto/`
Responsibilities:
- Argon2id key derivation
- AES-256-GCM encrypt/decrypt
- `TL1` header builder/parser
- profile hint derivation

### `profiles/`
Responsibilities:
- create profile
- rotate profile
- profile lookup by app package
- shortlist by profile hint
- expiry state computation

### `clipboard/`
Responsibilities:
- explicit clipboard read
- message prefix validation
- decrypt request dispatch
- post-decrypt clipboard clear option

### `storage/`
Responsibilities:
- secure local persistence
- wrapped profile key storage
- metadata persistence
- settings persistence

### `ui/`
Responsibilities:
- preview panel
- chips for active profile and expiry
- profile switcher
- decrypt result panel
- onboarding and settings screens

## State model

Primary keyboard states:
- `Idle`
- `EnigmaComposing`
- `DecryptReady`
- `DecryptSuccess`
- `DecryptFailure`
- `ProfileExpired`

## Recommended implementation order

1. `crypto`
2. `profiles`
3. `storage`
4. `ime`
5. `clipboard`
6. `ui`

## Notes

- do not implement auto-decrypt from clipboard in background
- do not store plaintext message history
- exact-contact auto-detection is not an MVP feature
