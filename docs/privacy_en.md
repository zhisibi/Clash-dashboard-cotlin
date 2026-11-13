# Mimi Panel Privacy Policy (Android)

Last updated: November 13, 2026
Effective: November 13, 2026
Policy version: 5

Mimi Panel ("the App") is provided by the developer Zhang Shibo ("we" or "us"). The App is a control panel that runs locally on your device and connects to the external-controller API of a mihomo-compatible proxy core that you deploy yourself. We take the protection of your personal information seriously; please read this policy carefully before using the App. The three appendices at the end of this policy (Personal Information Collection List, Third-Party Sharing List and App Permissions) form part of this policy.

The Chinese version of this policy (docs/privacy.md) is the governing version; this English version is provided for convenience.

## 1. We do not collect your personal information

- The App has no account registration or sign-in and never asks for your phone number, email or other identity information.
- The App does not collect or upload any personal information, device identifiers, usage statistics or behavioral data. We run no servers and perform no analytics or ad tracking of any kind.
- The App contains no third-party SDKs (no analytics, advertising, push or crash-reporting SDKs).
- All data processing the App needs happens locally on your device, or between the App and the backend you configure yourself. See Appendix 1 for an item-by-item list.

## 2. Information stored on your device, and how long it is kept

The following information is kept only in the App's storage on your device and is never sent to us:

- Backend details you enter: name, protocol (HTTP/HTTPS), host, port, path prefix and access secret. They are stored in the App's sandboxed storage (system SharedPreferences), which the Android sandbox isolates from other apps. The secret is stored in plain form within that sandboxed storage; Android auto backup is turned off for the App (allowBackup=false), so it is never backed up to the cloud. Keep your device and secret safe: anyone who learns the secret may be able to control your backend.
- App settings: theme, accent color, UI language, frosted glass and light effects, latency test parameters, log level, display and retention preferences, also stored in the App's sandboxed storage.
- A copy of the wallpaper image you choose (only if you set a wallpaper), stored in the App's private directory.
- Connections and logs data: the connection list, live and total traffic, memory usage and the backend log stream are data coming from your backend. They are shown and used in memory only, never written to disk, and released when you disconnect or exit the App.
- Exporting: the App can export log text only (through the system share sheet or "Save as", to a destination you choose). Backend configurations, including hosts and secrets, cannot be exported.

Retention: backend details and app settings are kept until you delete them in the App or uninstall the App; the wallpaper copy until you remove the wallpaper or uninstall. The exception is the crash-log mirror in the public Downloads folder, which uninstalling does not remove — see below.

### Home-screen widget snapshot

If you add the App's home-screen widget, the App keeps a separate local snapshot (its own SharedPreferences file, widget_snapshot) containing: the backend label, connection status, total upload and download traffic, memory usage, active connection count, current mode, the main policy group's name and the current node's name. It is used solely for the widget to display on this device and is never sent to any server.

### Crash logs

When the App hits an uncaught exception, it generates a crash log locally so you can troubleshoot. Each log contains: the time of the crash, the App version, the device manufacturer and model, the OS version, the application binary interface (ABI), the exception class name, the thread name and the full exception stack trace.

- The internal copy is kept in the App's private directory. Only the newest 20 logs are kept; older ones are deleted automatically.
- So a log can still be retrieved when the App itself will not open, the same log is also mirrored to the public Downloads folder, Download/MimiPanel. The mirror is likewise capped at the newest 20 copies.
- Please note: Download/MimiPanel is in public storage, and **uninstalling the App does not delete these mirrored files**. To delete them, open the Download/MimiPanel folder in a file manager and delete them manually. Other apps with storage access on the device can read that folder, so check a log's contents before sharing it.
- Crash logs are never uploaded automatically. They leave the device only when you tap "Copy", "Share" or "Save", and only to the clipboard, app or location you choose.

## 3. Network access

Except as listed below, the App's network requests go only to the backend addresses you configure, to read and change the proxy core's status, configuration, connections, logs and rules:

