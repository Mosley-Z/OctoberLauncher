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

rootProject.name = "OctoberRain"

// 模块划分见 docs/03-target-architecture.md 与 docs/10-engineering-contract.md
include(":app")
include(":core:common")
include(":core:design")
include(":core:device")
include(":core:render")
include(":core:data")
include(":core:weread-sync")
include(":core:widget-api")
include(":feature:home")
include(":feature:widgets")
include(":feature:tray")
include(":feature:settings")
include(":feature:content")
include(":feature:files")
include(":feature:weread-app")
include(":service")
