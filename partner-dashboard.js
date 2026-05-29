(function () {
    const config = window.VEILTYPE_BACKEND || {};
    const apiBase = (config.apiBaseUrl || window.location.origin).replace(/\/$/, "");
    const params = new URLSearchParams(window.location.search);
    const refCode = (params.get("ref") || "").trim();

    function set(selector, value) {
        document.querySelectorAll(selector).forEach((node) => {
            node.textContent = value;
        });
    }

    function link(selector, value) {
        document.querySelectorAll(selector).forEach((node) => {
            node.href = value;
            node.textContent = value;
        });
    }

    if (!refCode) {
        set("[data-dashboard-status]", "Enter your ref code on the login page.");
        return;
    }

    fetch(apiBase + "/api/partner/" + encodeURIComponent(refCode))
        .then((response) => response.json())
        .then((data) => {
            if (!data.ok) {
                throw new Error(data.error || "Partner not found.");
            }
            set("[data-dashboard-status]", data.partner.status === "active" ? "Active partner" : "Pending approval");
            set("[data-partner-name]", data.partner.display_name);
            set("[data-ref-code]", data.partner.ref_code);
            set("[data-clicks]", data.stats.clicks);
            set("[data-purchases]", data.stats.confirmed_purchases);
            set("[data-pending]", "$" + data.stats.commission_pending_usd.toFixed(2));
            set("[data-available]", "$" + data.stats.commission_available_usd.toFixed(2));
            set("[data-paid]", "$" + data.stats.commission_paid_usd.toFixed(2));
            link("[data-veiltype-link]", data.links.veiltype);
            link("[data-founder-link]", data.links.founder_access);
        })
        .catch((error) => {
            set("[data-dashboard-status]", error.message);
        });
}());
