from __future__ import annotations

import json
import re
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Iterable
from urllib.parse import quote

import requests
from bs4 import BeautifulSoup, Comment, NavigableString, Tag


ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "index.html"
LANGUAGES = {
    "en": ("English", "index.html"),
    "ru": ("Русский", "ru.html"),
    "es": ("Español", "es.html"),
    "pt": ("Português", "pt.html"),
    "de": ("Deutsch", "de.html"),
    "fr": ("Français", "fr.html"),
    "it": ("Italiano", "it.html"),
    "tr": ("Türkçe", "tr.html"),
}
GENERATED_LANGUAGES = ("es", "pt", "de", "fr", "it", "tr")
SKIP_PARENTS = {"script", "style", "code", "pre", "svg", "noscript"}
PROTECTED_TERMS = (
    "AES-256-GCM",
    "Product Hunt",
    "VeilType",
    "TrueLock",
    "WhatsApp",
    "Telegram",
    "GitHub",
    "Android",
    "Messenger",
    "messenger",
    "Signal",
    "INTERNET",
    ".veil",
    "APK",
    "SMS",
)
TRANSLATABLE_META = {
    "description",
    "og:title",
    "og:description",
    "twitter:title",
    "twitter:description",
}
EDITORIAL_OVERRIDES = {
    "es": {
        "h1": "Cifra antes de que la aplicación de mensajería reciba el texto sin cifrar.",
        "faq": "¿VeilType es una aplicación de mensajería?",
    },
    "pt": {
        "h1": "Criptografe antes que o aplicativo de mensagens receba o texto não criptografado.",
        "faq": "O VeilType é um aplicativo de mensagens?",
    },
    "de": {
        "h1": "Verschlüsseln Sie, bevor der Messenger Klartext erhält.",
        "faq": "Ist VeilType ein Messenger?",
    },
    "fr": {
        "h1": "Chiffrez avant que l’application de messagerie ne reçoive le texte en clair.",
        "faq": "VeilType est-il une application de messagerie ?",
    },
    "it": {
        "h1": "Crittografa prima che l’app di messaggistica riceva il testo in chiaro.",
        "faq": "VeilType è un’app di messaggistica?",
    },
    "tr": {
        "h1": "Mesajlaşma uygulaması düz metni almadan önce şifreleyin.",
        "faq": "VeilType bir mesajlaşma uygulaması mı?",
    },
}
CACHE_PATH = ROOT / "tools" / "translation_cache.json"
REQUEST_HEADERS = {"User-Agent": "VeilType localization builder/1.0"}


def protect_terms(text: str) -> tuple[str, dict[str, str]]:
    protected = text
    replacements: dict[str, str] = {}
    for index, term in enumerate(PROTECTED_TERMS):
        token = f"ZXQTERM{index}QXZ"
        if term in protected:
            protected = protected.replace(term, token)
            replacements[token] = term
    return protected, replacements


def restore_terms(text: str, replacements: dict[str, str]) -> str:
    restored = text
    for token, term in replacements.items():
        restored = re.sub(re.escape(token), term, restored, flags=re.IGNORECASE)
    return restored


def should_translate(text: str) -> bool:
    normalized = text.strip()
    if not normalized or len(normalized) < 2:
        return False
    if normalized in {"EN", "RU", "ES", "PT", "DE", "FR", "IT", "TR"}:
        return False
    return bool(re.search(r"[A-Za-z]", normalized))


def translate(text: str, language: str, cache: dict[str, str]) -> str:
    key = f"v2\u0000{language}\u0000{text}"
    if key in cache:
        return cache[key]

    protected, replacements = protect_terms(text)
    url = (
        "https://translate.googleapis.com/translate_a/single"
        f"?client=gtx&sl=en&tl={language}&dt=t&q={quote(protected)}"
    )
    last_error: Exception | None = None
    for attempt in range(5):
        try:
            response = requests.get(url, headers=REQUEST_HEADERS, timeout=30)
            response.raise_for_status()
            payload = response.json()
            translated = "".join(part[0] for part in payload[0] if part and part[0])
            translated = restore_terms(translated, replacements)
            cache[key] = translated
            time.sleep(0.06)
            return translated
        except (requests.RequestException, ValueError, TypeError, IndexError) as error:
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Translation failed for {language}: {text[:80]}") from last_error


