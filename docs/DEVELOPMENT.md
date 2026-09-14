# MemPharma — Developer Documentation

Practical notes for building, testing, understanding and releasing MemPharma.

## 1. Build

All Gradle commands use the wrapper (Gradle 8.11.1) and expect **JDK 17** and an Android SDK
(`compileSdk 35`, `build-tools 35.0.0`). On a dev container or CI set:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/android-sdk        # or local.properties sdk.dir
./gradlew :app:assembleDebug                 # fast debug build
./gradlew :app:testDebugUnitTest             # JVM unit tests (48 tests)
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
  so behaviour is identical everywhere. It also owns the live-alert queue: an alarm that has
  rung and not yet been taken stays pending until `recordTaken` clears it.
- **`data/repo/ActiveAlertRepository`** — persists that live-alert queue in DataStore
  (`mempharma_active_alerts`), deliberately outside Room so adding it needs no schema migration.
- **`data/settings/SettingsRepository`** — DataStore-backed app settings: the global font-scale
  and the chosen alert sound (`AlertTone`). Read by `AlarmRingerService` so reminders ring with
  the tone the person picked, always rendered on the alarm stream (there is no silent option).
- **`data/settings/LanguageStore`** — the display language picked in Settings. Kept in a small
  `SharedPreferences` file (deliberately not DataStore) because it must be readable
  *synchronously* while a context attaches — see *Languages / localization* below.
- **`data/sms/SmsAlertManager`** — the optional *"text a family member"* refill alerts. The
  decision rules are pure (`domain/SmsTrigger`), the chosen contact, optional patient name and
  send-history live in a DataStore (`SmsAlertRepository`, so no Room migration), and sending is
  triggered both straight after a dose is recorded and by a daily `SmsAlertWorker`.
- **BroadcastReceivers** are system-instantiated, so they reach Hilt through
  `AppGraph.from(context)` (`@EntryPoint`), not constructor injection.
- **Screens** each own a `@HiltViewModel`; navigation is a single `NavHost`
  with a large bottom `NavigationBar` (`Today`, `Medicines`, `History`, `Settings`).

### Languages / localization

MemPharma ships **Portuguese (Portugal) as the default language** and keeps English:

- `res/values/strings.xml` → **Portuguese (pt-PT)** — the default/fallback.
- `res/values-en/strings.xml` → **English** — used when the phone is set to English.

**Settings → Language** lets the person pin the language — *Igual ao telemóvel* (the default),
*Português* or *English* — instead of following the phone:

- `data/settings/LanguageStore` persists the choice (`system` / `pt-PT` / `en`) in a small
  `SharedPreferences` file. `AppLocale.normalise` maps anything stale or hand-edited back to
  `system`, so a bad value can never break startup.
- `Context.withAppLanguage()` (`data/settings/LanguageStore.kt`) reads that value and, via
  `AppLocale.localized`, returns a context whose resources use the chosen language. The
  `Application` and both activities call it from `attachBaseContext`, so every screen,
  notification, refill text, audit note and CSV header follows the choice.
- Text resolved **outside Compose** (notification titles/actions, `SmsTexts`, audit notes,
  `ExportUtils`) goes through `Context.withAppLanguage()` too, so a change made in the same
  session applies immediately, without waiting for a restart.
- Changing the language re-creates the settings screen (`Activity.recreate()`), which re-runs
  `attachBaseContext` with the new language.

With *Igual ao telemóvel* nothing is forced, so the **displayed language can differ from the
device language** (a French phone falls back to the Portuguese default). Because of that:

- Never call `Locale.getDefault()` for user-visible dates/times. Use `rememberAppLocale()`
  (`util/AppLocale.kt`), which mirrors the resource fallback, and pass it to the
  locale-parameterized helpers in `util/TimeFormat.kt`.
- Keep user-facing text in `strings.xml` only. Compose screens use `stringResource` /
  `pluralStringResource`; ViewModels expose `@StringRes Int` (e.g. `AddEditState.error`) or take
  `@ApplicationContext` and wrap it with `Context.withAppLanguage()` when they need a localized
  default value (`AddEditMedViewModel`, `TrackingRepository`).
