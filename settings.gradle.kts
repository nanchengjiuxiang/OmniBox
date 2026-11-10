pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "OmniBox"
include(":app")

// ---- core ----
include(":core:designsystem")
include(":core:common")
include(":core:model")
include(":core:data")

// ---- features ----
include(":feature:home")
include(":feature:qrcode")
include(":feature:unitconverter")
include(":feature:randomwheel")
include(":feature:password")
include(":feature:timestamp")
include(":feature:texttools")
include(":feature:imagecompress")
include(":feature:exif")
include(":feature:formatconverter")
include(":feature:compass")
include(":feature:countdown")
include(":feature:decibel")
