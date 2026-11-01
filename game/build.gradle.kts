plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation(libs.korge)
}

application {
    mainClass.set("crazytower.game.MainKt")
    // KorGE reaches into AWT internals to create its OpenGL context.
    applicationDefaultJvmArgs = listOf(
        "java.desktop/sun.java2d.opengl", "java.desktop/java.awt", "java.desktop/sun.awt", "java.desktop/sun.awt.X11",
        "java.desktop/sun.lwawt", "java.desktop/sun.lwawt.macosx", "java.desktop/com.apple.eawt", "java.desktop/com.apple.eawt.event",
    ).map { "--add-opens=$it=ALL-UNNAMED" }
}

// `./gradlew :game:run` is the developer's start: F7 shows FPS and time per system (DevMode.kt).
// Packaged builds (installer, Steam, demo) do not go through this task and never get the flag.
tasks.named<JavaExec>("run") {
    systemProperty("crazytower.dev", "true")
}
