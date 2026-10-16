# AmpyFocus Extreme: privileged AmpybiOS bridge contract

**State: contract only; integration in the AmpybiOS OS source tree is a separate task.** No app install can silently grant this privilege.

## Trust boundary

AmpybiOS must define a signature-only permission owned by the OS:
`com.ampybios.permission.MANAGE_FOCUS`.
It must be granted to the **system-image signed** AmpyFocus Extreme APK. A privileged
system-owned provider exposes authority:
`content://com.ampybios.focusbridge.policy`

Provider must require the signature permission for **read and write**, verify
calling UID/signature, reject cross-user requests, keep an independent watchdog
and always preserve emergency calls, clock alarms, device unlock and recovery.

## Provider call protocol v1

Client: `ContentResolver.call(uri, "set_focus", null, extras)`

Input Bundle:
- `protocol`: integer = `1`
- `active`: boolean
- `blocked_packages`: StringArrayList; user-consented targets only
- `request_id`: String (unique audit correlation)

Output Bundle:
- `applied`: boolean — true ONLY after policy is actually enforced or safely removed
- `reason`: String — actionable error when not applied

No silent success: unrecognized protocols must return false. Policy service
must persist its own applied-package snapshot, restore policies on expiry,
crash and recovery, and check an independent OS-owned expiration schedule.
The Android app is not allowed to kill arbitrary processes itself.

An Extreme APK built from this repo will show **Bridge non disponibile** on
stock Android; this is intentional. Before shipping, implement and test the
provider in the real AmpybiOS tree, configure signature allowlists, adopt
protected framework hooks for any desired global firewall, and automate tests
for fail-safe recovery.

## Required pre-release tests

1. Starting/ending session suspends/restores exactly the consented targets.
2. Reboot during focus, crash mid-policy, device-owner removal, OTA and clock change.
3. Emergency dialing, alarms, telephony, permissions, settings needed for repair.
4. Child/multi-user separation; provider caller authentication; audit-log privacy.
5. Network restrictions, DNS-over-HTTPS, VPN, captive portal and app-bound webviews
   only after separate audited network-policy code is actually implemented.
