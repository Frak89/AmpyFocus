# GPS + cell/network focus locations

## Modes

- **Inside**: focus when the estimated device location is confidently inside the configured radius.
- **Outside**: focus when confidently outside the radius.
- **Off**: no location condition; manual and scheduled sessions remain available.

The app uses Android's `LocationManager.GPS_PROVIDER` and `NETWORK_PROVIDER`.
NETWORK_PROVIDER may incorporate nearby cell towers and Wi-Fi; it does **not**
provide ground-truth proximity to a single mobile cell or exact antenna distance.

## Configuration

1. Allow coarse/fine location while in use, then (if desired) **Allow all the time**
   in the Android app-permission settings.
2. In AmpyFocus: **Configura zona GPS / rete mobile** → **Usa posizione corrente come centro**.
3. Set a radius: metres or kilometres, range **50 m to 50 km**.
4. Enable **DENTRO** or **FUORI**. The UI displays confidence, distance and mode.
5. Return here to disable, or **Elimina coordinate salvate** to remove them.

On-device preferences store approximate zone center and last-fix accuracy. No server
calls are made to store or transmit location. The OS location provider may independently
use external network-assisted location services depending on OS configuration.

## Conservative boundary math

Use Haversine great-circle distance from saved center. A fix is **INSIDE** only if
`distance + reportedAccuracy <= radius`; **OUTSIDE** only if
`distance - reportedAccuracy > radius`. Otherwise **UNKNOWN**.

Position is considered stale 10 minutes after recording, and then location-only
blocking is disabled until an unambiguous new fix arrives. Android throttles background
location, and a cell-based fix is often too coarse for 50–100 m zones.

Geofence trigger adds to other rules with logical OR: manual session **OR**
time schedule **OR** geographic zone. Exit of all focus conditions restores policy.

## Safety and privacy

Never depend on consumer-phone geofencing for location security, physical access
control or emergencies. The app does not permanently disable GPS, the modem or 112.
Its background location permission may be denied or revoked at any time.
