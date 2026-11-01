# HyperDial Call Recording Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add automatic mic-only call recording to HyperDial, with recordings kept on the phone and uploaded to the user's Google Drive on Wi-Fi, without touching any telephony code.

**Architecture:** A `PHONE_STATE` broadcast receiver starts a microphone foreground service when a call goes active and stops it on hang-up; the service records through a pluggable `AudioSource` (mic now, both-sides later) to a local `.m4a` and writes a `Recording` row to a JSON-backed store. A WorkManager job uploads pending recordings to Drive (unmetered-network constraint) through a `DriveClient` built on Google Sign-In + the Drive REST API. A Recordings screen lists and plays them; settings hold the master toggle and sign-in.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1, XML Views, `org.fossify:commons` 7.1.3, Android SDK 36 (min 26), JDK 21. Added libs: `androidx.work:work-runtime-ktx:2.9.1`, `com.google.android.gms:play-services-auth:21.2.0`, `com.squareup.okhttp3:okhttp:4.12.0`. JSON via Android's built-in `org.json`. Python 3.12 via `uv` (pytest) only where a pure check helps; device checks over adb; `ffmpeg`/`ffprobe` for amplitude.

**Spec:** `docs/superpowers/specs/2026-11-01-hyperdial-recording-design.md`

## Global Constraints

- All new code lives in a new package `app/src/main/kotlin/org/fossify/phone/recording/`. Do NOT edit telephony files: `services/CallService.kt`, `services/SimpleCallScreeningService.kt`, `helpers/CallManager.kt`, `helpers/CallContactHelper.kt`, `helpers/ToneGeneratorHelper.kt`, `receivers/*`.
- Source: **mic-only** this plan; keep the `AudioSource` interface the only unit that knows how audio is captured, so a both-sides impl drops in later.
- Storage: keep local copies; upload to Drive **only on unmetered (Wi-Fi)** networks; record **all** calls behind a master toggle (`Config.recordCalls`, default false until the user enables it).
- Drive scope is exactly `https://www.googleapis.com/auth/drive.file` — never a broader scope. Upload target is a Drive folder named `HyperDial Recordings`.
- No announcement/consent tone is played.
- License stays GPL-3.0; keep the `upstream` remote mergeable (isolated package + minimal manifest additions).
- Audio: AAC in `.m4a` via `MediaRecorder` (`MPEG_4`/`AAC`), mono, 64 kbps. `MicAudioSource` tries audio sources in order `VOICE_COMMUNICATION` → `VOICE_RECOGNITION` → `MIC`, first that initializes.
- Build: `./gradlew assembleFossDebug -q`; also `./gradlew lintFossDebug -q` must pass the upstream baseline. Unit tests: `./gradlew :app:testFossDebugUnitTest -q` (JVM unit tests under `app/src/test/`).
- Device serial: `S=$(adb mdns services | awk '/_adb-tls-connect/{print $3}' | head -1)`; if `adb devices` lacks it, `adb connect $S`. All adb commands use `-s $S`.
- Keep shell output minimal: show only error lines and pass/fail results.
- No emergency number is dialed in any test; the user picks the number for real-call tests.

## Review Focus

1. **In-call mic muted** (the central risk): on this OEM the recorder may capture silence. Task 8 makes a real call and checks the file is non-silent; a silent result is the finding that routes us to the both-sides path, not a bug to patch.
2. **Microphone foreground-service start from the background** (Android 14+): starting a `microphone`-typed FGS from the `PHONE_STATE` receiver may be blocked. Task 3's device step confirms the service actually starts and holds the mic during a call.
3. **Missed/declined call** (RINGING → IDLE with no OFFHOOK): no recording file is created. Task 4 test `maps_ringing_then_idle_to_no_recording` pins it.
4. **Upload with no signed-in account**: recordings stay `PENDING`, nothing crashes, a sign-in banner shows. Task 6 test `worker_without_account_keeps_pending` pins it.
5. **No Wi-Fi at hang-up, Wi-Fi later**: the job waits on the unmetered constraint and drains when Wi-Fi returns. Task 6 test `enqueue_uses_unmetered_constraint` plus Task 8 device step pin it.

---

### Task 1: Recording model, store, and naming

**Files:**
- Create: `app/src/main/kotlin/org/fossify/phone/recording/Recording.kt`, `recording/RecordingStore.kt`, `recording/RecordingNaming.kt`
- Test: `app/src/test/kotlin/org/fossify/phone/recording/RecordingStoreTest.kt`, `recording/RecordingNamingTest.kt`

