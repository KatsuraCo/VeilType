(function () {
    const config = window.VEILTYPE_BACKEND || {};
    const apiBase = (config.apiBaseUrl || window.location.origin).replace(/\/$/, "");
    const form = document.querySelector("[data-partner-apply-form]");
    const status = document.querySelector("[data-partner-apply-status]");
    if (!form) return;

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const payload = Object.fromEntries(new FormData(form).entries());
        status.textContent = "Submitting...";
        try {
            const response = await fetch(apiBase + "/api/partners/apply", {
                method: "POST",
                headers: { "content-type": "application/json" },
                body: JSON.stringify(payload),
            });
            const data = await response.json();
            if (!data.ok) throw new Error(data.error || "Application failed.");
            status.innerHTML = `Application received. Your ref code: <strong>${data.ref_code}</strong>. Dashboard: <a href="./partner-dashboard.html?ref=${encodeURIComponent(data.ref_code)}">open</a>`;
            form.reset();
        } catch (error) {
            status.textContent = error.message;
        }
    });
}());
