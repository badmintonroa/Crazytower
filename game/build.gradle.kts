plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation(libs.korge)
}

// KorGE reaches into AWT internals to create its OpenGL context.
val addOpens = listOf(
    "java.desktop/sun.java2d.opengl", "java.desktop/java.awt", "java.desktop/sun.awt", "java.desktop/sun.awt.X11",
    "java.desktop/sun.lwawt", "java.desktop/sun.lwawt.macosx", "java.desktop/com.apple.eawt", "java.desktop/com.apple.eawt.event",
).map { "--add-opens=$it=ALL-UNNAMED" }

application {
    mainClass.set("crazytower.game.MainKt")
    applicationDefaultJvmArgs = addOpens
}

// ---------------------------------------------------------------- edition (full game or Steam demo)
//   ./gradlew :game:run -Pdemo=true          plays the demo, which ends at two stars
//   -PstoreUrl=steam://store/<appid>         the store page the demo's last window opens

val demo = providers.gradleProperty("demo").map { it.toBoolean() }.getOrElse(false)
val storeUrl = providers.gradleProperty("storeUrl").getOrElse("")
val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")

val writeEdition = tasks.register("writeEdition") {
    val dir = layout.buildDirectory.dir("generated/edition")
    inputs.property("demo", demo)
    inputs.property("storeUrl", storeUrl)
    outputs.dir(dir)
    doLast {
        val file = dir.get().file("crazytower/edition.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("edition=${if (demo) "demo" else "full"}\nstoreUrl=$storeUrl\n")
    }
}
sourceSets.main { resources.srcDir(writeEdition) }

// ---------------------------------------------------------------- Steam package
//   ./gradlew :game:packageApp               build/jpackage/CrazyTower/      (CrazyTower.exe on Windows)
//   ./gradlew :game:packageApp -Pdemo=true   build/jpackage/CrazyTowerDemo/
// A folder with its own Java runtime, ready for a SteamPipe depot. jpackage builds for the
// system it runs on, so the Windows version is built on Windows (see .github/workflows/steam.yml).

val appName = if (demo) "CrazyTowerDemo" else "CrazyTower"
val windows = System.getProperty("os.name").lowercase().contains("win")
val launcher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }

tasks.register<Exec>("packageApp") {
    group = "distribution"
    description = "Builds a self-contained app folder for Steam with jpackage."
    dependsOn("installDist")
    val lib = layout.buildDirectory.dir("install/game/lib")
    val dest = layout.buildDirectory.dir("jpackage")
    inputs.dir(lib)
    inputs.property("appVersion", appVersion)
    outputs.dir(dest.map { it.dir(appName) })
    doFirst {
        delete(dest.get().dir(appName))
        val home = launcher.get().metadata.installationPath.asFile
        executable = File(home, if (windows) "bin/jpackage.exe" else "bin/jpackage").path
    }
    val icon = file(if (windows) "packaging/crazytower.ico" else "packaging/crazytower.png")
    args(
        "--type", "app-image",
        "--name", appName,
        "--app-version", appVersion,
        "--vendor", "CrazyTower",
        "--description", if (demo) "CrazyTower Demo" else "CrazyTower",
        "--icon", icon.path,
        "--input", lib.get().asFile.path,
        "--main-jar", "game.jar",
        "--main-class", application.mainClass.get(),
        "--dest", dest.get().asFile.path,
        // only what the game needs, without debug data: a smaller download
        "--add-modules", "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,jdk.unsupported,jdk.crypto.ec,jdk.zipfs",
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files --strip-native-commands",
    )
    for (option in addOpens + "-Xmx2g") args("--java-options", option)
}

// `./gradlew :game:run` is the developer's start: F7 shows FPS and time per system (DevMode.kt).
// Packaged builds (installer, Steam, demo) do not go through this task and never get the flag.
tasks.named<JavaExec>("run") {
    systemProperty("crazytower.dev", "true")
}
