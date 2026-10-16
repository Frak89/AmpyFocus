# AmpyFocus

**AmpyFocus** is an Android digital focus launcher and policy engine. One source tree creates **two installable APK variants**:

| Flavor | Application ID | Intended device |
|---|---|---|
| Device Owner | `com.ampy.focus.owner` | Provisioned Android **fully managed / Device Owner** device |
| Extreme | `com.ampy.focus.extreme` | **AmpybiOS** build with a trusted FocusBridge provider |

## Implemented in the first developer preview (v0.1.0)

- OLED-dark focus screen, editable goal, countdown, intentionally long-press-to-exit escape control.
- Start manual 25/60/120-minute focus sessions.
- Local daily schedule, including schedules crossing midnight. Rescheduled on boot, time-zone/time change and app updates.
- Choose launcher apps to suspend using the **DevicePolicyManager** API on a properly provisioned Device Owner phone; restore the packages suspended by AmpyFocus when focus ends.
- Minimal launcher picker: launch allowed installed apps, exclude selected blocked apps while focusing.
- Offline generated brown-noise playback via a foreground media service.
- An **Extreme** APK with a secured, documented ContentProvider contract for a platform-signed AmpybiOS policy bridge. **The bridge is not included in this app.** The Extreme APK does not claim to block apps without it.
- Android GitHub Actions: run unit tests and assemble *both* debug APKs as downloadable build artifacts.

## Not yet implemented

Full category/domain pornography filtering, browser/WebView/DoH filtering, DNS/VPN firewall, app-specific time quotas, DND enforcement, background process control, additional WAV/FLAC soundscapes, tracking launch attempts, and a native AmpybiOS FocusBridge **are planned, not functional** in v0.1.0. Blocking an app that can browse the web is not the same as filtering its content.

**Safety:** No indiscriminate process killing, no disabling critical system packages, no forced rooting or device ownership, no attempts to prevent user recovery. Focus may be ended intentionally. AlarmManager scheduling is **inexact** under Doze/Android power management, so time windows are best effort; opening the app also reconciles state.

## Build

Open in Android Studio with **JDK 17**, Android SDK **36**, and Gradle **8.13** (Gradle is installed automatically by the CI; a wrapper binary is not included in this repo yet). From a local Gradle 8.13 installation:

    gradle :app:testOwnerDebugUnitTest :app:assembleOwnerDebug :app:assembleExtremeDebug

Artifacts:
- `app/build/outputs/apk/owner/debug/app-owner-debug.apk`
- `app/build/outputs/apk/extreme/debug/app-extreme-debug.apk`

CI workflow: **Actions → Android APK (two editions)**, then open the successful run's **Artifacts** section. Debug APKs are test builds and are not signed production releases.

## Device Owner provisioning — read before trying

**Do not experiment on your daily phone without a backup and a recovery plan.** Being a regular *Device Admin* is **not** Device Owner. Android generally requires provisioning before personal accounts are set up; the command may fail on an already configured handset. Follow the current official Android Enterprise provisioning documentation or use a test device.

Example, only on an eligible clean test device with the Owner APK already installed:

    adb shell dpm set-device-owner com.ampy.focus.owner/com.ampy.focus.FocusAdminReceiver

The Owner edition will show “Device Owner non configurato” and **will not apply package suspensions** unless it truly has Device Owner status. The app only suspends **explicitly selected** packages, and restores its recorded suspensions on exit. Android will reject protected roles/system packages.

## Extreme bridge

The Extreme APK needs a **real privileged component in an AmpybiOS system image**. See [docs/AMPYBIOS_BRIDGE.md](docs/AMPYBIOS_BRIDGE.md). **Installing only the Extreme APK does not grant system privileges or implement an OS-wide filter.**

## Privacy & distribution

Local-only preferences; no analytics SDK, network permission, or tracking. No AccessibilityService or VPN is requested in the preview. Re-evaluate disclosures, provisioning limitations, signatures, website-filtering reliability and store policies **before public distribution**.

Licensed under Apache-2.0. Contributions welcome.
