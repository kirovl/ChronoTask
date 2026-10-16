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

tasks.named<Test>("test") {
    // Verify artifacts from this build, including when running tests on a fresh machine.
    dependsOn(
        ":chronotask-core:publishToMavenLocal",
        ":chronotask-reflect:publishToMavenLocal",
        ":chronotask-ksp:publishToMavenLocal",
        ":chronotask-processor:publishToMavenLocal",
        ":chronotask-test:publishToMavenLocal"
    )
    systemProperty("chronotask.publicationGroup", project.group.toString())
    systemProperty("chronotask.releaseVersion", project.version.toString())
    systemProperty("maven.repo.local", System.getProperty("maven.repo.local")
        ?: "${System.getProperty("user.home")}/.m2/repository")
}
