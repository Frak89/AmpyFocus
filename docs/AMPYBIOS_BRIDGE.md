# AmpyFocus ↔ AmpybiOS FocusBridge v2

## Privilege boundary

App package: `com.ampy.focus.extreme`. Privileged provider authority:
`content://com.ampybios.focusbridge.policy`. The **OS** defines and enforces
`com.ampybios.permission.MANAGE_FOCUS` (signature protection, not dangerous).

The Extreme app and provider must both be **signed with the authorized AmpybiOS
platform certificate**, installed in the OS image, and tested with AOSP build policies.
Installing an ordinary debug APK does not grant the permission.

## Provider IPC

`ContentResolver.call(uri, "set_focus", null, bundle)`

Input:
- `protocol`: 2
- `active`: Boolean
- `blocked_packages`: array of package names
- `blocked_domains`: array of validated hostname/suffix patterns
- `family_dns`: Boolean
- `silent_mode`: Boolean
- `metered_enabled`: Boolean
- `metered_packages`: array of package names
- `location_enabled`: Boolean
- `location_mode`: 0/1 inside/2 outside
- `location_radius_m`: 50–50000
- `location_lat`, `location_lon`: double when configured
- `daily_quota_enabled`, `daily_quota_min`
- `request_id`: audit correlation ID

Output:
- `applied`: Boolean (true only when **all requested OS-level policies** are truly applied)
- `reason`: human-readable failure/success

The included OS provider **currently enforces package suspension only** using
`PackageManager.setPackagesSuspended` with platform permission `SUSPEND_APPS`.
It rejects unsupported DNS, metered or custom-domain policies rather than
pretending the entire request was fulfilled. The client handles DND and Android
geo/usage state separately; the provider is **not yet independent geofence enforcement**.

## Recovery requirements

- Each successful suspension has a 20-minute OS-owned lease.
- The app renews policy every 10 minutes through alarms; Android power policy
  can delay alarms, so leases can expire rather than trap the device.
- The OS attempts to unsuspend managed packages when lease expires and on boot.
- The OS provider cannot suspend the phone, system UI, installer, settings, critical
  permission components or the AmpyFocus clients.
- If `applied=false`, the UI must not claim full protection.
- No system process killer is implemented. Do not turn off Android's core services.

## To complete Extreme

Implement a tested OS-owned network-policy service (per-UID allowed traffic,
DNS/DoH/VPN bypass policies, block lists for pornography/dating/content), and
wire it to FocusBridge with transactional rollback; add unit/integration/device
tests for permission bypasses, profile boundaries, SELinux, battery, OTA and
failsafe recovery. App alone cannot enforce those system capabilities.
