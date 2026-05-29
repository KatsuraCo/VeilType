(function () {
    const config = window.VEILTYPE_COMMERCE || {};
    const requiredSales = Number(config.friendReferralRequiredSales || 5);
    const storageKey = "veiltype-friend-invite-code";
    const safeCode = /^[a-z0-9_-]{4,64}$/i;

    function readCode() {
        try {
            const stored = localStorage.getItem(storageKey) || "";
            if (safeCode.test(stored)) {
                return stored;
            }
        } catch (error) {
            return "";
        }
        return "";
    }

    function createCode() {
        const chars = "abcdefghjkmnpqrstuvwxyz23456789";
        let suffix = "";
        if (window.crypto && window.crypto.getRandomValues) {
            const bytes = new Uint8Array(8);
            window.crypto.getRandomValues(bytes);
            for (let i = 0; i < bytes.length; i += 1) {
                suffix += chars[bytes[i] % chars.length];
            }
        } else {
            for (let i = 0; i < 8; i += 1) {
                suffix += chars[Math.floor(Math.random() * chars.length)];
            }
        }
        return "friend_" + suffix;
    }

    function currentCode() {
        let code = readCode();
        if (!code) {
            code = createCode();
            try {
                localStorage.setItem(storageKey, code);
            } catch (error) {
                // Ignore storage failures; the link still works for the current page.
            }
        }
        return code;
    }

    function inviteUrl(code) {
        const target = document.documentElement.lang === "ru" ? "founder-access-ru.html" : "founder-access.html";
        const url = new URL(target, window.location.href);
        url.searchParams.set("ref", code);
        url.searchParams.set("utm_source", "customer_referral");
        url.searchParams.set("utm_campaign", "invite_5");
        return url.toString();
    }

    function render() {
        const code = currentCode();
        const link = inviteUrl(code);
        document.querySelectorAll("[data-friend-required]").forEach(function (node) {
            node.textContent = String(requiredSales);
        });
        document.querySelectorAll("[data-friend-code]").forEach(function (node) {
            node.textContent = code;
        });
        document.querySelectorAll("[data-friend-link]").forEach(function (node) {
            if ("value" in node) {
                node.value = link;
            } else {
                node.textContent = link;
            }
        });
        document.querySelectorAll("[data-friend-mail]").forEach(function (node) {
            if (!node.href || !node.href.startsWith("mailto:")) {
                return;
            }
            const emailUrl = new URL(node.href);
            const body = emailUrl.searchParams.get("body") || "";
            emailUrl.searchParams.set("body", body + "\n\nInvite code: " + code + "\nInvite link: " + link);
            node.href = emailUrl.toString();
        });
        document.querySelectorAll("[data-copy-friend-link]").forEach(function (button) {
            button.addEventListener("click", function () {
                if (!navigator.clipboard) {
                    return;
                }
                navigator.clipboard.writeText(link).then(function () {
                    button.textContent = button.dataset.copiedLabel || "Copied";
                });
            });
        });
    }

    render();
}());
