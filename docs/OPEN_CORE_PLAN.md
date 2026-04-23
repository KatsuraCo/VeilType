# VeilType Open-Core Plan

## Goal

Increase trust without giving away the entire commercial product.

The open part should let another engineer verify:
- message and capsule formats,
- cryptographic choices,
- interoperability,
- test vectors,
- what the product explicitly does and does not claim.

If the public part does not include verifiable crypto and protocol behavior, it will not create meaningful trust.

## What should become public first

### 1. Protocol and format layer

Publish from `shared/`:
- `shared/protocol/TL1_MESSAGE_FORMAT.md`
- `shared/protocol/TLA1_TLV1_CAPSULE_FORMAT.md`
- `shared/profiles/key_profile.schema.json`
- `shared/profiles/KEY_PROFILE_SCHEMA.md`

Why:
- this is the smallest high-trust surface,
- it lets others reason about compatibility and message structure,
- it does not expose the whole product UX.

### 2. Test vectors

Publish:
- `shared/test_vectors/tl1_aes256gcm_vectors.json`
- `scripts/generate_test_vectors.py`

Why:
- this makes claims auditable,
- third parties can verify independent implementations,
- it signals seriousness better than marketing copy.

### 3. Public trust and threat model docs

Publish:
- local-only trust model,
- non-claims,
- key-loss behavior,
- device-compromise limitations,
- compatibility assumptions.

Why:
- honest boundaries increase trust more than inflated security language.

### 4. Minimal reference implementation

Open a small library, not the whole app:
- TL1 encode/decode
- key import/export codec
- optional TLA1/TLV1 container parse/verify helpers

Do not start by open-sourcing the entire Android app.
Start with the smallest auditable core.

## What should stay closed for now

Keep private:
- Android IME product shell and UI polish
- premium UX flows around onboarding and conversion
- release automation, signing, and packaging
- commercial licensing/payment integration
- private roadmap branches and experimental features

This is the actual "core open, product closed" boundary.

## Recommended repository split

### Public repo

Name suggestion:
- `veiltype-core`

Contents:
- protocol specs
- schemas
- test vectors
- trust model
- reference codec implementation
- interoperability tests

### Private repo

Keep in main private product repo:
- Android app
- keyboard UX
- launch assets
- release process
- payment and activation logic

## Recommended phase order

### Phase 1

Publish docs + vectors only.

Outcome:
- fastest trust gain,
- lowest commercial risk,
- easiest to launch before payment stack is done.

### Phase 2

Publish minimal reference library.

Outcome:
- stronger technical credibility,
- easier for reviewers and agents to inspect real behavior.

### Phase 3

Publish interoperability checklist and public verification instructions.

Outcome:
- others can validate behavior without needing your full app.

## What not to do

- Do not open-source only screenshots and marketing docs and call it open-core.
- Do not publish the full app first and decide the boundary later.
- Do not promise formal security guarantees you cannot independently verify.
- Do not mix release secrets, keystores, or internal ops into the public repo.

## Near-term concrete action

The best first public package is:
1. `shared/`
2. one `OPEN_CORE_README`
3. one `TRUST_MODEL`
4. one `REFERENCE_TESTS` folder

That is enough to create real trust before the payment system is live.

