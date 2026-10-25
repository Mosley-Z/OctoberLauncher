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

// app负责组装；feature通过core公共契约协作，模块依赖由各自构建脚本声明。
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

include(":feature:reader")
