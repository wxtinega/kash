// Categorising, "What was this?" questions, safe-to-spend. Pure Kotlin, no Android dependencies.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:parser"))
    testImplementation(libs.junit)
}
