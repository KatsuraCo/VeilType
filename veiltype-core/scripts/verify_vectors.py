from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
VECTORS_PATH = ROOT / "vectors" / "tl1_aes256gcm_vectors.json"
SRC_PATH = ROOT / "src"
if str(SRC_PATH) not in sys.path:
    sys.path.insert(0, str(SRC_PATH))

from veiltype_core.profile import derive_profile_hint, derive_profile_key
from veiltype_core.tl1 import Tl1MessageCodec


def main() -> None:
    codec = Tl1MessageCodec()
    payload = json.loads(VECTORS_PATH.read_text(encoding="utf-8"))
    vectors = payload["vectors"]
    for vector in vectors:
        sequence = vector["sequence"]
        salt = bytes.fromhex(vector["profile_salt_hex"])
        expected_key = bytes.fromhex(vector["derived_key_hex"])
        expected_hint = bytes.fromhex(vector["profile_hint_hex"])
        key = derive_profile_key(sequence, salt)
        hint = derive_profile_hint(key)
        if key != expected_key:
            raise SystemExit(f"Key mismatch for {vector['vector_id']}")
        if hint != expected_hint:
            raise SystemExit(f"Hint mismatch for {vector['vector_id']}")
        plaintext = codec.decrypt(vector["message"], [key])
        expected_plaintext = bytes.fromhex(vector["plaintext_utf8_hex"]).decode("utf-8")
        if plaintext != expected_plaintext:
            raise SystemExit(f"Plaintext mismatch for {vector['vector_id']}")
    print(f"Verified {len(vectors)} TL1 vectors")


if __name__ == "__main__":
    main()
