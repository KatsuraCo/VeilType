# VeilType Affiliate Backend MVP

Manual backend for the period before an automatic payment widget is available.

It supports:

- partner applications and approval;
- referral click tracking;
- manual purchase confirmation by admin;
- creator commission creation;
- partner dashboard stats;
- commission payout marking.

It does not process payments. It records purchases after manual confirmation.

## Run locally

```powershell
$env:ADMIN_TOKEN="change-me"
node backend/server.mjs
```

Open:

- `http://localhost:8787/partner-login.html`
- `http://localhost:8787/partner-dashboard.html?ref=demo`
- `http://localhost:8787/admin.html`

## Production notes

- Set `ADMIN_TOKEN` to a strong secret.
- Put the server behind HTTPS.
- Keep `backend/data/` private and backed up.
- Replace manual admin purchase confirmation with payment webhooks after the first 30 sales.
