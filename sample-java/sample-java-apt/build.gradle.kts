plugins {
    `java-library`
}

dependencies {
    implementation(project(":chronotask-core"))
    annotationProcessor(project(":chronotask-processor"))

    testImplementation(project(":chronotask-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}
