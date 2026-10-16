# AmpybiOS privileged FocusBridge prototype

This is a **platform/AOSP source component**, **not** an APK that works on stock
Android and **not** part of the public Android Studio build.

## Integration

1. Copy `ampybios-integration/` into your compatible Android/AmpybiOS source
   tree, for example `packages/apps/AmpyFocusBridge`.
2. Add `AmpyFocusBridge` to the device's `PRODUCT_PACKAGES`.
3. Use AmpybiOS platform signing, signature permission and required platform
   `SUSPEND_APPS` permission. Review Android 17 API/SELinux compatibility.
4. Build a platform-signed **Extreme client** into the system image with
   `com.ampybios.permission.MANAGE_FOCUS`. The GitHub debug Extreme APK cannot
   acquire signature-only permissions.
5. Validate AOSP build + signed install, service publication, tests and rollback.
   No AOSP build or platform-signature verification is performed in GitHub Actions.

## Implemented OS surface (prototype)

The provider exposes FocusBridge protocol 2 to the signature-permitted caller.
A supported request suspends only explicit nonessential packages and stores an
OS-owned 20-minute lease. The provider's receiver attempts restoration on
expiry, reboot and package update. This is a **baseline module**, not production
assurance. It still needs integration tests, SELinux and audit review.

Unsupported rules (DNS filtering, website domains, metered limitations) return
`applied=false`. No network interception or kernel modification is shipped.

The public Device Owner flavor is compiled separately and does **not**
require this AOSP component.
