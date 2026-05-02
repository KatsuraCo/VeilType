from __future__ import annotations

from hashlib import sha256

from argon2.low_level import Type, hash_secret_raw


ARGON2_MEMORY_KIB = 19_456
ARGON2_ITERATIONS = 2
ARGON2_PARALLELISM = 1
KEY_LENGTH_BYTES = 32
PROFILE_HINT_BYTES = 8


def canonical_visual_sequence(card_ids: list[int]) -> str:
    if len(card_ids) != 5:
        raise ValueError("Exactly 8 card ids are required")
    return "-".join(f"{card:02d}" for card in card_ids)


def canonical_emoji_sequence(emojis: list[str]) -> str:
    if len(emojis) != 8:
        raise ValueError("Exactly 8 emoji tokens are required")
    out: list[str] = []
    for token in emojis:
        if not token or not token.strip():
            raise ValueError("Emoji token must not be blank")
        out.append(_code_points_hex(token))
    return "-".join(out)


def derive_profile_key(sequence: str, salt: bytes) -> bytes:
    return hash_secret_raw(
        secret=sequence.encode("utf-8"),
        salt=salt,
        time_cost=ARGON2_ITERATIONS,
        memory_cost=ARGON2_MEMORY_KIB,
        parallelism=ARGON2_PARALLELISM,
        hash_len=KEY_LENGTH_BYTES,
        type=Type.ID,
    )


def derive_profile_hint(profile_key: bytes) -> bytes:
    if len(profile_key) != KEY_LENGTH_BYTES:
        raise ValueError("profile_key must be 32 bytes")
    return sha256(b"TLKH1" + profile_key).digest()[:PROFILE_HINT_BYTES]


def _code_points_hex(value: str) -> str:
    return "+".join(f"{ord(char):x}" for char in value)