- The pure rules in `domain/SmsTrigger.kt` take an `SmsTemplates` value (built from resources by
  `data/sms/SmsTexts.kt`) so the domain stays free of Android dependencies and remains unit-tested.
- **Known limits**: notification-channel names are immutable once created (existing installs keep
  the old wording), and audit-log notes are stored in the language active when the event was
  recorded, so old rows stay in their original language (no migration).

### Automated refill texts (optional)

The person can pick **one contact from their contacts** in `Settings → Text a family member`.
MemPharma then texts that person when a medicine is running low, and again if it runs out.

```
stock drops (dose recorded)              daily SmsAlertWorker
      └─► TrackingRepository.recordTaken         └─► SmsAlertManager.evaluateAll()
                    └────────────► SmsAlertManager ────────────┘
                                       │  pure rules: domain/SmsTrigger
                                       ├─ SmsAlertRepository (contact, patient name, what was sent)
                                       └─ SmsSender (SmsManager, multipart)
```

- **Per-medicine level**: the Add/Edit medicine screen sets `lowStockThreshold`
  ("text my family member when this many are left"). A medicine that is switched off is ignored.
- **Patient name (optional)**: `Settings → Text a family member` also takes an optional patient
  name (stored in the same DataStore, independent of the chosen contact). It is woven into every
  message — `John's Metformin…`, or `James' Metformin…` when the name already ends in an *s*.
  Left blank, the original wording is used unchanged.
- **Escalation**: crossing into `LOW` sends once; reaching `OUT` sends again immediately.
- **Repeat**: every 3 days while the medicine stays low/empty (`SmsTrigger.REPEAT_INTERVAL_MILLIS`).
- **Reset**: recording a refill (`TrackingRepository.refill`) clears the record, so the next
  run-low episode sends again. Deleting a medicine clears it too.
- **No duplicates**: a `Mutex` in `SmsAlertManager` serialises the event path and the worker; the
  3-day rule is re-checked against the DataStore record before every send.
- **Added already low?** `MedicationRepository.create` seeds the record *without* sending, so
  setting the app up never fires a text.
- **Contact picking needs no `READ_CONTACTS`**: `Intent.ACTION_PICK` with
  `CommonDataKinds.Phone.CONTENT_TYPE` returns a single phone row that Android grants temporary
  read access to. A "Type a number instead" fallback covers the rare device that refuses it.
- **If `SEND_SMS` is missing** nothing is recorded (so the text goes out once permission is
  granted) and a quiet, self-replacing notification explains what to do.

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
- **Alarm-style scheduling**: when exact alarms are allowed, slots are registered with
  `AlarmManager.setAlarmClock()` — the same API the phone's own Clock uses. It shows the system
  alarm icon, runs the reminder as a user-visible alarm (Doze-exempt) and allows the ringer
  foreground service to be started from the background, so the "foreground service blocked"
  fallback is rarely needed.

### Ringing through Silent / Do Not Disturb (like the phone's own alarm)

- The looping tone is rendered with `AudioAttributes.USAGE_ALARM` in `AlarmRingerService`, i.e.
  on the **alarm** stream. Silent mode only mutes the ringer and notification streams, and the
  system's Do Not Disturb "alarms" category is allowed by default, so a reminder is still heard.
  The audio-focus request uses the same alarm attributes.
- The alert notification uses the `dose_reminders_alarm` channel with `setBypassDnd(true)`, so
  the notification **and** the full-screen alert can interrupt DND too. The system only honours
  that while the app holds **Do Not Disturb access** (`ACCESS_NOTIFICATION_POLICY`), which the
  person grants from the Settings reminders card.
- Channel settings are immutable once created, so the DND-bypassing channel has a new id and
  `Notifications.ensureChannels` deletes the old `dose_reminders` channel.
- DND *Total silence* still blocks even alarms on most devices — a system-level policy no
  third-party app can override. The alarm-stream volume is respected as-is, exactly like the
  stock Clock.
