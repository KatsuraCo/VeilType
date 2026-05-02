from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent.parent
SRC_PATH = ROOT / "src"
if str(SRC_PATH) not in sys.path:
    sys.path.insert(0, str(SRC_PATH))

from veiltype_core.emoji_bundle import EmojiKeyBundle, EmojiKeyBundleCodec


class EmojiKeyBundleCodecTests(unittest.TestCase):
    def test_round_trip(self) -> None:
        codec = EmojiKeyBundleCodec()
        bundle = EmojiKeyBundle(
            title="Chat A",
            emojis=["a", "b", "c", "d", "e", "f", "g", "h"],
            profile_salt=bytes.fromhex("00112233445566778899aabbccddeeff"),
            app_package="org.telegram.messenger",
            peer_hint="Alice",
        )
        encoded = codec.encode(bundle)
        decoded = codec.decode(encoded)
        self.assertEqual(decoded.title, "Chat A")
        self.assertEqual(decoded.emojis, ["a", "b", "c", "d", "e", "f", "g", "h"])
        self.assertEqual(decoded.profile_salt, bytes.fromhex("00112233445566778899aabbccddeeff"))
        self.assertEqual(decoded.app_package, "org.telegram.messenger")
        self.assertEqual(decoded.peer_hint, "Alice")

    def test_rejects_non_16_byte_salt(self) -> None:
        codec = EmojiKeyBundleCodec()
        encoded = (
            "EKS1:"
            "eyJ2ZXJzaW9uIjoxLCJ0aXRsZSI6bnVsbCwiZW1vamlzIjpbImEiLCJiIiwiYyIsImQiLCJlIiwiZiIsImciLCJoIl0sInNhbHRfYjY0IjoiYVEiLCJhcHBfcGFja2FnZSI6bnVsbCwicGVlcl9oaW50IjpudWxsfQ"
        )
        with self.assertRaises(ValueError):
            codec.decode(encoded)


if __name__ == "__main__":
    unittest.main()
