# VeilType license backend

Minimal offline-license issuing backend for direct sales outside Google Play.

It does not decrypt user data and does not store message content. It only stores paid orders and issued activation codes.

## Start locally

```powershell
cd C:\Users\dkats\StudioProjects\enigma_keyboard
$env:ADMIN_TOKEN="local-admin-token"
$env:PAYMENT_WEBHOOK_SECRET="local-webhook-secret"
$env:VEILTYPE_LICENSE_PRIVATE_KEY_PATH="tools/license_private/veiltype_ed25519_private.pem"
node backend/license-server.mjs
```

## Manual paid order

Use this after a manual payment while the payment service is not connected:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8787/api/orders/create `
  -Headers @{ "x-admin-token" = "local-admin-token" } `
  -ContentType "application/json" `
  -Body '{"email":"buyer@example.com","orderId":"ORDER-001","maxActivations":1}'
```

The buyer then opens `/activate.html`, enters email, order ID, and optional device ID from the Android app.

## Public redeem endpoint

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8787/api/licenses/redeem `
  -ContentType "application/json" `
  -Body '{"email":"buyer@example.com","orderId":"ORDER-001","deviceId":""}'
```

## Payment webhook

When a payment provider is ready, configure it to POST successful purchases to:

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
  "amountUsd": 3
}
```

## Security notes

- The private Ed25519 key must stay only on the backend.
- `backend/data/` is ignored by git and contains order/license state.
- Without a backend account system, this protects paid features but cannot fully prevent APK copying.
