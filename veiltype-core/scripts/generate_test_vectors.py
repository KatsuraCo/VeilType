import base64
import json
from hashlib import sha256
from pathlib import Path

from argon2.low_level import Type, hash_secret_raw
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


PROJECT_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_PATH = PROJECT_ROOT / "shared" / "test_vectors" / "tl1_aes256gcm_vectors.json"


def b64url_no_pad(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def derive_profile_key(sequence: str, salt: bytes) -> bytes:
    return hash_secret_raw(
        secret=sequence.encode("utf-8"),
        salt=salt,
        time_cost=2,
        memory_cost=19456,
        parallelism=1,
        hash_len=32,
        type=Type.ID,
    )


def profile_hint(key: bytes) -> bytes:
    return sha256(b"TLKH1" + key).digest()[:8]


def build_vector(vector_id: str, cards: list[int], salt_hex: str, nonce_hex: str, plaintext: str) -> dict:
    sequence = "-".join(f"{card:02d}" for card in cards)
    salt = bytes.fromhex(salt_hex)
    nonce = bytes.fromhex(nonce_hex)

    key = derive_profile_key(sequence, salt)
    hint = profile_hint(key)

    version = bytes([0x01])
    algorithm_id = bytes([0x01])
    flags = bytes([0x03])
    reserved = bytes([0x00])
    header = version + algorithm_id + flags + reserved + hint + nonce

    aesgcm = AESGCM(key)
    encrypted = aesgcm.encrypt(nonce, plaintext.encode("utf-8"), header)
    ciphertext = encrypted[:-16]
    tag = encrypted[-16:]
    payload = header + ciphertext + tag

    return {
        "vector_id": vector_id,
        "format": "TL1",
        "algorithm": "AES-256-GCM",
        "argon2id": {
            "time_cost": 2,
            "memory_cost_kib": 19456,
            "parallelism": 1,
            "hash_len": 32
        },
        "cards": cards,
        "sequence": sequence,
        "plaintext": plaintext,
        "plaintext_utf8_hex": plaintext.encode("utf-8").hex(),
        "profile_salt_hex": salt.hex(),
        "derived_key_hex": key.hex(),
        "profile_hint_hex": hint.hex(),
        "nonce_hex": nonce.hex(),
        "header_hex": header.hex(),
        "ciphertext_hex": ciphertext.hex(),
        "tag_hex": tag.hex(),
        "payload_hex": payload.hex(),
        "message": "TL1:" + b64url_no_pad(payload)
    }


def main() -> None:
    vectors = [
        build_vector(
            vector_id="tl1-001-ru-short",
            cards=[3, 17, 8, 24, 11],
            salt_hex="3a8f94d9b2d12055e6f4b6c39018cf41",
            nonce_hex="00112233445566778899aabb",
            plaintext="Завтра в 18:00"
        ),
        build_vector(
            vector_id="tl1-002-en-short",
            cards=[3, 17, 8, 24, 11],
            salt_hex="3a8f94d9b2d12055e6f4b6c39018cf41",
            nonce_hex="102132435465768798a9bacb",
            plaintext="Meet at the second entrance."
        ),
        build_vector(
            vector_id="tl1-003-ru-medium",
            cards=[14, 5, 22, 1, 9],
            salt_hex="6b4a125d0914f1d2aa39d3ea25c8bb17",
            nonce_hex="abcdef0123456789fedcba98",
            plaintext="Переходи на запасной канал и не пересылай это сообщение дальше."
        )
    ]

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        json.dump({"vectors": vectors}, f, ensure_ascii=False, indent=2)

    print(f"Generated {len(vectors)} vectors at {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