def iter_text_nodes(soup: BeautifulSoup) -> Iterable[NavigableString]:
    for node in soup.find_all(string=True):
        if isinstance(node, Comment) or not isinstance(node, NavigableString):
            continue
        parent = node.parent
        if not isinstance(parent, Tag) or parent.name in SKIP_PARENTS:
            continue
        if parent.name in {"select", "option"} or parent.find_parent("select"):
            continue
        if should_translate(str(node)):
            yield node


def translate_text_node(node: NavigableString, language: str, cache: dict[str, str]) -> None:
    raw = str(node)
    leading = raw[: len(raw) - len(raw.lstrip())]
    trailing = raw[len(raw.rstrip()) :]
    core = raw.strip()
    node.replace_with(f"{leading}{translate(core, language, cache)}{trailing}")


def translate_attributes(soup: BeautifulSoup, language: str, cache: dict[str, str]) -> None:
    for node in soup.find_all(True):
        for attribute in ("aria-label", "title", "alt", "placeholder"):
            value = node.get(attribute)
            if isinstance(value, str) and should_translate(value):
                node[attribute] = translate(value, language, cache)

    for meta in soup.find_all("meta"):
        name = (meta.get("name") or meta.get("property") or "").lower()
        content = meta.get("content")
        if name in TRANSLATABLE_META and isinstance(content, str):
            meta["content"] = translate(content, language, cache)


def collect_attribute_texts(soup: BeautifulSoup) -> set[str]:
    values: set[str] = set()
    for node in soup.find_all(True):
        for attribute in ("aria-label", "title", "alt", "placeholder"):
            value = node.get(attribute)
            if isinstance(value, str) and should_translate(value):
                values.add(value)
    for meta in soup.find_all("meta"):
        name = (meta.get("name") or meta.get("property") or "").lower()
        content = meta.get("content")
        if name in TRANSLATABLE_META and isinstance(content, str):
            values.add(content)
    return values


def prefetch_translations(values: set[str], language: str, cache: dict[str, str]) -> None:
    missing = [value for value in values if f"v2\u0000{language}\u0000{value}" not in cache]
    with ThreadPoolExecutor(max_workers=10) as executor:
        list(executor.map(lambda value: translate(value, language, cache), missing))


def replace_language_navigation(soup: BeautifulSoup, current_language: str) -> None:
    old_switch = soup.select_one(".locale-switch") or soup.select_one("#localeSelect")
    if not old_switch:
        raise RuntimeError("Locale switch container is missing")

    select = soup.new_tag("select", id="localeSelect")
    select["class"] = "locale-select"
    select["aria-label"] = "Language"
    for code, (label, _) in LANGUAGES.items():
        option = soup.new_tag("option", value=code)
        option.string = label
        if code == current_language:
            option["selected"] = "selected"
        select.append(option)
    old_switch.replace_with(select)


def replace_language_runtime(soup: BeautifulSoup, current_language: str) -> None:
    for script in soup.find_all("script"):
        content = script.string or ""
        if (
            script.get("id") == "localeRuntime"
            or "veiltype-site-language-manual" in content
            or "var files = {" in content
        ):
            script.decompose()

    mapping = {code: file_name for code, (_, file_name) in LANGUAGES.items()}
    script = soup.new_tag("script")
    script["id"] = "localeRuntime"
    script.string = f"""
(function () {{
    var files = {json.dumps(mapping, ensure_ascii=False)};
    var current = {json.dumps(current_language)};
    var select = document.getElementById("localeSelect");
    var query = new URLSearchParams(window.location.search).get("lang");
    var stored = "";
    try {{ stored = localStorage.getItem("veiltype-site-language") || ""; }} catch (error) {{ stored = ""; }}

    if (query && files[query] && query !== current) {{
        window.location.replace("./" + files[query] + window.location.hash);
        return;
    }}
    if (current === "en" && !query && stored && files[stored] && stored !== "en") {{
        window.location.replace("./" + files[stored] + window.location.hash);
        return;
    }}
    if (current === "en" && !query && !stored) {{
        var detected = String(navigator.language || "en").slice(0, 2).toLowerCase();
        if (files[detected] && detected !== "en") {{
            window.location.replace("./" + files[detected] + window.location.hash);
            return;
        }}
    }}
    if (select) {{
        select.value = current;
        select.addEventListener("change", function () {{
            var next = select.value;
            try {{ localStorage.setItem("veiltype-site-language", next); }} catch (error) {{}}
            window.location.href = "./" + files[next] + window.location.search.replace(/([?&])lang=[^&]*&?/, "$1").replace(/[?&]$/, "") + window.location.hash;
        }});
    }}
}}());
"""
    soup.body.append(script)


