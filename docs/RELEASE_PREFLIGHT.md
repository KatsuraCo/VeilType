# Release Preflight

Run this before any release candidate or store upload.

## What It Checks

- `testDebugUnitTest`
- `lintDebug`
- `assembleRelease`
- suspicious local artifacts in the repo/worktree:
  - `android/keystore.properties`
  - `*.jks`
  - `*.keystore`
  - `*.ovpn`

## Command

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\release_preflight.ps1
```

## Pass Criteria

- All Gradle checks succeed.
- No suspicious artifacts are present in the repo/worktree.

## If It Fails

- Fix the reported Gradle or lint issue first.
- Remove or relocate any secret/release artifact before shipping.
- Re-run the script from the repo root.
