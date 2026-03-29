# Enigma Keyboard: Technical Specification

Version: 0.1
Date: 2026-03-27
Status: Draft for MVP implementation
Owner: TrueLock / internal

## 1. Product concept

Enigma Keyboard is a local-only encryption layer for ordinary chats.

The user types a message in any messenger, enables Enigma mode on the keyboard, and sends an unreadable encrypted string instead of plain text.

The recipient uses the same keyboard and the same secret sequence to decrypt the message locally. The decrypted text is shown in a preview panel above the keyboard after the user copies the encrypted string from the chat and taps the decrypt action.

The system must not depend on a server for encryption, decryption, key recovery, or message delivery.

Core rule:
- loss of key = loss of access to messages

## 2. Product goals

Goals:
- work on top of existing messengers
- require no proprietary chat backend
- encrypt and decrypt locally only
- use an ordered secret sequence instead of a typed password
- support fast daily usage with remembered per-profile keys
- minimize friction for sender and recipient

Non-goals:
- building a new messenger
- cloud sync
- password recovery
- server-side key escrow
- AI-based text transformation
- hiding meaning in ordinary-looking text

## 3. Target platforms

MVP:
- Android custom keyboard using `InputMethodService`

Phase 2:
- Windows desktop helper with hotkey, clipboard decrypt, and encrypted paste

Out of scope for MVP:
- iOS custom keyboard parity
- macOS
- browser extension
- sticker-like or image-based encrypted capsules
- full audio and video capsule parity

## 4. Main user scenarios

### 4.1 Encrypt and send
1. User opens any chat app.
2. User opens Enigma Keyboard.
3. User enables `Enigma` mode.
4. User selects an active key profile.
5. User types plain text.
6. Keyboard shows plaintext preview and encrypted output preview.
7. User taps `Encrypt & Paste`.
8. Plain text is replaced in the input field by an encrypted string.
9. User sends the message through the host app normally.

### 4.2 Decrypt from clipboard
1. User sees an encrypted string in chat.
2. User copies the string to clipboard.
3. User opens Enigma Keyboard.
4. User taps `Decrypt`.
5. Keyboard reads clipboard.
6. If the message format is valid and key matches, keyboard shows decrypted text in the preview panel.
7. User may copy plaintext to clipboard if needed.

### 4.3 Create key profile
1. User taps `Key`.
2. User creates a new profile.
3. User enters profile label, for example `Yasha TG`.
4. User selects an ordered secret sequence.
5. Keyboard derives a local master key from the sequence.
6. Profile is stored locally in protected storage.

### 4.4 Rotate key profile
1. Existing profile approaches 48-hour expiry.
2. Keyboard warns user in advance.
3. User chooses one of:
- confirm same secret sequence and renew profile
- create a new sequence
- archive profile and stop encrypting with it
4. Old profile remains available for decrypting older messages unless user deletes it.

## 5. UX requirements

### 5.1 Keyboard modes

The keyboard must support 3 modes:
- Normal
- Enigma
- Decrypt

### Normal mode
- standard text input
- quick buttons: `E`, `Decrypt`, `Key`

### Enigma mode
- visible badge: `Enigma ON`
- active profile label visible at all times
- expiry timer visible
- preview panel shows:
  - current plaintext
  - resulting encrypted string preview
- actions:
  - `Encrypt & Paste`
  - `Cancel`
  - `Switch Key`

### Decrypt mode
- button `Decrypt clipboard`
- preview panel for decrypted plaintext
- actions:
  - `Copy`
  - `Clear`
  - `Try another key`

### 5.2 Error UX

Errors must be short and explicit:
- `Clipboard is empty`
- `Message format not recognized`
- `Wrong key`
- `Profile expired`
- `Encryption failed`
- `Message too large`

### 5.3 Chat detection UX

The keyboard may reliably detect the host application package on Android, but must not claim universal automatic chat detection.

Rules:
- default profile selection may be per app package
- specific contact mapping is manual only
- if multiple profiles exist for one app, user must choose one explicitly

## 6. Security model

