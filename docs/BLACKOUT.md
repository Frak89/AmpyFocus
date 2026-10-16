# AmpyFocus Blackout — visual lock without unsafe system overlays

The goal is a plain OLED-black display with **three indicators only**:
**date**, **time** and **battery percentage**. No shortcuts or controls are drawn.

## Why an Activity, not a global touch-stealing overlay?

Android does not let third-party `SYSTEM_ALERT_WINDOW` overlays safely own
status/nav bars, lockscreen, emergency dialogs or all other applications.
The implementation uses a normal foreground `BlackoutActivity` with
immersive edge-to-edge system bars. This works without AccessibilityService or
draw-over-other-apps permission.

On **Device Owner** devices, a separately enabled Lock Task kiosk invokes
the Android Enterprise API to constrain Home, Recents and outside apps.
The previous allowlist is recorded and restored after Focus. Do **not**
provision Device Owner for the first time on your daily phone.

On **Extreme**, the public Android APK remains subject to normal Android
system gestures. A true system-level blackout on AmpybiOS needs separate,
audited ROM hooks. The AOSP FocusBridge prototype in this repository only
suspends apps, and does not control global overlays/system gestures.

## How to try

1. Install the **Device Owner debug APK** or **Extreme debug APK** from the
   latest successful GitHub Actions run.
2. Open AmpyFocus, choose **Blackout di prova • 1 minuto**.
3. Observe the black screen, with only date, time and battery.
4. Hold one indicator ~700 ms for a turquoise/gold glitch.
5. Shake the phone strongly from side to side four times in ~2 seconds:
   the indicators zig-zag, the phone vibrates in a zig-zag pattern and the
   Focus session is deliberately ended.
6. As a backup, keep holding an indicator **8 seconds** for a recovery dialog
   that includes **112 emergency dialer** and a terminate control.
7. Optionally enable **Blackout automatico** to enter the display when a
   manual Focus session starts, or when Focus is already active and the
   launcher/dashboard is opened.
8. On a provisioned test Device Owner only, enable the separate **Kiosk
   Device Owner** option for system-supported Android Lock Task mode.

## Important practical limits

- When a **scheduled or geographical** Focus session starts while another
  app is in the foreground, Android's background activity launch restrictions
  mean the black Activity cannot necessarily rise on top automatically.
  Package restrictions may still apply. Reliable immediate global interception
  requires platform integration in AmpybiOS, not a regular APK.
- Screen power/lock, system emergency flows and recovery controls stay under
  Android's security model.
- Sensor readings are only consumed while Blackout is on screen. No accelerometer
  data are logged or sent anywhere.
- No permission to display over other apps, no silent Accessibility control,
  and no attempt to disable emergency dialing.
- The visual effect is an inspired glitch/scanline approximation, not yet the
  full Organic SVG animation engine from AmpybiOS.
- Physical-device testing is still required for accelerometer sensitivity,
  Haptic/Vibrator behavior, kiosk lifecycle, rotation, OS emergency calling,
  and time expiry while screen-off. GitHub CI checks builds and unit tests,
  not those system behaviors.
