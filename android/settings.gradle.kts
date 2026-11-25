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
        // LiveKit Android يعتمد على audioswitch المستضاف على JitPack
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Maqraatal-Noor"
include(":app")
project(":app").projectDir = file("app")
