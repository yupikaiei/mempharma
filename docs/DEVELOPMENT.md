# MemPharma — Developer Documentation

Practical notes for building, testing, understanding and releasing MemPharma.

## 1. Build

All Gradle commands use the wrapper (Gradle 8.11.1) and expect **JDK 17** and an Android SDK
(`compileSdk 35`, `build-tools 35.0.0`). On a dev container or CI set:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/android-sdk        # or local.properties sdk.dir
./gradlew :app:assembleDebug                 # fast debug build
./gradlew :app:testDebugUnitTest             # JVM unit tests (9 tests)
./gradlew :app:assembleRelease               # R8/minified release
./gradlew :app:lintDebug                     # Android Lint (optional)
```

## 2. Architecture

Single-activity, **MVVM**, unidirectional data flow:

```
UI (Compose) ──► ViewModel (StateFlow) ──► Repository ──► Room DAO ──► SQLite
        ▲              │                         │
        └──────────────┴── observe flows ◄──────┘
                              │
            AlarmScheduler ◄──┘   (re)schedules exact alarms per medicine
```

- **`domain/DoseEngine.kt`** — pure functions (next occurrence, today's slots, quantity math).
  Zero Android dependencies → the unit-tested core. All time math uses the device's local zone.
- **`data/repo/MedicationRepository`** — CRUD + keeps alarms in sync with the database.
- **`data/repo/TrackingRepository`** — the single funnel for real-world actions
  (taken / muted / missed / refill). Notification buttons **and** in-app buttons both call it,
  so behaviour is identical everywhere.
- **BroadcastReceivers** are system-instantiated, so they reach Hilt through
  `AppGraph.from(context)` (`@EntryPoint`), not constructor injection.
- **Screens** each own a `@HiltViewModel`; navigation is a single `NavHost`
  with a large bottom `NavigationBar` (`Today`, `Medicines`, `History`, `Settings`).

### Scheduling design (important)

- One exact alarm is kept per *dose time-of-day* per medicine ("slot").
- When a slot fires, `ReminderReceiver` posts the notification **and re-arms that slot for the
  next day**, so alarms are always exactly one-ahead and never accumulate.
- Alarm identity is deterministic: `requestCode = f(medId, minutesOfDay)`. No id bookkeeping in DB.
- **Reboot / app update** (`BootReceiver`) and **clock / timezone / date change**
  (`TimeChangeReceiver`) re-schedule everything from the database.
- Opening the app also calls `scheduler.rescheduleAll()` as a safety net
  (e.g. after Android force-stop clears alarms).
- **Exact-alarm permissions**:
  - API 31–32: `SCHEDULE_EXACT_ALARM` must be granted by the user (Settings screen guides them).
  - API 33+: `USE_EXACT_ALARM` is auto-granted for alarm/medicine apps.
  - When exact alarms are unavailable we degrade to inexact `setAndAllowWhileIdle` + the
    in-app overdue banner, so reminders are never silently lost.

### Notification → action flow

```
Reminder fires (exact alarm)
   └─ ReminderReceiver
        ├─ starts AlarmRingerService (foreground): loops alarm tone + vibrates
        │     until the person answers — it does not stop on its own
        ├─ posts an ONGOING full-screen notification (cannot be swiped away)
        ├─ launches AlarmActivity full-screen (fills device, shows over lock
        │     screen, turnScreenOn + showWhenLocked)
        ├─ re-arms next occurrence
        └─ actions (buttons in the full screen OR the notification) go to:
             ActionReceiver / AlarmActivity
             ├─ "✓ I took it" → TrackingRepository.recordTaken()
             │     • idempotent per dose (a mute is superseded by a take)
             │     • stock decremented (min 0)
             │     • TAKEN event logged with timestamp
             │     • reminders paused if stock hits 0
             │     • stops ringer, closes AlarmActivity, dismisses notification
             └─ "Not now" → TrackingRepository.recordMuted()
                   • only this dose; MUTED event logged; stock unchanged
                   • stops ringer / closes the full screen

