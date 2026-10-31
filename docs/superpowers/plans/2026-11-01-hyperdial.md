# HyperDial Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fork Fossify Phone into HyperDial, restyle it to a HyperOS-inspired look, and make it the Lava Agni 4's default Phone app without touching call-handling code.

**Architecture:** Upstream Fossify Phone history is merged into `D:\build\hyperdial`. All colors and sizes come from one token file; the shared `org.fossify:commons` library is themed by overriding its `you_*` / `default_*` color resources, not by forking it. Screens are restyled through layouts and drawables, with Kotlin edits limited to view setup and adapter binding. A small Python helper (`tools/devicecheck.py`) turns on-device checks (screenshots, view bounds, pixel colors) into pass/fail commands.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1, XML Views, `org.fossify:commons` 7.1.3, Android SDK 36 (min 26), JDK 21, Gradle wrapper, adb over Wi-Fi, Python 3.12 via `uv` (pillow, pytest) for device checks.

**Spec:** `docs/superpowers/specs/2026-11-01-hyperdial-design.md`

## Global Constraints

- Upstream base: `FossifyOrg/Phone` commit `4b2a2f355397`; license stays GPL-3.0, upstream LICENSE and copyright kept.
- `APP_ID=app.hyperdial`; debug package `app.hyperdial.debug`; app name `HyperDial`.
- No Xiaomi/HyperOS code, drawables, fonts or sounds; "HyperOS" and "Xiaomi" never appear in app name, icon or store text.
- Never edit: `services/CallService.kt`, `services/SimpleCallScreeningService.kt`, `helpers/CallManager.kt`, `helpers/CallContactHelper.kt`, `helpers/ToneGeneratorHelper.kt`, `receivers/*`. `helpers/CallNotificationManager.kt` may change only colors / view ids it sets on `call_notification.xml`.
- Kotlin edits elsewhere: view setup and adapter binding only.
- Tokens (light / dark): background `#F7F7F7`/`#000000`, surface `#FFFFFF`/`#1C1C1E`, primary text `#000000`/`#FFFFFF`, secondary text `#8A8A8E`/`#8D8D93`, accent `#0D84FF`/`#3D9BFF`, call green `#21C063`, end red `#F2453D`, in-call gradient `#2B2D33`→`#0E0F12`.
- Sizes: title 32sp bold → 18sp collapsed; corner radius 20dp; list row 72dp, avatar 44dp; dialpad key 76dp circle, digit 32sp light, letters 11sp; dialed number 36sp auto-shrink single line; call button 64dp; in-call actions 3×2 grid of 64dp circles at 12% white; end-call 72dp.
- Build: `./gradlew assembleFossDebug -q`; APK at `app/build/outputs/apk/foss/debug/*.apk`. Every build step in every task also runs `./gradlew lintFossDebug -q`, which must pass against upstream's lint baseline.
- Device checks: `dc() { uv run -q --with pillow python tools/devicecheck.py "$@"; }` — written `dc ui ...`, `dc pixel ...` below.
- Device serial: `S=$(adb mdns services | awk '/_adb-tls-connect/{print $3}' | head -1)`; if `adb devices` lacks it, `adb connect $S`. All adb commands use `-s $S`.
- Keep shell output minimal: show only error lines and pass/fail results.

## Review Focus

1. Switching the phone between light and dark while HyperDial is open — every screen repaints with the matching tokens (Task 2, Task 7).
2. Long contact names and long numbers — names ellipsize in lists, dialed number shrinks to stay on one line (Task 4, Task 6).
3. System font scale 1.3 — dialpad digits and letters fit inside the 76dp keys without clipping (Task 6).
4. Dual-SIM indicator in Recents rows and on the call screen stays visible after restyling (Task 4, Task 8).
5. Incoming call on a locked screen shows the restyled full-screen UI and answering works (Task 8, Task 10).

---

## Phase 1 — browsing and dialing

### Task 1: Project setup, identity, device-check helper

