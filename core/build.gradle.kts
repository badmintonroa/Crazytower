plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.fleks)
    api(libs.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

// headless benchmark of the maximum test tower: ./gradlew :core:bench --args="24"
tasks.register<JavaExec>("bench") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("crazytower.core.BenchmarkKt")
}

tasks.register<JavaExec>("playthrough") {
    classpath = sourceSets["test"].output + sourceSets["main"].output + configurations["testCompileClasspath"]
    mainClass.set("crazytower.core.PlaythroughKt")
    // -Dstrategy=office|home|mix picks how the bot builds
    System.getProperty("strategy")?.let { systemProperty("strategy", it) }
}
