# VeilType: Shared Key Setup Specification

Version: 0.2  
Date: 2026-04-04  
Status: Draft  
Owner: internal

## 1. Goal

Make first-run key setup simple enough for ordinary users without introducing:
- a server
- an account system
- cloud sync
- recovery paths

The system must let two people end up with the same 8-emoji shared key on both devices.

Core rule:
- the shared key exists only on the users' devices and in the explicit handoff they choose

## 2. Product decision

Chosen direction:
- one shared-key setup flow

This means:
- one person can generate an 8-emoji shared key locally
- the same key can be copied or shared through a separate trusted channel
- the other person imports and saves that exact key locally
- both sides then use it for `TL1`, `TLA1`, and `TLV1`

## 3. Security model

The setup flow must preserve the same trust model as the messaging flow:
- no server
- no account
- no cloud
- no recovery
- no hidden escrow

Private rules:
- the app must not silently transmit keys anywhere
- the app must not generate a cloud identity
- the app must not pretend lost keys can be restored later

## 4. Shared key representation

MVP representations:
- human-friendly ordered 8-emoji sequence
- machine-friendly encoded key package for copy/paste

Requirements:
- deterministic import result
- safe local storage after import
- no accidental mutation of the underlying key material

## 5. UX flows

### 5.1 Generate and share

Sender:
1. Opens key setup.
2. Generates a new 8-emoji shared key.
3. Saves it locally.
4. Shares it with the other person through a deliberate side channel.

Result:
- sender has a local saved key
- recipient receives the same secret outside the app's trust boundary

### 5.2 Import and save

Receiver:
1. Opens key setup.
2. Pastes or imports the 8-emoji shared key.
3. Reviews the key label or chat label.
4. Saves it locally.

Result:
- both users now have the same local key

### 5.3 Daily usage

After setup:
1. User opens any chat.
2. User selects VeilType Keyboard.
3. User encrypts with the saved shared key.
4. Recipient decrypts locally from the clipboard.

## 6. Data model

### 6.1 Local shared key record

Fields:
- `profile_id`
- `title`
- `app_package`
- `peer_hint`
- `secret_sequence_kind`
- `profile_salt`
- protected key material
- `created_at`
- `expires_at`
- `last_used_at`
- `status`

### 6.2 Status rules

Statuses:
- `active`
- `expiring`
- `expired`
- `archived`

Rules:
- expired profiles cannot encrypt new messages
- archived profiles are not used by default
- old profiles may still decrypt if retained locally

## 7. Import/export requirements

The flow must support:
- explicit export by user action
- explicit import by user action
- clipboard-friendly transport

The flow must not support:
- silent background exchange
- remote server lookup
- account-based device syncing

## 8. Android implementation guidance

Phase A:
- local shared-key generation
- local save
- copy/share action
- import from clipboard

Phase B:
- clearer receive-state UX
- stronger success/failure messaging
- better labeling of the active key in chat

Phase C:
- smoother first-run onboarding
- fewer chances to create the wrong key for the wrong chat

## 9. Product stance

This setup model is intentionally simple:
- one 8-emoji shared key
- both sides save it locally
- no third party in the trust path

It is stricter than mainstream messaging products, and that strictness is part of the product promise:
- no server
- no account
- no cloud
- no recovery
