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

rootProject.name = "CleanFlow"

// App module
include(":app")

// Core modules
include(":core:model")
include(":core:ui")
include(":core:common")

// Data modules
include(":data:database")
include(":data:network")
include(":data:repository")

// Feature modules — only modules with real Kotlin source.
include(":feature:clara")
include(":feature:setup")

