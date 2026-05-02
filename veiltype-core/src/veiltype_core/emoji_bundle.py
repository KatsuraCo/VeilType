from __future__ import annotations

from dataclasses import dataclass
import json

from .base64url import decode_no_padding, encode_no_padding


PREFIX = "EKS1:"


@dataclass(frozen=True)
class EmojiKeyBundle:
    title: str | None
    emojis: list[str]
    profile_salt: bytes
    app_package: str | None = None
    peer_hint: str | None = None


class EmojiKeyBundleCodec:
    def encode(self, bundle: EmojiKeyBundle) -> str:
        payload = {
            "version": 1,
            "title": _normalize_optional(bundle.title),
            "emojis": bundle.emojis,
            "salt_b64": encode_no_padding(bundle.profile_salt),
            "app_package": _normalize_optional(bundle.app_package),
            "peer_hint": _normalize_optional(bundle.peer_hint),
        }
        encoded = encode_no_padding(
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        )
        return PREFIX + encoded

    def decode(self, raw: str) -> EmojiKeyBundle:
        normalized = raw.strip()
        if not normalized.startswith(PREFIX):
            raise ValueError("invalid key bundle prefix")
        payload = json.loads(
            decode_no_padding(normalized.removeprefix(PREFIX)).decode("utf-8")
        )
        if payload.get("version", 0) != 1:
            raise ValueError("unsupported key bundle version")
        emojis = list(payload["emojis"])
        if len(emojis) != 8:
            raise ValueError("key bundle must contain exactly 8 emojis")
        profile_salt = decode_no_padding(payload["salt_b64"])
        if len(profile_salt) != 16:
            raise ValueError("key bundle salt must be 16 bytes")
        return EmojiKeyBundle(
            title=_normalize_optional(payload.get("title")),
            emojis=emojis,
            profile_salt=profile_salt,
            app_package=_normalize_optional(payload.get("app_package")),
            peer_hint=_normalize_optional(payload.get("peer_hint")),
        )


def _normalize_optional(value: str | None) -> str | None:
    if value is None:
        return None
    stripped = value.strip()
    return stripped or None