- The alert sound has **no silent choice**: a stored legacy `"silent"` value is read back as the
  system default alarm tone (`AlertTone.parseAlertTone`).

### Notification → action flow

```
Reminder fires (exact alarm)
   └─ ReminderReceiver
        ├─ records the live alert in ActiveAlertRepository (DataStore)
        ├─ starts AlarmRingerService (foreground): loops the chosen alert tone on
        │     the ALARM stream, so it rings even in Silent / Do Not Disturb (like
        │     the phone's own Clock), + vibrates until the person answers — it
        │     does not stop on its own
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
             │     • clears the live alert
             │     • stops ringer, dismisses notification, and either shows the
             │       next pending alert or closes AlarmActivity
             └─ "Not now" → TrackingRepository.recordMuted()
                   • only this dose; MUTED event logged; stock unchanged
                   • stops the ringing — but the alert STAYS pending and the
                     full-screen alarm remains visible in a quiet, muted state
                     until "I took it" is confirmed

The full-screen alarm cannot be dismissed with the back button or "stop". It is
persisted, so it is brought back to the front whenever the app is reopened or the
notification is tapped — even after the process was killed, a reboot, or the user
pressing Home — until the dose is confirmed taken. Muting does not clear it: a
muted-but-untaken alert comes back in its calm state (no tone). Several pending
alerts are chained, oldest first: confirming one shows the next. If a foreground
service start is blocked, ReminderReceiver falls back to posting the same
full-screen notification (no looping tone in that rare case).
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
- **Settings → Alert sound** lets the person choose the reminder tone from the device's own
  alarms, ringtones and notifications (plus **Default**), preview it, and have it persisted for
  the looping ringer service. There is no silent choice: a reminder always rings, and it is
  played on the alarm stream so it is heard even when the phone is silent or in Do Not Disturb.
- **Settings → Language** pins the app's language (*Igual ao telemóvel*, *Português*, *English*)
  without leaving the app; it applies to every screen, notification and text.- 48dp+ touch targets, big labelled bottom navigation, high-contrast colour roles.
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
| `ACCESS_NOTIFICATION_POLICY` | let the alarm interrupt Do Not Disturb | special access, granted by the person as "Do Not Disturb access" (Settings links there). Without it the reminder still rings, but the alert may stay hidden while DND is on |
| `SEND_SMS` | optionally text the chosen family member when a medicine is running out | runtime-granted; only used after the person turns the feature on |
| `ACCESS_NETWORK_STATE` | added by `androidx.work`'s own manifest (not by app code) | normal permission; the app itself still never talks to the network |

No `INTERNET` permission — the app is fully offline. Refill texts never leave the phone except
as an SMS to the one contact the person chose.

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
- [ ] Settings → Text a family member: type a patient name (the example updates), choose a contact,
      allow sending texts, **Send a test text** (it should include the patient name).
- [ ] Edit a medicine's "text my family member when this many are left" and take doses until it
      reaches that level → one text; take one more to 0 → a second text; refill → no repeat for
      3 days.
- [ ] Add a medicine with a time 2 minutes ahead; background the app.
- [ ] Lock screen notification appears → tap **✓ I took it**:
      stock decremented, TAKEN logged, notification gone.
- [ ] Press **Home** during an active alarm, then reopen the app → the alarm
      screen comes back until **✓ I took it** is pressed.
- [ ] Tap the notification body → goes straight to the alarm screen (not the app).
- [ ] Force-stop the app while an alarm is pending, then relaunch → alarm returns.
- [ ] Repeat with **Not now**: MUTED logged, stock unchanged, ringing stops, and the
      alert returns (muted, silent) on reopen until taken.
- [ ] Two medicines due at the same time → confirming the first shows the second.
- [ ] Settings → Alert sound → choose a tone and press **Preview** (it stops on its own),
      then trigger a reminder → the chosen tone loops.
- [ ] Choose **Silent** → the reminder vibrates and shows the full screen with no tone.
- [ ] Choose **Default** → the system alarm tone plays. Reopen the app → the choice persisted.
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
