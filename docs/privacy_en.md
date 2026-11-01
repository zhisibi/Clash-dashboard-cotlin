# Mimi Panel Privacy Policy (Android)

Last updated: November 1, 2026
Effective: November 1, 2026

Mimi Panel ("the App") is provided by the developer Zhang Shibo ("we" or "us"). The App is a control panel that runs locally on your device and connects to the external-controller API of a mihomo-compatible proxy core that you deploy yourself. We take the protection of your personal information seriously; please read this policy carefully before using the App.

The Chinese version of this policy (docs/privacy.md) is the governing version; this English version is provided for convenience.

## 1. We do not collect your personal information

- The App has no account registration or sign-in and never asks for your phone number, email or other identity information.
- The App does not collect or upload any personal information, device identifiers, usage statistics or behavioral data. We run no servers and perform no analytics or ad tracking of any kind.
- The App contains no third-party SDKs (no analytics, advertising, push or crash-reporting SDKs).

## 2. Information stored on your device

The following information is kept only in the App's private storage on your device (system SharedPreferences and the app's private directory; Android auto backup is turned off for the App, so this data is never backed up to the cloud) and is never sent to us or any third party:

- Backend details you enter: name, protocol (HTTP/HTTPS), host, port, path prefix and access secret.
- App settings: theme, accent color, frosted glass and light effects, latency test parameters, log level, display and retention preferences, and UI language.
- A copy of the wallpaper image you choose (only if you set a wallpaper).
- Crash logs: when the App hits an uncaught exception, it records the error, app version, device model and OS version locally so you can troubleshoot. Crash logs are never uploaded automatically; they leave the device only when you tap "Copy", "Share" or "Save", and only to the clipboard, app or location you choose.
- Home-screen widget snapshot (since 1.4.0): if you add the App's home-screen widget, the App keeps a separate local snapshot of the overview (backend label, connection status, live and total traffic, active connection count) solely for the widget to display on this device. It is never sent to any server.

Uninstalling the App deletes all of the above. You can also delete backends, remove the wallpaper and clear crash logs inside the App.

## 3. Network access

- The App requests the network permission (android.permission.INTERNET). Except as listed below, network requests go only to the backend addresses you configure, to read and change the proxy core's status, configuration, connections, logs and rules. On Android 9 and below (API 28 and lower) only, the App also declares the storage permission (android.permission.WRITE_EXTERNAL_STORAGE), used solely to write a copy of crash logs into the public Download/MimiPanel folder so logs can be retrieved when the App itself will not open; on Android 10 and later the system media store is used instead and no such permission is needed.
- Node latency tests are performed by your backend: the App only passes the test URL you set (default https://www.gstatic.com/generate_204) to the backend as a parameter and never visits that URL itself.
- If your proxy configuration sets icon URLs for proxy groups, the App loads those icon images from those URLs.
- Update check: only when you tap Settings > About > Version, the App asks GitHub (api.github.com) for the version number of this project's latest release; if there is a newer one and you choose Update, it opens the GitHub release page in the system browser. The request carries no personal information or device identifier; GitHub may log access details such as your IP address under its own privacy policy.
- Apart from the above, the App sends no data to us or to any other server.

## 4. Use of device capabilities

- Gravity sensor: used only when you turn on "Immersive Light → Follow device tilt", to adjust the direction of UI highlights. The data is used in memory in real time and is never stored or uploaded. The feature is off by default and stops automatically when the App goes to the background.
- Image picker: when setting a wallpaper, you pick one image through the system Photo Picker. The App can access only the image you pick and needs no storage permission.
- Saving and sharing files: done through the system "Save as" file picker (Storage Access Framework) and share sheet, and only for log content you choose to export.
- Clipboard: written only when you tap "Copy". The App never reads the clipboard.

## 5. Your rights

You can view, change or delete your saved backends and settings in the App at any time. You can withdraw consent under "Settings → About → Withdraw privacy consent"; the App then exits and asks for consent again on the next launch. You can also uninstall the App to delete all local data.

## 6. Minors

The App is intended for users who can deploy and manage a proxy core. It is not directed at minors and collects no personal information from minors.

## 7. Changes to this policy

If this policy changes, we will update it in the App and on this page, and ask for your consent again where required.

## 8. Contact us

If you have any questions about this policy, contact us by the developer's email: zhisibi@163.com