If a foreground service start is blocked, ReminderReceiver falls back to posting
the same full-screen notification (no looping tone in that rare case).
```

## 3. Data model

**`medications`**

| column | meaning |
|---|---|
| `id` | PK (auto) |
| `name`, `colorIndex` | display identity |
| `doseQuantity`, `unitLabel` | size of one dose |
| `quantity` | pills left in stock |
| `startDateEpochDay` | when treatment began (**traceability**) |
| `timesCsv` | daily dose times `"08:00,20:00"` |
| `lowStockThreshold`, `active` | refill alert level, on/off |

**`dose_events`** — append-only audit log (history + traceability).

| column | meaning |
|---|---|
| `id` | PK (auto) |
| `medicationId` | FK-ish (logical) |
| `scheduledForEpochMillis` | the dose occurrence that was due (null for refills) |
| `actionAtEpochMillis` | when the user/system acted |
| `action` | `TAKEN` \| `MUTED` \| `MISSED` \| `REFILLED` \| `EDITED` |
| `note` | human-readable detail |

Room schema JSON is exported to `app/schemas/` (enabled via `ksp { room.schemaLocation }`) so
future migrations can be validated. Version 1 uses `fallbackToDestructiveMigration()`; bump the
version + write a real `Migration` in `AppModule` before any schema change ships.

## 4. Accessibility & UX for elderly users

- Type scale already starts larger than Material defaults.
- **Settings → Text size** multiplies the whole UI via a global `fontScale` (CompositionLocal
  density override) — no per-widget work needed.
- 48dp+ touch targets, big labelled bottom navigation, high-contrast colour roles.
- One primary action per medicine card: **"✓ I took it"**; **"Not now"** is the quiet alternative.
- TalkBack: all icons have content descriptions / text labels; status pills carry text.
- `MedPalette` colours chosen for contrast with white initials.

## 5. Permissions

Declared in `AndroidManifest.xml`:

| permission | why | notes |
|---|---|---|
| `POST_NOTIFICATIONS` | show reminders | runtime prompt on Android 13+ |
| `USE_EXACT_ALARM` | precise timing | API 33+; auto for medicine/alarm apps |
| `SCHEDULE_EXACT_ALARM` | precise timing | API 31–32; user-granted; `maxSdkVersion=32` |
| `RECEIVE_BOOT_COMPLETED` | re-arm after reboot | |
| `VIBRATE` | alarm vibration | |
| `USE_FULL_SCREEN_INTENT` | full-screen alarm covers the whole display | Android 14+ needs the "Full-screen notifications" toggle (Settings guides the user) |
| `FOREGROUND_SERVICE` | keep the alarm ringing until answered | required to start a foreground service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | declare the ringer service type (`specialUse`) | Android 14+ |

No `INTERNET` permission — the app is fully offline.

## 6. Release pipeline (GitHub → phone)

`.github/workflows/build-apk.yml` runs on every push to `main`:

1. unit tests,
2. `assembleRelease` (signed, monotonic `versionCode`/`versionName` from the run number),
3. uploads the APK as a workflow artifact,
4. publishes a GitHub **release** with the APK **and a QR code** linking to the latest APK.

**One-time signing setup (needed for updates to install over each other):**

```bash
# 1. create a keystore (do this once, keep it safe — never commit it)
keytool -genkeypair -v \
  -keystore mempharma-release.keystore \
  -alias mempharma \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. add it to the repo as secrets
base64 -w0 mempharma-release.keystore   # copy the output
# Repo → Settings → Secrets and variables → Actions:
#   ANDROID_KEYSTORE_BASE64   = the base64 string
#   ANDROID_KEYSTORE_PASSWORD = store password
#   ANDROID_KEY_ALIAS         = mempharma
#   ANDROID_KEY_PASSWORD      = key password
```

**Install on a phone:** open the newest release → `app-release.apk` (or scan the QR) →
allow "install unknown apps" once. Subsequent builds update in place.

> Local builds without the secrets simply fall back to the **debug key**, so
> `assembleRelease` always works on a developer machine.

## 7. Testing & QA checklist

- [ ] `./gradlew :app:testDebugUnitTest` — green.
- [ ] Add a medicine with a time 2 minutes ahead; background the app.
- [ ] Lock screen notification appears → tap **✓ I took it**:
      stock decremented, TAKEN logged, notification gone.
- [ ] Repeat with **Not now**: MUTED logged, stock unchanged, no re-nag.
- [ ] Reboot the device → a future reminder still fires (`BootReceiver`).
- [ ] Change the system time/timezone → alarms follow (`TimeChangeReceiver`).
- [ ] Deny exact alarms → reminders still appear (inexact) + Settings guidance shows.
- [ ] Run stock to 0 → reminders pause; add a refill → reminders resume, REFILLED logged.
- [ ] **History → export** → CSV contains medicines (start dates) + event timestamps.
- [ ] Settings → text size Extra large → whole UI scales; no clipped controls.
- [ ] TalkBack reads all screens; every control reachable.

## 8. Scope decisions (v1)

**In:** multiple medicines, fixed daily schedules, quantity + refill + low stock,
dose audit log (taken/mute), started-date tracing, CSV export, GitHub APK distribution.

**Out (deliberately):** cloud sync, caregiver/multi-user, "as needed" (PRN) dosing,
pill/barcode recognition, wearables, prescription refill integrations.
