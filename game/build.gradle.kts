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
