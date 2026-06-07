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

## License backend

`backend/license-server.mjs` issues signed `VEIL...` activation codes for paid direct sales. It is separate from the affiliate backend and should run on its own port or service.

Start locally:

```powershell
$env:PORT="8788"
$env:ADMIN_TOKEN="local-admin-token"
$env:PAYMENT_WEBHOOK_SECRET="local-webhook-secret"
$env:VEILTYPE_LICENSE_PRIVATE_KEY_PATH="tools/license_private/veiltype_ed25519_private.pem"
$env:CORS_ORIGIN="https://veiltype.tech"
node backend/license-server.mjs
```

Create a manual paid order after a confirmed invoice:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8788/api/orders/create `
  -Headers @{ "x-admin-token" = "local-admin-token" } `
  -ContentType "application/json" `
  -Body '{"email":"buyer@example.com","orderId":"ORDER-001","amountUsd":10,"maxActivations":1}'
```

When the payment widget is ready, configure successful payments to call:

`POST /api/webhooks/payment`

Header:

`x-webhook-secret: <PAYMENT_WEBHOOK_SECRET>`

Body:

```json
{
  "email": "buyer@example.com",
  "orderId": "provider-order-id",
  "provider": "payment-provider-name",
  "providerPaymentId": "payment-id",
  "amountUsd": 10,
  "maxActivations": 1,
  "refCode": "creator_or_channel"
}
```

Keep the Ed25519 private key only on the backend host. Never publish it in the static site.
