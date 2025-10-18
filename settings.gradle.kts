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

rootProject.name = "CleaningPlanner"

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

// Feature modules
include(":feature:auth")
include(":feature:household")
include(":feature:rooms")
include(":feature:qr")
include(":feature:kidmode")
include(":feature:board")
include(":feature:printables")
include(":feature:settings")

// Testing
include(":testing:core")

