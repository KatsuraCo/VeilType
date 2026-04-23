# VeilType

VeilType is an Android-first keyboard that encrypts messages locally before they leave your chat app.

It lets a user:
- create or receive one 8-emoji shared key,
- type inside a custom keyboard,
- replace plaintext with `TL1` ciphertext in Telegram, WhatsApp, or another chat,
- decrypt copied `TL1` messages locally from the clipboard,
- send encrypted audio and video capsules as `TLA1` and `TLV1` files.

## Core trust model

- local-only encryption and decryption
- no server
- no account
- no cloud backup
- no recovery
- no master key
- losing the 8-emoji shared key means losing access

## Current product shape

This repo contains an Android app with:
- a custom IME keyboard,
- local shared-key storage,
- text encryption and clipboard decryption,
- audio capsules,
- video capsules,
- release APK output.

## Product story

The strongest story is still the simplest one:

1. Enable VeilType Keyboard.
2. Create or receive one 8-emoji shared key.
3. Open any chat and encrypt a message locally.

Audio and video capsules matter, but they are secondary to the text flow.

## Repository layout

- `android/` Android application and keyboard implementation
- `docs/` product, launch, and technical documentation
- `shared/` protocol notes and test vectors
- `veiltype-core/` public open-core package with specs, vectors, and Python reference implementation
- `scripts/` local tooling
- `windows/` future desktop helper work

## Launch materials

Product Hunt and launch docs live in:
- `docs/PRODUCT_HUNT_PACKAGE.md`
- `docs/PRODUCT_HUNT_LAUNCH_PACKAGE_FINAL.md`
- `docs/PRODUCT_HUNT_READINESS.md`

## Open-core materials

Prepared public open-core package:
- `veiltype-core/README.md`
- `veiltype-core/LICENSE`
- `veiltype-core/PUBLISH_PUBLIC_REPO.md`
- `veiltype-core/docs/`
- `veiltype-core/vectors/`
- `veiltype-core/src/veiltype_core/`

## Release artifact

Latest release APK output:
- `android/app/build/outputs/apk/release/app-release.apk`
