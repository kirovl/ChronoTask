plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

description = "ChronoTask Runtime Reflection Binding for Annotated Tasks"

dependencies {
    api(project(":chronotask-core"))
    implementation(kotlin("reflect"))

    testImplementation(project(":chronotask-test"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}
