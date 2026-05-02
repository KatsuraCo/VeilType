from __future__ import annotations

from dataclasses import dataclass
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .base64url import decode_no_padding, encode_no_padding


PREFIX = "TL1:"
VERSION = 0x01
ALGORITHM_ID_AES_256_GCM = 0x01
DEFAULT_FLAGS = 0x03
RESERVED = 0x00
PROFILE_HINT_BYTES = 8
NONCE_BYTES = 12
TAG_BYTES = 16
HEADER_BYTES = 24


@dataclass(frozen=True)
class Tl1Payload:
    version: int
    algorithm_id: int
    flags: int
    reserved: int
    profile_hint: bytes
    nonce: bytes
    ciphertext: bytes
    tag: bytes
    encoded_message: str

    @property
    def header(self) -> bytes:
        return bytes(
            [
                self.version,
                self.algorithm_id,
                self.flags,
                self.reserved,
            ]
        ) + self.profile_hint + self.nonce


class Tl1MessageCodec:
    def encrypt(
        self,
        plaintext: str,
        profile_key: bytes,
        profile_hint: bytes,
        nonce_override: bytes | None = None,
    ) -> Tl1Payload:
        if len(profile_key) != 32:
            raise ValueError("profile_key must be 32 bytes")
        if len(profile_hint) != PROFILE_HINT_BYTES:
            raise ValueError("profile_hint must be 8 bytes")
        if not plaintext:
            raise ValueError("plaintext must not be empty")

        nonce = nonce_override or os.urandom(NONCE_BYTES)
        if len(nonce) != NONCE_BYTES:
            raise ValueError("nonce must be 12 bytes")

        header = bytes(
            [
                VERSION,
                ALGORITHM_ID_AES_256_GCM,
                DEFAULT_FLAGS,
                RESERVED,
            ]
        ) + profile_hint + nonce

        encrypted = AESGCM(profile_key).encrypt(
            nonce,
            plaintext.encode("utf-8"),
            header,
        )
        ciphertext = encrypted[:-TAG_BYTES]
        tag = encrypted[-TAG_BYTES:]
        payload = header + ciphertext + tag
        return Tl1Payload(
            version=VERSION,
            algorithm_id=ALGORITHM_ID_AES_256_GCM,
            flags=DEFAULT_FLAGS,
            reserved=RESERVED,
            profile_hint=profile_hint,
            nonce=nonce,
            ciphertext=ciphertext,
            tag=tag,
            encoded_message=PREFIX + encode_no_padding(payload),
        )

    def decrypt(self, encoded_message: str, candidate_profile_keys: list[bytes]) -> str:
        payload = self.decode(encoded_message)
        cipher_bytes = payload.ciphertext + payload.tag
        for key in candidate_profile_keys:
            try:
                plaintext = AESGCM(key).decrypt(payload.nonce, cipher_bytes, payload.header)
                return plaintext.decode("utf-8")
            except Exception:
                continue
        raise ValueError("Wrong key or invalid message")

    def decode(self, encoded_message: str) -> Tl1Payload:
        if not encoded_message.startswith(PREFIX):
            raise ValueError("Message prefix not recognized")
        payload = decode_no_padding(encoded_message.removeprefix(PREFIX))
        if len(payload) < HEADER_BYTES + TAG_BYTES:
            raise ValueError("Payload too short")

        header = payload[:HEADER_BYTES]
        version = header[0]
        algorithm_id = header[1]
        flags = header[2]
        reserved = header[3]
        if version != VERSION:
            raise ValueError(f"Unsupported version: {version}")
        if algorithm_id != ALGORITHM_ID_AES_256_GCM:
            raise ValueError(f"Unsupported algorithm: {algorithm_id}")

        profile_hint = header[4:12]
        nonce = header[12:24]
        cipher_and_tag = payload[HEADER_BYTES:]
        ciphertext = cipher_and_tag[:-TAG_BYTES]
        tag = cipher_and_tag[-TAG_BYTES:]

        return Tl1Payload(
            version=version,
            algorithm_id=algorithm_id,
            flags=flags,
            reserved=reserved,
            profile_hint=profile_hint,
            nonce=nonce,
            ciphertext=ciphertext,
            tag=tag,
            encoded_message=encoded_message,
        )

    def extract_profile_hint(self, encoded_message: str) -> bytes:
        return self.decode(encoded_message).profile_hint

