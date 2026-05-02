import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, "..", "..");
const privateDir = path.join(projectRoot, "tools", "license_private");
const privateKeyPath = path.join(privateDir, "veiltype_ed25519_private.pem");

function base64Url(buffer) {
  return Buffer.from(buffer)
    .toString("base64")
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

function ensurePrivateKey() {
  if (fs.existsSync(privateKeyPath)) {
    return fs.readFileSync(privateKeyPath, "utf8");
  }
  fs.mkdirSync(privateDir, { recursive: true });
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ed25519");
  const privatePem = privateKey.export({ type: "pkcs8", format: "pem" });
  const publicDer = publicKey.export({ type: "spki", format: "der" });
  const rawPublic = publicDer.subarray(publicDer.length - 32);
  fs.writeFileSync(privateKeyPath, privatePem);
  fs.writeFileSync(path.join(privateDir, "veiltype_ed25519_public_raw_base64.txt"), rawPublic.toString("base64"));
  fs.writeFileSync(path.join(privateDir, "veiltype_ed25519_public.pem"), publicKey.export({ type: "spki", format: "pem" }));
  throw new Error(
    `Created a new key pair in ${privateDir}. Put the printed public key into LicenseVerifier.kt before issuing licenses.`,
  );
}

function arg(name, fallback = "") {
  const prefix = `--${name}=`;
  const found = process.argv.find((item) => item.startsWith(prefix));
  return found ? found.slice(prefix.length) : fallback;
}

const privatePem = ensurePrivateKey();
const privateKey = crypto.createPrivateKey(privatePem);
const now = new Date();
const ymd = now.toISOString().slice(0, 10).replaceAll("-", "");
const licenseId = arg("id", `VEIL-${ymd}-${crypto.randomBytes(3).toString("hex").toUpperCase()}`);
const deviceId = arg("device", "");
const expiresAt = arg("expires", "");

const payload = {
  licenseId,
  product: "veiltype",
  plan: arg("plan", "lifetime"),
  issuedAt: now.toISOString(),
};
if (expiresAt) payload.expiresAt = expiresAt;
if (deviceId) payload.deviceId = deviceId;

const payloadJson = JSON.stringify(payload);
const signature = crypto.sign(null, Buffer.from(payloadJson, "utf8"), privateKey).toString("base64");
const signed = JSON.stringify({
  payload: base64Url(Buffer.from(payloadJson, "utf8")),
  signature,
});
const activationCode = `VEIL-${base64Url(Buffer.from(signed, "utf8")).match(/.{1,4}/g).join("-")}`;

const outDir = path.join(projectRoot, "licenses");
fs.mkdirSync(outDir, { recursive: true });
const baseName = licenseId.replace(/[^A-Za-z0-9_-]/g, "_");
fs.writeFileSync(path.join(outDir, `${baseName}.lic`), signed);
fs.writeFileSync(path.join(outDir, `${baseName}.code.txt`), activationCode);

console.log(`License ID: ${licenseId}`);
console.log(`Activation code: ${activationCode}`);
console.log(`Saved to: ${outDir}`);
