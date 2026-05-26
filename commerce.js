(function () {
    const config = window.VEILTYPE_COMMERCE || {};
    const configuredUrl = typeof config.checkoutUrl === "string" ? config.checkoutUrl.trim() : "";
    if (!configuredUrl) {
        return;
    }

    let checkoutUrl;
    try {
        checkoutUrl = new URL(configuredUrl);
    } catch (error) {
        return;
    }

    if (checkoutUrl.protocol !== "https:") {
        return;
    }

    const primaryButton = document.querySelector("[data-checkout-primary]");
    const message = document.querySelector("[data-checkout-message]");
    const status = document.querySelector("[data-checkout-status]");
    if (!primaryButton) {
        return;
    }

    const attribution = window.VEILTYPE_ATTRIBUTION;
    primaryButton.href = attribution ? attribution.appendToUrl(checkoutUrl.toString()) : checkoutUrl.toString();
    primaryButton.textContent = primaryButton.dataset.checkoutReadyLabel;
    if (message) {
        message.textContent = message.dataset.checkoutReadyText;
    }
    if (status) {
        status.textContent = status.dataset.checkoutReadyText;
    }
    document.documentElement.dataset.checkout = "ready";
}());
