# HyperDial

A phone app with a HyperOS-inspired look, built for the Lava Agni 4 (works on
any Android 8.0+ phone). It handles dialing, recent calls, contacts and the
in-call screen, and can be set as the default Phone app.

HyperDial is based on [Fossify Phone](https://github.com/FossifyOrg/Phone).
All call handling is Fossify Phone's; HyperDial changes how it looks. It is not
affiliated with Fossify or Xiaomi.

## Build

Needs JDK 17+ and the Android SDK (platform 36).

```sh
./gradlew assembleFossDebug
```

The APK is written to `app/build/outputs/apk/foss/debug/`.

## License

GPL-3.0, the same as Fossify Phone. See [LICENSE](LICENSE).
