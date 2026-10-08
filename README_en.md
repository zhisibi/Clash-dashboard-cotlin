# Mimi Panel (Android)

English · [简体中文](README.md)

A native Android dashboard written in **Kotlin + Jetpack Compose** for the `external-controller` RESTful API of mihomo-compatible proxy cores. Look and features match Mimi Panel for HarmonyOS 1.2.2 (frosted glass, immersive light, accent colors, swipe between pages, pull to refresh, Chinese/English UI).

> The app **contains no proxy core** and never starts a proxy or VPN. It needs a backend that is already running (a core on your router or a LAN computer, or the external controller exposed by a proxy client on the phone).

| Item | Value |
| --- | --- |
| App name | Mimi Panel (Chinese 咪咪面板) |
| Package | `net.zash.clashpanel` (same package and signing key as earlier versions, so it upgrades in place and keeps backends and settings) |
| Version | 1.2.2 (versionCode 1020200) |
| Requirements | Android 8.0+ (minSdk 26, targetSdk 35); real-time frosted blur needs Android 12+ |
| Permissions | `android.permission.INTERNET` only |
| UI languages | Simplified Chinese, English (Settings → Language, can follow the system) |

## Changelog

### 1.2.2
- Ports the HarmonyOS 1.2.2 boundary-feedback fix (scrolling to an edge must give feedback):
  - **Page swiping**: swiping past the first page (Overview) or the last page (Settings) gives edge feedback: the system stretch on Android 12+ and the edge glow on Android 8–11 (the default `HorizontalPager` overscroll, nothing disables it)
  - **Bounce even when the content fits**: Compose only runs its overscroll effect when a container can scroll, so short content gave no feedback at the top or bottom. New `ui/Bounce.kt`: while a container can scroll the system stretch / glow is unchanged; while it can't, a spring rubber band moves the content (more resistance the further you pull) and springs back on release or after a fling, the same on Android 8–15
  - Covers the Settings list and every sub-page (Language, Backend, Panel, Proxies, Connections, Crash logs, About), the consent screen, the first-run backend setup, the privacy policy / user agreement, the proxy-group sheet, the connection-details sheet, crash log text (vertical and horizontal), Overview, and the Proxies / Providers, Connections, Logs and Rules / Rule providers lists
  - Empty, loading and error states on Proxies, Connections, Logs and Rules bounce too and still support pull-to-refresh
  - On pages with pull-to-refresh the top edge stays pull-to-refresh and the bottom edge bounces; in bottom sheets the top edge still drags the sheet down and the bottom edge bounces
  - The bounce is not counted as scrolling, so it does not trigger the bottom-bar auto-collapse, and the frosted header and bar keep blurring

### 1.2.1
- Ports the HarmonyOS 1.2.1 color-contrast fixes (WCAG: icons/controls/large text >= 3:1, body text >= 4.5:1):
  - **Save & Connect** and **Test Connection** in the backend form are never shown in Material's faded disabled state (38% alpha, about 2.2:1); they validate on tap instead: a missing host, a missing port or a port outside 1–65535 shows a toast, outlines the field in red and shows an ✗ message above the buttons; taps during a running test are ignored instead of greying the button out
  - Secondary text, labels, placeholders and status colors (good / warn / bad / log info) are darker in light mode; secondary text and the error color are lighter in dark mode, log info is `#38BDF8` in dark mode
  - The accent is split into a **text** tone (accent, >= 4.5), an **icon/control** tone (accentUi, >= 3), the original **decorative** color (accentFill: backdrop gradient, glow, swatches) and the **button fill + label** (primary / onPrimary). Tangerine, Bili Pink and Grass Green keep their vivid buttons in light mode with dark text `#1D2025`; in dark mode the fills are adjusted. Values are identical to the HarmonyOS app
  - The soft accent background (accentSoft) is 11% / 20% instead of 14% / 24%; new upload/download text colors (↑ / ↓ text on Connections)
  - Material 3 controls follow the new tokens: switches, radio buttons, checkboxes, sliders, progress bars and spinners, the selected bottom-bar icon, the cards-per-row filter chips, the selected node outline, the pull-to-refresh indicator, the consent "Agree" button, the close-connection button, segmented tabs and active round buttons. Material's primary is now the text tone so TextButton / OutlinedButton labels and focused field labels pass
- New `scripts/check-contrast.py`: parses `ui/Theme.kt`, mirrors `buildPalette()` and `Glass.kt`, and checks all 7 presets in light and dark mode against pages, cards, inputs, dialogs, translucent glass cards (default opacity 0.55 over the glass backdrop and glows), the header, the bottom bar and Material controls; exits non-zero on any failure (currently 0)

