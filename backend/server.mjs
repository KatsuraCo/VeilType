import { createHash, randomBytes } from "node:crypto";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { createServer } from "node:http";
import { extname, join, normalize, resolve } from "node:path";
import { DatabaseSync } from "node:sqlite";

const rootDir = resolve(new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"));
const dataDir = join(rootDir, "backend", "data");
const dbPath = join(dataDir, "affiliate.sqlite");
const port = Number(process.env.PORT || 8787);
const appEnv = process.env.APP_ENV || "development";
const adminToken = process.env.ADMIN_TOKEN || (appEnv === "production" ? "" : "dev-admin-token");

if (!adminToken) {
    throw new Error("ADMIN_TOKEN is required in production.");
}

mkdirSync(dataDir, { recursive: true });

const db = new DatabaseSync(dbPath);
db.exec(`
CREATE TABLE IF NOT EXISTS partners (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ref_code TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    email TEXT NOT NULL,
    country TEXT,
    platform TEXT,
    profile_url TEXT,
    payout_method TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    commission_veiltype_usd REAL NOT NULL DEFAULT 1.00,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TEXT
);

CREATE TABLE IF NOT EXISTS referral_clicks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ref_code TEXT NOT NULL,
    landing_path TEXT,
    user_agent TEXT,
    ip_hash TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    purchase_code TEXT UNIQUE NOT NULL,
    product_code TEXT NOT NULL,
    buyer_email TEXT,
    buyer_name TEXT,
    amount_usd REAL NOT NULL,
    regular_price_usd REAL,
    discount_usd REAL,
    ref_code TEXT,
    invite_code TEXT,
    status TEXT NOT NULL DEFAULT 'confirmed',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TEXT,
    refunded_at TEXT
);

CREATE TABLE IF NOT EXISTS commissions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    partner_id INTEGER NOT NULL REFERENCES partners(id),
    purchase_id INTEGER NOT NULL REFERENCES purchases(id),
    amount_usd REAL NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    hold_until TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TEXT,
    paid_at TEXT,
    UNIQUE(partner_id, purchase_id)
);
`);

const mimeTypes = {
    ".html": "text/html; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
    ".txt": "text/plain; charset=utf-8",
    ".md": "text/markdown; charset=utf-8",
};

function json(res, status, body) {
    res.writeHead(status, {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "no-store",
    });
    res.end(JSON.stringify(body));
}

function bad(res, status, message) {
    json(res, status, { ok: false, error: message });
}

function readBody(req) {
    return new Promise((resolveBody, reject) => {
        let data = "";
        req.on("data", (chunk) => {
            data += chunk;
            if (data.length > 64_000) {
                reject(new Error("Request body too large."));
                req.destroy();
            }
        });
        req.on("end", () => {
            if (!data) {
                resolveBody({});
                return;
            }
            try {
                resolveBody(JSON.parse(data));
            } catch (error) {
                reject(new Error("Invalid JSON."));
            }
        });
    });
}

function requireAdmin(req, res) {
    const header = req.headers.authorization || "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : "";
    if (token !== adminToken) {
        bad(res, 401, "Admin token required.");
        return false;
    }
    return true;
}

function slug(input) {
    const cleaned = String(input || "")
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "_")
        .replace(/^_+|_+$/g, "")
        .slice(0, 40);
    return cleaned || `creator_${randomBytes(4).toString("hex")}`;
}

function uniqueRef(base) {
    let candidate = slug(base);
    let index = 2;
    const exists = db.prepare("SELECT 1 FROM partners WHERE ref_code = ?");
    while (exists.get(candidate)) {
        candidate = `${slug(base)}_${index}`;
        index += 1;
    }
    return candidate;
}

function ipHash(req) {
    const ip = req.headers["x-forwarded-for"] || req.socket.remoteAddress || "";
    return createHash("sha256").update(String(ip)).digest("hex").slice(0, 24);
}

