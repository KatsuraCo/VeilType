# Enigma Keyboard

Android-first encrypted keyboard for ordinary chats.

## Scope

This project is separate from `encryptor77777` / TrueLock.

Purpose:
- build a dedicated Enigma-style keyboard product
- keep experiments isolated from the main TrueLock codebase
- support Android keyboard MVP first
- add Windows desktop helper later

Core principles:
- local-only encryption
- no server
- no recovery
- visual key profiles
- copied ciphertext can be decrypted from clipboard

## Project layout

- `docs/` product and technical documentation
- `android/` Android keyboard implementation
- `windows/` Windows helper implementation
- `shared/` protocol, crypto notes, test vectors, shared artifacts
- `scripts/` local tooling and generators

## Current status

Current phase:
- documentation and architecture definition

Next phase:
- finalize wire format
- define crypto test vectors
- prepare Android module structure
