# Clash 面板 (Kotlin)

A native Android dashboard for **mihomo / Clash** `external-controller`, written in Kotlin + Jetpack Compose. A native rewrite of the Zashboard experience; it does **not** bundle a mihomo core.

## Features
- **概览**: live up/down speed, totals, connections, memory, charts, top hosts, quick mode switch
- **代理**: group cards, node selection, group/node latency tests, sort/search/hide unavailable, GLOBAL & hidden groups, proxy providers (traffic, update, health check)
- **连接**: active/closed, search/regex, source-IP filter, sorting, close one/all, details, pause
- **日志**: level, type filter, regex search, pause, clear, copy, export to Downloads
- **规则**: rule list, enable/disable single rules, rule-provider updates, hit counts
- **设置**: multiple backends, connection test, mode/TUN/LAN/IPv6, ports, reload config, GEO update, flush DNS/FakeIP, upgrade/restart core, theme, wallpaper, latency settings, crash logs
- Operations the backend rejects (404/405, e.g. clients with restricted controllers) are marked as unsupported

## Requirements
- Android 8.0+ (minSdk 26, targetSdk 35)
- A running mihomo/Clash with `external-controller` enabled (e.g. `127.0.0.1:9090`)

## Build
```bash
./gradlew assembleRelease
```
Release signing is read from `keystore.properties` (git-ignored) in the project root, or env vars `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the release build is signed with the debug key.

```properties
storeFile=release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

## Stack
Kotlin 2 · Jetpack Compose (Material 3) · OkHttp (REST + WebSocket) · kotlinx.serialization · Coroutines · Coil
