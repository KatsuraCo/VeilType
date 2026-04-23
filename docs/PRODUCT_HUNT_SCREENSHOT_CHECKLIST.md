# Product Hunt Screenshot Checklist

## Goal

Export five screenshots that explain VeilType in one quick pass without extra narration.

## Visual rules

- Use one Android language across the whole set.
- Keep the same device frame and status bar style.
- Do not show broken empty states, debug text, or internal implementation labels.
- Prefer one clear action per screenshot.
- Crop tightly enough that text stays readable in Product Hunt thumbnails.

## Screenshot 1: Home

Show:
- product title
- subtitle
- simple setup path
- trust strip

Message:
"This is a local-only encryption keyboard for ordinary chats."

## Screenshot 2: Create key

Show:
- shared-key setup
- generated 8-emoji shared key
- primary create/copy actions

Message:
"Create one local 8-emoji shared key and save it on your phone."

## Screenshot 3: Receive key

Show:
- pasted or imported 8-emoji shared key
- save action
- readable success state

Message:
"Save the same key on the second phone."

## Screenshot 4: Keyboard in chat

Show:
- VeilType keyboard inside a real chat
- normal text becoming encrypted output
- no clutter from secondary controls

Message:
"Encrypt directly in Telegram, WhatsApp, or another chat."

## Screenshot 5: Decrypt or media

Pick one:
- clipboard decrypt flow
- audio capsule
- video capsule

Preferred order:
- use decrypt if the flow is cleaner
- use media only if the screen is visually solid

Message:
"Only the person with the same 8-emoji key can read it."

## Avoid in screenshots

- old legacy naming
- raw lifecycle management controls
- internal format explanations
- unfinished empty states
- fallback or failure screens

## Final check before export

1. Read every visible line out loud.
2. Remove anything that sounds internal.
3. Make sure screenshots 1 and 4 can explain the product alone.
4. Make sure screenshots 2 and 3 explain setup without extra context.
5. Make sure the whole set can be understood in under 20 seconds.
