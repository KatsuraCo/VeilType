(function () {
    const storageKey = "veiltype-first-touch-attribution";
    const maxAgeMs = 30 * 24 * 60 * 60 * 1000;
    const allowedKeys = ["ref", "promo", "utm_source", "utm_medium", "utm_campaign", "utm_content"];
    const safeValue = /^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/;

    function readStored() {
        try {
            const parsed = JSON.parse(localStorage.getItem(storageKey) || "null");
            if (!parsed || parsed.expiresAt < Date.now()) {
                localStorage.removeItem(storageKey);
                return null;
            }
            return parsed.values;
        } catch (error) {
            localStorage.removeItem(storageKey);
            return null;
        }
    }

    function readIncoming() {
        const params = new URLSearchParams(window.location.search);
        const values = {};
        allowedKeys.forEach(function (key) {
            const value = (params.get(key) || "").trim();
            if (value && safeValue.test(value)) {
                values[key] = value;
            }
        });
        return values.ref || values.promo ? values : null;
    }

    function appendToUrl(rawUrl, values) {
        if (!values) {
            return rawUrl;
        }
        const url = new URL(rawUrl, window.location.href);
        allowedKeys.forEach(function (key) {
            if (values[key] && !url.searchParams.has(key)) {
                url.searchParams.set(key, values[key]);
            }
        });
        return url.toString();
    }

    let values = readStored();
    if (!values) {
        values = readIncoming();
        if (values) {
            localStorage.setItem(storageKey, JSON.stringify({
                values: values,
                expiresAt: Date.now() + maxAgeMs,
            }));
        }
    }

    document.querySelectorAll("[data-carry-attribution]").forEach(function (link) {
        link.href = appendToUrl(link.href, values);
    });

    document.querySelectorAll("[data-attribution-email]").forEach(function (link) {
        const source = values && (values.ref || values.promo);
        if (!source || !link.href.startsWith("mailto:")) {
            return;
        }
        const emailUrl = new URL(link.href);
        const body = emailUrl.searchParams.get("body") || "";
        emailUrl.searchParams.set("body", body + "\n\nReferral source: " + source);
        link.href = emailUrl.toString();
    });

    window.VEILTYPE_ATTRIBUTION = {
        current: values,
        appendToUrl: function (url) {
            return appendToUrl(url, values);
        },
    };
}());