### 1.2.0
- **Renamed to Mimi Panel (咪咪面板)**: third-party brand names removed from the UI, strings and docs; the API client is now `CoreApi`. Package `net.zash.clashpanel` and the signing key are unchanged, so it installs over 1.1.x and keeps saved backends and settings
- **New icon**: the same original flat cat head as the HarmonyOS app (blue-purple gradient), as an Android adaptive icon (separate foreground/background layers inside the safe zone) plus a monochrome layer for Android 13 themed icons
- **Accent color**: Settings → Panel → Appearance → Accent color with 7 presets (Galaxy Blue default, Tangerine, Kitty Blue, Huawei Red, Elegant Purple, Bili Pink, Grass Green), applied app-wide immediately (buttons, segments, switches, sliders, radio buttons, cursors, charts, selected node outline, subscription progress, bottom bar selection) with a color preview; combines with light/dark/system theme, using slightly brighter tones in dark mode; palette identical to the HarmonyOS app
- **Frosted glass** (Settings → Panel → Frosted glass): material Custom / Thin / Regular / Thick, blur strength, card opacity, "Blur list items too"; applies to cards, page headers and the floating bar; shows the wallpaper through, or a soft accent gradient background when no wallpaper is set
  - Android 12+ uses real-time `RenderEffect` blur: the background (wallpaper/gradient) and the page content are recorded into `GraphicsLayer`s; cards blur the background, the header and the bar blur the content scrolling underneath. Android 8–11 falls back to a more opaque translucent look
- **Immersive light** (Settings → Panel → Immersive light): glass edge highlight (brighter toward the light), diagonal soft sheen, accent glow; light intensity, follow gravity (gravity sensor sampled at ~100 ms, quantized to 6°, stopped in the background), accent glow and light sweep (a light sweeps across the bar when switching pages)
- **Edge to edge**: content extends under the status bar and the gesture bar (navigation bar contrast scrim off) and pads by the real system bar sizes; every page uses a compact frosted header overlaying the content
- **Floating bottom bar**: a frosted bar just above the gesture bar; scrolling down collapses it to a round button at the bottom left (current page icon), scrolling back or tapping expands it; Settings → Panel → Navigation bar → Auto-hide navigation bar (on by default). The "backend connection failed" banner floats above the bar
- **Swipe between pages**: the six pages live in a `HorizontalPager` synced both ways with the bar (tapping animates, swiping moves the highlight right away) and the headers slide with their pages; filters and searches survive page and language switches. Page order: Overview, Proxies, Conns, Logs, Rules, Settings
- **Pull to refresh** on Overview, Proxies (groups / providers), Connections, Logs and Rules (rules / rule providers), with the HarmonyOS 1.1.9 semantics: Overview probes the backend and reloads config, proxies and rules; Proxies and Rules reload; Connections and Logs probe the backend and restart their live streams; a failed backend is reconnected; the accent-colored indicator shows below the header, stays until the requests finish (success or failure) and failures show a toast; works even when the content is shorter than the screen
- **Privacy policy and user agreement**: consent screen on first launch, no network requests before consent, declining exits; texts in `docs/privacy.md`, `docs/agreement.md` (English `docs/*_en.md`) adapted for Android (SharedPreferences, Photo Picker, Storage Access Framework, INTERNET permission)
- **Settings → About**: app name, version, developer, compatible API, privacy policy, user agreement, withdraw consent (disconnects and exits)
- **Language**: Settings → Language: Follow system (default), 简体中文, English; applies immediately without a restart. All UI text comes from in-app string tables (`i18n/strings_zh.json` / `strings_en.json`; `scripts/gen-i18n.py` generates the Kotlin tables and checks that both languages have the same keys and every key used in code exists), covering every page, dialog, toast, unit, relative time and the crash log contents. English layout polish: short bottom bar labels that shrink when space runs out, compact segments for long options, the glass "Material" options on their own row; settings search matches both languages; the launcher name is 咪咪面板 on Chinese systems and "Mimi Panel" otherwise
- Saving logs and crash logs now uses the system "Save as" picker (no storage permission); log files are named `mimi-logs-*.txt`
- System auto backup is off (`allowBackup=false` + `dataExtractionRules`), so backend secrets are never backed up to the cloud; the dynamic-receiver signature permission that androidx adds is removed, leaving INTERNET as the only permission
- The crash log entry on the Settings home shows a red dot while there are unseen crash logs

### 1.1.2
- Fixed a startup crash; stacked backend form buttons; crash log capture (Settings → Crash logs, prompt on next launch, copy/share/save); R8 without obfuscation for readable traces

### 1.1.1
- Restricted controllers: operations rejected with 405 (`PATCH /configs`) or 404 (`PUT /configs`, `/configs/geo`, `/restart`) are marked "not supported by this backend" with an explanation; GEO update falls back to `/upgrade/geo`

