# ScreenBaconViewer

**An Android companion app for ScreenBacon that makes connecting to your desktop easier.**

ScreenBaconViewer is an Android application designed to work alongside [ScreenBacon](LINK_TO_SCREENBACON_REPOSITORY), allowing you to use an Android device as a viewer for your desktop screen.

Instead of manually entering connection details, the application can discover ScreenBacon instances on your local network and simplify the connection process.

## Download

**[Download the latest APK](LINK_TO_GITHUB_RELEASES)**

You can find the latest version in the [GitHub Releases](LINK_TO_GITHUB_RELEASES) section.

> **Note:** ScreenBaconViewer is distributed as an APK and is not available on the Google Play Store. You may need to allow installation from your browser or file manager to install it.

## Features

- **Automatic device discovery:** Detects compatible ScreenBacon instances on the local network.
- **Simplified connection:** Reduces the need for manual connection configuration.
- **Local network communication:** Discovers available instances using UDP broadcast.
- **Android companion:** Designed to work with the ScreenBacon desktop application.

## How It Works

ScreenBaconViewer works together with the ScreenBacon desktop application:

1. **Start ScreenBacon** on your computer.
2. **Connect both devices** to the same local network.
3. **Discover the desktop:** ScreenBaconViewer listens for discovery messages and identifies available instances.
4. **Establish the connection:** The viewer uses the discovered connection information to connect to the desktop application.

The discovery mechanism uses UDP broadcast to find devices on the local network. The discovery process is separate from the connection used for screen sharing.

## Requirements

- An Android device.
- A computer running [ScreenBacon](LINK_TO_SCREENBACON_REPOSITORY).
- Both devices connected to the same local network.
- Network permissions and firewall settings that allow the required communication.

## Installation

1. Open the [latest release](LINK_TO_GITHUB_RELEASES).
2. Download the APK asset.
3. Install the APK on your Android device.
4. Start ScreenBacon on your computer.
5. Open ScreenBaconViewer and follow the connection flow.

## Building from Source

If you want to build the application from source, follow these steps:

### Prerequisites

- **JDK:** Version 17
- **Android SDK:** Platform API 34
- Android SDK environment variable set (`ANDROID_HOME` or `ANDROID_SDK_ROOT`), or a `local.properties` file pointing to your SDK path.

### Build Steps

1. **Clone the repository:**
   ```bash
   git clone <REPOSITORY_URL>
   cd ScreenBaconViewer
   ```

2. **Configure the Android SDK location:**
   If `ANDROID_HOME` is not defined in your environment, create a `local.properties` file in the root directory:
   ```properties
   sdk.dir=/path/to/android-sdk
   ```

3. **Compile the Debug APK:**
   - On Linux / macOS:
     ```bash
     ./gradlew assembleDebug
     ```
   - On Windows:
     ```cmd
     gradlew.bat assembleDebug
     ```

4. **Locate the output APK:**
   Once the build completes successfully, find the generated APK at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

5. **Install via ADB (optional):**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Or install directly with Gradle:
   ```bash
   ./gradlew installDebug
   ```

## Project Context

ScreenBaconViewer is part of the ScreenBacon project, which explores automatic device discovery and screen sharing over a local network.

The project involves Android development, network communication, device discovery, and integration with a desktop application.

This repository contains the Android viewer component; the desktop-side functionality is maintained separately in the ScreenBacon repository.

## Limitations

- Both devices must be able to communicate over the same local network.
- Discovery may be affected by firewall rules, network isolation, or router configurations.
- Installation is currently performed manually through APK distribution.

## License

See [LICENSE](LICENSE) for licensing information.
