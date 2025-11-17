# OTun-M Android

**OTun-M** (formerly OXray) is a custom-branded VPN client for Android based on sing-box-for-android.

## Project Overview

- **Original Project**: [sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- **Package Name**: `com.situstechnologies.OXray`
- **Company**: Situs Technologies LLC
- **Purpose**: Custom VPN client for use with self-hosted OBox MyCloud servers

## Features

- VLESS + Reality protocol support
- Shadowsocks protocol support
- Smart split tunneling
- Global proxy mode
- Custom branding and UI
- Zero-logging, privacy-focused

## Build Requirements

- Android Studio Arctic Fox or later
- Android SDK 21+
- JDK 17+
- Gradle 8.0+

## Building
```bash
# Clone the repository
git clone https://github.com/antsbtw/OTun-M-Android.git
cd OTun-M-Android

# Build debug APK
./gradlew assembleOtherDebug

# Install to connected device
./gradlew installOtherDebug
```

## Architecture

This app works exclusively with OBox MyCloud self-hosted VPN servers, emphasizing:
- User control and privacy
- Self-hosted infrastructure
- Zero-logging policy
- Companion system design

## License

Based on sing-box-for-android which is licensed under GPLv3.

## Credits

- Original sing-box project by SagerNet
- Modified and branded by Situs Technologies LLC
