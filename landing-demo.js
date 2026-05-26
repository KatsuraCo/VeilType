(function () {
    document.querySelectorAll("[data-keyboard-demo]").forEach(function (demo) {
        var encryptButton = demo.querySelector("[data-demo-encrypt]");
        var decryptButton = demo.querySelector("[data-demo-decrypt]");
        var phone = demo.querySelector(".demo-phone");
        var output = demo.querySelector("[data-demo-output]");
        var compose = demo.querySelector("[data-demo-compose]");
        var hint = demo.querySelector("[data-demo-hint]");
        var hintText = demo.querySelector("[data-demo-hint-text]");
        var isRussian = demo.dataset.language === "ru";
        var plain = isRussian ? "Встретимся в 19:00" : "Meet me at 7 PM";
        var cipher = "TL1:Q8bz...kP2";
        var encryptHint = isRussian ? "Нажмите замок, чтобы зашифровать" : "Tap the lock to encrypt";
        var decryptHint = isRussian ? "Нажмите глаз, чтобы расшифровать" : "Tap the eye to decrypt";

        if (!encryptButton || !decryptButton || !phone || !output || !compose || !hint || !hintText) {
            return;
        }

        function showMessage(encrypted) {
            phone.classList.toggle("is-encrypted", encrypted);
            output.textContent = encrypted ? cipher : plain;
            compose.textContent = encrypted ? cipher : plain;
            encryptButton.classList.toggle("active", encrypted);
            decryptButton.classList.toggle("active", !encrypted);
            hint.classList.toggle("is-decrypt", encrypted);
            hintText.textContent = encrypted ? decryptHint : encryptHint;
        }

        encryptButton.addEventListener("click", function () {
            showMessage(true);
        });

        decryptButton.addEventListener("click", function () {
            showMessage(false);
        });
    });

    document.querySelectorAll("[data-tour]").forEach(function (tour) {
        var tabs = Array.prototype.slice.call(tour.querySelectorAll("[data-tour-step]"));
        var panels = Array.prototype.slice.call(tour.querySelectorAll("[data-tour-panel]"));

        tabs.forEach(function (tab) {
            tab.addEventListener("click", function () {
                var step = tab.dataset.tourStep;
                tabs.forEach(function (candidate) {
                    var active = candidate === tab;
                    candidate.classList.toggle("is-active", active);
                    candidate.setAttribute("aria-selected", active ? "true" : "false");
                });
                panels.forEach(function (panel) {
                    panel.classList.toggle("is-active", panel.dataset.tourPanel === step);
                });
            });
        });
    });
}());
