# Icon Buzz BLE Scanner (read-only)

This is a diagnostic Android app for the Noise ColorFit Icon Buzz. It scans BLE devices, lets you select one, connects, and lists GATT services and characteristic UUIDs.

**Safety:** this first version is read-only. It does not write characteristics, upload a watch face, flash firmware, or change watch settings. A matching UUID is only a clue, not proof that a watch-face transfer protocol is compatible.

## Build an APK without Android Studio

1. Create a free GitHub repository and upload the contents of this folder.
2. Open the repository's **Actions** tab.
3. Select **Build diagnostic APK** and press **Run workflow** (or push to `main`).
4. When the workflow finishes, open its run and download the `IconBuzzScanner-debug-apk` artifact.
5. Extract the ZIP and install `app-debug.apk` on your Android phone. Android may ask you to allow installs from your browser/file manager.

## Test

1. Keep the watch charged and nearby.
2. Temporarily disconnect it from NoiseFit while testing, so two apps do not compete for the BLE connection.
3. Open Icon Buzz BLE Scanner, grant Bluetooth permissions, and tap **Scan for watch**.
4. Select the watch from the list and tap **Connect**.
5. Send me a screenshot or copy the service/characteristic list.

## Notes

- Android 12+ requires Nearby devices/Bluetooth permissions.
- The app displays BLE device addresses on the phone only; it does not upload them.
- This project uses the generic FEE7/FEE8/FEE9 UUIDs mentioned by an experimental third-party project. Icon Buzz compatibility has not been established.
