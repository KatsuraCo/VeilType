# VeilType: Technical Specification

Version: 0.2  
Date: 2026-04-04  
Status: Draft for MVP alignment  
Owner: internal

## 1. Product concept

VeilType is a local-only encryption layer for ordinary chats.

The user types a message in any messenger, switches to the VeilType keyboard, and replaces plaintext with unreadable encrypted text before sending.

The recipient uses VeilType and the same 8-emoji shared key to decrypt locally. Decrypted text is shown only after the recipient explicitly copies the encrypted string from chat and taps the decrypt action.

The system must not depend on a server for:
- encryption
- decryption
- key storage
- account state
- cloud sync
- recovery

Core rule:
- loss of key = loss of access

## 2. Product goals

Goals:
- work on top of existing messengers
- require no proprietary chat backend
- keep encryption and decryption fully local
- support one 8-emoji shared key per conversation
- minimize setup friction without weakening the trust model
- make the first successful encrypted message possible in under one minute

Non-goals:
- building a new messenger
- cloud sync
- password recovery
- server-side key escrow
- AI transformation of message text
- hiding ciphertext inside ordinary-looking text

## 3. Target platforms

MVP:
- Android custom keyboard using `InputMethodService`

Phase 2:
- Windows desktop helper with local encrypt/decrypt and clipboard support

Out of scope for MVP:
- iOS feature parity
- macOS
- browser extensions
- server-backed identity or discovery

## 4. Main user scenarios

### 4.1 Encrypt and send
1. User opens any chat app.
2. User switches to VeilType Keyboard.
3. User selects the active shared key.
4. User types plain text.
5. User taps the encrypt action.
6. Plain text is replaced in the input field by a `TL1` encrypted string.
7. User sends the message through the host app normally.

### 4.2 Decrypt from clipboard
1. User sees a `TL1` string in chat.
2. User copies it to the clipboard.
3. User opens VeilType Keyboard.
4. User taps the decrypt action.
5. Keyboard reads the clipboard.
6. If the message is valid and the key matches, keyboard shows decrypted text in the preview panel.

### 4.3 Create shared key
1. User opens the key setup screen.
2. User generates a new 8-emoji shared key.
3. User saves it locally.
4. User shares the same key with the other person through a separate trusted channel.

### 4.4 Import shared key
1. User receives the 8-emoji shared key from the other person.
2. User pastes or imports it.
3. User saves it locally under a readable label.
4. Keyboard can now encrypt and decrypt for that chat.

## 5. UX requirements

### 5.1 Keyboard states

The keyboard must support three clear states:
- Normal
- Encrypt
- Decrypt

### Normal state
- standard text input
- clear utility actions for encrypt, decrypt, and key switch

### Encrypt state
- active shared key visible at all times
- preview panel shows the encrypted result
- one clear action to replace plaintext with ciphertext

### Decrypt state
- one explicit action to read the clipboard
- preview panel for decrypted plaintext
- no silent auto-decrypt behavior

### 5.2 Error UX

Errors must be short and explicit:
- `Clipboard is empty`
- `Message format not recognized`
- `Wrong key`
- `No active key`
- `Encryption failed`
- `Message too large`

### 5.3 Key-selection UX

Rules:
- user must always know which shared key is active
- app-level default key selection is allowed
- if multiple keys exist for one app, the user must be able to switch intentionally

## 6. Security model

Security requirements:
- all encryption and decryption is local
- no plaintext leaves the device through a VeilType-controlled service
- no server stores or processes keys
- no recovery path exists
- no global master key exists
- each message uses a unique nonce
- tampering must be detectable

Security assumptions:
- device OS is not fully compromised
- app storage is protected by OS facilities where available
- clipboard exposure remains a known risk and must be documented

Explicit product warning:
- if the user loses the shared key, encrypted messages cannot be recovered

## 7. Shared key system

### 7.1 Shared key modes

MVP supports:
- generated shared key package
- ordered 8-emoji sequence as the default human-friendly secret representation

The product story should lead with:
- one 8-emoji shared key
- both people save the same key locally

### 7.2 Key derivation

