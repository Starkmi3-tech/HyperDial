# Contributing to HyperDial

Thanks for your interest in improving HyperDial! Contributions are welcome —
bug fixes, features, documentation, and translations.

HyperDial is licensed under **GPL-3.0**. By submitting a contribution you agree
that it is licensed under the same terms.

Because the `hyperdial` branch is protected, nobody pushes to it directly.
Everyone contributes through a **fork and a pull request** — the steps below.

## Prerequisites

- **JDK 17** or newer
- **Android SDK** with platform **36** installed
- Git, and optionally the [GitHub CLI](https://cli.github.com/) (`gh`)

## Step-by-step

### 1. Fork the repository

On <https://github.com/Starkmi3-tech/HyperDial>, click **Fork**. This creates a
copy under your account, e.g. `your-username/HyperDial`.

### 2. Clone your fork

```sh
git clone https://github.com/your-username/HyperDial.git
cd HyperDial
```

### 3. Add the original repo as `upstream`

This lets you keep your fork up to date.

```sh
git remote add upstream https://github.com/Starkmi3-tech/HyperDial.git
```

### 4. Create a branch off `hyperdial`

Never work on `hyperdial` directly — give each change its own branch:

```sh
git fetch upstream
git checkout -b short-topic-name upstream/hyperdial
```

Use a short, descriptive branch name, e.g. `fix-dialpad-crash` or
`add-recording-pause`.

### 5. Make your change

- Match the style of the surrounding code (Kotlin, 4-space indent, the naming
  and structure already in the file you're editing).
- Keep each pull request focused on **one logical change**.
- New logic should come with a unit test where it can be tested without a
  device (see the `app/src/test/` folder for examples).

### 6. Build, test, and lint

All three must pass before you open a pull request:

```sh
./gradlew assembleFossDebug
```

```sh
./gradlew testFossDebugUnitTest
```

```sh
./gradlew lintFossDebug
```

The debug APK is written to `app/build/outputs/apk/foss/debug/`.

### 7. Commit

Write a clear, imperative commit message:

```sh
git commit -am "Fix dialpad crash when the number field is empty"
```

### 8. Push to your fork

```sh
git push origin short-topic-name
```

### 9. Open a pull request

GitHub shows a **Compare & pull request** button after the push. Open the PR
with:

- **base:** `Starkmi3-tech/HyperDial`, branch `hyperdial`
- **compare:** your fork's branch

In the description, explain **what** changed and **why**, and mention how you
tested it. If it fixes an open issue, reference it (e.g. `Fixes #12`).

### 10. Review

The maintainer reviews the PR, may ask for changes, and merges it once it's
ready. To update the PR, just push more commits to the same branch — the PR
updates automatically.

## Keeping your fork up to date

Before starting new work, sync with the latest `hyperdial`:

```sh
git checkout hyperdial
git pull upstream hyperdial
git push origin hyperdial
```

## Reporting bugs and requesting features

Open an [issue](https://github.com/Starkmi3-tech/HyperDial/issues) and include:

- What you expected to happen and what actually happened
- Steps to reproduce
- Your device model and Android version (HyperDial targets Android 8.0+)
- Logs (`adb logcat`) if the app crashed

## A note on scope

HyperDial is a fork of [Fossify Phone](https://github.com/FossifyOrg/Phone) and
reuses its call-handling code. Changes to core dialing/telephony behavior are
usually better contributed upstream to Fossify Phone; HyperDial focuses on the
HyperOS-style interface and call recording.

## License

By contributing, you agree your work is licensed under **GPL-3.0**, the same as
the rest of the project. See [LICENSE](LICENSE).
