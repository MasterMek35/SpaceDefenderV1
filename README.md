# Space Defender V1

A small portrait Android arcade shooter written in Kotlin with a custom `View`.

## Included
- Start menu
- Touch/drag movement
- Tap/hold shooting
- Enemy spawning
- Bullet/enemy collision
- Score
- 3 lives
- Game over
- Pause/resume
- Local best score for the current app session
- No external art or game-engine dependency

## Build
Open this folder in Android Studio and let Gradle sync.

Then use:
- Build > Build App Bundle(s) / APK(s) > Build APK(s)

The project targets Android SDK 35 and requires Java 17 for Gradle/Kotlin.

## Controls
- Drag horizontally to move the ship.
- Tap/hold to fire.
- Tap the top-right area while playing to pause.
- Tap while paused to resume.
- Tap after game over to restart.