def apply_editorial_overrides(soup: BeautifulSoup, language: str) -> None:
    for node in soup.find_all(string=True):
        if isinstance(node, NavigableString) and "messenger" in str(node):
            node.replace_with(re.sub(r"\bmessenger\b", "Messenger", str(node)))
    for node in soup.find_all(True):
        for attribute in ("aria-label", "title", "alt", "placeholder", "content"):
            value = node.get(attribute)
            if isinstance(value, str) and "messenger" in value:
                node[attribute] = re.sub(r"\bmessenger\b", "Messenger", value)

    overrides = EDITORIAL_OVERRIDES.get(language)
    if not overrides:
        return
    heading = soup.select_one("h1")
    first_faq = soup.select_one("#faq summary")
    if heading:
        heading.string = overrides["h1"]
    if first_faq:
        first_faq.string = overrides["faq"]


def update_hreflang(soup: BeautifulSoup, current_language: str) -> None:
    for link in soup.select('link[rel="alternate"][hreflang]'):
        link.decompose()
    canonical = soup.select_one('link[rel="canonical"]')
    if canonical:
        canonical["href"] = f"https://veiltype.tech/{LANGUAGES[current_language][1] if current_language != 'en' else ''}"
    anchor = canonical
    for code, (_, file_name) in LANGUAGES.items():
        link = soup.new_tag("link", rel="alternate", hreflang=code)
        link["href"] = f"https://veiltype.tech/{'' if code == 'en' else file_name}"
        if anchor:
            anchor.insert_after(link)
            anchor = link
        else:
            soup.head.append(link)
    default_link = soup.new_tag("link", rel="alternate", hreflang="x-default")
    default_link["href"] = "https://veiltype.tech/"
    if anchor:
        anchor.insert_after(default_link)


def build_page(source_html: str, language: str, cache: dict[str, str], translate_page: bool) -> str:
    soup = BeautifulSoup(source_html, "html.parser")
    soup.html["lang"] = language
    replace_language_navigation(soup, language)
    update_hreflang(soup, language)
    if translate_page:
        text_nodes = list(iter_text_nodes(soup))
        values = {str(node).strip() for node in text_nodes}
        values.update(collect_attribute_texts(soup))
        prefetch_translations(values, language, cache)
        for node in text_nodes:
            translate_text_node(node, language, cache)
        translate_attributes(soup, language, cache)
        apply_editorial_overrides(soup, language)
    replace_language_runtime(soup, language)
    return str(soup)


def main() -> None:
    cache = json.loads(CACHE_PATH.read_text("utf-8")) if CACHE_PATH.exists() else {}
    english_html = SOURCE.read_text("utf-8-sig")
    russian_html = (ROOT / "ru.html").read_text("utf-8-sig")

    SOURCE.write_text(build_page(english_html, "en", cache, False), "utf-8")
    (ROOT / "ru.html").write_text(build_page(russian_html, "ru", cache, False), "utf-8")
    for language in GENERATED_LANGUAGES:
        output = ROOT / LANGUAGES[language][1]
        output.write_text(build_page(english_html, language, cache, True), "utf-8")
        CACHE_PATH.write_text(json.dumps(cache, ensure_ascii=False, indent=2), "utf-8")
        print(f"Built {output.name}", flush=True)


if __name__ == "__main__":
    main()
