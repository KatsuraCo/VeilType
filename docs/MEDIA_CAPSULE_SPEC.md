# VeilType Media Capsule Specification

Status: Draft  
Date: 2026-04-04

## 1. Why this exists

Audio and video do not fit the `TL1` copy/paste flow well.

For media, VeilType uses file-based capsules:
- `TLA1` for audio
- `TLV1` for video

The interaction goal is simple:
- one button to record
- one state to preview
- one explicit action to send
- one explicit action to decrypt and play

## 2. Product goals

- feel familiar enough for voice-note and short-video use
- preserve the same local-only trust model as text
- keep encrypted media file-based instead of text-based
- allow sharing through ordinary chat attachments
- keep playback and decryption explicit

## 3. User flows

### Audio
1. Tap `Voice`.
2. Record speech.
3. Stop and preview.
4. VeilType encrypts the recording into a `TLA1` capsule.
5. Share the capsule.
6. Recipient opens and plays it after local decrypt.

### Video
1. Tap `Video`.
2. Record a short clip.
3. Stop and preview.
4. VeilType encrypts the recording into a `TLV1` capsule.
5. Share the capsule.
6. Recipient opens and watches it after local decrypt.

## 4. UX constraints

- no server round-trip
- no hidden steganography
- no automatic background playback
- no plaintext media history by default
- no dependence on messenger-native voice/video internals

## 5. Container approach

Media capsules are exported as files.

Recommended handling:
- capture media locally
- store it temporarily only while editing or previewing
- encrypt the media into a capsule file
- share the capsule as an attachment
- decrypt to a temporary file only when the recipient opens it

## 6. Relationship to other formats

- `TL1` remains the text format
- `TLA1` is the audio capsule family
- `TLV1` is the video capsule family

This keeps the system predictable and consistent across media types.

## 7. Trust model reminder

Media capsules follow the same immutable promises as text:
- local-only
- no server
- no account
- no cloud
- no recovery
