(function () {
    const config = window.VEILTYPE_COMMERCE || {};
    const attribution = window.VEILTYPE_ATTRIBUTION;
    const attributionValues = attribution ? attribution.current : null;
    const refCode = attributionValues && (attributionValues.ref || attributionValues.promo);
    const regularPrice = Number(config.regularPriceUsd || 0);
    const launchCode = String(config.launchCode || "").trim();
    const launchEndsAt = Date.parse(config.launchEndsAt || "");
    const hasLaunchOffer = Boolean(launchCode) && Number.isFinite(launchEndsAt) && Date.now() < launchEndsAt;
    const hasCreatorDiscount = Boolean(refCode) && !hasLaunchOffer;
    const launchPrice = Number(config.launchPriceUsd || 0);
    const discount = hasLaunchOffer
        ? Math.max(0, regularPrice - launchPrice)
        : (hasCreatorDiscount ? Number(config.creatorDiscountUsd || 0) : 0);
    const finalPrice = Math.max(0, regularPrice - discount);
    const currency = config.currency || "USD";
    const productCode = config.productCode || "veiltype_free_forever";

    function money(value) {
        return "$" + value.toFixed(2);
    }

    function setText(selector, value) {
        document.querySelectorAll(selector).forEach(function (node) {
            node.textContent = value;
        });
    }

    function enrich(rawUrl) {
        const enriched = attribution ? attribution.appendToUrl(rawUrl) : rawUrl;
        const url = new URL(enriched, window.location.href);
        url.searchParams.set("product", productCode);
        url.searchParams.set("regular_price_usd", regularPrice.toFixed(2));
        url.searchParams.set("discount_usd", discount.toFixed(2));
        url.searchParams.set("final_price_usd", finalPrice.toFixed(2));
        url.searchParams.set("currency", currency);
        url.searchParams.set("campaign", hasLaunchOffer ? "product_hunt_launch" : (hasCreatorDiscount ? "creator" : "direct"));
        if (refCode && !url.searchParams.has("ref")) {
            url.searchParams.set("ref", refCode);
        }
        return url.toString();
    }

    setText("[data-regular-price]", money(regularPrice));
    setText("[data-final-price]", money(finalPrice));
    setText("[data-discount-value]", money(discount || Number(config.creatorDiscountUsd || 1)));
    document.querySelectorAll("[data-creator-ref]").forEach(function (node) {
        node.textContent = refCode || "direct";
    });
    document.querySelectorAll("[data-creator-offer]").forEach(function (node) {
        node.hidden = !hasCreatorDiscount;
    });
    document.querySelectorAll("[data-direct-offer]").forEach(function (node) {
        node.hidden = hasCreatorDiscount || hasLaunchOffer;
    });
    document.querySelectorAll("[data-launch-offer]").forEach(function (node) {
        node.hidden = !hasLaunchOffer;
    });
    document.querySelectorAll("[data-launch-code]").forEach(function (node) {
        node.textContent = launchCode;
    });
    document.documentElement.dataset.creatorDiscount = hasCreatorDiscount ? "active" : "inactive";

    const configuredUrl = typeof config.checkoutUrl === "string" ? config.checkoutUrl.trim() : "";

    document.querySelectorAll("[data-checkout-primary][href^='mailto:']").forEach(function (link) {
        const emailUrl = new URL(link.href);
        const subject = emailUrl.searchParams.get("subject") || "VeilType access request";
        const body = emailUrl.searchParams.get("body") || "";
        emailUrl.searchParams.set("subject", subject.replace(/\$3(\.00)?/g, money(finalPrice)));
        emailUrl.searchParams.set("body", body
            .replace(/\$3(\.00)?/g, money(finalPrice))
            + "\n\nProduct: " + productCode
            + "\nRegular price: " + money(regularPrice)
            + "\nDiscount: " + money(discount)
            + "\nFinal price: " + money(finalPrice)
            + (hasLaunchOffer ? "\nProduct Hunt code: " + launchCode : "")
            + (refCode ? "\nReferral code: " + refCode : ""));
        link.href = emailUrl.toString();
    });

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

    primaryButton.href = enrich(checkoutUrl.toString());
    primaryButton.textContent = primaryButton.dataset.checkoutReadyLabel || primaryButton.textContent;
    if (message) {
        message.textContent = message.dataset.checkoutReadyText;
    }
    if (status) {
        status.textContent = status.dataset.checkoutReadyText;
    }
    document.documentElement.dataset.checkout = "ready";
}());
