const supportedLanguages = ["en", "ru", "tr", "es", "pt", "it", "fr", "de"];
const languageSelect = document.getElementById("languageSelect");
const translatableNodes = document.querySelectorAll("[data-i18n]");
const metaDescription = document.getElementById("metaDescription");
const dictionaries = window.VEILTYPE_LOCALES || {};

function detectLanguage() {
    const stored = localStorage.getItem("veiltype-site-language");
    if (stored && supportedLanguages.includes(stored)) {
        return stored;
    }

    const browserLanguage = (navigator.language || "en").slice(0, 2).toLowerCase();
    return supportedLanguages.includes(browserLanguage) ? browserLanguage : "en";
}

function applyTranslations(language, dictionary) {
    translatableNodes.forEach((node) => {
        const key = node.dataset.i18n;
        if (dictionary[key]) {
            node.textContent = dictionary[key];
        }
    });

    document.documentElement.lang = language;
    document.title = dictionary.pageTitle;
    if (metaDescription) {
        metaDescription.setAttribute("content", dictionary.metaDescription);
    }
    if (languageSelect) {
        languageSelect.value = language;
    }
    localStorage.setItem("veiltype-site-language", language);
}

function switchLanguage(language) {
    const dictionary = dictionaries[language] || dictionaries.en;
    if (!dictionary) {
        return;
    }
    applyTranslations(language, dictionary);
}

if (languageSelect) {
    languageSelect.addEventListener("change", (event) => {
        switchLanguage(event.target.value);
    });
}

document.getElementById("footerYear").textContent = `\u00A9 ${new Date().getFullYear()} VeilType`;
switchLanguage(detectLanguage());
