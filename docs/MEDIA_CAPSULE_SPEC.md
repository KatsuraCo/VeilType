# Media Capsule Spec

Status: Draft
Date: 2026-03-29

## 1. Why this exists

Audio and video do not fit the `TL1` copy/paste format well.

For media, Enigma should use file-based capsules:
- `TLA1` for audio
- `TLV1` for video

The goal is to keep the interaction simple:
- one button to record
- one button to preview
- one button to send
- one action to decrypt and play back

## 2. Product goals

- feel like a familiar voice note or video note
- preserve Enigma's local-only security model
- keep the encrypted container file-based instead of text-based
- allow sharing through ordinary messengers as attachments
- keep the decryption UX explicit and user-driven

## 3. User flows

### Audio

1. Tap `Voice`.
2. Record speech.
3. Stop and preview.
4. Enigma encrypts the recording into a `TLA1` capsule.
5. Share the capsule.
6. Recipient opens it and plays it after decrypt.

### Video

1. Tap `Video`.
2. Record a short clip.
3. Stop and preview.
4. Enigma encrypts the recording into a `TLV1` capsule.
5. Share the capsule.
6. Recipient opens it and watches it after decrypt.

## 4. UX constraints

- no server round-trip
- no hidden steganography in ordinary stickers
- no automatic background playback
- no plaintext media history by default
- no dependence on messenger-native voice/video internals

## 5. Container approach

Media capsules should be exported as files.

Recommended handling:
- capture media locally
- store it temporarily only while the user is editing or previewing
- encrypt the media into a capsule file
- share the capsule as an attachment
- decrypt to a temporary file only when the recipient opens it

## 6. Relationship to other formats

- `TL1` remains the text message format
- `TLA1` is the audio capsule family
- `TLV1` is the video capsule family

This keeps the system simple and predictable.

