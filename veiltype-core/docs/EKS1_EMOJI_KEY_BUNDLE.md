# EKS1 Emoji Key Bundle

Status: Draft
Version: 1

## Purpose

`EKS1` is a compact text bundle for moving one emoji-based shared key setup
between devices or people.

It is not the message format itself.
It is a transport wrapper for profile bootstrap data.

## Top-level string format

```text
EKS1:<base64url-no-padding(json-utf8)>
```

Rules:
- uppercase prefix
- base64url without padding
- JSON encoded as UTF-8

## JSON payload fields

- `version` integer, currently `1`
- `title` optional string
- `emojis` array of exactly 8 emoji tokens
- `salt_b64` base64url-no-padding 16-byte salt
- `app_package` optional string
- `peer_hint` optional string

## Validation

Reject if:
- prefix is not `EKS1:`
- JSON cannot be decoded
- `version` is not `1`
- `emojis` does not contain exactly 8 entries
- decoded salt is not exactly 16 bytes

## Notes

- This bundle is for setup and import, not for encrypted chat traffic.
- The bundle can carry metadata like app package and peer hint.
- The salt is public bundle data and is used later in profile-key derivation.
