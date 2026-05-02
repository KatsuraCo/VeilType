# TLA1 / TLV1 Capsule Format

Status: Draft for media capsule MVP
Version: 1
Date: 2026-03-29

## 1. Purpose

`TLA1` and `TLV1` are file-based encrypted media capsule families for Enigma Keyboard.

`TLA1` is for audio capsules.
`TLV1` is for video capsules.

Design goals:
- feel like a native voice note or video note flow
- keep encryption local only
- avoid fragile steganography inside decorative stickers
- support share-sheet based exchange as a file attachment
- allow deterministic decrypt inside the Enigma app

## 2. Core decision

Media capsules are not raw text messages.

They are encrypted files with a small authenticated header plus encrypted media bytes.

Recommended user-facing rule:
- text messages use `TL1`
- audio capsules use `TLA1`
- video capsules use `TLV1`

## 3. Container model

Each capsule is a binary file containing:
- file magic
- version
- capsule family
- media kind
- algorithm id
- flags
- profile hint
- nonce
- optional clear metadata
- encrypted media bytes
- authentication tag

Recommended file handling:
- create a temporary clear media file only while recording or decoding
- encrypt the full media blob before sharing
- decrypt back to a temporary file only when the user explicitly opens it

## 4. File-based approach

The MVP must not depend on encoding the entire media payload into a visible chat string.

Reason:
- audio and video are too large for copy/paste UX
- chat apps may truncate or wrap long strings
- file attachments survive better than clipboard payloads

Preferred distribution channels:
- share sheet
- attachment send flow
- local export/import
- optional clipboard token that references the capsule file, not the media bytes themselves

## 5. Suggested file layout

Minimal container structure:

1. `magic` - identifies Enigma capsule file
2. `version`
3. `family`
4. `media_kind`
5. `algorithm_id`
6. `flags`
7. `profile_hint`
8. `nonce`
9. `clear metadata length`
10. `clear metadata`
11. `ciphertext`
12. `tag`

Metadata may include:
- original filename
- mime type
- duration
- width and height for video
- sample rate for audio

## 6. Encryption model

The media capsule should reuse the same local profile system as `TL1`.

Recommended cryptography:
- `Argon2id` for profile derivation
- `AES-256-GCM` or `XChaCha20-Poly1305` for capsule encryption

Rules:
- one nonce per capsule
- tamper detection required
- no server involvement
- no plaintext media storage after final export, unless the user explicitly keeps a draft

## 7. UX model

### 7.1 Audio capsule

Flow:
1. User taps `Voice`.
2. User holds to record or taps record and stop.
3. App shows a preview with duration and playback controls.
4. App encrypts the recording into a `TLA1` capsule.
5. User shares the capsule as a file or attachment.
6. Recipient opens it in Enigma and decrypts locally.

Visual target:
- close to a voice-note experience
- one primary action button
- one stop/send action

### 7.2 Video capsule

Flow:
1. User taps `Video`.
2. User records a short front-facing clip or circle-style capture.
3. App shows a preview bubble with playback controls.
4. App encrypts the recording into a `TLV1` capsule.
5. User shares the capsule as a file or attachment.
6. Recipient opens it in Enigma and decrypts locally.

Visual target:
- close to Telegram or WhatsApp video-note behavior
- round or circular preview affordance
- minimal controls

## 8. Decrypt flow

Recipient flow:
1. Open capsule from share sheet, file picker, or clipboard token.
2. App resolves family from file header.
3. App shortlists candidate profiles by `profile_hint`.
4. App decrypts locally.
5. App shows preview or playback inside Enigma.

The app must never silently decrypt media in the background.

## 9. Compatibility notes

- `TLA1` and `TLV1` are family names, not chat-service-native object types
- media capsules must not rely on Telegram/WhatsApp internals
- file compression, transcoding, and thumbnailing must happen inside the app before encryption

## 10. MVP scope

For the first implementation:
- support one audio capsule format
- support one video capsule format
- keep share/import simple
- avoid sticker-pipeline dependence
- keep UI intentionally close to native voice/video note interaction

