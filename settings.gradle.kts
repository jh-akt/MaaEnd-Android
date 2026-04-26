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
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "MaaEndAndroid"

include(":framework")
project(":framework").projectDir = file("MaaFramework-Android/framework")

include(":framework-ui")
project(":framework-ui").projectDir = file("MaaFramework-Android/framework-ui")

include(":app")
