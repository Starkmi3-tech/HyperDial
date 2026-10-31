# HyperDial — design spec

Date: 2026-11-01
Status: draft, awaiting review

## 1. What we are building

A phone app for the Lava Agni 4 (LAVA LXX525, MediaTek mt6897, Android 16) that
looks and feels like the HyperOS dialer and can be set as the default Phone app,
so it also draws the incoming and in-call screens.

**What the user asked for**
- The HyperOS dialer on the Lava Agni 4.
- The full experience: dialing, recents, contacts and the in-call screen
  ("Full default phone app" was chosen over "Dialer UI only").
- Built for Lava, installed over the existing wireless adb connection.

**Assumptions (correct these if wrong)**
- "Looks like HyperOS" means the same layout, spacing, colors and feel. It does
  not mean Xiaomi's own code, icons or animations, which we cannot legally ship.
- The app is for personal use on this phone. It will not be published to a store.
- The Lava stock Phone app stays installed, so there is always a way back.

## 2. Non-goals

- No Xiaomi/HyperOS code, drawables, fonts or sounds copied into the app.
- No root, no bootloader unlock, no system-partition install. The bootloader is
  locked and has no unlock method, so the app must work as a normal user app.
- No new telephony features. Call handling stays exactly as upstream has it.
- No Play Store release, no "HyperOS" or "Xiaomi" in the app name or icon
  (Xiaomi trademarks).

## 3. Base: fork of Fossify Phone

| Item | Value |
|---|---|
| Upstream | `FossifyOrg/Phone`, branch `main`, commit `4b2a2f355397` (2026-10-31) |
| License | GPL-3.0 (our fork stays GPL-3.0, upstream copyright kept) |
| Language / UI | Kotlin, XML View layouts (no Compose) |
| Shared library | `org.fossify:commons` 7.1.3 (kept as a dependency, not forked) |
| Build | AGP 9.4.1, Kotlin 2.4.20, compileSdk 36, targetSdk 36, minSdk 26, Java 17 target |
| Build variant | flavor `foss`, so `assembleFossDebug` |

Why this base: it is already a working default Phone app with its own
`InCallService` (`services/CallService.kt`), in-call screen
(`activities/CallActivity.kt`), dialpad (`activities/DialpadActivity.kt`) and
Recents / Contacts / Favorites tabs. The risky telephony code is proven; we
only change how it looks.

## 4. Identity

- App name: **HyperDial**.
- `APP_ID` (in `gradle.properties`): `app.hyperdial`. Debug builds get the
  existing `.debug` suffix, so `app.hyperdial.debug` installs alongside anything.
- New launcher icon (original artwork, dialer handset on a rounded square).
- Fossify name and logo removed from user-facing places (app name, icon, about
  screen title). License file, copyright notices and an "based on Fossify Phone"
  credit in About stay.

## 5. Design language ("HyperOS-inspired")

All visual values live in one place so every screen uses the same tokens:
`app/src/main/res/values/hyper_tokens.xml` (colors, dimens) plus a
`values-night/hyper_tokens.xml` for dark mode, and `styles` that reference them.

Starting tokens (tuned on the phone during the Phase 1 checks in section 9):

| Token | Light | Dark |
|---|---|---|
| Background | `#F7F7F7` | `#000000` |
| Surface (cards, list groups) | `#FFFFFF` | `#1C1C1E` |
| Primary text | `#000000` | `#FFFFFF` |
| Secondary text | `#8A8A8E` | `#8D8D93` |
| Accent | `#0D84FF` | `#3D9BFF` |
| Call green | `#21C063` | `#21C063` |
| End-call red | `#F2453D` | `#F2453D` |
| In-call background | dark gradient `#2B2D33` to `#0E0F12` | same |

| Element | Starting value |
|---|---|
| Large page title | 32sp, bold, collapses to 18sp on scroll |
| Card / group corner radius | 20dp |
| List row height | 72dp, avatar 44dp circle |
| Dialpad key | 76dp circle, no border, digit 32sp light, letters 11sp secondary |
| Dialed number | 36sp, auto-shrinks to fit one line |
| Call button | 64dp circle, call green, centered |
| In-call action buttons | 3 by 2 grid, 64dp translucent white circles (12% alpha), label under |
| End-call button | 72dp circle, end-call red, centered at bottom |

Reference for these values: public HyperOS dialer screenshots, compared side by
side with our screens on the phone. REA (if it reconnects) or apktool may be
used to read layout measurements from a HyperOS dialer APK, but only as numbers
to match; nothing from that APK is copied into ours.

## 6. Screens and phases

### Phase 1 — browsing and dialing (no change to call handling)

