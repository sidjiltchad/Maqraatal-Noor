// Maqraatal-Noor — root settings. Plain public repositories only, so a
// fresh clone builds in Android Studio without any sandbox-local paths.
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
rootProject.name = "Maqraatal-Noor"
include(":app")
project(":app").projectDir = file("app")
