plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3" apply false
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    `maven-publish`
    signing
}

allprojects {
    group = "io.github.chronotask"
    version = "1.0.0"

    repositories {
        google()
        mavenCentral()
    }
}

val publishedProjects = setOf(
    "chronotask-core",
    "chronotask-reflect",
    "chronotask-ksp",
    "chronotask-processor",
    "chronotask-test"
)

subprojects {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    if (name != "sample-android") {
        apply(plugin = "org.jetbrains.kotlin.jvm")

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjvm-default=all")
            }
        }

        tasks.withType<JavaCompile> {
            sourceCompatibility = "17"
            targetCompatibility = "17"
            options.encoding = "UTF-8"
        }

        tasks.withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }

    if (name in publishedProjects) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()

                    pom {
                        name.set(project.name)
                        description.set(project.description ?: "ChronoTask: Modern, lightweight in-memory task scheduling framework for Kotlin and Java.")
                        url.set("https://github.com/chronotask/chronotask")
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        developers {
                            developer {
                                id.set("chronotask")
                                name.set("ChronoTask Contributors")
                                email.set("contributors@chronotask.github.io")
                            }
                        }
                        scm {
                            connection.set("scm:git:git://github.com/chronotask/chronotask.git")
                            developerConnection.set("scm:git:ssh://github.com:chronotask/chronotask.git")
                            url.set("https://github.com/chronotask/chronotask")
                        }
                    }
                }
            }
        }

        configure<SigningExtension> {
            val signingKey = System.getenv("SIGNING_KEY") ?: project.findProperty("signing.key") as String?
            val signingPassword = System.getenv("SIGNING_PASSWORD") ?: project.findProperty("signing.password") as String?
            if (!signingKey.isNullOrBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword ?: "")
                val mavenJava = extensions.getByType<PublishingExtension>().publications["mavenJava"]
                sign(mavenJava)
            }
        }
    }
}
