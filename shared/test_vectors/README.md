# Test Vectors

This directory contains deterministic test vectors for the MVP protocol.

Current vector set:
- `tl1_aes256gcm_vectors.json`

Vector generation rules:
- fixed visual card sequence
- fixed Argon2id parameters
- fixed profile salt
- fixed nonce
- deterministic AES-256-GCM output

Purpose:
- verify Android implementation
- verify Windows implementation
- catch wire format regressions

Do not edit generated vector values by hand.

If format or crypto parameters change:
1. update protocol docs
2. update generator script
3. regenerate vectors