Security requirements:
- all encryption and decryption is local
- no plaintext leaves device through Enigma service
- no server stores or processes keys
- no recovery path exists
- no global master key exists
- each message uses a unique nonce
- tampering must be detectable

Security assumptions:
- device OS is not fully compromised
- keyboard app storage is protected by OS secure storage where available
- clipboard exposure is a known risk and must be documented

Explicit warning in product:
- if a user loses the key profile, encrypted messages cannot be recovered

## 7. Key system

### 7.1 Secret sequence modes

MVP supports two secret sequence modes:
- `emoji sequence`
- `visual sequence`

Primary MVP mode:
- `emoji sequence`

Fallback/extended mode:
- `visual sequence`

Both modes rely on strict ordering.

### 7.1.1 Emoji sequence

User selects:
- exactly 5 emoji tokens
- exact order matters

Reason:
- faster than card picking
- easier to remember
- fits keyboard UX better

### 7.1.2 Visual sequence

User selects:
- exactly 5 visual cards
- exact order matters

The app must not depend on arbitrary personal photos for key derivation in MVP.

### 7.2 Key derivation

Input:
- canonical string for an ordered secret sequence
- optional app-scoped salt
- profile-specific random salt

Derivation:
- normalize secret sequence into canonical byte string
- derive profile master key using `Argon2id`

Recommended parameters for MVP:
- memory cost tuned for mobile performance
- target derivation time: 150-400 ms on mid-range Android device

### 7.3 Key profiles

Each profile stores:
- `id`
- `title`
- `app_package`
- `peer_hint`
- `secret_sequence_kind`
- `profile_salt`
- protected key material or wrapped key
- `created_at`
- `expires_at`
- `last_used_at`
- `status`
- `allow_decrypt_after_expiry`

Statuses:
- `active`
- `expiring`
- `expired`
- `archived`

### 7.4 Key rotation

Rules:
- profile lifetime for encryption: 48 hours
- warning threshold: 6 hours before expiry
- expired profile cannot encrypt new messages
- expired profile may decrypt old messages if not deleted
- rotation creates a new profile version or renews existing one

## 8. Message format

Encrypted message must be copy/paste-safe for chat apps.

Required properties:
- ASCII-safe
- easy to detect in clipboard
- versioned
- integrity protected

Proposed format:

`TL1:<base64url_payload>`

Payload structure before encoding:
- version byte
- algorithm id
- profile hint hash
- nonce
- ciphertext
- authentication tag

Notes:
- `profile hint hash` must not reveal key directly
- it may help narrow down candidate profiles
- all fields must be authenticated

## 9. Cryptography

Recommended algorithm:
- `XChaCha20-Poly1305`

Fallback if library constraints require:
- `AES-256-GCM`

Requirements:
- random nonce per message
- authenticated encryption mandatory
- no deterministic encryption
- no custom crypto primitives

Plaintext input:
- UTF-8 encoded text

Ciphertext output:
- binary payload encoded with `base64url`

## 10. Length and message limits

The encrypted output will be longer than plaintext.

MVP constraints:
- soft limit: 500 UTF-8 bytes of plaintext
- hard limit: 1500 UTF-8 bytes of plaintext

If message exceeds hard limit:
- keyboard must block encryption and show a clear error

## 11. Android architecture

Main modules:
- `ime/` keyboard service
- `crypto/` encryption and key derivation
- `profiles/` profile management
- `clipboard/` decrypt flow
- `storage/` local secure persistence
- `ui/` keyboard panel states
- `settings/`
- `diagnostics/`

Main Android components:
- `InputMethodService`
- settings activity
- profile manager activity
- optional onboarding activity

## 12. Windows phase 2 architecture

Windows version is not a true keyboard in MVP.

It is a desktop helper:
- global hotkey
- small floating panel
- encrypt typed or pasted text
- paste encrypted string into active window
- decrypt clipboard into local preview panel

## 13. Local storage

Data to store locally:
- key profiles
- profile metadata
- app-package bindings
- user settings
- local audit counters

Storage requirements:
- use OS-protected storage where available
- encrypt sensitive records at rest
- never store raw visual sequence in plaintext
- never store decrypted message history by default

