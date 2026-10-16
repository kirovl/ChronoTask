plugins {
    java
    application
}

description = "ChronoTask Java 17+ Reference Application (APT & Builder API)"

application {
    mainClass.set("io.github.chronotask.sample.java.SampleJavaApplication")
}

dependencies {
    implementation(project(":chronotask-core"))
    annotationProcessor(project(":chronotask-processor"))

    testImplementation(project(":chronotask-test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}
