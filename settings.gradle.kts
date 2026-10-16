pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChronoTask"

include("chronotask-core")
include("chronotask-test")
include("chronotask-reflect")
include("chronotask-ksp")
include("chronotask-processor")
include("sample-kotlin-ksp")
include("sample-java")
include("sample-android")
