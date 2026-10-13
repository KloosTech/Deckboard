# Deckboard

A free third-party Kanban client for Nextcloud Deck.

> **Not affiliated with or endorsed by Nextcloud GmbH.** Nextcloud is a trademark of Nextcloud GmbH.
> Deckboard is an independent client that talks to the public Nextcloud Deck REST API of your own server.

Built with Kotlin Multiplatform and Compose Multiplatform. One shared UI for Android, iOS and desktop (macOS, Windows, Linux).

## Features

- Browse, create and delete boards; change board colors
- Kanban view with drag & drop of cards between columns
- Create, edit and delete cards (title, description, due date, labels, done state)
- Labels: create and assign colored labels, shown as named pills on the cards
- Share boards with Nextcloud users and groups (Edit / Share / Manage permissions) and assign members to cards
- Card comments, attachment list and activity history (per card and per board)
- Markdown descriptions and comments
- Default columns for new boards
- Optional auto-refresh polling

- Phone layout (single pane) and wide layout (sidebar + board)

## Getting started

You need a Nextcloud server with the **Deck** app installed.

1. In Nextcloud open *Settings → Security → Devices & sessions* and create an **app password**.
2. In Deckboard open Settings and enter:
   - **Base URL:** `https://your-server/index.php/apps/deck/api/v1.1`
   - **Username** and the **app password**
3. Press *Test connection*, then *Save*.

Use an app password, never your main account password. Credentials are stored on-device only and sent only to your server.

## Project layout

| Module | Purpose |
|--------|---------|
| [`shared`](./shared/src) | Shared code: API client (`data/`), UI (`ui/`), view model, platform bits |
| [`androidApp`](./androidApp) | Android entry point |
| [`iosApp`](./iosApp) | iOS entry point (Xcode project, SwiftUI host for the Compose UI) |
| [`desktopApp`](./desktopApp) | Desktop (JVM) entry point and packaging |

Package / bundle ID: `de.kloostech.deckboard`.

## Build & run

- Android: `./gradlew :androidApp:assembleDebug`
- Desktop: `./gradlew :desktopApp:run` (hot reload: `./gradlew :desktopApp:hotRun --auto`)
- Desktop installers: `./gradlew :desktopApp:packageDmg` (or `packageMsi` / `packageDeb`)
- iOS: open [`iosApp`](./iosApp) in Xcode, set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`, run

## Tests

- Desktop/JVM: `./gradlew :shared:jvmTest` (the API integration test needs a real Nextcloud Deck server)
- Android host: `./gradlew :shared:testAndroidHostTest`
- iOS: `./gradlew :shared:iosSimulatorArm64Test`

## Releasing

See [TODO.md](./TODO.md) for the store release checklist.

## License

Apache License 2.0, Copyright 2026 KloosTech. See [LICENSE](./LICENSE), [NOTICE](./NOTICE) and [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).

The name "Deckboard" and the app icon are not covered by the license grant (Apache-2.0, section 6): forks must use a different name and icon.
