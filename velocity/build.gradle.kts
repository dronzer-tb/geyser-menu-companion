plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta4"
    id("com.modrinth.minotaur") version "2.+"
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
    archiveBaseName.set("GeyserMenuCompanion-Velocity")
    relocate("io.netty", "com.geysermenu.companion.libs.netty")
    relocate("com.google.gson", "com.geysermenu.companion.libs.gson")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("geysermenu-companion")  // Your Modrinth project ID/slug
    versionNumber.set("${project.version}-velocity")
    versionName.set("GeyserMenu Companion ${project.version} (Velocity)")
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll("1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5")
    loaders.add("velocity")
    changelog.set("""
        ## Changes in ${project.version}
        - Fixed button resync after player reconnection
        - Improved TCP connection stability
        - Fixed Netty thread shutdown on server stop
    """.trimIndent())
    
    dependencies {
        required.project("floodgate")
    }
}
