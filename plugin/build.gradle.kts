import org.gradle.plugin.compatibility.compatibility
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata

plugins {
    `java-gradle-plugin`
    `maven-publish`
    `kotlin-dsl`
    alias(libs.plugins.pluginPublish)
    alias(libs.plugins.kotlin.serialization)
}

group = "io.github.cdsap"
version = "2.1.0"

// Keep historical Maven coordinates after moving sources out of the root project.
base {
    archivesName.set("InfoTestProcess")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

sourceSets {
    create("agent") {
        java.srcDir("src/agent/java")
    }
}

val develocityPluginClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    compileOnly(libs.develocity.gradlePlugin)
    develocityPluginClasspath(libs.develocity.gradlePlugin)
    implementation(libs.build.observability.core)
    implementation(libs.kotlinx.serializationJson)
    testImplementation(libs.junit)
    // GBOS contract validation is test/CI only — keep schemas off the plugin runtime classpath.
    testImplementation(libs.build.observability.schema)
    testImplementation(libs.json.schema.validator)
}

tasks.test {
    dependsOn("generatePomFileForPluginMavenPublication")
}

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    // compileOnly keeps Develocity out of the published POM; TestKit still needs it
    // on the plugin classpath when integration tests exercise the Develocity path.
    pluginClasspath.from(develocityPluginClasspath)
}

val agentJar = tasks.register<Jar>("agentJar") {
    group = "build"
    description =
        "Builds the java agent jar that is bundled into the plugin and injected into consumer test workers."
    archiveBaseName.set("info-test-process-agent")
    archiveVersion.set("")
    from(sourceSets["agent"].output)
    manifest {
        attributes(
            "Premain-Class" to "io.github.cdsap.testprocess.agent.WorkerRegistrarAgent",
            "Can-Retransform-Classes" to "false",
            "Can-Redefine-Classes" to "false"
        )
    }
}

// Route the agent jar through processResources so it ends up at
// build/resources/main/META-INF/agent/info-test-process-agent.jar — that path is
// included in both the final plugin jar AND the plugin classpath exposed by
// GradleRunner.withPluginClasspath() to integration tests.
tasks.named<ProcessResources>("processResources") {
    from(agentJar.flatMap { it.archiveFile }) {
        into("META-INF/agent")
    }
}

gradlePlugin {
    website.set("https://github.com/cdsap/InfoTestProcess")
    vcsUrl.set("https://github.com/cdsap/InfoTestProcess")
    plugins {
        create("InfoTestProcessPlugin") {
            id = "io.github.cdsap.testprocess"
            displayName = "Info Test Processes"
            description = "Retrieve information of the Test processes after the build execution"
            implementationClass = "io.github.cdsap.testprocess.InfoTestProcessPlugin"
            tags.set(listOf("test", "process"))
            // Proven by IntegrationNoDvTest / e2e-cc (store then HIT with --configuration-cache).
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

publishing {
    // pluginMaven is registered late by java-gradle-plugin; configureEach applies
    // when it appears. Metadata must live here — the marker resolves this coordinate.
    publications.withType<MavenPublication>().configureEach {
        if (name != "pluginMaven") return@configureEach
        artifactId = "InfoTestProcess"
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
