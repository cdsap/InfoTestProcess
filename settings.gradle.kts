pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter {
                // Commercial Gradle plugins (Develocity, plugin-publish, …)
                includeGroupByRegex("com\\.gradle.*")
                // Portal-only Gradle org artifacts (kotlin-dsl, compatibility-plugin, …)
                includeGroupByRegex("org\\.gradle\\..*")
            }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter {
                // develocity-gradle-plugin is not on Maven Central
                includeGroupByRegex("com\\.gradle.*")
                includeGroupByRegex("org\\.gradle\\..*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "InfoTestProcess"

include("plugin")
