plugins {
    `java-gradle-plugin`
    `maven-publish`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
    kotlin("plugin.serialization") version "2.3.10"

}

group = "io.github.cdsap"
version = "1.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("com.gradle:develocity-gradle-plugin:4.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation("junit:junit:4.13.2")
}

gradlePlugin {
    plugins {
        create("InfoTestProcessPlugin") {
            id = "io.github.cdsap.testprocess"
            displayName = "Info Test Processes"
            description = "Retrieve information of the Test processes after the build execution"
            implementationClass = "io.github.cdsap.testprocess.InfoTestProcessPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/cdsap/InfoTestProcess"
    vcsUrl = "https://github.com/cdsap/InfoTestProcess"
    tags = listOf("test", "process")
}

publishing {

    publications {
        create<MavenPublication>("testProcessPublication") {
            from(components["java"])
            artifactId = "testprocess"
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                scm {
                    connection.set("scm:git:git://github.com/cdsap/InfoTestProcess/")
                    url.set("https://github.com/cdsap/InfoTestProcess/")
                }
                name.set("InfoTestProcess")
                url.set("https://github.com/cdsap/InfoTestProcess/")
                description.set(
                    "Retrieve information of the Test process in your Build Scan or console"
                )
                licenses {
                    license {
                        name.set("The MIT License (MIT)")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("cdsap")
                        name.set("Inaki Villar")
                    }
                }
            }
        }
    }
}
