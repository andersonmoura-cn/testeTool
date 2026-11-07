pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "XadrezRPG"

// Motor de regras (Kotlin puro), testável sem Android SDK: ./gradlew -p engine test
includeBuild("engine")
include(":app")
