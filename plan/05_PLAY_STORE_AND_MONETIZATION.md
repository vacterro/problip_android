# Play Store / Monetization Notes

## Public identity

- Brand: Problip
- Play title: Problip: Random Beep Timer
- Launcher: Problip
- Suggested application ID: com.vacster.problip

## Monetization philosophy

Free product:
- fully useful
- no ads
- no time limit
- no subscription

Paid:
- individual permanent sound unlocks
- one permanent themes pack

Suggested pricing intention:
- sounds: around €0.49 each
- themes pack: around €1.99

Do not hardcode currency/price strings in the app.
Always display Play-provided localized pricing.

## Initial product IDs

Example:

```text
sound_glass
sound_wood
sound_soft_bell
sound_bonk
sound_space
theme_pack
```

Treat sound/theme unlocks as permanent non-consumable one-time products.

Avoid an All Sounds bundle in v1.0 unless discount/ownership interaction is deliberately designed.

## Privacy

Target posture:
- no account
- no ads
- no analytics
- no location
- no contacts
- no camera
- no microphone
- local settings only
- purchases processed by Google Play

Privacy policy should describe the real implementation, not aspirations.

## Policy-sensitive background behaviour

Problip background execution must be:
- explicitly started by the user
- visible via foreground notification while active
- directly beneficial to the stated product function
- stoppable by the user
- accurately declared in Play Console

Do not implement stealth autostart or unsupported reboot resurrection.

## Store copy direction

Short description candidate:

`Random audio cues at intervals you choose, with sounds and custom themes.`

Avoid:
- medical treatment claims
- ADHD/anxiety cure claims
- keyword spam
- fake popularity/ranking language
- "FREE!!!"
- misleading price statements

## Release principle

Re-check current Google Play requirements immediately before:
- choosing final target SDK
- integrating Billing
- submitting foreground-service declaration
- completing Data Safety
- entering closed testing
- production release

Policies evolve. The roadmap is the architecture/process contract, not permission to freeze platform rules forever.
