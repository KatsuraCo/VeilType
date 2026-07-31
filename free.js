(() => {
  const translations = {
    en: {
      skip: "Skip to content", languageLabel: "Language", navWorkflow: "How it works", navDownload: "Download", navTrust: "Trust model", navFeedback: "Feedback", getFree: "Get it free",
      heroEyebrow: "FREE FOREVER · ANDROID", heroTitle: "Encrypt before the messenger receives it.", heroCopy: "Write in a secure compose field, encrypt locally, then send through Telegram, WhatsApp, Signal, SMS or another app.", downloadApk: "Download free APK", seeWorkflow: "See the workflow", heroNote: "No account. No activation. No ads. Production build has no INTERNET permission.",
      signalFree: "$0 forever", signalFreeNote: "Complete personal feature set", signalNetwork: "Network permissions", signalCapsules: "Text and media capsules", signalKeyboard: "Keyboard first", signalKeyboardNote: "Use your existing messengers",
      workflowEyebrow: "A VISIBLE ENCRYPTION STEP", workflowTitle: "Normal chat app. Private compose path.", workflowCopy: "VeilType does not pretend every keystroke is encrypted. You explicitly choose the secure compose field and press Encrypt before sharing.", step1: "Write inside VeilType", step2: "Encrypt locally", step3: "Send through your app",
      featureEyebrow: "ONE KEYBOARD, TWO MODES", featureTitle: "Type normally when privacy is not needed. Encrypt when it is.", feature1Title: "Ordinary typing", feature1Copy: "Use VeilType like a normal keyboard for unencrypted messages.", feature2Title: "Armored text", feature2Copy: "Short encrypted messages can travel as copyable text.", feature3Title: "Media capsules", feature3Copy: "Photos, video, audio and files are shared as authenticated .veil files.", feature4Title: "Local identities", feature4Copy: "Password and recipient encryption use keys held on the device.",
      downloadEyebrow: "VEILTYPE 0.4.0", downloadTitle: "The complete keyboard is free.", downloadCopy: "The APK includes secure compose, ordinary typing, text and media capsules, local profiles and every personal feature. There is no trial period.", downloadPoint1: "Android 8.0 or newer", downloadPoint2: "14.0 MB signed APK", downloadPoint3: "No INTERNET permission", counterLabel: "downloads started", counterDisclosure: "The public counter records download starts and may update with a delay of up to four hours.",
      trustEyebrow: "TRUST THROUGH CONSTRAINTS", trustTitle: "A keyboard should not ask for blind trust.", trustCopy: "Verify the APK permissions, inspect the published core and test encryption in airplane mode. VeilType has not received an independent security audit, so we publish what is proven and state what is not.", privacyPolicy: "Privacy policy", securityModel: "Security model", openCore: "Open core",
      feedbackEyebrow: "SHORTEN THE FIX LOOP", feedbackTitle: "Tell us exactly where it breaks.", feedbackCopy: "Messenger compatibility changes. A structured report helps us reproduce and fix the problem faster.", typeLabel: "Type", typeBug: "Bug", typeFeature: "Feature request", typeCompatibility: "Messenger compatibility", messengerLabel: "Messenger or app", summaryLabel: "Short summary", detailsLabel: "Steps, expected result and actual result", versionLabel: "Phone, Android and VeilType version", publicIssue: "Open public issue", privateEmail: "Send private email", feedbackWarning: "Never include passwords, private keys, sensitive plaintext, contacts or real encrypted capsules.",
      ecosystemEyebrow: "FILES NEED PRIVACY TOO", ecosystemCopy: "Create protected capsules on Android and Windows. Also free forever.", openTrueLock: "Open TrueLock", footerTagline: "Private communication without another messenger."
    },
    ru: {
      skip: "Перейти к содержимому", languageLabel: "Язык", navWorkflow: "Как это работает", navDownload: "Скачать", navTrust: "Модель доверия", navFeedback: "Обратная связь", getFree: "Скачать бесплатно",
      heroEyebrow: "БЕСПЛАТНО НАВСЕГДА · ANDROID", heroTitle: "Шифруйте до того, как сообщение получит мессенджер.", heroCopy: "Пишите в защищённом поле, шифруйте локально и отправляйте через Telegram, WhatsApp, Signal, SMS или другое приложение.", downloadApk: "Скачать бесплатный APK", seeWorkflow: "Посмотреть процесс", heroNote: "Без аккаунта, активации и рекламы. В production-сборке нет разрешения INTERNET.",
      signalFree: "$0 навсегда", signalFreeNote: "Полный набор пользовательских функций", signalNetwork: "Сетевых разрешений", signalCapsules: "Текстовые и медиавложенные капсулы", signalKeyboard: "Сначала клавиатура", signalKeyboardNote: "Используйте привычные мессенджеры",
      workflowEyebrow: "ЯВНЫЙ ШАГ ШИФРОВАНИЯ", workflowTitle: "Обычный чат. Защищённый путь набора.", workflowCopy: "VeilType не делает вид, что каждое нажатие уже зашифровано. Вы явно выбираете защищённое поле и нажимаете Encrypt перед отправкой.", step1: "Напишите в VeilType", step2: "Зашифруйте локально", step3: "Отправьте через своё приложение",
      featureEyebrow: "ОДНА КЛАВИАТУРА, ДВА РЕЖИМА", featureTitle: "Печатайте обычно, когда защита не нужна. Шифруйте, когда нужна.", feature1Title: "Обычный набор", feature1Copy: "Используйте VeilType как нормальную клавиатуру для незашифрованных сообщений.", feature2Title: "Armored-текст", feature2Copy: "Короткие зашифрованные сообщения можно передавать как копируемый текст.", feature3Title: "Медиакapsулы", feature3Copy: "Фото, видео, аудио и файлы отправляются как аутентифицированные .veil-файлы.", feature4Title: "Локальные профили", feature4Copy: "Парольное и recipient-шифрование использует ключи, хранящиеся на устройстве.",
      downloadEyebrow: "VEILTYPE 0.4.0", downloadTitle: "Полная клавиатура бесплатна.", downloadCopy: "APK включает защищённый набор, обычный ввод, текстовые и медиакapsулы, локальные профили и все пользовательские функции. Пробного периода нет.", downloadPoint1: "Android 8.0 или новее", downloadPoint2: "Подписанный APK размером 14,0 МБ", downloadPoint3: "Нет разрешения INTERNET", counterLabel: "начатых загрузок", counterDisclosure: "Публичный счётчик учитывает начало загрузки и может обновляться с задержкой до четырёх часов.",
      trustEyebrow: "ДОВЕРИЕ ЧЕРЕЗ ОГРАНИЧЕНИЯ", trustTitle: "Клавиатура не должна требовать слепого доверия.", trustCopy: "Проверьте разрешения APK, изучите опубликованное ядро и протестируйте шифрование в авиарежиме. VeilType не проходил независимый аудит, поэтому мы публикуем доказанное и прямо указываем ограничения.", privacyPolicy: "Политика конфиденциальности", securityModel: "Модель безопасности", openCore: "Открытое ядро",
      feedbackEyebrow: "СОКРАЩАЕМ ПУТЬ ДО ИСПРАВЛЕНИЯ", feedbackTitle: "Расскажите, где именно всё ломается.", feedbackCopy: "Совместимость мессенджеров меняется. Структурированный отчёт помогает быстрее воспроизвести и исправить проблему.", typeLabel: "Тип", typeBug: "Ошибка", typeFeature: "Предложение функции", typeCompatibility: "Совместимость с мессенджером", messengerLabel: "Мессенджер или приложение", summaryLabel: "Краткое описание", detailsLabel: "Шаги, ожидаемый и фактический результат", versionLabel: "Телефон, Android и версия VeilType", publicIssue: "Создать публичный issue", privateEmail: "Отправить приватное письмо", feedbackWarning: "Никогда не прикладывайте пароли, закрытые ключи, конфиденциальный текст, контакты или настоящие зашифрованные капсулы.",
      ecosystemEyebrow: "ФАЙЛАМ ТОЖЕ НУЖНА ЗАЩИТА", ecosystemCopy: "Создавайте защищённые капсулы на Android и Windows. Также бесплатно навсегда.", openTrueLock: "Открыть TrueLock", footerTagline: "Приватное общение без ещё одного мессенджера."
    }
  };
  const placeholders = {
    en: { messengerPlaceholder: "Telegram 12.1", summaryPlaceholder: "What happened?", detailsPlaceholder: "1. I enabled VeilType…", versionPlaceholder: "Samsung S23, Android 15, VeilType 0.4.0" },
    ru: { messengerPlaceholder: "Telegram 12.1", summaryPlaceholder: "Что произошло?", detailsPlaceholder: "1. Я включил VeilType…", versionPlaceholder: "Samsung S23, Android 15, VeilType 0.4.0" }
  };
  const languageSelect = document.querySelector("#language");
  const browserLanguage = (navigator.language || "en").toLowerCase().startsWith("ru") ? "ru" : "en";
  const queryLanguage = new URLSearchParams(window.location.search).get("lang");
  let language = queryLanguage || localStorage.getItem("veiltype-language") || browserLanguage;
  if (!translations[language]) language = "en";
  function applyLanguage(nextLanguage) {
    language = translations[nextLanguage] ? nextLanguage : "en";
    document.documentElement.lang = language;
    document.querySelectorAll("[data-i18n]").forEach((element) => {
      const value = translations[language][element.dataset.i18n];
      if (value) element.textContent = value;
    });
    document.querySelectorAll("[data-i18n-placeholder]").forEach((element) => {
      const value = placeholders[language][element.dataset.i18nPlaceholder];
      if (value) element.placeholder = value;
    });
    if (languageSelect) languageSelect.value = language;
    localStorage.setItem("veiltype-language", language);
  }
  languageSelect?.addEventListener("change", (event) => applyLanguage(event.target.value));
  applyLanguage(language);

  async function loadCounter() {
    const targets = document.querySelectorAll('[data-counter="veiltype-android"]');
    try {
      const response = await fetch("https://yasha381.goatcounter.com/counter/veiltype-android.json");
      if (!response.ok) return;
      const data = await response.json();
      targets.forEach((target) => { target.textContent = data.count || "0"; });
    } catch (_) {
      targets.forEach((target) => { target.textContent = "0"; });
    }
  }
  loadCounter();

  const form = document.querySelector("[data-feedback-form]");
  function reportFromForm() {
    if (!form?.reportValidity()) return null;
    const data = new FormData(form);
    return {
      type: String(data.get("type") || "Bug"), messenger: String(data.get("messenger") || "Not specified").trim(),
      summary: String(data.get("summary") || "").trim(), details: String(data.get("details") || "").trim(),
      environment: String(data.get("environment") || "").trim()
    };
  }
  function reportBody(report) {
    return `Product: VeilType 0.4.0\nType: ${report.type}\nMessenger/app: ${report.messenger}\nEnvironment: ${report.environment}\n\nSteps and result:\n${report.details}\n\nSecurity note: no passwords, keys, contacts or sensitive content are included.`;
  }
  form?.addEventListener("submit", (event) => {
    event.preventDefault();
    const report = reportFromForm(); if (!report) return;
    const url = new URL("https://github.com/KatsuraCo/VeilType/issues/new");
    url.searchParams.set("title", `[${report.type}] ${report.summary}`); url.searchParams.set("body", reportBody(report));
    window.open(url.toString(), "_blank", "noopener");
  });
  document.querySelector("[data-email-feedback]")?.addEventListener("click", () => {
    const report = reportFromForm(); if (!report) return;
    window.location.href = `mailto:support@truelock.pro?subject=${encodeURIComponent(`[VeilType ${report.type}] ${report.summary}`)}&body=${encodeURIComponent(reportBody(report))}`;
  });
})();
