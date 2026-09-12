pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.develocity") version "4.5.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

develocity {
    // CI injects GE_URL / GE_API_KEY (see .github/workflows/build.yaml). Local builds
    // without credentials stay silent via publishing.onlyIf { isAuthenticated }.
    System.getenv("GE_URL")?.takeIf { it.isNotBlank() }?.let { server = it }
    System.getenv("GE_API_KEY")?.takeIf { it.isNotBlank() }?.let { accessKey = it }
    buildScan {
        publishing.onlyIf { it.isAuthenticated }
        uploadInBackground = System.getenv("CI") == null
    }
}

rootProject.name = "InfoTestProcess"
