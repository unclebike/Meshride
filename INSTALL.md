# MeshRide Installation Guide

## Prerequisites

- Hammerhead Karoo 2 or Karoo 3 cycling computer
- Meshtastic radio device (any Meshtastic-compatible LoRa node)
- The Meshtastic radio must be powered on and have BLE enabled

## Building from Source

### Requirements

- Android Studio Hedgehog or later
- JDK 17+
- GitHub Personal Access Token (for karoo-ext SDK from GitHub Packages)

### Setup GitHub Packages Authentication

Create or edit `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
```

The token needs `read:packages` scope.

### Build

```bash
./gradlew assembleRelease
```

The APK will be at `app/build/outputs/apk/release/meshride-release.apk`.

## Installing on Karoo

### Method 1: Hammerhead Companion App (Recommended)

1. Transfer the APK file to your phone
2. Long-press the APK file on your phone
3. Select "Share" and choose the Hammerhead Companion app
4. The Companion app will push the extension to your Karoo

### Method 2: ADB Sideload

1. Enable Developer Mode on your Karoo (Settings > About > tap Build Number 7 times)
2. Enable ADB over USB or WiFi
3. Connect to the Karoo:
   ```bash
   adb connect <karoo-ip>:5555
   ```
4. Install the APK:
   ```bash
   adb install meshride-release.apk
   ```

### Method 3: Direct File Transfer

1. Connect Karoo via USB to your computer
2. Copy the APK to the Karoo's Downloads folder
3. On the Karoo, use a file manager to locate and install the APK

## First-Time Setup

1. Open the MeshRide app from the Karoo launcher
2. Tap "Connect" to scan for nearby Meshtastic devices
3. Select your Meshtastic radio from the list
4. Configure your quick-send messages (3 slots available)
5. The extension will auto-connect on subsequent rides

## Adding Data Fields to Your Ride Screen

1. Go to Ride Profiles on your Karoo
2. Edit a ride profile's data pages
3. Add a data field and scroll to find "MeshRide" fields:
   - **Mesh Node Count** - shows how many nodes are on the mesh
   - **Mesh Last Message** - shows the last received message

## Using During a Ride

- **Incoming messages**: Appear as overlay alerts, auto-dismiss after 8 seconds
- **Quick messages**: Use the configured Bonus Actions (side buttons) to send pre-set messages
- **Node count**: Visible in the data field you configured

## Troubleshooting

- **No devices found**: Ensure your Meshtastic radio is powered on with BLE enabled
- **Connection drops**: The extension auto-reconnects; ensure the radio is within BLE range (~10m)
- **Data field shows "Searching"**: The BLE connection is not yet established
- **Extension not appearing**: Restart the Karoo after installing the APK