Input:
- canonical representation of the shared secret
- optional app-scoped salt
- profile-specific random salt

Derivation:
- normalize the secret into a canonical byte string
- derive a local key using `Argon2id`

Recommended MVP target:
- 150-400 ms derivation time on a mid-range Android device

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
- expired profiles cannot encrypt new messages
- expired profiles may decrypt old messages if not deleted

## 8. Message format

Encrypted messages must remain copy/paste-safe for chat apps.

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
- UTF-8 text

Ciphertext output:
- binary payload encoded with `base64url`

## 10. Length and message limits

MVP constraints:
- soft limit: 500 UTF-8 bytes of plaintext
- hard limit: 1500 UTF-8 bytes of plaintext

If the message exceeds the hard limit:
- keyboard must block encryption
- keyboard must show a clear error

## 11. Android architecture

Main modules:
- `ime/`
- `crypto/`
- `profiles/`
- `clipboard/`
- `storage/`
- `ui/`

Main Android components:
- `InputMethodService`
- main activity
- key/profile manager activity
- optional onboarding activity

## 12. Windows phase 2

Windows is not a true keyboard for MVP.

It is a local helper with:
- global hotkey
- floating panel
- local encrypt/decrypt
- encrypted paste into the active window

## 13. Local storage

Data stored locally:
- key profiles
- profile metadata
- app-package bindings
- settings
- local counters

Storage requirements:
- use OS-protected storage where available
- encrypt sensitive records at rest
- never store decrypted message history by default

## 14. Clipboard rules

Clipboard decrypt is core MVP behavior.

Rules:
- decrypt only on explicit user action
- never auto-read and auto-decrypt silently
- recognize valid encrypted format by prefix `TL1:`
- provide `clear clipboard` after successful decrypt

## 15. Screen/state specification

### 15.1 Onboarding
- welcome
- local-only trust statement
- no account
- no cloud
- no recovery
- create first shared key
- enable keyboard in Android settings

### 15.2 Keyboard idle state
- normal keyboard layout
- top utility actions

### 15.3 Keyboard encrypt state
- active key chip
- preview panel
- explicit encrypt action

### 15.4 Keyboard decrypt state
- clipboard status
- decrypt action
- result panel

### 15.5 Key management screen
- create key
- import key
- list saved keys
- renew
- archive
- delete

## 16. Telemetry and privacy

By default:
- no message content telemetry
- no key telemetry
- no clipboard content telemetry

Allowed only with opt-in:
- crash reports
- anonymous success/failure counts
- coarse performance timing buckets

## 17. Threats and limitations

Known limitations:
- if an attacker controls an unlocked device, security is reduced
- clipboard may leak to other software
- host chat apps still see ciphertext metadata, not plaintext
- screenshots can expose decrypted preview

Out-of-scope attacks for MVP:
- fully compromised OS
- hardware keyloggers
- malicious accessibility malware

## 18. Acceptance criteria for MVP

MVP is accepted when:
- user can create or import a shared key
- user can encrypt text from the keyboard in a normal text field
- encrypted text is inserted into chat input
- recipient can decrypt from the clipboard locally
- key expiry and renewal work
- no server is required for normal operation
- no plaintext is stored persistently by default

## 19. Implementation phases

### Phase 0
- finalize message format
- finalize derivation parameters
- create test vectors

### Phase 1
- Android crypto core
- local profile storage
- encryption/decryption library
- unit tests

### Phase 2
- Android keyboard MVP
- encrypt state
- decrypt from clipboard
- key switcher

### Phase 3
- storage review
- clipboard policy hardening
- error handling
- UX polish

### Phase 4
- Windows helper
- compatibility with Android key material

## 20. Future extension: media capsules

Post-MVP extension:
- `TLA1` audio capsules
- `TLV1` video capsules
- file-based encrypted containers

Interaction goal:
- one record button
- one preview state
- one explicit send action
- one explicit decrypt-and-play action

## 21. Final product stance

VeilType is not a convenience messaging platform.

It is a strict local encryption tool with immutable principles:
- no server
- no account
- no cloud
- no recovery
- no plaintext processing outside the device
- lost key means lost access
