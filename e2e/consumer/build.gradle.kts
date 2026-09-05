plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    // Force at least one fresh worker so the plugin's agent has something to register.
    forkEvery = 1
}
