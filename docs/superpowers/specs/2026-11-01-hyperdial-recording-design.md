# HyperDial — call recording design spec

Date: 2026-11-01
Status: draft, awaiting review

## 1. What we are building

Automatic call recording for HyperDial (the Fossify Phone fork on the Lava
Agni 4), with the recordings uploaded to the user's own Google Drive. The
first deliverable is a **mic-only** recorder; a **both-sides** capture path is
designed for later as a drop-in audio source, pending an on-device
feasibility test.

**What the user asked for**
- Auto-record calls, with **no announcement** played to the other party.
- Store recordings in the user's **Google Drive** (the user signs in; files
  live in their cloud).
- Record **both sides** of the call ideally; mic-only is accepted as the
  baseline now, with both-sides added later once verified on the phone.

**Decisions made during brainstorming**
- Source: **mic-only now, both-sides later** (pluggable audio source).
- Storage: **keep local copies on the phone and upload to Drive only on
  Wi-Fi** (saves mobile data; the user is budget-conscious).
- Record **all** calls, with a master on/off toggle.

**Assumptions (correct these if wrong)**
- Personal use on this one phone; not published to a store.
- India jurisdiction: one-party consent, so recording the user's own calls
  without announcement is legal. This spec does not add a consent tone.
- HyperDial is (or will be) the default phone app, but recording does **not**
  depend on that — it works through the public telephony-state broadcast.

## 2. Non-goals

- No editing of telephony logic. The files the HyperDial design spec forbids
  stay untouched: `services/CallService.kt`,
  `services/SimpleCallScreeningService.kt`, `helpers/CallManager.kt`,
  `helpers/CallContactHelper.kt`, `helpers/ToneGeneratorHelper.kt`,
  `receivers/*`. New recording code lives in a new `recording/` package.
- No root, no bootloader unlock, no system-partition install.
- No consent/announcement tone.
- No access to the user's wider Drive: the `drive.file` scope means the app
  only ever sees files it created.
- No Play Store release; OAuth runs in "testing" mode for the user's own
  Google account.
- Both-sides capture is **not** built in this phase — only the seam for it.

## 3. The central risk (must be tested on-device)

On many Android 10+ devices the OS **mutes the microphone for third-party
apps during a voice call**, so `MediaRecorder` can receive silence or only a
faint local voice. Whether this MediaTek (mt6897, Android 16) device allows
usable in-call mic capture is **unknown until tested on the phone**.

- If mic capture is usable → mic-only ships as designed.
- If the mic is muted → mic-only yields little, and the **both-sides path
  (privileged shell via Shizuku / wireless debugging)** becomes the real
  solution. The design keeps the audio source pluggable so that path slots in
  without reworking the service, store, uploader, or UI.

A one-call on-device test (record, then check the file is non-silent) decides
this before we invest further in mic-only tuning.

## 4. Architecture

All new units; each has one job and a defined interface.

| Unit | Type | Responsibility |
|---|---|---|
| `CallStateWatcher` | `BroadcastReceiver` (`PHONE_STATE`) | Detect call active (OFFHOOK) and call ended (IDLE); start/stop the service. |
| `CallRecordingService` | foreground `Service` (type `microphone`) | Own the `AudioSource` while a call is active; write one file; finalize on hang-up; hand the file to the store. |
| `AudioSource` | interface + `MicAudioSource` impl | Produce the audio. `MicAudioSource` wraps `MediaRecorder`. The both-sides impl is a future sibling. |
| `RecordingStore` | class over app storage | Persist files + metadata (number, contact name, timestamp, duration, upload state); expose the list to the UI. |
| `DriveUploader` | `WorkManager` `CoroutineWorker` | Upload pending recordings to Drive under a Wi-Fi + network constraint; retry with backoff; mark uploaded. |
| `DriveClient` | class | Google Sign-In + Drive REST (`drive.file`); ensure a "HyperDial Recordings" folder; upload a file; return its Drive id. |
| `RecordingsActivity` | screen | List recordings (play, delete, upload status), empty state, and entry to settings. |
| recording settings | entries in existing settings | Master toggle, Google sign-in / sign-out, "Wi-Fi only" (default on). |

### Data flow

1. Call connects → `PHONE_STATE` = OFFHOOK → `CallStateWatcher` starts
   `CallRecordingService` (as a foreground service).
2. Service opens `MicAudioSource` (`MediaRecorder`, AAC) and records to a
   local `.m4a` in the app's external files dir.
