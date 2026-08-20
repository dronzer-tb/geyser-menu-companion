plugins {
    java
}

dependencies {
    // Netty for TCP client
    implementation("io.netty:netty-all:4.1.137.Final")

    // JSON
    implementation("com.google.code.gson:gson:2.14.0")

    // Annotations
    compileOnly("org.checkerframework:checker-qual:4.2.2")
}
