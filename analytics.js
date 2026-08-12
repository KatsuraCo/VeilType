(function () {
  "use strict";

  var MEASUREMENT_ID = "G-V13VC9GMW8";
  var PRODUCT = "veiltype";
  var EXTERNAL_HOSTS = ["majorgeeks.com", "alternativeto.net", "producthunt.com", "reddit.com", "linkedin.com", "x.com", "threads.com"];

  window.dataLayer = window.dataLayer || [];
  window.gtag = window.gtag || function () {
    window.dataLayer.push(arguments);
  };

  function loadGoogleTag() {
    if (document.querySelector('script[data-veil-ga4="true"]')) return;
    var script = document.createElement("script");
    script.async = true;
    script.src = "https://www.googletagmanager.com/gtag/js?id=" + encodeURIComponent(MEASUREMENT_ID);
    script.setAttribute("data-veil-ga4", "true");
    document.head.appendChild(script);
    window.gtag("js", new Date());
    window.gtag("config", MEASUREMENT_ID, { send_page_view: false });
  }

  function hostOf(value) {
    if (!value) return "";
    try {
      return new URL(value, window.location.href).hostname.replace(/^www\./, "").toLowerCase();
    } catch (_) {
      return "";
    }
  }

  function sourceFromUrl() {
    var params = new URLSearchParams(window.location.search);
    return params.get("utm_source") || params.get("source") || params.get("ref") || "";
  }

  function attribution() {
    var referrerHost = hostOf(document.referrer);
    var explicitSource = sourceFromUrl();
    var source = explicitSource || referrerHost || "direct";
    var knownExternal = EXTERNAL_HOSTS.some(function (host) {
      return referrerHost === host || referrerHost.endsWith("." + host) || source === host;
    });

    return {
      source: source,
      referrer_host: referrerHost,
      external_problem_intent: knownExternal ? "yes" : "unknown"
    };
  }

  function platformFromLink(link) {
    var href = (link.getAttribute("href") || "").toLowerCase();
    var label = (link.textContent || link.getAttribute("aria-label") || "").toLowerCase();
    if (href.indexOf(".apk") !== -1 || label.indexOf("android") !== -1) return "android_apk";
    if (href.indexOf("play.google.com") !== -1 || label.indexOf("google play") !== -1) return "google_play";
    if (href.indexOf(".exe") !== -1 || label.indexOf("windows") !== -1) return "windows";
    if (href.indexOf("apkpure") !== -1) return "apkpure";
    return "unknown";
  }

  function isDownloadLink(link) {
    var href = (link.getAttribute("href") || "").toLowerCase();
    return href.indexOf("veiltype.apk") !== -1 ||
      href.indexOf("/downloads/") !== -1 ||
      href.indexOf("play.google.com") !== -1 ||
      href.indexOf("apkpure.com") !== -1;
  }

  function sendEvent(name, params) {
    var payload = Object.assign({
      product: PRODUCT,
      page_location: window.location.href,
      page_path: window.location.pathname
    }, attribution(), params || {});

    sendCollectBeacon(name, payload);
  }

  function clientId() {
    var key = "veil_ga4_client_id";
    var existing = localStorage.getItem(key);
    if (existing) return existing;
    var created = Date.now() + "." + Math.random().toString(36).slice(2, 12);
    localStorage.setItem(key, created);
    return created;
  }

  function sendCollectBeacon(name, params) {
    var query = new URLSearchParams({
      v: "2",
      tid: MEASUREMENT_ID,
      cid: clientId(),
      en: name,
      dl: window.location.href,
      dr: document.referrer || "",
      dt: document.title || ""
    });

    Object.keys(params || {}).forEach(function (key) {
      var value = params[key];
      if (value === undefined || value === null || value === "") return;
      query.set("ep." + key, String(value).slice(0, 500));
    });

    var url = "https://www.google-analytics.com/g/collect?" + query.toString();
    if (navigator.sendBeacon) {
      navigator.sendBeacon(url);
      return;
    }
    fetch(url, { method: "POST", mode: "no-cors", keepalive: true }).catch(function () {});
  }

  function reportLanding() {
    var data = attribution();
    if (data.source === "direct") return;
    var key = PRODUCT + ":external_referral_landing:" + data.source + ":" + window.location.pathname;
    if (sessionStorage.getItem(key)) return;
    sessionStorage.setItem(key, "1");
    sendEvent("external_referral_landing", { landing_source: data.source });
  }

  function bindDownloads() {
    document.addEventListener("click", function (event) {
      var link = event.target.closest && event.target.closest("a[href]");
      if (!link || !isDownloadLink(link)) return;
      sendEvent("download_click", {
        platform: platformFromLink(link),
        link_url: link.href,
        link_text: (link.textContent || "").trim().slice(0, 80)
      });
    }, true);
  }

  loadGoogleTag();
  reportLanding();
  bindDownloads();
}());
