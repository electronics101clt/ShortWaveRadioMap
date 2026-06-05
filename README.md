# ShortWave Radio Map

Android app wrapper for shortwave radio WebSDR receivers with automatic fallback and connectivity guidance.

## Features

- **WebView wrapper** for [rx.skywavelinux.com](https://rx.skywavelinux.com) - world map of KiwiSDR and WebSDR receivers
- **Automatic fallback** to alternative SDR map services when primary is unreachable:
  - rx.skywavelinux.com (primary)
  - map.kiwisdr.com (fallback 1)
  - rx-tx.info/map-sdr-points (fallback 2)
- **Network connectivity detection** with WiFi status checking
- **Localized guidance screens** (English & Chinese) for restricted network environments
- **Optimized for car head units** - targets Android 8.0+ with 32-bit ARM support
- **Hardware acceleration** enabled for smooth SDR waterfall displays
- **Back navigation** through WebView history

## Target Devices

Built specifically for Chinese Android car head units (10.1" displays), but works on any Android 8.0+ device:
- Minimum SDK: 26 (Android 8.0 Oreo)
- Target architecture: ARMv7 (32-bit)
- No Google Play Services required
- Sideload-friendly for devices without Play Store

## Installation

### Method 1: ADB (for developers)
```bash
adb install app-debug.apk
```

### Method 2: Sideload (for car head units)
1. Copy APK to USB stick or SD card
2. Use a file manager app to browse to the APK
3. Tap to install

## Build Instructions

```bash
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Network Requirements

This app requires internet connectivity to access WebSDR receivers:
- WiFi connection recommended (car head units are typically WiFi-only)
- In restricted network environments, VPN/proxy required before launching app
- Some KiwiSDR receivers may be blocked by regional firewalls

## Technical Details

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 33
- **WebView Configuration**:
  - JavaScript enabled
  - DOM storage enabled
  - Mixed content compatibility mode (for HTTP SDR receivers)
  - Hardware acceleration on

## Connectivity Guidance

If all fallback URLs fail, the app displays localized instructions for:
- VPN setup (Astrill, ExpressVPN, Surfshark)
- Alternative circumvention tools (Shadowsocks, V2Ray, Trojan)
- Mobile hotspot workarounds
- Domestic KiwiSDR receivers (for users in China)

## License

MIT License - Free to use and modify

## Credits

- [Skywavelinux SDR Map](https://rx.skywavelinux.com) by Philip Collier (AB9IL)
- [KiwiSDR Project](https://map.kiwisdr.com)
- [RX-TX.info SDR Directory](https://rx-tx.info)
