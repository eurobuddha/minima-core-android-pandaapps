# PandaApps — Android app store for Minima

A native Android **app store** for Minima Core companion apps. It fetches a curated [`apks.json`](https://github.com/eurobuddha/minima-core-apks) catalog over HTTPS, verifies each APK by **SHA-256**, and installs (or updates) via the system installer. First-time installs of eurobuddha's apps go direct; official-Minima updates download to your Downloads folder for manual install.

> ⚠️ **Development software — use at your own risk.** PandaApps and the apps it lists are experimental, actively-developed software provided **AS IS**, without warranty of any kind. They interact with a live blockchain and real funds; back up your seed, test with small amounts, and only risk what you can afford to lose. The store shows a matching disclaimer banner at the top of the list. Nothing here is financial advice.

## Features

- Curated catalog with **SHA-256 verification** before install.
- Install / update / open, with live download progress.
- Groups: your apps, official Minima, and more.
- A store-wide development disclaimer banner (sourced from the catalog's `disclaimer` field).

## Build

Standard Android Gradle build (the release variant is debug-signed for install parity):

```bash
./gradlew assembleRelease
```

Pinned to Android Studio's bundled JBR 21 (`org.gradle.java.home` in `gradle.properties`). The store lists itself, so new versions are published to the [minima-core-apks](https://github.com/eurobuddha/minima-core-apks) catalog.

## License

[MIT](LICENSE) © 2026 eurobuddha. Provided **as is, without warranty** — see the disclaimer above. Installed apps carry their own licenses.
