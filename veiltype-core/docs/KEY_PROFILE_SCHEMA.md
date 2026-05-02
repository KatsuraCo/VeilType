# Key Profile Schema

Status: Frozen for MVP Phase 0
Version: 1
Date: 2026-03-27

## 1. Purpose

A key profile represents one locally stored visual-key identity used by Enigma Keyboard for:
A key profile represents one locally stored secret-sequence identity used by Enigma Keyboard for:
- message encryption
- message decryption
- app-level profile selection
- expiry and rotation rules

## 2. Principles

- profile is local only
- no cloud sync in MVP
- no recovery
- expired profiles may remain decrypt-only
- one app may have multiple profiles, but explicit user choice is required if more than one is active
- secret sequence may be emoji-based or visual-card-based

## 3. Data model

Logical fields:
- `id`
- `title`
- `app_package`
- `peer_hint`
- `secret_sequence_kind`
- `profile_version`
- `profile_salt_b64`
- `wrapped_profile_key_b64`
- `profile_hint_b64`
- `created_at`
- `expires_at`
- `last_used_at`
- `status`
- `allow_decrypt_after_expiry`
- `rotation_period_hours`

## 4. Field meanings

### id
- UUID-like local identifier
- immutable

### title
- short user-facing label
- examples:
  - `Yasha TG`
  - `Son WA`
  - `Work Signal`

### app_package
- Android package name
- examples:
  - `org.telegram.messenger`
  - `com.whatsapp`
- may be null for a generic profile

### peer_hint
- manual free-form label
- not used for cryptography
- helps user distinguish conversations

### secret_sequence_kind
Allowed values:
- `emoji_sequence`
- `visual_sequence`

### profile_version
- integer
- starts at `1`
- incremented when profile is rotated in-place

### profile_salt_b64
- random 16-byte salt
- used as Argon2id salt

### wrapped_profile_key_b64
- encrypted or wrapped profile key stored locally
- raw visual sequence must not be persisted in plaintext in MVP

### profile_hint_b64
- 8-byte hint encoded as base64url
- derived from the profile key
- used only to shortlist candidate profiles for decryption

### created_at
- UTC timestamp

### expires_at
- UTC timestamp
- default lifetime is 48 hours from activation

### last_used_at
- UTC timestamp
- updated on successful encrypt or decrypt

### status
Allowed values:
- `active`
- `expiring`
- `expired`
- `archived`

### allow_decrypt_after_expiry
- boolean
- default `true`

### rotation_period_hours
- integer
- MVP default `48`

## 5. Storage policy

Must store:
- profile metadata
- wrapped profile key
- derived profile hint

Must not store in plaintext:
- raw selected card sequence
- decrypted messages
- clipboard history

## 6. Rotation rules

Default rule:
- encryption allowed for 48 hours
- warning begins 6 hours before expiry
- expired profile cannot encrypt
- expired profile can decrypt if `allow_decrypt_after_expiry = true`

Rotation modes:
- renew same visual sequence
- create new visual sequence
- archive old profile

## 7. Candidate selection for decryption

When decrypting `TL1` messages:
- read `profile_hint`
- shortlist local profiles with matching hint
- if none found, show failure
- if multiple matches found, try active first, then decrypt-capable expired profiles

## 8. Sample JSON object

```json
{
  "id": "3f6f7647-3f3c-4c8a-8f9d-3b7b0cbad8e4",
  "title": "Yasha TG",
  "app_package": "org.telegram.messenger",
  "peer_hint": "Yasha",
  "secret_sequence_kind": "emoji_sequence",
  "profile_version": 1,
  "profile_salt_b64": "u3xM5Ee0_8mAc4f8Q5Ex4A",
  "wrapped_profile_key_b64": "REDACTED",
  "profile_hint_b64": "gBzQv8M6M3E",
  "created_at": "2026-03-27T12:00:00Z",
  "expires_at": "2026-03-29T12:00:00Z",
  "last_used_at": "2026-03-27T12:13:00Z",
  "status": "active",
  "allow_decrypt_after_expiry": true,
  "rotation_period_hours": 48
}
```
