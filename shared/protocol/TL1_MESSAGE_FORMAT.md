# TL1 Message Format

Status: Frozen for MVP Phase 0
Version: 1
Date: 2026-03-27

## 1. Purpose

`TL1` is the first wire format for Enigma Keyboard encrypted chat messages.

Design goals:
- safe to copy and paste through ordinary chat apps
- ASCII-safe
- versioned
- authenticated
- compact enough for short chat messages

## 2. Top-level string format

User-visible message format:

`TL1:<base64url-no-padding(payload)>`

Requirements:
- prefix is always uppercase
- no whitespace inside encoded payload
- base64url alphabet only
- no trailing `=`

## 3. Cryptographic decisions for MVP

Frozen choices for MVP:
- key derivation: `Argon2id`
- encryption: `AES-256-GCM`
- message nonce size: `12 bytes`
- authentication tag size: `16 bytes`
- profile hint size: `8 bytes`

Rationale:
- available in mature Android libraries
- straightforward to test and validate
- simple to implement on Windows phase 2 as well

## 4. Payload layout

Binary payload layout before base64url encoding:

1. `version` - 1 byte
2. `algorithm_id` - 1 byte
3. `flags` - 1 byte
4. `reserved` - 1 byte
5. `profile_hint` - 8 bytes
6. `nonce` - 12 bytes
7. `ciphertext` - variable length
8. `tag` - 16 bytes

Minimum payload size:
- 40 bytes if plaintext is empty

## 5. Field definitions

### 5.1 version

For MVP:
- `0x01`

### 5.2 algorithm_id

Values:
- `0x01` = `AES-256-GCM`

Reserved for future:
- `0x02` = `XChaCha20-Poly1305`

### 5.3 flags

Bit layout:
- bit 0: `clipboard_safe`
- bit 1: `profile_required`
- bit 2-7: reserved

For MVP default:
- `0x03`

### 5.4 reserved

Always:
- `0x00`

### 5.5 profile_hint

Fixed 8-byte identifier used to narrow down local profile candidates.

Computation:
- `profile_hint = SHA-256("TLKH1" || derived_profile_key)[0:8]`

Notes:
- profile hint is not a key
- profile hint must not be reversible into the original visual sequence
- collisions are acceptable at low probability and only affect local candidate selection

### 5.6 nonce

Random 12-byte nonce generated per message.

Rules:
- never reuse nonce with the same derived profile key
- must come from secure random source

### 5.7 ciphertext

UTF-8 plaintext encrypted with AES-256-GCM.

No compression in MVP.

### 5.8 tag

16-byte authentication tag produced by AES-256-GCM.

## 6. AAD construction

Additional authenticated data is the 24-byte header:

- `version`
- `algorithm_id`
- `flags`
- `reserved`
- `profile_hint`
- `nonce`

Meaning:
- the unencrypted header is authenticated
- tampering with version, flags, hint, or nonce must invalidate the message

## 7. Plaintext encoding

Plaintext rules:
- UTF-8 only
- no binary attachments in MVP
- sender text must be normalized as entered
- empty plaintext is invalid for UI, though technically encryptable

## 8. Encryption procedure

1. User enters plaintext.
2. App derives profile key from selected key profile.
3. App computes `profile_hint`.
4. App generates random 12-byte nonce.
5. App builds 24-byte header.
6. App encrypts plaintext using `AES-256-GCM` with:
   - key = derived profile key
   - nonce = generated nonce
   - AAD = 24-byte header
7. App concatenates:
   - header
   - ciphertext
   - tag
8. App encodes full payload as base64url without padding.
9. App prefixes result with `TL1:`.

## 9. Decryption procedure

1. User copies a candidate message.
2. App checks prefix `TL1:`.
3. App base64url-decodes payload.
4. App reads header fields.
5. App uses `profile_hint` to shortlist candidate profiles.
6. For each candidate profile:
   - attempt AES-256-GCM decrypt
   - AAD must be the exact 24-byte header
7. If decrypt succeeds, plaintext is shown in preview.
8. If all candidates fail, app reports `Wrong key or invalid message`.

## 10. Message limits

MVP limits:
- soft limit: 500 UTF-8 bytes plaintext
- hard limit: 1500 UTF-8 bytes plaintext

Reason:
- encrypted messages expand in size
- chat usability degrades for large ciphertext blocks

## 11. Example structure

This section is structural only. Real vectors are stored in:
- `shared/test_vectors/tl1_aes256gcm_vectors.json`

Example:

```text
TL1:AX...<base64url>...
```

Decoded payload:

```text
[01][01][03][00][8-byte hint][12-byte nonce][ciphertext][16-byte tag]
```

## 12. Validation rules

Reject message if:
- prefix is not `TL1:`
- base64url decode fails
- payload length is below 40 bytes
- version is unsupported
- algorithm_id is unsupported
- tag validation fails

## 13. Compatibility policy

MVP compatibility policy:
- new clients must support `TL1`
- no backward compatibility guarantees yet beyond version 1
- future formats must use a different visible prefix if wire layout changes significantly

## 14. Related media capsule families

`TL1` is the text family.

Planned related families:
- `TLA1` for audio capsules
- `TLV1` for video capsules

These media families are not copy/paste strings like `TL1`.
They use file-based container formats and should be specified separately from the text wire format.
