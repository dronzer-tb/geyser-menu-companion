plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

dependencies {
    implementation(project(":common"))

    // Velocity API
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

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
