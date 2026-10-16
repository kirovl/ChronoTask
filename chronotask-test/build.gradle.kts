plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

description = "ChronoTask Test Support Utilities and Virtual Time Fixtures"

dependencies {
    api(project(":chronotask-core"))
    implementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation("org.junit.jupiter:junit-jupiter:5.11.3")

    testImplementation(project(":chronotask-reflect"))
    testImplementation(project(":chronotask-ksp"))
    testImplementation(project(":chronotask-processor"))
}
