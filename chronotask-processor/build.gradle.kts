plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

description = "ChronoTask Java Annotation Processor (APT / JSR-269)"

dependencies {
    api(project(":chronotask-core"))
    implementation("com.squareup:javapoet:1.13.0")

    testImplementation(project(":chronotask-test"))
    testImplementation(project(":chronotask-reflect"))
    testImplementation(project(":chronotask-ksp"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("com.google.testing.compile:compile-testing:0.21.0")
}
