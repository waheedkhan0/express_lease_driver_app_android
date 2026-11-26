# ExpressLease Driver App — Native Android

## Overview
Native Android driver companion app for **ExpressLease Motorcycle Rental** fleet management. Replaces the Flutter version with improved background GPS reliability and native Android performance.

## Features
- 🔐 **Secure login** with Odoo 18 session authentication (EncryptedSharedPreferences via AES-256-GCM)
- 🛵 **Bike selection** — bottom sheet showing all assigned motorcycles
- 📡 **Background GPS tracking** using native Android `ForegroundService` + `FusedLocationProviderClient`
- 🔋 **Doze/App Standby resilient** — `WorkManager` periodic fallback every 15 minutes
- 📶 **Offline queue** — GPS points stored in Room DB and replayed when back online
- 🔄 **Auto-restart on boot** via `BOOT_COMPLETED` broadcast receiver
- 🎨 **Material You dark UI** with Jetpack Compose

## Architecture
```
ForegroundService (GpsTrackingService)
  └── FusedLocationProviderClient (every 30s)
  └── OdooRepository → Retrofit → Odoo API
  └── Room DB (offline GPS queue)

WorkManager (GpsWorker) ← 15-min Doze fallback
BootReceiver ← auto-restarts on reboot

UI: Jetpack Compose + StateFlow + Hilt DI
```

## Odoo API Endpoints Used
| Endpoint | Purpose |
|---|---|
| `POST /web/session/authenticate` | Login |
| `POST /api/express_lease/my_bikes` | Fetch assigned bikes |
| `POST /api/express_lease/update_location` | Post GPS coordinates |
| `POST /web/session/destroy` | Logout |

## Setup

### Prerequisites
- Android Studio Ladybug (2024.2+)
- Android SDK 35
- Java 17
- Odoo 18 backend running (see `express_lease_odoo_18` module)

### Build & Run
```bash
git clone https://github.com/waheedkhan0/express_lease_driver_app_android
cd express_lease_driver_app_android
./gradlew assembleDebug
```

### Install on Device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## GPS Data Management (Odoo Backend)

With 100 bikes posting every 30s the data volume is managed by:
1. **Haversine filter** — skips history if moved < 20m AND < 15 min (already in `api.py`)
2. **PostgreSQL index** on `(bike_id, timestamp DESC)` — add via migration
3. **Scheduled retention** — Odoo cron purges data older than 90 days (hourly samples retained)
4. **Future**: TimescaleDB hypertable for fleet > 500 bikes

## Permissions
| Permission | Reason |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS coordinates |
| `ACCESS_BACKGROUND_LOCATION` | Tracking while app is minimized |
| `FOREGROUND_SERVICE_LOCATION` | Android 14+ foreground service type |
| `RECEIVE_BOOT_COMPLETED` | Auto-restart after device reboot |
| `POST_NOTIFICATIONS` | Persistent tracking notification |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent OS killing service |

## Related
- **Odoo Backend**: [`express_lease_odoo_18`](../express_lease_odoo_18/)
- **Flutter Version**: [`express_lease_driver_app_flutter`](../express_lease_driver_app_flutter/)
