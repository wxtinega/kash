// Pure Kotlin, no Android dependencies: the parser runs and is tested on any JVM,
// and the Android app depends on it.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
