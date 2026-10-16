# AmpyFocus — Minimal Launcher, Blackout & Deep Work

Android 17-oriented open-source focus/digital detox system. Two APK editions from one source repository:

| APK flavor | Application ID | Enforces |
| --- | --- | --- |
| **Device Owner** | `com.ampy.focus.owner` | Android `DevicePolicyManager`: opted-in package suspension, selected metered-network restrictions, optional family DNS-over-TLS |
| **Extreme / AmpybiOS** | `com.ampy.focus.extreme` | Contract with a **platform-signed AmpybiOS FocusBridge**. A separate AOSP source module for package suspension is included under `ampybios-integration/` |

## v0.3 developer build — implemented

### Blackout — OLED-black focus surface

- A fullscreen, pitch-black `BlackoutActivity` displays **only the date, time and battery percentage**. No app icons, navigation buttons or in-app shortcuts are visible. See [Blackout setup and limits](docs/BLACKOUT.md).
- Long-press any clock/date/battery indicator for a brief AmpybiOS-inspired turquoise/gold glitch, animated zig-zag text and scanlines.
- Four deliberate alternating strong shakes within ~2 seconds trigger a zig-zag vibration and glitch, then **end Focus**. Detects with accelerometer locally, foreground-only.
- Hold clock/date/battery for **8 seconds** to open emergency calling or end Focus if the accelerometer fails (essential recovery path). No permanent lockout.
- Optional real **Android Lock Task kiosk**, only with provisioned Device Owner and explicit consent. Restores previous kiosk policy on exit. Emergency dialer remains allowlisted.
- On regular Android (and on a standalone Extreme debug APK), **immersive fullscreen is not a secure all-app overlay**: system gestures may still reveal Android UI.
- One-minute preview and opt-in automatic Blackout when Focus starts; both are disabled by default except the manual preview button.
- A genuine native AmpybiOS global overlay/system lockout still requires platform ROM implementation and device tests.

## v0.2 developer build — implemented

- **Independent `MinimalHomeActivity` OLED launcher** with Android HOME role, large clock, goal, session countdown, Phone/Clock, up to 4 permitted apps and intentional hold-to-end action. The full configuration dashboard stays in `MainActivity`. Choose AmpyFocus as default Home in Android Settings.
- **Strict allowlist mode**: during a session suspend visible launchable apps except explicitly approved essentials, protected Android components and the app itself. Use the Essential Apps picker before activating; this is opt-in, and not a blanket suspension of hidden system services.
- Manual sessions (25/60/120 min), daily local schedules, automatic reconciliation after boot, time changes and app launch; session-completion notification.
- **Inside/outside geographic area** focus zones, configurable **50 metres to 50 kilometres**. Uses Android GPS and NETWORK_PROVIDER (typically cell-tower/Wi-Fi-assisted location). Consent required for foreground + separately background location. Boundary decisions account for reported accuracy; stale fixes are rejected after 10 minutes. Cached coordinates never leave the device.
- Set explicit applications to suspend under **Device Owner**, preserved locally and restored on expiry/exit where Android permits.
- **Daily usage budgets for selected apps** based on Android Usage Access data; best-effort, checked periodically (normally 15 min; Doze may delay).
- **Launch statistics** from actual `ACTIVITY_RESUMED` events, not unverifiable blocked-launch guesses.
- Opt-in **Non disturbare** access with previous filter restoration; user must configure Android DND alarm priorities to ensure important alerts.
- Opt-in **Cloudflare Family** DNS-over-TLS for malware/adult domains while focus is active **Device Owner only**, with prior DNS config snapshot/restoration when the original mode is supported. Can fail if DNS is unreachable. No guarantee against embedded browser DNS, VPNs, alternative DNS, mixed-domain content, or all adult/dating services. Cloudflare processes DNS queries.
- Opt-in **metered-data blocking** for *separately selected* apps while focusing (mobile and metered Wi-Fi) on Device Owner. Not a full Wi-Fi/mobile firewall.
- Original **Brown / Pink-like / White noise** generator; offline audio played at 48 kHz and user-exported **PCM WAV 24-bit/48-kHz mono, 60 seconds**, using the Android Documents picker. Music and harmony WAV tracks are **not** included.
- Extreme client sends structured geo, domain, filter, quota, schedule and metered policy requests to **FocusBridge protocol 2**.
- `ampybios-integration/` holds an **AOSP platform-signed privileged provider module** implementing guarded package suspension with 20-min watchdog lease and signature-only IPC. Its firewall/content-filter/process hooks are **NOT** implemented. It must be built *inside AmpybiOS* and audited on real devices.

## Limitations / not finished

This is **not a production-quality unstoppable blocker**. On stock Android the Extreme APK *cannot* become privileged simply by installation; its preview requires the AmpybiOS OS image and a platform-signed Extreme app. The privileged bridge sample is **not built by the GitHub APK workflow**. An AOSP integration build/test is still required.

Unimplemented: complete category-level web filtering (dating/porn/custom domain lists), extension-level enforcement, tamper-proof kiosk/anti-bypass mode, background process killing, real-time precise quotas, entire-network firewall, OS-wide process tracing, finished offline sound library, independently scheduled OS geofence monitoring, and installer-signed public releases. The list of custom domains is sent to the Extreme bridge, but its initial provider **rejects such requests rather than silently claiming to enforce them**.

Background location and alarms are throttled by Android; expect delays and unknown location states. No Android app can infer an exact radius from cell ID alone without extra telecom geolocation infrastructure. The app uses Android's *network location estimate*, not raw tower-based triangulation.

## Build & download

Open with Android Studio + JDK 17 and SDK 36, or from Gradle 8.13:

```bash
gradle --no-daemon :app:testOwnerDebugUnitTest :app:assembleOwnerDebug :app:assembleExtremeDebug
```

In GitHub Actions, open **Android APK (two editions)** → the latest successful workflow → **Artifacts**:

- `AmpyFocus-DeviceOwner-debug` (ZIP with APK)
- `AmpyFocus-Extreme-debug` (ZIP with APK)

These are **debug APKs**, not a publicly signed/stable release. The two variants use different application IDs and can coexist.

## Device Owner deployment

A regular Device Administrator **is NOT** a Device Owner. A Device Owner requires Android Enterprise provisioning, generally on a test device during initial setup. Provisioning your daily driver could require a factory reset. Backup and plan recovery first. Example on an eligible clean test device after app installation:

```bash
adb shell dpm set-device-owner com.ampy.focus.owner/com.ampy.focus.FocusAdminReceiver
```

The status UI explicitly reports unavailable privileges instead of pretending enforcement succeeded. For maximum lock-down, first choose essential permitted apps, select AmpyFocus as HOME, then enable Strict Allowlist. On the first run, do not use a daily-driver device.

## AmpybiOS integration

See [FocusBridge protocol v2](docs/AMPYBIOS_BRIDGE.md) and [AOSP instructions](ampybios-integration/README.md). The `Android.bp` package is a *source candidate* requiring validation against your exact Android 17 fork. Android 17 API/platform patches and the AmpybiOS OS repository **have not been changed here**.

## Safety & privacy

- An intentional long press and confirmation always allow ending a focus session; this also disables the geo-trigger.
- Emergency dialer, clock, core system roles, and Android device recovery are kept accessible.
- All selected apps/goal/coordinates stay on the device; no developer backend, analytics SDK, upload, or account system.
- Opt-in Cloudflare Family DNS will involve a third-party processor of DNS queries.
- See [SECURITY.md](SECURITY.md) and [geofencing guide](docs/GEOFENCING.md).

**License: MIT**. Contributions and code review welcome.
