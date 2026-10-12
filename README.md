# NotifyRing

Rings like an incoming phone call if a notification from an app you care about
(WhatsApp, Instagram, etc.) sits unread in your status bar longer than a
threshold you set. Built for the "I was asleep / heads-down and missed it"
problem — no snoozed notification silently dies in the shade again.

## What it does

1. You pick which apps to watch and a per-app threshold (e.g. WhatsApp → 2 min).
2. A background listener detects new notifications from those apps only.
3. A timer starts for that notification.
4. If you open/clear it before the timer ends → nothing happens.
5. If the timer runs out and the notification is **still in the status bar**
   → the phone rings full-screen, like an incoming call, even on silent/DND,
   until you dismiss or snooze it.

Everything happens **on-device**. No server, no account, no internet
permission, no cloud storage. Your notification content never leaves your
phone — the app only ever looks at package name + timestamp, never the
message text.

## Platform

Android only. This relies on `NotificationListenerService` and
full-screen intents, which iOS does not permit to third-party apps.

## Architecture

| Layer | Tech |
|---|---|
| UI (settings, app picker, alarm screen) | React Native (Expo dev-client) |
| Notification capture | Native Kotlin `NotificationListenerService` |
| Threshold timer | Native `AlarmManager` (not JS timers — those die in the background) |
| Local storage | SQLite (`expo-sqlite`) — package name, notification id, timestamp only |
| Alarm trigger | Native full-screen `Activity` + `USE_FULL_SCREEN_INTENT` + ringtone |

No backend, no Cloudflare, no sync. The whole system is event-driven: the
listener only wakes on an actual notification, the timer is OS-scheduled, and
there is no polling loop anywhere — so it stays idle at ~0 CPU/RAM between
events.

## Required Android permissions

- Notification Listener access (read notifications from selected apps)
- `SCHEDULE_EXACT_ALARM` (precise threshold timing)
- `USE_FULL_SCREEN_INTENT` (ring over lock screen / other apps)
- Battery optimization exemption (so OEMs like Xiaomi/Oppo/Vivo don't kill
  the listener service in the background — this is the single biggest
  real-world reliability risk, not a code problem)

## Project status

Early build. See [Issues](../../issues) / commit history for current progress.

## Setup (dev)

```bash
npm install
npx expo prebuild
npx expo run:android
```

Requires a physical Android device or emulator with Google Play services
(notification listener permission prompts don't work reliably on bare AVDs
without it).

## Non-goals

- No iOS support (not possible with this approach).
- No true "did you reply" detection — Android exposes no such API. The app
  treats "notification still visible after threshold" as the trigger, which
  covers the real-world case correctly in the vast majority of situations.
- No cloud sync, accounts, or analytics.
