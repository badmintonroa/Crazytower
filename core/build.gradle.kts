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
    // -Dbot=twin|wide|hub|all picks the bot, -Dcsv=<dir> writes a CSV line per day
    for (key in listOf("bot", "csv")) System.getProperty(key)?.let { systemProperty(key, it) }
}

// prints the classpath of the playthrough, for running several bots side by side without Gradle
tasks.register("playthroughClasspath") {
    dependsOn("testClasses")
    doLast { println((sourceSets["test"].output + sourceSets["main"].output + configurations["testCompileClasspath"]).asPath) }
}
