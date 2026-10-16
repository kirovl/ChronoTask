plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3" apply false
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.dokka") version "2.2.0" apply false
    id("com.gradleup.nmcp") version "1.6.2" apply false
    id("com.gradleup.nmcp.aggregation") version "1.6.2"
    `maven-publish`
    signing
}

allprojects {
    group = providers.gradleProperty("publicationGroup").get()
    version = providers.gradleProperty("releaseVersion").get()

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

dependencies {
    publishedProjects.forEach { add("nmcpAggregation", project(":$it")) }
}

nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME")
            .orElse(providers.gradleProperty("mavenCentralUsername"))
        password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD")
            .orElse(providers.gradleProperty("mavenCentralPassword"))
        publishingType = providers.gradleProperty("centralPublishingType").orElse("AUTOMATIC")
        publicationName = "ChronoTask:${project.version}"
    }
}

val validateCentralPublishing by tasks.registering {
    group = "publishing"
    description = "Checks Central credentials and signing before uploading the five-module release."
    doLast {
        require(!project.version.toString().endsWith("-SNAPSHOT")) {
            "Central releases require a non-SNAPSHOT releaseVersion."
        }
        for ((environmentName, propertyName) in listOf(
            "MAVEN_CENTRAL_USERNAME" to "mavenCentralUsername",
            "MAVEN_CENTRAL_PASSWORD" to "mavenCentralPassword",
            "SIGNING_KEY" to "signing.key"
        )) {
            require(!providers.environmentVariable(environmentName)
                .orElse(providers.gradleProperty(propertyName)).orNull.isNullOrBlank()) {
                "Missing $environmentName (or Gradle property $propertyName). See docs/publishing.md."
            }
        }
    }
}

tasks.named("nmcpPublishAggregationToCentralPortal") {
    dependsOn(validateCentralPublishing)
}

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
        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "com.gradleup.nmcp")

        configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }

        tasks.named<Jar>("javadocJar") {
            from(tasks.named<org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask>("dokkaGeneratePublicationHtml")
                .flatMap { it.outputDirectory })
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
                        url.set("https://github.com/kirovl/ChronoTask")
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        developers {
                            developer {
                                id.set("kirovl")
                                name.set("kirovl")
                                url.set("https://github.com/kirovl")
                            }
                        }
                        scm {
                            connection.set("scm:git:https://github.com/kirovl/ChronoTask.git")
                            developerConnection.set("scm:git:ssh://git@github.com/kirovl/ChronoTask.git")
                            url.set("https://github.com/kirovl/ChronoTask")
                        }
                    }
                }
            }
        }

        val publication = extensions.getByType<PublishingExtension>().publications["mavenJava"]
        configure<SigningExtension> {
            val signingKey = providers.environmentVariable("SIGNING_KEY")
                .orElse(providers.gradleProperty("signing.key")).orNull
            val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")
                .orElse(providers.gradleProperty("signing.password")).orElse("").get()
            if (!signingKey.isNullOrBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(publication)
            }
        }
    }
}
