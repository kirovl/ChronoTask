plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

description = "ChronoTask Kotlin Symbol Processing (KSP) Compiler Plugin"

dependencies {
    api(project(":chronotask-core"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.27")
    implementation("com.squareup:kotlinpoet:1.18.1")
    implementation("com.squareup:kotlinpoet-ksp:1.18.1")

    // Use own processor to generate test bindings
    kspTest(project(":chronotask-ksp"))

    testImplementation(project(":chronotask-test"))
    testImplementation(project(":chronotask-reflect"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

kotlin.sourceSets.test {
    kotlin.srcDir("build/generated/ksp/test/kotlin")
}
