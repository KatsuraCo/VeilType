# Launch Candidate Freeze

Updated: 2026-04-26  
Branch: `codex/predictive-typing-en-ru`

## Current Candidate

- APK: `C:\Users\dkats\StudioProjects\enigma_keyboard\android\app\build\outputs\apk\release\app-release.apk`
- Last verified artifact time: `2026-04-26 18:01:13`
- Release branch state: stabilization and UX cleanup only

## Included In Candidate

- Core keyboard input
  - letters / symbols / numeric mode transitions
  - shift / caps lock behavior
  - per-field numeric keypad mode
  - live keyboard height preset support
- Encryption / decryption
  - text encryption from keyboard
  - text decryption preview
  - one-time read enforcement on device
  - biometric gate for decrypt flows where configured
- Media capsules
  - audio capsule create / preview / send
  - photo capsule create / preview / add more / send
  - video capsule create / preview / send
  - generic binary sharing fallback for Telegram / WhatsApp class targets
- Key management
  - my keys list
  - edit existing key
  - one-time read flag
  - biometric decrypt flag
  - export allowed flag
  - protected export through biometric gate when available
- Safety features
  - panic wipe

## Explicitly Not In Launch Candidate

- Predictive typing UX
  - currently disabled on purpose
  - code path exists, but `PREDICTIVE_TYPING_ENABLED = false`
- Cloud or server-backed features
  - no remote revoke
  - no global burn-after-read
  - no synced receipts
- Dictation / speech-to-text
  - not part of this candidate
- TrueLock integration
  - not part of this candidate

## Candidate Rules

- No new product surface during freeze unless it fixes a launch-blocking bug.
- Allowed changes:
  - crash fixes
  - broken flow fixes
  - share / insert compatibility fixes
  - localization corrections
  - UX cleanup that does not alter core behavior
- Not allowed during freeze:
  - new monetization features
  - new security modes
  - prediction rework
  - dictation
  - TrueLock bridge

## Manual Validation Required Before Product Hunt

1. Core typing
   - English typing
   - Russian typing
   - shift single tap
   - caps lock double tap
   - language switch
   - numeric fields open num pad
2. Text security
   - encrypt selected/plain text
   - decrypt valid TL1
   - wrong key error
   - one-time read blocked on second open
3. Audio capsule
   - record
   - pause / resume
   - stop
   - play
   - send to Telegram
   - send to WhatsApp
4. Photo capsule
   - capture
   - preview
   - add more
   - send
   - cleanup on cancel
5. Video capsule
   - record
   - stop
   - preview
   - send
   - reopen after send / cancel without stale state
6. Key management
   - create key
   - edit key
   - disable export
   - biometric gate for export
   - panic wipe

## Known Risk Areas

- Android build environment is noisy:
  - Gradle / Kotlin cache sometimes requires `-Pkotlin.incremental=false`
  - shell timeout does not always mean build failure; artifact timestamp must be checked
- Media capsule flows are much more stable than before, but still highest risk versus plain text flows
- Product Hunt launch should not happen until the manual validation checklist above is passed on real devices

## Current Progress Snapshot

- Stage 1 core keyboard: 75%
- Stage 2.1 text / encrypt / decrypt: 60%
- Stage 2.2 audio: 60%
- Stage 2.3 photo: 100%
- Stage 2.4 video: 85%
- Stage 3 UX cleanup: 65%
- Overall roadmap: 70%