### 1.1.0
- Fixed the "Show GLOBAL" toggle; Overview became the first tab; custom wallpaper in Settings → Panel (card opacity / dim / blur)

### 1.0.0
- First native Kotlin + Jetpack Compose version

## Features

| Page | Features |
| --- | --- |
| Overview | Live upload/download speed, totals, active connections, memory, speed and memory charts, top hosts, quick proxy mode switch |
| Proxies | Group cards (1–3 columns), node selection, group / single node latency tests, latency colors, sorting, search (regex), hide unavailable nodes, show GLOBAL and hidden groups, unfix, proxy providers (subscription usage/expiry, update, health check) |
| Connections | Active / closed / all, search with regex, source IP filter, 11 sort fields, compact mode, close one / all / filtered, connection details (copyable), pause |
| Logs | Log level, type filter, regex search, pause, clear, newest/oldest first, copy, save as text file |
| Rules | Rule list, enable/disable single rules, rule provider update, hit counts, proxy chain and latency |
| Settings | UI language, multiple backends with connection test, proxy mode, core log level, TUN, LAN, IPv6, ports, reload config, update GEO, flush DNS/FakeIP cache, upgrade/restart core, theme, accent color, auto-hide navigation bar, frosted glass, immersive light, custom wallpaper, latency test options, retention counts, crash logs, about |

- Live data over WebSocket: `/traffic`, `/memory`, `/connections`, `/logs`, reconnecting with exponential backoff (1 s → 10 s)
- Endpoints answering 404/405/501 are marked "not supported by this backend" and explained in Settings
- Crashes are saved automatically and offered on the next launch; **Settings → Crash logs** can copy, share or save them

## Usage

1. Enable the external controller in your proxy client and note the address, port and secret
2. Install the APK, read and accept the privacy policy and user agreement, then enter the backend address, e.g. `192.168.1.1:9090` or `127.0.0.1:9090`
3. Tap "Test Connection", then "Save & Connect"

## Build

JDK 17+ and Android SDK 35:

```bash
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest    # JVM unit tests (the live test runs when a test mihomo listens on 127.0.0.1:19090, otherwise it is skipped)
```

After changing UI text run `python3 scripts/gen-i18n.py`: it generates `app/src/main/java/net/zash/clashpanel/i18n/Strings.kt` and `LegalTexts.kt` from `i18n/strings_*.json` and `docs/*.md` and checks the keys. Bump `PRIVACY_VERSION` in `i18n/I18n.kt` on material policy changes so users are asked to accept again.

After changing colors in `ui/Theme.kt` run `python3 scripts/check-contrast.py` (add `-v` for the worst pair per surface): it must report 0 failures.

### Signing

Release signing reads `keystore.properties` in the project root (git-ignored):

```properties
storeFile=release.keystore
storePassword=your password
keyAlias=your alias
keyPassword=your password
```

Environment variables `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` work too; without either the debug key is used.

> Upgrades must be signed with the same key, otherwise Android refuses to install over the old version.

### Automatic releases

`.github/workflows/release.yml` runs on `v*` tags (e.g. `git tag v1.2.0 && git push origin v1.2.0`) or manually from the Actions page, builds and publishes `MimiPanel-Android-<version>.apk` with notes taken from the matching changelog section above plus SHA-256.

Repository secrets (Settings → Secrets and variables → Actions; surrounding whitespace is trimmed):

| Secret | Content |
| --- | --- |
| `KEYSTORE_BASE64` | keystore as base64 (`base64 -w0 release.keystore`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

## Differences from the HarmonyOS app

- Frosted glass is drawn with Compose `GraphicsLayer` + `RenderEffect`; below Android 12 it is translucent only. There is no HarmonyOS "System" immersive material (Android has no equivalent); that row shows whether this device supports real-time blur instead
- The edge highlight is approximated with a gradient stroke (HarmonyOS colors each side separately); the accent glow is a colored shadow (colors show on Android 9+)
- The header blocks touches (so taps don't fall through to list items underneath), so swiping on the header does not change pages; swipe on the page content (the HarmonyOS header is inside the page and swipes with it)
- The launcher name follows the system language, not the in-app language (the app UI does switch)
- The package stays `net.zash.clashpanel` so existing users can upgrade (the HarmonyOS app moved to `com.zhisibi.mimipanel` for its store listing); the local settings file is still named `clash_panel`
- Wallpaper blur needs Android 12+

## Stack

Kotlin 2 · Jetpack Compose (Material 3) · OkHttp · kotlinx.serialization · Coroutines · Coil

## Thanks

- [mihomo](https://github.com/MetaCubeX/mihomo)
- [Zashboard](https://github.com/Zephyruso/zashboard)