- Node latency tests are performed by your backend: the App only passes the test URL you set (default https://connect.rom.miui.com/generate_204) to the backend as a parameter and never visits that URL itself.
- Icon loading: the icons shown for policy groups use URLs taken from the icon field in the data your backend returns. Such a URL can point anywhere, and the App loads the image from it. The server hosting that icon will therefore learn your device's IP address and the time of the request. If you do not want such requests, do not set icons for policy groups in your backend configuration.
- Update check (depends on the distribution channel):
  - GitHub channel build: only when you tap Settings > About > Version, the App asks GitHub (api.github.com) for the version number of this project's latest release; if there is a newer one and you choose Update, it opens the GitHub release page in the system browser. The request carries no personal information or device identifier; GitHub may log access details such as your IP address under its own privacy policy.
  - Huawei AppGallery channel build: the App does not contact GitHub. Tapping the update check opens this App's page in Huawei AppGallery directly, and updates are delivered by AppGallery.
- Home-screen widget networking: if you added the App's home-screen widget, tapping one of its mode buttons (Rule / Global / Direct) makes the widget process read the backend address and secret the App has saved and send a mode-switch request (PATCH /configs) to your own backend, so the mode can be changed while the App is not open. Apart from this, the widget contacts no other server.
- Apart from the above, the App sends no data to us or to any other server.

## 4. Use of device capabilities and permissions

- Gravity sensor: used only when you turn on "Immersive Light → Follow device tilt", to adjust the direction of UI highlights. The data is used in memory in real time and is never stored or uploaded. The feature is off by default and stops automatically when the App goes to the background.
- Image picker: when setting a wallpaper, you pick one image through the system Photo Picker. The App can access only the image you pick and needs no storage permission.
- Saving and sharing files: done through the system "Save as" file picker (Storage Access Framework) and share sheet, and only for log content you choose to export.
- Clipboard: written only when you tap "Copy". The App never reads the clipboard.
- The system permissions the App requests, and what each is for, are listed in Appendix 3.

## 5. Your rights

You can view, change or delete your saved backends and settings in the App at any time. You can withdraw consent under "Settings → About → Withdraw privacy consent"; the App then exits and asks for consent again on the next launch. You can delete the internal crash logs (Settings → Crash Logs); for the mirrored logs in the public Downloads folder, delete them manually in a file manager as described in Section 2. Uninstalling the App deletes everything in the App's sandbox and private directories (except the Downloads mirrors). To exercise any other statutory rights regarding your personal information, contact us at the email in Section 8.

## 6. Minors

The App is intended for users who can deploy and manage a proxy core. It is not directed at minors and collects no personal information from minors.

## 7. Changes to this policy

If this policy changes, we will update it in the App and on this page and increase the policy version number. On your next launch of the App after a change, we will ask for your consent again; if you do not agree, you may stop using and uninstall the App.

## 8. Contact us

If you have any questions about this policy, contact us by the developer's email: zhisibi@163.com. You can also give feedback in the QQ group: 1127250124.

## Appendix 1: Personal Information Collection List

The App collects no account, identity or device-identifier information. The table below lists the data the App must process locally for it to work. All of it is processed only on your device and is never uploaded to the developer or any third party:

| Data item | Purpose | How and where it is processed | Retention |
| --- | --- | --- | --- |
| Backend details (name, protocol, host, port, path prefix, access secret) | Connecting to and managing the backend you configure | Stored locally in the App's sandbox (SharedPreferences); the secret is stored in plain form; system auto backup is off | Until you delete the backend or uninstall the App |
| App settings (theme, language, latency test parameters, log level, etc.) | Remembering your preferences | Stored locally in the App's sandbox (SharedPreferences) | Until you change them, reset to defaults, or uninstall the App |
| Wallpaper image copy | Displaying the wallpaper you set | Stored locally in the App's private directory | Until you remove the wallpaper or uninstall the App |
| Widget snapshot (backend label, connection status, total upload/download traffic, memory usage, connection count, mode, group name, node name) | Displayed by the home-screen widget on this device | Stored locally in a separate SharedPreferences file (widget_snapshot) | Overwritten as the overview refreshes; deleted when the App is uninstalled |
| Crash logs (time, App version, device manufacturer/model, OS version, ABI, exception class/thread name, full stack trace) | For you to troubleshoot crashes yourself | One copy in the App's private directory; a mirror copy in the public Download/MimiPanel folder | Both locations keep only the newest 20; the internal copy is deleted on uninstall, the mirror must be deleted manually in a file manager |
| Connections and logs data (connection list, traffic, backend log stream, etc.) | Showing backend status in real time | Used in memory only, never written to disk | Released when you disconnect or exit the App |

## Appendix 2: Third-Party Sharing List

- The App contains no third-party SDKs of any kind (no analytics, advertising, push or crash-reporting SDKs). We run no servers that receive your data, and we do not share, transfer or sell your personal information to any third party.
- The following are accesses to external services that happen only when you trigger them. The data involved is handled by the other party under its own privacy policy, and is not personal information shared by the App with third parties:

| Scenario | External service | Notes |
| --- | --- | --- |
| Update check (GitHub channel build only) | GitHub (api.github.com and the release page) | Triggered when you tap the update check; the request carries no personal information or device identifier; GitHub may log access details such as your IP |
| Update check (Huawei AppGallery channel build only) | Huawei AppGallery | Triggered when you tap the update check; opens the App's AppGallery page and updates are delivered by AppGallery; this build does not contact GitHub |
| Loading policy-group icons | The server hosting the icon URL (determined by your backend configuration) | It learns your device IP and the request time; avoid it by not setting icons in the backend configuration |
| Connecting to your own backend | The backend you deploy yourself | This is the App's core function; the destination is configured by you and is not third-party sharing |

## Appendix 3: App Permissions

| Permission | Purpose | When it is used | Effect of refusing or disabling it |
| --- | --- | --- | --- |
| INTERNET | Communicating with the backend you configure; loading icon images per the backend configuration; for the GitHub channel build, checking GitHub for updates | Whenever the App's network features are used | The App cannot connect to any backend and its core functions are unavailable |
| WRITE_EXTERNAL_STORAGE (declared only on Android 9 and below, i.e. API 28 and lower, and capped by the system at maxSdkVersion 28) | Writing crash-log mirrors into the public Download/MimiPanel folder so logs can be retrieved when the App will not open | Only when the App writes a crash log after an uncaught exception | No effect on the main functions; only the Downloads mirror is not created on old Android versions. On Android 10 and later, mirrors are written through the system media store and this permission is neither needed nor declared |
