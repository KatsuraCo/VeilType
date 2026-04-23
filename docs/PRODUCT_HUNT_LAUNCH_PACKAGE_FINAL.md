# Product Hunt Launch Package

## Product Name
VeilType

## Tagline
An Android keyboard that encrypts messages before they leave your chat app.

## Short Description
VeilType is an Android-first keyboard that turns plain text into copy/paste-safe ciphertext, decrypts copied messages locally, and keeps 8-emoji shared keys on-device with no server, no account, no cloud, and no recovery.

## Full Product Hunt Description
VeilType is a local-only encryption layer for everyday chat.

Instead of asking people to move to a new messenger, VeilType works inside the chat apps they already use. You type a message, encrypt it in the keyboard, and send ciphertext instead of plain text. On the other side, the recipient can decrypt locally with the same 8-emoji shared key.

The trust model is intentionally strict:
- no server for encryption or decryption
- no account system
- no cloud backup
- no recovery
- no global master key
- lost key means lost access

VeilType is Android-first today. The launch story is text encryption first: one 8-emoji shared key, local-only message handling, and no extra chat network. Audio and video capsules exist as secondary flows on the same model.

## First Comment by Maker
VeilType started from a simple frustration: private messages should not require a new messenger, an account graph, or a server you have to trust with your keys.

The current release focuses on one direct workflow:
- create or receive one 8-emoji shared key
- encrypt in the keyboard
- send ciphertext through any normal chat app
- decrypt copied messages locally from the clipboard

I would love feedback on three things in particular: the launch copy, the first-run setup, and whether the core text flow feels clear enough on first impression.

## Launch Checklist
- Finalize the Product Hunt title and tagline.
- Use one clear hero image that explains the product in one glance.
- Export 5 screenshots in the order below.
- Make sure the first sentence says `Android-first` and `local-only`.
- Keep the story centered on encrypted text in ordinary chats.
- Avoid leading with secondary flows that dilute the first-run story.
- Verify the app icon, screenshots, and product name are visually consistent.
- Prepare a short maker comment that explains the trust model in plain language.
- Prepare a concise FAQ for key recovery, storage, and compatibility questions.
- Have one demo scenario ready for comments and follow-up questions.

## Screenshot Captions
1. Create one local 8-emoji shared key in a few taps.
2. Encrypt text directly from the keyboard.
3. Send ciphertext through any chat app.
4. Decrypt copied messages locally from the clipboard.
5. Keep audio and video capsules local-first, too.

## FAQ

### What problem does VeilType solve?
It lets you send encrypted messages inside the chat apps you already use, without switching to a new messenger.

### Does VeilType store my keys in the cloud?
No. 8-emoji shared keys stay local on the device.

### Can the app recover my messages if I lose the key?
No. Recovery is intentionally not part of the model. If you lose the 8-emoji shared key, you lose access.

### Is VeilType a messenger replacement?
No. It is an encryption layer for existing chats, not a new chat network.

### Is it Android-only?
The current launch package is Android-first. Windows helper support is planned later.
