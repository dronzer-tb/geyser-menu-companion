plugins {
    java
}

dependencies {
    // Netty for TCP client
    implementation("io.netty:netty-all:4.1.100.Final")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Annotations
    compileOnly("org.checkerframework:checker-qual:3.42.0")
}
