# Shutter Sound Toggle

A small Android app to toggle enforced camera shutter sound using **Kotlin**, **Jetpack Compose**, and **Shizuku**.

## Requirements

- Android 13+ (API 33+)
- [Shizuku](https://shizuku.rikka.app/) installed and running (wireless debugging / ADB)
- An Android device whose audio policy supports `FOR_SYSTEM` changes (not guaranteed)

## Usage

1. Install and start Shizuku.
2. Install this app and grant its Shizuku permission.
3. Switch **強制シャッター音を解除** on.
4. Set the phone to **silent or vibrate mode** to make the camera shutter quiet.
5. Switch off to restore the device's default enforcement policy.

**Important:** This switch disables forced audio routing, **not** the camera shutter sound in all ringer modes. Whether the stock camera is silent depends on the device and OS. The setting is reset by reboot/audio-server restart. Before uninstalling, switch it off to restore the default policy. Do not assume a successful audio-policy call guarantees silence: check with your stock camera.

## Build

Open this repository with Android Studio, select a JDK 17 Gradle runtime, sync and run the `app` module. Alternatively, with Gradle 8.11.1 installed:

```bash
gradle :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also builds an APK artifact on each push to main.

## Implementation

A Shizuku UserService running as `shell` invokes the hidden `AudioSystem.getForceUse(FOR_SYSTEM)` / `setForceUse(FOR_SYSTEM, ...)` functions, with a small AIDL interface. The UI queries the *live* audio-policy state rather than persisting an assumed preference.

The app uses `MediaActionSound.mustPlayShutterSound()` to determine the appropriate restore value. It has no network permission and doesn't capture photos or audio.

### Limitations

- This project is an **initial implementation** and has **not been device-tested on Pixel 9a / Android 17**.
- Shizuku must be started and authorized to change the setting.
- Hidden Android APIs and device vendor policy can change.
- No boot-time auto-apply; no root requirement.
- Audio-policy changes may affect other enforced system sounds.
- Check photography privacy rules and respect people nearby.

## Technical references

- [Shizuku](https://github.com/RikkaApps/Shizuku)
- [ShutterSoundToggle](https://github.com/soralis0912/ShutterSoundToggle) — force-use mechanism and restoration behavior
- [SilentPixel](https://github.com/Morendais/SilentPixel) — alternative volume-group approach

This is an independent implementation; the references are credited for technical research.
