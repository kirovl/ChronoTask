plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    application
}

dependencies {
    implementation(project(":chronotask-core"))
    implementation(project(":chronotask-reflect"))
    ksp(project(":chronotask-ksp"))
}

application {
    mainClass.set("io.github.chronotask.sample.kotlin.MainKt")
}
