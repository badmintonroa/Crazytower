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
    // -Dcrazytower.threads=n sets the simulation threads, 1 switches the helpers off
    System.getProperty("crazytower.threads")?.let { systemProperty("crazytower.threads", it) }
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

// several bot playthroughs at once, one process per run on its own core:
// ./gradlew :core:bots --args="200 1-8" -Dbot=all -Djobs=6   (logs and summary in core/build/bots)
tasks.register<JavaExec>("bots") {
    classpath = sourceSets["test"].output + sourceSets["main"].output + configurations["testCompileClasspath"]
    mainClass.set("crazytower.core.BotFarmKt")
    for (key in listOf("bot", "jobs", "out", "xmx")) System.getProperty(key)?.let { systemProperty(key, it) }
}
