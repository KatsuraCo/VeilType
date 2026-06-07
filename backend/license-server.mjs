import crypto from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, "..");
const dataDir = path.join(__dirname, "data");
const ordersPath = path.join(dataDir, "orders.json");

const port = Number(process.env.PORT || 8787);
const adminToken = process.env.ADMIN_TOKEN || "";
const webhookSecret = process.env.PAYMENT_WEBHOOK_SECRET || "";
const corsOrigin = process.env.CORS_ORIGIN || "*";
const privateKeyPath = path.resolve(
  projectRoot,
  process.env.VEILTYPE_LICENSE_PRIVATE_KEY_PATH ||
    "tools/license_private/veiltype_ed25519_private.pem",
);

function jsonResponse(res, status, body) {
  const payload = JSON.stringify(body, null, 2);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": corsOrigin,
    "access-control-allow-methods": "GET,POST,OPTIONS",
    "access-control-allow-headers": "content-type,x-admin-token,x-webhook-secret",
    "cache-control": "no-store",
  });
  res.end(payload);
}

function fail(res, status, code, message) {
  jsonResponse(res, status, { ok: false, code, message });
}

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

function normalizeOrderId(orderId) {
  return String(orderId || "").trim();
}

function normalizeRefCode(refCode) {
  const normalized = String(refCode || "").trim().toLowerCase();
  return /^[a-z0-9][a-z0-9_-]{0,63}$/.test(normalized) ? normalized : "";
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 64_000) {
        reject(new Error("Request body is too large."));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (!body.trim()) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(body));
      } catch {
        reject(new Error("Invalid JSON body."));
      }
    });
    req.on("error", reject);
  });
}

function loadOrders() {
  fs.mkdirSync(dataDir, { recursive: true });
  if (!fs.existsSync(ordersPath)) {
    return { orders: [] };
  }
  return JSON.parse(fs.readFileSync(ordersPath, "utf8"));
}

function saveOrders(state) {
  fs.mkdirSync(dataDir, { recursive: true });
  const tempPath = `${ordersPath}.tmp`;
  fs.writeFileSync(tempPath, JSON.stringify(state, null, 2));
  fs.renameSync(tempPath, ordersPath);
}

function findOrder(state, email, orderId) {
  const normalizedEmail = normalizeEmail(email);
  const normalizedOrderId = normalizeOrderId(orderId);
  return state.orders.find(
    (order) =>
      normalizeEmail(order.email) === normalizedEmail &&
      normalizeOrderId(order.orderId) === normalizedOrderId,
  );
}

function requireAdmin(req) {
  return Boolean(adminToken) && req.headers["x-admin-token"] === adminToken;
}

function requireWebhook(req) {
  return Boolean(webhookSecret) && req.headers["x-webhook-secret"] === webhookSecret;
}

