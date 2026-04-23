# VeilType Implementation Plan

## Immediate next steps

1. Freeze the local-only product narrative around one 8-emoji shared key.
2. Finalize crypto test vectors and `TL1` behavior.
3. Keep Android implementation aligned with the public trust model.
4. Treat text encryption as the primary launch flow.
5. Keep media capsules as a secondary layer on the same local-only model.

## Phase 0 outputs

- final message format
- crypto test vectors
- final key profile schema
- explicit product language for no server / no account / no cloud / no recovery

## Phase 1 outputs

- Android crypto core
- local storage abstraction
- key manager
- encrypt/decrypt service
- unit tests

## Phase 2 outputs

- working Android keyboard
- encrypt action
- decrypt from clipboard
- active-key switcher
- expiry warnings

## Phase 3 outputs

- security review checklist
- onboarding polish
- consistent microcopy across launch surfaces
- first-run setup validation on fresh devices

## Phase 4 outputs

- simple 8-emoji shared-key creation flow
- simple 8-emoji shared-key import flow
- cleaner share and receive UX
- better active-key clarity per app

## Phase 5 outputs

- audio capsule recording and playback
- video capsule recording and playback
- file-based containers for `TLA1` and `TLV1`
- explicit share/import flow through files or attachments

## Future improvements

- image-style capsule mode for visual transport instead of raw `TL1` text
- richer previews for `TLA1` / `TLV1`
- better desktop compatibility with Android key material
- faster fresh-device onboarding with the same honest trust model
