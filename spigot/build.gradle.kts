plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

dependencies {
    implementation(project(":common"))

    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    // Floodgate for Bedrock player detection
    compileOnly("org.geysermc.floodgate:api:2.2.3-SNAPSHOT")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("io.netty", "com.geysermenu.companion.libs.netty")
    relocate("com.google.gson", "com.geysermenu.companion.libs.gson")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
