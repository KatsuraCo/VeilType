# Publish `veiltype-core`

## Goal

Publish `veiltype-core/` as a separate public GitHub repository that increases
trust for VeilType without exposing the full Android product shell.

## What to publish

Publish the entire contents of `veiltype-core/` as the root of the new repo.

That means:
- `README.md`
- `LICENSE`
- `pyproject.toml`
- `docs/`
- `schemas/`
- `vectors/`
- `scripts/`
- `src/`
- `tests/`

## Suggested repository name

- `veiltype-core`

## Suggested GitHub description

`Public protocol specs, vectors, and reference implementation for VeilType local-first encrypted messaging.`

## Suggested website field

- your temporary landing URL
- later replace with the final production domain

## Suggested topics

- `encryption`
- `android`
- `keyboard`
- `protocol`
- `aes-gcm`
- `argon2id`
- `open-core`
- `privacy`
- `reference-implementation`
- `test-vectors`

## Suggested first paragraph for the public README

Use this if you want a shorter public-facing opener:

`veiltype-core is the public technical layer behind VeilType. It contains the frozen message formats, key-profile schema, deterministic test vectors, and a small Python reference implementation for local-first encrypted chat workflows.`

## Suggested trust paragraph

`This repository is intentionally narrower than the full product. The public goal is auditability: anyone should be able to inspect the protocol, reproduce the published vectors, and verify the core cryptographic behavior without needing the closed Android IME shell.`

## Suggested Product Hunt / launch paragraph

`VeilType itself is an Android-first keyboard for encrypted text inside normal chat apps. This repository exists so technical users, reviewers, and AI agents can inspect the protocol and verify the launch claims.`

## Recommended GitHub files after publish

Optional but useful next files:
- `NOTICE`
- `SECURITY.md`
- `CONTRIBUTING.md`
- `CHANGELOG.md`

## Practical publish sequence

1. Create a new public GitHub repository named `veiltype-core`.
2. Upload the contents of this folder as the repository root.
3. Verify GitHub detects the Apache-2.0 license.
4. Add the suggested description and topics.
5. Put the public repo link on the landing page as a trust artifact.
6. Reference it in your Product Hunt maker comment.

## What not to publish into this repo

Do not add:
- Android signing files
- private release scripts
- payment or activation logic
- internal screenshots not intended for public use
- unrelated product experiments