function dashboard(refCode) {
    const partner = db.prepare("SELECT * FROM partners WHERE ref_code = ?").get(refCode);
    if (!partner) {
        return null;
    }
    const clicks = db.prepare("SELECT COUNT(*) AS count FROM referral_clicks WHERE ref_code = ?").get(refCode).count;
    const purchases = db.prepare("SELECT COUNT(*) AS count FROM purchases WHERE ref_code = ? AND status = 'confirmed'").get(refCode).count;
    const sums = db.prepare(`
        SELECT
            COALESCE(SUM(CASE WHEN status = 'pending' THEN amount_usd END), 0) AS pending,
            COALESCE(SUM(CASE WHEN status = 'available' THEN amount_usd END), 0) AS available,
            COALESCE(SUM(CASE WHEN status = 'paid' THEN amount_usd END), 0) AS paid
        FROM commissions
        WHERE partner_id = ?
    `).get(partner.id);
    return {
        partner: {
            ref_code: partner.ref_code,
            display_name: partner.display_name,
            email: partner.email,
            country: partner.country,
            platform: partner.platform,
            profile_url: partner.profile_url,
            status: partner.status,
            commission_veiltype_usd: partner.commission_veiltype_usd,
        },
        links: {
            veiltype: `https://veiltype.tech/?ref=${encodeURIComponent(partner.ref_code)}`,
            founder_access: `https://veiltype.tech/founder-access.html?ref=${encodeURIComponent(partner.ref_code)}`,
        },
        stats: {
            clicks,
            confirmed_purchases: purchases,
            commission_pending_usd: Number(sums.pending.toFixed(2)),
            commission_available_usd: Number(sums.available.toFixed(2)),
            commission_paid_usd: Number(sums.paid.toFixed(2)),
        },
        rules: {
            commission_hold_days: 21,
            minimum_payout_usd: 20,
        },
    };
}

