# VeilType offline licensing

VeilType uses an offline signed license code:

- the generator signs a JSON payload with an Ed25519 private key;
- the Android app verifies that payload with the embedded public key;
- no backend is required for activation;
- revocation and device-count enforcement across devices are not possible without a backend.

Private keys are stored under `tools/license_private/` and must never be committed.

Generate a lifetime license:

```powershell
node tools/license/generate-license.mjs
```

Generate a device-bound license:

```powershell
node tools/license/generate-license.mjs --device=<device-id-from-app>
```
