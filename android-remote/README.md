# Aradhana Studio Remote (`com.hamrochurch.remote`)

Official Android Mobile Controller & Stage Prompter for **Aradhana Studio Presentation Software** (`hamrocms.com`).

## Key Capabilities
- **100% Offline LAN & Hotspot Architecture**: Works over Church Wi-Fi router, Windows Mobile Hotspot (`192.168.137.1`), or Phone Hotspot (`192.168.x.x` / `100.64.x.x`) with zero internet dependency.
- **Instant QR Pairing + Multi-IP Fast Probe**: Scans the QR code from Aradhana Studio PC (`Top Bar > Phone Icon` or `Settings > Connection`), probes all advertised private LAN IPs in parallel, and connects over Socket.IO (`5505`) with seamless automatic fallback to HTTP REST (`5506`).
- **Live Slide Control & Blind Swipe Pad**: Real-time (`<80ms`) live output preview, color-coded slide deck, thumb swipe pad, Volume Up/Down hardware slide stepping, Output Lock, Projector Window toggle, and granular layer Clear (`Restore`, `All`, `Text`, `Background`, `Overlays`, `Audio`, `Timers`).
- **Bhajan / Hymns (`1,842+` Nepali Songs)**: Quick numeric keypad dialer (`0–9`), category filter (`Bhajan`, `Chorus`, `Bal Sangati`, `New`), Devanagari + offline Roman-Nepali search (`stutiko` → `स्तुतिको`), and verse-by-verse bottom-sheet launcher.
- **Nepali & English Bibles**: Translation switcher, 3-step `Book → Chapter → Verse` multi-select picker, offline Roman-Nepali & Devanagari full-text search, and `Prev/Next Verse` stepping.
- **Service Schedule & Saved Shows**: Switch active projects, launch schedule items (`Show`, `Audio`, `Video`, `Image`, `Overlay`), and search saved shows.
- **Full-Screen Stage Prompter & Podium Tools**: High-contrast OLED prompter with **Touch-Lock (`🔒`)**, live clock, preaching countdown timer (`5m–45m`), urgent Stage/Projector lower-third alerts, and a 16:9 interactive **Laser Pointer / Spotlight Focus / Zoom** touchpad.

## Security Architecture
- Mandatory 128-bit (`32` hex char, minimum `16` char) bearer token verified with constant-time `crypto.timingSafeEqual` on the desktop.
- `LanSecurityValidator` strictly blocks non-LAN/public IP addresses so church tokens can never be exfiltrated over the public internet.
- Saved PC profiles are encrypted at rest via Android Keystore (`EncryptedSharedPreferences` AES-256-GCM) with `android:allowBackup="false"`.
- Offline-only ZXing QR scanner (`zxing-android-embedded`) requiring zero Play Services runtime model downloads.

## Building Locally
```bash
cd android-remote
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```
Release APK output: `app/build/outputs/apk/release/app-release.apk`
