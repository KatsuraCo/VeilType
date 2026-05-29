(function () {
    const config = window.VEILTYPE_BACKEND || {};
    const apiBase = (config.apiBaseUrl || window.location.origin).replace(/\/$/, "");
    const tokenInput = document.querySelector("[data-admin-token]");
    const status = document.querySelector("[data-admin-status]");

    function token() {
        return tokenInput.value.trim() || localStorage.getItem("veiltype-admin-token") || "";
    }

    function headers() {
        return {
            "content-type": "application/json",
            authorization: "Bearer " + token(),
        };
    }

    async function post(path, payload) {
        localStorage.setItem("veiltype-admin-token", token());
        const response = await fetch(apiBase + path, {
            method: "POST",
            headers: headers(),
            body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!data.ok) throw new Error(data.error || "Request failed.");
        return data;
    }

    document.querySelector("[data-approve-form]")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            const payload = Object.fromEntries(new FormData(event.currentTarget).entries());
            const data = await post("/api/admin/partners/approve", payload);
            status.textContent = `Partner approval updated: ${data.changed}`;
        } catch (error) {
            status.textContent = error.message;
        }
    });

    document.querySelector("[data-confirm-purchase-form]")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            const payload = Object.fromEntries(new FormData(event.currentTarget).entries());
            const data = await post("/api/admin/purchases/confirm", payload);
            status.textContent = `Purchase confirmed: ${data.purchase_code}`;
            event.currentTarget.reset();
        } catch (error) {
            status.textContent = error.message;
        }
    });
}());