| Screen | Upstream files restyled |
|---|---|
| Main shell: large title, tab bar (Recents, Contacts, Favorites), dialpad FAB | `activity_main.xml`, `MainActivity.kt` (view setup only) |
| Recents list | `fragment_recents.xml`, `item_recent_call.xml`, `item_recents_date.xml`, `adapters/RecentCallsAdapter.kt` (binding only) |
| Contacts list | `fragment_contacts.xml`, `fragment_letters_layout.xml`, `adapters/ContactsAdapter.kt` (binding only) |
| Favorites | `fragment_favorites.xml` |
| Dialpad | `activity_dialpad.xml`, `dialpad.xml`, `DialpadActivity.kt` (view setup only) |
| Dark mode | `values-night/hyper_tokens.xml` |

Phase 1 ends with HyperDial installed but **not** the default Phone app. Calls
placed from it go through the system, and the Lava in-call screen still shows.

### Phase 2 — default Phone app and in-call screens

| Screen | Upstream files restyled |
|---|---|
| Incoming call (answer / decline) | `activity_call.xml`, `CallActivity.kt` (view setup only) |
| Active call (timer, mute, keypad, speaker, add call, hold, end) | `activity_call.xml` |
| Call notification (heads-up while in another app) | `call_notification.xml` |
| Conference call | `activity_conference.xml`, `item_conference_call.xml`, `adapters/ConferenceCallsAdapter.kt` (binding only) |

Phase 2 ends with HyperDial set as the default Phone app.

## 7. Rules for changing code

- Restyle through resources first: layouts, drawables, `hyper_tokens.xml`,
  styles. Kotlin changes are allowed only in view setup and adapter binding.
- Do not edit telephony logic. These files stay as upstream has them:
  `services/CallService.kt`, `services/SimpleCallScreeningService.kt`,
  `helpers/CallManager.kt`, `helpers/CallContactHelper.kt`,
  `helpers/ToneGeneratorHelper.kt`, `receivers/*`.
- One exception: `helpers/CallNotificationManager.kt` fills
  `call_notification.xml`. In Phase 2 it may change only where it sets colors
  or view ids on that layout, never when or how the notification is posted.
- If a restyle seems to need any other change in those files, stop and raise
  it first.
- Keep an `upstream` git remote so upstream fixes can be merged later.

## 8. Build and install

- Project folder: `D:\build\hyperdial` (this repo). Upstream history is merged
  in, so the repo holds the whole app.
- Build on the laptop with the Gradle wrapper, JDK 21 (supported by AGP 9.4.1),
  Android SDK at `C:\Android` (platform 36 is installed).
  Command: `gradlew assembleFossDebug`.
- Install over the existing wireless adb link (`192.168.1.3`, port changes per
  session, found with `adb mdns services`):
  `adb -s <serial> install -r app-foss-debug.apk`.

## 9. Testing

**Every build**
- `gradlew assembleFossDebug` succeeds, `gradlew lintFossDebug` has no new errors.
- Install on the Agni 4; app opens without a crash (checked with `adb logcat`
  filtered to the app's process).

**Phase 1 (on the phone, screenshots taken over adb)**
- Each screen in light and dark mode, compared against the HyperOS reference.
- Place a call from the dialpad, from a Recents row and from a contact. Ending
  it on the Lava screen is fine in Phase 1.
- Search in Contacts and in the dialpad (T9) returns the right people.

**Phase 2 (needs the user and a second phone)**
Before switching the default: confirm how to switch back (Settings, Default
apps, Phone app, pick the Lava app) and that the Lava app is still installed.

| Test | Pass when |
|---|---|
| Outgoing call | Rings, connects, audio both ways, timer runs, ends cleanly |
| Incoming call, screen on | Answer and decline both work |
| Incoming call, screen locked | Full-screen incoming UI shows, answer works |
| Incoming call while in another app | Heads-up notification with answer / decline |
| Mute, speaker, keypad (DTMF tones heard by caller), hold | Each works and its button state is correct |
| Missed call | Shows in Recents and as a notification |
| Two calls (call waiting) and merge | Upstream behavior unchanged |
| Bluetooth headset (if available) | Audio routes to it |

Emergency calls are not test-dialed. Their path is upstream code we do not
touch.

**Done**
- Phase 1: all Phase 1 checks pass and the user accepts the look.
- Phase 2: the whole table passes, then two days of daily use with no missed or
  failed calls reported by the user.

## 10. Risks

| Risk | Handling |
|---|---|
| First Gradle build downloads AGP, Kotlin and dependencies (about 1 to 2 GB) | Expected; run in the background |
| `org.fossify:commons` applies its own theme colors at runtime | Override in our styles; if it repaints views in code, patch only the binding call |
| Lava firmware quirks with third-party in-call apps | Found by the Phase 2 table; the Lava app stays as fallback |
| Upstream changes conflict with the restyle | Resource-first changes keep conflicts small; merge upstream on demand |
| Look drifts from HyperOS | Single token file, side-by-side screenshot checks |
