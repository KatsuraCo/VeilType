# Product Hunt Launch Package

## Product Name
VeilType

## Tagline Options
- Local-only encrypted typing for ordinary chats.
- Send encrypted messages without switching messengers.
- A keyboard that turns plain text into local-only ciphertext.
- Private messaging for the chat apps you already use.
- 8-emoji shared-key encryption built into an Android keyboard.
- Local-only text, voice, and video encryption for everyday chat.

## Short Description
VeilType is an Android keyboard that encrypts messages locally before you send them in any chat app. It also supports local clipboard decryption and encrypted audio/video capsules, with no server, no account, no cloud, and no recovery.

## Full Description
VeilType is a local-only encryption layer for everyday chat.

Instead of building a new messenger, VeilType works on top of the apps people already use. You type a message, encrypt it in the keyboard, and send ciphertext instead of plain text. The recipient can decrypt it locally if they have the same 8-emoji shared key.

The product is built around a strict trust model:
- no server for encryption or decryption
- no account system
- no cloud backup
- no recovery
- no plaintext history by default
- no global master key
- lost key means lost access

Beyond text, VeilType also supports file-based media capsules:
- `TL1` for encrypted text
- `TLA1` for audio capsules
- `TLV1` for video capsules

The result is a privacy tool that feels familiar in the chat UI while keeping cryptography and key handling on-device.

## Target Audience
- Privacy-conscious people who want encrypted messaging without switching apps
- Users who already chat in Telegram, WhatsApp, Signal, or similar messengers and want an extra private layer
- Journalists, activists, founders, operators, and community admins who share sensitive information in ordinary chats
- Android users who prefer local control over cloud-based recovery and sync
- Early adopters who like practical security tools with a clear trust model

## Launch Bullets
- Encrypt and send from an Android keyboard without a separate messenger
- Decrypt copied ciphertext locally from the clipboard
- Keep one 8-emoji shared key on-device with no server, no account, and no cloud
- Share encrypted audio and video capsules as files
- Keep the trust model simple: local-only, honest, and strict

## First Comment Draft
VeilType started from a simple idea: people should be able to send encrypted messages in the chat apps they already use, without moving to a new messenger or trusting a server with their keys.

The current release keeps the workflow intentionally direct:
- create or receive one 8-emoji shared key
- encrypt in the keyboard
- send ciphertext through any ordinary chat
- decrypt from the clipboard when needed

We also added encrypted media capsules, but text is still the main launch story. I would love feedback on the launch copy, the onboarding flow, and whether the first-run value feels obvious enough.

## FAQ

### What problem does VeilType solve?
It lets you send encrypted messages inside ordinary chat apps without building a new messenger or relying on a server.

### Does VeilType store my keys in the cloud?
No. Shared keys stay local. The product is designed around local-only encryption and decryption.

### Can the app recover my messages if I lose my key?
No. Recovery is intentionally not part of the model. If you lose the 8-emoji shared key, you lose access to messages encrypted with it.

### Do both people need the app?
Yes. Both people need VeilType-compatible setup and the same 8-emoji shared key.

### Does it work with existing messengers?
Yes. VeilType is meant to work inside ordinary chat apps by replacing plain text with ciphertext.

### What is `TL1`?
`TL1` is the text message format used for encrypted copy/paste-safe ciphertext.

### What are `TLA1` and `TLV1`?
`TLA1` is the audio capsule format and `TLV1` is the video capsule format.

### Is this only for Android?
The current launch package is Android-first. Windows helper support is planned later.

### How do people share keys?
They create or transfer one 8-emoji shared key through an explicit separate channel, then both save it locally.

### Is this a messenger replacement?
No. It is an encryption layer for existing chats.

## Screenshot Script

### Screen 1: Home / Overview
Show the main VeilType screen with the product title, trust strip, and one clear setup path.
Caption idea: "A local-only encryption keyboard for ordinary chat."

### Screen 2: Create key
Show key setup with title, app binding, and an 8-emoji shared key.
Caption idea: "Create one local 8-emoji shared key in a few taps."

### Screen 3: Keyboard Encrypt Mode
Show the keyboard inside a real chat with plaintext becoming ciphertext.
Caption idea: "Type normally, then send ciphertext instead of plaintext."

### Screen 4: Clipboard Decrypt
Show the decrypt state with copied `TL1:` ciphertext and a readable local preview.
Caption idea: "Decrypt copied messages locally from the clipboard."

### Screen 5: Media Capsules
Show audio or video capsule recording and playback.
Caption idea: "Text first, with the same local-only model for media."

## Launch Checklist
- Finalize the Product Hunt title and tagline.
- Prepare a clean hero screenshot that explains the product in one glance.
- Export 5 screenshots in the order above.
- Write a short launch post with one primary use case.
- Prepare a first comment that explains the trust model in plain language.
- Add a concise FAQ for recovery, storage, and device compatibility questions.
- Verify the product name, icon, and screenshots are visually consistent.
- Make sure the opening sentence says `Android-first` and `local-only`.
- Keep the launch narrative centered on text encryption first.
- Have one demo scenario ready for comments and follow-up questions.
