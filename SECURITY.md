# Security and privacy

AmpyFocus is an opt-in personal focus tool, **not** enterprise malware, spyware,
device lockout software or guaranteed anti-bypass parental control.

- Manual escape: hold exit button, confirm, restore app suspensions where possible.
- Protected Android phone, system UI, settings, dialer, alarm, install/permission
  manager roles are not intentionally blocked.
- Never disable the cellular modem or emergency calling; all restrictions should
  degrade safely when unavailable or when Android denies privileges.
- Location coordinates, usage events and goals live only in app-local storage,
  with Android backup disabled. If you export an audio WAV it is saved to the
  user-selected document URI.
- Owner feature provisioning is explicit and cannot be created from a normal
  device-admin grant. Root is not requested or installed.
- Optional Cloudflare Family DNS sends name lookup metadata to Cloudflare;
  consult the provider's privacy terms. Filtering depends on connectivity.
- All Android settings and hardware-related restrictions may vary by OS,
  app roles and API level. Even installed FocusBridge prototype lacks a firewall.
- Debug APKs must never be treated as secure signed public releases.
- Validate OTA, loss of power, crashes, SIM changes, mock locations, permission
  revocation, inaccessible resolver, safe clocks, and user recovery before shipping.

Responsible vulnerability reporting: open a GitHub private security advisory
rather than disclosing details of an exploit publicly.