async function handleApi(req, res, url) {
    try {
        if (req.method === "POST" && url.pathname === "/api/partners/apply") {
            const body = await readBody(req);
            const displayName = String(body.display_name || "").trim();
            const email = String(body.email || "").trim();
            if (!displayName || !email.includes("@")) {
                bad(res, 400, "display_name and valid email are required.");
                return;
            }
            const refCode = uniqueRef(body.ref_code || displayName);
            db.prepare(`
                INSERT INTO partners (ref_code, display_name, email, country, platform, profile_url, payout_method)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            `).run(
                refCode,
                displayName,
                email,
                body.country || null,
                body.platform || null,
                body.profile_url || null,
                body.payout_method || null,
            );
            json(res, 201, { ok: true, ref_code: refCode, status: "pending" });
            return;
        }

        if (req.method === "POST" && url.pathname === "/api/ref/click") {
            const body = await readBody(req);
            const refCode = slug(body.ref_code || body.ref || "");
            if (!refCode) {
                bad(res, 400, "ref_code is required.");
                return;
            }
            db.prepare(`
                INSERT INTO referral_clicks (ref_code, landing_path, user_agent, ip_hash)
                VALUES (?, ?, ?, ?)
            `).run(refCode, body.landing_path || null, req.headers["user-agent"] || null, ipHash(req));
            json(res, 201, { ok: true });
            return;
        }

        const partnerMatch = url.pathname.match(/^\/api\/partner\/([A-Za-z0-9_-]+)$/);
        if (req.method === "GET" && partnerMatch) {
            const data = dashboard(partnerMatch[1]);
            if (!data) {
                bad(res, 404, "Partner not found.");
                return;
            }
            json(res, 200, { ok: true, ...data });
            return;
        }

        if (req.method === "POST" && url.pathname === "/api/admin/partners/approve") {
            if (!requireAdmin(req, res)) return;
            const body = await readBody(req);
            const result = db.prepare("UPDATE partners SET status = 'active', approved_at = CURRENT_TIMESTAMP WHERE ref_code = ?").run(slug(body.ref_code));
            json(res, 200, { ok: true, changed: result.changes });
            return;
        }

        if (req.method === "GET" && url.pathname === "/api/admin/partners") {
            if (!requireAdmin(req, res)) return;
            const rows = db.prepare("SELECT * FROM partners ORDER BY created_at DESC").all();
            json(res, 200, { ok: true, partners: rows });
            return;
        }

        if (req.method === "POST" && url.pathname === "/api/admin/purchases/confirm") {
            if (!requireAdmin(req, res)) return;
            const body = await readBody(req);
            const productCode = body.product_code || "veiltype_early_access";
            const amount = Number(body.amount_usd || 3.49);
            const refCode = body.ref_code ? slug(body.ref_code) : null;
            const purchaseCode = body.purchase_code || `VT-PUR-${Date.now()}-${randomBytes(2).toString("hex")}`;
            const purchase = db.prepare(`
                INSERT INTO purchases (purchase_code, product_code, buyer_email, buyer_name, amount_usd, regular_price_usd, discount_usd, ref_code, invite_code, status, confirmed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'confirmed', CURRENT_TIMESTAMP)
            `).run(
                purchaseCode,
                productCode,
                body.buyer_email || null,
                body.buyer_name || null,
                amount,
                Number(body.regular_price_usd || 4.49),
                Number(body.discount_usd || 0),
                refCode,
                body.invite_code || null,
            );

            let commission = null;
            if (refCode) {
                const partner = db.prepare("SELECT * FROM partners WHERE ref_code = ? AND status = 'active'").get(refCode);
                if (partner) {
                    const holdUntil = new Date(Date.now() + 21 * 24 * 60 * 60 * 1000).toISOString();
                    const commissionResult = db.prepare(`
                        INSERT OR IGNORE INTO commissions (partner_id, purchase_id, amount_usd, hold_until)
                        VALUES (?, ?, ?, ?)
                    `).run(partner.id, purchase.lastInsertRowid, partner.commission_veiltype_usd, holdUntil);
                    commission = { created: commissionResult.changes === 1, amount_usd: partner.commission_veiltype_usd, hold_until: holdUntil };
                }
            }
            json(res, 201, { ok: true, purchase_code: purchaseCode, commission });
            return;
        }

        if (req.method === "POST" && url.pathname === "/api/admin/commissions/refresh") {
            if (!requireAdmin(req, res)) return;
            const result = db.prepare(`
                UPDATE commissions
                SET status = 'available', available_at = CURRENT_TIMESTAMP
                WHERE status = 'pending' AND hold_until <= ?
            `).run(new Date().toISOString());
            json(res, 200, { ok: true, changed: result.changes });
            return;
        }

        if (req.method === "POST" && url.pathname === "/api/admin/commissions/mark-paid") {
            if (!requireAdmin(req, res)) return;
            const body = await readBody(req);
            const result = db.prepare(`
                UPDATE commissions
                SET status = 'paid', paid_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('available', 'pending')
            `).run(Number(body.commission_id));
            json(res, 200, { ok: true, changed: result.changes });
            return;
        }

        if (req.method === "GET" && url.pathname === "/api/admin/commissions") {
            if (!requireAdmin(req, res)) return;
            const rows = db.prepare(`
                SELECT c.*, p.ref_code, p.display_name, pur.purchase_code, pur.buyer_email
                FROM commissions c
                JOIN partners p ON p.id = c.partner_id
                JOIN purchases pur ON pur.id = c.purchase_id
                ORDER BY c.created_at DESC
            `).all();
            json(res, 200, { ok: true, commissions: rows });
            return;
        }

        bad(res, 404, "API route not found.");
    } catch (error) {
        bad(res, 500, error.message || "Server error.");
    }
}

function serveStatic(req, res, url) {
    const pathname = url.pathname === "/" ? "/index.html" : decodeURIComponent(url.pathname);
    const filePath = normalize(join(rootDir, pathname));
    if (!filePath.startsWith(rootDir) || !existsSync(filePath)) {
        res.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
        res.end("Not found");
        return;
    }
    const ext = extname(filePath);
    res.writeHead(200, {
        "content-type": mimeTypes[ext] || "application/octet-stream",
        "cache-control": ext === ".html" ? "no-cache" : "public, max-age=300",
    });
    res.end(readFileSync(filePath));
}

createServer((req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
    if (url.pathname.startsWith("/api/")) {
        handleApi(req, res, url);
        return;
    }
    serveStatic(req, res, url);
}).listen(port, "127.0.0.1", () => {
    console.log(`VeilType affiliate backend running at http://127.0.0.1:${port}/`);
    if (appEnv !== "production") {
        console.log("Development admin token: dev-admin-token");
    }
});
