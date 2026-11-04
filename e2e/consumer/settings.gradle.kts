pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}

plugins {
    id("io.github.cdsap.testprocess") version "2.2.1"
}

rootProject.name = "info-test-process-e2e-consumer"
