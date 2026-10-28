# Button Hates You (Android)

A button that really, really does not want to be pressed, now on your phone.
It lives in a little house (Living Room, Kitchen, Bedroom, Bathroom), gets
hungry, sleepy and dirty, and complains about all of it. Play mini-games to
earn 🪙 coins and 💎 diamonds, and spend them on clothes, food and furniture.

The Android port of the web game (https://buttonhatesyou.vercel.app), written in
**Kotlin** with **Jetpack Compose**, built for phones: one-hand portrait play,
swipe between rooms, haptics, and reminders when he needs you.

## Build

Requirements: JDK 17+ (25 works), Android SDK with platform 37.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # economy rules tests
```

Create `local.properties` with `sdk.dir=C:/path/to/Android/Sdk` if the SDK is
not found automatically.

## Layout

```
app/src/main/kotlin/com/ankitsilwal/buttonhatesyou/
  economy/   the rules: catalog, needs, house, rewards, achievements, chest (pure Kotlin, unit tested)
  data/      the saved profile (wallet + needs) and the actions that change it
  avatar/    the button drawn in Compose, with every outfit item
  games/     the arcade engine (480x800 world, scaled) and every mini-game
  ui/        rooms, sheets, HUD and screens
```

See ARCHITECTURE.md for how the parts fit together.