**Interfaces:**
- Produces:
  - `enum class UploadState { PENDING, UPLOADING, UPLOADED, FAILED }`
  - `data class Recording(val id: String, val filePath: String, val number: String, val name: String, val startedAt: Long, val durationMs: Long, val uploadState: UploadState, val driveId: String? = null)`
  - `class RecordingStore(private val dir: File)` with `fun add(r: Recording)`, `fun all(): List<Recording>` (newest first by `startedAt`), `fun update(id: String, transform: (Recording) -> Recording)`, `fun delete(id: String)`. Persists to `File(dir, "recordings.json")` as a JSON array via `org.json`.
  - `object RecordingNaming` with `fun fileName(number: String, startedAt: Long): String` → `"<sanitized-number>_<yyyyMMdd-HHmmss>.m4a"`, unknown/empty number → `"unknown"`, non-digit characters stripped except leading `+`.

- [ ] **Step 1: Write failing tests**

```kotlin
// RecordingNamingTest
@Test fun builds_name_from_number_and_time() // number "+91 90123", epoch → "+9190123_20261101-120000.m4a"
@Test fun unknown_number_becomes_unknown()    // "" → starts with "unknown_"
// RecordingStoreTest  (uses @TempDir)
@Test fun add_then_all_returns_newest_first()
@Test fun update_changes_upload_state()
@Test fun delete_removes_row()
@Test fun survives_reload_from_disk()          // new RecordingStore(dir) sees prior rows
```

- [ ] **Step 2: Run tests, verify they fail** — `./gradlew :app:testFossDebugUnitTest --tests '*recording*' -q`; Expected: FAIL (unresolved `Recording`/`RecordingStore`/`RecordingNaming`).
- [ ] **Step 3: Implement the model, `RecordingStore` (org.json read/write), and `RecordingNaming`** in the three files. Use `SimpleDateFormat("yyyyMMdd-HHmmss")` with the default locale/zone.
- [ ] **Step 4: Run tests, verify they pass** — same command; Expected: all pass.
- [ ] **Step 5: Commit** — `git add app/src/main/kotlin/org/fossify/phone/recording app/src/test/kotlin/org/fossify/phone/recording && git commit -m "feat: recording model, store, and naming"`.

### Task 2: Audio source abstraction and MicAudioSource

**Files:**
- Create: `recording/AudioSource.kt`, `recording/MicAudioSource.kt`
- Test: `app/src/test/kotlin/org/fossify/phone/recording/AudioSourceSelectionTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `interface AudioSource { fun start(outputFile: File); fun stop(); }`
  - `object MicSourceOrder { val candidates: List<Int> }` = `[MediaRecorder.AudioSource.VOICE_COMMUNICATION, VOICE_RECOGNITION, MIC]`, and `fun firstUsable(canUse: (Int) -> Boolean): Int?` returning the first candidate for which `canUse` is true, else null.
  - `class MicAudioSource : AudioSource` — wraps `MediaRecorder` (`MPEG_4`/`AAC`, mono, 64 kbps), picking the source via `MicSourceOrder.firstUsable { tryPrepare(it) }`. `MediaRecorder` interaction is device-verified, not unit-tested.

- [ ] **Step 1: Write failing test** — `AudioSourceSelectionTest`:

```kotlin
@Test fun picks_first_candidate_that_can_be_used()   // canUse = { it == VOICE_RECOGNITION } → VOICE_RECOGNITION
@Test fun returns_null_when_none_usable()             // canUse = { false } → null
@Test fun prefers_voice_communication_when_all_usable()
```

- [ ] **Step 2: Run test, verify it fails** — Expected: FAIL (unresolved `MicSourceOrder`).
- [ ] **Step 3: Implement `AudioSource`, `MicSourceOrder`, and `MicAudioSource`.** The selection logic in `MicSourceOrder` is the only pure part; `MicAudioSource.start/stop` drive `MediaRecorder`.
- [ ] **Step 4: Run test, verify it passes.**
- [ ] **Step 5: Commit** — `feat: pluggable audio source with mic implementation`.

### Task 3: CallRecordingService (microphone foreground service)

**Files:**
- Create: `recording/CallRecordingService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (service + FGS permissions), `app/src/main/res/values/strings.xml` (notification text)
- Test: `app/src/test/kotlin/org/fossify/phone/recording/RecordingSessionTest.kt`

