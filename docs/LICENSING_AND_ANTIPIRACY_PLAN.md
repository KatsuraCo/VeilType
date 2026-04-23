# Licensing And Anti-Piracy Plan

## Reality first

You cannot make an Android APK impossible to pirate.

You can, however, make piracy meaningfully less convenient than paying:
- one official APK
- easy checkout
- instant claim flow
- signed license entitlements
- graceful offline use for real customers

The goal is not perfect prevention.
The goal is to reduce casual sharing and keep the paid path easier than piracy.

## Commercial model

Do not sell separate APK files.

Sell the same APK with different entitlements:
- `SOLO`: one paid claim
- `DUO`: two paid claims
- `GIFT`: one purchased claim that can be transferred to another user

This is the right fit for VeilType because the product already has a two-person
communication loop.

## Product-facing offers

- `Solo Founder`: `$3`
- `Duo Gift Pack`: `$5`

The duo pack should be framed as:
- keep one for yourself
- gift the second to the person you want to chat with

## Delivery model

One APK stays public or semi-public.

Payment should issue:
- a purchase record
- one or two claim links
- one signed entitlement per claimed device

That means the APK can spread, but paid use still depends on valid entitlements.

## Recommended technical architecture

### 1. Signed entitlements

The backend should issue a signed license payload, for example:

```json
{
  "license_id": "lic_123",
  "plan": "DUO",
  "claim_index": 1,
  "device_id": "dev_abc",
  "issued_at": "2026-04-05T10:00:00Z",
  "grace_until": "2026-04-19T10:00:00Z",
  "features": ["text", "audio", "video"]
}
```

Sign it with a server-side private key.
Verify it in the app with an embedded public key.

That gives:
- offline verification
- tamper resistance
- no secret key inside the APK

### 2. Claim links instead of raw serial keys

For `SOLO`:
- one claim link

For `DUO`:
- two claim links

For gifts:
- buyer sends one unused claim link to another person

Claim links are better than plain reusable keys because they can be:
- single-use
- revoked if leaked before claim
- attached to a plan and claim slot

### 3. Device binding with grace, not hard lock

After claim, bind the entitlement to a stable local device identifier.

Recommended behavior:
- allow offline use for `7-14` days
- refresh the entitlement when the device is online
- do not hard-brick immediately on network issues

This protects honest buyers from bad UX while still limiting unlimited sharing.

### 4. Soft enforcement

Avoid an instant full lock on launch.

Better enforcement sequence:
1. app works normally after first valid claim
2. offline grace continues for a limited period
3. expired or missing entitlement disables sending new encrypted messages
4. reading already-local content can stay available, if you want a softer model

This is much better than aggressive lockouts, especially for a privacy tool.

### 5. Server checks only for licensing, not for message content

The server should never see:
- plaintext
- user keys
- message bodies

The server only needs to manage:
- purchases
- claim states
- issued entitlements
- optional revocation list

That keeps the trust model consistent.

## What not to do

Do not rely on:
- a second APK for gifts
- plain serial keys baked into the app
- only client-side boolean checks
- constant online-only validation
- heavy obfuscation as the main strategy

Obfuscation can help a little, but it is not the core defense.

## Practical anti-piracy stack

The pragmatic stack is:
- one APK
- code shrinking / obfuscation in release
- signed entitlements
- single-use claim links
- device binding
- short offline grace window
- optional revocation of obviously leaked claims

## Why this works for VeilType

VeilType is a relationship product.
Its strongest paid loop is not "buy one APK".
It is "buy for yourself and the second person you want to message".

That is why `Duo Gift Pack` is strategically stronger than trying to sell or
protect a second binary.

## Suggested implementation phases

### Phase 1

- finalize offer names: `SOLO`, `DUO`
- keep one APK
- build checkout to issue claim links

### Phase 2

- add signed entitlement verification in the app
- add offline grace cache
- block new encryption when entitlement is missing or expired

### Phase 3

- add claim management UI
- allow viewing current plan and claim status
- add entitlement refresh and revoke handling

## Bottom line

You will not stop a determined pirate.

You can still win commercially if:
- paying is faster than piracy
- gifting is easy
- solo and duo pricing are clear
- the app trusts signed entitlements, not hidden APK secrets