**Files:**
- Create: `tools/devicecheck.py`, `tools/test_devicecheck.py`, `local.properties` (not committed), `README.md` (replace upstream's)
- Modify: `gradle.properties` (`APP_ID`), `app/src/main/res/values*/` files defining `app_name` / `app_launcher_name`, `app/src/main/res/mipmap-anydpi*/ic_launcher*.xml` and the foreground/background drawables they reference

**Interfaces:**
- Produces (Python, `tools/devicecheck.py`):
  - `pixel_matches(png_path: str, x: int, y: int, hex_color: str, tol: int = 8) -> bool` — each RGB channel within `tol`.
  - `parse_node(ui_xml: str, resource_id: str) -> dict | None` — first node whose `resource-id` ends with `:id/<resource_id>`; returns `{"text": str, "bounds": (l, t, r, b)}`.
  - `dp_to_px(dp: float, density_dpi: int) -> int` — `round(dp * density_dpi / 160)`.
  - CLI: `screenshot <serial> <out.png>`, `ui <serial> <resource_id>` (prints node JSON, exit 1 if missing), `pixel <png> <x> <y> <hex>` (exit 0/1), `density <serial>` (prints dpi).
  - `ui` uses `adb shell uiautomator dump /sdcard/hd.xml` then `adb exec-out cat /sdcard/hd.xml`; `density` parses `adb shell wm density` (override value wins over physical).

- [ ] **Step 1: Bring in upstream**

```bash
cd /d/build/hyperdial
git init -b main && git add docs && git commit -qm "docs: HyperDial spec and plan"
git remote add upstream https://github.com/FossifyOrg/Phone.git
git fetch -q upstream && git merge -q --allow-unrelated-histories -m "merge: Fossify Phone 4b2a2f355397" 4b2a2f355397
printf 'sdk.dir=C\\:\\\\Android\n' > local.properties
```
Expected: `git log --oneline | head -3` shows the merge commit; `local.properties` is ignored by upstream `.gitignore`.

- [ ] **Step 2: Baseline build before any change**

Run: `./gradlew assembleFossDebug -q` (first run downloads ~1–2 GB; run in background)
Expected: exit 0 and one APK under `app/build/outputs/apk/foss/debug/`.

- [ ] **Step 3: Write failing tests for the helper**

```python
# tools/test_devicecheck.py
def test_pixel_matches_exact(tmp_path):      # 4x4 PNG filled #21C063
    assert pixel_matches(png, 1, 1, "#21C063")
def test_pixel_mismatch_beyond_tol(tmp_path):
    assert not pixel_matches(png, 1, 1, "#21C073", tol=8)   # blue off by 16
def test_parse_node_bounds_text():
    xml = '<hierarchy><node resource-id="app.hyperdial.debug:id/main_large_title" text="Contacts" bounds="[0,120][1220,220]"/></hierarchy>'
    assert parse_node(xml, "main_large_title") == {"text": "Contacts", "bounds": (0, 120, 1220, 220)}
def test_parse_node_missing_returns_none():
    assert parse_node("<hierarchy/>", "nope") is None
def test_dp_to_px():
    assert dp_to_px(76, 480) == 228
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `uv run -q --with pillow --with pytest pytest tools -q`
Expected: FAIL with `ImportError` / `NameError` for `pixel_matches`.

- [ ] **Step 5: Implement the three functions and the CLI in `tools/devicecheck.py`**

Pillow for PNG reads, `xml.etree.ElementTree` for the dump, `subprocess.run` for adb.

- [ ] **Step 6: Run tests to verify they pass**

Run: `uv run -q --with pillow --with pytest pytest tools -q`
Expected: `5 passed`.

- [ ] **Step 7: Apply identity**

Set `APP_ID=app.hyperdial` in `gradle.properties`. Set every `app_name` and `app_launcher_name` value under `app/src/main/res/values*/` to `HyperDial` (find them with `grep -rl 'name="app_launcher_name"\|name="app_name"' app/src/main/res`). Replace the launcher icon with an original adaptive icon: white handset glyph on an accent `#0D84FF` rounded-square background (vector drawables). Replace `README.md` with a short description that says HyperDial is based on Fossify Phone, links upstream, and states GPL-3.0.

- [ ] **Step 8: Build, install, verify identity and launch**

```bash
./gradlew assembleFossDebug -q
APK=$(ls app/build/outputs/apk/foss/debug/*.apk); /c/Android/build-tools/36.0.0/aapt.exe dump badging "$APK" | grep -E "^package: name|application-label:"
adb -s $S install -r "$APK" && adb -s $S shell monkey -p app.hyperdial.debug -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3; adb -s $S logcat -d -b crash | grep -c app.hyperdial
```
Expected: `package: name='app.hyperdial.debug'`, `application-label:'HyperDial'`, crash count `0`.

- [ ] **Step 9: Commit**

```bash
git add tools gradle.properties app/src/main/res README.md
git commit -m "feat: HyperDial identity and device-check helper"
```

### Task 2: Design tokens and theme override

**Files:**
- Create: `app/src/main/res/values/hyper_tokens.xml`, `app/src/main/res/values-night/hyper_tokens.xml`, `app/src/main/res/values-v31/colors.xml`, `app/src/main/res/values-night-v31/colors.xml`
- Modify: `app/src/main/res/values/colors.xml` (add `default_*` overrides)

**Interfaces:**
- Produces color resources: `hyper_background`, `hyper_surface`, `hyper_text_primary`, `hyper_text_secondary`, `hyper_accent`, `hyper_call_green`, `hyper_end_red`, `hyper_incall_top`, `hyper_incall_bottom`, `hyper_action_bg` (`#1FFFFFFF`).
- Produces dimens: `hyper_title_large` 32sp, `hyper_title_small` 18sp, `hyper_radius` 20dp, `hyper_row_height` 72dp, `hyper_avatar` 44dp, `hyper_key_size` 76dp, `hyper_key_digit` 32sp, `hyper_key_letters` 11sp, `hyper_dialed_number` 36sp, `hyper_call_button` 64dp, `hyper_action_size` 64dp, `hyper_end_button` 72dp.
- Produces drawables: `hyper_group_bg.xml` (surface, 20dp corners), `hyper_circle.xml` (oval, tinted by caller).
- In `values-v31` / `values-night-v31`: `you_background_color` → `@color/hyper_background`, `you_neutral_text_color` → `@color/hyper_text_primary`, `you_primary_color` and `you_primary_dark_color` → `@color/hyper_accent`, `you_status_bar_color` → `@color/hyper_background`.
- In `values/colors.xml`: `default_background_color`, `default_text_color`, `default_primary_color`, `default_accent_color`, `default_app_icon_color` → the light hyper tokens.

- [ ] **Step 1: Write the failing check**

```bash
APK=$(ls app/build/outputs/apk/foss/debug/*.apk)
/c/Android/build-tools/36.0.0/aapt.exe dump --values resources "$APK" | grep -A3 "color/you_background_color" | grep -c "0xfff7f7f7\|0xff000000"
```
Expected now: `0` (still Material You values).

- [ ] **Step 2: Create the four resource files and the `default_*` overrides with the values above**

- [ ] **Step 3: Rebuild and re-run the check**

Expected: `2` (light `0xfff7f7f7` and night `0xff000000`).

- [ ] **Step 4: Verify on the phone in both modes**

```bash
adb -s $S install -r "$APK"; for m in no yes; do adb -s $S shell cmd uimode night $m; adb -s $S shell monkey -p app.hyperdial.debug -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 2
uv run -q --with pillow python tools/devicecheck.py screenshot $S /tmp/t2_$m.png; done
uv run -q --with pillow python tools/devicecheck.py pixel /tmp/t2_no.png 600 1400 "#F7F7F7"
uv run -q --with pillow python tools/devicecheck.py pixel /tmp/t2_yes.png 600 1400 "#000000"
adb -s $S shell cmd uimode night auto
```
Expected: both `pixel` calls exit 0 (pick an empty background point if 600,1400 lands on a list row).

- [ ] **Step 5: Commit** — `git commit -am "feat: HyperOS-style color and size tokens"` (plus `git add` for new files).

### Task 3: Main shell — large title, tabs, dialpad button

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`, `app/src/main/kotlin/org/fossify/phone/activities/MainActivity.kt` (view setup only)

**Interfaces:**
- Consumes: Task 2 tokens.
- Produces: view `main_large_title` (TextView) inside an `AppBarLayout` above `main_tabs_holder`; its text is the selected tab's label; it scales from `hyper_title_large` to `hyper_title_small` as the list scrolls (driven by `AppBarLayout.OnOffsetChangedListener`). `main_dialpad_button` becomes a 64dp `hyper_accent` circle.

- [ ] **Step 1: Failing check** — `dc ui $S main_large_title` → exit 1 (view missing).
- [ ] **Step 2: Add the title and restyle tabs/FAB; set title text on tab change in `MainActivity` view setup.**
- [ ] **Step 3: Build, install, re-run** — node exists, `text` equals the selected tab's label; bottom of bounds minus top ≥ `dp_to_px(32, density)`.
- [ ] **Step 4: Scroll check** — `adb -s $S shell input swipe 600 2000 600 600 300`, re-dump: title height now < the Step 3 height.
- [ ] **Step 5: Commit** — `feat: large collapsing title on main screen`.

### Task 4: Recents list

**Files:**
- Modify: `res/layout/item_recent_call.xml`, `res/layout/item_recents_date.xml`, `res/layout/fragment_recents.xml`, `kotlin/.../adapters/RecentCallsAdapter.kt` (binding only)

**Interfaces:**
- Consumes: `hyper_row_height`, `hyper_avatar`, `hyper_group_bg`, text tokens.
- Produces: `item_recents_holder` height = 72dp, `item_recents_image` 44dp circle, rows of one date group drawn on `hyper_group_bg` (first/last row rounded), `item_recents_name` single line with end ellipsis, `item_recents_sim_image` / `item_recents_sim_id` unchanged in visibility logic.

- [ ] **Step 1: Failing check** — `dc ui $S item_recents_holder`; height ≠ `dp_to_px(72, density)`.
- [ ] **Step 2: Restyle the layouts; adapter only picks which background (top/middle/bottom/single) a row gets.**
- [ ] **Step 3: Build, install, re-run** — holder height == 72dp px; `item_recents_image` width == `dp_to_px(44, density)`.
- [ ] **Step 4: Long-name check** — find a Recents row with a name longer than the row (or save a test contact "Abcdefghijklmnopqrstuvwxyz Abcdefghijklmnop"), screenshot: name ends with "…" and does not overlap `item_recents_date_time`.
- [ ] **Step 5: Dual-SIM check** — if `adb -s $S shell getprop gsm.sim.state` shows two SIMs, a row's `item_recents_sim_image` node exists after restyle.
- [ ] **Step 6: Commit** — `feat: HyperOS-style recents list`.

### Task 5: Contacts and Favorites

**Files:**
- Modify: `res/layout/fragment_contacts.xml`, `res/layout/fragment_letters_layout.xml`, `res/layout/fragment_favorites.xml`, `kotlin/.../adapters/ContactsAdapter.kt` (binding only)

**Interfaces:**
- Consumes: same tokens as Task 4; same top/middle/bottom background choice as `RecentCallsAdapter`.

- [ ] **Step 1: Failing check** — contact row height (dump the row's root id from `ContactsAdapter`'s item layout) ≠ 72dp px.
- [ ] **Step 2: Restyle; letter fast-scroller uses `hyper_accent`.**
- [ ] **Step 3: Build, install, re-run** — row 72dp, avatar 44dp; Favorites tab shows the same row style.
- [ ] **Step 4: Search check** — type a known contact's first 3 letters in the search menu; that contact is the first row.
- [ ] **Step 5: Commit** — `feat: HyperOS-style contacts and favorites`.

### Task 6: Dialpad

**Files:**
- Modify: `res/layout/dialpad.xml`, `res/layout/activity_dialpad.xml`, `kotlin/.../activities/DialpadActivity.kt` (view setup only)

**Interfaces:**
- Consumes: `hyper_key_*`, `hyper_dialed_number`, `hyper_call_button`, `hyper_call_green`, `hyper_circle`.
- Produces: each `dialpad_N_holder` a 76dp borderless circle (pressed state ripple only); digits 32sp light; `dialpad_N_letters` 11sp secondary; `dialpad_input` 36sp, single line, `autoSizeTextType="uniform"` min 20sp; `dialpad_call_button` 64dp `hyper_call_green` circle centered under the keys.

- [ ] **Step 1: Failing check** — `dc ui $S dialpad_1_holder` width ≠ `dp_to_px(76, density)`.
- [ ] **Step 2: Restyle the two layouts; any size the activity sets in code switches to the new dimens.**
- [ ] **Step 3: Build, install, re-run** — key width and height == 76dp px; `dialpad_call_button` width == 64dp px; center pixel of the call button matches `#21C063`.
- [ ] **Step 4: Long-number check** — `adb -s $S shell input text 98765432109876543210`; `dialpad_input` node is one line high (height < 2 × `dp_to_px(36, density)`).
- [ ] **Step 5: Font-scale check** — `adb -s $S shell settings put system font_scale 1.3`, reopen dialpad, screenshot: no digit or letter cut off by its key; then `settings put system font_scale 1.0`.
- [ ] **Step 6: T9 check** — type the digits of a known contact's name; that contact appears in `dialpad_list`.
- [ ] **Step 7: Commit** — `feat: HyperOS-style dialpad`.

### Task 7: Phase 1 acceptance (with the user)

**Files:**
- Modify: `res/values*/hyper_tokens.xml` only, if the user asks for tuning.

- [ ] **Step 1: Light/dark sweep** — screenshot Recents, Contacts, Favorites, Dialpad in `uimode night no` and `yes`; send all 8 to the user (SendUserFile) next to public HyperOS dialer screenshots.
- [ ] **Step 2: Live dark switch** — with HyperDial open on Recents, run `cmd uimode night yes`: background pixel matches `#000000` within 2 s without relaunching by hand.
- [ ] **Step 3: Real calls (user picks the number to call)** — place one call each from the dialpad, a Recents row and a contact; each reaches the Lava in-call screen. User hangs up.
- [ ] **Step 4: Tune tokens from the user's feedback, rebuild, repeat Step 1 until the user accepts the look.**
- [ ] **Step 5: Commit** — `chore: tune tokens after phase 1 review`, then tag `phase1`.

---

## Phase 2 — in-call screens and default Phone app

Test calls for Tasks 8–9 use an emulator when one exists: `C:\Android\emulator\emulator.exe -list-avds`; start an API 36 AVD with `run_in_background`, install the APK there, set HyperDial as default Phone app on the emulator, and simulate calls with `adb -s emulator-5554 emu gsm call 5551234` / `emu gsm accept 5551234` / `emu gsm cancel 5551234`. Without an AVD, the user calls the Agni 4 from a second phone.

### Task 8: Incoming and active call screen

**Files:**
- Modify: `res/layout/activity_call.xml`, `kotlin/.../activities/CallActivity.kt` (view setup only); create `res/drawable/hyper_incall_bg.xml` (vertical gradient `hyper_incall_top` → `hyper_incall_bottom`)

**Interfaces:**
- Consumes: `hyper_action_*`, `hyper_end_*`, `hyper_call_green`, `hyper_end_red`, `hyper_circle`.
- Produces: `call_holder` background `hyper_incall_bg`; `caller_name_label` large white, `call_status_label` (timer) secondary under it; `button_grid` lays out `call_toggle_microphone`, `call_dialpad`, `call_toggle_speaker`, `call_add`, `call_toggle_hold`, `call_manage` as a 3×2 grid of 64dp `hyper_action_bg` circles with labels; `call_end` 72dp `hyper_end_red`; `call_accept` green / `call_decline` red with the upstream drag behavior kept; `call_sim_image` / `call_sim_id` visibility logic unchanged.

- [ ] **Step 1: Failing check** — simulate an incoming call, accept it, dump: `call_end` width ≠ `dp_to_px(72, density)`.
- [ ] **Step 2: Restyle; `CallActivity` changes limited to colors/sizes it sets on these views.**
- [ ] **Step 3: Build, install, simulate incoming call** — screenshot shows red/green accept–decline; accept works.
- [ ] **Step 4: Active-call checks** — `call_end` 72dp px; each of the six action buttons 64dp px; tapping mute and speaker toggles their state (node `selected`/`checked` changes or icon changes in screenshot); timer text changes over 3 s.
- [ ] **Step 5: Locked-screen check** — lock (`input keyevent 26`), simulate a call: full-screen restyled incoming UI appears; accept works.
- [ ] **Step 6: Verify telephony files untouched** — `git diff --stat phase1 -- app/src/main/kotlin/org/fossify/phone/services app/src/main/kotlin/org/fossify/phone/receivers app/src/main/kotlin/org/fossify/phone/helpers/CallManager.kt app/src/main/kotlin/org/fossify/phone/helpers/CallContactHelper.kt app/src/main/kotlin/org/fossify/phone/helpers/ToneGeneratorHelper.kt` prints nothing.
- [ ] **Step 7: Commit** — `feat: HyperOS-style call screen`.

### Task 9: Call notification and conference screen

**Files:**
- Modify: `res/layout/call_notification.xml`, `kotlin/.../helpers/CallNotificationManager.kt` (colors / view ids only), `res/layout/activity_conference.xml`, `res/layout/item_conference_call.xml`, `kotlin/.../adapters/ConferenceCallsAdapter.kt` (binding only)

- [ ] **Step 1: Failing check** — during a simulated call, press Home; screenshot the notification shade: answer/decline buttons are not yet `hyper_call_green` / `hyper_end_red`.
- [ ] **Step 2: Restyle notification layout and conference screens.**
- [ ] **Step 3: Build, install, re-run** — notification buttons sample to `#21C063` / `#F2453D`; on the emulator, two simulated calls merged show the conference screen with `hyper_group_bg` rows.
- [ ] **Step 4: Diff guard** — `git diff phase1 -- app/src/main/kotlin/org/fossify/phone/helpers/CallNotificationManager.kt` touches only color / view-id lines (reviewer reads it).
- [ ] **Step 5: Commit** — `feat: HyperOS-style call notification and conference`.

### Task 10: Default Phone app and real-call acceptance (with the user)

- [ ] **Step 1: Rollback ready** — confirm the Lava Phone app is installed (`adb -s $S shell cmd role get-role-holders android.app.role.DIALER` before switching shows it); tell the user the way back: Settings › Apps › Default apps › Phone app › Lava Phone.
- [ ] **Step 2: Switch** — open HyperDial and accept its "set as default" prompt on the phone (user taps). Check: `get-role-holders android.app.role.DIALER` prints `app.hyperdial.debug`.
- [ ] **Step 3: Run the spec's Phase 2 table with the user and a second phone** — outgoing, incoming screen on, incoming locked, incoming while in another app, mute / speaker / keypad DTMF / hold, missed call, call waiting + merge, Bluetooth if available. Record pass/fail per row; any fail → switch back to Lava Phone, fix, repeat the failed rows.
- [ ] **Step 4: Two-day daily use** — user reports no missed or failed calls. Then tag `phase2`.

No emergency number is dialed in any test.