**Interfaces:**
- Consumes: `AudioSource`/`MicAudioSource` (Task 2), `RecordingStore`/`Recording`/`RecordingNaming` (Task 1).
- Produces:
  - Actions `CallRecordingService.ACTION_START` / `ACTION_STOP`; `fun startFor(context: Context)` / `fun stopFor(context: Context)` helpers that `startForegroundService` / send stop.
  - `class RecordingSession(private val store: RecordingStore, private val clock: () -> Long)` — the testable core: `fun begin(number: String, name: String, outputDir: File): File` (returns the output file, records `startedAt`), `fun end(file: File, durationMs: Long)` (writes a `Recording` with `UploadState.PENDING`). The service wires `RecordingSession` to the `AudioSource` and the foreground notification.

- [ ] **Step 1: Write failing test** — `RecordingSessionTest`:

```kotlin
@Test fun begin_returns_named_file_in_dir()            // file name matches RecordingNaming, parent == dir
@Test fun end_writes_pending_recording_with_duration() // store.all() has 1 row, PENDING, durationMs set
```

- [ ] **Step 2: Run test, verify it fails** — Expected: FAIL (unresolved `RecordingSession`).
- [ ] **Step 3: Implement `RecordingSession`, then `CallRecordingService`** (foreground service, `foregroundServiceType="microphone"`, a low-priority ongoing notification "Recording call"); on `ACTION_START` begin the session + `MicAudioSource.start`, on `ACTION_STOP` stop the source, call `session.end`, enqueue upload (Task 6 helper — until it exists, leave a single call site `DriveUploader.enqueue(context)` added in Task 6), `stopSelf`. Register the service and `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` in the manifest.
- [ ] **Step 4: Run test, verify it passes.**
- [ ] **Step 5: Build + device check** — `./gradlew assembleFossDebug -q`; install; `adb -s $S shell am start-foreground-service -n app.hyperdial.debug/org.fossify.phone.recording.CallRecordingService -a <ACTION_START>` then confirm the ongoing notification and, after `ACTION_STOP`, one `.m4a` under the app files dir (`adb -s $S shell run-as app.hyperdial.debug ls files/recordings`). Expected: service runs foreground, file created. (If the OS refuses to start the mic FGS — Review Focus 2 — record the finding.)
- [ ] **Step 6: Commit** — `feat: call recording foreground service`.

### Task 4: CallStateWatcher (PHONE_STATE receiver)

**Files:**
- Create: `recording/CallStateWatcher.kt`
- Modify: `app/src/main/AndroidManifest.xml` (receiver for `android.intent.action.PHONE_STATE`)
- Test: `app/src/test/kotlin/org/fossify/phone/recording/CallStateMachineTest.kt`

**Interfaces:**
- Consumes: `CallRecordingService.startFor/stopFor` (Task 3), `Config.recordCalls`.
- Produces:
  - `enum class CallAction { START, STOP, NONE }`
  - `class CallStateMachine` with `fun onState(state: String, recordingEnabled: Boolean): CallAction` — OFFHOOK + enabled → START; IDLE after a START → STOP; RINGING → NONE; IDLE with no prior START → NONE; any state while disabled → NONE. Holds the "currently recording" flag internally.
  - `class CallStateWatcher : BroadcastReceiver` that owns one `CallStateMachine` and calls the service helpers.

- [ ] **Step 1: Write failing test** — `CallStateMachineTest`:

```kotlin
@Test fun maps_offhook_to_start_when_enabled()
@Test fun maps_offhook_to_none_when_disabled()
@Test fun maps_offhook_then_idle_to_start_then_stop()
@Test fun maps_ringing_then_idle_to_no_recording()   // RINGING→NONE, IDLE→NONE (Review Focus 3)
```

- [ ] **Step 2: Run test, verify it fails** — Expected: FAIL (unresolved `CallStateMachine`).
- [ ] **Step 3: Implement `CallStateMachine` and `CallStateWatcher`** (read `EXTRA_STATE`; gate on `config.recordCalls`). Register the receiver in the manifest for `PHONE_STATE`.
- [ ] **Step 4: Run test, verify it passes.**
- [ ] **Step 5: Build + device check** — install; with recording enabled make/drop a quick in-app state change is not possible headlessly, so verify via a real short call in Task 8. Here only confirm `assembleFossDebug`/`lintFossDebug` pass and the receiver is registered (`aapt dump xmltree` shows the `PHONE_STATE` intent filter).
- [ ] **Step 6: Commit** — `feat: phone-state watcher drives recording`.

### Task 5: DriveClient (Google Sign-In + Drive REST)

