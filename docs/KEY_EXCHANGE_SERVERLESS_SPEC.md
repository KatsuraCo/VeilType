# Enigma Keyboard: Serverless Key Exchange Specification

Version: 0.1
Date: 2026-03-28
Status: Draft
Owner: TrueLock / internal

## 1. Goal

Make contact setup simple enough for ordinary users without introducing a server into the trust model.

The system must allow two users to establish a shared chat profile using:
- QR code when they are nearby
- message bundle / text bundle through any chat as fallback
- manual secret entry only as a reserve path

The server must not participate in:
- key generation
- key storage
- shared secret derivation
- message decryption
- password recovery

Core rule:
- private keys never leave the device

## 2. Product decision

Chosen direction:
- **Option 1: fully serverless contact exchange**

This means:
- each device creates a local asymmetric key pair
- users exchange only public contact bundles
- both devices derive the same shared secret locally
- both users compare a short safety fingerprint out-of-band

## 3. Cryptographic model

### 3.1 Local identity keys

Each installation generates:
- `identity_private_key`
- `identity_public_key`

Recommended algorithm:
- `X25519` for key agreement

Optional future expansion:
- `Ed25519` for signatures if we later want signed bundles

### 3.2 Shared secret

For contact A and B:
- A computes `DH(A_private, B_public)`
- B computes `DH(B_private, A_public)`

Both sides derive the same secret and then run it through a KDF.

Recommended derivation:
- `HKDF-SHA256`

Derived outputs:
- profile encryption key
- profile fingerprint seed
- contact profile id seed

### 3.3 Fingerprint

After exchange the app shows a short human-verifiable fingerprint:
- 6 emoji
- or 12 hex chars
- or 4 short words

Recommended MVP:
- 6 emoji fingerprint

Users compare it:
- in person
- by phone call
- by another trusted channel

If fingerprints do not match:
- user must discard the contact setup

## 4. Contact bundle

### 4.1 Bundle contents

Minimum fields:
- protocol version
- app marker
- device id
- public identity key
- display name
- creation timestamp

Optional:
- short device label

### 4.2 Example logical payload

```json
{
  "v": 1,
  "app": "enigma_keyboard",
  "device_id": "dev_8f2a1b",
  "display_name": "Daniil",
  "created_at": "2026-03-28T12:00:00Z",
  "identity_public_key": "base64url..."
}
```

### 4.3 Encoding

Bundle transport format:
- compact JSON
- compressed later if needed
- encoded as `base64url`
- wrapped with prefix:

```text
EKC1:eyJ2IjoxLCJhcHAiOiJl...
```

Where:
- `EKC1` = Enigma Key Contact format v1

## 5. UX flows

### 5.1 Nearby flow: QR

Sender:
1. Opens `Contacts`
2. Taps `Show my QR`
3. App displays QR with contact bundle

Receiver:
1. Opens `Contacts`
2. Taps `Scan QR`
3. Scans sender QR
4. App imports sender public bundle
5. App derives shared contact secret locally
6. App shows contact preview
7. App displays safety fingerprint
8. User compares fingerprint with sender
9. User taps `Trust contact`

Result:
- chat profile created automatically

### 5.2 Remote flow: text bundle

Sender:
1. Opens `Contacts`
2. Taps `Share contact`
3. App copies or shares `EKC1:...`

Receiver:
1. Receives text bundle in any messenger
2. Copies it
3. In app taps `Import contact`
4. App reads bundle from clipboard
5. App derives shared contact secret locally
6. App shows safety fingerprint
7. Users compare fingerprint by another channel
8. Receiver trusts contact

### 5.3 Manual reserve flow

Only fallback:
- user creates shared profile from emoji sequence manually

This is not the primary UX after contact exchange is implemented.

## 6. Data model

### 6.1 Local identity

Fields:
- `device_id`
- `display_name`
- `identity_public_key`
- encrypted private key material in Android Keystore-backed storage
- `created_at`

### 6.2 Contact

Fields:
- `contact_id`
- `display_name`
- `device_label`
- `remote_public_key`
- `local_profile_id`
- `fingerprint`
- `verified_at`
- `created_at`
- `last_used_at`
- `status`

Statuses:
- `pending`
- `verified`
- `blocked`
- `archived`

### 6.3 Generated profile

After handshake the app creates a regular chat profile:
- title
- optional app package binding
- peer hint
- secret kind = `CONTACT_HANDSHAKE`
- derived profile key

This means the existing profile system remains the runtime layer.
The contact exchange only improves how profiles are created.

## 7. Security rules

### 7.1 Must

- private key generated locally only
- private key never exported in plaintext
- shared secret derived locally only
- no cloud recovery
- no server lookup as a trust source
- fingerprint must be shown before trust is finalized

### 7.2 Must not

- no sending the actual symmetric chat key directly
- no single global key reused for all contacts
- no hidden recovery key
- no automatic silent trust of imported contact bundles

## 8. Recommended Android implementation

### Phase A

- generate local identity pair on first launch
- create `Contacts` screen
- add `Show my QR`
- add `Import bundle from clipboard`
- derive shared secret and create profile

### Phase B

- add QR scan
- add safety fingerprint confirmation UI
- add contact list
- allow contact -> profile binding for app packages

### Phase C

- export/import signed contact card
- optional device rename
- contact revoke/archive flow

## 9. Required UI screens

MVP screens:
- `Contacts`
- `My Contact QR`
- `Import Contact`
- `Verify Fingerprint`
- `Contact Details`

## 10. Integration with current project

Current architecture already has:
- local profile storage
- per-app profile memory
- crypto pipeline for messages

This new feature should plug in as:

1. create local identity
2. exchange public contact bundles
3. derive shared profile secret
4. save normal profile to secure storage
5. use it from keyboard exactly like any other profile

So:
- keyboard logic changes only slightly
- most work is in setup UX and local contact storage

## 11. Why this is the right path

Compared to manual emoji secret exchange:
- fewer user mistakes
- no need to send the same secret directly
- much better onboarding
- still no server
- still strong trust model

Compared to a server-based directory:
- better privacy
- no central compromise point
- simpler security story

## 12. Immediate next step

Implement `Phase A`:
- local identity generation
- bundle export
- bundle import
- derived profile creation
- fingerprint confirmation
