# Product Hunt Readiness

## Goal

Prepare VeilType for a Product Hunt launch where a new visitor can understand the product in under one minute.

## What is already ready

- Android release APK build
- Product Hunt copy package
- local-only trust model
- working keyboard flow for text encryption
- shared-key creation and import
- audio and video capsule support

## What was improved in this pass

- Public docs now use the VeilType brand consistently.
- Launch copy now leads with one story:
  1. Enable keyboard
  2. Create or receive one 8-emoji shared key
  3. Encrypt in any ordinary chat
- Public narrative now removes legacy setup language that distracted from the first-run value.
- Trust-model messaging is now explicit and consistent:
  - no server
  - no account
  - no cloud
  - no recovery

## Remaining launch blockers

### P0
- Keyboard visual polish still needs another pass on real devices.
- The first successful end-to-end chat demo still depends on setup quality.
- Some secondary in-app screens still carry more complexity than the launch story.

### P1
- Shared-key receive flow can still become simpler.
- Media capsules are stronger as secondary launch material than as the main headline.
- More device testing is needed before a public Android launch.

## Recommended Product Hunt narrative

Lead with this story:

1. Turn on VeilType Keyboard.
2. Create or receive one 8-emoji shared key.
3. Send encrypted messages in the chat apps you already use.

Do not lead with:
- media capsules
- advanced key lifecycle
- low-level protocol names
- internal implementation language

## Demo sequence for launch

1. Home screen
2. Create 8-emoji shared key
3. Save the same 8-emoji key on the second phone
4. Open Telegram or WhatsApp
5. Encrypt and send one message
6. Decrypt it on the second device

## Honest readiness score

- Internal alpha: high
- Closed beta: medium
- Product Hunt page, copy, and launch assets: high
- Product Hunt first-run app UX: medium
- Overall PH readiness after this docs pass: around 80%

## Next highest-leverage work

1. Record one flawless 30-second demo on two Android phones.
2. Polish the keyboard toolbar and icon clarity.
3. Remove any remaining confusing technical labels from secondary screens.
4. Test the setup flow on fresh devices with no prior context.