**Files:**
- Create: `recording/DriveClient.kt`, `recording/DriveApi.kt`
- Modify: `app/build.gradle.kts` (add `play-services-auth`, `okhttp`)
- Test: `app/src/test/kotlin/org/fossify/phone/recording/DriveApiTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `object DriveApi` (pure request shaping, unit-tested): `fun folderQuery(name: String): String` → the Drive `q` for `mimeType='application/vnd.google-apps.folder' and name='<name>' and trashed=false`; `fun createFolderBody(name: String): String` (JSON); `fun uploadMetadata(name: String, parentId: String): String` (JSON with `name` and `parents`).
  - `class DriveClient(private val context: Context, private val http: OkHttpClient)` with `suspend fun hasAccount(): Boolean`, `suspend fun ensureFolder(): String` (returns folder id, creating `HyperDial Recordings` if absent), `suspend fun upload(file: File, displayName: String): String` (multipart upload to `https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart`, returns Drive file id). Auth: `GoogleSignIn.getLastSignedInAccount` + `GoogleAuthUtil.getToken(context, account.account, "oauth2:https://www.googleapis.com/auth/drive.file")`. `startSignIn(activity)` builds a `GoogleSignInClient` requesting `Scope(DRIVE_FILE)`.

- [ ] **Step 1: Write failing test** — `DriveApiTest`:

```kotlin
@Test fun folder_query_matches_name_and_type()
@Test fun create_folder_body_has_folder_mime()
@Test fun upload_metadata_sets_name_and_parent()
```

- [ ] **Step 2: Run test, verify it fails** — Expected: FAIL (unresolved `DriveApi`).
- [ ] **Step 3: Add the two dependencies; implement `DriveApi` (pure) and `DriveClient`** (OkHttp calls; token via `GoogleAuthUtil`). Keep all network I/O in `DriveClient`; `DriveApi` only builds strings.
- [ ] **Step 4: Run test, verify it passes.**
- [ ] **Step 5: OAuth prerequisite (with the user)** — read the debug keystore SHA-1 (`keytool -list -v -keystore ~/.android/debug.keystore -storepass android -keypass android | grep SHA1`); guide the user to create the Google Cloud project, enable Drive API, set OAuth consent to Testing with their account, and create an Android OAuth client for `app.hyperdial.debug` + that SHA-1. Record completion in the ledger. (No app code depends on a client-secret file for installed-app Google Sign-In.)
- [ ] **Step 6: Commit** — `feat: Google Drive client with drive.file scope`.

### Task 6: DriveUploader (WorkManager, Wi-Fi only)

**Files:**
- Create: `recording/DriveUploader.kt`
- Modify: `app/build.gradle.kts` (add `work-runtime-ktx`), `AndroidManifest.xml` (`INTERNET`, `ACCESS_NETWORK_STATE`)
- Test: `app/src/test/kotlin/org/fossify/phone/recording/DriveUploaderTest.kt`

**Interfaces:**
- Consumes: `RecordingStore`/`UploadState` (Task 1), `DriveClient` (Task 5).
- Produces:
  - `class DriveUploader(ctx, params) : CoroutineWorker` whose `doWork()` loads `PENDING`/`FAILED` rows, and for each: if `!DriveClient.hasAccount()` leaves it `PENDING` and returns `Result.success()` (nothing to do yet); else marks `UPLOADING`, calls `ensureFolder` + `upload`, marks `UPLOADED` with `driveId`, or `FAILED` + `Result.retry()` on error.
  - `object : fun enqueue(context: Context)` building a `OneTimeWorkRequest` with `Constraints(requiredNetworkType = UNMETERED)`, enqueued unique as `"drive-upload"` (`ExistingWorkPolicy.KEEP`). Expose `uploadConstraints(): Constraints` for the test.
  - A thin `UploadQueue` seam: `fun nextBatch(store: RecordingStore): List<Recording>` = rows in `PENDING`/`FAILED`, oldest first — unit-tested.

- [ ] **Step 1: Write failing tests** — `DriveUploaderTest`:

```kotlin
@Test fun enqueue_uses_unmetered_constraint()          // uploadConstraints().requiredNetworkType == UNMETERED
@Test fun next_batch_returns_pending_and_failed_oldest_first()
@Test fun worker_without_account_keeps_pending()        // fake DriveClient.hasAccount()=false → rows stay PENDING (Review Focus 4)
```

- [ ] **Step 2: Run tests, verify they fail** — Expected: FAIL (unresolved `DriveUploader`/`UploadQueue`).
- [ ] **Step 3: Add `work-runtime-ktx`; implement `UploadQueue`, the `enqueue`/`uploadConstraints` helpers, and `DriveUploader`.** Wire `DriveUploader.enqueue(context)` into `CallRecordingService.ACTION_STOP` (the call site stubbed in Task 3). Inject `DriveClient` via a small factory so the worker test can pass a fake.
- [ ] **Step 4: Run tests, verify they pass.**
- [ ] **Step 5: Commit** — `feat: Wi-Fi-only Drive upload worker`.

### Task 7: Recordings screen and settings entries

**Files:**
- Create: `activities/RecordingsActivity.kt`, `res/layout/activity_recordings.xml`, `res/layout/item_recording.xml`, `adapters/RecordingsAdapter.kt`
- Modify: `activities/SettingsActivity.kt` + `res/layout/activity_settings.xml` (master toggle, Google sign-in/out, open Recordings), `app/src/main/res/values/strings.xml`, `AndroidManifest.xml` (register `RecordingsActivity`), `helpers/Config.kt` (add `recordCalls: Boolean`)
- Test: `app/src/test/kotlin/org/fossify/phone/recording/RecordingRowFormatTest.kt`

**Interfaces:**
- Consumes: `RecordingStore`/`Recording`/`UploadState` (Task 1), `DriveClient` sign-in (Task 5), `Config.recordCalls`.
- Produces:
  - `object RecordingRowFormat` with `fun subtitle(r: Recording): String` → `"<duration mm:ss> · <upload label>"` where label is Pending/Uploading/Uploaded/Failed. Pure, unit-tested.
  - `RecordingsActivity` listing rows (play via `ACTION_VIEW` on the file through FileProvider, delete via store + file delete), empty state, and a sign-in banner when `!DriveClient.hasAccount()`.
  - Settings: a toggle bound to `config.recordCalls`, a Google sign-in/out row, and a row opening `RecordingsActivity`.

- [ ] **Step 1: Write failing test** — `RecordingRowFormatTest`:

```kotlin
@Test fun subtitle_shows_duration_and_state()   // 65_000ms + UPLOADED → "01:05 · Uploaded"
@Test fun subtitle_pending_label()
```

- [ ] **Step 2: Run test, verify it fails** — Expected: FAIL (unresolved `RecordingRowFormat`).
- [ ] **Step 3: Implement `RecordingRowFormat`, then the activity/adapter/layouts, settings entries, `Config.recordCalls`, and the FileProvider entry** (reuse any existing Fossify FileProvider authority; add one if none). Style with the hyper tokens used elsewhere.
- [ ] **Step 4: Run test, verify it passes; build + install** — open Settings, toggle recording on, open Recordings (empty state renders), sign-in row launches the Google chooser. Expected: screens render without crash; `lintFossDebug` passes.
- [ ] **Step 5: Commit** — `feat: recordings screen and settings`.

### Task 8: End-to-end on-device acceptance (with the user)

**Files:** none (verification only; token/strings tweaks allowed if a label is wrong).

- [ ] **Step 1: Enable + sign in** — on the phone, toggle recording on and sign into Google (Drive consent). Confirm `DriveClient.hasAccount()` true (a recording later flips to UPLOADED).
- [ ] **Step 2: Real call (user picks a safe number, never emergency)** — place and answer/connect a short call, speak a test phrase, hang up. Confirm a `.m4a` appears in the Recordings screen with the right number and a non-zero duration.
- [ ] **Step 3: Non-silent check (Review Focus 1)** — `adb -s $S shell run-as app.hyperdial.debug cat files/recordings/<file>` piped out (or `adb pull` via a world-readable copy), then `ffmpeg -i <file> -af volumedetect -f null -` and read `mean_volume`/`max_volume`. Expected: clearly non-silent (e.g. `max_volume` > -50 dB). If silent → record the finding; mic-only is not viable on this device and the both-sides phase is planned instead.
- [ ] **Step 4: Wi-Fi upload (Review Focus 5)** — with Wi-Fi on, the recording flips to UPLOADED and appears in Drive under `HyperDial Recordings`. Turn Wi-Fi off, record another; it stays PENDING; turn Wi-Fi on; it drains to UPLOADED.
- [ ] **Step 5: Missed-call guard (Review Focus 3)** — let a call ring and decline it; confirm no `.m4a` is produced.
- [ ] **Step 6: Record results** — ledger pass/fail per step and the mic-viability finding. No commit unless a label/string was changed.