## 14. Clipboard rules

Clipboard decrypt is core MVP behavior.

Rules:
- decrypt only on explicit user action
- never auto-read and auto-decrypt silently
- recognize valid encrypted format by prefix `TL1:`
- provide `clear clipboard` option after successful decrypt

## 15. Screen/state specification

### 15.1 Onboarding
- welcome
- security warning
- no recovery statement
- create first profile
- enable keyboard in Android settings

### 15.2 Keyboard idle state
- normal keyboard layout
- top buttons: `E`, `Decrypt`, `Key`

### 15.3 Keyboard enigma state
- active profile chip
- expiry chip
- plaintext preview
- encrypted preview
- `Encrypt & Paste`
- `Cancel`

### 15.4 Keyboard decrypt state
- clipboard status
- `Decrypt clipboard`
- result panel
- `Copy`
- `Clear`

### 15.5 Profile management screen
- list profiles
- active / expired markers
- renew profile
- archive profile
- delete profile
- bind profile to app package

## 16. Telemetry and privacy

By default:
- no message content telemetry
- no key telemetry
- no clipboard content telemetry

Allowed anonymous telemetry only if user opts in:
- app crashes
- encryption success/failure counts
- performance timing buckets

## 17. Threats and limitations

Known limitations:
- if attacker controls unlocked device, security is reduced
- clipboard may leak if other software reads it
- host chat apps still see encrypted string metadata, not plaintext
- screenshots can expose decrypted preview
- Android app-package detection is reliable, exact-contact detection is not

Out-of-scope attacks for MVP:
- fully compromised OS
- hardware keylogger
- malicious accessibility malware

## 18. Acceptance criteria for MVP

MVP is accepted when:
- user can create a visual key profile
- user can encrypt text from keyboard in any normal text field
- encrypted string is inserted into chat input
- recipient can decrypt from clipboard locally
- profile expiry and renewal work
- no server is required for normal operation
- no plaintext is stored persistently by default

## 19. Implementation phases

### Phase 0: protocol and crypto validation
- finalize message format
- finalize key derivation parameters
- create test vectors

### Phase 1: Android crypto core
- profile storage
- encryption/decryption library
- unit tests

### Phase 2: Android keyboard MVP
- keyboard UI
- enigma mode
- decrypt from clipboard
- profile switcher

### Phase 3: hardening
- secure storage review
- clipboard policy
- error handling
- UX polish

### Phase 4: Windows helper
- desktop encrypt/decrypt panel
- hotkey paste
- profile compatibility with Android

## 20. Open questions

- final choice between `XChaCha20-Poly1305` and `AES-GCM` based on library maturity
- exact number of visual cards in bundled set
- whether to support multiple active profiles per app package in MVP or only one
- whether to allow manual plaintext copy after decrypt by default
- whether expiry should be fixed at 48 hours or configurable

## 20.1 Future extension: image capsule / sticker mode

Potential post-MVP extension:
- convert encrypted payload into a machine-readable image capsule instead of sending raw `TL1:` text
- support sticker-like visual containers that can be shared through ordinary messengers as images
- recipient flow: import image locally and decode payload inside Enigma

Preferred direction:
- explicit image capsule with QR-like or structured visual payload

Avoid for early versions:
- fragile steganography hidden inside ordinary decorative stickers
- dependence on messenger-native sticker pipelines, compression, or format-specific behavior

## 20.2 Future extension: audio and video capsules

Potential post-MVP extension:
- `TLA1` audio capsules for voice-note style messages
- `TLV1` video capsules for short circle-style clips
- file-based encrypted containers instead of raw text payloads

Preferred interaction:
- one record button
- one preview state
- one send action
- one explicit decrypt-and-play action on the recipient side

Important constraint:
- the UI may mimic Telegram or WhatsApp closely, but the implementation stays inside Enigma and does not hook into messenger-native voice/video internals

## 21. Final product stance

Enigma Keyboard is not a convenience chat utility.

It is a strict local encryption tool with the following immutable principles:
- no server
- no recovery
- no cloud
- no plaintext processing outside device
- lost key means lost access
