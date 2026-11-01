package crazytower.game

/**
 * Developer mode: only then F7 shows the FPS overlay ([DebugOverlay]).
 *
 * `./gradlew :game:run` turns it on by itself (`-Dcrazytower.dev=true`, see `game/build.gradle.kts`).
 * Packaged builds (Windows installer, Steam, demo) never pass the flag, so players cannot open it.
 * `CRAZYTOWER_DEV=1` turns it on for a packaged build when debugging one on purpose.
 */
object DevMode {
    val active: Boolean =
        System.getProperty("crazytower.dev") == "true" || System.getenv("CRAZYTOWER_DEV") == "1"
}
