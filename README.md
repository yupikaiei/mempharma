# MemPharma

A friendly, modern Android app that helps elderly people **remember and track their medicine**.

Every dose reminder arrives as a clear, high-priority notification with two big actions — **"✓ I took it"** and **"Not now" (mute)**. Both actions are recorded locally with timestamps, along with stock (quantity) and the date the medicine was started, so everything can be traced back later. No account, no internet, no cloud — **all data stays on the device**.

> 🎯 Design goal: as simple as possible to use (huge buttons, high contrast, big text),
> while still feeling modern. Built natively with **Kotlin + Jetpack Compose (Material 3)**.

---

## Features

| | |
|---|---|
| 💊 **Multiple medicines** | each with its own colour, dose size, stock and schedule |
| 🔔 **Reminders** | exact-time alarms that work even when the app is closed |
| ✅ **"I took it"** | decrements the remaining stock, logs the event, dismisses the reminder |
| 🔕 **"Not now" (mute)** | silences *only that dose*, logs it as muted — no nagging, no penalty |
| 📊 **Stock tracking** | see pills remaining; amber "only N left" and red "refill needed" states |
| 📅 **Started-date tracking** | every medicine records when treatment began |
| 🗂️ **History & audit log** | every taken / muted / missed / refill event with timestamps |
| 📤 **CSV export** | share the full trace (medicines + start dates + event log) via the share sheet |
| 🔠 **Accessible by default** | large type, 48dp+ touch targets, TalkBack friendly, adjustable text size |
| 📲 **Easy installs** | every push to `main` produces a signed APK on GitHub Releases |

## Technology

- **Kotlin 2.x**, **Jetpack Compose + Material 3**, warm Material-themed UI
- **Jetpack Architecture**: single-activity, MVVM, `StateFlow` + `Flow`
- **Room** (SQLite) — local persistence with an append-only audit log
- **Hilt** — dependency injection
- **AlarmManager** exact alarms + `BroadcastReceiver`s (reminders, actions, boot/time re-arm)
- **Jetpack DataStore** — accessibility settings (font scale)
- **Gradle version catalog** (`gradle/libs.versions.toml`) for dependency management
- CI: **GitHub Actions** builds + publishes signed APKs

**Supported**: Android 8.0+ (`minSdk 26`), targets the latest Android.

## Repository layout

```
app/src/main/java/com/mempharma/app/
├── data/
│   ├── local/           Room entities, DAOs, database
│   ├── repo/            Medication + Tracking (audit) repositories
│   ├── scheduler/       alarms, reminder/action/boot/time receivers, notifications
│   └── settings/        DataStore-backed settings
├── domain/              pure, testable dose-engine logic
├── di/                  Hilt modules + receiver entry point
├── ui/                  Compose screens (theme, home, meds, edit, history, settings)
└── util/                time formatting + CSV export
```

## Getting started (developers)

1. **Prereqs**: JDK 17, Android Studio (or an Android SDK on your machine).
2. Open the folder in Android Studio and let Gradle sync (wrapper is included).
3. Run the **`app`** configuration, or build from the terminal:

   ```bash
   ./gradlew :app:assembleDebug        # debug APK
   ./gradlew :app:testDebugUnitTest    # JVM unit tests
   ./gradlew :app:assembleRelease      # minified release APK (uses debug key locally)
   ```

No signing secrets are required for local builds — `release` falls back to the debug keystore.
See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for architecture notes, permissions, and the release pipeline.

## Installing on your phone

The easiest path is the **GitHub Releases** page of this repository:

1. Open the **latest release** (from the push you want) and tap `app-release.apk` — or scan the
   attached QR code with your phone's camera.
2. Android will ask to allow installs "from this source" — allow it once.
3. The app installs. Later builds install **as updates** (same signing key), so you never lose data.

> 🔒 **Privacy**: everything is stored locally on the device. There is no account, no analytics,
> and no network permission in the app.

---

*Made with care for easier, safer medicine taking.*