function base64Url(buffer) {
  return Buffer.from(buffer)
    .toString("base64")
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

function activationCodeFromSignedLicense(signedLicense) {
  return `VEIL-${base64Url(Buffer.from(signedLicense, "utf8")).match(/.{1,4}/g).join("-")}`;
}

function loadPrivateKey() {
  if (!fs.existsSync(privateKeyPath)) {
    throw new Error(`License private key not found: ${privateKeyPath}`);
  }
  return crypto.createPrivateKey(fs.readFileSync(privateKeyPath, "utf8"));
}

function signLicense({ email, orderId, deviceId = "", plan = "lifetime", expiresAt = "" }) {
  const privateKey = loadPrivateKey();
  const safeOrder = normalizeOrderId(orderId).replace(/[^A-Za-z0-9_-]/g, "").slice(0, 28);
  const licenseId = `VEIL-${safeOrder || crypto.randomBytes(4).toString("hex").toUpperCase()}`;
  const payload = {
    licenseId,
    product: "veiltype",
    plan,
    issuedAt: new Date().toISOString(),
    orderId: normalizeOrderId(orderId),
    emailHash: crypto.createHash("sha256").update(normalizeEmail(email)).digest("hex").slice(0, 16),
  };
  if (expiresAt) payload.expiresAt = String(expiresAt);
  if (deviceId) payload.deviceId = String(deviceId).trim();

  const payloadJson = JSON.stringify(payload);
  const signature = crypto.sign(null, Buffer.from(payloadJson, "utf8"), privateKey).toString("base64");
  const signedLicense = JSON.stringify({
    payload: base64Url(Buffer.from(payloadJson, "utf8")),
    signature,
  });
  return {
    licenseId,
    activationCode: activationCodeFromSignedLicense(signedLicense),
    signedLicense,
    payload,
  };
}

function orderView(order) {
  return {
    email: order.email,
    orderId: order.orderId,
    paid: order.paid,
    plan: order.plan,
    maxActivations: order.maxActivations,
    activationCount: order.activations?.length || 0,
    refCode: order.refCode || "",
    createdAt: order.createdAt,
  };
}

async function createOrder(req, res, body, source) {
  if (source === "admin" && !requireAdmin(req)) {
    fail(res, 401, "admin_required", "Admin token is required.");
    return;
  }
  if (source === "webhook" && !requireWebhook(req)) {
    fail(res, 401, "webhook_required", "Webhook secret is required.");
    return;
  }

  const email = normalizeEmail(body.email);
  const orderId = normalizeOrderId(body.orderId || body.providerPaymentId);
  if (!email || !orderId) {
    fail(res, 400, "missing_order_fields", "Email and orderId are required.");
    return;
  }

  const state = loadOrders();
  let order = findOrder(state, email, orderId);
  if (!order) {
    order = {
      email,
      orderId,
      paid: true,
      plan: body.plan || "lifetime",
      maxActivations: Number(body.maxActivations || 1),
      provider: body.provider || source,
      providerPaymentId: body.providerPaymentId || "",
      amountUsd: body.amountUsd ?? 3,
      refCode: normalizeRefCode(body.refCode),
      createdAt: new Date().toISOString(),
      activations: [],
    };
    state.orders.push(order);
  } else {
    order.paid = true;
    order.plan = body.plan || order.plan || "lifetime";
    order.maxActivations = Number(body.maxActivations || order.maxActivations || 1);
    order.provider = body.provider || order.provider || source;
    order.providerPaymentId = body.providerPaymentId || order.providerPaymentId || "";
    order.refCode = order.refCode || normalizeRefCode(body.refCode);
    order.updatedAt = new Date().toISOString();
  }
  saveOrders(state);
  jsonResponse(res, 200, { ok: true, order: orderView(order) });
}

async function redeemLicense(res, body) {
  const email = normalizeEmail(body.email);
  const orderId = normalizeOrderId(body.orderId);
  const deviceId = String(body.deviceId || "").trim();
  if (!email || !orderId) {
    fail(res, 400, "missing_redeem_fields", "Email and order ID are required.");
    return;
  }

  const state = loadOrders();
  const order = findOrder(state, email, orderId);
  if (!order || !order.paid) {
    fail(res, 404, "order_not_found", "Paid order was not found.");
    return;
  }

  order.activations = Array.isArray(order.activations) ? order.activations : [];
  const existing = order.activations.find(
    (activation) => (activation.deviceId || "") === deviceId,
  );
  if (existing) {
    jsonResponse(res, 200, {
      ok: true,
      licenseId: existing.licenseId,
      activationCode: existing.activationCode,
      reused: true,
    });
    return;
  }

  if (order.activations.length >= Number(order.maxActivations || 1)) {
    fail(res, 409, "activation_limit_reached", "Activation limit reached for this order.");
    return;
  }

  const license = signLicense({
    email,
    orderId,
    deviceId,
    plan: order.plan || "lifetime",
  });
  order.activations.push({
    licenseId: license.licenseId,
    activationCode: license.activationCode,
    deviceId,
    createdAt: new Date().toISOString(),
  });
  order.updatedAt = new Date().toISOString();
  saveOrders(state);

  jsonResponse(res, 200, {
    ok: true,
    licenseId: license.licenseId,
    activationCode: license.activationCode,
    reused: false,
  });
}

async function issueDirect(req, res, body) {
  if (!requireAdmin(req)) {
    fail(res, 401, "admin_required", "Admin token is required.");
    return;
  }
  const email = normalizeEmail(body.email || "manual@veiltype.local");
  const orderId = normalizeOrderId(body.orderId || `MANUAL-${Date.now()}`);
  const license = signLicense({
    email,
    orderId,
    deviceId: body.deviceId || "",
    plan: body.plan || "lifetime",
    expiresAt: body.expiresAt || "",
  });
  jsonResponse(res, 200, {
    ok: true,
    licenseId: license.licenseId,
    activationCode: license.activationCode,
    payload: license.payload,
  });
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    jsonResponse(res, 204, {});
    return;
  }

  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  try {
    if (req.method === "GET" && url.pathname === "/api/health") {
      jsonResponse(res, 200, { ok: true, service: "veiltype-license", time: new Date().toISOString() });
      return;
    }

    if (req.method !== "POST") {
      fail(res, 404, "not_found", "Endpoint not found.");
      return;
    }

    const body = await readBody(req);
    if (url.pathname === "/api/orders/create") {
      await createOrder(req, res, body, "admin");
      return;
    }
    if (url.pathname === "/api/webhooks/payment") {
      await createOrder(req, res, body, "webhook");
      return;
    }
    if (url.pathname === "/api/licenses/redeem") {
      await redeemLicense(res, body);
      return;
    }
    if (url.pathname === "/api/licenses/issue") {
      await issueDirect(req, res, body);
      return;
    }

    fail(res, 404, "not_found", "Endpoint not found.");
  } catch (error) {
    fail(res, 500, "server_error", error.message || "Server error.");
  }
});

server.listen(port, () => {
  console.log(`VeilType license backend listening on http://localhost:${port}`);
});
