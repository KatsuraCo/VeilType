from __future__ import annotations

import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parent.parent
VECTORS_PATH = ROOT / "vectors" / "tl1_aes256gcm_vectors.json"
SRC_PATH = ROOT / "src"
if str(SRC_PATH) not in sys.path:
    sys.path.insert(0, str(SRC_PATH))

from veiltype_core.profile import derive_profile_hint, derive_profile_key
from veiltype_core.tl1 import Tl1MessageCodec


class Tl1ReferenceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.codec = Tl1MessageCodec()
        self.vectors = json.loads(VECTORS_PATH.read_text(encoding="utf-8"))["vectors"]

    def test_vectors_match_key_derivation_and_hint(self) -> None:
        for vector in self.vectors:
            with self.subTest(vector_id=vector["vector_id"]):
                key = derive_profile_key(
                    vector["sequence"],
                    bytes.fromhex(vector["profile_salt_hex"]),
                )
                hint = derive_profile_hint(key)
                self.assertEqual(key.hex(), vector["derived_key_hex"])
                self.assertEqual(hint.hex(), vector["profile_hint_hex"])

    def test_vectors_decrypt(self) -> None:
        for vector in self.vectors:
            with self.subTest(vector_id=vector["vector_id"]):
                key = bytes.fromhex(vector["derived_key_hex"])
                plaintext = self.codec.decrypt(vector["message"], [key])
                expected = bytes.fromhex(vector["plaintext_utf8_hex"]).decode("utf-8")
                self.assertEqual(plaintext, expected)

    def test_encrypt_matches_vector_when_nonce_is_fixed(self) -> None:
        for vector in self.vectors:
            with self.subTest(vector_id=vector["vector_id"]):
                key = bytes.fromhex(vector["derived_key_hex"])
                hint = bytes.fromhex(vector["profile_hint_hex"])
                nonce = bytes.fromhex(vector["nonce_hex"])
                plaintext = bytes.fromhex(vector["plaintext_utf8_hex"]).decode("utf-8")
                payload = self.codec.encrypt(
                    plaintext=plaintext,
                    profile_key=key,
                    profile_hint=hint,
                    nonce_override=nonce,
                )
                self.assertEqual(payload.encoded_message, vector["message"])


if __name__ == "__main__":
    unittest.main()
