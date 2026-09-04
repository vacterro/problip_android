# Testing and Release Checklist

## Core unit tests

- [ ] random delay always within 4000..7000 ms
- [ ] fixed 5 sec exact
- [ ] fixed 10 sec exact
- [ ] fixed 15 sec exact
- [ ] fixed 20 sec exact
- [ ] fixed 30 sec exact
- [ ] initial delay behaviour
- [ ] START starts scheduling
- [ ] duplicate START does not duplicate scheduler
- [ ] STOP cancels future playback
- [ ] duplicate STOP is harmless
- [ ] START after STOP works

## Audio

- [ ] sound loads successfully
- [ ] missing sound -> ERROR
- [ ] 0% volume
- [ ] 1% volume
- [ ] 5% volume
- [ ] 50% volume
- [ ] 100% volume
- [ ] 200+ sequential blips
- [ ] rapid START/STOP
- [ ] speaker
- [ ] Bluetooth
- [ ] wired/USB headphones where available
- [ ] music/video can coexist as intended

## Settings

- [ ] volume survives restart
- [ ] interval survives restart
- [ ] selected sound survives restart
- [ ] selected theme survives restart
- [ ] running session does not silently resurrect after reboot

## Foreground/background

- [ ] screen ON 30 min
- [ ] background activity 60 min
- [ ] locked screen 60 min
- [ ] screen OFF 60 min
- [ ] Battery Saver
- [ ] Doze
- [ ] swipe activity from recents
- [ ] force stop
- [ ] reboot
- [ ] incoming call interruption
- [ ] notification STOP
- [ ] notification tap opens app
- [ ] no orphan notification
- [ ] no orphan service
- [ ] no duplicate scheduler
- [ ] no leaked WakeLock if used

## Billing

- [ ] product query
- [ ] localized prices
- [ ] purchase success
- [ ] pending purchase
- [ ] acknowledge
- [ ] restart after purchase
- [ ] reinstall/restore
- [ ] billing disconnect/reconnect
- [ ] offline cached state
- [ ] refund/revocation behaviour reviewed
- [ ] locked items never become playable accidentally

## Lifecycle torture

Repeat:

```text
START
background
foreground
lock
unlock
change volume
change interval
STOP
START
kill activity
open activity
STOP
```

## Emulator matrix

- [ ] API 26
- [ ] API 31
- [ ] API 33
- [ ] API 34
- [ ] API 35
- [ ] API 36

## Physical devices

- [ ] main test phone
- [ ] at least one aggressive-OEM battery-management phone if possible

## Store/policy

- [ ] developer account verified
- [ ] merchant profile configured
- [ ] payout method configured
- [ ] privacy policy public
- [ ] privacy policy in app
- [ ] Data Safety accurate
- [ ] FGS declaration accurate
- [ ] FGS demo video
- [ ] internal test complete
- [ ] closed-test requirement satisfied if applicable
- [ ] Play Vitals checked
- [ ] production AAB signed and reproducible