3. Call ends → `PHONE_STATE` = IDLE → service stops the recorder, finalizes
   the file, reads the latest number/name from the call log, writes a
   `Recording` row to `RecordingStore`, stops the foreground service.
4. `RecordingStore` enqueues a `DriveUploader` job (unique per recording)
   constrained to **unmetered (Wi-Fi)** network.
5. When Wi-Fi is available and the user is signed in, `DriveUploader` calls
   `DriveClient.upload(file)` → file lands in "HyperDial Recordings"; the row
   is marked uploaded. If not signed in, the job stays pending and the UI
   shows "waiting for sign-in".

### Error handling

- Mic returns silence → the file is still saved and listed; the device test
  catches this case (amplitude check) and we switch strategy.
- Recorder fails to start (device refuses the source) → log, show a one-time
  in-app notice, do not crash the call.
- Upload fails or no Wi-Fi → WorkManager retries with backoff; the recording
  stays local and queued.
- Not signed in → recordings queue locally; a banner offers sign-in.
- Storage low → stop recording new calls and notify; never delete without the
  user's action (local copies are kept by the chosen storage policy).

## 5. Permissions and manifest

- Already granted: `RECORD_AUDIO`, `READ_PHONE_STATE`, `READ_CALL_LOG`.
- To add: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `INTERNET`,
  `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (already present for calls).
- Manifest: register `CallStateWatcher` for `android.intent.action.PHONE_STATE`
  and `CallRecordingService` with `foregroundServiceType="microphone"`.

## 6. Audio format

AAC in an `.m4a` container via `MediaRecorder` (`MPEG_4` / `AAC`), mono,
~64 kbps — small files, plays everywhere. `MicAudioSource` tries audio sources
in order `VOICE_COMMUNICATION` → `VOICE_RECOGNITION` → `MIC` and uses the
first that initializes.

## 7. Google Drive setup (user prerequisite)

Drive sign-in needs a one-time Google Cloud setup the user performs (guided):
1. Create a Google Cloud project; enable the **Google Drive API**.
2. OAuth consent screen: **External**, publishing status **Testing**, add the
   user's own Google account as a test user.
3. Create an **Android OAuth client**: package `app.hyperdial.debug`, plus the
   debug keystore **SHA-1** (we read it with `keytool` from the debug
   keystore and provide it).

No `google-services.json` is required; Google Sign-In uses the OAuth client
tied to package + SHA-1. The app requests the `drive.file` scope only.

## 8. Both-sides seam (future phase, not built now)

`AudioSource` is the only unit that knows how audio is captured. A future
`PrivilegedAudioSource` (driven through a Shizuku / wireless-debugging shell
helper that can read the call stream) implements the same interface; the
service, store, uploader, and UI are unchanged. The both-sides feasibility
test (scrcpy `--audio-source=voice-call`) decides whether this phase is
pursued and is tracked separately.

## 9. Testing

**Unit (pure Kotlin, no device):**
- File naming and `Recording` metadata mapping.
- Upload-queue state transitions (pending → uploading → uploaded / failed →
  retry), including the "not signed in" and "no Wi-Fi" holds.
- `DriveClient` folder-ensure and upload request shaping (mocked HTTP).

**On-device:**
- Make one real call (user picks a safe number, never emergency); confirm a
  `.m4a` is produced, is **non-silent** (pull the file over adb and read its
  peak/mean amplitude with `ffprobe`/`ffmpeg volumedetect`, or a small added
  audio helper), is listed in the Recordings screen with the right number and
  duration.
- With Wi-Fi on and signed in, the recording uploads and appears in Drive
  under "HyperDial Recordings"; the row flips to uploaded.
- With Wi-Fi off, the recording stays local and queued; turning Wi-Fi on
  drains the queue.

**Done**
- Mic-only: a real call produces a usable (non-silent) recording that uploads
  to the user's Drive on Wi-Fi, and the Recordings screen reflects state. If
  the device mutes the mic, that is recorded as the finding and the both-sides
  phase is planned instead.

## 10. Risks

| Risk | Handling |
|---|---|
| OS mutes mic during calls (see §3) | One-call device test decides; both-sides seam ready |
| Drive OAuth setup friction | Guided step-by-step; testing-mode consent for the user's own account |
| Foreground-service / mic policy on this OEM | FGS type `microphone`; test that recording starts without being killed |
| Battery / storage from always recording | Master toggle; local-only keep policy; low-storage guard |
| Upstream merge conflicts with new files | New code isolated in `recording/`; minimal manifest additions |
